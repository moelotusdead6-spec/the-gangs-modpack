package com.elysium.goldclaim;

import com.elysium.goldclaim.mixin.EnderDragonFightInvoker;
import net.minecraft.entity.boss.dragon.EnderDragonFight;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.concurrent.CompletionException;

public class RswDragonFightService {
    private static final Logger LOGGER = LoggerFactory.getLogger("GoldClaim/RswDragonFight");
    private static final String FIGHT_FILE = "rsw_dragon_fight.dat";
    private PendingReset pending;
    private ServerWorld exitPortalChecked;

    private record PendingReset(ServerCommandSource source, ServerWorld world, long generation,
                                long started, TeleportChunkArea area) {}

    private static ServerWorld rswEnd(MinecraftServer server) {
        return server.getWorld(RegistryKey.of(RegistryKeys.WORLD,
            new Identifier(GoldClaimMod.RSW_THE_END_DIMENSION)));
    }

    int status(ServerCommandSource source) {
        if (!source.hasPermissionLevel(2)) {
            source.sendError(Text.literal("Only server operators can inspect the RSW dragon fight."));
            return 0;
        }
        ServerWorld world = rswEnd(source.getServer());
        if (world == null || world.getEnderDragonFight() == null) {
            source.sendError(Text.literal("RSW End or its dragon fight is not loaded."));
            return 0;
        }
        var data = world.getEnderDragonFight().toData();
        source.sendFeedback(() -> Text.literal("[RSW Dragon] killed=" + data.dragonKilled()
            + ", previouslyKilled=" + data.previouslyKilled() + ", scanning=" + data.needsStateScanning()
            + ", respawning=" + isRespawning(world.getEnderDragonFight()) + ", dragon=" + data.dragonUUID()
            + ", exitPortal=" + data.exitPortalLocation() + ", loadedDragons="
            + world.getAliveEnderDragons().size() + ", resetPending=" + (this.pending != null)), false);
        return 1;
    }

    int requestReset(ServerCommandSource source, RswResetService resets) {
        if (!source.hasPermissionLevel(2)) {
            source.sendError(Text.literal("Only server operators can reset the RSW dragon fight."));
            return 0;
        }
        if (this.pending != null) {
            source.sendError(Text.literal("An RSW dragon reset is already being prepared."));
            return 0;
        }
        ServerWorld world = rswEnd(source.getServer());
        String refusal = resetRefusal(world, resets);
        if (refusal != null) {
            source.sendError(Text.literal(refusal));
            return 0;
        }
        this.pending = new PendingReset(source, world, resets.generation(), System.nanoTime(),
            new TeleportChunkArea(world, 0, 0, 1));
        LOGGER.info("RSW dragon-only reset requested by {}", source.getName());
        source.sendFeedback(() -> Text.literal("Preparing RSW dragon reset. Keep all players out of RSW End. "
            + "Terrain and other worlds will not be reset; the central exit fountain will be rebuilt."), true);
        return 1;
    }

    private static String resetRefusal(ServerWorld world, RswResetService resets) {
        if (resets.isClosed()) return "RSW is resetting; dragon recovery is unavailable.";
        if (world == null || world.getEnderDragonFight() == null) return "RSW End or its dragon fight is not loaded.";
        if (!world.getPlayers().isEmpty()) return "All players must leave RSW End before resetting its dragon fight.";
        if (!world.getAliveEnderDragons().isEmpty()) return "An RSW dragon is already alive; refusing to duplicate it.";
        var data = world.getEnderDragonFight().toData();
        if (isRespawning(world.getEnderDragonFight())) return "An RSW dragon respawn is already in progress.";
        if (!data.dragonKilled() && data.dragonUUID().isPresent()) {
            return "The RSW fight still tracks a living dragon, which may be unloaded; refusing to duplicate it.";
        }
        return null;
    }

    private static boolean isRespawning(EnderDragonFight fight) {
        // Vanilla 1.20.1 toData() always serializes isRespawning=false.
        return ((EnderDragonFightInvoker)fight).goldclaim$getDragonSpawnState() != null;
    }

    private void relocateBuriedExitPortal(MinecraftServer server, RswResetService resets) {
        ServerWorld world = rswEnd(server);
        if (world == null || world == this.exitPortalChecked || resets.isClosed()) return;
        EnderDragonFight fight = world.getEnderDragonFight();
        if (fight == null) return;
        this.exitPortalChecked = world;
        var data = fight.toData();
        // Only an already-open portal needs moving now; a closed one is placed on the surface when the dragon dies.
        if (!data.dragonKilled() || data.exitPortalLocation().isEmpty() || isRespawning(fight)) return;
        BlockPos portal = data.exitPortalLocation().get();
        if (!ExitPortalPlacement.isBuried(world, portal)) return;
        ((EnderDragonFightInvoker)fight).goldclaim$generateEndPortal(true);
        saveFightData(world, fight.toData());
        LOGGER.info("RSW End exit portal was buried at {}; now at {}", portal, fight.toData().exitPortalLocation().orElse(null));
    }

    void tick(MinecraftServer server, RswResetService resets) {
        try {
            relocateBuriedExitPortal(server, resets);
        } catch (RuntimeException failure) {
            LOGGER.error("Failed to check the RSW End exit portal", failure);
        }
        PendingReset request = this.pending;
        if (request == null) return;
        boolean finished = true;
        try {
            if (rswEnd(server) != request.world() || resets.generation() != request.generation()) {
                throw new IllegalStateException("RSW End changed while preparing the dragon reset.");
            }
            String refusal = resetRefusal(request.world(), resets);
            if (refusal != null) throw new IllegalStateException(refusal);
            if (System.nanoTime() - request.started() > 120_000_000_000L) {
                throw new IllegalStateException("RSW dragon reset terrain loading timed out.");
            }
            if (!request.area().ready()) {
                finished = false;
                return;
            }
            ServerWorld world = request.world();
            var oldData = world.getEnderDragonFight().toData();
            Path directory = getFightFile(world).toPath().getParent();
            Files.createDirectories(directory);
            Path backup = Files.createTempFile(directory,
                "rsw_dragon_fight-before-reset-", ".dat");
            writeFightData(backup.toFile(), oldData);
            var data = new EnderDragonFight.Data(false, false, oldData.previouslyKilled(), false,
                Optional.empty(), Optional.empty(), oldData.gateways());
            var fight = new EnderDragonFight(world, world.getSeed(), data, BlockPos.ORIGIN);
            // A legacy scan can mistake unrelated portal blocks for a completed fight.
            ((EnderDragonFightInvoker)fight).goldclaim$generateEndPortal(false);
            writeFightData(getFightFile(world), fight.toData());
            world.setEnderDragonFight(fight);
            LOGGER.info("RSW dragon fight reset by {}; previous fight saved to {}", request.source().getName(), backup);
            request.source().sendFeedback(() -> Text.literal("RSW dragon fight reset and saved. Enter the central "
                + "island in non-spectator mode to spawn the dragon. Exit fountain rebuilt; terrain, gateway "
                + "progress and other worlds preserved. Fight backup: " + backup.getFileName()), true);
        } catch (IOException | CompletionException | IllegalStateException failure) {
            LOGGER.error("RSW dragon-only reset failed", failure);
            request.source().sendError(Text.literal("RSW dragon reset failed: " + failure.getMessage()
                + ". Check the server log before retrying; the fountain may already have changed."));
        } finally {
            if (finished) {
                request.area().close();
                this.pending = null;
            }
        }
    }

    public static void ensureDragonFight(ServerWorld world) {
        if (world == null || !GoldClaimMod.RSW_THE_END_DIMENSION.equals(world.getRegistryKey().getValue().toString())) {
            return;
        }
        if (world.getEnderDragonFight() != null) {
            return;
        }
        try {
            File file = getFightFile(world);
            NbtCompound nbt = null;
            if (file.exists()) {
                try {
                    nbt = NbtIo.readCompressed(file);
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to read RSW dragon fight data from " + file, e);
                }
            }
            EnderDragonFight.Data data = EnderDragonFight.Data.DEFAULT;
            if (nbt != null && nbt.contains("DragonFight")) {
                var parseResult = EnderDragonFight.Data.CODEC.parse(NbtOps.INSTANCE, nbt.get("DragonFight"));
                data = parseResult.getOrThrow(false, message ->
                    LOGGER.error("Invalid RSW dragon fight data in {}: {}", file, message));
            }
            EnderDragonFight fight = new EnderDragonFight(world, world.getSeed(), data, BlockPos.ORIGIN);
            world.setEnderDragonFight(fight);
            LOGGER.info("Initialized RSW EnderDragonFight for dimension {}", world.getRegistryKey().getValue());
        } catch (RuntimeException failure) {
            throw new IllegalStateException("Failed to initialize RSW EnderDragonFight", failure);
        }
    }

    private static File getFightFile(ServerWorld world) {
        Path dimDir = world.getServer().getSavePath(WorldSavePath.ROOT)
            .resolve("dimensions").resolve("multiworld").resolve("rsw_the_end");
        return dimDir.resolve(FIGHT_FILE).toFile();
    }

    public static void saveFightData(ServerWorld world, EnderDragonFight.Data data) {
        if (data == null) return;
        File file = getFightFile(world);
        try {
            writeFightData(file, data);
        } catch (IOException | IllegalStateException e) {
            LOGGER.error("Failed to save RSW dragon fight data to {}", file, e);
        }
    }

    private static void writeFightData(File file, EnderDragonFight.Data data) throws IOException {
        Path path = file.toPath();
        Files.createDirectories(path.getParent());
        NbtCompound nbt = new NbtCompound();
        var encoded = EnderDragonFight.Data.CODEC.encodeStart(NbtOps.INSTANCE, data);
        nbt.put("DragonFight", encoded.getOrThrow(false, message ->
            LOGGER.error("Cannot encode RSW dragon fight data: {}", message)));
        Path temporary = Files.createTempFile(path.getParent(), "rsw-dragon-save-", ".tmp");
        try {
            NbtIo.writeCompressed(nbt, temporary.toFile());
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
