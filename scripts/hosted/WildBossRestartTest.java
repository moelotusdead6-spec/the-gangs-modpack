import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarFile;

public final class WildBossRestartTest {
    public static void main(String[] args) throws Exception {
        if (args[0].equals("--packaged")) {
            try (JarFile jar = new JarFile(args[1])) {
                require(jar.getManifest().getMainAttributes().getValue("Class-Path").equals(
                    "fabric-server-launch.jar " + WildBossRestart.PREDECESSOR), "Invalid manifest class path");
                for (String name : WildBossRestart.EMBEDDED) {
                    var entry = jar.getJarEntry("wild-boss-1.0.78-payload/" + name);
                    require(entry != null, "Missing packaged payload: " + name);
                    try (var in = jar.getInputStream(entry)) {
                        require(WildBossRestart.hash(in.readAllBytes()).equals(
                            WildBossRestart.checksums().getProperty(name)), "Bad packaged bytes: " + name);
                    }
                }
            }
            try (var loader = new java.net.URLClassLoader(
                new java.net.URL[]{Path.of(args[1]).toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
                var previous = Class.forName("TrialsLightnessRestart", true, loader);
                var checksums = previous.getDeclaredMethod("checksums");
                checksums.setAccessible(true);
                require(((Properties) checksums.invoke(null)).stringPropertyNames().equals(
                    TrialsLightnessRestart.EMBEDDED), "Predecessor payload shadowed");
            }
            System.out.println("PASS: packaged manifest, embedded hashes and predecessor resources.");
            return;
        }
        Path repo = Path.of(args[0]);
        Path previousMenu = Path.of(args[2]);
        Path root = trialsHost(repo, Path.of(args[1]));
        TrialsLightnessRestart.prepare(root, TrialsLightnessRestart.payloads(), TrialsLightnessRestart.checksums());
        copy(previousMenu, root.resolve(WildBossRestart.MENU));
        String oldMenu = Files.readString(root.resolve(WildBossRestart.MENU));
        var payloads = WildBossRestart.payloads();
        var sums = WildBossRestart.checksums();

        WildBossRestart.verifyPredecessor(root);
        var corrupt = new LinkedHashMap<>(payloads);
        corrupt.put(WildBossRestart.GOLDCLAIM, new byte[]{1, 2, 3});
        reject(root, corrupt, sums);
        require(!Files.exists(root.resolve(WildBossRestart.GOLDCLAIM)), "Wrote after bad payload");
        try (var channel = FileChannel.open(root.resolve("world/session.lock"), StandardOpenOption.WRITE);
             var lock = channel.lock()) {
            reject(root, payloads, sums);
        }
        Files.copy(root.resolve(WildBossRestart.OLD), root.resolve("mods/goldclaim-copy.jar"));
        reject(root, payloads, sums);
        Files.delete(root.resolve("mods/goldclaim-copy.jar"));

        WildBossRestart.prepare(root, payloads, sums);
        WildBossRestart.prepare(root, payloads, sums);
        require(!Files.exists(root.resolve(WildBossRestart.OLD)), "Old GoldClaim remains");
        WildBossRestart.check(root.resolve(WildBossRestart.BACKUP).resolve(WildBossRestart.OLD), WildBossRestart.OLD_HASH);
        WildBossRestart.check(root.resolve(WildBossRestart.GOLDCLAIM), sums.getProperty(WildBossRestart.GOLDCLAIM));
        WildBossRestart.check(root.resolve(WildBossRestart.MENU), sums.getProperty(WildBossRestart.MENU));
        require(Files.readString(root.resolve(WildBossRestart.BACKUP).resolve(WildBossRestart.MENU)).equals(oldMenu),
            "Previous menu not backed up");

        Files.delete(root.resolve(WildBossRestart.BACKUP).resolve("APPLIED.txt"));
        Files.delete(root.resolve(WildBossRestart.GOLDCLAIM));
        WildBossRestart.install(root, payloads, sums);
        require(Files.readString(root.resolve(WildBossRestart.BACKUP).resolve("APPLIED.txt"))
            .equals(WildBossRestart.APPLIED), "Interrupted install did not recover");

        Path fresh = trialsHost(repo, Path.of(args[1]));
        TrialsLightnessRestart.prepare(fresh, TrialsLightnessRestart.payloads(), TrialsLightnessRestart.checksums());
        write(fresh.resolve(WildBossRestart.MENU), "{\"custom\": true}\n");
        WildBossRestart.prepare(fresh, payloads, sums);
        require(Files.readString(fresh.resolve(WildBossRestart.MENU)).equals("{\"custom\": true}\n"), "Customised menu overwritten");
        WildBossRestart.check(fresh.resolve(WildBossRestart.GOLDCLAIM), sums.getProperty(WildBossRestart.GOLDCLAIM));

        Path unapplied = trialsHost(repo, Path.of(args[1]));
        copy(previousMenu, unapplied.resolve(WildBossRestart.MENU));
        WildBossRestart.prepare(unapplied, payloads, sums);
        WildBossRestart.check(unapplied.resolve(WildBossRestart.GOLDCLAIM), sums.getProperty(WildBossRestart.GOLDCLAIM));
        require(Files.exists(unapplied.resolve(TrialsLightnessRestart.BACKUP).resolve("APPLIED.txt")), "Trials not chained");

        Files.writeString(root.resolve(WildBossRestart.BACKUP).resolve("APPLIED.txt"), "changed");
        boolean failed = false;
        try { WildBossRestart.prepare(root, payloads, sums); } catch (IllegalStateException expected) { failed = true; }
        require(failed, "Changed marker was accepted");
        System.out.println("PASS: predecessor verification and chaining, locked world, payload corruption, duplicate IDs, "
            + "install, repeat, interruption, backups and customised-menu preservation.");
    }

    /** Host state immediately before the Trials/Lightness launcher ran (same fixture as TrialsLightnessRestartTest). */
    static Path trialsHost(Path repo, Path candidates) throws Exception {
        Path root = Files.createTempDirectory("wild-boss-tests-");
        Files.createDirectories(root.resolve("mods"));
        Files.createDirectories(root.resolve("world"));
        Files.createFile(root.resolve("world/session.lock"));
        Files.createDirectories(root.resolve(TrialsLightnessRestart.STAGING));
        copy(repo.resolve("artifacts").resolve(WildBossRestart.PREDECESSOR), root.resolve(WildBossRestart.PREDECESSOR));
        copy(repo.resolve("artifacts").resolve(TrialsLightnessRestart.PREDECESSOR), root.resolve(TrialsLightnessRestart.PREDECESSOR));
        for (var entry : InventoryMailboxRestart.DEPENDENCIES.entrySet()) {
            String name = Path.of(entry.getKey()).getFileName().toString();
            Path src = name.equals("fabric-server-launch.jar")
                ? repo.resolve("tmp/worldgen-compat-prod-tests-20261028").resolve(name)
                : repo.resolve("artifacts").resolve(name);
            copy(src, root.resolve(entry.getKey()));
        }
        for (var entry : InventoryMailboxRestart.MARKERS.entrySet()) write(root.resolve(entry.getKey()), entry.getValue());
        write(root.resolve(InventoryMailboxRestart.BACKUP).resolve("APPLIED.txt"), InventoryMailboxRestart.APPLIED);
        copy(repo.resolve("artifacts/goldclaim-1.0.76.jar"),
            root.resolve(InventoryMailboxRestart.BACKUP).resolve(InventoryMailboxRestart.OLD));
        for (var entry : InventoryMailboxRestart.payloads().entrySet()) write(root.resolve(entry.getKey()), entry.getValue());
        for (var entry : DragonFarseerRestart.payloads().entrySet()) write(root.resolve(entry.getKey()), entry.getValue());
        for (var entry : DragonFarseerRestart.DEPENDENCIES.entrySet()) {
            Path target = root.resolve(entry.getKey());
            if (Files.exists(target)) continue;
            String name = target.getFileName().toString();
            Path src = name.startsWith("alexsmobs")
                ? repo.resolve("tmp/modcache").resolve(name) : repo.resolve("tmp/testserver/mods").resolve(name);
            copy(src, target);
        }
        for (String name : DragonFarseerRestart.PREVIOUS.keySet()) {
            Path backup = root.resolve(DragonFarseerRestart.BACKUP).resolve(name);
            if (name.equals(DragonFarseerRestart.FARSEER)) {
                copy(repo.resolve("tmp/testserver/config/alexsmobs/farseer.json"), backup);
            } else {
                String oldLog = Files.readString(repo.resolve("scripts/hosted/log4j2-dragon-farseer.xml"))
                    .replace("\r\n", "\n")
                    .replaceAll("(?m)^.*LevelRangeFilter.*\\r?\\n", "")
                    .replaceAll("(?m)^.*\\(\\?is\\).*\\r?\\n", "")
                    .replace("additivity=\"false\"/>\n    <Root", "additivity=\"false\"/>\n\n    <Root")
                    .replace("\n", "\r\n");
                write(backup, oldLog);
            }
        }
        for (var entry : TrialsLightnessRestart.OLD.entrySet()) {
            Path target = root.resolve(entry.getKey());
            if (!Files.exists(target)) copy(repo.resolve("tmp/testserver/mods").resolve(target.getFileName()), target);
        }
        for (var entry : TrialsLightnessRestart.STAGED.entrySet()) {
            Path name = Path.of(entry.getKey()).getFileName();
            copy(candidates.resolve(name), root.resolve(TrialsLightnessRestart.STAGING).resolve(name));
        }
        return root;
    }

    static void copy(Path source, Path target) throws Exception {
        Files.createDirectories(target.getParent());
        Files.copy(source, target);
    }
    static void write(Path path, String value) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, value);
    }
    static void write(Path path, byte[] value) throws Exception {
        Files.createDirectories(path.getParent());
        Files.write(path, value);
    }
    static void reject(Path root, Map<String, byte[]> payloads, Properties sums) throws Exception {
        boolean failed = false;
        try { WildBossRestart.install(root, payloads, sums); }
        catch (IllegalStateException expected) { failed = true; }
        require(failed, "Unsafe installation was accepted");
    }
    static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
