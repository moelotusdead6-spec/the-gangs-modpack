import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarFile;

public final class TrialsLightnessRestartTest {
    public static void main(String[] args) throws Exception {
        if (args[0].equals("--packaged")) {
            try (JarFile jar = new JarFile(args[1])) {
                require(jar.getManifest().getMainAttributes().getValue("Class-Path").equals(
                    "fabric-server-launch.jar " + TrialsLightnessRestart.PREDECESSOR), "Invalid manifest class path");
                for (String name : TrialsLightnessRestart.EMBEDDED) {
                    var entry = jar.getJarEntry("trials-lightness-1.1.32-payload/" + name);
                    require(entry != null, "Missing packaged payload: " + name);
                    try (var in = jar.getInputStream(entry)) {
                        require(TrialsLightnessRestart.hash(in.readAllBytes()).equals(
                            TrialsLightnessRestart.checksums().getProperty(name)), "Bad packaged bytes: " + name);
                    }
                }
            }
            try (var loader = new java.net.URLClassLoader(
                new java.net.URL[]{Path.of(args[1]).toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
                Class.forName("DragonFarseerRestart", true, loader);
                var previous = Class.forName("InventoryMailboxRestart", true, loader);
                var checksums = previous.getDeclaredMethod("checksums");
                checksums.setAccessible(true);
                require(((Properties) checksums.invoke(null)).stringPropertyNames().equals(
                    InventoryMailboxRestart.PAYLOADS), "Predecessor payload shadowed");
            }
            System.out.println("PASS: packaged manifest, embedded hashes and predecessor resources.");
            return;
        }
        Path repo = Path.of(args[0]);
        Path candidates = Path.of(args[1]);
        Path root = Files.createTempDirectory("trials-lightness-tests-");
        Files.createDirectories(root.resolve("mods"));
        Files.createDirectories(root.resolve("world"));
        Files.createFile(root.resolve("world/session.lock"));
        Files.createDirectories(root.resolve(TrialsLightnessRestart.STAGING));
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
            if (Files.exists(target)) continue;
            copy(repo.resolve("tmp/testserver/mods").resolve(target.getFileName()), target);
        }
        for (var entry : TrialsLightnessRestart.STAGED.entrySet()) {
            Path name = Path.of(entry.getKey()).getFileName();
            copy(candidates.resolve(name), root.resolve(TrialsLightnessRestart.STAGING).resolve(name));
        }
        var payloads = TrialsLightnessRestart.payloads();
        var sums = TrialsLightnessRestart.checksums();
        TrialsLightnessRestart.preflight(root, payloads, sums);
        TrialsLightnessRestart.verifyPredecessor(root);
        var corrupt = new LinkedHashMap<>(payloads);
        corrupt.put("mods/gangstrials-1.0.9.jar", new byte[]{1, 2, 3});
        reject(root, corrupt, sums);
        require(!Files.exists(root.resolve("mods/gangstrials-1.0.9.jar")), "Wrote after bad payload");
        try (var channel = FileChannel.open(root.resolve("world/session.lock"), StandardOpenOption.WRITE);
             var lock = channel.lock()) {
            reject(root, payloads, sums);
        }
        TrialsLightnessRestart.prepare(root, payloads, sums);
        TrialsLightnessRestart.prepare(root, payloads, sums);
        for (var entry : TrialsLightnessRestart.OLD.entrySet()) {
            require(!Files.exists(root.resolve(entry.getKey())), "Old mod remains");
            TrialsLightnessRestart.check(root.resolve(TrialsLightnessRestart.BACKUP).resolve(entry.getKey()), entry.getValue());
        }
        for (String name : TrialsLightnessRestart.EMBEDDED) TrialsLightnessRestart.check(root.resolve(name), sums.getProperty(name));
        for (var entry : TrialsLightnessRestart.STAGED.entrySet()) TrialsLightnessRestart.check(root.resolve(entry.getKey()), entry.getValue());
        Files.delete(root.resolve(TrialsLightnessRestart.BACKUP).resolve("APPLIED.txt"));
        Files.delete(root.resolve("mods/gangstrials-1.0.9.jar"));
        TrialsLightnessRestart.install(root, payloads, sums);
        require(Files.readString(root.resolve(TrialsLightnessRestart.BACKUP).resolve("APPLIED.txt"))
            .equals(TrialsLightnessRestart.APPLIED), "Interrupted install did not recover");
        Path staged = root.resolve(TrialsLightnessRestart.STAGING).resolve("bclib-3.0.18.jar");
        Files.delete(staged);
        TrialsLightnessRestart.install(root, payloads, sums);
        Files.copy(root.resolve("mods/gangstrials-1.0.9.jar"), root.resolve("mods/unexpected-trials.jar"));
        reject(root, payloads, sums);
        Files.delete(root.resolve("mods/unexpected-trials.jar"));
        Files.writeString(root.resolve(InventoryMailboxRestart.BACKUP).resolve("APPLIED.txt"), "changed");
        reject(root, payloads, sums);
        System.out.println("PASS: predecessor verification, locked world, payload corruption, duplicate IDs, "
            + "install, repeat, interruption, backups and no longer needed staging files.");
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
        try { TrialsLightnessRestart.install(root, payloads, sums); }
        catch (IllegalStateException expected) { failed = true; }
        require(failed, "Unsafe installation was accepted");
    }
    static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
