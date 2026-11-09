import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ChefCompatRestartTest {
    private static final List<Path> ROOTS = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        byte[] payload = Files.readAllBytes(Path.of(args[0]));
        Path chef = Path.of(args[1]);
        Path farmer = Path.of(args[2]);
        Path goldclaim = Path.of(args[3]);
        try {
            installsAndRepeats(payload, chef, farmer);
            installsCombinedUpdate(payload, chef, farmer, goldclaim);
            rejectsChangedGoldClaim(payload, chef, farmer);
            rejectsCorruptPayload(payload, chef, farmer);
            rejectsChangedDependency(payload, chef, farmer, ChefCompatRestart.CHEF);
            rejectsChangedDependency(payload, chef, farmer, ChefCompatRestart.FARMER);
            rejectsExtraJar(payload, chef, farmer);
            rejectsChangedInstalledJar(payload, chef, farmer);
            rejectsRunningWorld(payload, chef, farmer);
            System.out.println("ChefCompatRestartTest passed");
        } finally {
            for (Path root : ROOTS) {
                try (var paths = Files.walk(root)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void installsAndRepeats(byte[] payload, Path chef, Path farmer) throws Exception {
        Path root = root(chef, farmer);
        ChefCompatRestart.install(root, payload);
        ChefCompatRestart.check(root.resolve(ChefCompatRestart.COMPAT), ChefCompatRestart.COMPAT_SHA256);
        ChefCompatRestart.install(root, payload);
        ChefCompatRestart.check(root.resolve(ChefCompatRestart.CHEF), ChefCompatRestart.CHEF_SHA256);
        ChefCompatRestart.check(root.resolve(ChefCompatRestart.FARMER), ChefCompatRestart.FARMER_SHA256);
    }

    private static void installsCombinedUpdate(byte[] payload, Path chef, Path farmer, Path goldclaim) throws Exception {
        Path root = root(chef, farmer);
        Files.copy(goldclaim, root.resolve(GoldClaimMailboxRestart.OLD_72));
        ChefCompatRestart.installUpdates(root, payload);
        ChefCompatRestart.installUpdates(root, payload);
        ChefCompatRestart.check(root.resolve(ChefCompatRestart.COMPAT), ChefCompatRestart.COMPAT_SHA256);
        GoldClaimMailboxRestart.check(root.resolve(GoldClaimMailboxRestart.NEW_73),
                GoldClaimMailboxRestart.NEW_73_SHA256);
        GoldClaimMailboxRestart.check(root.resolve(GoldClaimMailboxRestart.BACKUP)
                .resolve(GoldClaimMailboxRestart.OLD_72), GoldClaimMailboxRestart.OLD_72_SHA256);
        require(!Files.exists(root.resolve(GoldClaimMailboxRestart.OLD_72)), "old GoldClaim was not replaced");
    }

    private static void rejectsChangedGoldClaim(byte[] payload, Path chef, Path farmer) throws Exception {
        Path root = root(chef, farmer);
        Files.writeString(root.resolve(GoldClaimMailboxRestart.OLD_72), "unexpected");
        expectFailure(() -> ChefCompatRestart.installUpdates(root, payload), "unexpected SHA-256");
        require(!Files.exists(root.resolve(GoldClaimMailboxRestart.NEW_73)), "changed GoldClaim allowed install");
    }

    private static void rejectsCorruptPayload(byte[] payload, Path chef, Path farmer) throws Exception {
        Path root = root(chef, farmer);
        byte[] corrupt = payload.clone();
        corrupt[0] ^= 1;
        expectFailure(() -> ChefCompatRestart.install(root, corrupt), "Corrupt Chef compatibility payload");
        require(!Files.exists(root.resolve(ChefCompatRestart.COMPAT)), "corrupt payload changed mods");
    }

    private static void rejectsChangedDependency(byte[] payload, Path chef, Path farmer, Path changed) throws Exception {
        Path root = root(chef, farmer);
        Files.writeString(root.resolve(changed), "unexpected");
        expectFailure(() -> ChefCompatRestart.install(root, payload), "unexpected SHA-256");
        require(!Files.exists(root.resolve(ChefCompatRestart.COMPAT)), "changed dependency allowed install");
    }

    private static void rejectsExtraJar(byte[] payload, Path chef, Path farmer) throws Exception {
        Path root = root(chef, farmer);
        Path extra = root.resolve(Path.of("mods", "gangschefcompat-older.jar"));
        Files.writeString(extra, "unexpected");
        expectFailure(() -> ChefCompatRestart.install(root, payload), "Unexpected mod jars");
        require(Files.exists(extra) && !Files.exists(root.resolve(ChefCompatRestart.COMPAT)),
                "unexpected jar was removed or allowed install");
    }

    private static void rejectsChangedInstalledJar(byte[] payload, Path chef, Path farmer) throws Exception {
        Path root = root(chef, farmer);
        Path installed = root.resolve(ChefCompatRestart.COMPAT);
        Files.writeString(installed, "unexpected");
        expectFailure(() -> ChefCompatRestart.install(root, payload), "unexpected SHA-256");
        require(Files.readString(installed).equals("unexpected"), "changed jar was overwritten");
    }

    private static void rejectsRunningWorld(byte[] payload, Path chef, Path farmer) throws Exception {
        Path root = root(chef, farmer);
        try (FileChannel channel = FileChannel.open(root.resolve(Path.of("world", "session.lock")),
                StandardOpenOption.WRITE); var lock = channel.lock()) {
            try {
                ChefCompatRestart.install(root, payload);
                throw new AssertionError("Running world allowed install");
            } catch (OverlappingFileLockException expected) {
                require(!Files.exists(root.resolve(ChefCompatRestart.COMPAT)), "locked world changed mods");
            }
        }
    }

    private static Path root(Path chef, Path farmer) throws Exception {
        Path root = Files.createTempDirectory("chefcompat-restart-test-");
        ROOTS.add(root);
        Files.createDirectories(root.resolve("mods"));
        Files.createDirectories(root.resolve("world"));
        Files.writeString(root.resolve(Path.of("world", "session.lock")), "");
        Files.copy(chef, root.resolve(ChefCompatRestart.CHEF));
        Files.copy(farmer, root.resolve(ChefCompatRestart.FARMER));
        return root;
    }

    private interface Action { void run() throws Exception; }

    private static void expectFailure(Action action, String message) throws Exception {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            require(expected.getMessage().contains(message), "Unexpected error: " + expected.getMessage());
            return;
        }
        throw new AssertionError("Expected failure: " + message);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
