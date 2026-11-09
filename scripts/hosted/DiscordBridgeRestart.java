import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

public final class DiscordBridgeRestart {
    static final String JAR = "dcintegration-fabric-3.0.7-1.20.1.jar";
    static final String SHA256 = "8b7c543042b0316f35f224c6437e78a63c8b298eb549fac85965754875f0b00c";
    static final String NEXT = "gangs-chefcompat-restart-1.0.2.jar";
    static final String NEXT_SHA256 = "ceb7ff7e49b85c8c19616ba1606f3f1e5e92d398cb2e5d0d846767dfff7c5154";
    static final String FABRIC_SHA256 = "6f921315fe2f570c331ca877d1e815c29b93fdc4ad04ce0f0f5cdf4300710028";

    public static void main(String[] args) throws Throwable {
        Path root = Path.of(".").toAbsolutePath().normalize();
        check(root.resolve("fabric-server-launch.jar"), FABRIC_SHA256);
        check(root.resolve(NEXT), NEXT_SHA256);
        requireConfig(root);
        install(root, payload());
        System.out.println("[DiscordBridgeRestart] Handing off to " + NEXT + ".");
        ChefCompatRestart.main(args);
    }

    static void requireConfig(Path root) {
        if (!Files.isRegularFile(root.resolve(Path.of("config", "Discord-Integration.toml")))
                || !Files.isRegularFile(root.resolve(Path.of("DiscordIntegration-Data", "Messages.toml")))) {
            throw new IllegalStateException("Discord bridge configuration is missing; install both chat-only files first.");
        }
    }

    static void install(Path root, byte[] payload) throws Exception {
        if (!digest(payload).equals(SHA256)) throw new IllegalStateException("Corrupt Discord bridge payload.");
        Path mods = root.resolve("mods");
        try (FileChannel channel = FileChannel.open(root.resolve(Path.of("world", "session.lock")),
                StandardOpenOption.WRITE); var lock = channel.tryLock()) {
            if (lock == null) throw new IllegalStateException("World is still running.");
            try (var files = Files.list(mods)) {
                var unexpected = files.map(path -> path.getFileName().toString())
                        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith("dcintegration")
                                && name.toLowerCase(Locale.ROOT).endsWith(".jar") && !name.equals(JAR))
                        .toList();
                if (!unexpected.isEmpty()) throw new IllegalStateException("Unexpected Discord bridge jars: " + unexpected);
            }
            Path target = mods.resolve(JAR);
            if (Files.exists(target)) {
                check(target, SHA256);
                System.out.println("[DiscordBridgeRestart] Discord bridge already installed and verified.");
                return;
            }
            Path temporary = Files.createTempFile(mods, "dcintegration-", ".tmp");
            try {
                Files.write(temporary, payload);
                check(temporary, SHA256);
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temporary);
            }
            check(target, SHA256);
            System.out.println("[DiscordBridgeRestart] Discord bridge installed and verified.");
        }
    }

    static void check(Path file, String expected) throws Exception {
        if (!Files.isRegularFile(file) || !digest(Files.readAllBytes(file)).equals(expected)) {
            throw new IllegalStateException("Missing file or unexpected SHA-256: " + file);
        }
    }

    static String digest(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    static byte[] payload() throws IOException {
        try (InputStream stream = DiscordBridgeRestart.class.getResourceAsStream("/discordbridge-payload/mod.jar")) {
            if (stream == null) throw new IOException("Missing Discord bridge payload.");
            return stream.readAllBytes();
        }
    }
}
