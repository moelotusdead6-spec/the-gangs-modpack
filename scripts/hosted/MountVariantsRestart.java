import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

public final class MountVariantsRestart {
    static final Path BACKUP = Path.of("backups", "mount-variants-1.0.2");
    static final String STARTED = "Mount variant update started after verified predecessor installation.\n";
    static final String APPLIED = "Mount variants, inventory, armor, animation, neon rotation and Boots 1.0.8 installed.\n";
    static final Map<String, String> OLD = Map.of(
        "goldclaim-1.0.75.jar", "038011d0d06f445dac25635e98c9c1dc97050096981a1e16ed3b62c3101e2782",
        "gangsmounts-1.0.0.jar", "1fabab4553df4f9ef8c59faa62a81642eda9e8151b997b4d348f56cbe200c1d4",
        "gangsboots-1.0.7.jar", "5c262cb28a905ae55f48235d54e4eca263f3761ebcccfdda22b30dfbf34d5ef0");
    static final Map<String, String> NEW = Map.of(
        "goldclaim-1.0.76.jar", "1422fd21a4ae629bbeb1041acbe8c4c347cf7ce7ab518337a00bea043105862a",
        "gangsmounts-1.0.2.jar", "ed70a718cd94c4aac052c15ab7cd270b78fb724fb17c86cb8aaff5fcedab49ff",
        "gangsboots-1.0.8.jar", "e886f5067038279b40e6f00d00231062f42a462c8b8c5a62dc6e66b3a4e933a1");

    public static void main(String[] args) throws Throwable {
        Path root = Path.of(".").toAbsolutePath().normalize();
        MountClaimRestart.check(root.resolve("gangs-safe-graves-restart-1.0.0.jar"),
            "111c618cfdb4104a7a214bc736e829c709cf7b67f31cddaabda81c46f763d9eb");
        MountClaimRestart.check(root.resolve(GravesRestart.NEXT), GravesRestart.NEXT_SHA256);
        Map<String, byte[]> update = payloads();
        validatePayloads(update);
        preparePredecessors(root);
        install(root, update);
        String main;
        try (JarFile fabric = new JarFile(root.resolve("fabric-server-launch.jar").toFile())) {
            main = fabric.getManifest().getMainAttributes().getValue("Main-Class");
        }
        if (main == null || !main.startsWith("net.fabricmc.")) throw new IllegalStateException("Invalid Fabric launcher");
        System.setProperty("java.class.path", root.resolve("fabric-server-launch.jar").toString());
        try {
            Class.forName(main).getMethod("main", String[].class).invoke(null, (Object) args);
        } catch (java.lang.reflect.InvocationTargetException failure) {
            throw failure.getCause();
        }
    }

    static void preparePredecessors(Path root) throws Exception {
        MountClaimRestart.requirePrerequisites(root);
        Path started = root.resolve(BACKUP).resolve("STARTED.txt");
        if (!Files.exists(started)) {
            MountClaimRestart.install(root, MountClaimRestart.payloads(root));
        } else if (!Files.readString(started).equals(STARTED)) {
            throw new IllegalStateException("Unexpected variant installation phase");
        }
        if (!Files.readString(root.resolve(MountClaimRestart.BACKUP).resolve("APPLIED.txt")).equals(MountClaimRestart.APPLIED)) {
            throw new IllegalStateException("Mount/Claim predecessor not verified");
        }
        for (String name : Set.of("gangshop-1.0.12.jar", MountClaimRestart.MYTHIC)) {
            MountClaimRestart.check(root.resolve("mods").resolve(name), MountClaimRestart.NEW.get(name));
        }
        GravesRestart.install(root, GravesRestart.payload());
    }

    static Map<String, byte[]> payloads() throws Exception {
        Map<String, byte[]> result = new LinkedHashMap<>();
        for (String name : NEW.keySet()) {
            try (InputStream stream = MountVariantsRestart.class.getResourceAsStream("/mount-variants-1.0.2-payload/" + name)) {
                if (stream == null) throw new IllegalStateException("Missing variant payload: " + name);
                result.put(name, stream.readAllBytes());
            }
        }
        return result;
    }

    private static void validatePayloads(Map<String, byte[]> payloads) throws Exception {
        if (!payloads.keySet().equals(NEW.keySet())) throw new IllegalStateException("Incomplete variant payloads");
        for (var entry : NEW.entrySet()) {
            if (!MountClaimRestart.digest(payloads.get(entry.getKey())).equals(entry.getValue())) {
                throw new IllegalStateException("Corrupt variant payload: " + entry.getKey());
            }
        }
    }

    static void install(Path root, Map<String, byte[]> payloads) throws Exception {
        validatePayloads(payloads);
        try (var channel = FileChannel.open(root.resolve(Path.of("world", "session.lock")), StandardOpenOption.WRITE);
             var lock = channel.tryLock()) {
            if (lock == null) throw new IllegalStateException("World is still running");
            Path backup = root.resolve(BACKUP);
            Path started = backup.resolve("STARTED.txt");
            Path applied = backup.resolve("APPLIED.txt");
            if (Files.exists(started) && !Files.readString(started).equals(STARTED)
                || Files.exists(applied) && !Files.readString(applied).equals(APPLIED)) {
                throw new IllegalStateException("Unexpected variant installation marker");
            }
            for (var entry : OLD.entrySet()) {
                Path old = root.resolve("mods").resolve(entry.getKey());
                Path saved = backup.resolve(entry.getKey());
                if (Files.exists(old)) MountClaimRestart.check(old, entry.getValue());
                if (Files.exists(saved)) MountClaimRestart.check(saved, entry.getValue());
                if (!Files.exists(old) && !Files.exists(saved)) throw new IllegalStateException("Original and backup missing: " + entry.getKey());
            }
            for (var entry : NEW.entrySet()) {
                Path target = root.resolve("mods").resolve(entry.getKey());
                if (Files.exists(target)) MountClaimRestart.check(target, entry.getValue());
            }
            rejectDuplicates(root.resolve("mods"));
            Files.createDirectories(backup);
            for (var entry : OLD.entrySet()) {
                Path saved = backup.resolve(entry.getKey());
                if (!Files.exists(saved)) Files.copy(root.resolve("mods").resolve(entry.getKey()), saved);
                MountClaimRestart.check(saved, entry.getValue());
            }
            if (!Files.exists(started)) Files.writeString(started, STARTED, StandardOpenOption.CREATE_NEW);
            for (var entry : NEW.entrySet()) {
                Path target = root.resolve("mods").resolve(entry.getKey());
                if (!Files.exists(target)) MountClaimRestart.write(target, payloads.get(entry.getKey()), entry.getValue());
                MountClaimRestart.check(target, entry.getValue());
            }
            for (String name : OLD.keySet()) Files.deleteIfExists(root.resolve("mods").resolve(name));
            if (!Files.exists(applied)) Files.writeString(applied, APPLIED, StandardOpenOption.CREATE_NEW);
            System.out.println("[MountVariantsRestart] Update verified; predecessors retained; backups in " + BACKUP);
        }
    }

    private static void rejectDuplicates(Path mods) throws Exception {
        Pattern ids = Pattern.compile("\"id\"\\s*:\\s*\"(goldclaim|gangsmounts|gangsboots)\"");
        try (var files = Files.list(mods)) {
            for (Path file : files.filter(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".jar")).toList()) {
                String name = file.getFileName().toString();
                if (OLD.containsKey(name) || NEW.containsKey(name)) continue;
                try (JarFile jar = new JarFile(file.toFile())) {
                    var metadata = jar.getJarEntry("fabric.mod.json");
                    if (metadata == null) continue;
                    try (var stream = jar.getInputStream(metadata)) {
                        if (ids.matcher(new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).find()) {
                            throw new IllegalStateException("Unexpected managed mod jar: " + file);
                        }
                    }
                }
            }
        }
    }
}
