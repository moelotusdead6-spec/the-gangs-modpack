import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Properties;
import java.util.jar.JarFile;

/**
 * Restart launcher for Simple Voice Chat 2.6.25. While the world is not running it installs the
 * official jar and a server config that reuses the game port for UDP voice, then starts the original
 * Fabric launcher. Voice chat is optional: if any check fails nothing is changed and the server
 * still starts normally without it.
 */
public final class VoiceChatRestart {
    static final Path BACKUP = Path.of("backups/voicechat-restart-1.0.66");
    static final String FABRIC_LAUNCHER_SHA256 = "6f921315fe2f570c331ca877d1e815c29b93fdc4ad04ce0f0f5cdf4300710028";
    static final String JAR = "mods/voicechat-fabric-1.20.1-2.6.25.jar";
    static final String CONFIG = "config/voicechat/voicechat-server.properties";
    static final String CLAIM_MARKER = "backups/claim-trust-restart-1.0.65/APPLIED.txt";
    static final String APPLIED = "Simple Voice Chat 2.6.25; UDP voice on the Minecraft server port.\n";

    public static void main(String[] args) throws Throwable {
        Path root = Path.of(".").toAbsolutePath().normalize();
        check(root.resolve("fabric-server-launch.jar"), FABRIC_LAUNCHER_SHA256);
        try {
            install(root);
        } catch (Exception failure) {
            System.out.println("[VoiceChatRestart] Voice chat NOT installed; starting normally. Reason: "
                + failure.getMessage());
        }
        String main;
        try (JarFile launcher = new JarFile("fabric-server-launch.jar")) {
            main = launcher.getManifest().getMainAttributes().getValue("Main-Class");
        }
        if (main == null || main.equals(VoiceChatRestart.class.getName())) {
            throw new IllegalStateException("Invalid original Fabric launcher.");
        }
        System.setProperty("java.class.path", Path.of("fabric-server-launch.jar").toAbsolutePath().toString());
        System.out.println("[VoiceChatRestart] Starting original Fabric launcher.");
        try {
            Class.forName(main).getMethod("main", String[].class).invoke(null, (Object)args);
        } catch (java.lang.reflect.InvocationTargetException failure) {
            throw failure.getCause();
        }
    }

    static void install(Path root) throws Exception {
        Properties next = properties("files.properties");
        for (String key : new String[] {JAR, CONFIG}) {
            if (!next.containsKey(key)) throw new IllegalStateException("Incomplete restart manifest: " + key);
            try (InputStream stream = payload(key)) {
                if (!digest(stream.readAllBytes()).equals(next.getProperty(key))) {
                    throw new IllegalStateException("Corrupt payload: " + key);
                }
            }
        }
        try (FileChannel channel = FileChannel.open(root.resolve("world/session.lock"), StandardOpenOption.WRITE);
             var lock = channel.tryLock()) {
            if (lock == null) throw new IllegalStateException("World is still running.");
            Path backup = root.resolve(BACKUP);
            Path marker = backup.resolve("APPLIED.txt");
            requireOnlyVoiceJar(root);
            if (Files.exists(marker)) {
                // The mod rewrites its config with all keys on first start, so only the jar is pinned.
                check(root.resolve(JAR), next.getProperty(JAR));
                if (!Files.exists(root.resolve(CONFIG))) throw new IllegalStateException("Voice chat config is missing.");
                System.out.println("[VoiceChatRestart] Voice chat already installed and verified.");
                return;
            }
            if (!Files.exists(root.resolve(CLAIM_MARKER))) {
                throw new IllegalStateException("Claim trust update is not installed.");
            }
            requireUdpPortFree(root);
            Path jar = root.resolve(JAR);
            if (Files.exists(jar) && !hash(jar).equals(next.getProperty(JAR))) {
                throw new IllegalStateException("Unexpected voice chat jar: " + JAR);
            }
            Path config = root.resolve(CONFIG);
            if (Files.exists(config) && !hash(config).equals(next.getProperty(CONFIG))) {
                throw new IllegalStateException("Existing voice chat config would be replaced: " + CONFIG);
            }
            Files.createDirectories(backup);
            for (String target : new String[] {JAR, CONFIG}) {
                if (!Files.exists(root.resolve(target))) {
                    try (InputStream stream = payload(target)) {
                        write(root.resolve(target), stream.readAllBytes(), next.getProperty(target));
                    }
                }
            }
            check(jar, next.getProperty(JAR));
            check(config, next.getProperty(CONFIG));
            Files.writeString(marker, APPLIED, StandardOpenOption.CREATE_NEW);
            System.out.println("[VoiceChatRestart] Voice chat installed; marker in " + BACKUP);
        }
    }

    static void requireOnlyVoiceJar(Path root) throws Exception {
        String expected = Path.of(JAR).getFileName().toString();
        try (var files = Files.list(root.resolve("mods"))) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                if (name.startsWith("voicechat") && name.endsWith(".jar") && !name.equals(expected)) {
                    throw new IllegalStateException("Unexpected active voice chat mod: " + name);
                }
            }
        }
    }

    /** port=-1 shares the game port number, so server query must not also listen on it over UDP. */
    static void requireUdpPortFree(Path root) throws Exception {
        Properties server = new Properties();
        try (var reader = Files.newBufferedReader(root.resolve("server.properties"), StandardCharsets.ISO_8859_1)) {
            server.load(reader);
        }
        String gamePort = server.getProperty("server-port", "25565").trim();
        boolean query = Boolean.parseBoolean(server.getProperty("enable-query", "false").trim());
        String queryPort = server.getProperty("query.port", "25565").trim();
        if (query && queryPort.equals(gamePort)) {
            throw new IllegalStateException("Server query uses UDP port " + gamePort + ".");
        }
        try (var files = Files.list(root.resolve("mods"))) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString().toLowerCase();
                if (name.endsWith(".jar") && name.contains("geyser")) {
                    throw new IllegalStateException("Geyser may use UDP port " + gamePort + ".");
                }
            }
        }
    }

    static void write(Path destination, byte[] bytes, String expected) throws Exception {
        if (!digest(bytes).equals(expected)) throw new IllegalStateException("Unexpected bytes for " + destination);
        Files.createDirectories(destination.getParent());
        Path temporary = destination.resolveSibling(destination.getFileName() + ".voicechat-tmp");
        Files.write(temporary, bytes);
        try {
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, destination);
        }
        check(destination, expected);
    }

    static Properties properties(String name) throws Exception {
        Properties result = new Properties();
        try (InputStream stream = payload(name)) {
            result.load(new java.io.InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        return result;
    }

    static InputStream payload(String name) {
        InputStream stream = VoiceChatRestart.class.getResourceAsStream("/payload/" + name);
        if (stream == null) throw new IllegalStateException("Missing payload: " + name);
        return stream;
    }

    static void check(Path file, String expected) throws Exception {
        if (!Files.exists(file) || !hash(file).equals(expected)) {
            throw new IllegalStateException("Unexpected file hash: " + file);
        }
    }

    static String hash(Path file) throws Exception {
        return digest(Files.readAllBytes(file));
    }

    static String digest(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
