import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarFile;

public final class InventoryMailboxRestartTest {
    public static void main(String[] args) throws Exception {
        if (args[0].equals("--packaged")) {
            try (JarFile jar = new JarFile(args[1])) {
                require(jar.getManifest().getMainAttributes().getValue("Class-Path").equals(
                    "fabric-server-launch.jar " + InventoryMailboxRestart.NEXT), "Invalid Class-Path");
                for (String name : InventoryMailboxRestart.PAYLOADS) {
                    var entry = jar.getJarEntry("inventory-mailbox-1.0.0-payload/" + name);
                    require(entry != null, "Missing packaged payload");
                    try (var stream = jar.getInputStream(entry)) {
                        require(InventoryMailboxRestart.hash(stream.readAllBytes()).equals(
                            InventoryMailboxRestart.checksums().getProperty(name)), "Bad packaged bytes");
                    }
                }
            }
            try (var loader = new java.net.URLClassLoader(
                new java.net.URL[]{Path.of(args[1]).toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
                for (String name : new String[]{"DragonFarseerRestart", "MountVariantsRestart", "GravesRestart", "MountClaimRestart"}) {
                    Class.forName(name, true, loader).getMethod("main", String[].class);
                }
                var release = Class.forName("InventoryMailboxRestart", true, loader);
                var method = release.getDeclaredMethod("checksums");
                method.setAccessible(true);
                Properties ownSums = (Properties) method.invoke(null);
                require(ownSums.stringPropertyNames().equals(InventoryMailboxRestart.PAYLOADS),
                    "Predecessor classpath shadowed release payloads");
            }
            System.out.println("PASS: packaged manifest, payload hashes and predecessor class/resource loading.");
            return;
        }
        Path repo = Path.of(args[0]);
        Path temporary = Files.createTempDirectory("inventory-mailbox-tests-");
        try {
            var payloads = InventoryMailboxRestart.payloads();
            var sums = InventoryMailboxRestart.checksums();
            Path success = fixture(repo, temporary.resolve("success"));
            InventoryMailboxRestart.install(success, payloads, sums);
            InventoryMailboxRestart.install(success, payloads, sums);
            require(!Files.exists(success.resolve(InventoryMailboxRestart.OLD)), "Old GoldClaim remains installed");
            InventoryMailboxRestart.check(success.resolve(InventoryMailboxRestart.BACKUP).resolve(
                InventoryMailboxRestart.OLD), InventoryMailboxRestart.OLD_HASH);
            for (String name : InventoryMailboxRestart.PAYLOADS) {
                InventoryMailboxRestart.check(success.resolve(name), sums.getProperty(name));
            }
            Files.delete(success.resolve(InventoryMailboxRestart.HISTORY));
            Files.delete(success.resolve(InventoryMailboxRestart.BACKUP).resolve("APPLIED.txt"));
            InventoryMailboxRestart.install(success, payloads, sums);
            require(Files.readString(success.resolve(InventoryMailboxRestart.BACKUP).resolve("APPLIED.txt"))
                .equals(InventoryMailboxRestart.APPLIED), "Interrupted install did not recover");

            Path partial = fixture(repo, temporary.resolve("partial"));
            Files.createDirectories(partial.resolve(InventoryMailboxRestart.BACKUP).resolve("mods"));
            Files.copy(partial.resolve(InventoryMailboxRestart.OLD),
                partial.resolve(InventoryMailboxRestart.BACKUP).resolve(InventoryMailboxRestart.OLD));
            Files.write(partial.resolve(InventoryMailboxRestart.GOLDCLAIM), payloads.get(InventoryMailboxRestart.GOLDCLAIM));
            InventoryMailboxRestart.install(partial, payloads, sums);

            Path corrupt = fixture(repo, temporary.resolve("corrupt"));
            var broken = new LinkedHashMap<>(payloads);
            broken.put(InventoryMailboxRestart.HISTORY, new byte[]{1, 2, 3});
            reject(corrupt, broken, sums);
            require(!Files.exists(corrupt.resolve(InventoryMailboxRestart.GOLDCLAIM)), "Wrote update after bad payload");
            Path incomplete = fixture(repo, temporary.resolve("incomplete"));
            broken = new LinkedHashMap<>(payloads);
            broken.remove(InventoryMailboxRestart.VIEWER);
            reject(incomplete, broken, sums);
            Path dependency = fixture(repo, temporary.resolve("dependency"));
            Files.writeString(dependency.resolve(InventoryMailboxRestart.NEXT), "changed");
            reject(dependency, payloads, sums);
            Path marker = fixture(repo, temporary.resolve("marker"));
            Files.writeString(marker.resolve("backups/mount-variants-1.0.2/APPLIED.txt"), "not applied");
            reject(marker, payloads, sums);
            for (String name : InventoryMailboxRestart.PAYLOADS) {
                Path duplicate = fixture(repo, temporary.resolve("duplicate-" + Path.of(name).getFileName()));
                Files.write(duplicate.resolve("mods/renamed-mod.jar"), payloads.get(name));
                reject(duplicate, payloads, sums);
            }
            Path changed = fixture(repo, temporary.resolve("changed"));
            Files.writeString(changed.resolve(InventoryMailboxRestart.OLD), "changed");
            reject(changed, payloads, sums);
            Path locked = fixture(repo, temporary.resolve("locked"));
            try (var channel = FileChannel.open(locked.resolve("world/session.lock"), StandardOpenOption.WRITE);
                 var lock = channel.lock()) {
                reject(locked, payloads, sums);
            }
            Path backup = fixture(repo, temporary.resolve("backup"));
            InventoryMailboxRestart.install(backup, payloads, sums);
            Files.writeString(backup.resolve(InventoryMailboxRestart.BACKUP).resolve(InventoryMailboxRestart.OLD), "changed");
            reject(backup, payloads, sums);
            System.out.println("PASS: install, repeat, interrupted installs, original backups, missing/corrupt payloads, "
                + "changed dependencies/markers/mods, renamed duplicates, world lock and tampered backup refusal.");
        } finally {
            try (var paths = Files.walk(temporary)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private static Path fixture(Path repo, Path root) throws Exception {
        Files.createDirectories(root.resolve("mods"));
        Files.createDirectories(root.resolve("world"));
        Files.createFile(root.resolve("world/session.lock"));
        for (var entry : InventoryMailboxRestart.DEPENDENCIES.entrySet()) {
            String name = Path.of(entry.getKey()).getFileName().toString();
            Path source = repo.resolve("artifacts").resolve(name);
            if (name.equals("fabric-server-launch.jar")) {
                source = repo.resolve(Path.of("tmp", "worldgen-compat-prod-tests-20261028")).resolve(name);
            }
            InventoryMailboxRestart.check(source, entry.getValue());
            Files.copy(source, root.resolve(entry.getKey()));
        }
        Files.copy(repo.resolve(Path.of("artifacts", "goldclaim-1.0.76.jar")), root.resolve(InventoryMailboxRestart.OLD));
        for (var entry : InventoryMailboxRestart.MARKERS.entrySet()) {
            Path marker = root.resolve(entry.getKey());
            Files.createDirectories(marker.getParent());
            Files.writeString(marker, entry.getValue());
        }
        return root;
    }

    private static void reject(Path root, Map<String, byte[]> payloads, Properties sums) throws Exception {
        boolean failed = false;
        try { InventoryMailboxRestart.install(root, payloads, sums); }
        catch (IllegalStateException expected) { failed = true; }
        require(failed, "Unsafe installation was accepted: " + root);
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
