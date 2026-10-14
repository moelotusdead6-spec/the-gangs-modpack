package com.elysium.goldclaim.maintenance;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.nbt.BinaryTag;
import net.kyori.adventure.nbt.BinaryTagIO;
import net.kyori.adventure.nbt.BinaryTagTypes;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.IntArrayBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;
import net.kyori.adventure.nbt.StringBinaryTag;

public final class LegacyTeleportPurge {
    private record Change(Path path, byte[] bytes) {}
    private final Path game;
    private final Path world;
    private final List<Change> changes = new ArrayList<>();
    private final Set<Path> protectedFiles = new HashSet<>();
    private int removedStones;
    private int removedGoldClaimHomes;

    private LegacyTeleportPurge(Path game) throws IOException {
        this.game = game.toRealPath();
        var properties = new java.util.Properties();
        try (var reader = Files.newBufferedReader(this.game.resolve("server.properties"))) {
            properties.load(reader);
        }
        Path relative = Path.of(properties.getProperty("level-name", "world"));
        if (relative.isAbsolute() || relative.normalize().startsWith("..")) {
            throw new IOException("World must be inside the server directory.");
        }
        world = this.game.resolve(relative).normalize();
        checkPath(world);
    }

    public static boolean closed(String dimension) {
        return dimension.equals("minecraft:the_nether") || dimension.equals("minecraft:the_end");
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2 || args.length > 3
                || !(args[0].equals("--dry-run") || args[0].equals("--apply"))
                || (args[0].equals("--apply") && args.length != 3)) {
            throw new IllegalArgumentException("Usage: --dry-run SERVER or --apply SERVER BACKUP_DIRECTORY");
        }
        LegacyTeleportPurge purge = new LegacyTeleportPurge(Path.of(args[1]));
        purge.plan();
        System.out.println("Legacy teleport purge: " + purge.changes.size()
            + " changed files; " + purge.protectedFiles.size() + " original files to protect.");
        System.out.println("Original-dimension Waystones removed: " + purge.removedStones
            + "; GoldClaim homes removed: " + purge.removedGoldClaimHomes + ".");
        for (Change change : purge.changes) {
            System.out.println("Change: " + purge.game.relativize(change.path));
        }
        if (args[0].equals("--apply")) {
            Path backup = Path.of(args[2]).toAbsolutePath().normalize();
            Path backups = purge.game.resolve("backups");
            if (!backup.startsWith(backups) || backup.equals(backups)) {
                throw new IOException("Backup must be a generation within the server backups directory.");
            }
            purge.checkPath(backup);
            for (Path source : purge.protectedFiles) {
                Path target = backup.resolve(purge.game.relativize(source));
                purge.checkPath(target);
                if (!Files.exists(target)) {
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target);
                } else if (Files.mismatch(source, target) != -1) {
                    throw new IOException("Existing backup differs; refusing to overwrite " + target);
                }
            }
            for (Change change : purge.changes) {
                purge.checkPath(change.path);
                Files.createDirectories(change.path.getParent());
                Path temporary = change.path.resolveSibling(change.path.getFileName() + ".teleport-purge.tmp");
                Files.write(temporary, change.bytes, java.nio.file.StandardOpenOption.CREATE_NEW);
                Files.move(temporary, change.path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
            LegacyTeleportPurge verify = new LegacyTeleportPurge(purge.game);
            verify.plan();
            if (!verify.changes.isEmpty()) throw new IOException("Legacy teleport purge verification failed.");
            System.out.println("Verified: no saved legacy homes, warps, return locations or activated Waystone IDs remain.");
        }
    }

    private void plan() throws IOException {
        Set<String> removedIds = new HashSet<>();
        Path registry = world.resolve("data").resolve("waystones.dat");
        if (Files.exists(registry)) {
            CompoundBinaryTag original = read(registry);
            if (!(original.get("data") instanceof CompoundBinaryTag)) {
                throw new IOException("Invalid Waystones registry data.");
            }
            CompoundBinaryTag data = original.getCompound("data");
            if (!(data.get("Waystones") instanceof ListBinaryTag list)
                    || (list.size() > 0 && list.elementType() != BinaryTagTypes.COMPOUND)) {
                throw new IOException("Invalid Waystones registry list.");
            }
            ListBinaryTag stones = data.getList("Waystones", BinaryTagTypes.COMPOUND);
            var keep = new ArrayList<BinaryTag>();
            var removed = new ArrayList<BinaryTag>();
            for (BinaryTag entry : stones) {
                CompoundBinaryTag stone = (CompoundBinaryTag) entry;
                if (closed(stone.getString("World"))) {
                    removedStones++;
                    removed.add(stone.putBoolean("GoldClaimPhysicalCleanup", hasStoredChunk(stone)));
                    removedIds.add(uuid(stone.get("WaystoneUid")));
                    protectRegions(stone);
                } else {
                    keep.add(stone);
                }
            }
            if (!removed.isEmpty()) {
                Path pendingPath = world.resolve("data").resolve("goldclaim-legacy-waystones.dat");
                CompoundBinaryTag pending = Files.exists(pendingPath) ? read(pendingPath) : CompoundBinaryTag.empty();
                var pendingStones = new ArrayList<BinaryTag>();
                pending.getList("Waystones", BinaryTagTypes.COMPOUND).forEach(pendingStones::add);
                pendingStones.addAll(removed);
                stage(pendingPath, pending.put("Waystones",
                    ListBinaryTag.of(BinaryTagTypes.COMPOUND, pendingStones)));
                stage(registry, original.put("data",
                    data.put("Waystones", ListBinaryTag.of(BinaryTagTypes.COMPOUND, keep))));
            }
        }
        // A resumed installation must also remove IDs recorded in its pending physical cleanup.
        Path pending = world.resolve("data").resolve("goldclaim-legacy-waystones.dat");
        if (Files.exists(pending)) {
            for (BinaryTag tag : read(pending).getList("Waystones", BinaryTagTypes.COMPOUND)) {
                CompoundBinaryTag stone = (CompoundBinaryTag) tag;
                if (!closed(stone.getString("World"))) throw new IOException("Unexpected dimension in Waystone cleanup plan.");
                removedIds.add(uuid(stone.get("WaystoneUid")));
            }
        }
        for (String folder : List.of("playerdata", "modplayerdata", "ec_player_profiles")) {
            Path directory = world.resolve(folder);
            checkPath(directory);
            if (!Files.isDirectory(directory)) continue;
            try (var files = Files.list(directory)) {
                for (Path file : files.filter(p -> p.getFileName().toString().matches(".*\\.dat(?:_old)?")).toList()) {
                    CompoundBinaryTag original = read(file);
                    CompoundBinaryTag updated = folder.equals("playerdata")
                        ? cleanPlayer(original, removedIds) : cleanLocations(original);
                    if (!original.equals(updated)) stage(file, updated);
                }
            }
        }
        Path ecWorld = world.resolve("essentialcommands").resolve("world_data.dat");
        if (Files.exists(ecWorld)) {
            CompoundBinaryTag original = read(ecWorld);
            CompoundBinaryTag updated = cleanLocations(original);
            if (!original.equals(updated)) stage(ecWorld, updated);
        }
        for (String file : List.of("player-homes.json", "last-wild-locations.json")) {
            Path path = game.resolve("config").resolve("goldclaim").resolve(file);
            if (!Files.exists(path)) continue;
            checkPath(path);
            JsonElement original = JsonParser.parseString(Files.readString(path));
            JsonElement updated = original.deepCopy();
            cleanJson(updated);
            if (file.equals("player-homes.json") && original.isJsonArray() && updated.isJsonArray()) {
                removedGoldClaimHomes = original.getAsJsonArray().size() - updated.getAsJsonArray().size();
            }
            if (!original.equals(updated)) {
                addChange(path, new GsonBuilder().setPrettyPrinting().create().toJson(updated).concat("\n")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        }
    }

    public static CompoundBinaryTag cleanPlayer(CompoundBinaryTag original, Set<String> removedIds) {
        CompoundBinaryTag updated = cleanActivated(original, removedIds);
        if (closed(updated.getString("SpawnDimension"))) {
            for (String key : List.of("SpawnDimension", "SpawnX", "SpawnY", "SpawnZ", "SpawnAngle", "SpawnForced")) {
                updated = updated.remove(key);
            }
        }
        if (closed(updated.getCompound("LastDeathLocation").getString("dimension"))) {
            updated = updated.remove("LastDeathLocation");
        }
        return updated;
    }

    private static CompoundBinaryTag cleanActivated(CompoundBinaryTag original, Set<String> removedIds) {
        CompoundBinaryTag updated = original;
        for (var entry : original) {
            if (entry.getValue() instanceof CompoundBinaryTag child) {
                if (entry.getKey().equals("WaystonesData")) {
                    ListBinaryTag activated = child.getList("Waystones", BinaryTagTypes.STRING);
                    var keep = new ArrayList<BinaryTag>();
                    for (BinaryTag id : activated) {
                        if (!removedIds.contains(((StringBinaryTag) id).value())) keep.add(id);
                    }
                    if (keep.size() != activated.size()) {
                        updated = updated.put(entry.getKey(), child.put("Waystones",
                            ListBinaryTag.of(BinaryTagTypes.STRING, keep)));
                    }
                } else {
                    CompoundBinaryTag cleaned = cleanActivated(child, removedIds);
                    if (!child.equals(cleaned)) updated = updated.put(entry.getKey(), cleaned);
                }
            } else if (entry.getValue() instanceof ListBinaryTag list) {
                var keep = new ArrayList<BinaryTag>();
                boolean changed = false;
                for (BinaryTag item : list) {
                    BinaryTag cleaned = item instanceof CompoundBinaryTag compound
                        ? cleanActivated(compound, removedIds) : item;
                    keep.add(cleaned);
                    changed |= !item.equals(cleaned);
                }
                if (changed) updated = updated.put(entry.getKey(), ListBinaryTag.of(list.elementType(), keep));
            }
        }
        return updated;
    }

    public static CompoundBinaryTag cleanLocations(CompoundBinaryTag original) {
        CompoundBinaryTag updated = original;
        for (var entry : original) {
            if (entry.getValue() instanceof CompoundBinaryTag child) {
                if (closed(child.getString("WorldRegistryKey"))) updated = updated.remove(entry.getKey());
                else updated = updated.put(entry.getKey(), cleanLocations(child));
            } else if (entry.getValue() instanceof ListBinaryTag list) {
                var keep = new ArrayList<BinaryTag>();
                boolean changed = false;
                for (BinaryTag item : list) {
                    if (item instanceof CompoundBinaryTag compound && closed(compound.getString("WorldRegistryKey"))) {
                        changed = true;
                        continue;
                    }
                    BinaryTag cleaned = item instanceof CompoundBinaryTag compound ? cleanLocations(compound) : item;
                    keep.add(cleaned);
                    changed |= !item.equals(cleaned);
                }
                if (changed) updated = updated.put(entry.getKey(), ListBinaryTag.of(list.elementType(), keep));
            }
        }
        return updated;
    }

    public static void cleanJson(JsonElement element) {
        if (element.isJsonArray()) {
            var iterator = element.getAsJsonArray().iterator();
            while (iterator.hasNext()) {
                JsonElement entry = iterator.next();
                if (legacyJsonLocation(entry)) iterator.remove();
                else cleanJson(entry);
            }
        } else if (element.isJsonObject()) {
            var iterator = element.getAsJsonObject().entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                if (legacyJsonLocation(entry.getValue())) iterator.remove();
                else cleanJson(entry.getValue());
            }
        }
    }

    private static boolean legacyJsonLocation(JsonElement entry) {
        return entry.isJsonObject() && entry.getAsJsonObject().has("worldId")
            && closed(entry.getAsJsonObject().get("worldId").getAsString());
    }

    private static String uuid(BinaryTag tag) throws IOException {
        if (tag instanceof StringBinaryTag string) return UUID.fromString(string.value()).toString();
        if (!(tag instanceof IntArrayBinaryTag array) || array.value().length != 4) {
            throw new IOException("Invalid Waystone UUID; refusing partial cleanup.");
        }
        int[] parts = array.value();
        return new UUID((long) parts[0] << 32 | parts[1] & 0xffffffffL,
            (long) parts[2] << 32 | parts[3] & 0xffffffffL).toString();
    }

    private void protectRegions(CompoundBinaryTag stone) throws IOException {
        CompoundBinaryTag pos = stone.getCompound("BlockPos");
        if (!pos.keySet().containsAll(Set.of("X", "Y", "Z"))) throw new IOException("Invalid Waystone block position.");
        Path dimension = world.resolve(stone.getString("World").equals("minecraft:the_nether") ? "DIM-1" : "DIM1");
        for (String folder : List.of("region", "entities", "poi")) {
            for (String name : List.of("r." + (pos.getInt("X") >> 9) + "." + (pos.getInt("Z") >> 9) + ".mca",
                    "c." + (pos.getInt("X") >> 4) + "." + (pos.getInt("Z") >> 4) + ".mcc")) {
                Path file = dimension.resolve(folder).resolve(name);
                checkPath(file);
                if (Files.isRegularFile(file)) protectedFiles.add(file);
            }
        }
    }

    private boolean hasStoredChunk(CompoundBinaryTag stone) throws IOException {
        CompoundBinaryTag pos = stone.getCompound("BlockPos");
        if (!pos.keySet().containsAll(Set.of("X", "Y", "Z"))) throw new IOException("Invalid Waystone block position.");
        Path region = world.resolve(stone.getString("World").equals("minecraft:the_nether") ? "DIM-1" : "DIM1")
            .resolve("region").resolve("r." + (pos.getInt("X") >> 9) + "." + (pos.getInt("Z") >> 9) + ".mca");
        checkPath(region);
        if (!Files.exists(region)) return false;
        try (var file = new java.io.RandomAccessFile(region.toFile(), "r")) {
            if (file.length() < 8192) throw new IOException("Invalid region header: " + region);
            int slot = ((pos.getInt("X") >> 4) & 31) + (((pos.getInt("Z") >> 4) & 31) * 32);
            file.seek(slot * 4L);
            int location = file.readInt();
            if (location != 0 && ((location >>> 8) < 2 || (location & 255) == 0
                    || ((long) (location >>> 8) + (location & 255)) * 4096 > file.length())) {
                // Minecraft discards such a slot as having no saved chunk, so no physical block remains to clean up.
                System.out.println("Ignoring invalid chunk slot " + slot + " in " + region
                    + "; Minecraft treats it as an unsaved chunk.");
                return false;
            }
            return location != 0;
        }
    }

    private CompoundBinaryTag read(Path path) throws IOException {
        checkPath(path);
        return BinaryTagIO.reader(64 * 1024 * 1024).read(path, BinaryTagIO.Compression.GZIP);
    }

    private void stage(Path path, CompoundBinaryTag tag) throws IOException {
        var bytes = new java.io.ByteArrayOutputStream();
        BinaryTagIO.writer().write(tag, bytes, BinaryTagIO.Compression.GZIP);
        addChange(path, bytes.toByteArray());
    }

    private void addChange(Path path, byte[] bytes) throws IOException {
        checkPath(path);
        if (Files.isRegularFile(path)) protectedFiles.add(path);
        changes.add(new Change(path, bytes));
    }

    private void checkPath(Path path) throws IOException {
        if (!path.toAbsolutePath().normalize().startsWith(game)) throw new IOException("Path leaves server: " + path);
        for (Path current = path; current != null && current.startsWith(game); current = current.getParent()) {
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)
                    && !current.toAbsolutePath().normalize().equals(current.toRealPath())) {
                throw new IOException("Linked paths are not supported: " + current);
            }
            if (Files.isSymbolicLink(current)) {
                throw new IOException("Linked paths are not supported: " + current);
            }
        }
    }
}
