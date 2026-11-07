import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Properties;

/** Regression tests for TrialsBotaniaRestart.install. Args: gangshop-1.0.9.jar, directory with live lootr.json and overrides.json. */
public final class TrialsBotaniaRestartTest {
    public static void main(String[] args) throws Exception {
        Path gangShop = Path.of(args[0]);
        Path live = Path.of(args[1]);
        Properties manifest = TrialsBotaniaRestart.properties("files.properties");

        Path root = fixture(gangShop, live);
        TrialsBotaniaRestart.install(root);
        for (String key : manifest.stringPropertyNames()) {
            String name = key.substring(4);
            if (key.startsWith("new.")) TrialsBotaniaRestart.check(root.resolve(name), manifest.getProperty(key));
            if (key.startsWith("old.")) TrialsBotaniaRestart.check(root.resolve(TrialsBotaniaRestart.BACKUP).resolve(name), manifest.getProperty(key));
        }
        require(!Files.exists(root.resolve("mods/gangshop-1.0.9.jar")), "Gang Shop 1.0.9 was not removed.");
        require(Files.exists(root.resolve(TrialsBotaniaRestart.BACKUP).resolve("APPLIED.txt")), "Marker missing.");
        TrialsBotaniaRestart.install(root);

        // An interrupted run (backups taken, part of the payload written) resumes.
        Files.delete(root.resolve(TrialsBotaniaRestart.BACKUP).resolve("APPLIED.txt"));
        Files.delete(root.resolve("mods/gangstrials-1.0.7.jar"));
        TrialsBotaniaRestart.install(root);
        require(Files.exists(root.resolve("mods/gangstrials-1.0.7.jar")), "Resume did not reinstall the payload.");
        delete(root);

        // A config edited after staging blocks the whole update and changes nothing.
        root = fixture(gangShop, live);
        Files.writeString(root.resolve("config/lootr.json"), "{}");
        expectFailure(root, "File changed since staging");
        require(Files.exists(root.resolve("mods/gangshop-1.0.9.jar")), "Failed update removed Gang Shop 1.0.9.");
        require(!Files.exists(root.resolve("mods/gangstrials-1.0.7.jar")), "Failed update installed mods.");
        require(!Files.exists(root.resolve(TrialsBotaniaRestart.BACKUP)), "Failed update created backups.");
        delete(root);

        // An unexpected Gang Shop version blocks the update.
        root = fixture(gangShop, live);
        Files.writeString(root.resolve("mods/gangshop-1.0.8.jar"), "x");
        expectFailure(root, "Unexpected active mod");
        delete(root);
        System.out.println("TrialsBotaniaRestart tests passed.");
    }

    static Path fixture(Path gangShop, Path live) throws Exception {
        Path root = Files.createTempDirectory("trials-botania-restart");
        Files.createDirectories(root.resolve("mods"));
        Files.createDirectories(root.resolve("config/gangshop"));
        Files.createDirectories(root.resolve("world"));
        Files.writeString(root.resolve("world/session.lock"), "");
        Files.copy(gangShop, root.resolve("mods/gangshop-1.0.9.jar"));
        Files.copy(live.resolve("lootr.json"), root.resolve("config/lootr.json"));
        Files.copy(live.resolve("overrides.json"), root.resolve("config/gangshop/overrides.json"));
        return root;
    }

    static void expectFailure(Path root, String message) throws Exception {
        try {
            TrialsBotaniaRestart.install(root);
        } catch (IllegalStateException expected) {
            require(expected.getMessage().contains(message), "Unexpected failure: " + expected.getMessage());
            return;
        }
        throw new AssertionError("Expected failure: " + message);
    }

    static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static void delete(Path root) throws Exception {
        try (var files = Files.walk(root)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
        }
    }
}
