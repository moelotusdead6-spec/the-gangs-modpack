import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.Properties;

/** Regression tests using byte-exact copies of the live configuration files. */
public final class PassiveSpawnRestartTest {
    static Path fixtures;

    public static void main(String[] args) throws Exception {
        fixtures = Path.of(args[0]);
        freshInstallAndLaterEdits();
        interruptedInstallResumes();
        changedConfigIsRejected();
        updatedFileWithoutBackupIsRejected();
        runningWorldIsRejected();
        chainedPayloadIsNotShadowed();
        System.out.println("PassiveSpawnRestartTest passed");
    }

    static Path liveRoot() throws Exception {
        Path root = Files.createTempDirectory("passive-spawn-restart-test-");
        for (String target : PassiveSpawnRestart.TARGETS) {
            Path file = root.resolve(target);
            Files.createDirectories(file.getParent());
            Files.copy(fixtures.resolve(Path.of(target).getFileName()), file);
        }
        Files.createDirectories(root.resolve("world"));
        Files.writeString(root.resolve("world/session.lock"), "lock");
        Files.writeString(root.resolve("config/unrelated.json"), "{\"unchanged\":true}");
        return root;
    }

    static void freshInstallAndLaterEdits() throws Exception {
        Path root = liveRoot();
        try {
            PassiveSpawnRestart.install(root);
            verifyInstall(root);
            String custom = "{\"adminChanged\":true}";
            Files.writeString(root.resolve(PassiveSpawnRestart.WILDERNATURE), custom);
            PassiveSpawnRestart.install(root);
            expect(Files.readString(root.resolve(PassiveSpawnRestart.WILDERNATURE)).equals(custom),
                "later config changes must not be overwritten");
            expect(Files.readString(root.resolve("config/unrelated.json")).equals("{\"unchanged\":true}"),
                "unrelated config untouched");
        } finally {
            delete(root);
        }
    }

    static void interruptedInstallResumes() throws Exception {
        Path root = liveRoot();
        try {
            String target = PassiveSpawnRestart.WILDLIFE;
            Path saved = root.resolve(PassiveSpawnRestart.BACKUP).resolve(target);
            Files.createDirectories(saved.getParent());
            Files.copy(root.resolve(target), saved);
            try (var stream = PassiveSpawnRestart.payload(target)) {
                Files.write(root.resolve(target), stream.readAllBytes());
            }
            PassiveSpawnRestart.install(root);
            verifyInstall(root);
        } finally {
            delete(root);
        }
    }

    static void changedConfigIsRejected() throws Exception {
        for (String changed : PassiveSpawnRestart.TARGETS) {
            Path root = liveRoot();
            try {
                Files.writeString(root.resolve(changed), "unexpected");
                expectFailure(root, "Live config changed since staging");
                String other = changed.equals(PassiveSpawnRestart.WILDLIFE)
                    ? PassiveSpawnRestart.WILDERNATURE : PassiveSpawnRestart.WILDLIFE;
                expect(PassiveSpawnRestart.hash(root.resolve(other)).equals(PassiveSpawnRestart.PREVIOUS.get(other)),
                    "other config must not change after a rejected install");
                expect(!Files.exists(root.resolve(PassiveSpawnRestart.BACKUP)), "no backup on rejected install");
            } finally {
                delete(root);
            }
        }
    }

    static void updatedFileWithoutBackupIsRejected() throws Exception {
        Path root = liveRoot();
        try {
            try (var stream = PassiveSpawnRestart.payload(PassiveSpawnRestart.WILDLIFE)) {
                Files.write(root.resolve(PassiveSpawnRestart.WILDLIFE), stream.readAllBytes());
            }
            expectFailure(root, "Updated config has no original backup");
            expect(PassiveSpawnRestart.hash(root.resolve(PassiveSpawnRestart.WILDERNATURE))
                .equals(PassiveSpawnRestart.PREVIOUS.get(PassiveSpawnRestart.WILDERNATURE)),
                "other config remains unchanged");
        } finally {
            delete(root);
        }
    }

    static void runningWorldIsRejected() throws Exception {
        Path root = liveRoot();
        try {
            try (FileChannel channel = FileChannel.open(root.resolve("world/session.lock"), StandardOpenOption.WRITE);
                 FileLock lock = channel.lock()) {
                expectFailure(root, "World is still running");
            }
            expect(!Files.exists(root.resolve(PassiveSpawnRestart.BACKUP)), "no backup while world is locked");
        } finally {
            delete(root);
        }
    }

    static void chainedPayloadIsNotShadowed() throws Exception {
        Properties chained = MimicreamRestart.properties("files.properties");
        expect(chained.getProperty(MimicreamRestart.TICK) != null,
            "pending Mimicream launcher reads its own manifest");
        expect(!chained.containsKey(PassiveSpawnRestart.WILDLIFE),
            "passive-spawn payload must not shadow the chained payload");
    }

    static void verifyInstall(Path root) throws Exception {
        Properties next = PassiveSpawnRestart.properties();
        for (String target : PassiveSpawnRestart.TARGETS) {
            expect(PassiveSpawnRestart.hash(root.resolve(target)).equals(next.getProperty(target)),
                "installed payload: " + target);
            expect(PassiveSpawnRestart.hash(root.resolve(PassiveSpawnRestart.BACKUP).resolve(target))
                .equals(PassiveSpawnRestart.PREVIOUS.get(target)), "original backup: " + target);
        }
        expect(Files.isRegularFile(root.resolve(PassiveSpawnRestart.BACKUP).resolve("APPLIED.txt")),
            "installed marker exists");
    }

    static void expectFailure(Path root, String message) throws Exception {
        try {
            PassiveSpawnRestart.install(root);
        } catch (Exception failure) {
            if (failure.getMessage() != null && failure.getMessage().contains(message)) return;
            throw failure;
        }
        throw new AssertionError("Expected failure: " + message);
    }

    static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static void delete(Path root) throws Exception {
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }
}
