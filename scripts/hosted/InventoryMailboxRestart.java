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

public final class InventoryMailboxRestart {
    static final String NEXT = "gangs-dragon-farseer-restart-1.0.0.jar";
    static final String RESOURCE = "/inventory-mailbox-1.0.0-payload/";
    static final String GOLDCLAIM = "mods/goldclaim-1.0.77.jar";
    static final String HISTORY = "mods/gangsinvhistory-1.0.0.jar";
    static final String VIEWER = "mods/InvView-1.4.12-1.20+.jar";
    static final String OLD = "mods/goldclaim-1.0.76.jar";
    static final String OLD_HASH = "1422fd21a4ae629bbeb1041acbe8c4c347cf7ce7ab518337a00bea043105862a";
    static final Path BACKUP = Path.of("backups", "inventory-mailbox-1.0.0");
    static final String APPLIED = "Inventory history, InvView and stacked paginated mailbox installed.\n";
    static final Set<String> PAYLOADS = Set.of(GOLDCLAIM, HISTORY, VIEWER);
    static final Map<String, String> DEPENDENCIES = Map.of(
        "fabric-server-launch.jar", "6f921315fe2f570c331ca877d1e815c29b93fdc4ad04ce0f0f5cdf4300710028",
        NEXT, "71e53ee96516b2370707d2d3f9e37db9d6f38f1ab2181fb50cfea17e680606a7",
        "gangs-mount-variants-restart-1.0.3.jar", "3ec3710536cafcf695afefc722c4039e2ac7413503fd125b343b7162b50d3bdc",
        "gangs-safe-graves-restart-1.0.0.jar", "111c618cfdb4104a7a214bc736e829c709cf7b67f31cddaabda81c46f763d9eb",
        "gangs-mount-claim-restart-1.0.1.jar", "7b69f096f8de8fadf2933fe82ed7b71779856affaef52b7ca116de9ed36b8e67",
        "mods/gangsmounts-1.0.2.jar", "ed70a718cd94c4aac052c15ab7cd270b78fb724fb17c86cb8aaff5fcedab49ff",
        "mods/gangsboots-1.0.8.jar", "e886f5067038279b40e6f00d00231062f42a462c8b8c5a62dc6e66b3a4e933a1",
        "mods/gangsgravescompat-1.0.0.jar", "1cec6d25be33e515ef524ca0e69996cd67856b111cff62681e542de3d6be4515",
        "mods/gangshop-1.0.12.jar", "0e47892e9abb773763be9e527ec222b2bbc18e7d7f17ba93aba22761ea93a5ce");
    static final Map<String, String> MARKERS = Map.of(
        "backups/mount-variants-1.0.2/STARTED.txt",
        "Mount variant update started after verified predecessor installation.\n",
        "backups/mount-variants-1.0.2/APPLIED.txt",
        "Mount variants, inventory, armor, animation, neon rotation and Boots 1.0.8 installed.\n",
        "backups/mount-claim-1.0.0/APPLIED.txt",
        "Mounts, admin claim permissions and shop exclusions installed.\n");

    public static void main(String[] args) throws Throwable {
        Path root = Path.of(".").toAbsolutePath().normalize();
        preflight(root);
        // Re-running MountVariantsRestart.main would reinstall GoldClaim 1.0.76.
        DragonFarseerRestart.install(root, DragonFarseerRestart.payloads(), DragonFarseerRestart.checksums());
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

    static void preflight(Path root) throws Exception {
        for (var entry : DEPENDENCIES.entrySet()) check(root.resolve(entry.getKey()), entry.getValue());
        for (var entry : MARKERS.entrySet()) {
            if (!Files.readString(root.resolve(entry.getKey())).equals(entry.getValue())) {
                throw new IllegalStateException("Predecessor update is not verified as applied: " + entry.getKey());
            }
        }
    }

    static void install(Path root, Map<String, byte[]> payloads, Properties sums) throws Exception {
        if (!payloads.keySet().equals(PAYLOADS) || !sums.stringPropertyNames().equals(PAYLOADS)) {
            throw new IllegalStateException("Incomplete inventory/mailbox payloads");
        }
        for (String name : PAYLOADS) {
            if (!hash(payloads.get(name)).equals(sums.getProperty(name))) {
                throw new IllegalStateException("Corrupt payload: " + name);
            }
        }
        preflight(root);
        try (var channel = FileChannel.open(root.resolve(Path.of("world", "session.lock")), StandardOpenOption.WRITE);
             var lock = channel.tryLock()) {
            if (lock == null) throw new IllegalStateException("World is still running.");
            Path marker = root.resolve(BACKUP).resolve("APPLIED.txt");
            if (Files.exists(marker) && !Files.readString(marker).equals(APPLIED)) {
                throw new IllegalStateException("Unexpected inventory/mailbox installation marker");
            }
            rejectDuplicates(root);
            Path old = root.resolve(OLD);
            Path saved = root.resolve(BACKUP).resolve(OLD);
            if (Files.exists(old)) check(old, OLD_HASH);
            if (Files.exists(saved)) check(saved, OLD_HASH);
            if (!Files.exists(old) && !Files.exists(saved)) {
                throw new IllegalStateException("Original GoldClaim and its backup are missing.");
            }
            for (String name : PAYLOADS) {
                if (Files.exists(root.resolve(name))) check(root.resolve(name), sums.getProperty(name));
            }
            if (!Files.exists(saved)) {
                Files.createDirectories(saved.getParent());
                Files.copy(old, saved);
                check(saved, OLD_HASH);
            }
            for (String name : PAYLOADS) {
                Path target = root.resolve(name);
                if (!Files.exists(target)) write(target, payloads.get(name), sums.getProperty(name));
            }
            Files.deleteIfExists(old);
            if (!Files.exists(marker)) Files.writeString(marker, APPLIED, StandardOpenOption.CREATE_NEW);
            System.out.println("[InventoryMailboxRestart] Inventory history, InvView and mailbox verified; previous updates retained.");
        }
    }

    static void rejectDuplicates(Path root) throws Exception {
        Pattern ids = Pattern.compile("\"id\"\\s*:\\s*\"(goldclaim|gangsinvhistory|invview)\"");
        try (var paths = Files.list(root.resolve("mods"))) {
            for (Path path : paths.filter(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".jar")).toList()) {
                if (path.equals(root.resolve(OLD)) || PAYLOADS.stream().anyMatch(name -> path.equals(root.resolve(name)))) continue;
                try (JarFile jar = new JarFile(path.toFile())) {
                    var metadata = jar.getJarEntry("fabric.mod.json");
                    if (metadata == null) continue;
                    try (InputStream stream = jar.getInputStream(metadata)) {
                        if (ids.matcher(new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).find()) {
                            throw new IllegalStateException("Unexpected managed mod jar: " + path);
                        }
                    }
                }
            }
        }
    }

    static void write(Path target, byte[] bytes, String expected) throws Exception {
        Path temporary = Files.createTempFile(target.getParent(), "inventory-mailbox-", ".tmp");
        try {
            Files.write(temporary, bytes);
            check(temporary, expected);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            check(target, expected);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static Map<String, byte[]> payloads() throws Exception {
        var result = new LinkedHashMap<String, byte[]>();
        for (String name : PAYLOADS) result.put(name, resource(name));
        return result;
    }

    static Properties checksums() throws Exception {
        var sums = new Properties();
        sums.load(new java.io.ByteArrayInputStream(resource("checksums.properties")));
        return sums;
    }

    static byte[] resource(String name) throws Exception {
        try (InputStream stream = InventoryMailboxRestart.class.getResourceAsStream(RESOURCE + name)) {
            if (stream == null) throw new IllegalStateException("Missing restart payload: " + name);
            return stream.readAllBytes();
        }
    }

    static String hash(byte[] bytes) throws Exception {
        if (bytes == null) throw new IllegalStateException("Missing payload bytes");
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    static void check(Path path, String expected) throws Exception {
        if (!Files.isRegularFile(path) || !hash(Files.readAllBytes(path)).equals(expected)) {
            throw new IllegalStateException("Missing file or unexpected SHA-256: " + path);
        }
    }
}
