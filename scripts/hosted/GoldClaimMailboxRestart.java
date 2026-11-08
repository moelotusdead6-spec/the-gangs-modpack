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
import java.util.jar.JarFile;

/** Installs the mailbox and RSW map update, then continues the existing restart chain. */
public final class GoldClaimMailboxRestart {
    static final Path BACKUP = Path.of("backups", "goldclaim-mailbox-rswmap-restart-1.0.78");
    static final String PAYLOAD = "/mailbox-rswmap-payload/mods/goldclaim-1.0.71.jar";
    static final String OLD_68 = "mods/goldclaim-1.0.68.jar";
    static final String OLD_69 = "mods/goldclaim-1.0.69.jar";
    static final String OLD_70 = "mods/goldclaim-1.0.70.jar";
    static final String NEW_71 = "mods/goldclaim-1.0.71.jar";
    static final String OLD_68_SHA256 = "3c3c59e1f528a4fc699732190551ada24c9b05515a73ed92f05d4651a127f6ee";
    static final String OLD_69_SHA256 = "91b84e88fcfb8cabe5aef855b584c529cd81bd6e75bb0dc4a9430637f988e8f7";
    static final String OLD_70_SHA256 = "381022a2cf10c6a5de6de7368158e5181d24744833259a5f4764e022301a6352";
    static final String NEW_71_SHA256 = "3c1af3abe852511f2fd6a432959528c16383fbec077467c5d5a8617d7caa2697";
    static final String FABRIC_LAUNCHER_SHA256 = "6f921315fe2f570c331ca877d1e815c29b93fdc4ad04ce0f0f5cdf4300710028";
    static final String NEXT = "gangs-trials-botania-restart-1.0.74.jar";
    static final String NEXT_SHA256 = "bdac9539a5c8b0f4917ff97b56b39af349bf3549873fe8a69f54f81eba646c48";

    public static void main(String[] args) throws Throwable {
        Path root = Path.of(".").toAbsolutePath().normalize();
        check(root.resolve("fabric-server-launch.jar"), FABRIC_LAUNCHER_SHA256);
        try {
            install(root, payload());
        } catch (Exception failure) {
            System.out.println("[GoldClaimMailboxRestart] Mailbox/RSW map update NOT applied; starting normally. Reason: "
                + failure.getMessage());
        }
        Path next = root.resolve(NEXT);
        if (Files.exists(next) && hash(next).equals(NEXT_SHA256)) {
            System.out.println("[GoldClaimMailboxRestart] Handing off to " + NEXT + ".");
            invokeMain(Class.forName("TrialsBotaniaRestart"), args);
            return;
        }
        System.out.println("[GoldClaimMailboxRestart] Chained launcher missing or changed; starting original Fabric launcher.");
        String main;
        try (JarFile launcher = new JarFile(root.resolve("fabric-server-launch.jar").toFile())) {
            main = launcher.getManifest().getMainAttributes().getValue("Main-Class");
        }
        if (main == null || main.equals(GoldClaimMailboxRestart.class.getName())) {
            throw new IllegalStateException("Invalid original Fabric launcher.");
        }
        System.setProperty("java.class.path", root.resolve("fabric-server-launch.jar").toString());
        invokeMain(Class.forName(main), args);
    }

    static void invokeMain(Class<?> type, String[] args) throws Throwable {
        try {
            type.getMethod("main", String[].class).invoke(null, (Object)args);
        } catch (java.lang.reflect.InvocationTargetException failure) {
            throw failure.getCause();
        }
    }

    static void install(Path root, byte[] payload) throws Exception {
        if (!digest(payload).equals(NEW_71_SHA256)) throw new IllegalStateException("Corrupt GoldClaim 1.0.71 payload.");
        Path mods = root.resolve("mods");
        Path marker = root.resolve(BACKUP).resolve("APPLIED.txt");
        try (FileChannel channel = FileChannel.open(root.resolve(Path.of("world", "session.lock")), StandardOpenOption.WRITE);
             var lock = channel.tryLock()) {
            if (lock == null) throw new IllegalStateException("World is still running.");
            List<String> active = activeGoldClaims(mods);
            if (active.size() == 1 && active.get(0).equals(NEW_71)) {
                check(root.resolve(NEW_71), NEW_71_SHA256);
                verifyMarkerOrBackup(root, marker);
                System.out.println("[GoldClaimMailboxRestart] GoldClaim 1.0.71 already installed and verified.");
                return;
            }
            if (active.size() == 2 && active.contains(NEW_71)) {
                String originalName = active.get(0).equals(NEW_71) ? active.get(1) : active.get(0);
                String originalHash = oldHash(originalName);
                if (originalHash == null) throw new IllegalStateException("Unexpected active GoldClaim jars: " + active);
                check(root.resolve(NEW_71), NEW_71_SHA256);
                Path saved = root.resolve(BACKUP).resolve(originalName);
                check(saved, originalHash);
                check(root.resolve(originalName), originalHash);
                Files.delete(root.resolve(originalName));
                if (!Files.exists(marker)) Files.writeString(marker,
                    "GoldClaim 1.0.71 mailbox and RSW map update installed; backed up " + originalName + ".\n",
                    StandardOpenOption.CREATE_NEW);
                System.out.println("[GoldClaimMailboxRestart] Recovered an interrupted install and verified GoldClaim 1.0.71.");
                return;
            }
            if (active.size() != 1) throw new IllegalStateException("Unexpected active GoldClaim jars: " + active);
            String originalName = active.get(0);
            String originalHash = oldHash(originalName);
            if (originalHash == null) throw new IllegalStateException("Unexpected active GoldClaim jar: " + originalName);
            Path original = root.resolve(originalName);
            check(original, originalHash);
            Path saved = root.resolve(BACKUP).resolve(originalName);
            if (Files.exists(saved)) check(saved, originalHash);
            else {
                Files.createDirectories(saved.getParent());
                Files.copy(original, saved);
                check(saved, originalHash);
            }

            Path target = root.resolve(NEW_71);
            Path temporary = Files.createTempFile(mods, "goldclaim-1.0.71-", ".tmp");
            try {
                Files.write(temporary, payload);
                check(temporary, NEW_71_SHA256);
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
            check(target, NEW_71_SHA256);
            Files.delete(original);
            Files.writeString(marker, "GoldClaim 1.0.71 mailbox and RSW map update installed; backed up " + originalName + ".\n",
                StandardOpenOption.CREATE_NEW);
            System.out.println("[GoldClaimMailboxRestart] GoldClaim 1.0.71 installed; original saved in " + BACKUP + ".");
        }
    }

    static String oldHash(String name) {
        return switch (name) {
            case OLD_68 -> OLD_68_SHA256;
            case OLD_69 -> OLD_69_SHA256;
            case OLD_70 -> OLD_70_SHA256;
            default -> null;
        };
    }

    static List<String> activeGoldClaims(Path mods) throws IOException {
        List<String> jars;
        try (var files = Files.list(mods)) {
            jars = files.map(file -> file.getFileName().toString())
                .filter(name -> name.startsWith("goldclaim-") && name.endsWith(".jar"))
                .map(name -> "mods/" + name).toList();
        }
        return jars;
    }

    static void verifyMarkerOrBackup(Path root, Path marker) throws Exception {
        if (!Files.exists(marker)) return;
        String content = Files.readString(marker);
        String original = content.contains(OLD_68) ? OLD_68
            : content.contains(OLD_69) ? OLD_69 : content.contains(OLD_70) ? OLD_70 : null;
        if (original == null) throw new IllegalStateException("Invalid mailbox update marker.");
        check(root.resolve(BACKUP).resolve(original), oldHash(original));
    }

    static byte[] payload() throws IOException {
        try (InputStream stream = GoldClaimMailboxRestart.class.getResourceAsStream(PAYLOAD)) {
            if (stream == null) throw new IOException("Missing bundled GoldClaim 1.0.71.");
            return stream.readAllBytes();
        }
    }

    static void check(Path path, String expected) throws Exception {
        if (!Files.exists(path) || !hash(path).equals(expected)) {
            throw new IllegalStateException("Missing file or unexpected SHA-256: " + path);
        }
    }

    static String hash(Path path) throws Exception {
        return digest(Files.readAllBytes(path));
    }

    static String digest(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
