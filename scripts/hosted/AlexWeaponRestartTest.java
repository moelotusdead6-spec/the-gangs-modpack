import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.Properties;
import java.util.jar.JarFile;
import java.nio.charset.StandardCharsets;

public final class AlexWeaponRestartTest {
    private static Path repo;
    private static Path tests;
    private static final String SHOP = "config/gangshop/overrides.json";
    private static final String TAG = "config/paxi/datapacks/gangs_fixes/data/c/tags/items/ingots/netherite.json";

    public static void main(String[] args) throws Exception {
        repo = Path.of(args[0]);
        tests = Files.createTempDirectory("alex-weapon-installer-test-");
        try {
            Path normal = fixture("normal");
            AlexWeaponRestart.install(normal);
            verify(normal);
            AlexWeaponRestart.install(normal);
            Files.delete(normal.resolve(AlexWeaponRestart.BACKUP).resolve("APPLIED.txt"));
            Files.delete(normal.resolve(TAG));
            Files.delete(normal.resolve(AlexWeaponRestart.NEXT));
            AlexWeaponRestart.install(normal);
            verify(normal);
            Path partial = fixture("partial");
            copyPayload(TAG, partial.resolve(TAG));
            AlexWeaponRestart.install(partial);
            verify(partial);
            Path upgraded = upgradedFixture("upgrade-1.0.59");
            AlexWeaponRestart.install(upgraded);
            verifyUpgrade(upgraded);
            AlexWeaponRestart.install(upgraded);
            Files.delete(upgraded.resolve(AlexWeaponRestart.BACKUP).resolve("APPLIED.txt"));
            Files.delete(upgraded.resolve(AlexWeaponRestart.NEXT));
            AlexWeaponRestart.install(upgraded);
            verifyUpgrade(upgraded);
            Path upgradedLocked = upgradedFixture("upgrade-locked");
            try (var channel = FileChannel.open(upgradedLocked.resolve("world/session.lock"), StandardOpenOption.WRITE);
                 var lock = channel.lock()) {
                reject(upgradedLocked);
            }
            Path upgradedChanged = upgradedFixture("upgrade-changed");
            Files.writeString(upgradedChanged.resolve(SHOP), "unexpected");
            reject(upgradedChanged);
            Path upgradedCollections = upgradedFixture("upgrade-collections-changed");
            Files.writeString(upgradedCollections.resolve(AlexWeaponRestart.COLLECTIONS), "{}");
            reject(upgradedCollections);
            Path upgradedEvent = upgradedFixture("upgrade-event-conflict");
            Files.createDirectories(upgradedEvent.resolve(AlexWeaponRestart.EVENT_TABLE).getParent());
            Files.writeString(upgradedEvent.resolve(AlexWeaponRestart.EVENT_TABLE), "{}");
            reject(upgradedEvent);
            Path upgradedDuplicate = upgradedFixture("upgrade-duplicate");
            Files.copy(repo.resolve("artifacts/goldclaim-1.0.57.jar"),
                upgradedDuplicate.resolve(AlexWeaponRestart.OLD));
            reject(upgradedDuplicate);
            Path locked = fixture("locked");
            try (var channel = FileChannel.open(locked.resolve("world/session.lock"), StandardOpenOption.WRITE);
                 var lock = channel.lock()) {
                reject(locked);
            }
            for (String file : new String[]{AlexWeaponRestart.OLD, AlexWeaponRestart.LIMITS_OLD, SHOP,
                    "config/alexscaves_biome_generation/magnetic_caves.json", "fabric-server-launch.jar"}) {
                Path changed = fixture("changed-" + Path.of(file).getFileName());
                Files.writeString(changed.resolve(file), "unexpected");
                reject(changed);
            }
            Path conflict = fixture("conflict");
            Files.createDirectories(conflict.resolve(TAG).getParent());
            Files.writeString(conflict.resolve(TAG), "{}");
            reject(conflict);
            Path duplicate = fixture("duplicate");
            Files.writeString(duplicate.resolve("mods/goldclaim-0.0.0.jar"), "unexpected");
            reject(duplicate);
            Path missing = fixture("missing-shop");
            Files.delete(missing.resolve(SHOP));
            reject(missing);
            Files.writeString(normal.resolve(TAG), "{}");
            boolean rejected = false;
            try { AlexWeaponRestart.install(normal); } catch (Exception expected) { rejected = true; }
            require(rejected, "Post-install tampering was accepted");
            System.out.println("PASS: exact mod/shop/cave config backups, 29 tag installs, six cave configs, repeat start, interrupted recovery, "
                + "world lock, changed launcher/mod/shop/cave, conflicting tag, duplicate mod, missing shop and post-install tampering; "
                + "already-applied 1.0.59 upgrade with event reward install and collections backup, exact backup, repeat/resume, locked world, "
                + "changed config, changed collections, conflicting event table and duplicate rejection.");
        } finally {
            try (var files = Files.walk(tests)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
            }
        }
    }

    private static Path fixture(String name) throws Exception {
        Path root = tests.resolve(name);
        Properties required = new Properties();
        try (InputStream source = AlexWeaponRestartTest.class.getResourceAsStream("/payload/required.properties")) {
            required.load(source);
        }
        for (String file : required.stringPropertyNames()) {
            Path source = switch (file) {
                case "fabric-server-launch.jar" -> repo.resolve("tmp/rsw-biome-prod-tests/fabric-server-launch.jar");
                default -> repo.resolve(file);
            };
            Files.createDirectories(root.resolve(file).getParent());
            Files.copy(source, root.resolve(file));
        }
        Files.createDirectories(root.resolve("mods"));
        Files.copy(repo.resolve("artifacts/goldclaim-1.0.57.jar"), root.resolve(AlexWeaponRestart.OLD));
        Files.copy(repo.resolve("artifacts/commandlimits-1.0.0.jar"), root.resolve(AlexWeaponRestart.LIMITS_OLD));
        for (String biome : new String[]{"magnetic_caves", "primordial_caves", "toxic_caves",
                "abyssal_chasm", "forlorn_hollows", "candy_cavity"}) {
            String file = "config/alexscaves_biome_generation/" + biome + ".json";
            copyPayload("original/" + file, root.resolve(file));
        }
        Files.createDirectories(root.resolve("world"));
        Files.writeString(root.resolve("world/session.lock"), "fixture");
        Files.createDirectories(root.resolve(SHOP).getParent());
        String shop;
        try (JarFile original = new JarFile(repo.resolve(
                "artifacts/gangs-alex-weapon-restart-1.0.60.jar").toFile());
             InputStream source = original.getInputStream(original.getJarEntry("payload/" + SHOP))) {
            shop = new String(source.readAllBytes(), StandardCharsets.UTF_8).replaceAll(
                "(?m)^    \"soulsweapons:withered_wabbajack\": true,\\r?\\n", "");
        }
        Files.writeString(root.resolve(SHOP), shop);
        copyPayload("original/" + AlexWeaponRestart.COLLECTIONS, root.resolve(AlexWeaponRestart.COLLECTIONS));
        return root;
    }

    private static void copyPayload(String file, Path target) throws Exception {
        Files.createDirectories(target.getParent());
        try (InputStream source = AlexWeaponRestartTest.class.getResourceAsStream("/payload/" + file)) {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Path upgradedFixture(String name) throws Exception {
        Path root = fixture(name);
        Files.delete(root.resolve(AlexWeaponRestart.OLD));
        Files.delete(root.resolve(AlexWeaponRestart.LIMITS_OLD));
        Files.copy(repo.resolve("artifacts/goldclaim-1.0.59.jar"), root.resolve(AlexWeaponRestart.PREVIOUS));
        Properties hashes = new Properties();
        try (InputStream source = AlexWeaponRestartTest.class.getResourceAsStream("/payload/files.properties")) {
            hashes.load(source);
        }
        for (String file : hashes.stringPropertyNames()) {
            if (!file.equals(AlexWeaponRestart.NEXT) && !file.equals(AlexWeaponRestart.EVENT_TABLE)
                    && !file.equals(AlexWeaponRestart.COLLECTIONS)) copyPayload(file, root.resolve(file));
        }
        return root;
    }

    private static void verifyUpgrade(Path root) throws Exception {
        require(!Files.exists(root.resolve(AlexWeaponRestart.PREVIOUS)), "Previous GoldClaim remains");
        require(Files.mismatch(root.resolve(AlexWeaponRestart.NEXT), repo.resolve("artifacts/goldclaim-1.0.61.jar")) == -1,
            "Upgraded GoldClaim differs");
        verifyEvent(root);
        require(Files.mismatch(root.resolve(AlexWeaponRestart.BACKUP).resolve(AlexWeaponRestart.PREVIOUS),
            repo.resolve("artifacts/goldclaim-1.0.59.jar")) == -1, "Previous GoldClaim backup differs");
        require(Files.exists(root.resolve(AlexWeaponRestart.LIMITS_NEXT)), "Existing Command Limits was removed");
        require(!Files.exists(root.resolve(AlexWeaponRestart.BACKUP).resolve(SHOP)),
            "Upgrade must not rewrite already-installed shop configuration");
    }

    private static void verify(Path root) throws Exception {
        require(!Files.exists(root.resolve(AlexWeaponRestart.OLD)), "Duplicate old mod remains");
        require(Files.mismatch(root.resolve(AlexWeaponRestart.NEXT), repo.resolve("artifacts/goldclaim-1.0.61.jar")) == -1,
            "Installed mod differs");
        verifyEvent(root);
        require(Files.mismatch(root.resolve(AlexWeaponRestart.BACKUP).resolve(AlexWeaponRestart.OLD),
            repo.resolve("artifacts/goldclaim-1.0.57.jar")) == -1, "Original mod backup differs");
        require(Files.exists(root.resolve(AlexWeaponRestart.BACKUP).resolve(SHOP)), "Missing shop backup");
        require(Files.readString(root.resolve(TAG)).contains("minecraft:netherite_ingot"), "Missing netherite");
        require(Files.readString(root.resolve(SHOP)).contains("\"soulsweapons:withered_wabbajack\": true"),
            "Shop still permits Wabbajack");
        require(!Files.exists(root.resolve(AlexWeaponRestart.LIMITS_OLD)), "Old limiter remains");
        require(Files.mismatch(root.resolve(AlexWeaponRestart.LIMITS_NEXT),
            repo.resolve("artifacts/commandlimits-1.0.1.jar")) == -1, "Limiter differs");
        for (String biome : new String[]{"magnetic_caves", "primordial_caves", "toxic_caves",
                "abyssal_chasm", "forlorn_hollows", "candy_cavity"}) {
            String file = "config/alexscaves_biome_generation/" + biome + ".json";
            String config = Files.readString(root.resolve(file));
            require(config.contains("\"multiworld:rsw\"") && config.contains("\"minecraft:overworld\""),
                "Missing cave dimension: " + biome);
            require(Files.exists(root.resolve(AlexWeaponRestart.BACKUP).resolve(file)), "Missing cave backup");
        }
    }

    private static void verifyEvent(Path root) throws Exception {
        require(Files.mismatch(root.resolve(AlexWeaponRestart.EVENT_TABLE),
            repo.resolve(AlexWeaponRestart.EVENT_TABLE)) == -1, "Event loot table differs");
        require(Files.mismatch(root.resolve(AlexWeaponRestart.COLLECTIONS),
            repo.resolve(AlexWeaponRestart.COLLECTIONS)) == -1, "Event collections differ");
        require(Files.readString(root.resolve(AlexWeaponRestart.COLLECTIONS)).contains("event_bow_staff"),
            "Missing event collection");
        require(!Files.readString(root.resolve(AlexWeaponRestart.BACKUP).resolve(AlexWeaponRestart.COLLECTIONS))
            .contains("event_bow_staff"), "Collections backup is not the original");
    }

    private static void reject(Path root) throws Exception {
        boolean rejected = false;
        try { AlexWeaponRestart.install(root); } catch (Exception expected) { rejected = true; }
        require(rejected, "Unsafe installation was accepted");
        require(!Files.exists(root.resolve(AlexWeaponRestart.BACKUP)), "Rejected installation wrote backups");
        require(!Files.exists(root.resolve(AlexWeaponRestart.NEXT)), "Rejected installation installed a mod");
    }

    private static void require(boolean passed, String message) {
        if (!passed) throw new IllegalStateException(message);
    }
}
