import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.Properties;

/** Regression tests for VoiceChatRestart. Usage: java VoiceChatRestartTest <live-server.properties> */
public final class VoiceChatRestartTest {
    static Path liveProperties;

    public static void main(String[] args) throws Exception {
        liveProperties = Path.of(args[0]);
        freshInstallOnLiveLayout();
        restartAfterModRewritesConfig();
        refusesQueryOnGamePort();
        refusesGeyser();
        refusesMissingClaimUpdate();
        refusesExistingConfig();
        refusesOtherVoiceJar();
        refusesRunningWorld();
        System.out.println("VoiceChatRestartTest passed");
    }

    static Path liveRoot() throws Exception {
        Path root = Files.createTempDirectory("voicechat-restart");
        Files.createDirectories(root.resolve("mods"));
        Files.createDirectories(root.resolve("world"));
        Files.writeString(root.resolve("world/session.lock"), "lock");
        Files.copy(liveProperties, root.resolve("server.properties"));
        Files.writeString(root.resolve("mods/goldclaim-1.0.64.jar"), "claim");
        Files.createDirectories(root.resolve(VoiceChatRestart.CLAIM_MARKER).getParent());
        Files.writeString(root.resolve(VoiceChatRestart.CLAIM_MARKER), "applied");
        return root;
    }

    static void freshInstallOnLiveLayout() throws Exception {
        Path root = liveRoot();
        VoiceChatRestart.install(root);
        verify(root);
        String config = Files.readString(root.resolve(VoiceChatRestart.CONFIG));
        for (String line : new String[] {"port=-1", "bind_address=", "voice_host=", "max_voice_distance=48.0",
                "enable_groups=true", "force_voice_chat=false"}) {
            expect(config.lines().anyMatch(l -> l.equals(line)), "config has " + line);
        }
        delete(root);
    }

    static void restartAfterModRewritesConfig() throws Exception {
        Path root = liveRoot();
        VoiceChatRestart.install(root);
        Files.writeString(root.resolve(VoiceChatRestart.CONFIG), "# rewritten by mod\nport=-1\n");
        VoiceChatRestart.install(root);
        expect(Files.readString(root.resolve(VoiceChatRestart.CONFIG)).contains("rewritten"), "later config kept");
        delete(root);
    }

    static void refusesQueryOnGamePort() throws Exception {
        Path root = liveRoot();
        String props = Files.readString(root.resolve("server.properties")).replace("enable-query=false", "enable-query=true");
        expect(props.contains("enable-query=true"), "fixture has query enabled");
        Files.writeString(root.resolve("server.properties"), props);
        expectRefusal(root, "Server query");
        delete(root);
    }

    static void refusesGeyser() throws Exception {
        Path root = liveRoot();
        Files.writeString(root.resolve("mods/Geyser-Fabric.jar"), "geyser");
        expectRefusal(root, "Geyser");
        delete(root);
    }

    static void refusesMissingClaimUpdate() throws Exception {
        Path root = liveRoot();
        Files.delete(root.resolve(VoiceChatRestart.CLAIM_MARKER));
        expectRefusal(root, "Claim trust update");
        delete(root);
    }

    static void refusesExistingConfig() throws Exception {
        Path root = liveRoot();
        Files.createDirectories(root.resolve(VoiceChatRestart.CONFIG).getParent());
        Files.writeString(root.resolve(VoiceChatRestart.CONFIG), "port=24454\n");
        expectRefusal(root, "Existing voice chat config");
        expect(Files.readString(root.resolve(VoiceChatRestart.CONFIG)).equals("port=24454\n"), "config untouched");
        delete(root);
    }

    static void refusesOtherVoiceJar() throws Exception {
        Path root = liveRoot();
        Files.writeString(root.resolve("mods/voicechat-fabric-1.20.1-2.6.0.jar"), "old");
        expectRefusal(root, "Unexpected active voice chat mod");
        delete(root);
    }

    static void refusesRunningWorld() throws Exception {
        Path root = liveRoot();
        try (FileChannel channel = FileChannel.open(root.resolve("world/session.lock"), StandardOpenOption.WRITE);
             var lock = channel.lock()) {
            boolean refused = false;
            try {
                VoiceChatRestart.install(root);
            } catch (Exception | Error expected) {
                refused = true;
            }
            expect(refused, "locked world refused");
        }
        expect(!Files.exists(root.resolve(VoiceChatRestart.JAR)), "running world untouched");
        delete(root);
    }

    static void expectRefusal(Path root, String message) throws Exception {
        try {
            VoiceChatRestart.install(root);
        } catch (IllegalStateException expected) {
            expect(expected.getMessage().contains(message), "refusal message: " + expected.getMessage());
            expect(!Files.exists(root.resolve(VoiceChatRestart.JAR)), "jar not installed on refusal");
            expect(!Files.exists(root.resolve(VoiceChatRestart.BACKUP).resolve("APPLIED.txt")), "no marker on refusal");
            return;
        }
        throw new AssertionError("Expected refusal: " + message);
    }

    static void verify(Path root) throws Exception {
        Properties next = VoiceChatRestart.properties("files.properties");
        VoiceChatRestart.check(root.resolve(VoiceChatRestart.JAR), next.getProperty(VoiceChatRestart.JAR));
        VoiceChatRestart.check(root.resolve(VoiceChatRestart.CONFIG), next.getProperty(VoiceChatRestart.CONFIG));
        expect(Files.exists(root.resolve(VoiceChatRestart.BACKUP).resolve("APPLIED.txt")), "marker written");
        expect(Files.exists(root.resolve("mods/goldclaim-1.0.64.jar")), "other mods untouched");
    }

    static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static void delete(Path root) throws Exception {
        try (var files = Files.walk(root)) {
            for (Path path : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }
}
