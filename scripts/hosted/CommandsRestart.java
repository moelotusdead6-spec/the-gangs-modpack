import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

public final class CommandsRestart {
    static final String PREDECESSOR = "gangs-gems-upgrade-0.1.2.jar";
    static final String PREDECESSOR_HASH = "9de764088849b21cea0529ed2c8d3977ae47080166d396e7baecbf042397d4fd";
    static final Path BACKUP = Path.of("backups", "commands-1.0.0");
    static final String APPLIED = "Command updates installed; previous mods backed up.\n";
    static final String RESOURCE = "/commands-1.0.0-payload/";
    static final Map<String, String> OLD = Map.of(
        "goldclaim-1.0.79.jar", "1a4f6a10515c078452032632ba2ef8bf7f0ee3286b384b7006899c04cbe3e2e9",
        "gangsgems-0.1.2.jar", "97cea3c949a09271ac65ed6f3cf89fd3b3d272c6f440cedc18e45b57d9c5744e",
        "gangsmounts-1.0.2.jar", "ed70a718cd94c4aac052c15ab7cd270b78fb724fb17c86cb8aaff5fcedab49ff");
    static final Set<String> NEW = Set.of("goldclaim-1.0.80.jar", "gangsgems-0.1.3.jar", "gangsmounts-1.0.3.jar");
    private static final Pattern MANAGED = Pattern.compile("\"id\"\\s*:\\s*\"(?:goldclaim|gangsgems|gangsmounts)\"");

    public static void main(String[] args) throws Throwable {
        Path root = Path.of(".").toAbsolutePath().normalize();
        install(root, payloads(), checksums());
        System.setProperty("log4j.configurationFile", root.resolve("log4j2-server.xml").toString());
        String main;
        try (JarFile launcher = new JarFile(root.resolve("fabric-server-launch.jar").toFile())) {
            main = launcher.getManifest().getMainAttributes().getValue("Main-Class");
        }
        if (main == null || !main.startsWith("net.fabricmc.")) throw new IllegalStateException("Invalid Fabric launcher");
        System.setProperty("java.class.path", root.resolve("fabric-server-launch.jar").toString());
        try {
            Class.forName(main).getMethod("main", String[].class).invoke(null, (Object) args);
        } catch (java.lang.reflect.InvocationTargetException failure) {
            throw failure.getCause();
        }
    }

    static void install(Path root, Map<String, byte[]> payload, Properties sums) throws Exception {
        if (!payload.keySet().equals(NEW) || !sums.stringPropertyNames().equals(NEW)) {
            throw new IllegalStateException("Incomplete command update payload");
        }
        for (String name : NEW) {
            String expected = sums.getProperty(name);
            if (!expected.matches("[0-9a-f]{64}") || !GobberShopRestart.hash(payload.get(name)).equals(expected)) {
                throw new IllegalStateException("Corrupt command payload: " + name);
            }
        }
        try (var channel = FileChannel.open(root.resolve(Path.of("world", "session.lock")), StandardOpenOption.WRITE);
             var lock = channel.tryLock()) {
            if (lock == null) throw new IllegalStateException("World is still running");
            verifyPredecessor(root);
            rejectDuplicates(root);
            Path backup = root.resolve(BACKUP);
            Path marker = backup.resolve("APPLIED.txt");
            boolean applied = Files.exists(marker, LinkOption.NOFOLLOW_LINKS);
            if (applied) GobberShopRestart.marker(marker, APPLIED);
            for (var entry : OLD.entrySet()) {
                Path old = root.resolve("mods").resolve(entry.getKey());
                Path saved = backup.resolve(entry.getKey());
                boolean hasOld = Files.exists(old, LinkOption.NOFOLLOW_LINKS);
                boolean hasSaved = Files.exists(saved, LinkOption.NOFOLLOW_LINKS);
                if (hasOld) GobberShopRestart.check(old, entry.getValue());
                if (hasSaved) GobberShopRestart.check(saved, entry.getValue());
                if (applied && (hasOld || !hasSaved)) throw new IllegalStateException("Invalid completed update state: " + old);
                if (!hasOld && !hasSaved) throw new IllegalStateException("Missing original and backup: " + old);
            }
            for (String name : NEW) {
                Path target = root.resolve("mods").resolve(name);
                if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) GobberShopRestart.check(target, sums.getProperty(name));
                else if (applied) throw new IllegalStateException("Installed mod missing: " + target);
            }
            if (applied) return;
            Files.createDirectories(backup);
            for (var entry : OLD.entrySet()) {
                Path old = root.resolve("mods").resolve(entry.getKey());
                Path saved = backup.resolve(entry.getKey());
                if (!Files.exists(saved, LinkOption.NOFOLLOW_LINKS)) {
                    GobberShopRestart.write(saved, Files.readAllBytes(old), entry.getValue(), false);
                }
            }
            for (String name : NEW) {
                Path target = root.resolve("mods").resolve(name);
                if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                    GobberShopRestart.write(target, payload.get(name), sums.getProperty(name), false);
                }
            }
            for (String name : OLD.keySet()) Files.deleteIfExists(root.resolve("mods").resolve(name));
            byte[] markerBytes = APPLIED.getBytes(StandardCharsets.UTF_8);
            GobberShopRestart.write(marker, markerBytes, GobberShopRestart.hash(markerBytes), false);
            System.out.println("[CommandsRestart] Command updates installed and previous mods backed up.");
        }
    }

    static void verifyPredecessor(Path root) throws Exception {
        GobberShopRestart.check(root.resolve(PREDECESSOR), PREDECESSOR_HASH);
        GobberShopRestart.check(root.resolve(GangGemsRestart.PREDECESSOR), GangGemsRestart.PREDECESSOR_HASH);
        GobberShopRestart.check(root.resolve("fabric-server-launch.jar"), GangGemsRestart.FABRIC_HASH);
        GobberShopRestart.check(root.resolve(GangGemsRestart.FURNITURE), GangGemsRestart.FURNITURE_HASH);
        GobberShopRestart.check(root.resolve(GangGemsRestart.DISCORD), GangGemsRestart.DISCORD_HASH);
        GobberShopRestart.marker(root.resolve(GangGemsRestart.MARKER), GangGemsRestart.APPLIED);
        GobberShopRestart.marker(root.resolve(GangGemsUpgrade.BACKUP).resolve("APPLIED.txt"), GangGemsUpgrade.APPLIED);
        GobberShopRestart.check(root.resolve(GangGemsUpgrade.BACKUP).resolve(GangGemsUpgrade.OLD), GangGemsUpgrade.OLD_HASH);
        GobberShopRestart.marker(root.resolve(GobberShopRestart.BACKUP).resolve("APPLIED.txt"), GobberShopRestart.APPLIED);
        Properties previous = GobberShopRestart.checksums();
        GobberShopRestart.check(root.resolve(GobberShopRestart.SHOP), previous.getProperty(GobberShopRestart.SHOP));
        GobberShopRestart.check(root.resolve(GobberShopRestart.TAG), previous.getProperty(GobberShopRestart.TAG));
        String config = Files.readString(root.resolve(GobberShopRestart.CONFIG));
        if (!GobberShopRestart.disableUnbreakable(config).equals(config)) {
            throw new IllegalStateException("Gobber unbreakableEndTools was re-enabled");
        }
    }

    static void rejectDuplicates(Path root) throws Exception {
        try (var files = Files.list(root.resolve("mods"))) {
            for (Path file : files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")).toList()) {
                String name = file.getFileName().toString();
                if (OLD.containsKey(name) || NEW.contains(name)) continue;
                try (JarFile jar = new JarFile(file.toFile())) {
                    var metadata = jar.getJarEntry("fabric.mod.json");
                    if (metadata == null) continue;
                    try (InputStream input = jar.getInputStream(metadata)) {
                        if (MANAGED.matcher(new String(input.readAllBytes(), StandardCharsets.UTF_8)).find()) {
                            throw new IllegalStateException("Unexpected competing mod: " + file);
                        }
                    }
                }
            }
        }
    }

    static Map<String, byte[]> payloads() throws Exception {
        Map<String, byte[]> result = new LinkedHashMap<>();
        for (String name : NEW) result.put(name, resource(name));
        return result;
    }

    static Properties checksums() throws Exception {
        Properties result = new Properties();
        result.load(new java.io.ByteArrayInputStream(resource("checksums.properties")));
        return result;
    }

    static byte[] resource(String name) throws Exception {
        try (InputStream input = CommandsRestart.class.getResourceAsStream(RESOURCE + name)) {
            if (input == null) throw new IllegalStateException("Missing command payload: " + name);
            return input.readAllBytes();
        }
    }
}
