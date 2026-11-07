import java.nio.file.Files;
import java.nio.file.Path;

/** Usage: EndPortal2RestartTest <goldclaim-1.0.68.jar> */
public final class EndPortal2RestartTest {
    public static void main(String[] args) throws Exception {
        Path liveJar = Path.of(args[0]);
        Path root = Files.createTempDirectory("end-portal-restart");
        Files.createDirectories(root.resolve("mods"));
        Files.createDirectories(root.resolve("world"));
        Files.writeString(root.resolve("world/session.lock"), "");
        Files.copy(liveJar, root.resolve("mods/goldclaim-1.0.68.jar"));

        Files.writeString(root.resolve("mods/goldclaim-1.0.66.jar"), "stray");
        expectFailure(() -> EndPortal2Restart.install(root), "Unexpected active mod: goldclaim-1.0.66.jar");
        require(Files.exists(root.resolve("mods/goldclaim-1.0.68.jar")), "live jar kept on unexpected mod");
        Files.delete(root.resolve("mods/goldclaim-1.0.66.jar"));

        EndPortal2Restart.install(root);
        require(!Files.exists(root.resolve("mods/goldclaim-1.0.68.jar")), "old jar removed");
        require(Files.exists(root.resolve("mods/goldclaim-1.0.69.jar")), "new jar installed");
        require(Files.exists(root.resolve(EndPortal2Restart.BACKUP).resolve("mods/goldclaim-1.0.68.jar")), "old jar backed up");
        require(Files.exists(root.resolve(EndPortal2Restart.BACKUP).resolve("APPLIED.txt")), "marker written");
        EndPortal2Restart.install(root);

        Files.writeString(root.resolve("mods/goldclaim-1.0.69.jar"), "tampered");
        expectFailure(() -> EndPortal2Restart.install(root), "Unexpected file hash");
        System.out.println("EndPortal2RestartTest passed");
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
