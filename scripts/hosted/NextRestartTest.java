import java.net.URLClassLoader;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.jar.JarFile;

public final class NextRestartTest {
    private static Path repo;
    private static Path fabric;
    private static Path tests;
    private static final Path BACKUP = Path.of("backups", "next-restart-goldclaim-1.0.49");

    public static void main(String[] args) throws Exception {
        repo = Path.of(args[0]);
        fabric = Path.of(args[1]);
        tests = Files.createTempDirectory(repo.resolve("tmp"), "hosted-next-restart-test-");
        Path install = fixture("install");
        byte[] originalPermissions = Files.readAllBytes(install.resolve("mods/luckperms/luckperms-h2-v2.mv.db"));
        NextRestart.install(install);
        assertInstalled(install);
        require(Arrays.equals(originalPermissions, Files.readAllBytes(install.resolve(BACKUP).resolve("luckperms-h2-v2.mv.db"))),
            "LuckPerms was not backed up exactly");
        Files.writeString(install.resolve("mods/luckperms/luckperms-h2-v2.mv.db"), "changed by migration");
        NextRestart.install(install);
        require(Arrays.equals(originalPermissions, Files.readAllBytes(install.resolve(BACKUP).resolve("luckperms-h2-v2.mv.db"))),
            "Repeated launch overwrote pre-migration backup");
        Files.delete(install.resolve(BACKUP).resolve("APPLIED.txt"));
        Files.delete(install.resolve("mods/goldclaim-1.0.49.jar"));
        NextRestart.install(install);
        assertInstalled(install);
        Path corrupt = fixture("corrupt");
        Files.writeString(corrupt.resolve("mods/goldclaim-1.0.48.jar"), "corrupt");
        expectFailure(corrupt, "SHA-256 mismatch");
        require(!Files.exists(corrupt.resolve(BACKUP)), "Corrupt preflight changed files");
        Path duplicate = fixture("duplicate");
        Files.writeString(duplicate.resolve("mods/goldclaim-0.0.0.jar"), "unexpected");
        expectFailure(duplicate, "Unexpected installed jar");
        require(!Files.exists(duplicate.resolve(BACKUP)), "Duplicate preflight changed files");
        Path locked = fixture("locked");
        try (FileChannel channel = FileChannel.open(locked.resolve("world/session.lock"), StandardOpenOption.WRITE);
             var lock = channel.lock()) {
            expectFailure(locked, null);
            require(!Files.exists(locked.resolve(BACKUP)), "Locked server changed files");
        }
        Path missing = fixture("missing-permissions");
        Files.delete(missing.resolve("mods/luckperms/luckperms-h2-v2.mv.db"));
        expectFailure(missing, "Backup source missing");
        require(Files.exists(missing.resolve("mods/goldclaim-1.0.48.jar")), "Missing backup source replaced active mod");
        Path changedShop = fixture("changed-shop");
        Files.writeString(changedShop.resolve("config/gangshop/overrides.json"), "{}");
        expectFailure(changedShop, "Shop overrides changed since staging");
        require(!Files.exists(changedShop.resolve(BACKUP)), "Changed shop preflight modified files");
        Path changedOptimizer = fixture("changed-optimizer");
        Files.writeString(changedOptimizer.resolve("mods/teleportoptimizer-1.0.0-obfuscated.jar"), "changed");
        expectFailure(changedOptimizer, "SHA-256 mismatch");
        require(!Files.exists(changedOptimizer.resolve(BACKUP)), "Optimizer preflight modified files");
        Path disabledOptimizer = fixture("disabled-optimizer");
        Path optimizerJar = disabledOptimizer.resolve("mods/teleportoptimizer-1.0.0-obfuscated.jar");
        Files.move(optimizerJar, optimizerJar.resolveSibling(optimizerJar.getFileName() + ".disabled"));
        expectFailure(disabledOptimizer, "Teleport Optimizer must remain enabled");
        require(!Files.exists(disabledOptimizer.resolve(BACKUP)), "Disabled optimizer preflight modified files");
        Path changedSpawn = fixture("changed-spawn");
        Files.writeString(changedSpawn.resolve("mods/spawnanimations-v1.11.5-mc1.17-26.2.9-mod.jar"), "changed");
        expectFailure(changedSpawn, "SHA-256 mismatch");
        require(!Files.exists(changedSpawn.resolve(BACKUP)), "Spawn preflight modified files");
        Path conflictingPause = fixture("conflicting-pause");
        Files.createDirectories(conflictingPause.resolve("config/paxi/datapacks"));
        Files.writeString(conflictingPause.resolve("config/paxi/datapacks/gangs-performance-pause-1.0.0.zip"), "different");
        expectFailure(conflictingPause, "SHA-256 mismatch");
        require(!Files.exists(conflictingPause.resolve(BACKUP)), "Pause preflight modified files");
        Path interrupted = fixture("interrupted-performance");
        NextRestart.install(interrupted);
        Files.delete(interrupted.resolve(BACKUP).resolve("APPLIED.txt"));
        Files.delete(interrupted.resolve("config/paxi/datapacks/gangs-performance-pause-1.0.0.zip"));
        NextRestart.install(interrupted);
        assertInstalled(interrupted);
        Path probe = fixture("classpath");
        Path launcher = probe.resolve("gangs-next-restart-1.0.49.jar");
        Files.copy(repo.resolve("artifacts/gangs-next-restart-1.0.49.jar"), launcher);
        try (var jar = new JarFile(launcher.toFile())) {
            require("fabric-server-launch.jar".equals(jar.getManifest().getMainAttributes().getValue("Class-Path")),
                "Wrapper manifest lost original launcher");
        }
        Path libraries = probe.resolve("libraries/net/fabricmc/fabric-loader/0.19.3");
        Files.createDirectories(libraries);
        Files.copy(fabric.resolve("libraries/net/fabricmc/fabric-loader/0.19.3/fabric-loader-0.19.3.jar"),
            libraries.resolve("fabric-loader-0.19.3.jar"));
        try (var jar = new JarFile(probe.resolve("fabric-server-launch.jar").toFile());
             var loader = new URLClassLoader(new java.net.URL[]{launcher.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            String main = jar.getManifest().getMainAttributes().getValue("Main-Class");
            Class.forName(main, false, loader).getMethod("main", String[].class);
        }
        System.out.println("PASS: install, exact permission backup, idempotence, interrupted-install recovery, corrupt/duplicate rejection, live-world lock, missing-backup refusal, real Fabric manifest/classpath delegation.");
        System.out.println("Disposable test directory: " + tests);
    }

    private static Path fixture(String name) throws Exception {
        Path root = tests.resolve(name);
        Files.createDirectories(root.resolve("mods/luckperms"));
        Files.createDirectories(root.resolve("config/luckperms"));
        Files.createDirectories(root.resolve("config/gangshop"));
        Files.copy(repo.resolve("tmp/oct22-original-overrides.json"), root.resolve("config/gangshop/overrides.json"));
        Files.createDirectories(root.resolve("world"));
        Files.writeString(root.resolve("world/session.lock"), "test");
        Files.writeString(root.resolve("mods/luckperms/luckperms-h2-v2.mv.db"), "test permission data");
        Files.writeString(root.resolve("config/luckperms/luckperms.conf"), "storage-method = \"h2\"");
        Files.copy(fabric.resolve("fabric-server-launch.jar"), root.resolve("fabric-server-launch.jar"));
        for (String jar : new String[]{"goldclaim-1.0.48.jar", "gangsales-1.0.0.jar", "rankbadges-1.0.3.jar", "Chunky-1.3.146.jar",
                "teleportoptimizer-1.0.0-obfuscated.jar", "spawnanimations-v1.11.5-mc1.17-26.2.9-mod.jar"}) {
            Files.copy(repo.resolve("artifacts").resolve(jar), root.resolve("mods").resolve(jar));
        }
        return root;
    }

    private static void assertInstalled(Path root) throws Exception {
        require(!Files.exists(root.resolve("mods/goldclaim-1.0.48.jar")), "Old GoldClaim remains");
        require(!Files.exists(root.resolve("mods/gangsales-1.0.0.jar")), "Old GangSales remains");
        String optimizer = "teleportoptimizer-1.0.0-obfuscated.jar";
        require(!Files.exists(root.resolve("mods").resolve(optimizer + ".disabled")), "Teleport Optimizer was disabled");
        require(Arrays.equals(Files.readAllBytes(repo.resolve("artifacts").resolve(optimizer)),
            Files.readAllBytes(root.resolve("mods").resolve(optimizer))), "Active optimizer changed");
        require(!Files.exists(root.resolve(BACKUP).resolve(optimizer)), "Unchanged optimizer unnecessarily backed up");
        String spawn = "spawnanimations-v1.11.5-mc1.17-26.2.9-mod.jar";
        require(Arrays.equals(Files.readAllBytes(repo.resolve("artifacts").resolve(spawn)),
            Files.readAllBytes(root.resolve("mods").resolve(spawn))), "Spawn Animations mod was changed/removed");
        require(Arrays.equals(Files.readAllBytes(repo.resolve("artifacts/gangs-performance-pause-1.0.0.zip")),
            Files.readAllBytes(root.resolve("config/paxi/datapacks/gangs-performance-pause-1.0.0.zip"))), "Pause pack differs");
        require(Arrays.equals(Files.readAllBytes(repo.resolve("config/gangshop/overrides.json")),
            Files.readAllBytes(root.resolve("config/gangshop/overrides.json"))), "Shop exclusions differ");
        for (String name : new String[]{"goldclaim-1.0.49.jar", "gangsales-1.0.1.jar", "rankbadges-1.0.3.jar"}) {
            require(Arrays.equals(Files.readAllBytes(repo.resolve("artifacts").resolve(name)),
                Files.readAllBytes(root.resolve("mods").resolve(name))), "Payload differs: " + name);
        }
        require(Files.exists(root.resolve(BACKUP).resolve("APPLIED.txt")), "Completion marker missing");
    }

    private static void expectFailure(Path root, String message) throws Exception {
        try {
            NextRestart.install(root);
        } catch (Exception failure) {
            if (message != null) require(failure.getMessage() != null && failure.getMessage().contains(message),
                "Unexpected failure: " + failure);
            return;
        }
        throw new AssertionError("Unsafe install unexpectedly succeeded");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
