import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

public final class MountClaimRestartTest {
    private static final List<Path> ROOTS = new ArrayList<>();
    private static Path repo;
    private static Path mythic;

    public static void main(String[] args) throws Exception {
        repo = Path.of(args[0]);
        mythic = Path.of(args[1]);
        try {
            Path normal = root();
            install(normal);
            install(normal);
            verify(normal);

            Path corrupt = root();
            var bad = new LinkedHashMap<>(MountClaimRestart.payloads(corrupt));
            bad.put("gangsmounts-1.0.0.jar", new byte[]{1, 2, 3});
            expectFailure(() -> MountClaimRestart.install(corrupt, bad), "Corrupt payload");
            unchanged(corrupt);

            Path duplicate = root();
            Files.copy(duplicate.resolve("mods/gangshop-1.0.11.jar"), duplicate.resolve("mods/renamed.jar"));
            expectFailure(() -> install(duplicate), "Unexpected managed mod jar");
            unchanged(duplicate);

            Path changed = root();
            Files.writeString(changed.resolve("mods/goldclaim-1.0.75.jar"), "unexpected");
            expectFailure(() -> install(changed), "unexpected SHA-256");
            unchanged(changed);

            Path locked = root();
            try (FileChannel channel = FileChannel.open(locked.resolve("world/session.lock"), StandardOpenOption.WRITE);
                    var lock = channel.lock()) {
                try {
                    install(locked);
                    throw new AssertionError("Running world permitted installation");
                } catch (OverlappingFileLockException expected) {
                    unchanged(locked);
                }
            }

            Path interrupted = root();
            var partial = MountClaimRestart.payloads(interrupted);
            Files.write(interrupted.resolve("mods/gangshop-1.0.12.jar"), partial.get("gangshop-1.0.12.jar"));
            Path backup = interrupted.resolve(MountClaimRestart.BACKUP).resolve("goldclaim-1.0.74.jar");
            Files.createDirectories(backup.getParent());
            Files.move(interrupted.resolve("mods/goldclaim-1.0.74.jar"), backup);
            install(interrupted);
            verify(interrupted);
            System.out.println("MountClaimRestartTest passed: install/repeat, corrupt payload, duplicate mod, "
                + "changed target, world lock and interrupted recovery.");
        } finally {
            for (Path root : ROOTS) {
                try (var paths = Files.walk(root)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
                }
            }
        }
    }

    private static Path root() throws Exception {
        Path root = Files.createTempDirectory("mount-claim-restart-test-");
        ROOTS.add(root);
        Files.createDirectories(root.resolve("world"));
        Files.createDirectories(root.resolve("mods"));
        Files.createDirectories(root.resolve(MountClaimRestart.STAGING));
        Files.writeString(root.resolve("world/session.lock"), "");
        for (String name : MountClaimRestart.OLD.keySet()) {
            Files.copy(repo.resolve("artifacts").resolve(name), root.resolve("mods").resolve(name));
        }
        Files.copy(mythic, root.resolve(MountClaimRestart.STAGING).resolve(MountClaimRestart.MYTHIC));
        Files.writeString(root.resolve("unrelated.txt"), "preserve");
        return root;
    }

    private static void install(Path root) throws Exception {
        MountClaimRestart.install(root, MountClaimRestart.payloads(root));
    }

    private static void verify(Path root) throws Exception {
        for (var entry : MountClaimRestart.NEW.entrySet()) {
            MountClaimRestart.check(root.resolve("mods").resolve(entry.getKey()), entry.getValue());
        }
        for (var entry : MountClaimRestart.OLD.entrySet()) {
            require(!Files.exists(root.resolve("mods").resolve(entry.getKey())), "Old jar remained active");
            MountClaimRestart.check(root.resolve(MountClaimRestart.BACKUP).resolve(entry.getKey()), entry.getValue());
        }
        require(Files.readString(root.resolve("unrelated.txt")).equals("preserve"), "Unrelated file changed");
    }

    private static void unchanged(Path root) throws Exception {
        for (var entry : MountClaimRestart.OLD.entrySet()) {
            MountClaimRestart.check(root.resolve("mods").resolve(entry.getKey()), entry.getValue());
        }
        require(!Files.exists(root.resolve(MountClaimRestart.BACKUP)), "Failed preflight wrote backups");
    }

    private static void expectFailure(Action action, String message) throws Exception {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            require(expected.getMessage().contains(message), "Unexpected error: " + expected.getMessage());
            return;
        }
        throw new AssertionError("Expected failure: " + message);
    }

    private interface Action { void run() throws Exception; }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
