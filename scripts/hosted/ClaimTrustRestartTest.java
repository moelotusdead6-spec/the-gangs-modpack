import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

/** Regression tests for ClaimTrustRestart. Usage: java ClaimTrustRestartTest <live-fixture-dir> */
public final class ClaimTrustRestartTest {
    static Path fixtures;

    public static void main(String[] args) throws Exception {
        fixtures = Path.of(args[0]);
        freshInstallMatchesLiveLayout();
        secondRunIsNoOp();
        interruptedRunResumes();
        adminShopEditsArePreserved();
        refusesUnexpectedGoldClaim();
        refusesChangedNeonTag();
        refusesRunningWorld();
        mergeHandlesLfAndEmptyObject();
        System.out.println("ClaimTrustRestartTest passed");
    }

    static Path liveRoot() throws Exception {
        Path root = Files.createTempDirectory("claim-trust-restart");
        copy("goldclaim-1.0.63.live.jar", root.resolve(ClaimTrustRestart.OLD_GOLDCLAIM));
        copy("neon-v8.live.jar", root.resolve(ClaimTrustRestart.OLD_NEON));
        copy("neon_signs.live.json", root.resolve(ClaimTrustRestart.NEON_TAG));
        copy("overrides.live.json", root.resolve(ClaimTrustRestart.OVERRIDES));
        copy("fabric-server-launch.live.jar", root.resolve("fabric-server-launch.jar"));
        Files.writeString(root.resolve("mods/goldclaim-1.0.56.jar.disabled-before-1.0.57"), "old");
        Files.writeString(root.resolve("mods/other-mod.jar"), "other");
        Files.createDirectories(root.resolve("world"));
        Files.writeString(root.resolve("world/session.lock"), "lock");
        return root;
    }

    static void copy(String fixture, Path target) throws Exception {
        Files.createDirectories(target.getParent());
        Files.copy(fixtures.resolve(fixture), target);
    }

    static void freshInstallMatchesLiveLayout() throws Exception {
        Path root = liveRoot();
        String before = Files.readString(root.resolve(ClaimTrustRestart.OVERRIDES));
        ClaimTrustRestart.install(root);
        verify(root);
        String after = Files.readString(root.resolve(ClaimTrustRestart.OVERRIDES));
        List<String> deny = ClaimTrustRestart.denyEntries();
        expect(deny.size() == 97, "deny list has 97 Neon entries");
        expect(!after.replace("\r\n", "").contains("\n"), "CRLF line endings preserved");
        expect(after.length() > before.length(), "deny entries added");
        int added = 0;
        for (String key : deny) if (!before.contains("\"" + key + "\"")) added++;
        expect(added == 32, "32 missing live deny entries added, was " + added);
        String[] beforeLines = before.split("\r\n");
        for (String line : beforeLines) {
            if (!line.trim().isEmpty() && !line.trim().equals("}") && !line.trim().endsWith("true")) {
                expect(after.contains(line), "unchanged line preserved: " + line);
            }
        }
        expect(after.contains("\"itemSellOverrides\""), "sell overrides kept");
        expect(!after.contains("neon-wall-wonders-gwbzt3mk:neon_arcade_alien\": 1"), "no Neon prices added");
        expect(Files.readString(root.resolve(ClaimTrustRestart.BACKUP).resolve(ClaimTrustRestart.OVERRIDES))
            .equals(before), "overrides backup is the original");
        expect(Files.exists(root.resolve("mods/goldclaim-1.0.56.jar.disabled-before-1.0.57")), "disabled files untouched");
        delete(root);
    }

    static void secondRunIsNoOp() throws Exception {
        Path root = liveRoot();
        ClaimTrustRestart.install(root);
        String overrides = ClaimTrustRestart.hash(root.resolve(ClaimTrustRestart.OVERRIDES));
        ClaimTrustRestart.install(root);
        expect(overrides.equals(ClaimTrustRestart.hash(root.resolve(ClaimTrustRestart.OVERRIDES))), "rerun changes nothing");
        verify(root);
        delete(root);
    }

    static void interruptedRunResumes() throws Exception {
        Path root = liveRoot();
        ClaimTrustRestart.install(root);
        Files.delete(root.resolve(ClaimTrustRestart.BACKUP).resolve("APPLIED.txt"));
        ClaimTrustRestart.install(root);
        verify(root);
        delete(root);

        root = liveRoot();
        Path backup = root.resolve(ClaimTrustRestart.BACKUP);
        Files.createDirectories(backup.resolve("mods"));
        Files.copy(root.resolve(ClaimTrustRestart.OLD_GOLDCLAIM), backup.resolve(ClaimTrustRestart.OLD_GOLDCLAIM));
        Files.delete(root.resolve(ClaimTrustRestart.OLD_GOLDCLAIM));
        try (var stream = ClaimTrustRestart.payload(ClaimTrustRestart.NEW_GOLDCLAIM)) {
            Files.write(root.resolve(ClaimTrustRestart.NEW_GOLDCLAIM), stream.readAllBytes());
        }
        ClaimTrustRestart.install(root);
        verify(root);
        delete(root);
    }

    static void adminShopEditsArePreserved() throws Exception {
        Path root = liveRoot();
        Path overrides = root.resolve(ClaimTrustRestart.OVERRIDES);
        String edited = Files.readString(overrides).replace("\"version\": 1", "\"version\": 1");
        edited = edited.replaceFirst("\"moddedBlocksSellMultiplier\": 1\\.0", "\"moddedBlocksSellMultiplier\": 0.75");
        expect(edited.contains("0.75"), "fixture edit applied");
        Files.writeString(overrides, edited);
        ClaimTrustRestart.install(root);
        expect(Files.readString(overrides).contains("\"moddedBlocksSellMultiplier\": 0.75"), "admin edit kept");
        verify(root);
        delete(root);
    }

    static void refusesUnexpectedGoldClaim() throws Exception {
        Path root = liveRoot();
        Files.writeString(root.resolve("mods/goldclaim-1.0.62.jar"), "stray");
        expectRefusal(root, "Unexpected active mod");
        delete(root);
    }

    static void refusesChangedNeonTag() throws Exception {
        Path root = liveRoot();
        Files.writeString(root.resolve(ClaimTrustRestart.NEON_TAG), "{\"replace\":false,\"values\":[]}");
        expectRefusal(root, "File changed since staging");
        delete(root);
    }

    static void refusesRunningWorld() throws Exception {
        Path root = liveRoot();
        try (FileChannel channel = FileChannel.open(root.resolve("world/session.lock"), StandardOpenOption.WRITE);
             var lock = channel.lock()) {
            boolean refused = false;
            try {
                ClaimTrustRestart.install(root);
            } catch (Exception | Error expected) {
                refused = true;
            }
            expect(refused, "locked world refused");
        }
        expect(Files.exists(root.resolve(ClaimTrustRestart.OLD_GOLDCLAIM)), "running world untouched");
        expect(!Files.exists(root.resolve(ClaimTrustRestart.BACKUP)), "no backup for running world");
        delete(root);
    }

    static void mergeHandlesLfAndEmptyObject() {
        String lf = "{\n  \"denyItems\": {\n    \"a:b\": true\n  },\n  \"version\": 1\n}\n";
        String merged = ClaimTrustRestart.addDenyEntries(lf, List.of("a:b", "c:d"));
        expect(merged.equals("{\n  \"denyItems\": {\n    \"a:b\": true,\n    \"c:d\": true\n  },\n  \"version\": 1\n}\n"),
            "LF merge: " + merged);
        String empty = "{\"denyItems\": {}}";
        expect(ClaimTrustRestart.addDenyEntries(empty, List.of("c:d")).equals("{\"denyItems\": {\n  \"c:d\": true\n}}"),
            "empty merge");
        boolean refused = false;
        try {
            ClaimTrustRestart.addDenyEntries("{\"denyItems\": {\"a\": true,}}", List.of("c:d"));
        } catch (IllegalStateException expected) {
            refused = true;
        }
        expect(refused, "malformed JSON refused");
    }

    static void expectRefusal(Path root, String message) throws Exception {
        String overrides = ClaimTrustRestart.hash(root.resolve(ClaimTrustRestart.OVERRIDES));
        try {
            ClaimTrustRestart.install(root);
        } catch (IllegalStateException expected) {
            expect(expected.getMessage().contains(message), "refusal message: " + expected.getMessage());
            expect(Files.exists(root.resolve(ClaimTrustRestart.OLD_GOLDCLAIM)), "old GoldClaim kept");
            expect(overrides.equals(ClaimTrustRestart.hash(root.resolve(ClaimTrustRestart.OVERRIDES))), "overrides kept");
            return;
        }
        throw new AssertionError("Expected refusal: " + message);
    }

    static void verify(Path root) throws Exception {
        Properties next = ClaimTrustRestart.properties("files.properties");
        ClaimTrustRestart.check(root.resolve(ClaimTrustRestart.NEW_GOLDCLAIM), next.getProperty(ClaimTrustRestart.NEW_GOLDCLAIM));
        ClaimTrustRestart.check(root.resolve(ClaimTrustRestart.NEW_NEON), next.getProperty(ClaimTrustRestart.NEW_NEON));
        ClaimTrustRestart.check(root.resolve(ClaimTrustRestart.NEON_TAG), next.getProperty(ClaimTrustRestart.NEON_TAG));
        expect(!Files.exists(root.resolve(ClaimTrustRestart.OLD_GOLDCLAIM)), "old GoldClaim removed");
        expect(!Files.exists(root.resolve(ClaimTrustRestart.OLD_NEON)), "old Neon removed");
        expect(Files.exists(root.resolve(ClaimTrustRestart.BACKUP).resolve("APPLIED.txt")), "marker written");
        String tag = Files.readString(root.resolve(ClaimTrustRestart.NEON_TAG), StandardCharsets.UTF_8);
        expect(tag.split("neon-wall-wonders-gwbzt3mk:").length - 1 == 87, "87 Neon tag entries");
        try (var files = Files.list(root.resolve("mods"))) {
            long goldclaims = files.filter(p -> p.getFileName().toString().matches("goldclaim-.*\\.jar")).count();
            expect(goldclaims == 1, "exactly one GoldClaim jar");
        }
    }

    static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static void delete(Path root) throws Exception {
        try (var files = Files.walk(root)) {
            for (Path path : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }
}
