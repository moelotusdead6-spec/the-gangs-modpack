import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarFile;

/**
 * Restart launcher for Oux Badges replacing Rank Badges. GoldClaim 1.0.66 still decides each player's rank and
 * hands it to Oux Badges (server-only), which shows [Owner]/[Mod]/[Gang]/[Drifter] in chat, the tab list and
 * above heads. While the world is not running it replaces mods/goldclaim-1.0.64.jar or 1.0.65.jar with 1.0.66,
 * removes mods/rankbadges-1.0.3.jar, and installs mods/oux-badges-hiec7dbm.jar plus config/oux-badges.json,
 * keeping the originals in BACKUP. It then hands off to the still-pending gangs-alex-map-seed-restart-1.0.71.jar
 * (or gangs-alex-boss-restart-1.0.70.jar) so those updates are applied on the same start. If any check fails
 * nothing is changed and the server still starts normally.
 */
public final class OuxBadgesRestart {
    static final Path BACKUP = Path.of("backups/oux-badges-restart-1.0.72");
    static final String FABRIC_LAUNCHER_SHA256 = "6f921315fe2f570c331ca877d1e815c29b93fdc4ad04ce0f0f5cdf4300710028";
    static final String MAP_SEED = "gangs-alex-map-seed-restart-1.0.71.jar";
    static final String MAP_SEED_SHA256 = "b42f1dd8616d1b992ded7f5d399e586f8bc443edbe8f6547a84c9a27266b87b3";
    static final String ALEX_BOSS = "gangs-alex-boss-restart-1.0.70.jar";
    static final String ALEX_BOSS_SHA256 = "01d5523cba42dd37543463a227cdfba28529639aa6978bbfff891525e2fcd836";
    static final List<String> OLD_GOLDCLAIMS = List.of("mods/goldclaim-1.0.64.jar", "mods/goldclaim-1.0.65.jar");
    static final String NEW_GOLDCLAIM = "mods/goldclaim-1.0.66.jar";
    static final String RANK_BADGES = "mods/rankbadges-1.0.3.jar";
    static final String OUX_BADGES = "mods/oux-badges-hiec7dbm.jar";
    static final String OUX_CONFIG = "config/oux-badges.json";
    static final String ATTRIBUTE_FIX = "AttributeFix-Fabric-";
    static final String APPLIED = "GoldClaim 1.0.66 + Oux Badges: rank badges from Oux Badges; Rank Badges removed.\n";

    public static void main(String[] args) throws Throwable {
        Path root = Path.of(".").toAbsolutePath().normalize();
        check(root.resolve("fabric-server-launch.jar"), FABRIC_LAUNCHER_SHA256);
        try {
            install(root);
        } catch (Exception failure) {
            System.out.println("[OuxBadgesRestart] Update NOT applied; starting normally. Reason: " + failure.getMessage());
        }
        Path mapSeed = root.resolve(MAP_SEED);
        if (Files.exists(mapSeed) && hash(mapSeed).equals(MAP_SEED_SHA256)) {
            System.out.println("[OuxBadgesRestart] Handing off to " + MAP_SEED + ".");
            invokeMain(Class.forName("AlexMapSeedRestart"), args);
            return;
        }
        Path alexBoss = root.resolve(ALEX_BOSS);
        if (Files.exists(alexBoss) && hash(alexBoss).equals(ALEX_BOSS_SHA256)) {
            System.out.println("[OuxBadgesRestart] " + MAP_SEED + " missing or changed; handing off to " + ALEX_BOSS + ".");
            invokeMain(Class.forName("AlexBossRestart"), args);
            return;
        }
        System.out.println("[OuxBadgesRestart] Chained launchers missing or changed; starting original Fabric launcher.");
        String main;
        try (JarFile launcher = new JarFile("fabric-server-launch.jar")) {
            main = launcher.getManifest().getMainAttributes().getValue("Main-Class");
        }
        if (main == null || main.equals(OuxBadgesRestart.class.getName())) {
            throw new IllegalStateException("Invalid original Fabric launcher.");
        }
        System.setProperty("java.class.path", Path.of("fabric-server-launch.jar").toAbsolutePath().toString());
        invokeMain(Class.forName(main), args);
    }

    static void invokeMain(Class<?> type, String[] args) throws Throwable {
        try {
            type.getMethod("main", String[].class).invoke(null, (Object)args);
        } catch (java.lang.reflect.InvocationTargetException failure) {
            throw failure.getCause();
        }
    }

    static void install(Path root) throws Exception {
        Properties hashes = properties("files.properties");
        for (String name : List.of(OLD_GOLDCLAIMS.get(0), OLD_GOLDCLAIMS.get(1), NEW_GOLDCLAIM, RANK_BADGES, OUX_BADGES, OUX_CONFIG)) {
            if (hashes.getProperty(name) == null) throw new IllegalStateException("Incomplete restart manifest.");
        }
        for (String name : List.of(NEW_GOLDCLAIM, OUX_BADGES, OUX_CONFIG)) {
            try (InputStream stream = payload(name)) {
                if (!digest(stream.readAllBytes()).equals(hashes.getProperty(name))) throw new IllegalStateException("Corrupt payload.");
            }
        }
        try (FileChannel channel = FileChannel.open(root.resolve("world/session.lock"), StandardOpenOption.WRITE);
             var lock = channel.tryLock()) {
            if (lock == null) throw new IllegalStateException("World is still running.");
            Path marker = root.resolve(BACKUP).resolve("APPLIED.txt");
            requireOnly(root, "goldclaim-", "goldclaim-1.0.64.jar", "goldclaim-1.0.65.jar", "goldclaim-1.0.66.jar");
            requireOnly(root, "rankbadges", "rankbadges-1.0.3.jar");
            requireOnly(root, "oux-badges", "oux-badges-hiec7dbm.jar");
            if (Files.exists(marker)) {
                verifyInstalled(root, hashes);
                System.out.println("[OuxBadgesRestart] Update already installed and verified.");
                return;
            }
            // GoldClaim 1.0.66 carries the 1.0.65 boss health targets, which need AttributeFix above 1024 health.
            requireAttributeFix(root);

            // Validate everything before changing anything; a partial earlier run is resumable.
            String oldGoldClaim = oldGoldClaim(root, hashes);
            Path rankBadges = root.resolve(RANK_BADGES);
            if (Files.exists(rankBadges) && !hash(rankBadges).equals(hashes.getProperty(RANK_BADGES))) {
                throw new IllegalStateException("File changed since staging: " + RANK_BADGES);
            }
            for (String name : List.of(NEW_GOLDCLAIM, OUX_BADGES, OUX_CONFIG)) {
                Path file = root.resolve(name);
                if (Files.exists(file) && !hash(file).equals(hashes.getProperty(name))) {
                    throw new IllegalStateException("Unexpected target file: " + name);
                }
            }

            backup(root, oldGoldClaim, hashes);
            if (Files.exists(rankBadges)) backup(root, RANK_BADGES, hashes);
            for (String name : List.of(NEW_GOLDCLAIM, OUX_BADGES, OUX_CONFIG)) {
                if (!Files.exists(root.resolve(name))) {
                    try (InputStream stream = payload(name)) {
                        write(root.resolve(name), stream.readAllBytes(), hashes.getProperty(name));
                    }
                }
            }
            Files.deleteIfExists(root.resolve(oldGoldClaim));
            Files.deleteIfExists(rankBadges);
            verifyInstalled(root, hashes);
            Files.writeString(marker, APPLIED, StandardOpenOption.CREATE_NEW);
            System.out.println("[OuxBadgesRestart] Update installed; originals saved in " + BACKUP);
        }
    }

    /** Returns the GoldClaim jar being replaced: the active 1.0.64/1.0.65 jar, or its backup from an interrupted run. */
    static String oldGoldClaim(Path root, Properties hashes) throws Exception {
        List<String> active = new ArrayList<>();
        for (String name : OLD_GOLDCLAIMS) if (Files.exists(root.resolve(name))) active.add(name);
        if (active.size() > 1) throw new IllegalStateException("More than one GoldClaim jar is active.");
        if (active.size() == 1) {
            String name = active.get(0);
            if (!hash(root.resolve(name)).equals(hashes.getProperty(name))) {
                throw new IllegalStateException("File changed since staging: " + name);
            }
            return name;
        }
        for (String name : OLD_GOLDCLAIMS) {
            Path saved = root.resolve(BACKUP).resolve(name);
            if (Files.exists(saved) && hash(saved).equals(hashes.getProperty(name))) return name;
        }
        throw new IllegalStateException("Original GoldClaim jar and backup are missing.");
    }

    static void backup(Path root, String name, Properties hashes) throws Exception {
        Path saved = root.resolve(BACKUP).resolve(name);
        if (!Files.exists(saved)) {
            Files.createDirectories(saved.getParent());
            Files.copy(root.resolve(name), saved);
        }
        check(saved, hashes.getProperty(name));
    }

    static void requireOnly(Path root, String prefix, String... allowed) throws Exception {
        try (var files = Files.list(root.resolve("mods"))) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                if (name.startsWith(prefix) && name.endsWith(".jar") && !List.of(allowed).contains(name)) {
                    throw new IllegalStateException("Unexpected active mod: " + name);
                }
            }
        }
    }

    static void requireAttributeFix(Path root) throws Exception {
        try (var files = Files.list(root.resolve("mods"))) {
            if (files.noneMatch(file -> file.getFileName().toString().startsWith(ATTRIBUTE_FIX)
                    && file.getFileName().toString().endsWith(".jar"))) {
                throw new IllegalStateException("AttributeFix is not installed.");
            }
        }
    }

    static void verifyInstalled(Path root, Properties hashes) throws Exception {
        check(root.resolve(NEW_GOLDCLAIM), hashes.getProperty(NEW_GOLDCLAIM));
        check(root.resolve(OUX_BADGES), hashes.getProperty(OUX_BADGES));
        // Oux Badges rewrites its config on load and admins may tune it, so only its presence is checked.
        if (!Files.exists(root.resolve(OUX_CONFIG))) throw new IllegalStateException("Missing " + OUX_CONFIG);
        for (String name : OLD_GOLDCLAIMS) {
            if (Files.exists(root.resolve(name))) throw new IllegalStateException("Old GoldClaim jar is still active.");
        }
        if (Files.exists(root.resolve(RANK_BADGES))) throw new IllegalStateException("Rank Badges is still active.");
        boolean saved = false;
        for (String name : OLD_GOLDCLAIMS) {
            Path backup = root.resolve(BACKUP).resolve(name);
            if (Files.exists(backup)) {
                check(backup, hashes.getProperty(name));
                saved = true;
            }
        }
        if (!saved) throw new IllegalStateException("GoldClaim backup is missing.");
    }

    static void write(Path destination, byte[] bytes, String expected) throws Exception {
        if (!digest(bytes).equals(expected)) throw new IllegalStateException("Unexpected bytes for " + destination);
        Files.createDirectories(destination.getParent());
        Path temporary = destination.resolveSibling(destination.getFileName() + ".ouxbadges-tmp");
        Files.write(temporary, bytes);
        try {
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
        }
        check(destination, expected);
    }

    static Properties properties(String name) throws Exception {
        Properties result = new Properties();
        try (InputStream stream = payload(name)) {
            result.load(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8));
        }
        return result;
    }

    static InputStream payload(String name) {
        InputStream stream = OuxBadgesRestart.class.getResourceAsStream("/ouxbadges-payload/" + name);
        if (stream == null) throw new IllegalStateException("Missing payload: " + name);
        return stream;
    }

    static void check(Path file, String expected) throws Exception {
        if (!Files.exists(file) || !hash(file).equals(expected)) {
            throw new IllegalStateException("Unexpected file hash: " + file);
        }
    }

    static String hash(Path file) throws Exception {
        try (InputStream stream = Files.newInputStream(file)) {
            return digest(stream.readAllBytes());
        }
    }

    static String digest(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
