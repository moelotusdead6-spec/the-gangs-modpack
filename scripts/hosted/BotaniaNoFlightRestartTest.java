import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class BotaniaNoFlightRestartTest {
    private static final List<Path> ROOTS = new ArrayList<>();
    private static Path botania;

    public static void main(String[] args) throws Exception {
        byte[] payload = Files.readAllBytes(Path.of(args[0]));
        botania = Path.of(args[1]);
        try {
            Path root = root();
            BotaniaNoFlightRestart.install(root, payload);
            BotaniaNoFlightRestart.install(root, payload);
            BotaniaNoFlightRestart.check(root.resolve("mods").resolve(BotaniaNoFlightRestart.MOD),
                BotaniaNoFlightRestart.MOD_SHA256);
            require(Files.mismatch(botania, root.resolve("mods").resolve(BotaniaNoFlightRestart.BOTANIA)) == -1,
                "Original Botania changed");
            Path corrupt = root();
            byte[] damaged = payload.clone();
            damaged[0] ^= 1;
            expectFailure(() -> BotaniaNoFlightRestart.install(corrupt, damaged), "Corrupt");
            unchanged(corrupt);
            Path duplicate = root();
            Files.writeString(duplicate.resolve("mods/Botania-old.jar"), "preserve");
            expectFailure(() -> BotaniaNoFlightRestart.install(duplicate, payload), "Unexpected mod jars");
            unchanged(duplicate);
            Path duplicateCompat = root();
            Files.writeString(duplicateCompat.resolve("mods/gangsbotaniacompat-old.jar"), "preserve");
            expectFailure(() -> BotaniaNoFlightRestart.install(duplicateCompat, payload), "Unexpected mod jars");
            unchanged(duplicateCompat);
            Path changed = root();
            Path changedMod = changed.resolve("mods").resolve(BotaniaNoFlightRestart.MOD);
            Files.writeString(changedMod, "preserve");
            expectFailure(() -> BotaniaNoFlightRestart.install(changed, payload), "unexpected SHA-256");
            require(Files.readString(changedMod).equals("preserve"), "Changed compatibility jar overwritten");
            Path changedBotania = root();
            Files.writeString(changedBotania.resolve("mods").resolve(BotaniaNoFlightRestart.BOTANIA), "preserve");
            expectFailure(() -> BotaniaNoFlightRestart.install(changedBotania, payload), "Botania does not match");
            unchanged(changedBotania);
            Path missingBotania = root();
            Files.delete(missingBotania.resolve("mods").resolve(BotaniaNoFlightRestart.BOTANIA));
            expectFailure(() -> BotaniaNoFlightRestart.install(missingBotania, payload), "Botania does not match");
            unchanged(missingBotania);
            Path locked = root();
            try (FileChannel channel = FileChannel.open(locked.resolve("world/session.lock"), StandardOpenOption.WRITE);
                    var lock = channel.lock()) {
                try {
                    BotaniaNoFlightRestart.install(locked, payload);
                    throw new AssertionError("Running world permitted installation");
                } catch (OverlappingFileLockException expected) {
                    unchanged(locked);
                }
            }
            verifyCombinedPendingUpdate(payload, Path.of(args[2]));
            System.out.println("BotaniaNoFlightRestartTest passed: installs, repeats, tampering, duplicates, lock and pending update.");
        } finally {
            for (Path root : ROOTS) {
                try (var paths = Files.walk(root)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void verifyCombinedPendingUpdate(byte[] payload, Path repo) throws Exception {
        Path root = root();
        Files.copy(repo.resolve("tmp/modcache/chefs-delight-1.0.5-fa-1.20.1.jar"), root.resolve(ChefCompatRestart.CHEF));
        Files.copy(repo.resolve("tmp/modcache/FarmersDelight-1.20.1-2.5.7+refabricated.jar"), root.resolve(ChefCompatRestart.FARMER));
        Files.copy(repo.resolve("artifacts/goldclaim-1.0.72.jar"), root.resolve(GoldClaimMailboxRestart.OLD_72));
        Files.createDirectories(root.resolve("config"));
        Files.createDirectories(root.resolve("DiscordIntegration-Data"));
        Files.writeString(root.resolve("config/Discord-Integration.toml"), "fixture: no token");
        Files.writeString(root.resolve("DiscordIntegration-Data/Messages.toml"), "fixture: chat settings");
        for (int run = 0; run < 2; run++) {
            BotaniaNoFlightRestart.install(root, payload);
            DiscordBridgeRestart.requireConfig(root);
            DiscordBridgeRestart.install(root, DiscordBridgeRestart.payload());
            ChefCompatRestart.installUpdates(root, ChefCompatRestart.payload());
        }
        GoldClaimMailboxRestart.check(root.resolve(GoldClaimMailboxRestart.NEW_73), GoldClaimMailboxRestart.NEW_73_SHA256);
        ChefCompatRestart.check(root.resolve(ChefCompatRestart.COMPAT), ChefCompatRestart.COMPAT_SHA256);
        DiscordBridgeRestart.check(root.resolve("mods").resolve(DiscordBridgeRestart.JAR), DiscordBridgeRestart.SHA256);
        require(Files.readString(root.resolve("config/Discord-Integration.toml")).equals("fixture: no token"),
            "Discord configuration overwritten");
        require(Files.readString(root.resolve("DiscordIntegration-Data/Messages.toml")).equals("fixture: chat settings"),
            "Discord messages overwritten");
    }

    private static Path root() throws Exception {
        Path root = Files.createTempDirectory("botania-no-flight-restart-test-");
        ROOTS.add(root);
        Files.createDirectories(root.resolve("mods"));
        Files.createDirectories(root.resolve("world"));
        Files.writeString(root.resolve("world/session.lock"), "");
        Files.copy(botania, root.resolve("mods").resolve(BotaniaNoFlightRestart.BOTANIA));
        return root;
    }

    private static void unchanged(Path root) {
        require(!Files.exists(root.resolve("mods").resolve(BotaniaNoFlightRestart.MOD)), "Failed preflight installed mod");
    }

    private interface Action { void run() throws Exception; }

    private static void expectFailure(Action action, String message) throws Exception {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            require(expected.getMessage().contains(message), "Wrong failure: " + expected.getMessage());
            return;
        }
        throw new AssertionError("Expected failure: " + message);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
