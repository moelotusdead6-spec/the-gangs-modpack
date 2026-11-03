import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarFile;

public final class ClaimLunarRestart {
    private static final String OLD = "goldclaim-1.0.51.jar";
    private static final String NEXT = "goldclaim-1.0.52.jar";
    private static final String PACK = "config/paxi/datapacks/gangs_fixes/data/";
    private static final List<String> EVENTS = List.of(
        "blue_moon.json", "super_blue_moon.json", "harvest_moon.json", "super_harvest_moon.json", "super_moon.json");
    private static final String EVENT_PATH = PACK + "enhancedcelestials/enhancedcelestials/lunar/event/";
    private static final Path BACKUP = Path.of("backups", "next-restart-goldclaim-1.0.52");
    private static final List<String> REQUIRED = List.of(
        "fabric-server-launch.jar",
        "mods/supplementaries-1.20-3.1.44-fabric.jar",
        "mods/Enhanced-Celestials-fabric-1.20.1-5.0.3.1.jar",
        PACK + "multiworld/enhancedcelestials/lunar/dimension_settings/rsw.json",
        PACK + "enhancedcelestials/enhancedcelestials/lunar/event/blood_moon.json",
        PACK + "enhancedcelestials/enhancedcelestials/lunar/event/super_blood_moon.json");

    public static void main(String[] args) throws Throwable {
        install(Path.of(".").toAbsolutePath().normalize());
        String main;
        try (JarFile jar = new JarFile("fabric-server-launch.jar")) {
            main = jar.getManifest().getMainAttributes().getValue("Main-Class");
        }
        if (main == null || main.equals(ClaimLunarRestart.class.getName())) {
            throw new IllegalStateException("Invalid original Fabric launcher.");
        }
        System.setProperty("java.class.path", Path.of("fabric-server-launch.jar").toAbsolutePath().toString());
        System.out.println("[ClaimLunarRestart] Starting original Fabric launcher.");
        try {
            Class.forName(main).getMethod("main", String[].class).invoke(null, (Object)args);
        } catch (InvocationTargetException failure) {
            throw failure.getCause();
        }
    }

    static void install(Path root) throws Exception {
        Properties hashes = new Properties();
        try (InputStream source = payload("hashes.properties")) {
            hashes.load(source);
        }
        for (String path : REQUIRED) check(root.resolve(path), hashes.getProperty(path));
        checkPayload(NEXT, hashes.getProperty(NEXT));
        for (String event : EVENTS) checkPayload(event, hashes.getProperty(event));
        try (FileChannel channel = FileChannel.open(root.resolve("world/session.lock"), StandardOpenOption.WRITE);
             var lock = channel.tryLock()) {
            if (lock == null) throw new IllegalStateException("World is still running; no update applied.");
            Path old = root.resolve("mods").resolve(OLD);
            Path target = root.resolve("mods").resolve(NEXT);
            Path saved = root.resolve(BACKUP).resolve(OLD);
            try (var files = Files.list(root.resolve("mods"))) {
                for (Path file : files.toList()) {
                    String name = file.getFileName().toString();
                    if (name.startsWith("goldclaim-") && name.endsWith(".jar")
                            && !name.equals(OLD) && !name.equals(NEXT)) {
                        throw new IllegalStateException("Unexpected active GoldClaim: " + name);
                    }
                }
            }
            if (Files.exists(old)) check(old, hashes.getProperty(OLD));
            else {
                check(saved, hashes.getProperty(OLD));
                // A previous interrupted installation may have archived the old jar.
            }
            if (Files.exists(saved)) check(saved, hashes.getProperty(OLD));
            if (Files.exists(target)) check(target, hashes.getProperty(NEXT));
            for (String event : EVENTS) {
                Path events = root.resolve(EVENT_PATH + event);
                if (Files.exists(events)) check(events, hashes.getProperty(event));
            }
            Path marker = root.resolve(BACKUP).resolve("APPLIED.txt");
            if (Files.exists(marker)) {
                if (Files.exists(old)) throw new IllegalStateException("Old GoldClaim reappeared after installation.");
                check(target, hashes.getProperty(NEXT));
                for (String event : EVENTS) check(root.resolve(EVENT_PATH + event), hashes.getProperty(event));
                System.out.println("[ClaimLunarRestart] Previously installed patch verified.");
                return;
            }
            Files.createDirectories(saved.getParent());
            if (!Files.exists(saved)) {
                Files.copy(old, saved);
                check(saved, hashes.getProperty(OLD));
            }
            installPayload(NEXT, target, hashes.getProperty(NEXT));
            for (String event : EVENTS) {
                installPayload(event, root.resolve(EVENT_PATH + event), hashes.getProperty(event));
            }
            if (Files.exists(old)) Files.delete(old);
            Files.writeString(marker,
                "GoldClaim 1.0.52: 15-minute ground cleanup, claimed bomb/cannonball protection; "
                + "complete RSW lunar event pool with Wild Blue/Harvest moons preserved.\n",
                StandardOpenOption.CREATE_NEW);
            System.out.println("[ClaimLunarRestart] Patch installed; original GoldClaim saved in " + BACKUP);
        }
    }

    private static void installPayload(String resource, Path target, String hash) throws Exception {
        if (Files.exists(target)) {
            check(target, hash);
            return;
        }
        Files.createDirectories(target.getParent());
        Path pending = target.resolveSibling(target.getFileName() + ".pending");
        try (InputStream source = payload(resource)) {
            Files.copy(source, pending, StandardCopyOption.REPLACE_EXISTING);
        }
        check(pending, hash);
        Files.move(pending, target, StandardCopyOption.ATOMIC_MOVE);
        check(target, hash);
    }

    private static InputStream payload(String name) {
        InputStream source = ClaimLunarRestart.class.getResourceAsStream("/payload/" + name);
        if (source == null) throw new IllegalStateException("Missing embedded payload: " + name);
        return source;
    }

    private static void checkPayload(String name, String expected) throws Exception {
        try (InputStream source = payload(name)) {
            if (!digest(source).equals(expected)) throw new IllegalStateException("Corrupt payload: " + name);
        }
    }

    private static void check(Path path, String expected) throws Exception {
        try (InputStream source = Files.newInputStream(path)) {
            if (expected == null || !digest(source).equalsIgnoreCase(expected)) {
                throw new IllegalStateException("SHA-256 mismatch; refusing to overwrite: " + path);
            }
        }
    }

    private static String digest(InputStream source) throws Exception {
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[65536];
        int count;
        while ((count = source.read(buffer)) != -1) hash.update(buffer, 0, count);
        return HexFormat.of().formatHex(hash.digest());
    }
}
