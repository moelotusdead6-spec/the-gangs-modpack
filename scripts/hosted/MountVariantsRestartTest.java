import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MountVariantsRestartTest {
    private static final List<Path> ROOTS = new ArrayList<>();
    private static Path fabricLauncher;

    public static void main(String[] args) throws Exception {
        Path repo = Path.of(args[0]);
        Path mountDeps = Path.of(args[1]);
        Path gravesDeps = Path.of(args[2]);
        fabricLauncher = Path.of(args[3]);
        Map<String, byte[]> payloads = new HashMap<>();
        for (String name : MountVariantsRestart.NEW.keySet()) payloads.put(name, Files.readAllBytes(repo.resolve("artifacts").resolve(name)));
        var embedded = MountVariantsRestart.payloads();
        require(embedded.keySet().equals(payloads.keySet()), "Embedded payloads incomplete");
        for (String name : payloads.keySet()) require(java.util.Arrays.equals(embedded.get(name), payloads.get(name)), "Embedded payload differs: " + name);
        try {
            Path fresh = root(repo, mountDeps, gravesDeps);
            MountVariantsRestart.preparePredecessors(fresh);
            MountVariantsRestart.install(fresh, payloads);
            MountVariantsRestart.preparePredecessors(fresh);
            MountVariantsRestart.install(fresh, payloads);
            for (var entry : MountVariantsRestart.NEW.entrySet()) MountClaimRestart.check(fresh.resolve("mods").resolve(entry.getKey()), entry.getValue());
            GravesRestart.check(fresh.resolve(GravesRestart.COMPAT), GravesRestart.COMPAT_SHA256);
            for (var entry : MountVariantsRestart.OLD.entrySet()) {
                MountClaimRestart.check(fresh.resolve(MountVariantsRestart.BACKUP).resolve(entry.getKey()), entry.getValue());
                require(!Files.exists(fresh.resolve("mods").resolve(entry.getKey())), "Old duplicate retained");
            }
            Path interrupted = prepared(repo, mountDeps, gravesDeps);
            Path backup = interrupted.resolve(MountVariantsRestart.BACKUP);
            Files.createDirectories(backup);
            for (String name : MountVariantsRestart.OLD.keySet()) Files.copy(interrupted.resolve("mods").resolve(name), backup.resolve(name));
            Files.writeString(backup.resolve("STARTED.txt"), MountVariantsRestart.STARTED);
            String first = MountVariantsRestart.NEW.keySet().iterator().next();
            Files.write(interrupted.resolve("mods").resolve(first), payloads.get(first));
            MountVariantsRestart.preparePredecessors(interrupted);
            MountVariantsRestart.install(interrupted, payloads);
            require(Files.exists(backup.resolve("APPLIED.txt")), "Interrupted install did not resume");
            Path corrupt = prepared(repo, mountDeps, gravesDeps);
            var broken = new HashMap<>(payloads);
            byte[] bytes = payloads.get(first).clone();
            bytes[0] ^= 1;
            broken.put(first, bytes);
            fail(() -> MountVariantsRestart.install(corrupt, broken), "Corrupt");
            require(!Files.exists(corrupt.resolve(MountVariantsRestart.BACKUP)), "Corrupt payload wrote backups");
            Path duplicate = prepared(repo, mountDeps, gravesDeps);
            Files.write(duplicate.resolve("mods").resolve("renamed-mount.jar"), payloads.get("gangsmounts-1.0.2.jar"));
            fail(() -> MountVariantsRestart.install(duplicate, payloads), "Unexpected managed");
            Path duplicateBoots = prepared(repo, mountDeps, gravesDeps);
            Files.write(duplicateBoots.resolve("mods").resolve("renamed-boots.jar"), payloads.get("gangsboots-1.0.8.jar"));
            fail(() -> MountVariantsRestart.install(duplicateBoots, payloads), "Unexpected managed");
            Path changedBoots = prepared(repo, mountDeps, gravesDeps);
            Files.writeString(changedBoots.resolve("mods").resolve("gangsboots-1.0.7.jar"), "changed");
            fail(() -> MountVariantsRestart.install(changedBoots, payloads), "SHA-256");
            Files.writeString(fresh.resolve("mods").resolve(first), "unexpected");
            fail(() -> MountVariantsRestart.install(fresh, payloads), "SHA-256");
            Path locked = prepared(repo, mountDeps, gravesDeps);
            try (var channel = FileChannel.open(locked.resolve("world").resolve("session.lock"), StandardOpenOption.WRITE);
                 var lock = channel.lock()) {
                try {
                    MountVariantsRestart.install(locked, payloads);
                    throw new AssertionError("Running world allowed update");
                } catch (OverlappingFileLockException expected) {
                    require(!Files.exists(locked.resolve(MountVariantsRestart.BACKUP)), "Locked world changed");
                }
            }
            System.out.println("MountVariantsRestartTest passed: embedded payloads, predecessor bootstrap/repeat, graves and Boots 1.0.8 retained, backups, interrupted recovery, corruption, mount/boot duplicates, changed old/new targets and world lock.");
        } finally {
            for (Path root : ROOTS) {
                try (var files = Files.walk(root)) {
                    for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
                }
            }
        }
    }

    private static Path prepared(Path repo, Path mountDeps, Path gravesDeps) throws Exception {
        Path root = root(repo, mountDeps, gravesDeps);
        MountVariantsRestart.preparePredecessors(root);
        return root;
    }

    private static Path root(Path repo, Path mountDeps, Path gravesDeps) throws Exception {
        Path root = Files.createTempDirectory("mount-variants-restart-test-");
        ROOTS.add(root);
        Files.createDirectories(root.resolve("mods"));
        Files.createDirectories(root.resolve("world"));
        Files.writeString(root.resolve("world").resolve("session.lock"), "");
        for (var entry : MountClaimRestart.OLD.entrySet()) copy(repo.resolve("artifacts").resolve(entry.getKey()), root.resolve("mods").resolve(entry.getKey()));
        copy(repo.resolve("artifacts").resolve("gangsboots-1.0.7.jar"), root.resolve("mods").resolve("gangsboots-1.0.7.jar"));
        for (String relative : MountClaimRestart.PREREQUISITES.keySet()) {
            Path source = relative.equals("fabric-server-launch.jar") ? fabricLauncher
                : relative.endsWith("gangscosmetics-2.0.2.jar") ? repo.resolve("artifacts").resolve("gangscosmetics-2.0.2.jar")
                : mountDeps.resolve(Path.of(relative).getFileName());
            copy(source, root.resolve(relative));
        }
        for (String name : GravesRestart.DEPENDENCIES.keySet()) {
            Path source = name.startsWith("graves-") ? gravesDeps.resolve("universal-graves-66RxRZcI.jar") : gravesDeps.resolve(name);
            copy(source, root.resolve("mods").resolve(name));
        }
        copy(mountDeps.resolve(MountClaimRestart.MYTHIC), root.resolve(MountClaimRestart.STAGING).resolve(MountClaimRestart.MYTHIC));
        Path previous = root.resolve("backups").resolve("furniture-hub-1.0.0").resolve("APPLIED.txt");
        Files.createDirectories(previous.getParent());
        Files.writeString(previous, "Furniture mods, staff-only workstation, shop exclusions and hub projectile protection installed.\n");
        return root;
    }

    private static void copy(Path source, Path target) throws Exception {
        Files.createDirectories(target.getParent());
        Files.copy(source, target);
    }
    private interface Action { void run() throws Exception; }
    private static void fail(Action action, String expected) throws Exception {
        try { action.run(); }
        catch (IllegalStateException failure) {
            require(failure.getMessage().contains(expected), "Unexpected failure: " + failure);
            return;
        }
        throw new AssertionError("Expected failure: " + expected);
    }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
