import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

public final class GangGemsRestart {
    static final String PREDECESSOR = "gangs-gobber-shop-restart-1.0.79-r2.jar";
    static final String PREDECESSOR_HASH = "d9b8f677df014bf34fbb96e7ec0afdf5d47dd54852644dd33da5eb8d46f80130";
    static final String FABRIC_HASH = "6f921315fe2f570c331ca877d1e815c29b93fdc4ad04ce0f0f5cdf4300710028";
    static final String FURNITURE = "mods/immersive_furniture-fabric-0.4.1+1.20.1.jar";
    static final String FURNITURE_HASH = "c9e2cd4138d9fc23b61a02823290990b181d9de9a2bbb903eeefe73c772970dc";
    static final String DISCORD = "mods/dcintegration-fabric-3.0.7-1.20.1.jar";
    static final String DISCORD_HASH = "8b7c543042b0316f35f224c6437e78a63c8b298eb549fac85965754875f0b00c";
    static final String MOD = "mods/gangsgems-0.1.1.jar";
    static final String RESOURCE = "/gangsgems-0.1.1-payload/";
    static final Path MARKER = Path.of("backups", "gangsgems-0.1.1", "APPLIED.txt");
    static final String APPLIED = "Gang Gems 0.1.1 installed with payment and furniture changes disabled.\n";
    private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*\"gangsgems\"");

    public static void main(String[] args) throws Throwable {
        Path root = Path.of(".").toAbsolutePath().normalize();
        install(root, resource("gangsgems-0.1.1.jar"), new String(resource("sha256.txt"), StandardCharsets.US_ASCII).trim());
        try {
            Class.forName("GobberShopRestart").getMethod("main", String[].class).invoke(null, (Object) args);
        } catch (java.lang.reflect.InvocationTargetException failure) {
            throw failure.getCause();
        }
    }

    static void install(Path root, byte[] payload, String hash) throws Exception {
        if (!hash.matches("[0-9a-f]{64}") || !GobberShopRestart.hash(payload).equals(hash)) {
            throw new IllegalStateException("Invalid Gang Gems payload checksum");
        }
        try (var channel = FileChannel.open(root.resolve(Path.of("world", "session.lock")), StandardOpenOption.WRITE);
             var lock = channel.tryLock()) {
            if (lock == null) throw new IllegalStateException("World is still running");
            GobberShopRestart.check(root.resolve(PREDECESSOR), PREDECESSOR_HASH);
            GobberShopRestart.check(root.resolve("fabric-server-launch.jar"), FABRIC_HASH);
            GobberShopRestart.check(root.resolve(FURNITURE), FURNITURE_HASH);
            GobberShopRestart.check(root.resolve(DISCORD), DISCORD_HASH);
            GobberShopRestart.preflight(root, GobberShopRestart.payloads(), GobberShopRestart.checksums());
            GobberShopRestart.marker(root.resolve(GobberShopRestart.BACKUP).resolve("APPLIED.txt"), GobberShopRestart.APPLIED);
            for (String name : GobberShopRestart.EMBEDDED) {
                GobberShopRestart.check(root.resolve(name), GobberShopRestart.checksums().getProperty(name));
            }
            for (String name : GobberShopRestart.OLD.keySet()) {
                if (Files.exists(root.resolve(name))) throw new IllegalStateException("Old mod remains: " + name);
            }
            String gobberConfig = Files.readString(root.resolve(GobberShopRestart.CONFIG));
            if (!GobberShopRestart.disableUnbreakable(gobberConfig).equals(gobberConfig)) {
                throw new IllegalStateException("Gobber unbreakableEndTools was re-enabled");
            }
            rejectDuplicates(root);
            Path target = root.resolve(MOD);
            Path marker = root.resolve(MARKER);
            if (Files.exists(marker)) {
                GobberShopRestart.marker(marker, APPLIED);
                GobberShopRestart.check(target, hash);
                return;
            }
            for (String config : new String[]{"gangsgems-furniture.json", "gangsgems-discord.json", "gangsgems.json"}) {
                if (Files.exists(root.resolve("config").resolve(config), LinkOption.NOFOLLOW_LINKS)) {
                    throw new IllegalStateException("Unexpected existing Gang Gems configuration: " + config);
                }
            }
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                GobberShopRestart.check(target, hash);
            } else {
                Path temporary = Files.createTempFile(target.getParent(), "gangsgems-", ".tmp");
                try {
                    Files.write(temporary, payload);
                    GobberShopRestart.check(temporary, hash);
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
                    GobberShopRestart.check(target, hash);
                } finally {
                    Files.deleteIfExists(temporary);
                }
            }
            Files.createDirectories(marker.getParent());
            Files.writeString(marker, APPLIED, StandardOpenOption.CREATE_NEW);
            System.out.println("[GangGemsRestart] Gang Gems installed in inactive test mode.");
        }
    }

    static void rejectDuplicates(Path root) throws Exception {
        try (var paths = Files.list(root.resolve("mods"))) {
            for (Path path : paths.filter(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".jar")).toList()) {
                if (path.equals(root.resolve(MOD))) continue;
                try (JarFile jar = new JarFile(path.toFile())) {
                    var entry = jar.getJarEntry("fabric.mod.json");
                    if (entry == null) continue;
                    try (InputStream input = jar.getInputStream(entry)) {
                        if (ID.matcher(new String(input.readAllBytes(), StandardCharsets.UTF_8)).find()) {
                            throw new IllegalStateException("Unexpected competing Gang Gems mod: " + path);
                        }
                    }
                }
            }
        }
    }

    static byte[] resource(String name) throws Exception {
        try (InputStream input = GangGemsRestart.class.getResourceAsStream(RESOURCE + name)) {
            if (input == null) throw new IllegalStateException("Missing Gang Gems payload: " + name);
            return input.readAllBytes();
        }
    }
}
