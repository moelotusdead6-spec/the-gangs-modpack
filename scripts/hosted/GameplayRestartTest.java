import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

public final class GameplayRestartTest {
    private static Path repo;
    private static Path tests;

    public static void main(String[] args) throws Exception {
        repo = Path.of(args[0]);
        tests = Files.createTempDirectory("gameplay-restart-test-");
        try {
            Path normal = fixture("normal");
            GameplayRestart.install(normal);
            verify(normal);
            GameplayRestart.install(normal);
            Files.delete(normal.resolve(GameplayRestart.BACKUP).resolve("APPLIED.txt"));
            Files.delete(normal.resolve(GameplayRestart.NEW_MODS.get(1)));
            GameplayRestart.install(normal);
            verify(normal);
            Path locked = fixture("locked");
            try (var channel = FileChannel.open(locked.resolve("world/session.lock"), StandardOpenOption.WRITE);
                 var lock = channel.lock()) {
                reject(locked);
            }
            for (String file : List.of(GameplayRestart.CONFIG, GameplayRestart.OLD_MODS.get(0),
                    GameplayRestart.OLD_MODS.get(1), GameplayRestart.OLD_MODS.get(2), "fabric-server-launch.jar")) {
                Path changed = fixture("changed-" + Path.of(file).getFileName());
                Files.writeString(changed.resolve(file), "unexpected");
                reject(changed);
            }
            Path duplicate = fixture("duplicate");
            Files.writeString(duplicate.resolve("mods/bclib-0.0.0.jar"), "duplicate");
            reject(duplicate);
            Files.writeString(normal.resolve(GameplayRestart.CONFIG), "{}");
            boolean rejected = false;
            try { GameplayRestart.install(normal); } catch (Exception expected) { rejected = true; }
            require(rejected, "Installed config tampering accepted");
            System.out.println("PASS: exact three-mod and config backups, idempotency, interrupted recovery, "
                + "world lock, changed inputs, duplicate versions and installed config tampering.");
        } finally {
            try (var paths = Files.walk(tests)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private static Path fixture(String name) throws Exception {
        Path root = tests.resolve(name);
        Files.createDirectories(root.resolve("mods"));
        Files.createDirectories(root.resolve("world"));
        Files.writeString(root.resolve("world/session.lock"), "");
        Files.copy(repo.resolve("tmp/worldgen-compat-prod-tests-20261028/fabric-server-launch.jar"),
            root.resolve("fabric-server-launch.jar"));
        Path client = Path.of(System.getProperty("user.home"),
            "AppData/Roaming/PrismLauncher/instances/The Gangs Modpack/minecraft/mods");
        for (String old : GameplayRestart.OLD_MODS) {
            Path source = old.contains("goldclaim")
                ? repo.resolve("artifacts/goldclaim-1.0.61.jar") : client.resolve(Path.of(old).getFileName());
            Files.copy(source, root.resolve(old));
        }
        Path config = root.resolve(GameplayRestart.CONFIG);
        Files.createDirectories(config.getParent());
        try (var stream = GameplayRestart.payload("original-fiw.json")) { Files.copy(stream, config); }
        Files.createDirectories(root.resolve("config/gangshop"));
        Files.writeString(root.resolve("config/gangshop/prices.json"), "owner-prices-unchanged");
        return root;
    }

    private static void verify(Path root) throws Exception {
        Properties previous = GameplayRestart.properties("previous.properties");
        Properties next = GameplayRestart.properties("files.properties");
        for (String file : previous.stringPropertyNames()) {
            require(GameplayRestart.hash(root.resolve(GameplayRestart.BACKUP).resolve(file)).equals(previous.getProperty(file)),
                "Original backup mismatch: " + file);
        }
        for (String file : next.stringPropertyNames()) {
            require(GameplayRestart.hash(root.resolve(file)).equals(next.getProperty(file)), "Installed hash mismatch: " + file);
        }
        for (String old : GameplayRestart.OLD_MODS) require(!Files.exists(root.resolve(old)), "Old mod still active");
        require(Files.readString(root.resolve("config/gangshop/prices.json")).equals("owner-prices-unchanged"),
            "Shop prices changed");
    }

    private static void reject(Path root) throws Exception {
        boolean rejected = false;
        try { GameplayRestart.install(root); } catch (Exception expected) { rejected = true; }
        require(rejected, "Unsafe update accepted");
        require(!Files.exists(root.resolve(GameplayRestart.BACKUP)), "Rejected update wrote backups");
        for (String replacement : GameplayRestart.NEW_MODS) {
            require(!Files.exists(root.resolve(replacement)), "Rejected update wrote a mod");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
