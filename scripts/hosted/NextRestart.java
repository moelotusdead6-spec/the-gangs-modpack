import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.jar.JarFile;

public final class NextRestart {
    private static final String LAUNCHER_HASH =
        "6f921315fe2f570c331ca877d1e815c29b93fdc4ad04ce0f0f5cdf4300710028";
    private static final String CHUNKY_HASH =
        "ae7fb9d35a3a9d9d4f2104aeae7b31a901c5e5842891e2edd9ddcc42c2646a38";
    private static final List<Update> UPDATES = List.of(
        new Update("goldclaim", "1.0.48", "1.0.49",
            "e5aa5123210110f23d06079936706306ca5212b304c23be061c9c5c6e852b9f3",
            "d48f42f137b7474ad470e16c52cb7da1f2ab5dfe3f5c7f0fad063f0bc95fdcd3"),
        new Update("gangsales", "1.0.0", "1.0.1",
            "61b92c0a8184b8f96fa53540b2205bc37fdae4e2d68bb98fdd3f8a5e7d773087",
            "e71ca9a5f0894dd389c7ec9daf72fb57dd8f8fba1806511791427c124033b5dd"));
    private static final Path BACKUP = Path.of("backups", "next-restart-goldclaim-1.0.49");
    private static final String OLD_SHOP_HASH = "7c6792b15d2e91b4466f54967aa881ea6c121431624ebe28bf83d77613d2502c";
    private static final String SHOP_HASH = "96844b2537dc763d4e190d651b160e1b88647e206b65d59cedea57dd30b7ef89";
    private static final String OPTIMIZER = "teleportoptimizer-1.0.0-obfuscated.jar";
    private static final String OPTIMIZER_HASH = "94b02622b2f91a2c8a0226db1faa25a9b6b116bc0ec0e49eb90c622da83ce5ee";
    private static final String SPAWN_ANIMATIONS = "spawnanimations-v1.11.5-mc1.17-26.2.9-mod.jar";
    private static final String SPAWN_HASH = "179b7eb6807b47bda83ef8ec92b464bcd274d00420ed79599c99cb1dc6631eb8";
    private static final String PAUSE_PACK = "gangs-performance-pause-1.0.0.zip";
    private static final String PAUSE_HASH = "4ad553daf027e6f9da202bac560b9abba6ba16843b5aa77dcb0ad358de9ff4ef";

    record Update(String name, String previous, String next, String oldHash, String newHash) {
        String oldName() { return name + "-" + previous + ".jar"; }
        String newName() { return name + "-" + next + ".jar"; }
    }

    public static void main(String[] args) throws Throwable {
        install(Path.of(".").toAbsolutePath().normalize());
        String main;
        try (JarFile jar = new JarFile("fabric-server-launch.jar")) {
            main = jar.getManifest().getMainAttributes().getValue("Main-Class");
        }
        if (main == null || main.equals(NextRestart.class.getName())) {
            throw new IllegalStateException("Invalid original Fabric Main-Class; refusing to launch.");
        }
        // Fabric derives its allowed parent libraries from the launch classpath.
        System.setProperty("java.class.path", Path.of("fabric-server-launch.jar").toAbsolutePath().toString());
        System.out.println("[NextRestart] Starting the original Fabric launcher.");
        try {
            Class.forName(main).getMethod("main", String[].class).invoke(null, (Object) args);
        } catch (InvocationTargetException failure) {
            throw failure.getCause();
        }
    }

    static void install(Path root) throws Exception {
        check(root.resolve("fabric-server-launch.jar"), LAUNCHER_HASH);
        check(root.resolve("mods/Chunky-1.3.146.jar"), CHUNKY_HASH);
        try (FileChannel channel = FileChannel.open(root.resolve("world/session.lock"), StandardOpenOption.WRITE);
             FileLock lock = channel.tryLock()) {
            if (lock == null) throw new IllegalStateException("The world is still locked. No update applied.");
            Path backup = root.resolve(BACKUP);
            boolean applied = Files.exists(backup.resolve("APPLIED.txt"));
            for (Update update : UPDATES) {
                validate(root, backup, update, applied);
            }
            validatePerformance(root, applied);
            Path shop = root.resolve("config/gangshop/overrides.json");
            String shopHash = hash(shop);
            if (!shopHash.equals(SHOP_HASH) && (applied || !shopHash.equals(OLD_SHOP_HASH))) {
                throw new IllegalStateException("Shop overrides changed since staging; restage instead of overwriting.");
            }
            try (InputStream source = payload("gangshop-overrides.json")) {
                if (!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.readAllBytes())).equals(SHOP_HASH)) {
                    throw new IllegalStateException("Corrupt embedded shop overrides.");
                }
            }
            if (applied) {
                System.out.println("[NextRestart] Update already applied; verified installed jars.");
                return;
            }
            Files.createDirectories(backup);
            backupOnce(root.resolve("mods/luckperms/luckperms-h2-v2.mv.db"),
                backup.resolve("luckperms-h2-v2.mv.db"));
            backupOnce(root.resolve("config/luckperms/luckperms.conf"), backup.resolve("luckperms.conf"));
            if (shopHash.equals(OLD_SHOP_HASH)) {
                backupOnce(shop, backup.resolve("gangshop-overrides.json"));
                check(backup.resolve("gangshop-overrides.json"), OLD_SHOP_HASH);
            } else {
                check(backup.resolve("gangshop-overrides.json"), OLD_SHOP_HASH);
            }
            for (Update update : UPDATES) {
                Path old = root.resolve("mods").resolve(update.oldName());
                Path saved = backup.resolve(update.oldName());
                if (Files.exists(old)) {
                    backupOnce(old, saved);
                    check(saved, update.oldHash());
                    Files.delete(old);
                }
                Path target = root.resolve("mods").resolve(update.newName());
                if (!Files.exists(target)) {
                    Path pending = backup.resolve(update.newName() + ".pending");
                    try (InputStream payload = payload(update)) {
                        Files.copy(payload, pending, StandardCopyOption.REPLACE_EXISTING);
                    }
                    check(pending, update.newHash());
                    Files.move(pending, target, StandardCopyOption.ATOMIC_MOVE);
                }
                check(target, update.newHash());
            }
            Path pendingShop = shop.resolveSibling("overrides.json.pending");
            try (InputStream source = payload("gangshop-overrides.json")) {
                Files.copy(source, pendingShop, StandardCopyOption.REPLACE_EXISTING);
            }
            check(pendingShop, SHOP_HASH);
            Files.move(pendingShop, shop, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            check(shop, SHOP_HASH);
            Path pause = root.resolve("config/paxi/datapacks").resolve(PAUSE_PACK);
            if (!Files.exists(pause)) {
                Files.createDirectories(pause.getParent());
                Path pending = pause.resolveSibling(PAUSE_PACK + ".pending");
                try (InputStream source = payload(PAUSE_PACK)) {
                    Files.copy(source, pending, StandardCopyOption.REPLACE_EXISTING);
                }
                check(pending, PAUSE_HASH);
                Files.move(pending, pause, StandardCopyOption.ATOMIC_MOVE);
            }
            check(pause, PAUSE_HASH);
            Files.writeString(backup.resolve("APPLIED.txt"),
                "GoldClaim 1.0.49, GangSales 1.0.1 and shulker/bin shop exclusions installed before Fabric startup. Teleport Optimizer remains enabled; Spawn Animations paused with saved-mob recovery. Chest boss rates and Rank Badges unchanged.\n",
                StandardOpenOption.CREATE_NEW);
            System.out.println("[NextRestart] Update applied. Original jars and pre-migration LuckPerms saved in " + BACKUP);
        }
    }

    private static void validatePerformance(Path root, boolean applied) throws Exception {
        check(root.resolve("mods").resolve(SPAWN_ANIMATIONS), SPAWN_HASH);
        Path optimizer = root.resolve("mods").resolve(OPTIMIZER);
        Path disabled = optimizer.resolveSibling(OPTIMIZER + ".disabled");
        if (Files.exists(disabled)) {
            throw new IllegalStateException("Teleport Optimizer must remain enabled; restage before startup.");
        }
        check(optimizer, OPTIMIZER_HASH);
        Path pause = root.resolve("config/paxi/datapacks").resolve(PAUSE_PACK);
        if (Files.exists(pause) || applied) check(pause, PAUSE_HASH);
        if (Files.exists(pause.resolveSibling("gangs_performance_pause"))) {
            throw new IllegalStateException("Conflicting unpacked performance pause; restage before startup.");
        }
        try (InputStream source = payload(PAUSE_PACK)) {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.readAllBytes()));
            if (!hash.equals(PAUSE_HASH)) throw new IllegalStateException("Corrupt embedded performance pause.");
        }
    }

    private static void validate(Path root, Path backup, Update update, boolean applied) throws Exception {
        Path old = root.resolve("mods").resolve(update.oldName());
        Path target = root.resolve("mods").resolve(update.newName());
        try (var files = Files.list(root.resolve("mods"))) {
            for (Path file : files.filter(p -> p.getFileName().toString().startsWith(update.name() + "-")
                    && p.getFileName().toString().endsWith(".jar")).toList()) {
                if (!file.equals(old) && !file.equals(target)) {
                    throw new IllegalStateException("Unexpected installed jar: " + file);
                }
            }
        }
        if (applied) {
            if (Files.exists(old)) throw new IllegalStateException("Old jar returned after installation: " + old);
            check(target, update.newHash());
            return;
        }
        if (Files.exists(old)) check(old, update.oldHash());
        else check(backup.resolve(update.oldName()), update.oldHash());
        if (Files.exists(target)) check(target, update.newHash());
        if (Files.exists(backup.resolve(update.oldName()))) {
            check(backup.resolve(update.oldName()), update.oldHash());
        }
        try (InputStream source = payload(update)) {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.readAllBytes()));
            if (!hash.equals(update.newHash())) throw new IllegalStateException("Corrupt embedded payload: " + update.newName());
        }
    }

    private static InputStream payload(Update update) {
        return payload(update.newName());
    }

    private static InputStream payload(String name) {
        InputStream source = NextRestart.class.getResourceAsStream("/payload/" + name);
        if (source == null) throw new IllegalStateException("Missing embedded payload: " + name);
        return source;
    }

    private static void backupOnce(Path source, Path target) throws Exception {
        if (Files.exists(target)) return;
        if (!Files.isRegularFile(source)) throw new IllegalStateException("Backup source missing: " + source);
        Path pending = target.resolveSibling(target.getFileName() + ".pending");
        Files.copy(source, pending, StandardCopyOption.REPLACE_EXISTING);
        check(pending, hash(source));
        Files.move(pending, target, StandardCopyOption.ATOMIC_MOVE);
    }

    private static String hash(Path path) throws Exception {
        if (!Files.isRegularFile(path)) throw new IllegalStateException("Required file missing: " + path);
        try (InputStream stream = Files.newInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[65536];
            for (int read; (read = stream.read(buffer)) != -1;) digest.update(buffer, 0, read);
            return HexFormat.of().formatHex(digest.digest());
        }
    }

    private static void check(Path path, String expected) throws Exception {
        if (!hash(path).equals(expected)) throw new IllegalStateException("SHA-256 mismatch: " + path);
    }
}
