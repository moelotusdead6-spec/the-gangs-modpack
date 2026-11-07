import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.jar.JarFile;

/**
 * Restart launcher for Trial Chambers (RSW only), Botania and indestructible Lootr containers. While the world is
 * not running it installs every "new.*" payload file from files.properties and moves every "old.*" file (checked by
 * SHA-256) into BACKUP: Gang Shop 1.0.9 becomes 1.0.10, and the live lootr.json and Gang Shop overrides are replaced
 * by staged copies made from them. It also swaps the live Alex's Caves jar for the level-seed build, which the
 * 1.0.71 launcher refused because it only accepted a newer original. A "src.*" entry names a class path resource to
 * read a payload file from (the level-seed jar is taken from gangs-alex-map-seed-restart-1.0.71.jar). It then hands
 * off to gangs-oux-badges-restart-1.0.72.jar, which continues the existing launcher chain. If any check fails nothing
 * is changed and the server still starts normally.
 */
public final class TrialsBotaniaRestart {
    static final Path BACKUP = Path.of("backups/trials-botania-restart-1.0.74");
    static final String RESOURCES = "/trialsbotania-payload/";
    static final String FABRIC_LAUNCHER_SHA256 = "6f921315fe2f570c331ca877d1e815c29b93fdc4ad04ce0f0f5cdf4300710028";
    static final String OUX_BADGES = "gangs-oux-badges-restart-1.0.72.jar";
    static final String OUX_BADGES_SHA256 = "befda734de424031052c8172df52479c126c45116591265100d795884cbe2a6d";
    static final String APPLIED = "Trial Chambers, Botania, Gang Shop 1.0.10, Lootr protection and Alex's Caves level seed installed.\n";

    public static void main(String[] args) throws Throwable {
        Path root = Path.of(".").toAbsolutePath().normalize();
        check(root.resolve("fabric-server-launch.jar"), FABRIC_LAUNCHER_SHA256);
        try {
            install(root);
        } catch (Exception failure) {
            System.out.println("[TrialsBotaniaRestart] Update NOT applied; starting normally. Reason: " + failure.getMessage());
        }
        Path oux = root.resolve(OUX_BADGES);
        if (Files.exists(oux) && hash(oux).equals(OUX_BADGES_SHA256)) {
            System.out.println("[TrialsBotaniaRestart] Handing off to " + OUX_BADGES + ".");
            invokeMain(Class.forName("OuxBadgesRestart"), args);
            return;
        }
        System.out.println("[TrialsBotaniaRestart] Chained launcher missing or changed; starting original Fabric launcher.");
        String main;
        try (JarFile launcher = new JarFile("fabric-server-launch.jar")) {
            main = launcher.getManifest().getMainAttributes().getValue("Main-Class");
        }
        if (main == null || main.equals(TrialsBotaniaRestart.class.getName())) {
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
        Map<String, String> oldFiles = new TreeMap<>();
        Map<String, String> newFiles = new TreeMap<>();
        Properties manifest = properties("files.properties");
        for (String key : manifest.stringPropertyNames()) {
            if (key.startsWith("old.")) oldFiles.put(key.substring(4), manifest.getProperty(key));
            else if (key.startsWith("new.")) newFiles.put(key.substring(4), manifest.getProperty(key));
        }
        if (oldFiles.isEmpty() || newFiles.isEmpty()) throw new IllegalStateException("Incomplete restart manifest.");
        for (Map.Entry<String, String> file : newFiles.entrySet()) {
            try (InputStream stream = payload(file.getKey())) {
                if (!digest(stream.readAllBytes()).equals(file.getValue())) throw new IllegalStateException("Corrupt payload.");
            }
        }
        try (FileChannel channel = FileChannel.open(root.resolve("world/session.lock"), StandardOpenOption.WRITE);
             var lock = channel.tryLock()) {
            if (lock == null) throw new IllegalStateException("World is still running.");
            Path marker = root.resolve(BACKUP).resolve("APPLIED.txt");
            requireOnly(root, "gangshop-", "gangshop-1.0.9.jar", "gangshop-1.0.10.jar");
            requireOnly(root, "gangstrials-", "gangstrials-1.0.7.jar");
            requireOnly(root, "Botania-", "Botania-1.20.1-456-FABRIC.jar");
            requireOnly(root, "trickytrialsbackport-", "trickytrialsbackport-fabric-2026.1-gangs.1.jar");
            requireOnly(root, "alexscaves-", "alexscaves-2.1.8.jar", "alexscaves-2.1.8-gangs-levelseed.jar");
            if (Files.exists(marker)) {
                verifyInstalled(root, oldFiles, newFiles);
                System.out.println("[TrialsBotaniaRestart] Update already installed and verified.");
                return;
            }

            // Validate everything before changing anything; a partial earlier run is resumable.
            for (Map.Entry<String, String> file : oldFiles.entrySet()) {
                Path live = root.resolve(file.getKey());
                Path saved = root.resolve(BACKUP).resolve(file.getKey());
                String installed = newFiles.get(file.getKey());
                if (Files.exists(live)) {
                    String current = hash(live);
                    if (current.equals(file.getValue())) continue;
                    if (installed != null && current.equals(installed) && Files.exists(saved)) continue;
                    throw new IllegalStateException("File changed since staging: " + file.getKey());
                }
                if (!Files.exists(saved) || !hash(saved).equals(file.getValue())) {
                    throw new IllegalStateException("Original file and backup are missing: " + file.getKey());
                }
            }
            for (Map.Entry<String, String> file : newFiles.entrySet()) {
                Path target = root.resolve(file.getKey());
                if (!oldFiles.containsKey(file.getKey()) && Files.exists(target) && !hash(target).equals(file.getValue())) {
                    throw new IllegalStateException("Unexpected target file: " + file.getKey());
                }
            }

            for (Map.Entry<String, String> file : oldFiles.entrySet()) {
                Path live = root.resolve(file.getKey());
                if (Files.exists(live) && hash(live).equals(file.getValue())) backup(root, file.getKey(), file.getValue());
            }
            for (Map.Entry<String, String> file : newFiles.entrySet()) {
                Path target = root.resolve(file.getKey());
                if (!Files.exists(target) || !hash(target).equals(file.getValue())) {
                    try (InputStream stream = payload(file.getKey())) {
                        write(target, stream.readAllBytes(), file.getValue());
                    }
                }
            }
            for (String name : oldFiles.keySet()) {
                if (!newFiles.containsKey(name)) Files.deleteIfExists(root.resolve(name));
            }
            verifyInstalled(root, oldFiles, newFiles);
            Files.writeString(marker, APPLIED, StandardOpenOption.CREATE_NEW);
            System.out.println("[TrialsBotaniaRestart] Update installed; originals saved in " + BACKUP);
        }
    }

    static void backup(Path root, String name, String expected) throws Exception {
        Path saved = root.resolve(BACKUP).resolve(name);
        if (!Files.exists(saved)) {
            Files.createDirectories(saved.getParent());
            Files.copy(root.resolve(name), saved);
        }
        check(saved, expected);
    }

    static void requireOnly(Path root, String prefix, String... allowed) throws Exception {
        try (var files = Files.list(root.resolve("mods"))) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                if (name.startsWith(prefix) && name.endsWith(".jar") && !List.of(allowed).contains(name)) {
                    throw new IllegalStateException("Unexpected active mod: " + name);
                }
            }
        }
    }

    static void verifyInstalled(Path root, Map<String, String> oldFiles, Map<String, String> newFiles) throws Exception {
        for (Map.Entry<String, String> file : newFiles.entrySet()) {
            Path target = root.resolve(file.getKey());
            // Mods may rewrite their configs on load and admins may tune them, so only their presence is checked.
            if (file.getKey().startsWith("config/")) {
                if (!Files.exists(target)) throw new IllegalStateException("Missing " + file.getKey());
            } else {
                check(target, file.getValue());
            }
        }
        for (Map.Entry<String, String> file : oldFiles.entrySet()) {
            if (!newFiles.containsKey(file.getKey()) && Files.exists(root.resolve(file.getKey()))) {
                throw new IllegalStateException("Old file is still active: " + file.getKey());
            }
            check(root.resolve(BACKUP).resolve(file.getKey()), file.getValue());
        }
    }

    static void write(Path destination, byte[] bytes, String expected) throws Exception {
        if (!digest(bytes).equals(expected)) throw new IllegalStateException("Unexpected bytes for " + destination);
        Files.createDirectories(destination.getParent());
        Path temporary = destination.resolveSibling(destination.getFileName() + ".trialsbotania-tmp");
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

    static InputStream payload(String name) throws Exception {
        String resource = RESOURCES + name;
        if (!name.equals("files.properties")) {
            String source = properties("files.properties").getProperty("src." + name);
            if (source != null) resource = source;
        }
        InputStream stream = TrialsBotaniaRestart.class.getResourceAsStream(resource);
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
