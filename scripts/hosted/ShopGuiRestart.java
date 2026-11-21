import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

public final class ShopGuiRestart {
    static final String RESOURCE = "/shop-gui-1.0.13-payload/";
    static final String PREDECESSOR = "gangs-wild-boss-restart-1.0.0.jar";
    static final String PREDECESSOR_HASH = "dce8ce5f6a691a3ccea89ed9f1c777de75adfc1aaff0824245b2b7e22af06a93";
    static final String GUI = "gang-shop-ozsoihjx.jar";
    static final String GUI_HASH = "4a172b39340c3b6a7db61f91bda866f4d4ba0424d77e7305cf1d37f08c96c897";
    static final String OLD = "mods/gangshop-1.0.12.jar";
    static final String OLD_HASH = "0e47892e9abb773763be9e527ec222b2bbc18e7d7f17ba93aba22761ea93a5ce";
    static final String MOD = "mods/gangshop-1.0.13.jar";
    static final String GUI_TARGET = "mods/" + GUI;
    static final String ACCESS = "config/gangshop/access.json";
    static final Path BACKUP = Path.of("backups", "shop-gui-1.0.13");
    static final String BACKED_UP = "Shop configuration, wallet and previous mod backed up.\n";
    static final String APPLIED = "Gang Shop GUI 1.0.13 installed.\n";
    static final Set<String> EMBEDDED = Set.of(MOD, ACCESS);

    public static void main(String[] args) throws Throwable {
        Path root = Path.of(".").toAbsolutePath().normalize();
        Map<String, byte[]> payload = payloads();
        Properties sums = checksums();
        preflight(root, payload, sums);
        WildBossRestart.check(root.resolve(PREDECESSOR), PREDECESSOR_HASH);
        WildBossRestart.prepare(root, WildBossRestart.payloads(), WildBossRestart.checksums());
        install(root, payload, sums);
        WildBossRestart.main(args);
    }

    static void preflight(Path root, Map<String, byte[]> payload, Properties sums) throws Exception {
        if (!payload.keySet().equals(EMBEDDED) || !sums.stringPropertyNames().equals(EMBEDDED)) {
            throw new IllegalStateException("Incomplete shop payload");
        }
        for (String name : EMBEDDED) {
            if (!WildBossRestart.hash(payload.get(name)).equals(sums.getProperty(name))) {
                throw new IllegalStateException("Corrupt shop payload: " + name);
            }
        }
        WildBossRestart.check(root.resolve(GUI), GUI_HASH);
        for (String name : new String[]{MOD, GUI_TARGET}) {
            Path target = root.resolve(name);
            if (Files.exists(target)) WildBossRestart.check(target, name.equals(MOD) ? sums.getProperty(MOD) : GUI_HASH);
        }
        Path old = root.resolve(OLD);
        Path saved = root.resolve(BACKUP).resolve(OLD);
        if (Files.exists(old)) WildBossRestart.check(old, OLD_HASH);
        if (Files.exists(saved)) WildBossRestart.check(saved, OLD_HASH);
        if (!Files.exists(old) && !Files.exists(saved)) throw new IllegalStateException("Previous shop mod and backup missing");
        Path marker = root.resolve(BACKUP).resolve("APPLIED.txt");
        if (Files.exists(marker) && !Files.readString(marker).equals(APPLIED)) throw new IllegalStateException("Unexpected shop marker");
        Path backupMarker = root.resolve(BACKUP).resolve("BACKED_UP.txt");
        if (Files.exists(backupMarker) && !Files.readString(backupMarker).equals(BACKED_UP)) throw new IllegalStateException("Unexpected backup marker");
        if (Files.exists(marker) && !Files.exists(backupMarker)) throw new IllegalStateException("Applied shop update has no backup marker");
        if (Files.exists(backupMarker)) {
            if (!Files.isRegularFile(root.resolve(BACKUP).resolve("world/gangshop_wallets.json"))) {
                throw new IllegalStateException("Wallet backup missing");
            }
        } else {
            if (!Files.isRegularFile(root.resolve("world/gangshop_wallets.json"), LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalStateException("Shop wallet missing");
            }
            if (!Files.isDirectory(root.resolve("config/gangshop"), LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalStateException("Shop config missing");
            }
        }
        rejectDuplicates(root);
    }

    static void install(Path root, Map<String, byte[]> payload, Properties sums) throws Exception {
        try (var channel = FileChannel.open(root.resolve(Path.of("world", "session.lock")), StandardOpenOption.WRITE);
             var lock = channel.tryLock()) {
            if (lock == null) throw new IllegalStateException("World is still running");
            preflight(root, payload, sums);
            Path marker = root.resolve(BACKUP).resolve("APPLIED.txt");
            if (Files.exists(marker)) {
                WildBossRestart.check(root.resolve(MOD), sums.getProperty(MOD));
                WildBossRestart.check(root.resolve(GUI_TARGET), GUI_HASH);
                if (Files.exists(root.resolve(OLD))) throw new IllegalStateException("Old shop remains installed");
                return;
            }
            Path backupMarker = root.resolve(BACKUP).resolve("BACKED_UP.txt");
            if (!Files.exists(backupMarker)) {
                backup(root, Path.of(OLD));
                backup(root, Path.of("world", "gangshop_wallets.json"));
                Path config = root.resolve("config/gangshop");
                if (!Files.isDirectory(config, LinkOption.NOFOLLOW_LINKS)) throw new IllegalStateException("Shop config missing");
                try (var files = Files.walk(config)) {
                    for (Path file : files.toList()) {
                        if (Files.isSymbolicLink(file)) throw new IllegalStateException("Shop config contains a symbolic link: " + file);
                        if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) backup(root, root.relativize(file));
                    }
                }
                Files.writeString(backupMarker, BACKED_UP, StandardOpenOption.CREATE_NEW);
            }
            if (!Files.exists(root.resolve(MOD))) WildBossRestart.write(root.resolve(MOD), payload.get(MOD), sums.getProperty(MOD));
            if (!Files.exists(root.resolve(GUI_TARGET))) WildBossRestart.write(root.resolve(GUI_TARGET), Files.readAllBytes(root.resolve(GUI)), GUI_HASH);
            replace(root.resolve(ACCESS), payload.get(ACCESS), sums.getProperty(ACCESS));
            Files.deleteIfExists(root.resolve(OLD));
            Files.writeString(marker, APPLIED, StandardOpenOption.CREATE_NEW);
            System.out.println("[ShopGuiRestart] Shop GUI installed; live configuration and wallet backed up.");
        }
    }

    static void backup(Path root, Path name) throws Exception {
        Path source = root.resolve(name);
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("Missing backup source: " + source);
        }
        Path target = root.resolve(BACKUP).resolve(name);
        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        WildBossRestart.check(target, WildBossRestart.fileHash(source));
    }

    static void replace(Path target, byte[] bytes, String expected) throws Exception {
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), "shop-gui-", ".tmp");
        try {
            Files.write(temporary, bytes);
            WildBossRestart.check(temporary, expected);
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            WildBossRestart.check(target, expected);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static void rejectDuplicates(Path root) throws Exception {
        Pattern ids = Pattern.compile("\"id\"\\s*:\\s*\"(?:gangshop|gang-shop-ozsoihjx)\"");
        try (var files = Files.list(root.resolve("mods"))) {
            for (Path file : files.filter(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".jar")).toList()) {
                String name = "mods/" + file.getFileName();
                if (name.equals(OLD) || name.equals(MOD) || name.equals(GUI_TARGET)) continue;
                try (JarFile jar = new JarFile(file.toFile())) {
                    var entry = jar.getJarEntry("fabric.mod.json");
                    if (entry == null) continue;
                    try (InputStream input = jar.getInputStream(entry)) {
                        if (ids.matcher(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).find()) {
                            throw new IllegalStateException("Unexpected shop jar: " + file);
                        }
                    }
                }
            }
        }
    }

    static Map<String, byte[]> payloads() throws Exception {
        var result = new java.util.LinkedHashMap<String, byte[]>();
        for (String name : EMBEDDED) result.put(name, resource(name));
        return result;
    }

    static Properties checksums() throws Exception {
        var result = new Properties();
        result.load(new java.io.ByteArrayInputStream(resource("checksums.properties")));
        return result;
    }

    static byte[] resource(String name) throws Exception {
        try (InputStream input = ShopGuiRestart.class.getResourceAsStream(RESOURCE + name)) {
            if (input == null) throw new IllegalStateException("Missing shop payload: " + name);
            return input.readAllBytes();
        }
    }
}
