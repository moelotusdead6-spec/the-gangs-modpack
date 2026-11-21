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

public final class TrialsLightnessRestart {
    static final String RESOURCE = "/trials-lightness-1.1.32-payload/";
    static final Path BACKUP = Path.of("backups", "trials-lightness-1.1.32");
    static final Path STAGING = Path.of(".gangs-pending-1.1.32");
    static final String APPLIED = "Trial spawners, Boots of Lightness and mod updates installed.\n";
    static final String PREDECESSOR = "gangs-inventory-mailbox-restart-1.0.0.jar";
    static final String PREDECESSOR_HASH = "d443adb443b9433f3922c275b6fd76610c2e1c3b24f93840ee60be9a5289ab8b";
    static final Map<String, String> OLD = Map.of(
        "mods/bclib-3.0.17.jar", "b9741c75b79be0edb3644a23bed5eaca3bd5ab802124db26703d4478b43b4210",
        "mods/better-end-4.0.13.jar", "15ff0912458b5479afaf07595e3cf953a8d7427d95119fa8a8210c5dba4e7570",
        "mods/gangsboots-1.0.8.jar", "e886f5067038279b40e6f00d00231062f42a462c8b8c5a62dc6e66b3a4e933a1",
        "mods/gangstrials-1.0.7.jar", "10e7d3218416ea10fe60814a72b3fe84a1dc0faebebb230b464586f4e2d4beac");
    static final Map<String, String> STAGED = Map.of(
        "mods/bclib-3.0.18.jar", "d00200c9e8c04099fbbbf12b96af3f50daa4705fa01d42d8f6b215936f11df09",
        "mods/better-end-4.0.14.jar", "f49680e613e2346a49bf6e72828b4d05fee787e9cc24081df74c80dcda34035a",
        "mods/memoryleakfix-fabric-1.17+-1.1.2.jar", "a9fadc6d369e6bbfb10eab4c00780c3295292c634b6551d2e1e2897ec38b63ab",
        "mods/inventoryessentials-fabric-1.20.1-8.2.19.jar", "93148e3752012ac703f9ef33c22feed075dfe38be9b039b56aa4c34223371f9e");
    static final String POOLS = "config/paxi/datapacks/gangs_fixes/data/minecraft/worldgen/template_pool/trial_chambers/spawner/";
    static final Set<String> EMBEDDED = Set.of(
        "mods/gangsboots-1.0.9.jar", "mods/gangstrials-1.0.9.jar",
        POOLS + "all.json", POOLS + "breeze.json", POOLS + "melee.json",
        POOLS + "ranged.json", POOLS + "slow_ranged.json", POOLS + "small_melee.json");

    public static void main(String[] args) throws Throwable {
        Path root = Path.of(".").toAbsolutePath().normalize();
        var payloads = payloads();
        var sums = checksums();
        prepare(root, payloads, sums);
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

    static void prepare(Path root, Map<String, byte[]> payloads, Properties sums) throws Exception {
        preflight(root, payloads, sums);
        if (Files.exists(root.resolve("mods/gangsboots-1.0.8.jar"))) {
            InventoryMailboxRestart.preflight(root);
            DragonFarseerRestart.install(root, DragonFarseerRestart.payloads(), DragonFarseerRestart.checksums());
            InventoryMailboxRestart.install(root, InventoryMailboxRestart.payloads(), InventoryMailboxRestart.checksums());
        }
        verifyPredecessor(root);
        install(root, payloads, sums);
    }

    static void verifyPredecessor(Path root) throws Exception {
        check(root.resolve(PREDECESSOR), PREDECESSOR_HASH);
        for (var entry : InventoryMailboxRestart.DEPENDENCIES.entrySet()) {
            if (!entry.getKey().equals("mods/gangsboots-1.0.8.jar")) check(root.resolve(entry.getKey()), entry.getValue());
        }
        for (var entry : InventoryMailboxRestart.MARKERS.entrySet()) {
            if (!Files.readString(root.resolve(entry.getKey())).equals(entry.getValue())) {
                throw new IllegalStateException("Predecessor marker mismatch: " + entry.getKey());
            }
        }
        Path marker = root.resolve(InventoryMailboxRestart.BACKUP).resolve("APPLIED.txt");
        if (!Files.readString(marker).equals(InventoryMailboxRestart.APPLIED)) throw new IllegalStateException("Mailbox update not applied");
        check(root.resolve(InventoryMailboxRestart.BACKUP).resolve(InventoryMailboxRestart.OLD), InventoryMailboxRestart.OLD_HASH);
        if (Files.exists(root.resolve(InventoryMailboxRestart.OLD))) throw new IllegalStateException("Old GoldClaim remains installed");
        var sums = InventoryMailboxRestart.checksums();
        for (String name : InventoryMailboxRestart.PAYLOADS) check(root.resolve(name), sums.getProperty(name));
        var dragonSums = DragonFarseerRestart.checksums();
        for (String name : new String[]{DragonFarseerRestart.MOD, DragonFarseerRestart.FARSEER, DragonFarseerRestart.LOG}) {
            check(root.resolve(name), dragonSums.getProperty(name));
        }
    }

    static void preflight(Path root, Map<String, byte[]> payloads, Properties sums) throws Exception {
        check(root.resolve(PREDECESSOR), PREDECESSOR_HASH);
        if (!payloads.keySet().equals(EMBEDDED) || !sums.stringPropertyNames().equals(EMBEDDED)) {
            throw new IllegalStateException("Incomplete embedded payload");
        }
        for (String name : EMBEDDED) {
            if (!hash(payloads.get(name)).equals(sums.getProperty(name))) throw new IllegalStateException("Corrupt payload: " + name);
            if (Files.exists(root.resolve(name))) check(root.resolve(name), sums.getProperty(name));
        }
        for (var entry : STAGED.entrySet()) {
            Path target = root.resolve(entry.getKey());
            if (Files.exists(target)) check(target, entry.getValue());
            else check(root.resolve(STAGING).resolve(target.getFileName()), entry.getValue());
        }
        for (var entry : OLD.entrySet()) {
            Path original = root.resolve(entry.getKey());
            Path backup = root.resolve(BACKUP).resolve(entry.getKey());
            if (Files.exists(original)) check(original, entry.getValue());
            if (Files.exists(backup)) check(backup, entry.getValue());
            if (!Files.exists(original) && !Files.exists(backup)) throw new IllegalStateException("Original and backup missing: " + entry.getKey());
        }
        Path marker = root.resolve(BACKUP).resolve("APPLIED.txt");
        if (Files.exists(marker) && !Files.readString(marker).equals(APPLIED)) throw new IllegalStateException("Unexpected update marker");
        rejectDuplicates(root);
    }

    static void install(Path root, Map<String, byte[]> payloads, Properties sums) throws Exception {
        try (var channel = FileChannel.open(root.resolve(Path.of("world", "session.lock")), StandardOpenOption.WRITE);
             var lock = channel.tryLock()) {
            if (lock == null) throw new IllegalStateException("World is still running");
            verifyPredecessor(root);
            preflight(root, payloads, sums);
            for (var entry : OLD.entrySet()) {
                Path saved = root.resolve(BACKUP).resolve(entry.getKey());
                if (!Files.exists(saved)) {
                    Files.createDirectories(saved.getParent());
                    Files.copy(root.resolve(entry.getKey()), saved);
                    check(saved, entry.getValue());
                }
            }
            for (String name : EMBEDDED) {
                Path target = root.resolve(name);
                if (!Files.exists(target)) {
                    Files.createDirectories(target.getParent());
                    write(target, payloads.get(name), sums.getProperty(name));
                }
            }
            for (var entry : STAGED.entrySet()) {
                Path target = root.resolve(entry.getKey());
                if (!Files.exists(target)) copy(root.resolve(STAGING).resolve(target.getFileName()), target, entry.getValue());
            }
            for (String name : OLD.keySet()) Files.deleteIfExists(root.resolve(name));
            Path marker = root.resolve(BACKUP).resolve("APPLIED.txt");
            if (!Files.exists(marker)) Files.writeString(marker, APPLIED, StandardOpenOption.CREATE_NEW);
            System.out.println("[TrialsLightnessRestart] Trial fixes, boots and pinned mod updates verified.");
        }
    }

    static void rejectDuplicates(Path root) throws Exception {
        Pattern ids = Pattern.compile("\"id\"\\s*:\\s*\"(bclib|betterend|gangsboots|gangstrials|memoryleakfix|inventoryessentials)\"");
        try (var paths = Files.list(root.resolve("mods"))) {
            for (Path path : paths.filter(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".jar")).toList()) {
                String name = "mods/" + path.getFileName();
                if (OLD.containsKey(name) || STAGED.containsKey(name) || EMBEDDED.contains(name)) continue;
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

    static void copy(Path source, Path target, String expected) throws Exception {
        Path temporary = Files.createTempFile(target.getParent(), "trials-lightness-", ".tmp");
        try {
            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
            check(temporary, expected);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            check(target, expected);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static void write(Path target, byte[] bytes, String expected) throws Exception {
        Path temporary = Files.createTempFile(target.getParent(), "trials-lightness-", ".tmp");
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
        for (String name : EMBEDDED) result.put(name, resource(name));
        return result;
    }

    static Properties checksums() throws Exception {
        var sums = new Properties();
        sums.load(new java.io.ByteArrayInputStream(resource("checksums.properties")));
        return sums;
    }

    static byte[] resource(String name) throws Exception {
        try (InputStream stream = TrialsLightnessRestart.class.getResourceAsStream(RESOURCE + name)) {
            if (stream == null) throw new IllegalStateException("Missing restart payload: " + name);
            return stream.readAllBytes();
        }
    }

    static String hash(byte[] bytes) throws Exception {
        if (bytes == null) throw new IllegalStateException("Missing payload bytes");
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    static void check(Path path, String expected) throws Exception {
        if (!Files.isRegularFile(path)) throw new IllegalStateException("Missing file: " + path);
        var digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(path)) {
            byte[] buffer = new byte[65536];
            for (int n; (n = input.read(buffer)) != -1;) digest.update(buffer, 0, n);
        }
        if (!HexFormat.of().formatHex(digest.digest()).equals(expected)) throw new IllegalStateException("Unexpected SHA-256: " + path);
    }
}
