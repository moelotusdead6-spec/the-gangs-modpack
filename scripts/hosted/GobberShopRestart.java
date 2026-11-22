import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
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

public final class GobberShopRestart {
    static final String RESOURCE = "/gobber-shop-1.0.79-payload/";
    static final Path BACKUP = Path.of("backups", "gobber-shop-1.0.79");
    static final String APPLIED = "Gobber, neon and shop update 1.0.79 installed.\n";
    static final String BACKED_UP = "Previous mods, Gobber and shop settings backed up.\n";
    static final String GOLD_OLD = "mods/goldclaim-1.0.78.jar";
    static final String SHOP_OLD = "mods/gangshop-1.0.13.jar";
    static final String GOLD = "mods/goldclaim-1.0.79.jar";
    static final String SHOP = "mods/gangshop-1.0.14.jar";
    static final String TAG = "config/paxi/datapacks/gangs_fixes/data/c/tags/items/hidden_from_recipe_viewers.json";
    static final String CONFIG = "config/gobber2/general.json5";
    static final String WALLET = "world/gangshop_wallets.json";
    static final Map<String, String> OLD = Map.of(
        GOLD_OLD, "a7b98f263084ce4af89a682e612dc6ee53d4c02a0763a7a93c16c6dac1c4d103",
        SHOP_OLD, "cd726e0bf00e0126254421801e4f7e24513a1d62b3278cc98d3d6635c4f317d2");
    static final Set<String> EMBEDDED = Set.of(GOLD, SHOP, TAG);
    static final Pattern SETTING = Pattern.compile(
        "(?m)^([ \\t]*\"unbreakableEndTools\"[ \\t]*:[ \\t]*)(true|false)([ \\t]*,?[ \\t]*(?://[^\\r\\n]*)?)$");

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
        try (var channel = FileChannel.open(root.resolve(Path.of("world", "session.lock")), StandardOpenOption.WRITE);
             var lock = channel.tryLock()) {
            if (lock == null) throw new IllegalStateException("World is still running");
            preflight(root, payload, sums);
            Path marker = root.resolve(BACKUP).resolve("APPLIED.txt");
            if (Files.exists(marker)) {
                for (String name : EMBEDDED) check(root.resolve(name), sums.getProperty(name));
                for (String name : OLD.keySet()) {
                    if (Files.exists(root.resolve(name))) throw new IllegalStateException("Old mod remains: " + name);
                }
                if (!disableUnbreakable(Files.readString(root.resolve(CONFIG))).equals(Files.readString(root.resolve(CONFIG)))) {
                    throw new IllegalStateException("Gobber unbreakableEndTools was re-enabled");
                }
                return;
            }
            Path backupMarker = root.resolve(BACKUP).resolve("BACKED_UP.txt");
            if (!Files.exists(backupMarker)) {
                for (String name : OLD.keySet()) backup(root, Path.of(name));
                backup(root, Path.of(CONFIG));
                backup(root, Path.of(WALLET));
                Path shopConfig = root.resolve(Path.of("config", "gangshop"));
                try (var paths = Files.walk(shopConfig)) {
                    for (Path path : paths.toList()) {
                        if (Files.isSymbolicLink(path)) throw new IllegalStateException("Symbolic link in shop config: " + path);
                        if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) backup(root, root.relativize(path));
                    }
                }
                Files.writeString(backupMarker, BACKED_UP, StandardOpenOption.CREATE_NEW);
            }
            for (String name : EMBEDDED) {
                Path target = root.resolve(name);
                if (!Files.exists(target)) write(target, payload.get(name), sums.getProperty(name), false);
            }
            String config = Files.readString(root.resolve(CONFIG));
            byte[] updated = disableUnbreakable(config).getBytes(StandardCharsets.UTF_8);
            if (!config.equals(new String(updated, StandardCharsets.UTF_8))) {
                write(root.resolve(CONFIG), updated, hash(updated), true);
            }
            for (String name : OLD.keySet()) Files.deleteIfExists(root.resolve(name));
            Files.writeString(marker, APPLIED, StandardOpenOption.CREATE_NEW);
            System.out.println("[GobberShopRestart] Update installed and backups verified.");
        }
    }

    static void preflight(Path root, Map<String, byte[]> payload, Properties sums) throws Exception {
        if (!payload.keySet().equals(EMBEDDED) || !sums.stringPropertyNames().equals(EMBEDDED)) {
            throw new IllegalStateException("Incomplete embedded payload");
        }
        for (String name : EMBEDDED) {
            if (!hash(payload.get(name)).equals(sums.getProperty(name))) throw new IllegalStateException("Corrupt payload: " + name);
            if (Files.exists(root.resolve(name))) check(root.resolve(name), sums.getProperty(name));
        }
        check(root.resolve("gangs-shop-gui-restart-1.0.13.jar"),
            "cf3fdd1e23b519c9c081bad4e4a0ebe0918448d28de7750a3b89c9812bda0e47");
        check(root.resolve(Path.of("mods", "gang-shop-ozsoihjx.jar")),
            "4a172b39340c3b6a7db61f91bda866f4d4ba0424d77e7305cf1d37f08c96c897");
        marker(root.resolve(Path.of("backups", "wild-boss-goldclaim-1.0.78", "APPLIED.txt")),
            "GoldClaim 1.0.78 kit countdowns, Cosmetic Keys and /wild boss rules installed.\n");
        marker(root.resolve(Path.of("backups", "shop-gui-1.0.13", "APPLIED.txt")),
            "Gang Shop GUI 1.0.13 installed.\n");
        marker(root.resolve(Path.of("backups", "shop-gui-1.0.13", "BACKED_UP.txt")),
            "Shop configuration, wallet and previous mod backed up.\n");
        for (var entry : OLD.entrySet()) {
            Path old = root.resolve(entry.getKey());
            Path backup = root.resolve(BACKUP).resolve(entry.getKey());
            if (Files.exists(old)) check(old, entry.getValue());
            if (Files.exists(backup)) check(backup, entry.getValue());
            if (!Files.exists(old) && !Files.exists(backup)) throw new IllegalStateException("Old mod and backup missing: " + old);
        }
        Path marker = root.resolve(BACKUP).resolve("APPLIED.txt");
        Path backupMarker = root.resolve(BACKUP).resolve("BACKED_UP.txt");
        if (Files.exists(marker)) marker(marker, APPLIED);
        if (Files.exists(backupMarker)) marker(backupMarker, BACKED_UP);
        if (Files.exists(marker) && !Files.exists(backupMarker)) throw new IllegalStateException("Backup marker missing");
        if (Files.exists(backupMarker)) {
            for (String name : OLD.keySet()) check(root.resolve(BACKUP).resolve(name), OLD.get(name));
            for (String name : Set.of(CONFIG, WALLET)) {
                if (!Files.isRegularFile(root.resolve(BACKUP).resolve(name), LinkOption.NOFOLLOW_LINKS)) {
                    throw new IllegalStateException("Backup missing: " + name);
                }
            }
        }
        if (!Files.isRegularFile(root.resolve(CONFIG), LinkOption.NOFOLLOW_LINKS)
                || !Files.isRegularFile(root.resolve(WALLET), LinkOption.NOFOLLOW_LINKS)
                || !Files.isDirectory(root.resolve(Path.of("config", "gangshop")), LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("Required live config or wallet missing");
        }
        disableUnbreakable(Files.readString(root.resolve(CONFIG)));
        rejectDuplicates(root);
    }

    static String disableUnbreakable(String content) {
        var matches = SETTING.matcher(content);
        if (!matches.find()) throw new IllegalStateException("Gobber unbreakableEndTools setting missing or malformed");
        String result = content.substring(0, matches.start(2)) + "false" + content.substring(matches.end(2));
        if (matches.find()) throw new IllegalStateException("Multiple Gobber unbreakableEndTools settings");
        return result;
    }

    static void rejectDuplicates(Path root) throws Exception {
        Pattern ids = Pattern.compile("\"id\"\\s*:\\s*\"(?:goldclaim|gangshop)\"");
        Set<String> allowed = Set.of(GOLD_OLD, SHOP_OLD, GOLD, SHOP);
        try (var paths = Files.list(root.resolve("mods"))) {
            for (Path path : paths.filter(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".jar")).toList()) {
                String name = "mods/" + path.getFileName();
                if (allowed.contains(name)) continue;
                try (JarFile jar = new JarFile(path.toFile())) {
                    var entry = jar.getJarEntry("fabric.mod.json");
                    if (entry == null) continue;
                    try (InputStream input = jar.getInputStream(entry)) {
                        if (ids.matcher(new String(input.readAllBytes(), StandardCharsets.UTF_8)).find()) {
                            throw new IllegalStateException("Unexpected competing mod: " + path);
                        }
                    }
                }
            }
        }
    }

    static void backup(Path root, Path name) throws Exception {
        Path source = root.resolve(name);
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) throw new IllegalStateException("Backup source missing: " + source);
        Path target = root.resolve(BACKUP).resolve(name);
        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        check(target, fileHash(source));
    }

    static void write(Path target, byte[] bytes, String expected, boolean replace) throws Exception {
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), "gobber-shop-", ".tmp");
        try {
            Files.write(temporary, bytes);
            check(temporary, expected);
            if (replace) Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            else Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            check(target, expected);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static void marker(Path path, String expected) throws Exception {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || !Files.readString(path).equals(expected)) {
            throw new IllegalStateException("Missing or unexpected predecessor marker: " + path);
        }
    }

    static Map<String, byte[]> payloads() throws Exception {
        Map<String, byte[]> result = new LinkedHashMap<>();
        for (String name : EMBEDDED) result.put(name, resource(name));
        return result;
    }

    static Properties checksums() throws Exception {
        Properties result = new Properties();
        result.load(new java.io.ByteArrayInputStream(resource("checksums.properties")));
        return result;
    }

    static byte[] resource(String name) throws Exception {
        try (InputStream input = GobberShopRestart.class.getResourceAsStream(RESOURCE + name)) {
            if (input == null) throw new IllegalStateException("Missing payload: " + name);
            return input.readAllBytes();
        }
    }

    static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    static String fileHash(Path file) throws Exception {
        return hash(Files.readAllBytes(file));
    }

    static void check(Path file, String expected) throws Exception {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || !fileHash(file).equals(expected)) {
            throw new IllegalStateException("Missing or unexpected SHA-256: " + file);
        }
    }
}
