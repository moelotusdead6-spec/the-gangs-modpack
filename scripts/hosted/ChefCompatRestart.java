import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/** Installs the Chef trade compatibility mod before continuing the selected restart chain. */
public final class ChefCompatRestart {
    static final Path CHEF = Path.of("mods", "chefs-delight-1.0.5-fa-1.20.1.jar");
    static final Path FARMER = Path.of("mods", "FarmersDelight-1.20.1-2.5.7+refabricated.jar");
    static final Path COMPAT = Path.of("mods", "gangschefcompat-1.0.0.jar");
    static final String CHEF_SHA256 = "f13cf8e835af96055c4ff962ad6505aa6229e68759e1d4c70e71ab0bbf5dedf3";
    static final String FARMER_SHA256 = "7f5c6cf0e05f66c11925c6e1e66c513fa4f9660a742e38762c929b3ca37de2d9";
    static final String COMPAT_SHA256 = "1fab7ab76a13e545ca73156ffa1ab7bded6bd93478c9cbef5b8d98707047c5a9";
    static final String FABRIC_SHA256 = "6f921315fe2f570c331ca877d1e815c29b93fdc4ad04ce0f0f5cdf4300710028";
    static final String NEXT = "gangs-goldclaim-mailbox-rswmap-restart-1.0.80.jar";
    static final String NEXT_SHA256 = "2ef28ca884025351918d61aac961cb3e9515694b6ee1b69c057dfd895596b673";
    static final String PAYLOAD = "/chefcompat-payload/mods/gangschefcompat-1.0.0.jar";

    public static void main(String[] args) throws Throwable {
        Path root = Path.of(".").toAbsolutePath().normalize();
        check(root.resolve("fabric-server-launch.jar"), FABRIC_SHA256);
        check(root.resolve(NEXT), NEXT_SHA256);
        installUpdates(root, payload());
        System.out.println("[ChefCompatRestart] Handing off to " + NEXT + ".");
        GoldClaimMailboxRestart.main(args);
    }

    static void installUpdates(Path root, byte[] payload) throws Exception {
        install(root, payload);
        GoldClaimMailboxRestart.install(root, GoldClaimMailboxRestart.payload());
    }

    static void install(Path root, byte[] payload) throws Exception {
        if (!digest(payload).equals(COMPAT_SHA256)) {
            throw new IllegalStateException("Corrupt Chef compatibility payload.");
        }
        Path mods = root.resolve("mods");
        try (FileChannel channel = FileChannel.open(root.resolve(Path.of("world", "session.lock")),
                StandardOpenOption.WRITE);
             var lock = channel.tryLock()) {
            if (lock == null) throw new IllegalStateException("World is still running.");
            requireOnly(mods, "chefs-delight-", CHEF.getFileName().toString());
            requireOnly(mods, "farmersdelight-", FARMER.getFileName().toString());
            requireOnly(mods, "gangschefcompat-", COMPAT.getFileName().toString());
            check(root.resolve(CHEF), CHEF_SHA256);
            check(root.resolve(FARMER), FARMER_SHA256);
            Path target = root.resolve(COMPAT);
            if (Files.exists(target)) {
                check(target, COMPAT_SHA256);
                System.out.println("[ChefCompatRestart] Compatibility mod already installed and verified.");
                return;
            }
            Path temporary = Files.createTempFile(mods, "gangschefcompat-", ".tmp");
            try {
                Files.write(temporary, payload);
                check(temporary, COMPAT_SHA256);
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temporary);
            }
            check(target, COMPAT_SHA256);
            System.out.println("[ChefCompatRestart] Compatibility mod installed and verified.");
        }
    }

    static void requireOnly(Path mods, String prefix, String allowed) throws IOException {
        List<String> unexpected;
        try (var files = Files.list(mods)) {
            unexpected = files.map(path -> path.getFileName().toString())
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)
                            && name.toLowerCase(Locale.ROOT).endsWith(".jar") && !name.equals(allowed))
                    .toList();
        }
        if (!unexpected.isEmpty()) throw new IllegalStateException("Unexpected mod jars: " + unexpected);
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
        try (InputStream stream = ChefCompatRestart.class.getResourceAsStream(PAYLOAD)) {
            if (stream == null) throw new IOException("Missing Chef compatibility payload.");
            return stream.readAllBytes();
        }
    }
}
