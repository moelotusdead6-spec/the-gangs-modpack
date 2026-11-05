import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.Properties;

/**
 * Regression tests for OuxBadgesRestart.
 * Usage: java OuxBadgesRestartTest <goldclaim-1.0.64.jar> <goldclaim-1.0.65.jar> <rankbadges-1.0.3.jar>
 */
public final class OuxBadgesRestartTest {
    static Path goldClaim64;
    static Path goldClaim65;
    static Path rankBadges;

    public static void main(String[] args) throws Exception {
        if (args.length == 3 && args[0].equals("--hold-lock")) {
            holdLock(Path.of(args[1]), Path.of(args[2]));
            return;
        }
        goldClaim64 = Path.of(args[0]);
        goldClaim65 = Path.of(args[1]);
        rankBadges = Path.of(args[2]);
        freshInstallFrom64();
        freshInstallFrom65();
        installWithoutRankBadges();
        secondRunIsNoOp();
        interruptedRunResumes();
        refusesChangedGoldClaim();
        refusesTwoGoldClaims();
        refusesUnexpectedRankBadges();
        refusesChangedConfig();
        refusesMissingAttributeFix();
        refusesRunningWorld();
        System.out.println("OuxBadgesRestartTest passed");
    }

    static Path liveRoot(Path goldClaim) throws Exception {
        Path root = Files.createTempDirectory("oux-badges-restart");
        Files.createDirectories(root.resolve("mods"));
        Files.copy(goldClaim, root.resolve("mods").resolve(goldClaim.getFileName()));
        Files.copy(rankBadges, root.resolve(OuxBadgesRestart.RANK_BADGES));
        Files.writeString(root.resolve("mods/AttributeFix-Fabric-1.20.1-21.0.5.jar"), "attributefix");
        Files.writeString(root.resolve("mods/goldclaimmap-1.0.2.jar"), "client map");
        Files.createDirectories(root.resolve("config"));
        Files.writeString(root.resolve("config/goldclaim.json"), "{}");
        Files.createDirectories(root.resolve("world"));
        Files.writeString(root.resolve("world/session.lock"), "lock");
        return root;
    }

    static void freshInstallFrom64() throws Exception {
        Path root = liveRoot(goldClaim64);
        OuxBadgesRestart.install(root);
        verify(root);
        expect(Files.exists(root.resolve(OuxBadgesRestart.BACKUP).resolve("mods/goldclaim-1.0.64.jar")), "1.0.64 saved");
        expect(Files.exists(root.resolve(OuxBadgesRestart.BACKUP).resolve(OuxBadgesRestart.RANK_BADGES)), "Rank Badges saved");
        expect(Files.readString(root.resolve("config/goldclaim.json")).equals("{}"), "other config untouched");
        expect(Files.readString(root.resolve("mods/goldclaimmap-1.0.2.jar")).equals("client map"), "other mods untouched");
        delete(root);
    }

    static void freshInstallFrom65() throws Exception {
        Path root = liveRoot(goldClaim65);
        OuxBadgesRestart.install(root);
        verify(root);
        expect(Files.exists(root.resolve(OuxBadgesRestart.BACKUP).resolve("mods/goldclaim-1.0.65.jar")), "1.0.65 saved");
        delete(root);
    }

    static void installWithoutRankBadges() throws Exception {
        Path root = liveRoot(goldClaim64);
        Files.delete(root.resolve(OuxBadgesRestart.RANK_BADGES));
        OuxBadgesRestart.install(root);
        verify(root);
        delete(root);
    }

    static void secondRunIsNoOp() throws Exception {
        Path root = liveRoot(goldClaim64);
        OuxBadgesRestart.install(root);
        Path oux = root.resolve(OuxBadgesRestart.OUX_BADGES);
        long modified = Files.getLastModifiedTime(oux).toMillis();
        // Oux Badges may rewrite or an admin may tune the config after the first start.
        Files.writeString(root.resolve(OuxBadgesRestart.OUX_CONFIG), "{\"format\": \"<{badge}> \"}");
        OuxBadgesRestart.install(root);
        expect(modified == Files.getLastModifiedTime(oux).toMillis(), "rerun rewrites nothing");
        expect(Files.readString(root.resolve(OuxBadgesRestart.OUX_CONFIG)).contains("<{badge}>"), "tuned config kept");
        delete(root);
    }

    static void interruptedRunResumes() throws Exception {
        Path root = liveRoot(goldClaim64);
        Path saved = root.resolve(OuxBadgesRestart.BACKUP).resolve("mods/goldclaim-1.0.64.jar");
        Files.createDirectories(saved.getParent());
        Files.copy(root.resolve("mods/goldclaim-1.0.64.jar"), saved);
        try (var stream = OuxBadgesRestart.payload(OuxBadgesRestart.NEW_GOLDCLAIM)) {
            Files.write(root.resolve(OuxBadgesRestart.NEW_GOLDCLAIM), stream.readAllBytes());
        }
        Files.delete(root.resolve("mods/goldclaim-1.0.64.jar"));
        OuxBadgesRestart.install(root);
        verify(root);
        delete(root);
    }

    static void refusesChangedGoldClaim() throws Exception {
        Path root = liveRoot(goldClaim64);
        Files.writeString(root.resolve("mods/goldclaim-1.0.64.jar"), "changed");
        expectFailure(root, "File changed since staging");
        expectUntouched(root);
        delete(root);
    }

    static void refusesTwoGoldClaims() throws Exception {
        Path root = liveRoot(goldClaim64);
        Files.copy(goldClaim65, root.resolve("mods/goldclaim-1.0.65.jar"));
        expectFailure(root, "More than one GoldClaim");
        expectUntouched(root);
        delete(root);
    }

    static void refusesUnexpectedRankBadges() throws Exception {
        Path root = liveRoot(goldClaim64);
        Files.writeString(root.resolve("mods/rankbadges-1.0.4.jar"), "newer");
        expectFailure(root, "Unexpected active mod");
        expectUntouched(root);
        delete(root);
    }

    static void refusesChangedConfig() throws Exception {
        Path root = liveRoot(goldClaim64);
        Files.writeString(root.resolve(OuxBadgesRestart.OUX_CONFIG), "{}");
        expectFailure(root, "Unexpected target file");
        expectUntouched(root);
        delete(root);
    }

    static void refusesMissingAttributeFix() throws Exception {
        Path root = liveRoot(goldClaim65);
        Files.delete(root.resolve("mods/AttributeFix-Fabric-1.20.1-21.0.5.jar"));
        expectFailure(root, "AttributeFix is not installed");
        expect(Files.exists(root.resolve("mods/goldclaim-1.0.65.jar")), "1.0.65 untouched");
        delete(root);
    }

    static void refusesRunningWorld() throws Exception {
        Path root = liveRoot(goldClaim64);
        Path ready = root.resolve("locked.ready");
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process holder = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"), "OuxBadgesRestartTest",
            "--hold-lock", root.resolve("world/session.lock").toString(), ready.toString()).inheritIO().start();
        try {
            for (int i = 0; i < 200 && !Files.exists(ready); i++) Thread.sleep(50);
            expect(Files.exists(ready), "lock holder started");
            expectFailure(root, "World is still running");
        } finally {
            holder.destroy();
            holder.waitFor();
        }
        expect(!Files.exists(root.resolve(OuxBadgesRestart.BACKUP)), "no backup while running");
        expectUntouched(root);
        Files.delete(ready);
        delete(root);
    }

    static void holdLock(Path lockFile, Path ready) throws Exception {
        try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.WRITE);
             var lock = channel.lock()) {
            Files.writeString(ready, "locked");
            Thread.sleep(60_000);
        }
    }

    static void verify(Path root) throws Exception {
        Properties hashes = OuxBadgesRestart.properties("files.properties");
        OuxBadgesRestart.verifyInstalled(root, hashes);
        OuxBadgesRestart.check(root.resolve(OuxBadgesRestart.OUX_CONFIG), hashes.getProperty(OuxBadgesRestart.OUX_CONFIG));
        expect(Files.exists(root.resolve(OuxBadgesRestart.BACKUP).resolve("APPLIED.txt")), "marker written");
    }

    static void expectUntouched(Path root) throws Exception {
        expect(!Files.exists(root.resolve(OuxBadgesRestart.NEW_GOLDCLAIM)), "GoldClaim 1.0.66 not installed");
        expect(!Files.exists(root.resolve(OuxBadgesRestart.OUX_BADGES)), "Oux Badges not installed");
        expect(Files.exists(root.resolve(OuxBadgesRestart.RANK_BADGES)), "Rank Badges untouched");
    }

    static void expectFailure(Path root, String message) throws Exception {
        try {
            OuxBadgesRestart.install(root);
        } catch (IllegalStateException expected) {
            expect(expected.getMessage().contains(message), "expected '" + message + "', got " + expected.getMessage());
            expect(!Files.exists(root.resolve(OuxBadgesRestart.BACKUP).resolve("APPLIED.txt")), "no marker after refusal");
            return;
        }
        throw new AssertionError("Expected refusal: " + message);
    }

    static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static void delete(Path root) throws Exception {
        try (var files = Files.walk(root)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
        }
    }
}
