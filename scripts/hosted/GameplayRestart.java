import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarFile;

public final class GameplayRestart {
    static final Path BACKUP = Path.of("backups/gameplay-restart-1.0.62");
    static final List<String> OLD_MODS = List.of(
        "mods/goldclaim-1.0.61.jar", "mods/bclib-3.0.14.jar", "mods/better-end-4.0.11.jar");
    static final List<String> NEW_MODS = List.of(
        "mods/goldclaim-1.0.62.jar", "mods/bclib-3.0.16.jar", "mods/better-end-4.0.12.jar");
    static final String CONFIG = "config/fiw-mods-api/config.json";

    public static void main(String[] args) throws Throwable {
        install(Path.of(".").toAbsolutePath().normalize());
        String main;
        try (JarFile launcher = new JarFile("fabric-server-launch.jar")) {
            main = launcher.getManifest().getMainAttributes().getValue("Main-Class");
        }
        if (main == null || main.equals(GameplayRestart.class.getName())) {
            throw new IllegalStateException("Invalid original Fabric launcher.");
        }
        System.setProperty("java.class.path", Path.of("fabric-server-launch.jar").toAbsolutePath().toString());
        System.out.println("[GameplayRestart] Starting original Fabric launcher.");
        try {
            Class.forName(main).getMethod("main", String[].class).invoke(null, (Object)args);
        } catch (InvocationTargetException failure) {
            throw failure.getCause();
        }
    }

    static void install(Path root) throws Exception {
        Properties previous = properties("previous.properties");
        Properties next = properties("files.properties");
        if (previous.size() != 4 || next.size() != 4
                || !previous.stringPropertyNames().containsAll(OLD_MODS) || !previous.containsKey(CONFIG)
                || !next.stringPropertyNames().containsAll(NEW_MODS) || !next.containsKey(CONFIG)) {
            throw new IllegalStateException("Incomplete gameplay restart manifest.");
        }
        check(root.resolve("fabric-server-launch.jar"),
            "6f921315fe2f570c331ca877d1e815c29b93fdc4ad04ce0f0f5cdf4300710028");
        for (String name : next.stringPropertyNames()) {
            try (InputStream stream = payload(name)) {
                if (!digest(stream).equals(next.getProperty(name))) {
                    throw new IllegalStateException("Corrupt payload: " + name);
                }
            }
        }
        try (FileChannel channel = FileChannel.open(root.resolve("world/session.lock"), StandardOpenOption.WRITE);
             var lock = channel.tryLock()) {
            if (lock == null) throw new IllegalStateException("World is still running; no update applied.");
            Path backup = root.resolve(BACKUP);
            boolean applied = Files.exists(backup.resolve("APPLIED.txt"));
            try (var files = Files.list(root.resolve("mods"))) {
                for (Path file : files.toList()) {
                    String name = file.getFileName().toString();
                    if (name.matches("(goldclaim|bclib|better-end)-.*\\.jar")
                            && !OLD_MODS.contains("mods/" + name) && !NEW_MODS.contains("mods/" + name)) {
                        throw new IllegalStateException("Unexpected active mod: " + name);
                    }
                }
            }
            // Validate every input before writing anything, including interrupted-install recovery.
            for (int i = 0; i < OLD_MODS.size(); i++) {
                String old = OLD_MODS.get(i);
                String replacement = NEW_MODS.get(i);
                Path source = root.resolve(old);
                Path saved = backup.resolve(old);
                if (Files.exists(source)) {
                    if (applied) throw new IllegalStateException("Old mod reappeared: " + old);
                    check(source, previous.getProperty(old));
                } else {
                    check(saved, previous.getProperty(old));
                }
                if (Files.exists(saved)) check(saved, previous.getProperty(old));
                if (Files.exists(root.resolve(replacement))) check(root.resolve(replacement), next.getProperty(replacement));
                else if (applied) throw new IllegalStateException("Installed mod missing: " + replacement);
            }
            String configHash = hash(root.resolve(CONFIG));
            if (!configHash.equals(next.getProperty(CONFIG))
                    && (applied || !configHash.equals(previous.getProperty(CONFIG)))) {
                throw new IllegalStateException("Fiw configuration changed since staging; restage.");
            }
            Path savedConfig = backup.resolve(CONFIG);
            if (Files.exists(savedConfig)) check(savedConfig, previous.getProperty(CONFIG));
            else if (configHash.equals(next.getProperty(CONFIG))) {
                throw new IllegalStateException("Updated Fiw configuration has no original backup.");
            }
            if (applied) {
                System.out.println("[GameplayRestart] Installed gameplay update verified.");
                return;
            }
            Files.createDirectories(savedConfig.getParent());
            if (!Files.exists(savedConfig)) Files.copy(root.resolve(CONFIG), savedConfig);
            for (int i = 0; i < OLD_MODS.size(); i++) {
                String old = OLD_MODS.get(i);
                Path source = root.resolve(old);
                Path saved = backup.resolve(old);
                Files.createDirectories(saved.getParent());
                if (Files.exists(source)) {
                    if (!Files.exists(saved)) Files.copy(source, saved);
                    check(saved, previous.getProperty(old));
                }
                write(root, NEW_MODS.get(i), next.getProperty(NEW_MODS.get(i)));
                Files.deleteIfExists(source);
            }
            write(root, CONFIG, next.getProperty(CONFIG));
            Files.writeString(backup.resolve("APPLIED.txt"),
                "GoldClaim 1.0.62; BCLib 3.0.16; Better End 4.0.12; two UUID-scoped Fiw bypasses.\n",
                StandardOpenOption.CREATE_NEW);
            System.out.println("[GameplayRestart] Update installed; originals saved in " + BACKUP);
        }
    }

    private static void write(Path root, String file, String expected) throws Exception {
        Path target = root.resolve(file);
        if (Files.exists(target) && hash(target).equals(expected)) return;
        Path pending = target.resolveSibling(target.getFileName() + ".gameplay-pending");
        try (InputStream stream = payload(file)) {
            Files.copy(stream, pending, StandardCopyOption.REPLACE_EXISTING);
        }
        check(pending, expected);
        Files.move(pending, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        check(target, expected);
    }

    static Properties properties(String name) throws Exception {
        Properties result = new Properties();
        try (InputStream stream = payload(name)) { result.load(stream); }
        return result;
    }

    static InputStream payload(String file) {
        InputStream stream = GameplayRestart.class.getResourceAsStream("/payload/" + file);
        if (stream == null) throw new IllegalStateException("Missing payload: " + file);
        return stream;
    }

    static String hash(Path file) throws Exception {
        try (InputStream stream = Files.newInputStream(file)) { return digest(stream); }
    }

    private static String digest(InputStream stream) throws Exception {
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[65536];
        for (int read; (read = stream.read(buffer)) != -1;) hash.update(buffer, 0, read);
        return HexFormat.of().formatHex(hash.digest());
    }

    private static void check(Path file, String expected) throws Exception {
        if (!Files.isRegularFile(file) || !hash(file).equals(expected)) {
            throw new IllegalStateException("File missing or changed since staging: " + file);
        }
    }
}
