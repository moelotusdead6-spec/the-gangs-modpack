import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarFile;

/**
 * Installs passive-spawn configuration while the world is stopped, then hands off to the
 * existing Mimicream restart launcher. Later starts leave the mods' current configs alone.
 */
public final class PassiveSpawnRestart {
    static final Path BACKUP = Path.of("backups/passive-spawn-restart-1.0.69");
    static final String FABRIC = "fabric-server-launch.jar";
    static final String FABRIC_SHA256 = "6f921315fe2f570c331ca877d1e815c29b93fdc4ad04ce0f0f5cdf4300710028";
    static final String CHAINED = "gangs-mimicream-restart-1.0.67.jar";
    static final String CHAINED_SHA256 = "95cc10a794dc7d4300dce62b561f23f6482b8c6a9fd0690dd0dca3966c33afd6";
    static final String WILDLIFE = "config/WildlifeConfig.json";
    static final String WILDERNATURE = "config/wildernature.json";
    static final List<String> TARGETS = List.of(WILDLIFE, WILDERNATURE);
    static final Map<String, String> PREVIOUS = Map.of(
        WILDLIFE, "8e082f291718541a5f2b9973683926e0701ac9757b05c1157c2017ac9135814e",
        WILDERNATURE, "ebef10b2bbbc025766fb34b1cd31c46f3fbc3d35454144aa8e2a3dca3f33a01b");

    public static void main(String[] args) throws Throwable {
        Path root = Path.of(".").toAbsolutePath().normalize();
        try {
            check(root.resolve(FABRIC), FABRIC_SHA256);
            install(root);
        } catch (Exception failure) {
            System.out.println("[PassiveSpawnRestart] Update not fully applied; starting normally. Reason: "
                + failure.getMessage());
        }

        Path chained = root.resolve(CHAINED);
        if (Files.isRegularFile(chained) && hash(chained).equals(CHAINED_SHA256)) {
            System.out.println("[PassiveSpawnRestart] Handing off to " + CHAINED + ".");
            invokeMain(Class.forName("MimicreamRestart"), args);
            return;
        }
        System.out.println("[PassiveSpawnRestart] Pending Mimicream launcher missing or changed; "
            + "starting the original Fabric launcher.");
        String main;
        try (JarFile launcher = new JarFile(root.resolve(FABRIC).toFile())) {
            main = launcher.getManifest().getMainAttributes().getValue("Main-Class");
        }
        if (main == null || main.equals(PassiveSpawnRestart.class.getName())) {
            throw new IllegalStateException("Invalid original Fabric launcher.");
        }
        System.setProperty("java.class.path", root.resolve(FABRIC).toString());
        invokeMain(Class.forName(main), args);
    }

    static void invokeMain(Class<?> type, String[] args) throws Throwable {
        try {
            type.getMethod("main", String[].class).invoke(null, (Object) args);
        } catch (java.lang.reflect.InvocationTargetException failure) {
            throw failure.getCause();
        }
    }

    static void install(Path root) throws Exception {
        Properties next = properties();
        for (String target : TARGETS) {
            String expected = next.getProperty(target);
            if (expected == null) throw new IllegalStateException("Missing payload checksum: " + target);
            try (InputStream stream = payload(target)) {
                if (!digest(stream.readAllBytes()).equals(expected)) {
                    throw new IllegalStateException("Corrupt payload: " + target);
                }
            }
        }

        try (FileChannel channel = FileChannel.open(root.resolve("world/session.lock"), StandardOpenOption.WRITE);
             FileLock lock = acquireLock(channel)) {
            Path backup = root.resolve(BACKUP);
            Path marker = backup.resolve("APPLIED.txt");
            if (Files.exists(marker)) {
                for (String target : TARGETS) {
                    if (!Files.isRegularFile(root.resolve(target))) {
                        throw new IllegalStateException("Previously installed config is missing: " + target);
                    }
                }
                System.out.println("[PassiveSpawnRestart] Update already applied; current configs left unchanged.");
                return;
            }

            for (String target : TARGETS) {
                Path file = root.resolve(target);
                Path saved = backup.resolve(target);
                if (!Files.isRegularFile(file)) throw new IllegalStateException("Missing live config: " + target);
                String actual = hash(file);
                if (!actual.equals(PREVIOUS.get(target)) && !actual.equals(next.getProperty(target))) {
                    throw new IllegalStateException("Live config changed since staging: " + target);
                }
                if (Files.exists(saved)) {
                    check(saved, PREVIOUS.get(target));
                } else if (actual.equals(next.getProperty(target))) {
                    throw new IllegalStateException("Updated config has no original backup: " + target);
                }
            }

            for (String target : TARGETS) {
                Path file = root.resolve(target);
                Path saved = backup.resolve(target);
                if (!Files.exists(saved)) {
                    Files.createDirectories(saved.getParent());
                    Files.copy(file, saved);
                }
                check(saved, PREVIOUS.get(target));
                if (!hash(file).equals(next.getProperty(target))) {
                    try (InputStream stream = payload(target)) {
                        write(file, stream.readAllBytes(), next.getProperty(target));
                    }
                }
            }
            for (String target : TARGETS) check(root.resolve(target), next.getProperty(target));
            Files.writeString(marker, "Passive Wildlife habitats and vanilla animal spawns restored.\n",
                StandardOpenOption.CREATE_NEW);
            System.out.println("[PassiveSpawnRestart] Update installed; originals saved in " + BACKUP + ".");
        }
    }

    static FileLock acquireLock(FileChannel channel) throws Exception {
        try {
            FileLock lock = channel.tryLock();
            if (lock == null) throw new IllegalStateException("World is still running.");
            return lock;
        } catch (OverlappingFileLockException failure) {
            throw new IllegalStateException("World is still running.", failure);
        }
    }

    static void write(Path destination, byte[] bytes, String expected) throws Exception {
        if (!digest(bytes).equals(expected)) throw new IllegalStateException("Unexpected payload: " + destination);
        Path temporary = Files.createTempFile(destination.getParent(), ".passive-spawn-", ".tmp");
        try {
            Files.write(temporary, bytes);
            try {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        check(destination, expected);
    }

    static Properties properties() throws Exception {
        Properties result = new Properties();
        try (InputStream stream = payload("files.properties")) {
            result.load(stream);
        }
        return result;
    }

    static InputStream payload(String name) {
        InputStream stream = PassiveSpawnRestart.class.getClassLoader()
            .getResourceAsStream("passive-spawn-payload/" + name);
        if (stream == null) throw new IllegalStateException("Missing payload: " + name);
        return stream;
    }

    static void check(Path file, String expected) throws Exception {
        if (!Files.isRegularFile(file) || !hash(file).equals(expected)) {
            throw new IllegalStateException("Missing or unexpected file: " + file);
        }
    }

    static String hash(Path file) throws Exception {
        return digest(Files.readAllBytes(file));
    }

    static String digest(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
