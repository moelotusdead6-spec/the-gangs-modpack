import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

public final class NeonLootRestartTest {
    private static Path repo;
    private static Path tests;

    public static void main(String[] args) throws Exception {
        repo = Path.of(args[0]);
        tests = Files.createTempDirectory("neon-loot-restart-test-");
        try {
            Path normal = fixture("normal");
            NeonLootRestart.install(normal);
            verify(normal);
            NeonLootRestart.install(normal);
            verify(normal);

            Path changedMonthly = fixture("changed-monthly");
            Path monthlyFile = changedMonthly.resolve(NeonLootRestart.MONTHLY);
            String originalMonthly = Files.readString(monthlyFile);
            int poolsOpen = NeonLootRestart.findTopLevelArray(originalMonthly, "pools");
            int poolsClose = NeonLootRestart.matchingEnd(originalMonthly, poolsOpen);
            String customPool = ",\n    {\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\","
                + "\"name\":\"minecraft:diamond\"}]}\n  ";
            String customized = originalMonthly.substring(0, poolsClose).stripTrailing()
                + customPool + originalMonthly.substring(poolsClose);
            Files.writeString(monthlyFile, customized);
            String customizedHash = NeonLootRestart.hash(monthlyFile);
            NeonLootRestart.install(changedMonthly);
            verify(changedMonthly);
            require(NeonLootRestart.hash(changedMonthly.resolve(NeonLootRestart.BACKUP)
                    .resolve(NeonLootRestart.MONTHLY)).equals(customizedHash),
                "Changed monthly table was not backed up exactly");
            String installedMonthly = Files.readString(monthlyFile, StandardCharsets.UTF_8);
            require(installedMonthly.contains("minecraft:diamond"),
                "Custom monthly loot pool was not preserved");
            require(installedMonthly.contains("\"gangs:neon_signs\""),
                "Neon monthly loot pool was not merged");
            NeonLootRestart.install(changedMonthly);
            verify(changedMonthly);

            Path bom = fixture("bom-monthly");
            Path bomMonthly = bom.resolve(NeonLootRestart.MONTHLY);
            byte[] bomOriginal = Files.readAllBytes(bomMonthly);
            byte[] withBom = new byte[bomOriginal.length + 3];
            withBom[0] = (byte)0xEF; withBom[1] = (byte)0xBB; withBom[2] = (byte)0xBF;
            System.arraycopy(bomOriginal, 0, withBom, 3, bomOriginal.length);
            Files.write(bomMonthly, withBom);
            NeonLootRestart.install(bom);
            verify(bom);
            byte[] bomInstalled = Files.readAllBytes(bomMonthly);
            require(bomInstalled[0] == (byte)0xEF && bomInstalled[1] == (byte)0xBB && bomInstalled[2] == (byte)0xBF,
                "BOM monthly table lost its BOM");
            require(Files.readString(bomMonthly).contains("\"gangs:neon_signs\""),
                "Neon pool was not merged into BOM monthly table");

            String live = System.getenv("NEON_LIVE_MONTHLY");
            if (live != null) {
                Path liveRoot = fixture("live-monthly");
                Files.copy(Path.of(live), liveRoot.resolve(NeonLootRestart.MONTHLY),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                NeonLootRestart.install(liveRoot);
                verify(liveRoot);
                Files.copy(liveRoot.resolve(NeonLootRestart.MONTHLY), Path.of(live + ".merged"),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            Path malformed = fixture("malformed-monthly");
            Path malformedMonthly = malformed.resolve(NeonLootRestart.MONTHLY);
            String malformedContents = "{\"pools\":[{\"rolls\":1,\"entries\":[],}],\"functions\":{}}";
            Files.writeString(malformedMonthly, malformedContents);
            reject(malformed);
            require(Files.readString(malformedMonthly).equals(malformedContents),
                "Malformed monthly table was changed before refusal");

            Path locked = fixture("locked");
            try (FileChannel channel = FileChannel.open(locked.resolve("world/session.lock"),
                    StandardOpenOption.WRITE);
                 var lock = channel.lock()) {
                reject(locked);
            }

            String changedFile = NeonLootRestart.LOOT_FILES.get(0);
            Path changed = fixture("changed");
            Files.writeString(changed.resolve(changedFile), "unexpected");
            reject(changed);

            Path duplicate = fixture("duplicate");
            Files.writeString(duplicate.resolve("mods/goldclaim-1.0.64.jar"), "duplicate");
            reject(duplicate);

            System.out.println("PASS: verified upgrade, exact backups, changed monthly merge, idempotency, "
                + "malformed monthly rejection, world-lock rejection, changed weekly-file rejection, "
                + "and duplicate GoldClaim rejection.");
        } finally {
            try (var paths = Files.walk(tests)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private static Path fixture(String name) throws Exception {
        Path root = tests.resolve(name);
        Files.createDirectories(root.resolve("mods"));
        Files.createDirectories(root.resolve("world"));
        Files.writeString(root.resolve("world/session.lock"), "");
        Files.copy(repo.resolve("tmp/worldgen-compat-prod-tests-20261028/fabric-server-launch.jar"),
            root.resolve("fabric-server-launch.jar"));
        Files.copy(repo.resolve("artifacts/goldclaim-1.0.62.jar"), root.resolve(NeonLootRestart.OLD_MOD));
        Properties previous = NeonLootRestart.properties("previous.properties");
        for (String file : NeonLootRestart.LOOT_FILES) {
            if (previous.getProperty(file).equals("ABSENT")) continue;
            Path source = root.resolve(file);
            Files.createDirectories(source.getParent());
            try (var stream = NeonLootRestart.payload("original/" + file)) {
                Files.copy(stream, source);
            }
        }
        return root;
    }

    private static void verify(Path root) throws Exception {
        Properties previous = NeonLootRestart.properties("previous.properties");
        Properties next = NeonLootRestart.properties("files.properties");
        Properties applied = new Properties();
        try (var reader = Files.newBufferedReader(root.resolve(NeonLootRestart.BACKUP.resolve("APPLIED.txt")),
                StandardCharsets.UTF_8)) {
            applied.load(reader);
        }
        for (String file : previous.stringPropertyNames()) {
            if (previous.getProperty(file).equals("ABSENT")) {
                require(!Files.exists(root.resolve(NeonLootRestart.BACKUP.resolve(file))),
                    "Previously absent file was backed up: " + file);
                continue;
            }
            String expectedBackup = file.equals(NeonLootRestart.MONTHLY)
                ? applied.getProperty("monthly_backup_sha256", previous.getProperty(file))
                : previous.getProperty(file);
            require(NeonLootRestart.hash(root.resolve(NeonLootRestart.BACKUP.resolve(file)))
                    .equals(expectedBackup), "Original backup mismatch: " + file);
        }
        for (String file : next.stringPropertyNames()) {
            String expected = file.equals(NeonLootRestart.MONTHLY)
                ? applied.getProperty("monthly_sha256", next.getProperty(file))
                : next.getProperty(file);
            require(NeonLootRestart.hash(root.resolve(file)).equals(expected),
                "Installed hash mismatch: " + file);
        }
        require(!Files.exists(root.resolve(NeonLootRestart.OLD_MOD)), "Old GoldClaim jar remains active");
        require(Files.readString(root.resolve(NeonLootRestart.BACKUP.resolve("APPLIED.txt")))
            .startsWith(NeonLootRestart.APPLIED), "Applied marker is incorrect");
    }

    private static void reject(Path root) throws Exception {
        boolean rejected = false;
        try {
            NeonLootRestart.install(root);
        } catch (Exception expected) {
            rejected = true;
        }
        require(rejected, "Unsafe update accepted");
        require(!Files.exists(root.resolve(NeonLootRestart.BACKUP)), "Rejected update wrote backups");
        require(!Files.exists(root.resolve(NeonLootRestart.NEW_MOD)), "Rejected update installed GoldClaim");
        for (String file : NeonLootRestart.LOOT_FILES) {
            require(!Files.exists(root.resolve(file + ".neon-pending")),
                "Rejected update left a pending file: " + file);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
