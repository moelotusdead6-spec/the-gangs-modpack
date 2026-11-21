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

/**
 * Installs GoldClaim 1.0.78 (kit countdowns, staff Cosmetic Keys, /wild Alex's Caves boss refusal) and the
 * SkiesKits menu countdown lore. Runs after the already-applied Trials/Lightness launcher state is verified.
 */
public final class WildBossRestart {
    static final String RESOURCE = "/wild-boss-1.0.78-payload/";
    static final Path BACKUP = Path.of("backups", "wild-boss-goldclaim-1.0.78");
    static final String APPLIED = "GoldClaim 1.0.78 kit countdowns, Cosmetic Keys and /wild boss rules installed.\n";
    static final String PREDECESSOR = "gangs-trials-lightness-restart-1.1.32.jar";
    static final String PREDECESSOR_HASH = "9454847701fbbe2146245e51ed6838d2e865552a5725d5a1b413d8ad68e46735";
    static final String GOLDCLAIM = "mods/goldclaim-1.0.78.jar";
    static final String OLD = "mods/goldclaim-1.0.77.jar";
    static final String OLD_HASH = "40cfb2cd2cfc17be8adc7205f2d51b2480e10df0957b0992132ba0d7bfca08ab";
    static final String MENU = "config/skieskits/menu.json";
    /** The pack's previous menu.json (LF and CRLF); a menu edited on the host is left untouched. */
    static final Set<String> MENU_OLD = Set.of(
        "237226b9d61dba1ccbcc312b6d7f5df44d021da0e2638370c6fc59de741ee794",
        "4f4f427c325702d5f55f93dd96ddee4831fd23814d088dd1f24e1bf540637246");
    static final Set<String> EMBEDDED = Set.of(GOLDCLAIM, MENU);

    public static void main(String[] args) throws Throwable {
        Path root = Path.of(".").toAbsolutePath().normalize();
        prepare(root, payloads(), checksums());
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
        if (!applied(root)) {
            // Only reached before this update: the predecessor still expects GoldClaim 1.0.77 to be installed.
            if (!Files.exists(root.resolve(TrialsLightnessRestart.BACKUP).resolve("APPLIED.txt"))) {
                TrialsLightnessRestart.prepare(root, TrialsLightnessRestart.payloads(), TrialsLightnessRestart.checksums());
            }
            verifyPredecessor(root);
        }
        install(root, payloads, sums);
    }

    static boolean applied(Path root) throws Exception {
        Path marker = root.resolve(BACKUP).resolve("APPLIED.txt");
        if (!Files.exists(marker)) return false;
        if (!Files.readString(marker).equals(APPLIED)) throw new IllegalStateException("Unexpected update marker");
        return true;
    }

    static void verifyPredecessor(Path root) throws Exception {
        check(root.resolve(PREDECESSOR), PREDECESSOR_HASH);
        TrialsLightnessRestart.verifyPredecessor(root);
        Path marker = root.resolve(TrialsLightnessRestart.BACKUP).resolve("APPLIED.txt");
        if (!Files.readString(marker).equals(TrialsLightnessRestart.APPLIED)) throw new IllegalStateException("Trials update not applied");
        var sums = TrialsLightnessRestart.checksums();
        for (String name : TrialsLightnessRestart.EMBEDDED) check(root.resolve(name), sums.getProperty(name));
        for (var entry : TrialsLightnessRestart.STAGED.entrySet()) check(root.resolve(entry.getKey()), entry.getValue());
        for (String name : TrialsLightnessRestart.OLD.keySet()) {
            if (Files.exists(root.resolve(name))) throw new IllegalStateException("Trials replaced mod remains: " + name);
        }
    }

    static void preflight(Path root, Map<String, byte[]> payloads, Properties sums) throws Exception {
        if (!payloads.keySet().equals(EMBEDDED) || !sums.stringPropertyNames().equals(EMBEDDED)) {
            throw new IllegalStateException("Incomplete embedded payload");
        }
        for (String name : EMBEDDED) {
            if (!hash(payloads.get(name)).equals(sums.getProperty(name))) throw new IllegalStateException("Corrupt payload: " + name);
        }
        Path installed = root.resolve(GOLDCLAIM);
        if (Files.exists(installed)) check(installed, sums.getProperty(GOLDCLAIM));
        Path original = root.resolve(OLD);
        Path backup = root.resolve(BACKUP).resolve(OLD);
        if (Files.exists(original)) check(original, OLD_HASH);
        if (Files.exists(backup)) check(backup, OLD_HASH);
        if (!Files.exists(original) && !Files.exists(backup)) throw new IllegalStateException("Original and backup missing: " + OLD);
        Path savedMenu = root.resolve(BACKUP).resolve(MENU);
        if (Files.exists(savedMenu) && !MENU_OLD.contains(fileHash(savedMenu))) throw new IllegalStateException("Unexpected menu backup");
        rejectDuplicates(root);
    }

    static void install(Path root, Map<String, byte[]> payloads, Properties sums) throws Exception {
        try (var channel = FileChannel.open(root.resolve(Path.of("world", "session.lock")), StandardOpenOption.WRITE);
             var lock = channel.tryLock()) {
            if (lock == null) throw new IllegalStateException("World is still running");
            preflight(root, payloads, sums);
            Path saved = root.resolve(BACKUP).resolve(OLD);
            if (!Files.exists(saved)) {
                Files.createDirectories(saved.getParent());
                Files.copy(root.resolve(OLD), saved);
                check(saved, OLD_HASH);
            }
            Path target = root.resolve(GOLDCLAIM);
            if (!Files.exists(target)) write(target, payloads.get(GOLDCLAIM), sums.getProperty(GOLDCLAIM));
            Files.deleteIfExists(root.resolve(OLD));
            installMenu(root, payloads.get(MENU), sums.getProperty(MENU));
            Path marker = root.resolve(BACKUP).resolve("APPLIED.txt");
            if (!Files.exists(marker)) Files.writeString(marker, APPLIED, StandardOpenOption.CREATE_NEW);
            System.out.println("[WildBossRestart] GoldClaim 1.0.78 verified.");
        }
    }

    static void installMenu(Path root, byte[] bytes, String expected) throws Exception {
        Path menu = root.resolve(MENU);
        String current = Files.exists(menu) ? fileHash(menu) : null;
        if (expected.equals(current)) return;
        if (current == null || !MENU_OLD.contains(current)) {
            System.out.println("[WildBossRestart] WARNING: " + MENU + " was customised or is missing; left unchanged. "
                + "Use %goldclaim:kit_ready w1|w2|w3|m% in each kit's no_permission lore to show countdowns.");
            return;
        }
        Path saved = root.resolve(BACKUP).resolve(MENU);
        if (!Files.exists(saved)) {
            Files.createDirectories(saved.getParent());
            Files.copy(menu, saved);
            check(saved, current);
        }
        Path temporary = Files.createTempFile(menu.getParent(), "wild-boss-", ".tmp");
        try {
            Files.write(temporary, bytes);
            check(temporary, expected);
            Files.move(temporary, menu, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            check(menu, expected);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static void rejectDuplicates(Path root) throws Exception {
        Pattern ids = Pattern.compile("\"id\"\\s*:\\s*\"goldclaim\"");
        try (var paths = Files.list(root.resolve("mods"))) {
            for (Path path : paths.filter(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".jar")).toList()) {
                String name = "mods/" + path.getFileName();
                if (name.equals(OLD) || name.equals(GOLDCLAIM)) continue;
                try (JarFile jar = new JarFile(path.toFile())) {
                    var metadata = jar.getJarEntry("fabric.mod.json");
                    if (metadata == null) continue;
                    try (InputStream stream = jar.getInputStream(metadata)) {
                        if (ids.matcher(new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).find()) {
                            throw new IllegalStateException("Unexpected GoldClaim jar: " + path);
                        }
                    }
                }
            }
        }
    }

    static void write(Path target, byte[] bytes, String expected) throws Exception {
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), "wild-boss-", ".tmp");
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
        try (InputStream stream = WildBossRestart.class.getResourceAsStream(RESOURCE + name)) {
            if (stream == null) throw new IllegalStateException("Missing restart payload: " + name);
            return stream.readAllBytes();
        }
    }

    static String hash(byte[] bytes) throws Exception {
        if (bytes == null) throw new IllegalStateException("Missing payload bytes");
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    static String fileHash(Path path) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(path)) {
            byte[] buffer = new byte[65536];
            for (int n; (n = input.read(buffer)) != -1;) digest.update(buffer, 0, n);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    static void check(Path path, String expected) throws Exception {
        if (!Files.isRegularFile(path)) throw new IllegalStateException("Missing file: " + path);
        if (!fileHash(path).equals(expected)) throw new IllegalStateException("Unexpected SHA-256: " + path);
    }
}
