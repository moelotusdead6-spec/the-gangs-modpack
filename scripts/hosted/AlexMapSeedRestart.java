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
 * Restart launcher for Alex's Caves 2.1.8-gangs-levelseed (cave biomes and cave maps use each level's own
 * seed, so they work in multiworld:rsw and the overworld at the same time). While the world is not running
 * it replaces mods/alexscaves-2.1.8.jar with the patched jar, keeping the original in BACKUP, then hands
 * off to the still-pending gangs-alex-boss-restart-1.0.70.jar (which runs the passive-spawn and Mimicream
 * launchers) so those updates are applied on the same start. If any check fails nothing is changed and
 * the server still starts normally.
 */
public final class AlexMapSeedRestart {
    static final Path BACKUP = Path.of("backups/alex-map-seed-restart-1.0.71");
    static final String FABRIC_LAUNCHER_SHA256 = "6f921315fe2f570c331ca877d1e815c29b93fdc4ad04ce0f0f5cdf4300710028";
    static final String CHAINED = "gangs-alex-boss-restart-1.0.70.jar";
    static final String CHAINED_SHA256 = "01d5523cba42dd37543463a227cdfba28529639aa6978bbfff891525e2fcd836";
    static final String OLD_CAVES = "mods/alexscaves-2.1.8.jar";
    static final String NEW_CAVES = "mods/alexscaves-2.1.8-gangs-levelseed.jar";
    static final String APPLIED = "Alex's Caves 2.1.8-gangs-levelseed: per-level cave biome seed for maps and generation.\n";

    public static void main(String[] args) throws Throwable {
        Path root = Path.of(".").toAbsolutePath().normalize();
        check(root.resolve("fabric-server-launch.jar"), FABRIC_LAUNCHER_SHA256);
        try {
            install(root);
        } catch (Exception failure) {
            System.out.println("[AlexMapSeedRestart] Update NOT applied; starting normally. Reason: " + failure.getMessage());
        }
        Path chained = root.resolve(CHAINED);
        if (Files.exists(chained) && hash(chained).equals(CHAINED_SHA256)) {
            System.out.println("[AlexMapSeedRestart] Handing off to " + CHAINED + ".");
            invokeMain(Class.forName("AlexBossRestart"), args);
            return;
        }
        System.out.println("[AlexMapSeedRestart] " + CHAINED + " missing or changed; starting original Fabric launcher.");
        String main;
        try (JarFile launcher = new JarFile("fabric-server-launch.jar")) {
            main = launcher.getManifest().getMainAttributes().getValue("Main-Class");
        }
        if (main == null || main.equals(AlexMapSeedRestart.class.getName())) {
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
        String oldHash = hashes.getProperty(OLD_CAVES);
        String newHash = hashes.getProperty(NEW_CAVES);
        if (oldHash == null || newHash == null) throw new IllegalStateException("Incomplete restart manifest.");
        try (InputStream stream = payload(NEW_CAVES)) {
            if (!digest(stream.readAllBytes()).equals(newHash)) throw new IllegalStateException("Corrupt payload.");
        }
        try (FileChannel channel = FileChannel.open(root.resolve("world/session.lock"), StandardOpenOption.WRITE);
             var lock = channel.tryLock()) {
            if (lock == null) throw new IllegalStateException("World is still running.");
            Path oldFile = root.resolve(OLD_CAVES);
            Path newFile = root.resolve(NEW_CAVES);
            Path saved = root.resolve(BACKUP).resolve(OLD_CAVES);
            Path marker = root.resolve(BACKUP).resolve("APPLIED.txt");
            requireOnlyAlexsCaves(root);
            if (Files.exists(marker)) {
                verifyInstalled(root, oldHash, newHash);
                System.out.println("[AlexMapSeedRestart] Update already installed and verified.");
                return;
            }

            // Validate everything before changing anything; a partial earlier run is resumable.
            if (Files.exists(oldFile)) {
                if (!hash(oldFile).equals(oldHash)) throw new IllegalStateException("File changed since staging: " + OLD_CAVES);
            } else if (!Files.exists(saved) || !hash(saved).equals(oldHash)) {
                throw new IllegalStateException("Original file and backup are missing: " + OLD_CAVES);
            }
            if (Files.exists(newFile) && !hash(newFile).equals(newHash)) {
                throw new IllegalStateException("Unexpected target file: " + NEW_CAVES);
            }

            if (!Files.exists(saved)) {
                Files.createDirectories(saved.getParent());
                Files.copy(oldFile, saved);
            }
            check(saved, oldHash);
            if (!Files.exists(newFile)) {
                try (InputStream stream = payload(NEW_CAVES)) {
                    write(newFile, stream.readAllBytes(), newHash);
                }
            }
            Files.deleteIfExists(oldFile);
            verifyInstalled(root, oldHash, newHash);
            Files.writeString(marker, APPLIED, StandardOpenOption.CREATE_NEW);
            System.out.println("[AlexMapSeedRestart] Update installed; original saved in " + BACKUP);
        }
    }

    static void requireOnlyAlexsCaves(Path root) throws Exception {
        try (var files = Files.list(root.resolve("mods"))) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                if (name.matches("alexscaves-\\d.*\\.jar")
                        && !name.equals("alexscaves-2.1.8.jar") && !name.equals("alexscaves-2.1.8-gangs-levelseed.jar")) {
                    throw new IllegalStateException("Unexpected active mod: " + name);
                }
            }
        }
    }

    static void verifyInstalled(Path root, String oldHash, String newHash) throws Exception {
        check(root.resolve(NEW_CAVES), newHash);
        if (Files.exists(root.resolve(OLD_CAVES))) throw new IllegalStateException("Old Alex's Caves jar is still active.");
        check(root.resolve(BACKUP).resolve(OLD_CAVES), oldHash);
    }

    static void write(Path destination, byte[] bytes, String expected) throws Exception {
        if (!digest(bytes).equals(expected)) throw new IllegalStateException("Unexpected bytes for " + destination);
        Files.createDirectories(destination.getParent());
        Path temporary = destination.resolveSibling(destination.getFileName() + ".alexmapseed-tmp");
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
        InputStream stream = AlexMapSeedRestart.class.getResourceAsStream("/alexmapseed-payload/" + name);
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
