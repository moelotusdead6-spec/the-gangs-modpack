import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.Properties;

/** Regression tests for AlexBossRestart. Usage: java AlexBossRestartTest <live goldclaim-1.0.64.jar> */
public final class AlexBossRestartTest {
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
        refusesChangedGoldClaim();
        refusesUnexpectedGoldClaim();
        refusesMissingAttributeFix();
        refusesRunningWorld();
        System.out.println("AlexBossRestartTest passed");
    }

    static Path liveRoot() throws Exception {
        Path root = Files.createTempDirectory("alex-boss-restart");
        Files.createDirectories(root.resolve("mods"));
        Files.copy(oldJar, root.resolve(AlexBossRestart.OLD_GOLDCLAIM));
        Files.writeString(root.resolve("mods/AttributeFix-Fabric-1.20.1-21.0.5.jar"), "attributefix");
        Files.writeString(root.resolve("mods/alexscaves-2.1.8.jar"), "alexscaves");
        Files.createDirectories(root.resolve("world"));
        Files.writeString(root.resolve("world/session.lock"), "lock");
        return root;
    }

    static void freshInstall() throws Exception {
        Path root = liveRoot();
        AlexBossRestart.install(root);
        verify(root);
        expect(Files.readString(root.resolve("mods/alexscaves-2.1.8.jar")).equals("alexscaves"), "other mods untouched");
        delete(root);
    }

    static void secondRunIsNoOp() throws Exception {
        Path root = liveRoot();
        AlexBossRestart.install(root);
        long modified = Files.getLastModifiedTime(root.resolve(AlexBossRestart.NEW_GOLDCLAIM)).toMillis();
        AlexBossRestart.install(root);
        expect(modified == Files.getLastModifiedTime(root.resolve(AlexBossRestart.NEW_GOLDCLAIM)).toMillis(),
            "rerun rewrites nothing");
        verify(root);
        delete(root);
    }

    static void interruptedRunResumes() throws Exception {
        Path root = liveRoot();
        Path saved = root.resolve(AlexBossRestart.BACKUP).resolve(AlexBossRestart.OLD_GOLDCLAIM);
        Files.createDirectories(saved.getParent());
        Files.copy(root.resolve(AlexBossRestart.OLD_GOLDCLAIM), saved);
        try (var stream = AlexBossRestart.payload(AlexBossRestart.NEW_GOLDCLAIM)) {
            Files.write(root.resolve(AlexBossRestart.NEW_GOLDCLAIM), stream.readAllBytes());
        }
        Files.delete(root.resolve(AlexBossRestart.OLD_GOLDCLAIM));
        AlexBossRestart.install(root);
        verify(root);
        delete(root);
    }

    static void refusesChangedGoldClaim() throws Exception {
        Path root = liveRoot();
        Files.writeString(root.resolve(AlexBossRestart.OLD_GOLDCLAIM), "changed");
        expectFailure(root, "File changed since staging");
        expect(!Files.exists(root.resolve(AlexBossRestart.NEW_GOLDCLAIM)), "nothing installed");
        delete(root);
    }

    static void refusesUnexpectedGoldClaim() throws Exception {
        Path root = liveRoot();
        Files.writeString(root.resolve("mods/goldclaim-1.0.63.jar"), "old");
        expectFailure(root, "Unexpected active mod");
        expect(Files.exists(root.resolve(AlexBossRestart.OLD_GOLDCLAIM)), "1.0.64 untouched");
        delete(root);
    }

    static void refusesMissingAttributeFix() throws Exception {
        Path root = liveRoot();
        Files.delete(root.resolve("mods/AttributeFix-Fabric-1.20.1-21.0.5.jar"));
        expectFailure(root, "AttributeFix is not installed");
        expect(Files.exists(root.resolve(AlexBossRestart.OLD_GOLDCLAIM)), "1.0.64 untouched");
        delete(root);
    }

    static void refusesRunningWorld() throws Exception {
        Path root = liveRoot();
        Path ready = root.resolve("locked.ready");
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process holder = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"), "AlexBossRestartTest",
            "--hold-lock", root.resolve("world/session.lock").toString(), ready.toString()).inheritIO().start();
        try {
            for (int i = 0; i < 200 && !Files.exists(ready); i++) Thread.sleep(50);
            expect(Files.exists(ready), "lock holder started");
            expectFailure(root, "World is still running");
        } finally {
            holder.destroy();
            holder.waitFor();
        }
        expect(!Files.exists(root.resolve(AlexBossRestart.BACKUP)), "no backup while running");
        expect(Files.exists(root.resolve(AlexBossRestart.OLD_GOLDCLAIM)), "1.0.64 untouched while running");
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
        Properties hashes = AlexBossRestart.properties("files.properties");
        AlexBossRestart.verifyInstalled(root, hashes.getProperty(AlexBossRestart.OLD_GOLDCLAIM),
            hashes.getProperty(AlexBossRestart.NEW_GOLDCLAIM));
        expect(Files.exists(root.resolve(AlexBossRestart.BACKUP).resolve("APPLIED.txt")), "marker written");
    }

    static void expectFailure(Path root, String message) throws Exception {
        try {
            AlexBossRestart.install(root);
        } catch (IllegalStateException expected) {
            expect(expected.getMessage().contains(message), "expected '" + message + "', got " + expected.getMessage());
            expect(!Files.exists(root.resolve(AlexBossRestart.BACKUP).resolve("APPLIED.txt")), "no marker after refusal");
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
