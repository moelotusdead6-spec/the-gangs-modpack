import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

public final class GravesRestart {
    static final Path COMPAT = Path.of("mods", "gangsgravescompat-1.0.0.jar");
    static final String COMPAT_SHA256 = "1cec6d25be33e515ef524ca0e69996cd67856b111cff62681e542de3d6be4515";
    static final String NEXT = "gangs-mount-claim-restart-1.0.1.jar";
    static final String NEXT_SHA256 = "7b69f096f8de8fadf2933fe82ed7b71779856affaef52b7ca116de9ed36b8e67";
    static final String FABRIC_SHA256 = "6f921315fe2f570c331ca877d1e815c29b93fdc4ad04ce0f0f5cdf4300710028";
    static final Map<String, String> DEPENDENCIES = Map.of(
        "graves-3.0.3+1.20.1.jar",
        "d606625c99a4e9becf743bf9d889aa1b9d65cce92ceedd158cb33169c695c46f",
        "sophisticatedbackpacks-1.20.1-3.23.4.5.110.jar",
        "15ec32d411a70384743a5770ae6a22b71a8418af7f8eebbffac7bfc8a1836c0f",
        "sophisticatedcore-1.20.1-1.2.7.15.166.jar",
        "1739acb62e22f99424f32424b9962bd92d29e0752f5495aa116df9a7c1dd831a");
    private static final Pattern MOD_ID = Pattern.compile("\"id\"\\s*:\\s*\"gangsgravescompat\"");

    public static void main(String[] args) throws Throwable {
        Path root = Path.of(".").toAbsolutePath().normalize();
        check(root.resolve("fabric-server-launch.jar"), FABRIC_SHA256);
        check(root.resolve(NEXT), NEXT_SHA256);
        install(root, payload());
        System.out.println("[GravesRestart] Handing off to the unchanged " + NEXT + ".");
        MountClaimRestart.main(args);
    }

    static void install(Path root, byte[] payload) throws Exception {
        if (!digest(payload).equals(COMPAT_SHA256)) {
            throw new IllegalStateException("Corrupt safe-graves payload.");
        }
        Path mods = root.resolve("mods");
        try (FileChannel channel = FileChannel.open(root.resolve(Path.of("world", "session.lock")),
                StandardOpenOption.WRITE);
             var lock = channel.tryLock()) {
            if (lock == null) throw new IllegalStateException("World is still running.");
            for (var entry : DEPENDENCIES.entrySet()) {
                check(mods.resolve(entry.getKey()), entry.getValue());
            }
            Path target = root.resolve(COMPAT);
            if (Files.exists(target)) check(target, COMPAT_SHA256);
            rejectDuplicates(mods, target);
            if (Files.exists(target)) {
                System.out.println("[GravesRestart] Safe-graves mod already installed and verified.");
                return;
            }
            Path temporary = Files.createTempFile(mods, "gangsgravescompat-", ".tmp");
            try {
                Files.write(temporary, payload);
                check(temporary, COMPAT_SHA256);
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temporary);
            }
            check(target, COMPAT_SHA256);
            System.out.println("[GravesRestart] Safe-graves mod installed and verified.");
        }
    }

    private static void rejectDuplicates(Path mods, Path target) throws IOException {
        try (var files = Files.list(mods)) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".jar")).toList()) {
                if (file.equals(target)) continue;
                try (JarFile jar = new JarFile(file.toFile())) {
                    var entry = jar.getJarEntry("fabric.mod.json");
                    if (entry == null) continue;
                    try (InputStream stream = jar.getInputStream(entry)) {
                        if (MOD_ID.matcher(new String(stream.readAllBytes(),
                                java.nio.charset.StandardCharsets.UTF_8)).find()) {
                            throw new IllegalStateException("Duplicate safe-graves mod: " + file);
                        }
                    }
                }
            }
        }
    }

    static void check(Path file, String expected) throws Exception {
        if (!Files.isRegularFile(file) || !digest(Files.readAllBytes(file)).equals(expected)) {
            throw new IllegalStateException("Missing file or unexpected SHA-256: " + file);
        }
    }

    static String digest(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    static byte[] payload() throws IOException {
        try (InputStream stream = GravesRestart.class.getResourceAsStream(
                "/safe-graves-payload/mods/gangsgravescompat-1.0.0.jar")) {
            if (stream == null) throw new IOException("Missing safe-graves payload.");
            return stream.readAllBytes();
        }
    }
}
