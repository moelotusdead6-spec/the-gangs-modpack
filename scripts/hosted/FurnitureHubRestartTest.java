import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public final class FurnitureHubRestartTest {
    private static final List<Path> ROOTS = new ArrayList<>();
    private static Path repo;
    private static Path staging;
    private static Properties manifest;

    public static void main(String[] args) throws Exception {
        repo = Path.of(args[0]);
        staging = Path.of(args[1]);
        manifest = FurnitureHubRestart.manifest();
        try {
            Path root = root();
            var payloads = FurnitureHubRestart.payloads(root, manifest);
            FurnitureHubRestart.install(root, manifest, payloads);
            FurnitureHubRestart.install(root, manifest, payloads);
            verifyInstalled(root);
            Files.write(root.resolve("config/immersive_furniture.json"), Files.readAllBytes(Path.of(args[2])));
            FurnitureHubRestart.install(root, manifest, payloads);
            verifyInstalled(root);

            Path corrupt = root();
            var damaged = new LinkedHashMap<>(FurnitureHubRestart.payloads(corrupt, manifest));
            damaged.put("mods/goldclaim-1.0.74.jar", new byte[]{1, 2, 3});
            expectFailure(() -> FurnitureHubRestart.install(corrupt, manifest, damaged), "Corrupt payload");
            unchanged(corrupt);

            Path duplicate = root();
            Files.copy(duplicate.resolve("mods/gangshop-1.0.10.jar"), duplicate.resolve("mods/renamed-shop.jar"));
            expectFailure(() -> install(duplicate), "Unexpected managed mod jar");
            unchanged(duplicate);

            Path changed = root();
            Files.writeString(changed.resolve("config/immersive_furniture.json"), "{\"requiredCraftingPermissionLevel\":0}");
            expectFailure(() -> install(changed), "unexpected SHA-256");
            unchanged(changed);
            require(Files.readString(changed.resolve("config/immersive_furniture.json")).contains(":0"),
                "Existing config overwritten");

            Path changedTarget = root();
            Files.copy(changedTarget.resolve("mods/goldclaim-1.0.73.jar"), changedTarget.resolve("mods/goldclaim-1.0.74.jar"));
            expectFailure(() -> install(changedTarget), "Unexpected target");
            originalsIntact(changedTarget);

            Path missing = root();
            Files.delete(missing.resolve("mods/goldclaim-1.0.73.jar"));
            expectFailure(() -> install(missing), "Original and backup missing");
            require(!Files.exists(missing.resolve("mods/gangshop-1.0.11.jar")), "Missing original allowed changes");

            Path missingStaging = root();
            Files.delete(missingStaging.resolve(FurnitureHubRestart.STAGING).resolve("bf_blockpack-fabric-1.20.1-1.1.2.jar"));
            try {
                install(missingStaging);
                throw new AssertionError("Missing staging permitted install");
            } catch (java.nio.file.NoSuchFileException expected) {
                unchanged(missingStaging);
            }

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
            var partial = FurnitureHubRestart.payloads(interrupted, manifest);
            Files.write(interrupted.resolve("mods/gangshop-1.0.11.jar"), partial.get("mods/gangshop-1.0.11.jar"));
            Path saved = interrupted.resolve(FurnitureHubRestart.BACKUP).resolve("mods/goldclaim-1.0.73.jar");
            Files.createDirectories(saved.getParent());
            Files.move(interrupted.resolve("mods/goldclaim-1.0.73.jar"), saved);
            install(interrupted);
            verifyInstalled(interrupted);

            Path prerequisite = root();
            expectFailure(() -> FurnitureHubRestart.requirePrerequisites(prerequisite), "unexpected SHA-256");
            unchanged(prerequisite);
            System.out.println("FurnitureHubRestartTest passed: install/repeat/native config, corrupt payload, renamed duplicate, "
                + "changed config/target, missing original/staging/prerequisite, world lock and interrupted recovery.");
        } finally {
            for (Path root : ROOTS) {
                try (var paths = Files.walk(root)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void install(Path root) throws Exception {
        FurnitureHubRestart.install(root, manifest, FurnitureHubRestart.payloads(root, manifest));
    }

    private static Path root() throws Exception {
        Path root = Files.createTempDirectory("furniture-hub-restart-test-");
        ROOTS.add(root);
        Files.createDirectories(root.resolve("world"));
        Files.createDirectories(root.resolve("mods"));
        Files.createDirectories(root.resolve("config"));
        Files.createDirectories(root.resolve(FurnitureHubRestart.STAGING));
        Files.writeString(root.resolve("world/session.lock"), "");
        for (String name : FurnitureHubRestart.entries(manifest, "old.").keySet()) {
            Files.copy(repo.resolve("artifacts").resolve(Path.of(name).getFileName()), root.resolve(name));
        }
        for (String name : FurnitureHubRestart.entries(manifest, "staged.").values()) {
            Files.copy(staging.resolve(name), root.resolve(FurnitureHubRestart.STAGING).resolve(name));
        }
        Files.writeString(root.resolve("config/unrelated.json"), "preserve");
        return root;
    }

    private static void verifyInstalled(Path root) throws Exception {
        for (var entry : FurnitureHubRestart.entries(manifest, "new.").entrySet()) {
            String actual = FurnitureHubRestart.digest(Files.readAllBytes(root.resolve(entry.getKey())));
            require(actual.equals(entry.getValue()) || actual.equals(manifest.getProperty("accepted." + entry.getKey())),
                "Incorrect installed file: " + entry.getKey());
        }
        for (var entry : FurnitureHubRestart.entries(manifest, "old.").entrySet()) {
            require(!Files.exists(root.resolve(entry.getKey())), "Old jar still active");
            FurnitureHubRestart.check(root.resolve(FurnitureHubRestart.BACKUP).resolve(entry.getKey()), entry.getValue());
        }
        require(Files.readString(root.resolve("config/unrelated.json")).equals("preserve"), "Unrelated configuration changed");
    }

    private static void unchanged(Path root) throws Exception {
        originalsIntact(root);
        require(!Files.exists(root.resolve("mods/goldclaim-1.0.74.jar")), "Failed preflight installed GoldClaim");
        require(!Files.exists(root.resolve("mods/gangshop-1.0.11.jar")), "Failed preflight installed shop");
        require(!Files.exists(root.resolve(FurnitureHubRestart.BACKUP)), "Failed preflight wrote backups");
    }

    private static void originalsIntact(Path root) throws Exception {
        for (var entry : FurnitureHubRestart.entries(manifest, "old.").entrySet()) {
            FurnitureHubRestart.check(root.resolve(entry.getKey()), entry.getValue());
        }
    }

    private interface Action { void run() throws Exception; }

    private static void expectFailure(Action action, String message) throws Exception {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            require(expected.getMessage().contains(message), "Wrong failure: " + expected.getMessage());
            return;
        }
        throw new AssertionError("Expected failure: " + message);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
