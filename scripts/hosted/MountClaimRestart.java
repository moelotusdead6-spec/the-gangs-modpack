import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

public final class MountClaimRestart {
    static final Path STAGING = Path.of("staging", "mount-claim-1.0.0");
    static final Path BACKUP = Path.of("backups", "mount-claim-1.0.0");
    static final String RESOURCES = "/mount-claim-1.0.0-payload/";
    static final String APPLIED = "Mounts, admin claim permissions and shop exclusions installed.\n";
    static final Map<String, String> OLD = Map.of(
        "goldclaim-1.0.74.jar", "ea43770aadb71de491e4a5d51176e37bfbc42a19c265ad6cc2fc82b51fb71c43",
        "gangshop-1.0.11.jar", "39d2deee0019ba566233655205810835a927dad2f9aad8c9fef6b17173c1827f");
    static final Map<String, String> NEW = Map.of(
        "goldclaim-1.0.75.jar", "038011d0d06f445dac25635e98c9c1dc97050096981a1e16ed3b62c3101e2782",
        "gangshop-1.0.12.jar", "0e47892e9abb773763be9e527ec222b2bbc18e7d7f17ba93aba22761ea93a5ce",
        "gangsmounts-1.0.0.jar", "1fabab4553df4f9ef8c59faa62a81642eda9e8151b997b4d348f56cbe200c1d4",
        "mythic-mounts-1.20.1-7.2.jar", "74808c8d2a3ad49929780a746ccd2e5a4452f168eef686e69467512c42fd27cc");
    static final String MYTHIC = "mythic-mounts-1.20.1-7.2.jar";
    static final Map<String, String> PREREQUISITES = Map.of(
        "fabric-server-launch.jar", "6f921315fe2f570c331ca877d1e815c29b93fdc4ad04ce0f0f5cdf4300710028",
        "mods/gangscosmetics-2.0.2.jar", "ed6bc5ca0bbe88e85d62300600df8486bb8b92c5e8f1a6bc368faaaed5b3bfc1",
        "mods/geckolib-fabric-1.20.1-4.8.4.jar", "ed2e76a7f818fd1a044fe986a8b7f7f870131d6746cd5bcf584b7ac9c27df483",
        "mods/Patchouli-1.20.1-85-FABRIC.jar", "485e23ab9727c7e9f585f435020d733706a80c7f0a1dc20e85c7398e1d76b919");

    public static void main(String[] args) throws Throwable {
        Path root = Path.of(".").toAbsolutePath().normalize();
        requirePrerequisites(root);
        install(root, payloads(root));
        String main;
        try (JarFile launcher = new JarFile(root.resolve("fabric-server-launch.jar").toFile())) {
            main = launcher.getManifest().getMainAttributes().getValue("Main-Class");
        }
        if (main == null || main.equals(MountClaimRestart.class.getName())) {
            throw new IllegalStateException("Invalid Fabric launcher");
        }
        System.setProperty("java.class.path", root.resolve("fabric-server-launch.jar").toString());
        System.out.println("[MountClaimRestart] Starting original Fabric launcher; prior update retained.");
        try {
            Class.forName(main).getMethod("main", String[].class).invoke(null, (Object)args);
        } catch (java.lang.reflect.InvocationTargetException failure) {
            throw failure.getCause();
        }
    }

    static void requirePrerequisites(Path root) throws Exception {
        for (var entry : PREREQUISITES.entrySet()) check(root.resolve(entry.getKey()), entry.getValue());
        String previous = "Furniture mods, staff-only workstation, shop exclusions and hub projectile protection installed.\n";
        if (!Files.readString(root.resolve("backups/furniture-hub-1.0.0/APPLIED.txt")).equals(previous)) {
            throw new IllegalStateException("The preceding furniture update is not verified as applied.");
        }
    }

    static Map<String, byte[]> payloads(Path root) throws Exception {
        Map<String, byte[]> payloads = new LinkedHashMap<>();
        for (String name : NEW.keySet()) {
            byte[] data;
            if (name.equals(MYTHIC)) {
                data = Files.readAllBytes(root.resolve(STAGING).resolve(name));
            } else {
                try (InputStream stream = MountClaimRestart.class.getResourceAsStream(RESOURCES + name)) {
                    if (stream == null) throw new IllegalStateException("Missing payload " + name);
                    data = stream.readAllBytes();
                }
            }
            payloads.put(name, data);
        }
        return payloads;
    }

    static void install(Path root, Map<String, byte[]> payloads) throws Exception {
        if (!payloads.keySet().equals(NEW.keySet())) throw new IllegalStateException("Incomplete payloads");
        for (var entry : NEW.entrySet()) {
            if (!digest(payloads.get(entry.getKey())).equals(entry.getValue())) {
                throw new IllegalStateException("Corrupt payload: " + entry.getKey());
            }
        }
        try (FileChannel channel = FileChannel.open(root.resolve("world/session.lock"), StandardOpenOption.WRITE);
                var lock = channel.tryLock()) {
            if (lock == null) throw new IllegalStateException("World is still running.");
            Path marker = root.resolve(BACKUP).resolve("APPLIED.txt");
            if (Files.exists(marker) && !Files.readString(marker).equals(APPLIED)) {
                throw new IllegalStateException("Unexpected installation marker");
            }
            for (var entry : OLD.entrySet()) {
                Path original = root.resolve("mods").resolve(entry.getKey());
                Path saved = root.resolve(BACKUP).resolve(entry.getKey());
                if (Files.exists(original)) check(original, entry.getValue());
                if (Files.exists(saved)) check(saved, entry.getValue());
                if (!Files.exists(original) && !Files.exists(saved)) {
                    throw new IllegalStateException("Original and backup missing: " + entry.getKey());
                }
            }
            for (var entry : NEW.entrySet()) {
                Path target = root.resolve("mods").resolve(entry.getKey());
                if (Files.exists(target)) check(target, entry.getValue());
            }
            requireOnlyManagedMods(root);
            for (var entry : OLD.entrySet()) {
                Path original = root.resolve("mods").resolve(entry.getKey());
                Path saved = root.resolve(BACKUP).resolve(entry.getKey());
                if (!Files.exists(saved)) {
                    Files.createDirectories(saved.getParent());
                    Files.copy(original, saved);
                    check(saved, entry.getValue());
                }
            }
            for (var entry : NEW.entrySet()) {
                Path target = root.resolve("mods").resolve(entry.getKey());
                if (!Files.exists(target)) write(target, payloads.get(entry.getKey()), entry.getValue());
            }
            for (String name : OLD.keySet()) Files.deleteIfExists(root.resolve("mods").resolve(name));
            if (!Files.exists(marker)) Files.writeString(marker, APPLIED, StandardOpenOption.CREATE_NEW);
            System.out.println("[MountClaimRestart] Update verified; previous mods saved in " + BACKUP);
        }
    }

    static void requireOnlyManagedMods(Path root) throws Exception {
        Set<String> ids = Set.of("goldclaim", "gangshop", "gangsmounts", "mythicmounts");
        Pattern pattern = Pattern.compile("\"id\"\\s*:\\s*\"([a-z0-9_-]+)\"");
        try (var files = Files.list(root.resolve("mods"))) {
            for (Path file : files.filter(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".jar")).toList()) {
                try (JarFile jar = new JarFile(file.toFile())) {
                    var metadata = jar.getJarEntry("fabric.mod.json");
                    if (metadata == null) continue;
                    String id;
                    try (InputStream stream = jar.getInputStream(metadata)) {
                        var matcher = pattern.matcher(new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                        if (!matcher.find()) throw new IllegalStateException("Cannot read mod ID: " + file);
                        id = matcher.group(1);
                    }
                    String name = file.getFileName().toString();
                    if (ids.contains(id) && !OLD.containsKey(name) && !NEW.containsKey(name)) {
                        throw new IllegalStateException("Unexpected managed mod jar: " + file);
                    }
                }
            }
        }
    }

    static void write(Path target, byte[] bytes, String hash) throws Exception {
        Path temp = Files.createTempFile(target.getParent(), "mount-claim-", ".tmp");
        try {
            Files.write(temp, bytes);
            check(temp, hash);
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            check(target, hash);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    static void check(Path file, String hash) throws Exception {
        if (!Files.isRegularFile(file) || !digest(Files.readAllBytes(file)).equals(hash)) {
            throw new IllegalStateException("Missing file or unexpected SHA-256: " + file);
        }
    }

    static String digest(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
