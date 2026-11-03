import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Properties;
import java.util.jar.JarFile;

/**
 * Restart launcher for GoldClaim 1.0.65 (Alex's Caves bosses never change blocks; boss health targets).
 * While the world is not running it replaces mods/goldclaim-1.0.64.jar with 1.0.65, keeping the original
 * in BACKUP, then hands off to the still-pending gangs-passive-spawn-restart-1.0.69.jar (which in turn runs
 * gangs-mimicream-restart-1.0.67.jar) so those updates are applied on the same start. If any check fails
 * nothing is changed and the server still starts normally.
 */
public final class AlexBossRestart {
    static final Path BACKUP = Path.of("backups/alex-boss-restart-1.0.70");
    static final String FABRIC_LAUNCHER_SHA256 = "6f921315fe2f570c331ca877d1e815c29b93fdc4ad04ce0f0f5cdf4300710028";
    static final String PASSIVE = "gangs-passive-spawn-restart-1.0.69.jar";
    static final String PASSIVE_SHA256 = "a209f6a8400431a6cc03ca3064f11ee32041244e846e9aae439d18525dcc7d87";
    static final String CHAINED = "gangs-mimicream-restart-1.0.67.jar";
    static final String CHAINED_SHA256 = "95cc10a794dc7d4300dce62b561f23f6482b8c6a9fd0690dd0dca3966c33afd6";
    static final String OLD_GOLDCLAIM = "mods/goldclaim-1.0.64.jar";
    static final String NEW_GOLDCLAIM = "mods/goldclaim-1.0.65.jar";
    static final String ATTRIBUTE_FIX = "AttributeFix-Fabric-";
    static final String APPLIED = "GoldClaim 1.0.65: Alex's Caves boss block protection and health targets.\n";

    public static void main(String[] args) throws Throwable {
        Path root = Path.of(".").toAbsolutePath().normalize();
        check(root.resolve("fabric-server-launch.jar"), FABRIC_LAUNCHER_SHA256);
        try {
            install(root);
        } catch (Exception failure) {
            System.out.println("[AlexBossRestart] Update NOT applied; starting normally. Reason: " + failure.getMessage());
        }
        Path passive = root.resolve(PASSIVE);
        if (Files.exists(passive) && hash(passive).equals(PASSIVE_SHA256)) {
            System.out.println("[AlexBossRestart] Handing off to " + PASSIVE + ".");
            invokeMain(Class.forName("PassiveSpawnRestart"), args);
            return;
        }
        Path chained = root.resolve(CHAINED);
        if (Files.exists(chained) && hash(chained).equals(CHAINED_SHA256)) {
            System.out.println("[AlexBossRestart] " + PASSIVE + " missing or changed; handing off to " + CHAINED + ".");
            invokeMain(Class.forName("MimicreamRestart"), args);
            return;
        }
        System.out.println("[AlexBossRestart] Chained launchers missing or changed; starting original Fabric launcher.");
        String main;
        try (JarFile launcher = new JarFile("fabric-server-launch.jar")) {
            main = launcher.getManifest().getMainAttributes().getValue("Main-Class");
        }
        if (main == null || main.equals(AlexBossRestart.class.getName())) {
            throw new IllegalStateException("Invalid original Fabric launcher.");
        }
        System.setProperty("java.class.path", Path.of("fabric-server-launch.jar").toAbsolutePath().toString());
        invokeMain(Class.forName(main), args);
    }

    static void invokeMain(Class<?> type, String[] args) throws Throwable {
        try {
            type.getMethod("main", String[].class).invoke(null, (Object)args);
        } catch (java.lang.reflect.InvocationTargetException failure) {
            throw failure.getCause();
        }
    }

    static void install(Path root) throws Exception {
        Properties hashes = properties("files.properties");
        String oldHash = hashes.getProperty(OLD_GOLDCLAIM);
        String newHash = hashes.getProperty(NEW_GOLDCLAIM);
        if (oldHash == null || newHash == null) throw new IllegalStateException("Incomplete restart manifest.");
        try (InputStream stream = payload(NEW_GOLDCLAIM)) {
            if (!digest(stream.readAllBytes()).equals(newHash)) throw new IllegalStateException("Corrupt payload.");
        }
        try (FileChannel channel = FileChannel.open(root.resolve("world/session.lock"), StandardOpenOption.WRITE);
             var lock = channel.tryLock()) {
            if (lock == null) throw new IllegalStateException("World is still running.");
            Path oldFile = root.resolve(OLD_GOLDCLAIM);
            Path newFile = root.resolve(NEW_GOLDCLAIM);
            Path saved = root.resolve(BACKUP).resolve(OLD_GOLDCLAIM);
            Path marker = root.resolve(BACKUP).resolve("APPLIED.txt");
            requireOnlyGoldClaim(root);
            // Above 1024 max health only works with AttributeFix; without it the bosses would be capped.
            requireAttributeFix(root);
            if (Files.exists(marker)) {
                verifyInstalled(root, oldHash, newHash);
                System.out.println("[AlexBossRestart] Update already installed and verified.");
                return;
            }

            // Validate everything before changing anything; a partial earlier run is resumable.
            if (Files.exists(oldFile)) {
                if (!hash(oldFile).equals(oldHash)) throw new IllegalStateException("File changed since staging: " + OLD_GOLDCLAIM);
            } else if (!Files.exists(saved) || !hash(saved).equals(oldHash)) {
                throw new IllegalStateException("Original file and backup are missing: " + OLD_GOLDCLAIM);
            }
            if (Files.exists(newFile) && !hash(newFile).equals(newHash)) {
                throw new IllegalStateException("Unexpected target file: " + NEW_GOLDCLAIM);
            }

            if (!Files.exists(saved)) {
                Files.createDirectories(saved.getParent());
                Files.copy(oldFile, saved);
            }
            check(saved, oldHash);
            if (!Files.exists(newFile)) {
                try (InputStream stream = payload(NEW_GOLDCLAIM)) {
                    write(newFile, stream.readAllBytes(), newHash);
                }
            }
            Files.deleteIfExists(oldFile);
            verifyInstalled(root, oldHash, newHash);
            Files.writeString(marker, APPLIED, StandardOpenOption.CREATE_NEW);
            System.out.println("[AlexBossRestart] Update installed; original saved in " + BACKUP);
        }
    }

    static void requireOnlyGoldClaim(Path root) throws Exception {
        try (var files = Files.list(root.resolve("mods"))) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                if (name.startsWith("goldclaim-") && name.endsWith(".jar")
                        && !name.equals("goldclaim-1.0.64.jar") && !name.equals("goldclaim-1.0.65.jar")) {
                    throw new IllegalStateException("Unexpected active mod: " + name);
                }
            }
        }
    }

    static void requireAttributeFix(Path root) throws Exception {
        try (var files = Files.list(root.resolve("mods"))) {
            if (files.noneMatch(file -> file.getFileName().toString().startsWith(ATTRIBUTE_FIX)
                    && file.getFileName().toString().endsWith(".jar"))) {
                throw new IllegalStateException("AttributeFix is not installed.");
            }
        }
    }

    static void verifyInstalled(Path root, String oldHash, String newHash) throws Exception {
        check(root.resolve(NEW_GOLDCLAIM), newHash);
        if (Files.exists(root.resolve(OLD_GOLDCLAIM))) throw new IllegalStateException("Old GoldClaim jar is still active.");
        check(root.resolve(BACKUP).resolve(OLD_GOLDCLAIM), oldHash);
    }

    static void write(Path destination, byte[] bytes, String expected) throws Exception {
        if (!digest(bytes).equals(expected)) throw new IllegalStateException("Unexpected bytes for " + destination);
        Files.createDirectories(destination.getParent());
        Path temporary = destination.resolveSibling(destination.getFileName() + ".alexboss-tmp");
        Files.write(temporary, bytes);
        try {
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
        }
        check(destination, expected);
    }

    static Properties properties(String name) throws Exception {
        Properties result = new Properties();
        try (InputStream stream = payload(name)) {
            result.load(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8));
        }
        return result;
    }

    static InputStream payload(String name) {
        InputStream stream = AlexBossRestart.class.getResourceAsStream("/alexboss-payload/" + name);
        if (stream == null) throw new IllegalStateException("Missing payload: " + name);
        return stream;
    }

    static void check(Path file, String expected) throws Exception {
        if (!Files.exists(file) || !hash(file).equals(expected)) {
            throw new IllegalStateException("Unexpected file hash: " + file);
        }
    }

    static String hash(Path file) throws Exception {
        try (InputStream stream = Files.newInputStream(file)) {
            return digest(stream.readAllBytes());
        }
    }

    static String digest(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
