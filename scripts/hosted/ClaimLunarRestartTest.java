import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Properties;

public final class ClaimLunarRestartTest {
    private static final String EVENTS = "config/paxi/datapacks/gangs_fixes/data/enhancedcelestials/enhancedcelestials/lunar/event/";
    private static Path repo;
    private static Path tests;
    private static final Path BACKUP = Path.of("backups/next-restart-goldclaim-1.0.52/goldclaim-1.0.51.jar");

    public static void main(String[] args) throws Exception {
        repo = Path.of(args[0]);
        tests = Files.createTempDirectory(repo.resolve("tmp"), "claim-lunar-installer-test-");
        Path install = fixture("normal");
        ClaimLunarRestart.install(install);
        require(Files.exists(install.resolve("mods/goldclaim-1.0.52.jar")), "Missing new mod");
        require(!Files.exists(install.resolve("mods/goldclaim-1.0.51.jar")), "Duplicate mod remains");
        require(Files.mismatch(repo.resolve("artifacts/goldclaim-1.0.51.jar"), install.resolve(BACKUP)) == -1,
            "Original jar backup is not exact");
        ClaimLunarRestart.install(install);
        Files.delete(install.resolve("backups/next-restart-goldclaim-1.0.52/APPLIED.txt"));
        Files.delete(install.resolve("mods/goldclaim-1.0.52.jar"));
        Files.delete(install.resolve(EVENTS + "blue_moon.json"));
        ClaimLunarRestart.install(install);
        require(Files.exists(install.resolve(EVENTS + "blue_moon.json")), "Interrupted update did not resume");
        Path locked = fixture("locked");
        try (var channel = FileChannel.open(locked.resolve("world/session.lock"), StandardOpenOption.WRITE);
             var lock = channel.lock()) {
            reject(locked);
        }
        Path corrupt = fixture("corrupt");
        Files.writeString(corrupt.resolve("mods/goldclaim-1.0.51.jar"), "corrupt");
        reject(corrupt);
        Path changedData = fixture("changed-data");
        Files.writeString(changedData.resolve(EVENTS + "blood_moon.json"), "{}");
        reject(changedData);
        Path conflict = fixture("conflicting-event");
        Files.writeString(conflict.resolve(EVENTS + "blue_moon.json"), "{}");
        reject(conflict);
        Path duplicate = fixture("duplicate");
        Files.writeString(duplicate.resolve("mods/goldclaim-0.0.0.jar"), "unexpected");
        reject(duplicate);
        System.out.println("PASS: install, exact backup, repeat startup, interrupted recovery, world lock, "
            + "corrupt jar/changed data/conflicting event/duplicate refusal before writes.");
        System.out.println("Test fixture: " + tests);
    }

    private static Path fixture(String name) throws Exception {
        Path root = tests.resolve(name);
        Properties properties = new Properties();
        try (InputStream source = ClaimLunarRestartTest.class.getResourceAsStream("/payload/hashes.properties")) {
            properties.load(source);
        }
        for (String path : properties.stringPropertyNames()) {
            if (!path.contains("/")) continue;
            Path target = root.resolve(path);
            Files.createDirectories(target.getParent());
            Path source = switch (path) {
                case "mods/supplementaries-1.20-3.1.44-fabric.jar" -> repo.resolve("artifacts/supplementaries-1.20-3.1.44-fabric.jar");
                case "mods/Enhanced-Celestials-fabric-1.20.1-5.0.3.1.jar" -> repo.resolve("tmp/enhanced-celestials-release-check.jar");
                default -> repo.resolve(path);
            };
            Files.copy(source, target);
        }
        Files.createDirectories(root.resolve("world"));
        Files.writeString(root.resolve("world/session.lock"), "fixture");
        Files.copy(repo.resolve("tmp/rsw-biome-prod-tests/fabric-server-launch.jar"), root.resolve("fabric-server-launch.jar"));
        Files.copy(repo.resolve("artifacts/goldclaim-1.0.51.jar"), root.resolve("mods/goldclaim-1.0.51.jar"));
        return root;
    }

    private static void reject(Path root) throws Exception {
        boolean rejected = false;
        try {
            ClaimLunarRestart.install(root);
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        require(rejected, "Invalid fixture was accepted: " + root);
        require(!Files.exists(root.resolve(BACKUP)), "Rejected fixture wrote backup files");
        require(!Files.exists(root.resolve("mods/goldclaim-1.0.52.jar")), "Rejected fixture installed mod");
    }

    private static void require(boolean passed, String message) {
        if (!passed) throw new IllegalStateException(message);
    }
}
