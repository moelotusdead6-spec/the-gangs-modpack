import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

public final class GoldClaimMailboxRestartTest {
    private static final List<Path> ROOTS = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        try {
            byte[] payload = Files.readAllBytes(Path.of(args[0]));
            replaces(payload, GoldClaimMailboxRestart.OLD_68, Path.of(args[1]));
            replaces(payload, GoldClaimMailboxRestart.OLD_69, Path.of(args[2]));
            replaces(payload, GoldClaimMailboxRestart.OLD_70, Path.of(args[3]));
            acceptsExisting(payload);
            recoversInterruptedInstall(payload, GoldClaimMailboxRestart.OLD_68, Path.of(args[1]));
            recoversInterruptedInstall(payload, GoldClaimMailboxRestart.OLD_69, Path.of(args[2]));
            recoversInterruptedInstall(payload, GoldClaimMailboxRestart.OLD_70, Path.of(args[3]));
            refusesInterruptedInstallWithoutBackup(payload, Path.of(args[2]));
            refusesUnexpectedJar(payload);
            refusesWrongOldHash(payload);
            refusesCorruptPayload(payload);
            System.out.println("GoldClaimMailboxRestartTest passed");
        } finally {
            for (Path root : ROOTS) {
                List<Path> paths;
                try (var files = Files.walk(root)) {
                    paths = files.sorted(Comparator.reverseOrder()).toList();
                }
                for (Path file : paths) Files.deleteIfExists(file);
            }
        }
    }

    static void replaces(byte[] payload, String oldName, Path oldJar) throws Exception {
        Path root = root();
        Path old = root.resolve(oldName);
        Files.copy(oldJar, old);
        String oldHash = GoldClaimMailboxRestart.oldHash(oldName);
        GoldClaimMailboxRestart.install(root, payload);
        require(!Files.exists(old), "old GoldClaim removed: " + oldName);
        GoldClaimMailboxRestart.check(root.resolve(GoldClaimMailboxRestart.NEW_71), GoldClaimMailboxRestart.NEW_71_SHA256);
        GoldClaimMailboxRestart.check(root.resolve(GoldClaimMailboxRestart.BACKUP).resolve(oldName), oldHash);
        require(Files.exists(root.resolve(GoldClaimMailboxRestart.BACKUP).resolve("APPLIED.txt")), "completion marker written");
        GoldClaimMailboxRestart.install(root, payload);
        require(Files.exists(root.resolve(GoldClaimMailboxRestart.NEW_71)), "repeat install is idempotent");
    }

    static void acceptsExisting(byte[] payload) throws Exception {
        Path root = root();
        Files.write(root.resolve(GoldClaimMailboxRestart.NEW_71), payload);
        GoldClaimMailboxRestart.install(root, payload);
        GoldClaimMailboxRestart.install(root, payload);
        require(Files.exists(root.resolve(GoldClaimMailboxRestart.NEW_71)), "already-current jar stays installed");
    }

    static void recoversInterruptedInstall(byte[] payload, String oldName, Path oldJar) throws Exception {
        Path root = root();
        String oldHash = GoldClaimMailboxRestart.oldHash(oldName);
        Path original = root.resolve(oldName);
        Files.copy(oldJar, original);
        Path saved = root.resolve(GoldClaimMailboxRestart.BACKUP).resolve(oldName);
        Files.createDirectories(saved.getParent());
        Files.copy(oldJar, saved);
        Files.write(root.resolve(GoldClaimMailboxRestart.NEW_71), payload);
        GoldClaimMailboxRestart.install(root, payload);
        require(!Files.exists(original), "interrupted install removes old jar after verification");
        GoldClaimMailboxRestart.check(saved, oldHash);
        require(Files.exists(root.resolve(GoldClaimMailboxRestart.BACKUP).resolve("APPLIED.txt")),
            "interrupted install writes completion marker");
    }

    static void refusesUnexpectedJar(byte[] payload) throws Exception {
        Path root = root();
        Files.writeString(root.resolve("mods/goldclaim-1.0.66.jar"), "unexpected");
        expectFailure(() -> GoldClaimMailboxRestart.install(root, payload), "Unexpected active GoldClaim jar");
        require(!Files.exists(root.resolve(GoldClaimMailboxRestart.NEW_71)), "unexpected active jar prevents changes");
    }

    static void refusesInterruptedInstallWithoutBackup(byte[] payload, Path oldJar) throws Exception {
        Path root = root();
        Path original = root.resolve(GoldClaimMailboxRestart.OLD_69);
        Path updated = root.resolve(GoldClaimMailboxRestart.NEW_71);
        Files.copy(oldJar, original);
        Files.write(updated, payload);
        expectFailure(() -> GoldClaimMailboxRestart.install(root, payload), "Missing file or unexpected SHA-256");
        require(Files.exists(original) && Files.exists(updated), "missing backup preserves both jars");
    }

    static void refusesWrongOldHash(byte[] payload) throws Exception {
        Path root = root();
        Files.writeString(root.resolve(GoldClaimMailboxRestart.OLD_69), "tampered");
        expectFailure(() -> GoldClaimMailboxRestart.install(root, payload), "unexpected SHA-256");
        require(Files.exists(root.resolve(GoldClaimMailboxRestart.OLD_69)), "tampered original preserved");
        require(!Files.exists(root.resolve(GoldClaimMailboxRestart.NEW_71)), "tampered original prevents installation");
    }

    static void refusesCorruptPayload(byte[] payload) throws Exception {
        Path root = root();
        Files.write(root.resolve(GoldClaimMailboxRestart.OLD_69), new byte[]{1, 2, 3});
        payload[0] ^= 1;
        expectFailure(() -> GoldClaimMailboxRestart.install(root, payload), "Corrupt GoldClaim 1.0.71 payload");
        require(Files.exists(root.resolve(GoldClaimMailboxRestart.OLD_69)), "corrupt payload leaves original untouched");
    }

    static Path root() throws Exception {
        Path root = Files.createTempDirectory("goldclaim-mailbox-restart");
        Files.createDirectories(root.resolve("mods"));
        Files.createDirectories(root.resolve("world"));
        Files.writeString(root.resolve("world/session.lock"), "");
        ROOTS.add(root);
        return root;
    }

    interface Action { void run() throws Exception; }

    static void expectFailure(Action action, String message) throws Exception {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            if (!expected.getMessage().contains(message)) throw new AssertionError("Wrong failure: " + expected.getMessage());
            return;
        }
        throw new AssertionError("Expected failure: " + message);
    }

    static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
