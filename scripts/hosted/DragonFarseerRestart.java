import java.io.IOException;
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
import java.util.Properties;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

public final class DragonFarseerRestart {
    static final String NEXT = "gangs-mount-variants-restart-1.0.3.jar";
    static final String NEXT_HASH = "3ec3710536cafcf695afefc722c4039e2ac7413503fd125b343b7162b50d3bdc";
    static final String MOD = "mods/gangsdragonloot-1.0.0.jar";
    static final String FARSEER = "config/alexsmobs/farseer.json";
    static final String LOG = "log4j2-server.xml";
    static final String RESOURCE = "/dragon-farseer-1.0.0-payload/";
    static final Path BACKUP = Path.of("backups", "dragon-farseer-1.0.0");
    static final Map<String, String> PREVIOUS = Map.of(
        FARSEER, "c245528d844458d8ac416572ee0df7575bb23d58beb07367d4cd55c66fec9000",
        LOG, "a40a2c638080af173f82962a89bd97f64b19c4e943dbf3ea5bca205b215c0bbe");
    static final Map<String, String> DEPENDENCIES = Map.of(
        "fabric-server-launch.jar", "6f921315fe2f570c331ca877d1e815c29b93fdc4ad04ce0f0f5cdf4300710028",
        NEXT, NEXT_HASH,
        "gangs-safe-graves-restart-1.0.0.jar", "111c618cfdb4104a7a214bc736e829c709cf7b67f31cddaabda81c46f763d9eb",
        "gangs-mount-claim-restart-1.0.1.jar", "7b69f096f8de8fadf2933fe82ed7b71779856affaef52b7ca116de9ed36b8e67",
        "mods/simplyswords-1.56.0-1.20.1.jar", "ddbaf8ab85a6b003a817d0c0e817dcbdf9fcfe6b889c729ff72c4b6a3b1f3e9a",
        "mods/simplymore-fabric-1.1.3.jar", "3137d64e9f4c5c7f0e62db7862b209f06c6895e4811d186520d49c0c2be5bcb4",
        "mods/alexsmobs-2.0.12-fabric+1.20.1.jar", "44f548f3995ea4f90b10795e6b1775821e4856a9824ff57b74973b32398453bc");

    public static void main(String[] args) throws Throwable {
        Path root = Path.of(".").toAbsolutePath().normalize();
        install(root, payloads(), checksums());
        System.setProperty("log4j.configurationFile", root.resolve(LOG).toString());
        try {
            Class.forName("MountVariantsRestart").getMethod("main", String[].class).invoke(null, (Object) args);
        } catch (java.lang.reflect.InvocationTargetException failure) {
            throw failure.getCause();
        }
    }

    static void install(Path root, Map<String, byte[]> payloads, Properties checksums) throws Exception {
        for (String name : new String[]{MOD, FARSEER, LOG}) {
            if (!hash(payloads.get(name)).equals(checksums.getProperty(name))) {
                throw new IllegalStateException("Corrupt payload: " + name);
            }
        }
        for (var entry : DEPENDENCIES.entrySet()) check(root.resolve(entry.getKey()), entry.getValue());
        try (FileChannel channel = FileChannel.open(root.resolve(Path.of("world", "session.lock")),
                StandardOpenOption.WRITE); var lock = channel.tryLock()) {
            if (lock == null) throw new IllegalStateException("World is still running.");
            requireSingleMod(root);
            for (var entry : PREVIOUS.entrySet()) {
                Path target = root.resolve(entry.getKey());
                String actual = hash(Files.readAllBytes(target));
                if (!actual.equals(entry.getValue()) && !actual.equals(checksums.getProperty(entry.getKey()))) {
                    throw new IllegalStateException("File changed since staging: " + target);
                }
                Path saved = root.resolve(BACKUP).resolve(entry.getKey());
                if (Files.exists(saved)) check(saved, entry.getValue());
                else if (!actual.equals(entry.getValue())) {
                    throw new IllegalStateException("Updated config is missing its original backup: " + target);
                }
            }
            if (Files.exists(root.resolve(MOD))) check(root.resolve(MOD), checksums.getProperty(MOD));
            for (String name : PREVIOUS.keySet()) {
                Path saved = root.resolve(BACKUP).resolve(name);
                if (!Files.exists(saved)) {
                    Files.createDirectories(saved.getParent());
                    Files.copy(root.resolve(name), saved);
                    check(saved, PREVIOUS.get(name));
                }
            }
            for (var entry : payloads.entrySet()) {
                Path target = root.resolve(entry.getKey());
                String expected = checksums.getProperty(entry.getKey());
                if (Files.isRegularFile(target) && hash(Files.readAllBytes(target)).equals(expected)) continue;
                Path temporary = Files.createTempFile(target.getParent(), "dragon-farseer-", ".tmp");
                try {
                    Files.write(temporary, entry.getValue());
                    check(temporary, expected);
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } finally {
                    Files.deleteIfExists(temporary);
                }
                check(target, expected);
            }
            System.out.println("[DragonFarseerRestart] Dragon loot, Farseer exclusion and console filter verified.");
        }
    }

    static void requireSingleMod(Path root) throws IOException {
        Pattern id = Pattern.compile("\"id\"\\s*:\\s*\"gangsdragonloot\"");
        try (var files = Files.list(root.resolve("mods"))) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".jar")).toList()) {
                if (file.equals(root.resolve(MOD))) continue;
                try (JarFile jar = new JarFile(file.toFile())) {
                    var manifest = jar.getJarEntry("fabric.mod.json");
                    if (manifest == null) continue;
                    try (InputStream stream = jar.getInputStream(manifest)) {
                        if (id.matcher(new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).find()) {
                            throw new IllegalStateException("Duplicate dragon loot mod: " + file);
                        }
                    }
                }
            }
        }
    }

    static Map<String, byte[]> payloads() throws IOException {
        var result = new LinkedHashMap<String, byte[]>();
        for (String name : new String[]{MOD, FARSEER, LOG}) result.put(name, resource(name));
        return result;
    }

    static Properties checksums() throws IOException {
        var properties = new Properties();
        properties.load(new java.io.ByteArrayInputStream(resource("checksums.properties")));
        return properties;
    }

    static byte[] resource(String name) throws IOException {
        try (InputStream stream = DragonFarseerRestart.class.getResourceAsStream(RESOURCE + name)) {
            if (stream == null) throw new IOException("Missing restart payload: " + name);
            return stream.readAllBytes();
        }
    }

    static String hash(byte[] bytes) throws Exception {
        if (bytes == null) throw new IllegalStateException("Missing payload bytes.");
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    static void check(Path file, String expected) throws Exception {
        if (!Files.isRegularFile(file) || !hash(Files.readAllBytes(file)).equals(expected)) {
            throw new IllegalStateException("Missing file or unexpected SHA-256: " + file);
        }
    }
}
