import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

public final class CommandsRestartTest {
    public static void main(String[] args) throws Exception {
        Path repo = Path.of(args[0]);
        Path root = Path.of(args[1]);
        Files.createDirectories(root);
        GobberShopRestartTest.fixture(repo, root);
        GobberShopRestartTest.copy(repo.resolve("artifacts").resolve(CommandsRestart.PREDECESSOR),
            root.resolve(CommandsRestart.PREDECESSOR));
        GobberShopRestartTest.copy(repo.resolve("artifacts").resolve(GangGemsRestart.PREDECESSOR),
            root.resolve(GangGemsRestart.PREDECESSOR));
        GobberShopRestartTest.copy(Path.of(args[2]), root.resolve("fabric-server-launch.jar"));
        GobberShopRestartTest.copy(Path.of(args[3]), root.resolve(GangGemsRestart.FURNITURE));
        GobberShopRestartTest.copy(Path.of(args[4]), root.resolve(GangGemsRestart.DISCORD));
        GobberShopRestart.install(root, GobberShopRestart.payloads(), GobberShopRestart.checksums());
        GobberShopRestartTest.copy(repo.resolve("sources/gangsgems/build/libs/gangsgems-0.1.1.jar"),
            root.resolve(GangGemsUpgrade.OLD));
        GobberShopRestartTest.write(root.resolve(GangGemsRestart.MARKER), GangGemsRestart.APPLIED);
        GangGemsUpgrade.install(root, GangGemsUpgrade.resource("gangsgems-0.1.2.jar"),
            new String(GangGemsUpgrade.resource("sha256.txt"), StandardCharsets.US_ASCII).trim());
        GobberShopRestartTest.copy(repo.resolve("artifacts/gangsmounts-1.0.2.jar"),
            root.resolve("mods/gangsmounts-1.0.2.jar"));
        Path config = root.resolve("config/gangsgems-discord.json");
        Path wallet = root.resolve("world/gangsgems/wallet.json");
        GobberShopRestartTest.write(config, "existing config");
        GobberShopRestartTest.write(wallet, "existing wallet");
        var payload = CommandsRestart.payloads();
        var sums = CommandsRestart.checksums();

        Map<String, byte[]> corrupt = new LinkedHashMap<>(payload);
        corrupt.put("goldclaim-1.0.80.jar", new byte[]{1});
        GobberShopRestartTest.expectFailure(() -> CommandsRestart.install(root, corrupt, sums));
        try (FileChannel channel = FileChannel.open(root.resolve("world/session.lock"), StandardOpenOption.WRITE);
             var lock = channel.lock()) {
            GobberShopRestartTest.expectFailure(() -> CommandsRestart.install(root, payload, sums));
        }
        Path predecessorMarker = root.resolve(GangGemsUpgrade.BACKUP).resolve("APPLIED.txt");
        Files.writeString(predecessorMarker, "unexpected");
        GobberShopRestartTest.expectFailure(() -> CommandsRestart.install(root, payload, sums));
        Files.writeString(predecessorMarker, GangGemsUpgrade.APPLIED);
        Path duplicate = root.resolve("mods/unexpected-commands.jar");
        try (var jar = new JarOutputStream(Files.newOutputStream(duplicate))) {
            jar.putNextEntry(new JarEntry("fabric.mod.json"));
            jar.write("{\"id\":\"gangsmounts\"}".getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
        }
        GobberShopRestartTest.expectFailure(() -> CommandsRestart.install(root, payload, sums));
        Files.delete(duplicate);
        Path oldMount = root.resolve("mods/gangsmounts-1.0.2.jar");
        byte[] oldBytes = Files.readAllBytes(oldMount);
        Files.writeString(oldMount, "corrupt");
        GobberShopRestartTest.expectFailure(() -> CommandsRestart.install(root, payload, sums));
        Files.write(oldMount, oldBytes);
        GobberShopRestartTest.require(!Files.exists(root.resolve(CommandsRestart.BACKUP)),
            "Rejected preflight changed installed mods or backups");

        CommandsRestart.install(root, payload, sums);
        CommandsRestart.install(root, payload, sums);
        verify(root, sums);
        GobberShopRestartTest.require(Files.readString(config).equals("existing config")
            && Files.readString(wallet).equals("existing wallet"), "Wallet or configuration changed");
        Path marker = root.resolve(CommandsRestart.BACKUP).resolve("APPLIED.txt");
        Files.delete(marker);
        CommandsRestart.install(root, payload, sums);
        verify(root, sums);
        Files.delete(marker);
        Path newMount = root.resolve("mods/gangsmounts-1.0.3.jar");
        Files.delete(newMount);
        CommandsRestart.install(root, payload, sums);
        verify(root, sums);
        Files.writeString(marker, "unexpected");
        GobberShopRestartTest.expectFailure(() -> CommandsRestart.install(root, payload, sums));
        Files.writeString(marker, CommandsRestart.APPLIED);
        Files.writeString(newMount, "corrupt");
        GobberShopRestartTest.expectFailure(() -> CommandsRestart.install(root, payload, sums));
        GobberShopRestartTest.require(Files.readString(wallet).equals("existing wallet"), "Rejected retry changed wallet");

        Path packaged = Path.of(CommandsRestart.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        if (Files.isRegularFile(packaged)) {
            try (var jar = new java.util.jar.JarFile(packaged.toFile())) {
                String actual = jar.getManifest().getMainAttributes().getValue("Class-Path");
                String expected = "fabric-server-launch.jar " + CommandsRestart.PREDECESSOR + " " + GangGemsRestart.PREDECESSOR;
                GobberShopRestartTest.require(expected.equals(actual), "Incorrect packaged launcher class path");
            }
        }
        System.out.println("CommandsRestart tests passed.");
    }

    private static void verify(Path root, java.util.Properties sums) throws Exception {
        GobberShopRestart.marker(root.resolve(CommandsRestart.BACKUP).resolve("APPLIED.txt"), CommandsRestart.APPLIED);
        for (var entry : CommandsRestart.OLD.entrySet()) {
            GobberShopRestart.check(root.resolve(CommandsRestart.BACKUP).resolve(entry.getKey()), entry.getValue());
            GobberShopRestartTest.require(!Files.exists(root.resolve("mods").resolve(entry.getKey())), "Old mod remains");
        }
        for (String name : CommandsRestart.NEW) {
            GobberShopRestart.check(root.resolve("mods").resolve(name), sums.getProperty(name));
        }
    }
}
