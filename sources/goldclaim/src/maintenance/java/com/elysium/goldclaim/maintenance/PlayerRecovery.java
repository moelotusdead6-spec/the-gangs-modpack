package com.elysium.goldclaim.maintenance;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipFile;
import net.kyori.adventure.nbt.BinaryTag;
import net.kyori.adventure.nbt.BinaryTagIO;
import net.kyori.adventure.nbt.BinaryTagTypes;
import net.kyori.adventure.nbt.ByteBinaryTag;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.IntArrayBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;

/**
 * Offline, one-time restoration of items a player lost to a bug, copied byte-for-byte from a pinned
 * pre-loss playerdata snapshot and delivered as named shulker boxes in free Ender Chest slots.
 */
public final class PlayerRecovery {
    public static final int EXIT_SOURCE_MISSING = 3;
    public static final int EXIT_NO_SPACE = 4;

    /** A stack in the snapshot: Inventory slot, or a Trinkets group/slot/index. */
    public record Pick(String group, String slot, int index, String id) {
        static Pick inventory(int slot, String id) { return new Pick(null, null, slot, id); }
        static Pick trinket(String group, String slot, int index, String id) { return new Pick(group, slot, index, id); }
    }

    public record Plan(String name, String uuid, String sourceSha256, String marker, List<Pick> picks) {}

    public static final Plan KUNOISBACK = new Plan("Kunoisback", "07337021-ea67-4593-b127-73f4261b1134",
        "52F57AED91C0EDCB789D5B7BA887320BB827CC1C391BB3BD8DB3628DAE7B1A01", "kunoisback-20261014.done", List.of(
            Pick.inventory(0, "minecraft:water_bucket"),
            Pick.inventory(1, "simplymore:gobber_nether_grandsword"),
            Pick.inventory(2, "gobber2:gobber2_bow_nether"),
            Pick.inventory(4, "mythicmetals:carmot_staff"),
            Pick.inventory(6, "simplyswords:gobber_compat/gobber_nether/gobber_nether_claymore"),
            Pick.inventory(7, "minecraft:diamond_pickaxe"),
            Pick.inventory(8, "alexscaves:cooked_dinosaur_chop"),
            Pick.inventory(9, "paladins:netherite_kite_shield"),
            Pick.inventory(10, "sophisticatedbackpacks:netherite_backpack"),
            Pick.inventory(11, "sophisticatedbackpacks:netherite_backpack"),
            Pick.inventory(12, "sophisticatedbackpacks:netherite_backpack"),
            Pick.inventory(13, "sophisticatedbackpacks:netherite_backpack"),
            Pick.inventory(14, "mythicmetals:mythril_drill"),
            Pick.inventory(16, "gobber2:gobber2_ring_vision"),
            Pick.inventory(17, "gobber2:gobber2_ring_luck"),
            Pick.inventory(23, "alexscaves:tectonic_shard"),
            Pick.inventory(26, "gobber2:gobber2_ring_phoenix"),
            Pick.inventory(27, "gobber2:gobber2_sword_nether"),
            Pick.inventory(28, "gobber2:gobber2_boots"),
            Pick.inventory(29, "minecraft:stick"),
            Pick.inventory(33, "minecraft:wheat_seeds"),
            Pick.inventory(34, "minecraft:glow_ink_sac"),
            Pick.inventory(35, "gobber2:gobber2_ring_above"),
            Pick.inventory(101, "gobber2:gobber2_leggings"),
            Pick.inventory(102, "gobber2:gobber2_chestplate"),
            Pick.inventory(103, "gobber2:gobber2_helmet"),
            Pick.inventory(-106, "gobber2:gobber2_ring_strength"),
            Pick.trinket("chest", "back", 0, "sophisticatedbackpacks:netherite_backpack")));

    private PlayerRecovery() {}

    public static void main(String[] args) throws Exception {
        boolean apply = args.length == 3 && args[0].equals("--apply");
        if (!apply && !(args.length == 2 && args[0].equals("--dry-run"))) {
            throw new IllegalArgumentException("Usage: --dry-run SERVER or --apply SERVER BACKUP_DIRECTORY");
        }
        System.exit(run(KUNOISBACK, Path.of(args[1]), apply ? Path.of(args[2]) : null));
    }

    public static int run(Plan plan, Path server, Path backup) throws IOException {
        Path game = server.toRealPath();
        Path world = world(game);
        Path marker = world.resolve("goldclaim-recovery").resolve(plan.marker());
        checkPath(game, marker);
        if (Files.exists(marker)) {
            System.out.println(plan.name() + " recovery already applied; nothing to do.");
            return 0;
        }
        Path target = world.resolve("playerdata").resolve(plan.uuid() + ".dat");
        checkPath(game, target);
        if (!Files.isRegularFile(target)) throw new IOException("Player data not found: " + target);
        byte[] source = findSource(plan, game);
        if (source == null) {
            System.out.println("Pinned pre-loss snapshot for " + plan.name() + " was not found; recovery skipped.");
            return EXIT_SOURCE_MISSING;
        }
        CompoundBinaryTag snapshot = read(source);
        CompoundBinaryTag current = read(Files.readAllBytes(target));
        if (!plan.uuid().equals(uuid(snapshot)) || !plan.uuid().equals(uuid(current))) {
            throw new IOException("Player UUID mismatch; refusing recovery.");
        }
        String boxPrefix = plan.name() + " recovery ";
        if (containsNamed(current, boxPrefix)) {
            System.out.println(plan.name() + " already holds recovery boxes; marking as applied.");
            if (backup != null) writeMarker(game, marker);
            return 0;
        }
        Set<String> otherBackpacks = backpacksOutside(game, world, target);
        List<CompoundBinaryTag> items = new ArrayList<>();
        for (Pick pick : plan.picks()) {
            CompoundBinaryTag stack = find(snapshot, pick);
            String backpack = backpackId(stack);
            if (backpack != null && (otherBackpacks.contains(backpack) || backpacks(current).contains(backpack))) {
                System.out.println("Skipped " + pick.id() + ": its backpack storage is already held elsewhere.");
                continue;
            }
            items.add(stack.remove("Slot"));
        }
        List<CompoundBinaryTag> boxes = boxes(items, boxPrefix);
        CompoundBinaryTag updated = place(current, boxes);
        if (updated == null) {
            System.out.println("Not enough free Ender Chest or inventory slots for " + boxes.size() + " recovery boxes.");
            return EXIT_NO_SPACE;
        }
        System.out.println(plan.name() + " recovery: " + items.size() + " stacks in " + boxes.size() + " shulker boxes.");
        if (backup == null) return 0;

        Path backups = game.resolve("backups");
        Path generation = backup.toAbsolutePath().normalize();
        if (!generation.startsWith(backups) || generation.equals(backups)) {
            throw new IOException("Backup must be a generation within the server backups directory.");
        }
        checkPath(game, generation);
        try (FileChannel lockChannel = FileChannel.open(world.resolve("session.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock lock = lockChannel.tryLock()) {
            if (lock == null) throw new IOException("World is locked; stop the server before recovery.");
            Path saved = generation.resolve("player-recovery");
            Files.createDirectories(saved);
            Files.copy(target, saved.resolve(plan.uuid() + ".dat"), StandardCopyOption.REPLACE_EXISTING);
            Path old = target.resolveSibling(plan.uuid() + ".dat_old");
            if (Files.isRegularFile(old)) {
                Files.copy(old, saved.resolve(plan.uuid() + ".dat_old"), StandardCopyOption.REPLACE_EXISTING);
            }
            Files.write(saved.resolve(plan.uuid() + ".source.dat"), source);
            Path temporary = target.resolveSibling(plan.uuid() + ".dat.recovery.tmp");
            Files.deleteIfExists(temporary);
            Files.write(temporary, write(updated), StandardOpenOption.CREATE_NEW);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
        CompoundBinaryTag verify = read(Files.readAllBytes(target));
        if (!verify.equals(updated) || !onlyContainerChanged(current, verify)) {
            throw new IOException("Recovery verification failed; original is in " + generation.resolve("player-recovery"));
        }
        writeMarker(game, marker);
        System.out.println("Verified: recovery boxes delivered; all other player data unchanged.");
        return 0;
    }

    public static CompoundBinaryTag find(CompoundBinaryTag snapshot, Pick pick) throws IOException {
        CompoundBinaryTag stack = null;
        if (pick.group() == null) {
            for (BinaryTag tag : snapshot.getList("Inventory", BinaryTagTypes.COMPOUND)) {
                if (((CompoundBinaryTag) tag).getByte("Slot") == (byte) pick.index()) stack = (CompoundBinaryTag) tag;
            }
        } else {
            ListBinaryTag list = snapshot.getCompound("cardinal_components").getCompound("trinkets:trinkets")
                .getCompound(pick.group()).getCompound(pick.slot()).getList("Items", BinaryTagTypes.COMPOUND);
            if (pick.index() < list.size()) stack = list.getCompound(pick.index());
        }
        if (stack == null || !pick.id().equals(stack.getString("id"))) {
            throw new IOException("Snapshot does not match the recovery plan at " + pick);
        }
        return stack;
    }

    public static List<CompoundBinaryTag> boxes(List<CompoundBinaryTag> items, String prefix) {
        int count = Math.max(1, (items.size() + 26) / 27);
        List<CompoundBinaryTag> boxes = new ArrayList<>();
        for (int box = 0, from = 0; box < count; box++) {
            int size = (items.size() - from + (count - box) - 1) / (count - box);
            List<BinaryTag> contents = new ArrayList<>();
            for (int i = 0; i < size; i++) contents.add(items.get(from + i).putByte("Slot", (byte) i));
            from += size;
            String name = "{\"text\":\"" + prefix + (box + 1) + "/" + count + "\",\"italic\":false}";
            boxes.add(CompoundBinaryTag.builder().putString("id", "minecraft:shulker_box").putByte("Count", (byte) 1)
                .put("tag", CompoundBinaryTag.builder()
                    .put("BlockEntityTag", CompoundBinaryTag.builder().putString("id", "minecraft:shulker_box")
                        .put("Items", ListBinaryTag.of(BinaryTagTypes.COMPOUND, contents)).build())
                    .put("display", CompoundBinaryTag.builder().putString("Name", name).build()).build())
                .build());
        }
        return boxes;
    }

    /** Places every box into free Ender Chest slots, else free main-inventory slots; null if neither fits all. */
    public static CompoundBinaryTag place(CompoundBinaryTag player, List<CompoundBinaryTag> boxes) {
        for (String key : List.of("EnderItems", "Inventory")) {
            int limit = key.equals("EnderItems") ? 27 : 36;
            ListBinaryTag list = player.getList(key, BinaryTagTypes.COMPOUND);
            Set<Integer> used = new HashSet<>();
            for (BinaryTag tag : list) used.add((int) ((CompoundBinaryTag) tag).getByte("Slot"));
            List<BinaryTag> merged = new ArrayList<>();
            list.forEach(merged::add);
            int next = 0;
            for (CompoundBinaryTag box : boxes) {
                while (next < limit && used.contains(next)) next++;
                if (next >= limit) break;
                merged.add(box.putByte("Slot", (byte) next));
                used.add(next);
            }
            if (merged.size() == list.size() + boxes.size()) {
                return player.put(key, ListBinaryTag.of(BinaryTagTypes.COMPOUND, merged));
            }
        }
        return null;
    }

    public static boolean onlyContainerChanged(CompoundBinaryTag before, CompoundBinaryTag after) {
        Set<String> keys = new HashSet<>(before.keySet());
        keys.addAll(after.keySet());
        int changed = 0;
        for (String key : keys) {
            if (before.get(key) == null ? after.get(key) != null : !before.get(key).equals(after.get(key))) {
                if (!key.equals("EnderItems") && !key.equals("Inventory")) return false;
                ListBinaryTag oldList = before.getList(key, BinaryTagTypes.COMPOUND);
                ListBinaryTag newList = after.getList(key, BinaryTagTypes.COMPOUND);
                for (int i = 0; i < oldList.size(); i++) {
                    if (i >= newList.size() || !oldList.get(i).equals(newList.get(i))) return false;
                }
                changed++;
            }
        }
        return changed == 1;
    }

    public static boolean containsNamed(BinaryTag tag, String prefix) {
        if (tag instanceof CompoundBinaryTag compound) {
            if (compound.getCompound("display").getString("Name").contains(prefix)) return true;
            for (var entry : compound) if (containsNamed(entry.getValue(), prefix)) return true;
        } else if (tag instanceof ListBinaryTag list) {
            for (BinaryTag item : list) if (containsNamed(item, prefix)) return true;
        }
        return false;
    }

    public static Set<String> backpacks(BinaryTag tag) {
        Set<String> found = new HashSet<>();
        collectBackpacks(tag, found);
        return found;
    }

    private static void collectBackpacks(BinaryTag tag, Set<String> found) {
        if (tag instanceof CompoundBinaryTag compound) {
            String id = backpackId(compound);
            if (id != null) found.add(id);
            for (var entry : compound) collectBackpacks(entry.getValue(), found);
        } else if (tag instanceof ListBinaryTag list) {
            for (BinaryTag item : list) collectBackpacks(item, found);
        }
    }

    private static String backpackId(CompoundBinaryTag stack) {
        return stack.getCompound("tag").get("contentsUuid") instanceof IntArrayBinaryTag array
            ? Arrays.toString(array.value()) : null;
    }

    private static Set<String> backpacksOutside(Path game, Path world, Path target) throws IOException {
        Set<String> found = new HashSet<>();
        Path directory = world.resolve("playerdata");
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".dat")).toList()) {
                if (file.equals(target)) continue;
                checkPath(game, file);
                try {
                    found.addAll(backpacks(read(Files.readAllBytes(file))));
                } catch (IOException ignored) {
                    // Unreadable data of another player cannot hold a usable backpack.
                }
            }
        }
        return found;
    }

    private static byte[] findSource(Plan plan, Path game) throws IOException {
        String suffix = "playerdata/" + plan.uuid() + ".dat";
        Path backups = game.resolve("backups");
        if (Files.isDirectory(backups)) {
            try (var files = Files.walk(backups, 6)) {
                for (Path file : files.filter(p -> p.toString().replace('\\', '/').endsWith(suffix)).sorted().toList()) {
                    byte[] bytes = Files.readAllBytes(file);
                    if (sha256(bytes).equals(plan.sourceSha256())) return bytes;
                }
            }
        }
        Path archives = game.getParent() == null ? null : game.getParent().resolve("Backups");
        if (archives != null && Files.isDirectory(archives)) {
            try (var files = Files.list(archives)) {
                for (Path zip : files.filter(p -> p.toString().toLowerCase().endsWith(".zip")).sorted().toList()) {
                    try (ZipFile archive = new ZipFile(zip.toFile())) {
                        for (var entry : archive.stream().filter(e -> e.getName().replace('\\', '/').endsWith(suffix)).toList()) {
                            try (InputStream in = archive.getInputStream(entry)) {
                                byte[] bytes = in.readAllBytes();
                                if (sha256(bytes).equals(plan.sourceSha256())) return bytes;
                            }
                        }
                    } catch (IOException ignored) {
                        // Skip unreadable archives; other locations may still hold the snapshot.
                    }
                }
            }
        }
        return null;
    }

    private static Path world(Path game) throws IOException {
        var properties = new java.util.Properties();
        try (var reader = Files.newBufferedReader(game.resolve("server.properties"))) {
            properties.load(reader);
        }
        Path relative = Path.of(properties.getProperty("level-name", "world"));
        if (relative.isAbsolute() || relative.normalize().startsWith("..")) {
            throw new IOException("World must be inside the server directory.");
        }
        Path world = game.resolve(relative).normalize();
        checkPath(game, world);
        return world;
    }

    private static void writeMarker(Path game, Path marker) throws IOException {
        checkPath(game, marker);
        Files.createDirectories(marker.getParent());
        Files.writeString(marker, "applied " + java.time.Instant.now() + System.lineSeparator());
    }

    private static String uuid(CompoundBinaryTag player) {
        if (!(player.get("UUID") instanceof IntArrayBinaryTag array) || array.value().length != 4) return null;
        int[] parts = array.value();
        return new java.util.UUID((long) parts[0] << 32 | parts[1] & 0xffffffffL,
            (long) parts[2] << 32 | parts[3] & 0xffffffffL).toString();
    }

    public static CompoundBinaryTag read(byte[] bytes) throws IOException {
        return BinaryTagIO.reader(64 * 1024 * 1024).read(new java.io.ByteArrayInputStream(bytes), BinaryTagIO.Compression.GZIP);
    }

    public static byte[] write(CompoundBinaryTag tag) throws IOException {
        var bytes = new ByteArrayOutputStream();
        BinaryTagIO.writer().write(tag, bytes, BinaryTagIO.Compression.GZIP);
        return bytes.toByteArray();
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void checkPath(Path game, Path path) throws IOException {
        if (!path.toAbsolutePath().normalize().startsWith(game)) throw new IOException("Path leaves server: " + path);
        for (Path current = path; current != null && current.startsWith(game); current = current.getParent()) {
            if (Files.isSymbolicLink(current) || (Files.exists(current, LinkOption.NOFOLLOW_LINKS)
                    && !current.toAbsolutePath().normalize().equals(current.toRealPath()))) {
                throw new IOException("Linked paths are not supported: " + current);
            }
        }
    }
}
