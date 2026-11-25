import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

public final class GangGemsUpgrade {
    static final String OLD = "mods/gangsgems-0.1.1.jar";
    static final String OLD_HASH = "1876e77e549ebe57a6c60b8734c20c14d46541c8e19902f34bc4abdec444ef1c";
    static final String NEW = "mods/gangsgems-0.1.2.jar";
    static final Path BACKUP = Path.of("backups", "gangsgems-0.1.2");
    static final String APPLIED = "Gang Gems 0.1.2 installed with existing configuration and wallet preserved.\n";
    private static final String RESOURCE = "/gangsgems-0.1.2-payload/";
    private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*\"gangsgems\"");

    public static void main(String[] args) throws Throwable {
        Path root = Path.of(".").toAbsolutePath().normalize();
        install(root, resource("gangsgems-0.1.2.jar"),
            new String(resource("sha256.txt"), StandardCharsets.US_ASCII).trim());
        try {
            Class.forName("GobberShopRestart").getMethod("main", String[].class).invoke(null, (Object) args);
        } catch (java.lang.reflect.InvocationTargetException failure) {
            throw failure.getCause();
        }
    }

    static void install(Path root, byte[] payload, String hash) throws Exception {
        if (!hash.matches("[0-9a-f]{64}") || !GobberShopRestart.hash(payload).equals(hash)) {
            throw new IllegalStateException("Invalid Gang Gems upgrade payload checksum");
        }
        try (var channel = FileChannel.open(root.resolve(Path.of("world", "session.lock")), StandardOpenOption.WRITE);
             var lock = channel.tryLock()) {
            if (lock == null) throw new IllegalStateException("World is still running");
            GobberShopRestart.check(root.resolve(GangGemsRestart.PREDECESSOR), GangGemsRestart.PREDECESSOR_HASH);
            GobberShopRestart.check(root.resolve("fabric-server-launch.jar"), GangGemsRestart.FABRIC_HASH);
            GobberShopRestart.check(root.resolve(GangGemsRestart.FURNITURE), GangGemsRestart.FURNITURE_HASH);
            GobberShopRestart.check(root.resolve(GangGemsRestart.DISCORD), GangGemsRestart.DISCORD_HASH);
            GobberShopRestart.preflight(root, GobberShopRestart.payloads(), GobberShopRestart.checksums());
            GobberShopRestart.marker(root.resolve(GobberShopRestart.BACKUP).resolve("APPLIED.txt"), GobberShopRestart.APPLIED);
            for (String name : GobberShopRestart.EMBEDDED) {
                GobberShopRestart.check(root.resolve(name), GobberShopRestart.checksums().getProperty(name));
            }
            for (String name : GobberShopRestart.OLD.keySet()) {
                if (Files.exists(root.resolve(name), LinkOption.NOFOLLOW_LINKS)) {
                    throw new IllegalStateException("Old mod remains: " + name);
                }
            }
            String gobberConfig = Files.readString(root.resolve(GobberShopRestart.CONFIG));
            if (!GobberShopRestart.disableUnbreakable(gobberConfig).equals(gobberConfig)) {
                throw new IllegalStateException("Gobber unbreakableEndTools was re-enabled");
            }
            GobberShopRestart.marker(root.resolve(GangGemsRestart.MARKER), GangGemsRestart.APPLIED);
            rejectDuplicates(root);

            Path old = root.resolve(OLD);
            Path target = root.resolve(NEW);
            Path backup = root.resolve(BACKUP).resolve(OLD);
            Path marker = root.resolve(BACKUP).resolve("APPLIED.txt");
            if (Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) {
                GobberShopRestart.marker(marker, APPLIED);
                GobberShopRestart.check(backup, OLD_HASH);
                GobberShopRestart.check(target, hash);
                if (Files.exists(old, LinkOption.NOFOLLOW_LINKS)) throw new IllegalStateException("Old Gang Gems mod remains");
                return;
            }
            boolean hasOld = Files.exists(old, LinkOption.NOFOLLOW_LINKS);
            boolean hasBackup = Files.exists(backup, LinkOption.NOFOLLOW_LINKS);
            boolean hasTarget = Files.exists(target, LinkOption.NOFOLLOW_LINKS);
            if (hasOld) GobberShopRestart.check(old, OLD_HASH);
            if (hasBackup) GobberShopRestart.check(backup, OLD_HASH);
            if (hasTarget) GobberShopRestart.check(target, hash);
            if (!hasOld && !(hasBackup && hasTarget)) {
                throw new IllegalStateException("Original Gang Gems mod or recoverable upgrade state missing");
            }
            if (!hasBackup) {
                Files.createDirectories(backup.getParent());
                GobberShopRestart.write(backup, Files.readAllBytes(old), OLD_HASH, false);
            }
            if (!hasTarget) GobberShopRestart.write(target, payload, hash, false);
            if (hasOld) Files.delete(old);
            Files.createDirectories(marker.getParent());
            byte[] markerBytes = APPLIED.getBytes(StandardCharsets.UTF_8);
            GobberShopRestart.write(marker, markerBytes, GobberShopRestart.hash(markerBytes), false);
            System.out.println("[GangGemsUpgrade] Installed 0.1.2; previous mod backed up.");
        }
    }

    private static void rejectDuplicates(Path root) throws Exception {
        try (var paths = Files.list(root.resolve("mods"))) {
            for (Path path : paths.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")).toList()) {
                if (path.equals(root.resolve(OLD)) || path.equals(root.resolve(NEW))) continue;
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
        try (InputStream input = GangGemsUpgrade.class.getResourceAsStream(RESOURCE + name)) {
            if (input == null) throw new IllegalStateException("Missing Gang Gems upgrade payload: " + name);
            return input.readAllBytes();
        }
    }
}
