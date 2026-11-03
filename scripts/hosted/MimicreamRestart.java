import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarFile;

/**
 * Restart launcher that removes Mimicream and stops Gobber's Ring of Repair from repairing Gang Boots.
 * While the world is not running it adds the gangs_fixes datapack files, replaces the pinned tick
 * function and merges one Gang Shop deny entry, then starts the original Fabric launcher. It replaces
 * gangs-voicechat-restart-1.0.66.jar, whose only remaining job (verifying voice chat) it keeps.
 * If any check fails nothing is changed and the server still starts normally.
 */
public final class MimicreamRestart {
    static final Path BACKUP = Path.of("backups/mimicream-restart-1.0.67");
    static final String FABRIC_LAUNCHER_SHA256 = "6f921315fe2f570c331ca877d1e815c29b93fdc4ad04ce0f0f5cdf4300710028";
    static final String VOICE_MARKER = "backups/voicechat-restart-1.0.66/APPLIED.txt";
    static final String VOICE_JAR = "mods/voicechat-fabric-1.20.1-2.6.25.jar";
    static final String VOICE_JAR_SHA256 = "314765c54a4967a971836469c8ba0a31ff0718f418d54bb770fef8a97f3f1036";
    static final String DATA = "config/paxi/datapacks/gangs_fixes/data/";
    static final String TICK = DATA + "gangs_fixes/functions/tick.mcfunction";
    static final List<String> ADDED = List.of(
        DATA + "alexsmobs/loot_tables/entities/mimicube.json",
        DATA + "alexsmobs/loot_tables/gameplay/transmutation_table_rare.json",
        DATA + "alexsmobs/recipes/hemolymph_blaster.json",
        DATA + "alexsmobs/recipes/mimicream_repair_recipe.json",
        DATA + "gobber2/tags/items/ring_repair_blacklist.json");
    static final String OVERRIDES = "config/gangshop/overrides.json";
    static final String DENY = "alexsmobs:mimicream";
    static final String APPLIED = "Mimicream removed; Gang Boots blacklisted from Ring of Repair; Mimicream shop deny.\n";

    public static void main(String[] args) throws Throwable {
        Path root = Path.of(".").toAbsolutePath().normalize();
        check(root.resolve("fabric-server-launch.jar"), FABRIC_LAUNCHER_SHA256);
        try {
            install(root);
        } catch (Exception failure) {
            System.out.println("[MimicreamRestart] Update NOT applied; starting normally. Reason: " + failure.getMessage());
        }
        String main;
        try (JarFile launcher = new JarFile("fabric-server-launch.jar")) {
            main = launcher.getManifest().getMainAttributes().getValue("Main-Class");
        }
        if (main == null || main.equals(MimicreamRestart.class.getName())) {
            throw new IllegalStateException("Invalid original Fabric launcher.");
        }
        System.setProperty("java.class.path", Path.of("fabric-server-launch.jar").toAbsolutePath().toString());
        System.out.println("[MimicreamRestart] Starting original Fabric launcher.");
        try {
            Class.forName(main).getMethod("main", String[].class).invoke(null, (Object)args);
        } catch (java.lang.reflect.InvocationTargetException failure) {
            throw failure.getCause();
        }
    }

    static void install(Path root) throws Exception {
        Properties previous = properties("previous.properties");
        Properties next = properties("files.properties");
        if (!previous.containsKey(TICK)) throw new IllegalStateException("Incomplete restart manifest: " + TICK);
        for (String key : targets()) {
            if (!next.containsKey(key)) throw new IllegalStateException("Incomplete restart manifest: " + key);
            try (InputStream stream = payload(key)) {
                if (!digest(stream.readAllBytes()).equals(next.getProperty(key))) {
                    throw new IllegalStateException("Corrupt payload: " + key);
                }
            }
        }
        try (FileChannel channel = FileChannel.open(root.resolve("world/session.lock"), StandardOpenOption.WRITE);
             var lock = channel.tryLock()) {
            if (lock == null) throw new IllegalStateException("World is still running.");
            if (!Files.exists(root.resolve(VOICE_MARKER))) throw new IllegalStateException("Voice chat update is not installed.");
            check(root.resolve(VOICE_JAR), VOICE_JAR_SHA256);
            Path backup = root.resolve(BACKUP);
            Path marker = backup.resolve("APPLIED.txt");
            if (Files.exists(marker)) {
                verifyInstalled(root, next);
                System.out.println("[MimicreamRestart] Update already installed and verified.");
                return;
            }

            // Validate everything before changing anything; a partial earlier run is resumable.
            for (String target : ADDED) {
                Path file = root.resolve(target);
                if (Files.exists(file) && !hash(file).equals(next.getProperty(target))) {
                    throw new IllegalStateException("Unexpected existing file: " + target);
                }
            }
            Path tick = root.resolve(TICK);
            String tickHash = Files.exists(tick) ? hash(tick) : "";
            if (!tickHash.equals(previous.getProperty(TICK)) && !tickHash.equals(next.getProperty(TICK))) {
                throw new IllegalStateException("Unexpected live tick function: " + TICK);
            }
            Path overrides = root.resolve(OVERRIDES);
            String overridesText = decode(Files.readAllBytes(overrides));
            String mergedOverrides = addDenyEntries(overridesText, List.of(DENY));

            Files.createDirectories(backup);
            Path savedTick = backup.resolve(TICK);
            if (tickHash.equals(previous.getProperty(TICK)) && !Files.exists(savedTick)) {
                Files.createDirectories(savedTick.getParent());
                Files.copy(tick, savedTick);
            }
            check(savedTick, previous.getProperty(TICK));
            Path savedOverrides = backup.resolve(OVERRIDES);
            if (!Files.exists(savedOverrides)) {
                Files.createDirectories(savedOverrides.getParent());
                Files.copy(overrides, savedOverrides);
            }

            for (String target : targets()) {
                Path file = root.resolve(target);
                if (!Files.exists(file) || !hash(file).equals(next.getProperty(target))) {
                    try (InputStream stream = payload(target)) {
                        write(file, stream.readAllBytes(), next.getProperty(target));
                    }
                }
            }
            if (!mergedOverrides.equals(overridesText)) {
                byte[] bytes = mergedOverrides.getBytes(StandardCharsets.UTF_8);
                write(overrides, bytes, digest(bytes));
            }
            verifyInstalled(root, next);
            Files.writeString(marker, APPLIED + "overrides_backup_sha256=" + hash(savedOverrides) + "\n",
                StandardOpenOption.CREATE_NEW);
            System.out.println("[MimicreamRestart] Update installed; originals saved in " + BACKUP);
        }
    }

    static List<String> targets() {
        List<String> all = new ArrayList<>(ADDED);
        all.add(TICK);
        return all;
    }

    static void verifyInstalled(Path root, Properties next) throws Exception {
        for (String target : targets()) check(root.resolve(target), next.getProperty(target));
        String overrides = decode(Files.readAllBytes(root.resolve(OVERRIDES)));
        if (!objectKeys(overrides, findTopLevelValue(overrides, "denyItems", '{')).contains(DENY)) {
            throw new IllegalStateException("Missing shop deny entry: " + DENY);
        }
    }

    /** Adds missing {@code "key": true} entries to the top-level denyItems object, preserving the rest of the file. */
    static String addDenyEntries(String json, List<String> deny) {
        if (json.startsWith("\uFEFF")) return "\uFEFF" + addDenyEntries(json.substring(1), deny);
        int open = findTopLevelValue(json, "denyItems", '{');
        Set<String> existing = objectKeys(json, open);
        List<String> missing = new ArrayList<>();
        for (String key : deny) if (!existing.contains(key)) missing.add(key);
        if (missing.isEmpty()) return json;
        int close = skipValue(json, open) - 1;
        int contentEnd = close;
        while (contentEnd > open + 1 && Character.isWhitespace(json.charAt(contentEnd - 1))) contentEnd--;
        boolean empty = contentEnd == open + 1;
        String newline = json.contains("\r\n") ? "\r\n" : "\n";
        int closeLine = json.lastIndexOf('\n', close - 1) + 1;
        String closeIndent = json.substring(closeLine, close);
        if (!closeIndent.chars().allMatch(ch -> ch == ' ' || ch == '\t')) closeIndent = "";
        String entryIndent = closeIndent + "  ";
        if (!empty) {
            int lastLine = json.lastIndexOf('\n', contentEnd - 1) + 1;
            int first = lastLine;
            while (first < contentEnd && (json.charAt(first) == ' ' || json.charAt(first) == '\t')) first++;
            if (first > lastLine) entryIndent = json.substring(lastLine, first);
        }
        StringBuilder addition = new StringBuilder();
        for (int i = 0; i < missing.size(); i++) {
            if (!empty || i > 0) addition.append(',');
            addition.append(newline).append(entryIndent).append('"').append(missing.get(i)).append("\": true");
        }
        String merged = json.substring(0, contentEnd) + addition + newline + closeIndent + json.substring(close);
        findTopLevelValue(merged, "denyItems", '{');
        return merged;
    }

    static int findTopLevelValue(String json, String name, char type) {
        int index = skipWhitespace(json, json.startsWith("\uFEFF") ? 1 : 0);
        if (index >= json.length() || json.charAt(index) != '{') {
            throw new IllegalStateException("Shop overrides are not a JSON object.");
        }
        int found = -1;
        index = skipWhitespace(json, index + 1);
        if (index < json.length() && json.charAt(index) == '}') {
            index++;
        } else {
            while (true) {
                int keyEnd = stringEnd(json, index);
                String key = json.substring(index + 1, keyEnd - 1);
                index = skipWhitespace(json, keyEnd);
                if (index >= json.length() || json.charAt(index++) != ':') {
                    throw new IllegalStateException("Malformed shop overrides key.");
                }
                index = skipWhitespace(json, index);
                if (key.equals(name)) {
                    if (found != -1) throw new IllegalStateException("Duplicate " + name + " in shop overrides.");
                    if (index >= json.length() || json.charAt(index) != type) {
                        throw new IllegalStateException(name + " has an unexpected type.");
                    }
                    found = index;
                }
                index = skipWhitespace(json, skipValue(json, index));
                if (index < json.length() && json.charAt(index) == ',') {
                    index = skipWhitespace(json, index + 1);
                } else if (index < json.length() && json.charAt(index) == '}') {
                    index++;
                    break;
                } else {
                    throw new IllegalStateException("Malformed shop overrides object.");
                }
            }
        }
        if (skipWhitespace(json, index) != json.length()) {
            throw new IllegalStateException("Unexpected content after shop overrides.");
        }
        if (found == -1) throw new IllegalStateException("Shop overrides have no " + name + ".");
        return found;
    }

    static Set<String> objectKeys(String json, int open) {
        Set<String> keys = new LinkedHashSet<>();
        int index = skipWhitespace(json, open + 1);
        if (json.charAt(index) == '}') return keys;
        while (true) {
            int keyEnd = stringEnd(json, index);
            String key = json.substring(index + 1, keyEnd - 1);
            if (key.indexOf('\\') >= 0) throw new IllegalStateException("Escaped shop deny keys are not supported.");
            keys.add(key);
            index = skipWhitespace(json, keyEnd);
            if (json.charAt(index++) != ':') throw new IllegalStateException("Malformed denyItems.");
            index = skipWhitespace(json, skipValue(json, skipWhitespace(json, index)));
            if (json.charAt(index) == '}') return keys;
            if (json.charAt(index++) != ',') throw new IllegalStateException("Malformed denyItems.");
            index = skipWhitespace(json, index);
        }
    }

    private static int skipValue(String json, int index) {
        if (index >= json.length()) throw new IllegalStateException("Missing JSON value.");
        char first = json.charAt(index);
        if (first == '"') return stringEnd(json, index);
        if (first == '{') return skipContainer(json, index, '}', true);
        if (first == '[') return skipContainer(json, index, ']', false);
        if (json.startsWith("true", index)) return index + 4;
        if (json.startsWith("false", index)) return index + 5;
        if (json.startsWith("null", index)) return index + 4;
        if (first == '-' || first >= '0' && first <= '9') {
            int end = index + 1;
            while (end < json.length() && "0123456789+-.eE".indexOf(json.charAt(end)) >= 0) end++;
            return end;
        }
        throw new IllegalStateException("Malformed JSON value.");
    }

    private static int skipContainer(String json, int index, char close, boolean object) {
        index = skipWhitespace(json, index + 1);
        if (index < json.length() && json.charAt(index) == close) return index + 1;
        while (true) {
            if (object) {
                index = skipWhitespace(json, stringEnd(json, index));
                if (index >= json.length() || json.charAt(index++) != ':') {
                    throw new IllegalStateException("Malformed JSON object.");
                }
                index = skipWhitespace(json, index);
            }
            index = skipWhitespace(json, skipValue(json, index));
            if (index < json.length() && json.charAt(index) == close) return index + 1;
            if (index >= json.length() || json.charAt(index++) != ',') {
                throw new IllegalStateException("Malformed JSON container.");
            }
            index = skipWhitespace(json, index);
        }
    }

    private static int stringEnd(String json, int index) {
        if (index >= json.length() || json.charAt(index) != '"') throw new IllegalStateException("Expected JSON string.");
        for (int i = index + 1; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (ch == '\\') i++;
            else if (ch == '"') return i + 1;
        }
        throw new IllegalStateException("Unterminated JSON string.");
    }

    private static int skipWhitespace(String json, int index) {
        while (index < json.length() && Character.isWhitespace(json.charAt(index))) index++;
        return index;
    }

    static String decode(byte[] bytes) throws Exception {
        return StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString();
    }

    static void write(Path destination, byte[] bytes, String expected) throws Exception {
        if (!digest(bytes).equals(expected)) throw new IllegalStateException("Unexpected bytes for " + destination);
        Files.createDirectories(destination.getParent());
        Path temporary = destination.resolveSibling(destination.getFileName() + ".mimicream-tmp");
        Files.write(temporary, bytes);
        try {
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
        }
        check(destination, expected);
    }

    static Properties properties(String name) throws Exception {
        Properties result = new Properties();
        try (InputStream stream = payload(name)) {
            result.load(new java.io.InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        return result;
    }

    static InputStream payload(String name) {
        InputStream stream = MimicreamRestart.class.getResourceAsStream("/payload/" + name);
        if (stream == null) throw new IllegalStateException("Missing payload: " + name);
        return stream;
    }

    static void check(Path file, String expected) throws Exception {
        if (!Files.exists(file) || !hash(file).equals(expected)) {
            throw new IllegalStateException("Unexpected file hash: " + file);
        }
    }

    static String hash(Path file) throws Exception {
        try (InputStream stream = Files.newInputStream(file)) {
            return digest(stream.readAllBytes());
        }
    }

    static String digest(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
