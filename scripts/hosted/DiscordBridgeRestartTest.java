import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class DiscordBridgeRestartTest {
    private static final List<Path> ROOTS = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        byte[] payload = Files.readAllBytes(Path.of(args[0]));
        try {
            Path root = root();
            expectFailure(() -> DiscordBridgeRestart.requireConfig(root), "configuration is missing");
            Files.createDirectories(root.resolve("config"));
            Files.createDirectories(root.resolve("DiscordIntegration-Data"));
            Files.writeString(root.resolve(Path.of("config", "Discord-Integration.toml")), "test");
            expectFailure(() -> DiscordBridgeRestart.requireConfig(root), "configuration is missing");
            Files.writeString(root.resolve(Path.of("DiscordIntegration-Data", "Messages.toml")), "test");
            DiscordBridgeRestart.requireConfig(root);
            DiscordBridgeRestart.install(root, payload);
            DiscordBridgeRestart.install(root, payload);
            DiscordBridgeRestart.check(root.resolve("mods").resolve(DiscordBridgeRestart.JAR), DiscordBridgeRestart.SHA256);
            Path corruptRoot = root();
            byte[] corrupt = payload.clone();
            corrupt[0] ^= 1;
            expectFailure(() -> DiscordBridgeRestart.install(corruptRoot, corrupt), "Corrupt Discord bridge payload");
            require(!Files.exists(corruptRoot.resolve("mods").resolve(DiscordBridgeRestart.JAR)));
            Path extraRoot = root();
            Path extra = extraRoot.resolve(Path.of("mods", "dcintegration-old.jar"));
            Files.writeString(extra, "unchanged");
            expectFailure(() -> DiscordBridgeRestart.install(extraRoot, payload), "Unexpected Discord bridge jars");
            require(Files.readString(extra).equals("unchanged"));
            Path changedRoot = root();
            Path changed = changedRoot.resolve("mods").resolve(DiscordBridgeRestart.JAR);
            Files.writeString(changed, "unchanged");
            expectFailure(() -> DiscordBridgeRestart.install(changedRoot, payload), "unexpected SHA-256");
            require(Files.readString(changed).equals("unchanged"));
            Path lockedRoot = root();
            try (FileChannel channel = FileChannel.open(lockedRoot.resolve(Path.of("world", "session.lock")),
                    StandardOpenOption.WRITE); var lock = channel.lock()) {
                try {
                    DiscordBridgeRestart.install(lockedRoot, payload);
                    throw new AssertionError("Running world allowed installation.");
                } catch (OverlappingFileLockException expected) {
                    require(!Files.exists(lockedRoot.resolve("mods").resolve(DiscordBridgeRestart.JAR)));
                }
            }
            System.out.println("DiscordBridgeRestartTest passed");
        } finally {
            for (Path root : ROOTS) {
                try (var paths = Files.walk(root)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
                }
            }
        }
    }

    private static Path root() throws Exception {
        Path root = Files.createTempDirectory("discord-bridge-restart-test-");
        ROOTS.add(root);
        Files.createDirectories(root.resolve("mods"));
        Files.createDirectories(root.resolve("world"));
        Files.writeString(root.resolve(Path.of("world", "session.lock")), "");
        return root;
    }

    private interface Action { void run() throws Exception; }

    private static void expectFailure(Action action, String message) throws Exception {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            require(expected.getMessage().contains(message));
            return;
        }
        throw new AssertionError("Expected failure: " + message);
    }

    private static void require(boolean condition) {
        if (!condition) throw new AssertionError("Discord bridge restart test failed.");
    }
}
