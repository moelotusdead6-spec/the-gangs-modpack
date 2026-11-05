import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.Properties;

/** Regression tests for AlexMapSeedRestart. Usage: java AlexMapSeedRestartTest <live alexscaves-2.1.8.jar> */
public final class AlexMapSeedRestartTest {
    static Path oldJar;

    public static void main(String[] args) throws Exception {
        if (args.length == 3 && args[0].equals("--hold-lock")) {
            holdLock(Path.of(args[1]), Path.of(args[2]));
            return;
        }
        oldJar = Path.of(args[0]);
        freshInstall();
        secondRunIsNoOp();
        interruptedRunResumes();
        refusesChangedAlexsCaves();
        refusesUnexpectedAlexsCaves();
        refusesRunningWorld();
        System.out.println("AlexMapSeedRestartTest passed");
    }

    static Path liveRoot() throws Exception {
        Path root = Files.createTempDirectory("alex-map-seed-restart");
        Files.createDirectories(root.resolve("mods"));
        Files.copy(oldJar, root.resolve(AlexMapSeedRestart.OLD_CAVES));
        Files.writeString(root.resolve("mods/alexsmobs-2.0.12-fabric+1.20.1.jar"), "alexsmobs");
        Files.writeString(root.resolve("mods/alexscaves-levelseed-smoke-1.0.0-production.jar"), "addon");
        Files.createDirectories(root.resolve("world"));
        Files.writeString(root.resolve("world/session.lock"), "lock");
        return root;
    }

    static void freshInstall() throws Exception {
        Path root = liveRoot();
        AlexMapSeedRestart.install(root);
        verify(root);
        expect(Files.readString(root.resolve("mods/alexsmobs-2.0.12-fabric+1.20.1.jar")).equals("alexsmobs"),
            "other mods untouched");
        delete(root);
    }

    static void secondRunIsNoOp() throws Exception {
        Path root = liveRoot();
        AlexMapSeedRestart.install(root);
        long modified = Files.getLastModifiedTime(root.resolve(AlexMapSeedRestart.NEW_CAVES)).toMillis();
        AlexMapSeedRestart.install(root);
        expect(modified == Files.getLastModifiedTime(root.resolve(AlexMapSeedRestart.NEW_CAVES)).toMillis(),
            "rerun rewrites nothing");
        verify(root);
        delete(root);
    }

    static void interruptedRunResumes() throws Exception {
        Path root = liveRoot();
        Path saved = root.resolve(AlexMapSeedRestart.BACKUP).resolve(AlexMapSeedRestart.OLD_CAVES);
        Files.createDirectories(saved.getParent());
        Files.copy(root.resolve(AlexMapSeedRestart.OLD_CAVES), saved);
        try (var stream = AlexMapSeedRestart.payload(AlexMapSeedRestart.NEW_CAVES)) {
            Files.write(root.resolve(AlexMapSeedRestart.NEW_CAVES), stream.readAllBytes());
        }
        Files.delete(root.resolve(AlexMapSeedRestart.OLD_CAVES));
        AlexMapSeedRestart.install(root);
        verify(root);
        delete(root);
    }

    static void refusesChangedAlexsCaves() throws Exception {
        Path root = liveRoot();
        Files.writeString(root.resolve(AlexMapSeedRestart.OLD_CAVES), "changed");
        expectFailure(root, "File changed since staging");
        expect(!Files.exists(root.resolve(AlexMapSeedRestart.NEW_CAVES)), "nothing installed");
        delete(root);
    }

    static void refusesUnexpectedAlexsCaves() throws Exception {
        Path root = liveRoot();
        Files.writeString(root.resolve("mods/alexscaves-2.1.7.jar"), "old");
        expectFailure(root, "Unexpected active mod");
        expect(Files.exists(root.resolve(AlexMapSeedRestart.OLD_CAVES)), "2.1.8 untouched");
        delete(root);
    }

    static void refusesRunningWorld() throws Exception {
        Path root = liveRoot();
        Path ready = root.resolve("locked.ready");
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process holder = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"), "AlexMapSeedRestartTest",
            "--hold-lock", root.resolve("world/session.lock").toString(), ready.toString()).inheritIO().start();
        try {
            for (int i = 0; i < 200 && !Files.exists(ready); i++) Thread.sleep(50);
            expect(Files.exists(ready), "lock holder started");
            expectFailure(root, "World is still running");
        } finally {
            holder.destroy();
            holder.waitFor();
        }
        expect(!Files.exists(root.resolve(AlexMapSeedRestart.BACKUP)), "no backup while running");
        expect(Files.exists(root.resolve(AlexMapSeedRestart.OLD_CAVES)), "2.1.8 untouched while running");
        Files.delete(ready);
        delete(root);
    }

    static void holdLock(Path lockFile, Path ready) throws Exception {
        try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.WRITE);
             var lock = channel.lock()) {
            Files.writeString(ready, "locked");
            Thread.sleep(60_000);
        }
    }

    static void verify(Path root) throws Exception {
        Properties hashes = AlexMapSeedRestart.properties("files.properties");
        AlexMapSeedRestart.verifyInstalled(root, hashes.getProperty(AlexMapSeedRestart.OLD_CAVES),
            hashes.getProperty(AlexMapSeedRestart.NEW_CAVES));
        expect(Files.exists(root.resolve(AlexMapSeedRestart.BACKUP).resolve("APPLIED.txt")), "marker written");
    }

    static void expectFailure(Path root, String message) throws Exception {
        try {
            AlexMapSeedRestart.install(root);
        } catch (IllegalStateException expected) {
            expect(expected.getMessage().contains(message), "expected '" + message + "', got " + expected.getMessage());
            expect(!Files.exists(root.resolve(AlexMapSeedRestart.BACKUP).resolve("APPLIED.txt")), "no marker after refusal");
            return;
        }
        throw new AssertionError("Expected refusal: " + message);
    }

    static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static void delete(Path root) throws Exception {
        try (var files = Files.walk(root)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
        }
    }
}
