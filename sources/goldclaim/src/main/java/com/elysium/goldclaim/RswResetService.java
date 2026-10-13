package com.elysium.goldclaim;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class RswResetService {
    private static final Logger LOGGER = LoggerFactory.getLogger("GoldClaim/RSW");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path journal;
    static final List<String> RSW_SUB_DIMENSIONS = List.of("rsw", "rsw_nether", "rsw_the_end");
    private State state = new State();
    private final Set<ServerWorld> unloadingWorlds = new HashSet<>();
    private CompletableFuture<Void> archive;
    private boolean initialized;
    private long lastRemaining = Long.MAX_VALUE;
    private long retryAt;
    private boolean delayAnnounced;

    RswResetService() {
        this(Path.of("config", "goldclaim", "rsw-reset.json"));
    }

    RswResetService(Path journal) {
        this.journal = journal;
    }

    static final class State {
        int schemaVersion = 3;
        long nextResetAtMs;
        long startedAtMs;
        long seed;
        long previousSeed;
        String phase = "IDLE";
        String backup;
        int warningMask;
        boolean manualReset;
        long generation;
        Map<UUID, Location> locations = new HashMap<>();
    }

    record Location(double x, double y, double z, float yaw, float pitch) {
        boolean isFinite() {
            return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                && Float.isFinite(yaw) && Float.isFinite(pitch);
        }
    }

    enum ManualResetResult { SCHEDULED, ALREADY_PENDING }

    void initialize(long now) {
        try {
            if (Files.exists(this.journal)) {
                try (var reader = Files.newBufferedReader(this.journal)) {
                    State loaded = GSON.fromJson(reader, State.class);
                    if (loaded == null || loaded.phase == null) {
                        throw new IOException("Invalid RSW reset journal");
                    }
                    this.state = loaded;
                }
            }
            validate(this.state);
            if (this.state.nextResetAtMs <= 0) {
                this.state.nextResetAtMs = RswSchedule.nextReset(now);
            } else if (this.state.phase.equals("IDLE") && !this.state.manualReset) {
                long scheduled = RswSchedule.nextReset(this.state.nextResetAtMs - 1);
                if (scheduled != this.state.nextResetAtMs) {
                    this.state.nextResetAtMs = this.state.nextResetAtMs <= now
                        ? RswSchedule.latestReset(now) : RswSchedule.nextReset(now);
                }
            }
            this.lastRemaining = this.state.nextResetAtMs - now;
            this.state.schemaVersion = 3;
            this.save();
            this.initialized = true;
        } catch (IOException | RuntimeException failure) {
            LOGGER.error("RSW scheduling is blocked until its journal can be read and saved safely.", failure);
        }
    }

    boolean isClosed() {
        return !this.initialized || !this.state.phase.equals("IDLE");
    }

    ManualResetResult requestManualReset(long now) throws IOException {
        if (this.isClosed()) {
            throw new IllegalStateException("RSW is closed for reset or recovery; a new reset cannot be scheduled.");
        }
        long deadline = now + 900000L;
        if (this.state.manualReset || this.state.nextResetAtMs <= deadline) {
            return ManualResetResult.ALREADY_PENDING;
        }
        State requested = this.copyState();
        requested.nextResetAtMs = deadline;
        requested.manualReset = true;
        requested.warningMask = 1;
        this.save(requested);
        this.state = requested;
        this.lastRemaining = deadline - now;
        return ManualResetResult.SCHEDULED;
    }

    long resetAt() {
        return this.state.nextResetAtMs;
    }

    long generation() {
        return this.state.generation;
    }

    Location location(UUID player) {
        return this.isClosed() ? null : this.state.locations.get(player);
    }

    void remember(UUID player, Location location) throws IOException {
        if (this.isClosed()) {
            return;
        }
        if (!location.isFinite()) {
            throw new IllegalArgumentException("Non-finite RSW return position for " + player);
        }
        if (location.equals(this.state.locations.get(player))) {
            return;
        }
        State updated = this.copyState();
        updated.locations.put(player, location);
        this.save(updated);
        this.state = updated;
    }

    private State copyState() {
        return GSON.fromJson(GSON.toJson(this.state), State.class);
    }

    static State completedState(State current, long now) {
        State completed = GSON.fromJson(GSON.toJson(current), State.class);
        completed.nextResetAtMs = RswSchedule.nextReset(now);
        completed.phase = "IDLE";
        completed.warningMask = 0;
        completed.manualReset = false;
        completed.generation++;
        completed.locations.clear();
        return completed;
    }

    static void validate(State state) {
        if (!java.util.Set.of("IDLE", "UNLOADING", "ARCHIVING", "CREATING").contains(state.phase)) {
            throw new IllegalArgumentException("Unknown RSW reset phase: " + state.phase);
        }
        if (!state.phase.equals("IDLE") && (state.backup == null || !state.backup.matches("reset-[0-9]+"))) {
            throw new IllegalArgumentException("Invalid RSW backup location");
        }
        if (state.locations == null || state.locations.entrySet().stream()
                .anyMatch(entry -> entry.getKey() == null || entry.getValue() == null || !entry.getValue().isFinite())) {
            throw new IllegalArgumentException("Invalid RSW return locations");
        }
        if (state.manualReset && state.nextResetAtMs <= 0) {
            throw new IllegalArgumentException("Manual RSW reset is missing its deadline");
        }
    }

    void tick(MinecraftServer server, Consumer<ServerPlayerEntity> evacuate,
              BiConsumer<MinecraftServer, Long> create, Consumer<MinecraftServer> ready) {
        if (!this.initialized || System.currentTimeMillis() < this.retryAt) {
            return;
        }
        long now = System.currentTimeMillis();
        try {
            if (this.isClosed() && !this.delayAnnounced && now - this.state.startedAtMs > 900000L) {
                this.delayAnnounced = true;
                broadcast(server, "RSW reset is taking longer than estimated. It remains closed until safely ready.");
            }
            if (this.state.phase.equals("IDLE")) {
                long remaining = this.state.nextResetAtMs - now;
                for (int index = 0; index < RswSchedule.WARNING_SECONDS.length; index++) {
                    long threshold = RswSchedule.WARNING_SECONDS[index] * 1000L;
                    if (remaining > 0 && remaining <= threshold && this.lastRemaining > threshold
                            && (this.state.warningMask & (1 << index)) == 0) {
                        this.state.warningMask |= 1 << index;
                        this.save();
                        long seconds = (remaining + 999) / 1000;
                        broadcast(server, String.format("RSW will reset in %02d:%02d. Reset may take up to 15 minutes (estimate).", seconds / 60, seconds % 60));
                        break;
                    }
                }
                this.lastRemaining = remaining;
                if (remaining > 0) {
                    return;
                }
                this.state.startedAtMs = now;
                ServerWorld mainWorld = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, new Identifier("multiworld", "rsw")));
                this.state.previousSeed = mainWorld == null ? this.state.seed : mainWorld.getSeed();
                do {
                    this.state.seed = ThreadLocalRandom.current().nextLong();
                } while (this.state.seed == this.state.previousSeed);
                this.state.backup = "reset-" + now;
                this.state.phase = "UNLOADING";
                this.save();
                this.delayAnnounced = false;
                broadcast(server, "RSW reset is starting. Players are returning to the hub; RSW is temporarily closed.");
            }
            if (this.state.phase.equals("UNLOADING") || this.state.phase.equals("ARCHIVING")) {
                List<ServerWorld> loadedWorlds = new ArrayList<>();
                for (String sub : RSW_SUB_DIMENSIONS) {
                    ServerWorld w = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, new Identifier("multiworld", sub)));
                    if (w != null) {
                        loadedWorlds.add(w);
                    }
                }
                if (!loadedWorlds.isEmpty()) {
                    for (ServerWorld w : loadedWorlds) {
                        this.evacuate(w, evacuate);
                        if (this.unloadingWorlds.add(w)) {
                            this.unload(server, w);
                        }
                    }
                    return;
                }
                for (ServerWorld w : this.unloadingWorlds) {
                    w.close();
                }
                this.unloadingWorlds.clear();
                if (this.archive == null) {
                    this.state.phase = "ARCHIVING";
                    this.save();
                    Path root = server.getSavePath(WorldSavePath.ROOT);
                    Path backup = resetStaging(root, this.state.backup);
                    this.archive = CompletableFuture.runAsync(() -> {
                        try {
                            Files.createDirectories(backup);
                            for (String sub : RSW_SUB_DIMENSIONS) {
                                Path dimension = root.resolve("dimensions/multiworld/" + sub);
                                if (Files.exists(dimension)) {
                                    Path target = backup.resolve("world").resolve(sub);
                                    Files.createDirectories(target.getParent());
                                    if (Files.exists(target)) {
                                        target = backup.resolve("world").resolve(sub + "-recovery-" + System.currentTimeMillis());
                                    }
                                    Files.move(dimension, target);
                                }
                                Path config = Path.of("config", "multiworld", "worlds", "multiworld", sub + ".yml");
                                if (Files.exists(config)) {
                                    Path target = backup.resolve(sub + ".yml");
                                    if (!Files.exists(target)) {
                                        Files.move(config, target);
                                    } else {
                                        Files.move(config, backup.resolve(sub + "-recovery-" + System.currentTimeMillis() + ".yml"));
                                    }
                                }
                            }
                        } catch (IOException failure) {
                            throw new java.io.UncheckedIOException(failure);
                        }
                    });
                    return;
                }
                if (!this.archive.isDone()) {
                    return;
                }
                this.archive.join();
                this.archive = null;
                this.state.phase = "CREATING";
                this.save();
            }
            if (this.state.phase.equals("CREATING")) {
                boolean wrongSeed = false;
                for (String sub : RSW_SUB_DIMENSIONS) {
                    ServerWorld w = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, new Identifier("multiworld", sub)));
                    if (w != null && w.getSeed() != this.state.seed) {
                        wrongSeed = true;
                        break;
                    }
                }
                if (wrongSeed) {
                    this.state.phase = "UNLOADING";
                    this.save();
                    return;
                }
                boolean allLoaded = true;
                for (String sub : RSW_SUB_DIMENSIONS) {
                    if (server.getWorld(RegistryKey.of(RegistryKeys.WORLD, new Identifier("multiworld", sub))) == null) {
                        allLoaded = false;
                        break;
                    }
                }
                if (!allLoaded) {
                    create.accept(server, this.state.seed);
                    return;
                }
                for (String sub : RSW_SUB_DIMENSIONS) {
                    Path config = Path.of("config", "multiworld", "worlds", "multiworld", sub + ".yml");
                    if (!Files.exists(config)) {
                        throw new IOException("Multiworld did not persist configuration for " + sub);
                    }
                    Object configuration = Class.forName("me.isaiah.multiworld.config.FileConfiguration")
                        .getConstructor(java.io.File.class).newInstance(config.toFile());
                    long persistedSeed = ((Number)configuration.getClass().getMethod("getLong", String.class)
                        .invoke(configuration, "seed")).longValue();
                    if (persistedSeed != this.state.seed) {
                        throw new IOException("Persisted Multiworld seed for " + sub + " does not match the new RSW seed");
                    }
                }
                ready.accept(server);
                clearResetStaging(server.getSavePath(WorldSavePath.ROOT), this.state.backup);
                State completed = completedState(this.state, now);
                this.save(completed);
                this.state = completed;
                this.lastRemaining = this.state.nextResetAtMs - now;
                LOGGER.info("RSW reset complete: old seed={}, new seed={}, duration={}s; temporary reset data removed",
                    this.state.previousSeed, this.state.seed, (now - this.state.startedAtMs) / 1000);
                broadcast(server, "RSW has reset with a new seed and is ready. Next reset: 5am EST.");
            }
        } catch (Exception failure) {
            if (this.state.phase.equals("IDLE")) {
                this.initialized = false;
            }
            this.retryAt = now + 60000;
            if (this.archive != null && this.archive.isCompletedExceptionally()) {
                this.archive = null;
            }
            LOGGER.error("RSW reset paused safely; RSW remains closed. Retrying in 60 seconds.", failure);
            broadcast(server, "RSW reset is delayed. RSW remains closed while recovery is retried.");
        }
    }

    void tick(MinecraftServer server, ServerWorld world, Consumer<ServerPlayerEntity> evacuate,
              BiConsumer<MinecraftServer, Long> create, Consumer<ServerWorld> ready) {
        this.tick(server, evacuate, create, s -> {
            ServerWorld w = s.getWorld(RegistryKey.of(RegistryKeys.WORLD, new Identifier("multiworld", "rsw")));
            if (w != null) {
                ready.accept(w);
            }
        });
    }

    private void evacuate(ServerWorld world, Consumer<ServerPlayerEntity> evacuate) {
        for (ServerPlayerEntity player : new ArrayList<>(world.getPlayers())) {
            evacuate.accept(player);
        }
        if (!world.getPlayers().isEmpty()) {
            throw new IllegalStateException("RSW evacuation failed; refusing to unload it");
        }
        for (long chunk : world.getForcedChunks().toLongArray()) {
            world.setChunkForced(net.minecraft.util.math.ChunkPos.getPackedX(chunk), net.minecraft.util.math.ChunkPos.getPackedZ(chunk), false);
        }
    }

    private void unload(MinecraftServer server, ServerWorld world) throws ReflectiveOperationException {
        Class<?> fantasyType = Class.forName("xyz.nucleoid.fantasy.Fantasy");
        Object fantasy = fantasyType.getMethod("get", MinecraftServer.class).invoke(null, server);
        Method open = java.util.Arrays.stream(fantasyType.getMethods())
            .filter(method -> method.getName().equals("getOrOpenPersistentWorld") && method.getParameterCount() == 2)
            .findFirst().orElseThrow();
        Object handle = open.invoke(fantasy, world.getRegistryKey().getValue(), null);
        if (handle.getClass().getMethod("asWorld").invoke(handle) != world) {
            throw new IllegalStateException("Fantasy handle does not match the active " + world.getRegistryKey().getValue());
        }
        handle.getClass().getMethod("setTickWhenEmpty", boolean.class).invoke(handle, false);
        handle.getClass().getMethod("unload").invoke(handle);
    }

    static Path resetStaging(Path root, String transaction) {
        if (transaction == null || !transaction.matches("reset-[0-9]+")) {
            throw new IllegalArgumentException("Invalid RSW reset transaction: " + transaction);
        }
        Path world = root.toAbsolutePath().normalize();
        return world.getParent().resolve(".rsw-reset-in-progress").resolve(world.getFileName()).resolve(transaction);
    }

    static void clearResetStaging(Path root, String transaction) throws IOException {
        Path staging = resetStaging(root, transaction);
        // Also finish transactions started by versions that archived inside the world.
        Path legacy = root.resolve("dimensions/multiworld/rsw-reset-backups").resolve(transaction);
        for (Path directory : List.of(staging, legacy)) {
            if (!Files.exists(directory, java.nio.file.LinkOption.NOFOLLOW_LINKS)) continue;
            try (var entries = Files.walk(directory)) {
                for (Path entry : entries.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(entry);
                }
            }
        }
    }

    private void save() throws IOException {
        this.save(this.state);
    }

    private void save(State persisted) throws IOException {
        Files.createDirectories(this.journal.getParent());
        Path temporary = this.journal.resolveSibling(this.journal.getFileName() + ".tmp");
        Files.writeString(temporary, GSON.toJson(persisted));
        try {
            Files.move(temporary, this.journal, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, this.journal, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void broadcast(MinecraftServer server, String message) {
        server.getPlayerManager().broadcast(Text.literal("[RSW] " + message), false);
    }
}