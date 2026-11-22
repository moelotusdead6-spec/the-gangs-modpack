import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

public final class GobberShopRestartTest {
    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--packaged")) {
            var payload = GobberShopRestart.payloads();
            var sums = GobberShopRestart.checksums();
            require(payload.keySet().equals(GobberShopRestart.EMBEDDED), "Incomplete packaged payload");
            for (var entry : payload.entrySet()) {
                require(GobberShopRestart.hash(entry.getValue()).equals(sums.getProperty(entry.getKey())),
                    "Packaged checksum mismatch: " + entry.getKey());
            }
            System.out.println("Packaged GobberShopRestart payload verified.");
            return;
        }
        Path repo = Path.of(args[0]);
        Path root = Path.of(args[1]);
        Files.createDirectories(root);
        fixture(repo, root);
        Map<String, byte[]> payload = new LinkedHashMap<>();
        payload.put(GobberShopRestart.GOLD, Files.readAllBytes(repo.resolve("artifacts/goldclaim-1.0.79.jar")));
        payload.put(GobberShopRestart.SHOP, Files.readAllBytes(repo.resolve("artifacts/gangshop-1.0.14.jar")));
        payload.put(GobberShopRestart.TAG, Files.readAllBytes(repo.resolve(GobberShopRestart.TAG)));
        Properties sums = new Properties();
        for (var entry : payload.entrySet()) sums.setProperty(entry.getKey(), GobberShopRestart.hash(entry.getValue()));

        String original = Files.readString(root.resolve(GobberShopRestart.CONFIG));
        require(GobberShopRestart.disableUnbreakable(original).equals(original.replace("true", "false")),
            "Gobber config merge changed unrelated settings");
        expectFailure(() -> GobberShopRestart.disableUnbreakable("{}"));
        expectFailure(() -> GobberShopRestart.disableUnbreakable(original + "\n\"unbreakableEndTools\": true\n"));

        Map<String, byte[]> corrupt = new LinkedHashMap<>(payload);
        corrupt.put(GobberShopRestart.TAG, new byte[] {1});
        expectFailure(() -> GobberShopRestart.install(root, corrupt, sums));
        require(Files.exists(root.resolve(GobberShopRestart.GOLD_OLD)), "Corrupt payload touched old mods");
        try (FileChannel channel = FileChannel.open(root.resolve("world/session.lock"), StandardOpenOption.WRITE);
             var lock = channel.lock()) {
            expectFailure(() -> GobberShopRestart.install(root, payload, sums));
        }

        Path duplicate = root.resolve("mods/unexpected.jar");
        try (var jar = new JarOutputStream(Files.newOutputStream(duplicate))) {
            jar.putNextEntry(new JarEntry("fabric.mod.json"));
            jar.write("{\"id\":\"goldclaim\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            jar.closeEntry();
        }
        expectFailure(() -> GobberShopRestart.install(root, payload, sums));
        Files.delete(duplicate);
        GobberShopRestart.install(root, payload, sums);
        require(Files.readString(root.resolve(GobberShopRestart.CONFIG)).equals(original.replace("true", "false")),
            "Config merge failed");
        require(Files.readString(root.resolve(GobberShopRestart.BACKUP).resolve(GobberShopRestart.CONFIG)).equals(original),
            "Original Gobber config was not backed up");
        require(Files.readString(root.resolve(GobberShopRestart.BACKUP).resolve("config/gangshop/catalog-editor.json"))
            .equals("custom shop"), "Shop editor was not backed up");
        require(Files.readString(root.resolve(GobberShopRestart.BACKUP).resolve(GobberShopRestart.WALLET))
            .equals("wallet"), "Wallet was not backed up");
        for (String name : GobberShopRestart.OLD.keySet()) {
            require(!Files.exists(root.resolve(name)), "Old mod remains");
            GobberShopRestart.check(root.resolve(GobberShopRestart.BACKUP).resolve(name), GobberShopRestart.OLD.get(name));
        }
        GobberShopRestart.install(root, payload, sums);
        Files.delete(root.resolve(GobberShopRestart.BACKUP).resolve("APPLIED.txt"));
        GobberShopRestart.install(root, payload, sums);
        require(Files.readString(root.resolve(GobberShopRestart.BACKUP).resolve(GobberShopRestart.CONFIG)).equals(original),
            "Interrupted install overwrote backup");
        Files.writeString(root.resolve(GobberShopRestart.CONFIG), original);
        expectFailure(() -> GobberShopRestart.install(root, payload, sums));
        System.out.println("GobberShopRestart tests passed.");
    }

    static void fixture(Path repo, Path root) throws Exception {
        copy(repo.resolve("artifacts/gangs-shop-gui-restart-1.0.13.jar"), root.resolve("gangs-shop-gui-restart-1.0.13.jar"));
        copy(repo.resolve("artifacts/gang-shop-ozsoihjx.jar"), root.resolve("mods/gang-shop-ozsoihjx.jar"));
        copy(repo.resolve("artifacts/goldclaim-1.0.78.jar"), root.resolve(GobberShopRestart.GOLD_OLD));
        copy(repo.resolve("artifacts/gangshop-1.0.13.jar"), root.resolve(GobberShopRestart.SHOP_OLD));
        write(root.resolve("world/session.lock"), "");
        write(root.resolve(GobberShopRestart.WALLET), "wallet");
        write(root.resolve("config/gangshop/catalog-editor.json"), "custom shop");
        write(root.resolve(GobberShopRestart.CONFIG),
            "{\n  // Preserve custom settings.\n  \"custom\": 42,\n  \"unbreakableEndTools\": true,\n}\n");
        write(root.resolve("backups/wild-boss-goldclaim-1.0.78/APPLIED.txt"),
            "GoldClaim 1.0.78 kit countdowns, Cosmetic Keys and /wild boss rules installed.\n");
        write(root.resolve("backups/shop-gui-1.0.13/APPLIED.txt"), "Gang Shop GUI 1.0.13 installed.\n");
        write(root.resolve("backups/shop-gui-1.0.13/BACKED_UP.txt"), "Shop configuration, wallet and previous mod backed up.\n");
    }

    static void copy(Path source, Path target) throws Exception {
        Files.createDirectories(target.getParent());
        Files.copy(source, target);
    }

    static void write(Path path, String content) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }

    static void expectFailure(Checked action) throws Exception {
        try {
            action.run();
        } catch (Exception expected) {
            return;
        }
        throw new IllegalStateException("Expected operation to fail");
    }

    interface Checked {
        void run() throws Exception;
    }

    static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
