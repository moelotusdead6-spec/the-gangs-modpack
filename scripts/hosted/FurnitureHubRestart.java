import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

public final class FurnitureHubRestart {
    static final String RESOURCES = "/furniture-hub-1.0.0-payload/";
    static final Path STAGING = Path.of("staging", "furniture-hub-1.0.0");
    static final Path BACKUP = Path.of("backups", "furniture-hub-1.0.0");
    static final String APPLIED = "Furniture mods, staff-only workstation, shop exclusions and hub projectile protection installed.\n";
    static final Map<String, String> PREREQUISITES = Map.of(
        "fabric-server-launch.jar", "6f921315fe2f570c331ca877d1e815c29b93fdc4ad04ce0f0f5cdf4300710028",
        "gangs-botania-no-flight-restart-1.0.1.jar", "b81fa6f3ecfa645901cd7aa5494366d8af1cd1cc6fc513e7a905dcf26c0d2125",
        "mods/gangsbotaniacompat-1.0.1.jar", "8f05304d0976bb90fa9ba51ca58b51ab889083e377fede47bea6c99d3f3f2614",
        "mods/gangschefcompat-1.0.0.jar", "1fab7ab76a13e545ca73156ffa1ab7bded6bd93478c9cbef5b8d98707047c5a9",
        "mods/dcintegration-fabric-3.0.7-1.20.1.jar", "8b7c543042b0316f35f224c6437e78a63c8b298eb549fac85965754875f0b00c");

    public static void main(String[] args) throws Throwable {
        Path root = Path.of(".").toAbsolutePath().normalize();
        requirePrerequisites(root);
        Properties manifest = manifest();
        install(root, manifest, payloads(root, manifest));
        // The predecessor's updates are already live; replaying it would reject the upgraded GoldClaim/shop.
        String main;
        try (JarFile launcher = new JarFile(root.resolve("fabric-server-launch.jar").toFile())) {
            main = launcher.getManifest().getMainAttributes().getValue("Main-Class");
        }
        if (main == null || main.equals(FurnitureHubRestart.class.getName())) {
            throw new IllegalStateException("Invalid original Fabric launcher.");
        }
        System.out.println("[FurnitureHubRestart] Starting original Fabric launcher; prior updates retained.");
        System.setProperty("java.class.path", root.resolve("fabric-server-launch.jar").toString());
        try {
            Class.forName(main).getMethod("main", String[].class).invoke(null, (Object)args);
        } catch (java.lang.reflect.InvocationTargetException failure) {
            throw failure.getCause();
        }
    }

    static void requirePrerequisites(Path root) throws Exception {
        for (var entry : PREREQUISITES.entrySet()) check(root.resolve(entry.getKey()), entry.getValue());
        for (String config : Set.of("config/Discord-Integration.toml", "DiscordIntegration-Data/Messages.toml")) {
            if (!Files.isRegularFile(root.resolve(config))) {
                throw new IllegalStateException("Missing existing Discord configuration: " + config);
            }
        }
    }

    static void install(Path root, Properties manifest, Map<String, byte[]> payloads) throws Exception {
        Map<String, String> originals = entries(manifest, "old.");
        Map<String, String> targets = entries(manifest, "new.");
        if (originals.size() != 2 || targets.size() != 8 || !targets.keySet().equals(payloads.keySet())) {
            throw new IllegalStateException("Incomplete furniture/hub manifest.");
        }
        for (var entry : targets.entrySet()) {
            if (!digest(payloads.get(entry.getKey())).equals(entry.getValue())) {
                throw new IllegalStateException("Corrupt payload: " + entry.getKey());
            }
        }
        try (FileChannel channel = FileChannel.open(root.resolve(Path.of("world", "session.lock")),
                StandardOpenOption.WRITE); var lock = channel.tryLock()) {
            if (lock == null) throw new IllegalStateException("World is still running.");
            requireOnlyManagedMods(root, manifest, originals.keySet(), targets.keySet());
            Path marker = root.resolve(BACKUP).resolve("APPLIED.txt");
            if (Files.exists(marker) && !Files.readString(marker).equals(APPLIED)) {
                throw new IllegalStateException("Unexpected installation marker.");
            }
            for (var entry : originals.entrySet()) {
                Path original = root.resolve(entry.getKey());
                Path saved = root.resolve(BACKUP).resolve(entry.getKey());
                if (Files.exists(original)) check(original, entry.getValue());
                if (Files.exists(saved)) check(saved, entry.getValue());
                if (!Files.exists(original) && !Files.exists(saved)) {
                    throw new IllegalStateException("Original and backup missing: " + entry.getKey());
                }
            }
            for (var entry : targets.entrySet()) {
                Path target = root.resolve(entry.getKey());
                if (Files.exists(target) && !digest(Files.readAllBytes(target)).equals(entry.getValue())) {
                    String accepted = manifest.getProperty("accepted." + entry.getKey());
                    if (accepted == null) throw new IllegalStateException("Unexpected target: " + entry.getKey());
                    check(target, accepted);
                }
            }
            for (var entry : originals.entrySet()) {
                Path original = root.resolve(entry.getKey());
                Path saved = root.resolve(BACKUP).resolve(entry.getKey());
                if (!Files.exists(saved)) {
                    Files.createDirectories(saved.getParent());
                    Files.copy(original, saved);
                    check(saved, entry.getValue());
                }
            }
            for (var entry : targets.entrySet()) {
                Path target = root.resolve(entry.getKey());
                if (!Files.exists(target)) write(target, payloads.get(entry.getKey()), entry.getValue());
            }
            for (String original : originals.keySet()) Files.deleteIfExists(root.resolve(original));
            if (!Files.exists(marker)) Files.writeString(marker, APPLIED, StandardOpenOption.CREATE_NEW);
            System.out.println("[FurnitureHubRestart] Update verified; originals retained in " + BACKUP + ".");
        }
    }

    static void requireOnlyManagedMods(Path root, Properties manifest, Set<String> originals, Set<String> targets)
            throws IOException {
        Set<String> ids = entries(manifest, "mod.").keySet();
        if (ids.size() != 7) throw new IllegalStateException("Incomplete managed mod list.");
        Pattern idPattern = Pattern.compile("\"id\"\\s*:\\s*\"([a-z0-9_-]+)\"");
        try (var files = Files.list(root.resolve("mods"))) {
            for (Path file : files.filter(path -> path.getFileName().toString().toLowerCase(java.util.Locale.ROOT)
                    .endsWith(".jar")).toList()) {
                try (JarFile jar = new JarFile(file.toFile())) {
                    var metadata = jar.getJarEntry("fabric.mod.json");
                    if (metadata == null) continue;
                    String id;
                    try (InputStream stream = jar.getInputStream(metadata)) {
                        var matcher = idPattern.matcher(new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                        if (!matcher.find()) throw new IllegalStateException("Cannot read mod ID: " + file);
                        id = matcher.group(1);
                    }
                    String name = "mods/" + file.getFileName();
                    if (ids.contains(id) && !originals.contains(name) && !targets.contains(name)) {
                        throw new IllegalStateException("Unexpected managed mod jar: " + file);
                    }
                }
            }
        }
    }

    static void write(Path target, byte[] bytes, String expected) throws Exception {
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), "furniture-hub-", ".tmp");
        try {
            Files.write(temporary, bytes);
            check(temporary, expected);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            check(target, expected);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static Map<String, String> entries(Properties manifest, String prefix) {
        Map<String, String> entries = new java.util.TreeMap<>();
        for (String key : manifest.stringPropertyNames()) {
            if (!key.startsWith(prefix)) continue;
            String name = key.substring(prefix.length());
            if (name.contains("..") || name.contains("\\") || name.startsWith("/")) {
                throw new IllegalStateException("Invalid manifest path: " + name);
            }
            entries.put(name, manifest.getProperty(key));
        }
        return entries;
    }

    static Map<String, byte[]> payloads(Path root, Properties manifest) throws IOException {
        Map<String, byte[]> payloads = new LinkedHashMap<>();
        for (String name : entries(manifest, "new.").keySet()) {
            String staged = manifest.getProperty("staged." + name);
            if (staged != null) {
                if (!Path.of(staged).getFileName().toString().equals(staged)) {
                    throw new IllegalStateException("Invalid staged filename: " + staged);
                }
                payloads.put(name, Files.readAllBytes(root.resolve(STAGING).resolve(staged)));
            } else {
                try (InputStream stream = resource(name)) {
                    payloads.put(name, stream.readAllBytes());
                }
            }
        }
        return payloads;
    }

    static Properties manifest() throws IOException {
        Properties properties = new Properties();
        try (InputStream stream = resource("files.properties")) { properties.load(stream); }
        return properties;
    }

    static InputStream resource(String name) throws IOException {
        InputStream stream = FurnitureHubRestart.class.getResourceAsStream(RESOURCES + name);
        if (stream == null) throw new IOException("Missing embedded payload: " + name);
        return stream;
    }

    static void check(Path file, String expected) throws Exception {
        if (!Files.isRegularFile(file) || !digest(Files.readAllBytes(file)).equals(expected)) {
            throw new IllegalStateException("Missing file or unexpected SHA-256: " + file);
        }
    }

    static String digest(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
