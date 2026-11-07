import java.nio.file.Files;
import java.nio.file.Path;

/** Usage: EndPortalRestartTest <goldclaim-1.0.66.jar> */
public final class EndPortalRestartTest {
    public static void main(String[] args) throws Exception {
        Path liveJar = Path.of(args[0]);
        Path root = Files.createTempDirectory("end-portal-restart");
        Files.createDirectories(root.resolve("mods"));
        Files.createDirectories(root.resolve("world"));
        Files.writeString(root.resolve("world/session.lock"), "");
        Files.copy(liveJar, root.resolve("mods/goldclaim-1.0.66.jar"));

        Files.writeString(root.resolve("mods/goldclaim-1.0.65.jar"), "stray");
        expectFailure(() -> EndPortalRestart.install(root), "Unexpected active mod: goldclaim-1.0.65.jar");
        require(Files.exists(root.resolve("mods/goldclaim-1.0.66.jar")), "live jar kept on unexpected mod");
        Files.delete(root.resolve("mods/goldclaim-1.0.65.jar"));

        EndPortalRestart.install(root);
        require(!Files.exists(root.resolve("mods/goldclaim-1.0.66.jar")), "old jar removed");
        require(Files.exists(root.resolve("mods/goldclaim-1.0.68.jar")), "new jar installed");
        require(Files.exists(root.resolve(EndPortalRestart.BACKUP).resolve("mods/goldclaim-1.0.66.jar")), "old jar backed up");
        require(Files.exists(root.resolve(EndPortalRestart.BACKUP).resolve("APPLIED.txt")), "marker written");
        EndPortalRestart.install(root);

        Files.writeString(root.resolve("mods/goldclaim-1.0.68.jar"), "tampered");
        expectFailure(() -> EndPortalRestart.install(root), "Unexpected file hash");
        System.out.println("EndPortalRestartTest passed");
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

    static void require(boolean condition, String what) {
        if (!condition) throw new AssertionError(what);
    }
}
