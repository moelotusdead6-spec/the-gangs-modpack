import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Properties;
import java.util.jar.JarFile;

public final class AlexWeaponRestart {
    static final String OLD = "mods/goldclaim-1.0.57.jar";
    static final String NEXT = "mods/goldclaim-1.0.61.jar";
    static final String OLD_HASH = "1bd258dbf520758c7a58ffab43ded0c2e3a190331a82a822c1541b3093bab581";
    static final String PREVIOUS = "mods/goldclaim-1.0.59.jar";
    static final String PREVIOUS_HASH = "c212e89e0700dcbb3a84b9c5fcc173e17eb5de0d9c4901888f47a9ada055a808";
    static final String LIMITS_OLD = "mods/commandlimits-1.0.0.jar";
    static final String LIMITS_NEXT = "mods/commandlimits-1.0.1.jar";
    static final String LIMITS_HASH = "0d95509d5096568ff6aca8f77e7ed9e470408a24b61846987e14bb8deb999e63";
    static final String EVENT_TABLE = "config/paxi/datapacks/gangs_kits/data/gangs/loot_tables/events/reward.json";
    static final String COLLECTIONS = "config/randomcollections/config.json";
    static final Path BACKUP = Path.of("backups/alex-weapon-restart-1.0.61");

    public static void main(String[] args) throws Throwable {
        install(Path.of(".").toAbsolutePath().normalize());
        String main;
        try (JarFile launcher = new JarFile("fabric-server-launch.jar")) {
            main = launcher.getManifest().getMainAttributes().getValue("Main-Class");
        }
        if (main == null || main.equals(AlexWeaponRestart.class.getName())) {
            throw new IllegalStateException("Invalid original Fabric launcher.");
        }
        System.setProperty("java.class.path", Path.of("fabric-server-launch.jar").toAbsolutePath().toString());
        System.out.println("[AlexWeaponRestart] Starting original Fabric launcher.");
        try {
            Class.forName(main).getMethod("main", String[].class).invoke(null, (Object) args);
        } catch (InvocationTargetException failure) {
            throw failure.getCause();
        }
    }

    static void install(Path root) throws Exception {
        Properties hashes = properties("files.properties");
        Properties previous = properties("previous.properties");
        Properties required = properties("required.properties");
        if (!hashes.stringPropertyNames().equals(previous.stringPropertyNames())
                || !hashes.containsKey(NEXT) || !hashes.containsKey(LIMITS_NEXT) || hashes.size() != 40) {
            throw new IllegalStateException("Incomplete restart manifest.");
        }
        for (String file : hashes.stringPropertyNames()) {
            if (!file.equals(NEXT) && !file.equals(LIMITS_NEXT) && !file.equals("config/gangshop/overrides.json")
                                && !file.equals(EVENT_TABLE) && !file.equals(COLLECTIONS)
                                &&  !file.matches("config/alexscaves_biome_generation/(magnetic_caves|primordial_caves|toxic_caves|abyssal_chasm|forlorn_hollows|candy_cavity)\\.json")
                    && !file.matches("config/paxi/datapacks/gangs_fixes/data/(c|forge)/tags/items/[a-z_/]+\\.json")) {
                throw new IllegalStateException("Unexpected payload destination: " + file);
            }
            try (InputStream source = payload(file)) {
                if (!digest(source).equals(hashes.getProperty(file))) {
                    throw new IllegalStateException("Corrupt payload: " + file);
                }
            }
        }
        for (String file : required.stringPropertyNames()) check(root.resolve(file), required.getProperty(file));
        try (FileChannel channel = FileChannel.open(root.resolve("world/session.lock"), StandardOpenOption.WRITE);
             var lock = channel.tryLock()) {
            if (lock == null) throw new IllegalStateException("World is still running; no update applied.");
            Path backup = root.resolve(BACKUP);
            boolean teleportOnly = Files.exists(root.resolve(PREVIOUS)) || Files.exists(backup.resolve(PREVIOUS));
            String previousMod = teleportOnly ? PREVIOUS : OLD;
            String previousHash = teleportOnly ? PREVIOUS_HASH : OLD_HASH;
            Path savedMod = backup.resolve(previousMod);
            boolean applied = Files.exists(backup.resolve("APPLIED.txt"));
            try (var files = Files.list(root.resolve("mods"))) {
                for (Path file : files.toList()) {
                    String name = file.getFileName().toString();
                    if (name.startsWith("goldclaim-") && name.endsWith(".jar")
                            && !name.equals(Path.of(previousMod).getFileName().toString())
                            && !name.equals(Path.of(NEXT).getFileName().toString())) {
                        throw new IllegalStateException("Unexpected active GoldClaim: " + name);
                    }
                    if (name.startsWith("commandlimits-") && name.endsWith(".jar")
                            && (teleportOnly || !name.equals(Path.of(LIMITS_OLD).getFileName().toString()))
                            && !name.equals(Path.of(LIMITS_NEXT).getFileName().toString())) {
                        throw new IllegalStateException("Unexpected active Command Limits: " + name);
                    }
                }
            }
            if (Files.exists(root.resolve(previousMod))) {
                if (applied) throw new IllegalStateException("Old GoldClaim reappeared after installation.");
                check(root.resolve(previousMod), previousHash);
            } else {
                check(savedMod, previousHash);
            }
            if (Files.exists(savedMod)) check(savedMod, previousHash);
            Path savedLimits = backup.resolve(LIMITS_OLD);
            if (teleportOnly) {
                check(root.resolve(LIMITS_NEXT), hashes.getProperty(LIMITS_NEXT));
            } else if (Files.exists(root.resolve(LIMITS_OLD))) {
                if (applied) throw new IllegalStateException("Old Command Limits reappeared after installation.");
                check(root.resolve(LIMITS_OLD), LIMITS_HASH);
            } else {
                check(savedLimits, LIMITS_HASH);
            }
            if (Files.exists(savedLimits)) check(savedLimits, LIMITS_HASH);
            for (String file : hashes.stringPropertyNames()) {
                Path target = root.resolve(file);
                String before = previous.getProperty(file);
                if (applied || (teleportOnly && !file.equals(NEXT) && !file.equals(EVENT_TABLE) && !file.equals(COLLECTIONS))) {
                    check(target, hashes.getProperty(file));
                } else if (Files.exists(target)) {
                    String current = hash(target);
                    if (!current.equals(hashes.getProperty(file)) && !current.equals(before)) {
                        throw new IllegalStateException("File changed since staging; restage: " + file);
                    }
                    if (!before.equals("-") && current.equals(hashes.getProperty(file))) {
                        check(backup.resolve(file), before);
                    }
                } else if (!before.equals("-")) {
                    throw new IllegalStateException("Required existing file is missing: " + file);
                }
                if (!before.equals("-") && Files.exists(backup.resolve(file))) {
                    check(backup.resolve(file), before);
                }
            }
            if (applied) {
                System.out.println("[AlexWeaponRestart] Previously installed event reward, teleport, recipe, Wabbajack, staff locate, RSW cave and coastal terrain fixes verified.");
                return;
            }
            Files.createDirectories(savedMod.getParent());
            if (!Files.exists(savedMod)) {
                Files.copy(root.resolve(previousMod), savedMod);
                check(savedMod, previousHash);
            }
            if (!teleportOnly && !Files.exists(savedLimits)) {
                Files.copy(root.resolve(LIMITS_OLD), savedLimits);
                check(savedLimits, LIMITS_HASH);
            }
            for (String file : hashes.stringPropertyNames()) {
                Path target = root.resolve(file);
                String before = previous.getProperty(file);
                if (Files.exists(target) && hash(target).equals(hashes.getProperty(file))) continue;
                if (!before.equals("-")) {
                    Path saved = backup.resolve(file);
                    Files.createDirectories(saved.getParent());
                    if (!Files.exists(saved)) Files.copy(target, saved);
                    check(saved, before);
                }
                Files.createDirectories(target.getParent());
                Path pending = target.resolveSibling(target.getFileName() + ".alex-1.0.61.pending");
                try (InputStream source = payload(file)) {
                    Files.copy(source, pending, StandardCopyOption.REPLACE_EXISTING);
                }
                check(pending, hashes.getProperty(file));
                Files.move(pending, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                check(target, hashes.getProperty(file));
            }
            Files.deleteIfExists(root.resolve(previousMod));
            if (!teleportOnly) Files.deleteIfExists(root.resolve(LIMITS_OLD));
            Files.writeString(backup.resolve("APPLIED.txt"),
                "GoldClaim 1.0.61: per-player /gangskit event rewards; acceptance-time TPA, request-time TPAHere, player-name autofill; "
                    + "Wabbajack banned; shop entry denied; 29 Alex recipe tags; "
                    + "Command Limits 1.0.1 with staff-only locate; six cave biomes allowed in new RSW terrain; "
                    + "six coastal biomes protected from the sea-level air cut in future terrain.\n",
                StandardOpenOption.CREATE_NEW);
            System.out.println("[AlexWeaponRestart] Update installed; exact original files saved in " + BACKUP);
        }
    }

    private static Properties properties(String name) throws Exception {
        Properties properties = new Properties();
        try (InputStream source = payload(name)) { properties.load(source); }
        return properties;
    }

    private static InputStream payload(String file) {
        InputStream source = AlexWeaponRestart.class.getResourceAsStream("/payload/" + file);
        if (source == null) throw new IllegalStateException("Missing embedded payload: " + file);
        return source;
    }

    private static String hash(Path path) throws Exception {
        try (InputStream source = Files.newInputStream(path)) { return digest(source); }
    }

    private static void check(Path path, String expected) throws Exception {
        if (expected == null || !hash(path).equals(expected)) {
            throw new IllegalStateException("SHA-256 mismatch; refusing to overwrite: " + path);
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
