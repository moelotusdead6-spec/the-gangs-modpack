import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.jar.JarFile;

public final class DragonFarseerRestartTest {
    public static void main(String[] args) throws Exception {
        if (args[0].equals("--packaged")) {
            try (JarFile jar = new JarFile(args[1])) {
                require(jar.getManifest().getMainAttributes().getValue("Class-Path").equals(
                    "fabric-server-launch.jar " + DragonFarseerRestart.NEXT), "Invalid Class-Path");
                for (var entry : DragonFarseerRestart.payloads().entrySet()) {
                    require(DragonFarseerRestart.hash(entry.getValue()).equals(
                        DragonFarseerRestart.checksums().getProperty(entry.getKey())), "Bad embedded payload");
                    var resource = jar.getJarEntry("dragon-farseer-1.0.0-payload/" + entry.getKey());
                    require(resource != null, "Missing packaged payload");
                    try (var stream = jar.getInputStream(resource)) {
                        require(DragonFarseerRestart.hash(stream.readAllBytes()).equals(
                            DragonFarseerRestart.checksums().getProperty(entry.getKey())), "Bad packaged bytes");
                    }
                }
            }
            try (var loader = new java.net.URLClassLoader(
                new java.net.URL[]{Path.of(args[1]).toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
                for (String name : new String[]{"MountVariantsRestart", "GravesRestart", "MountClaimRestart"}) {
                    Class.forName(name, true, loader).getMethod("main", String[].class);
                }
            }
            System.out.println("PASS: packaged manifest and embedded payload checksums.");
            return;
        }
        Path repo = Path.of(args[0]);
        Path temporary = Files.createTempDirectory("dragon-farseer-tests-");
        try {
            var payloads = DragonFarseerRestart.payloads();
            var sums = DragonFarseerRestart.checksums();
            Path success = fixture(repo, temporary.resolve("success"));
            DragonFarseerRestart.install(success, payloads, sums);
            DragonFarseerRestart.install(success, payloads, sums);
            for (var entry : payloads.entrySet()) DragonFarseerRestart.check(
                success.resolve(entry.getKey()), sums.getProperty(entry.getKey()));
            for (var entry : DragonFarseerRestart.PREVIOUS.entrySet()) {
                DragonFarseerRestart.check(success.resolve(DragonFarseerRestart.BACKUP).resolve(entry.getKey()), entry.getValue());
            }

            Path partial = fixture(repo, temporary.resolve("partial"));
            DragonFarseerRestart.install(partial, payloads, sums);
            Files.delete(partial.resolve(DragonFarseerRestart.MOD));
            Files.copy(partial.resolve(DragonFarseerRestart.BACKUP).resolve(DragonFarseerRestart.LOG),
                partial.resolve(DragonFarseerRestart.LOG), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            DragonFarseerRestart.install(partial, payloads, sums);

            Path changed = fixture(repo, temporary.resolve("changed"));
            Files.writeString(changed.resolve(DragonFarseerRestart.FARSEER), "changed by owner");
            reject(changed, payloads);
            require(!Files.exists(changed.resolve(DragonFarseerRestart.MOD)), "Wrote mod on preflight failure");
            Path dependency = fixture(repo, temporary.resolve("dependency"));
            Files.writeString(dependency.resolve("mods/simplyswords-1.56.0-1.20.1.jar"), "changed");
            reject(dependency, payloads);
            Path corrupt = fixture(repo, temporary.resolve("corrupt"));
            var broken = new java.util.LinkedHashMap<>(payloads);
            broken.put(DragonFarseerRestart.MOD, new byte[]{1, 2, 3});
            reject(corrupt, broken);
            Path duplicate = fixture(repo, temporary.resolve("duplicate"));
            Files.write(duplicate.resolve("mods/renamed-patch.jar"), payloads.get(DragonFarseerRestart.MOD));
            reject(duplicate, payloads);
            Path locked = fixture(repo, temporary.resolve("locked"));
            try (var channel = FileChannel.open(locked.resolve("world/session.lock"), StandardOpenOption.WRITE);
                 var lock = channel.lock()) {
                reject(locked, payloads);
            }
            Path backup = fixture(repo, temporary.resolve("backup"));
            DragonFarseerRestart.install(backup, payloads, sums);
            Files.writeString(backup.resolve(DragonFarseerRestart.BACKUP).resolve(DragonFarseerRestart.LOG), "changed");
            reject(backup, payloads);
            System.out.println("PASS: install, repeat, interruption, backups, changed config/dependency, "
                + "corrupt payload, renamed duplicate and running-world refusal.");
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
        for (var entry : DragonFarseerRestart.DEPENDENCIES.entrySet()) {
            String name = Path.of(entry.getKey()).getFileName().toString();
            Path source = repo.resolve("artifacts").resolve(name);
            if (name.equals("fabric-server-launch.jar")) {
                source = repo.resolve("tmp/worldgen-compat-prod-tests-20261028").resolve(name);
            } else if (name.startsWith("simply")) {
                source = repo.resolve("tmp/testserver/mods").resolve(name);
            } else if (name.startsWith("alexsmobs")) {
                source = repo.resolve("tmp/modcache").resolve(name);
            }
            DragonFarseerRestart.check(source, entry.getValue());
            Files.copy(source, root.resolve(entry.getKey()));
        }
        Files.createDirectories(root.resolve("config/alexsmobs"));
        Files.copy(repo.resolve("tmp/testserver/config/alexsmobs/farseer.json"),
            root.resolve(DragonFarseerRestart.FARSEER));
        String oldLog = Files.readString(repo.resolve("scripts/hosted/log4j2-dragon-farseer.xml"))
            .replace("\r\n", "\n")
            .replaceAll("(?m)^.*LevelRangeFilter.*\\r?\\n", "")
            .replaceAll("(?m)^.*\\(\\?is\\).*\\r?\\n", "")
            .replace("additivity=\"false\"/>\n    <Root", "additivity=\"false\"/>\n\n    <Root")
            .replace("\n", "\r\n");
        Files.writeString(root.resolve(DragonFarseerRestart.LOG), oldLog, StandardCharsets.UTF_8);
        for (var entry : DragonFarseerRestart.PREVIOUS.entrySet()) {
            DragonFarseerRestart.check(root.resolve(entry.getKey()), entry.getValue());
        }
        return root;
    }

    private static void reject(Path root, Map<String, byte[]> payloads) throws Exception {
        boolean failed = false;
        try { DragonFarseerRestart.install(root, payloads, DragonFarseerRestart.checksums()); }
        catch (IllegalStateException expected) { failed = true; }
        require(failed, "Unsafe installation was accepted: " + root);
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
