import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

public final class BotaniaNoFlightRestart {
    static final String MOD = "gangsbotaniacompat-1.0.1.jar";
    static final String MOD_SHA256 = "8f05304d0976bb90fa9ba51ca58b51ab889083e377fede47bea6c99d3f3f2614";
    static final String BOTANIA = "Botania-1.20.1-456-FABRIC.jar";
    static final String BOTANIA_SHA512 = "2e189e5b7a9eda42c1ae5f1a218ac2f10b434b310d9e47176e2b9cbaf0c229166a56b2b8c766ab53ae1fa6e87d464f78c98a00fe06f715f8e2b645778fbb8a44";
    static final String NEXT = "gangs-discord-bridge-restart-1.0.1.jar";
    static final String NEXT_SHA256 = "4a801c6667e5f5bb132e7b8235482cbc044a78e03822a3e25b9d54fb5dc3fab5";
    static final String FABRIC_SHA256 = "6f921315fe2f570c331ca877d1e815c29b93fdc4ad04ce0f0f5cdf4300710028";
    static final String PAYLOAD = "/botania-no-flight-1.0.1-payload/mod.jar";

    public static void main(String[] args) throws Throwable {
        Path root = Path.of(".").toAbsolutePath().normalize();
        check(root.resolve("fabric-server-launch.jar"), FABRIC_SHA256);
        check(root.resolve(NEXT), NEXT_SHA256);
        install(root, payload());
        System.out.println("[BotaniaNoFlightRestart] Handing off to pending " + NEXT + ".");
        try {
            Class.forName("DiscordBridgeRestart").getMethod("main", String[].class).invoke(null, (Object)args);
        } catch (java.lang.reflect.InvocationTargetException failure) {
            throw failure.getCause();
        }
    }

    static void install(Path root, byte[] payload) throws Exception {
        if (!digest(payload, "SHA-256").equals(MOD_SHA256)) {
            throw new IllegalStateException("Corrupt Botania no-flight payload.");
        }
        Path mods = root.resolve("mods");
        try (FileChannel channel = FileChannel.open(root.resolve(Path.of("world", "session.lock")),
                StandardOpenOption.WRITE); var lock = channel.tryLock()) {
            if (lock == null) throw new IllegalStateException("World is still running.");
            requireOnly(mods, "botania-", BOTANIA);
            requireOnly(mods, "gangsbotaniacompat-", MOD);
            Path botania = mods.resolve(BOTANIA);
            if (!Files.isRegularFile(botania)
                    || !digest(Files.readAllBytes(botania), "SHA-512").equals(BOTANIA_SHA512)) {
                throw new IllegalStateException("Botania does not match the verified 1.20.1-456 Fabric jar.");
            }
            Path target = mods.resolve(MOD);
            if (Files.exists(target)) {
                check(target, MOD_SHA256);
                System.out.println("[BotaniaNoFlightRestart] No-flight compatibility mod already installed and verified.");
                return;
            }
            Path temporary = Files.createTempFile(mods, "gangsbotaniacompat-", ".tmp");
            try {
                Files.write(temporary, payload);
                check(temporary, MOD_SHA256);
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temporary);
            }
            check(target, MOD_SHA256);
            System.out.println("[BotaniaNoFlightRestart] Tiara flight, glide and dash disabled; items preserved.");
        }
    }

    static void requireOnly(Path mods, String prefix, String allowed) throws IOException {
        try (var files = Files.list(mods)) {
            var unexpected = files.map(path -> path.getFileName().toString())
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)
                    && name.toLowerCase(Locale.ROOT).endsWith(".jar") && !name.equals(allowed))
                .toList();
            if (!unexpected.isEmpty()) throw new IllegalStateException("Unexpected mod jars: " + unexpected);
        }
    }

    static void check(Path file, String expected) throws Exception {
        if (!Files.isRegularFile(file) || !digest(Files.readAllBytes(file), "SHA-256").equals(expected)) {
            throw new IllegalStateException("Missing file or unexpected SHA-256: " + file);
        }
    }

    static String digest(byte[] bytes, String algorithm) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(bytes));
    }

    static byte[] payload() throws IOException {
        try (InputStream stream = BotaniaNoFlightRestart.class.getResourceAsStream(PAYLOAD)) {
            if (stream == null) throw new IOException("Missing Botania no-flight payload.");
            return stream.readAllBytes();
        }
    }
}
