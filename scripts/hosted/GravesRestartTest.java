import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class GravesRestartTest {
    private static final List<Path> ROOTS = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        byte[] payload = Files.readAllBytes(Path.of(args[0]));
        List<Path> dependencies = List.of(Path.of(args[1]), Path.of(args[2]), Path.of(args[3]));
        try {
            Path installed = root(dependencies);
            GravesRestart.install(installed, payload);
            GravesRestart.install(installed, payload);
            GravesRestart.check(installed.resolve(GravesRestart.COMPAT), GravesRestart.COMPAT_SHA256);
            for (var entry : GravesRestart.DEPENDENCIES.entrySet()) {
                GravesRestart.check(installed.resolve("mods").resolve(entry.getKey()), entry.getValue());
            }
            Path corrupt = root(dependencies);
            byte[] changed = payload.clone();
            changed[0] ^= 1;
            expectFailure(() -> GravesRestart.install(corrupt, changed), "Corrupt");
            require(!Files.exists(corrupt.resolve(GravesRestart.COMPAT)), "corrupt payload installed");
            for (String name : GravesRestart.DEPENDENCIES.keySet()) {
                Path root = root(dependencies);
                Files.writeString(root.resolve("mods").resolve(name), "unexpected");
                expectFailure(() -> GravesRestart.install(root, payload), "SHA-256");
                require(!Files.exists(root.resolve(GravesRestart.COMPAT)), "changed dependency allowed install");
            }
            Path duplicate = root(dependencies);
            Files.write(duplicate.resolve("mods").resolve("renamed-backup.jar"), payload);
            expectFailure(() -> GravesRestart.install(duplicate, payload), "Duplicate");
            require(!Files.exists(duplicate.resolve(GravesRestart.COMPAT)), "renamed duplicate allowed install");
            Files.writeString(installed.resolve(GravesRestart.COMPAT), "unexpected");
            expectFailure(() -> GravesRestart.install(installed, payload), "SHA-256");
            require(Files.readString(installed.resolve(GravesRestart.COMPAT)).equals("unexpected"),
                "changed target was overwritten");
            Path locked = root(dependencies);
            try (var channel = FileChannel.open(locked.resolve("world").resolve("session.lock"),
                    StandardOpenOption.WRITE); var lock = channel.lock()) {
                try {
                    GravesRestart.install(locked, payload);
                    throw new AssertionError("running world allowed install");
                } catch (OverlappingFileLockException expected) {
                    require(!Files.exists(locked.resolve(GravesRestart.COMPAT)), "locked world changed");
                }
            }
            System.out.println("GravesRestartTest passed: repeat, corruption, dependency pins, "
                + "renamed duplicate, changed target and world lock.");
        } finally {
            for (Path root : ROOTS) {
                try (var paths = Files.walk(root)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                        Files.deleteIfExists(path);
                    }
                }
            }
        }
    }

    private static Path root(List<Path> dependencies) throws Exception {
        Path root = Files.createTempDirectory("safe-graves-restart-test-");
        ROOTS.add(root);
        Files.createDirectories(root.resolve("mods"));
        Files.createDirectories(root.resolve("world"));
        Files.writeString(root.resolve("world").resolve("session.lock"), "");
        var names = List.of("graves-3.0.3+1.20.1.jar",
            "sophisticatedbackpacks-1.20.1-3.23.4.5.110.jar",
            "sophisticatedcore-1.20.1-1.2.7.15.166.jar");
        for (int i = 0; i < dependencies.size(); i++) {
            Files.copy(dependencies.get(i), root.resolve("mods").resolve(names.get(i)));
        }
        return root;
    }

    private interface Action { void run() throws Exception; }

    private static void expectFailure(Action action, String message) throws Exception {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            require(expected.getMessage().contains(message), "unexpected failure: " + expected.getMessage());
            return;
        }
        throw new AssertionError("expected failure: " + message);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
