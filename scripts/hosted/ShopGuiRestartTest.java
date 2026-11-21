import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.jar.JarFile;

public final class ShopGuiRestartTest {
    public static void main(String[] args) throws Exception {
        if (args[0].equals("--packaged")) {
            try (JarFile jar = new JarFile(args[1])) {
                require(jar.getManifest().getMainAttributes().getValue("Class-Path").equals(
                    "fabric-server-launch.jar " + ShopGuiRestart.PREDECESSOR), "Invalid manifest class path");
                for (String name : ShopGuiRestart.EMBEDDED) {
                    var entry = jar.getJarEntry("shop-gui-1.0.13-payload/" + name);
                    require(entry != null, "Missing payload " + name);
                    try (var input = jar.getInputStream(entry)) {
                        require(WildBossRestart.hash(input.readAllBytes()).equals(ShopGuiRestart.checksums().getProperty(name)),
                            "Corrupt packaged payload " + name);
                    }
                }
                require(jar.getJarEntry("shop-gui-1.0.13-payload/" + ShopGuiRestart.GUI) == null, "GUI was repackaged");
            }
            try (var loader = new java.net.URLClassLoader(
                new java.net.URL[]{Path.of(args[1]).toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
                for (String name : new String[]{"ShopGuiRestart", "WildBossRestart", "TrialsLightnessRestart"}) {
                    var type = Class.forName(name, true, loader);
                    var checksums = type.getDeclaredMethod("checksums");
                    checksums.setAccessible(true);
                    require(!((java.util.Properties) checksums.invoke(null)).isEmpty(), "Missing or shadowed resources: " + name);
                }
            }
            System.out.println("PASS: packaged manifest and payload.");
            return;
        }
        Path repo = Path.of(args[0]);
        Path root = WildBossRestartTest.trialsHost(repo, Path.of(args[1]));
        Files.copy(repo.resolve("artifacts").resolve(ShopGuiRestart.PREDECESSOR), root.resolve(ShopGuiRestart.PREDECESSOR));
        Files.copy(repo.resolve("artifacts").resolve(ShopGuiRestart.GUI), root.resolve(ShopGuiRestart.GUI));
        WildBossRestart.check(root.resolve(ShopGuiRestart.OLD), ShopGuiRestart.OLD_HASH);
        write(root.resolve("world/gangshop_wallets.json"), "{\"wallets\":{\"example\":100}}\n");
        write(root.resolve("config/gangshop/prices.json"), "{\"custom\":true}\n");
        write(root.resolve("config/gangshop/access.json"), "{\"old\":true}\n");
        write(root.resolve(WildBossRestart.MENU), "{\"custom\":true}\n");

        var payload = ShopGuiRestart.payloads();
        var sums = ShopGuiRestart.checksums();
        var corrupt = new LinkedHashMap<>(payload);
        corrupt.put(ShopGuiRestart.MOD, new byte[]{1});
        reject(root, corrupt, sums);
        Files.copy(root.resolve(ShopGuiRestart.OLD), root.resolve("mods/unexpected-shop.jar"));
        reject(root, payload, sums);
        Files.delete(root.resolve("mods/unexpected-shop.jar"));
        try (var channel = FileChannel.open(root.resolve("world/session.lock"), StandardOpenOption.WRITE);
             var lock = channel.lock()) {
            reject(root, payload, sums);
        }
        WildBossRestart.prepare(root, WildBossRestart.payloads(), WildBossRestart.checksums());
        ShopGuiRestart.install(root, payload, sums);
        require(Files.readString(root.resolve(ShopGuiRestart.BACKUP).resolve("world/gangshop_wallets.json"))
            .equals("{\"wallets\":{\"example\":100}}\n"), "Wallet backup mismatch");
        require(Files.readString(root.resolve(ShopGuiRestart.BACKUP).resolve("config/gangshop/access.json"))
            .equals("{\"old\":true}\n"), "Access backup mismatch");
        require(Files.readString(root.resolve("config/gangshop/prices.json")).equals("{\"custom\":true}\n"), "Prices overwritten");
        require(!Files.exists(root.resolve(ShopGuiRestart.OLD)), "Previous mod remains");
        WildBossRestart.check(root.resolve(ShopGuiRestart.MOD), sums.getProperty(ShopGuiRestart.MOD));
        WildBossRestart.check(root.resolve(ShopGuiRestart.GUI_TARGET), ShopGuiRestart.GUI_HASH);
        WildBossRestart.check(root.resolve(WildBossRestart.GOLDCLAIM), WildBossRestart.checksums().getProperty(WildBossRestart.GOLDCLAIM));

        write(root.resolve("config/gangshop/access.json"), "{\"changedAfterInstall\":true}\n");
        ShopGuiRestart.install(root, payload, sums);
        require(Files.readString(root.resolve("config/gangshop/access.json")).equals("{\"changedAfterInstall\":true}\n"),
            "Repeated startup overwrote access settings");
        Files.delete(root.resolve(ShopGuiRestart.BACKUP).resolve("APPLIED.txt"));
        ShopGuiRestart.install(root, payload, sums);
        require(Files.readString(root.resolve(ShopGuiRestart.BACKUP).resolve("world/gangshop_wallets.json"))
            .equals("{\"wallets\":{\"example\":100}}\n"), "Interrupted installation replaced backup");
        Files.delete(root.resolve(ShopGuiRestart.BACKUP).resolve("BACKED_UP.txt"));
        boolean failed = false;
        try {
            ShopGuiRestart.preflight(root, payload, sums);
        } catch (IllegalStateException expected) {
            failed = true;
        }
        require(failed, "Applied update without backup marker was accepted");
        System.out.println("PASS: pending update, preflight, lock, duplicate mods, backup, repeat and interrupted installation.");
    }

    static void reject(Path root, java.util.Map<String, byte[]> payload, java.util.Properties sums) throws Exception {
        boolean failed = false;
        try {
            ShopGuiRestart.install(root, payload, sums);
        } catch (IllegalStateException expected) {
            failed = true;
        }
        require(failed, "Unsafe installation was accepted");
        require(!Files.exists(root.resolve(ShopGuiRestart.MOD)), "Mod installed after rejection");
    }

    static void write(Path path, String content) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }

    static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
