import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

/** Regression tests for MimicreamRestart. Usage: java MimicreamRestartTest <live-fixture-dir> */
public final class MimicreamRestartTest {
    static Path fixtures;

    public static void main(String[] args) throws Exception {
        if (args.length == 3 && args[0].equals("--hold-lock")) {
            holdLock(Path.of(args[1]), Path.of(args[2]));
            return;
        }
        fixtures = Path.of(args[0]);
        freshInstallMatchesLiveLayout();
        secondRunIsNoOp();
        interruptedRunResumes();
        refusesChangedTick();
        refusesUnexpectedAddedFile();
        refusesMissingVoiceChat();
        refusesRunningWorld();
        System.out.println("MimicreamRestartTest passed");
    }

    static Path liveRoot() throws Exception {
        Path root = Files.createTempDirectory("mimicream-restart");
        copy("tick.live.mcfunction", root.resolve(MimicreamRestart.TICK));
        copy("overrides.live.json", root.resolve(MimicreamRestart.OVERRIDES));
        copy("voicechat.live.jar", root.resolve(MimicreamRestart.VOICE_JAR));
        Files.createDirectories(root.resolve(MimicreamRestart.VOICE_MARKER).getParent());
        Files.writeString(root.resolve(MimicreamRestart.VOICE_MARKER), "voice");
        Path unrelated = root.resolve(MimicreamRestart.DATA + "c/tags/items/glass_blocks.json");
        Files.createDirectories(unrelated.getParent());
        Files.writeString(unrelated, "{}");
        Files.createDirectories(root.resolve("world"));
        Files.writeString(root.resolve("world/session.lock"), "lock");
        return root;
    }

    static void copy(String fixture, Path target) throws Exception {
        Files.createDirectories(target.getParent());
        Files.copy(fixtures.resolve(fixture), target);
    }

    static void freshInstallMatchesLiveLayout() throws Exception {
        Path root = liveRoot();
        String before = Files.readString(root.resolve(MimicreamRestart.OVERRIDES));
        MimicreamRestart.install(root);
        verify(root);
        String after = Files.readString(root.resolve(MimicreamRestart.OVERRIDES));
        expect(!before.contains("\"alexsmobs:mimicream\""), "live fixture lacks the deny entry");
        expect(after.contains("\"alexsmobs:mimicream\": true"), "deny entry added");
        String newline = before.contains("\r\n") ? "\r\n" : "\n";
        List<String> beforeLines = List.of(before.split(newline, -1));
        List<String> afterLines = List.of(after.split(newline, -1));
        expect(afterLines.size() == beforeLines.size() + 1, "exactly one line added");
        int changed = 0;
        for (int i = 0, j = 0; i < beforeLines.size(); i++, j++) {
            if (afterLines.get(j).contains("\"alexsmobs:mimicream\"")) j++;
            if (!beforeLines.get(i).equals(afterLines.get(j))) changed++;
        }
        expect(changed <= 1, "only the previous last deny line gains a comma, changed " + changed);
        expect(Files.readString(root.resolve(MimicreamRestart.BACKUP).resolve(MimicreamRestart.OVERRIDES)).equals(before),
            "overrides backup is the original");
        expect(MimicreamRestart.hash(root.resolve(MimicreamRestart.BACKUP).resolve(MimicreamRestart.TICK))
            .equals(MimicreamRestart.hash(fixtures.resolve("tick.live.mcfunction"))), "tick backup is the original");
        expect(Files.readString(root.resolve(MimicreamRestart.TICK)).contains("clear @a alexsmobs:mimicream"),
            "tick clears Mimicream");
        expect(Files.readString(root.resolve(MimicreamRestart.DATA + "c/tags/items/glass_blocks.json")).equals("{}"),
            "unrelated datapack files untouched");
        delete(root);
    }

    static void secondRunIsNoOp() throws Exception {
        Path root = liveRoot();
        MimicreamRestart.install(root);
        String overrides = MimicreamRestart.hash(root.resolve(MimicreamRestart.OVERRIDES));
        MimicreamRestart.install(root);
        expect(overrides.equals(MimicreamRestart.hash(root.resolve(MimicreamRestart.OVERRIDES))), "rerun changes nothing");
        verify(root);
        delete(root);
    }

    static void interruptedRunResumes() throws Exception {
        Path root = liveRoot();
        Properties next = next();
        String first = MimicreamRestart.ADDED.get(0);
        try (var stream = MimicreamRestart.payload(first)) {
            Files.createDirectories(root.resolve(first).getParent());
            Files.write(root.resolve(first), stream.readAllBytes());
        }
        Files.createDirectories(root.resolve(MimicreamRestart.BACKUP).resolve(MimicreamRestart.TICK).getParent());
        Files.copy(root.resolve(MimicreamRestart.TICK), root.resolve(MimicreamRestart.BACKUP).resolve(MimicreamRestart.TICK));
        try (var stream = MimicreamRestart.payload(MimicreamRestart.TICK)) {
            Files.write(root.resolve(MimicreamRestart.TICK), stream.readAllBytes());
        }
        expect(MimicreamRestart.hash(root.resolve(first)).equals(next.getProperty(first)), "partial file staged");
        MimicreamRestart.install(root);
        verify(root);
        delete(root);
    }

    static void refusesChangedTick() throws Exception {
        Path root = liveRoot();
        Files.writeString(root.resolve(MimicreamRestart.TICK), "say changed\n");
        String overrides = MimicreamRestart.hash(root.resolve(MimicreamRestart.OVERRIDES));
        expectFailure(root, "Unexpected live tick function");
        expect(overrides.equals(MimicreamRestart.hash(root.resolve(MimicreamRestart.OVERRIDES))), "overrides untouched");
        for (String added : MimicreamRestart.ADDED) expect(!Files.exists(root.resolve(added)), "nothing added: " + added);
        delete(root);
    }

    static void refusesUnexpectedAddedFile() throws Exception {
        Path root = liveRoot();
        Path existing = root.resolve(MimicreamRestart.ADDED.get(4));
        Files.createDirectories(existing.getParent());
        Files.writeString(existing, "{\"values\":[]}");
        expectFailure(root, "Unexpected existing file");
        expect(MimicreamRestart.hash(root.resolve(MimicreamRestart.TICK))
            .equals(MimicreamRestart.hash(fixtures.resolve("tick.live.mcfunction"))), "tick untouched");
        delete(root);
    }

    static void refusesMissingVoiceChat() throws Exception {
        Path root = liveRoot();
        Files.delete(root.resolve(MimicreamRestart.VOICE_MARKER));
        expectFailure(root, "Voice chat update is not installed");
        delete(root);
    }

    static void refusesRunningWorld() throws Exception {
        Path root = liveRoot();
        Path ready = root.resolve("locked.ready");
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process holder = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"), "MimicreamRestartTest",
            "--hold-lock", root.resolve("world/session.lock").toString(), ready.toString()).inheritIO().start();
        try {
            for (int i = 0; i < 200 && !Files.exists(ready); i++) Thread.sleep(50);
            expect(Files.exists(ready), "lock holder started");
            expectFailure(root, "World is still running");
        } finally {
            holder.destroy();
            holder.waitFor();
        }
        expect(!Files.exists(root.resolve(MimicreamRestart.BACKUP)), "no backup while running");
        Files.delete(ready);
        delete(root);
    }

    static void holdLock(Path lockFile, Path ready) throws Exception {
        try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.WRITE);
             var lock = channel.lock()) {
            Files.writeString(ready, "locked");
            Thread.sleep(60_000);
        }
    }

    static Properties next() throws Exception {
        return MimicreamRestart.properties("files.properties");
    }

    static void verify(Path root) throws Exception {
        MimicreamRestart.verifyInstalled(root, next());
        expect(Files.exists(root.resolve(MimicreamRestart.BACKUP).resolve("APPLIED.txt")), "marker written");
    }

    static void expectFailure(Path root, String message) throws Exception {
        try {
            MimicreamRestart.install(root);
        } catch (IllegalStateException expected) {
            expect(expected.getMessage().contains(message), "expected '" + message + "', got " + expected.getMessage());
            expect(!Files.exists(root.resolve(MimicreamRestart.BACKUP).resolve("APPLIED.txt")), "no marker after refusal");
            return;
        }
        throw new AssertionError("Expected refusal: " + message);
    }

    static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static void delete(Path root) throws Exception {
        try (var files = Files.walk(root)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
        }
    }
}
