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
 * Restart launcher for GoldClaim 1.0.64 and Neon Wall Wonders 11. It installs the
 * update only while the world is not running, keeps originals in BACKUP and then
 * starts the original Fabric server launcher.
 */
public final class ClaimTrustRestart {
    static final Path BACKUP = Path.of("backups/claim-trust-restart-1.0.65");
    static final String FABRIC_LAUNCHER_SHA256 = "6f921315fe2f570c331ca877d1e815c29b93fdc4ad04ce0f0f5cdf4300710028";
    static final String OLD_GOLDCLAIM = "mods/goldclaim-1.0.63.jar";
    static final String NEW_GOLDCLAIM = "mods/goldclaim-1.0.64.jar";
    static final String OLD_NEON = "mods/neon-wall-wonders-gwbzt3mk (10).jar";
    static final String NEW_NEON = "mods/neon-wall-wonders-gwbzt3mk.jar";
    static final String NEON_TAG = "config/paxi/datapacks/gangs_kits/data/gangs/tags/items/neon_signs.json";
    static final String OVERRIDES = "config/gangshop/overrides.json";
    static final String APPLIED = "GoldClaim 1.0.64 claim trust update; Neon Wall Wonders 11; "
        + "87-sign kit tag; Neon shop deny entries.\n";

    public static void main(String[] args) throws Throwable {
        install(Path.of(".").toAbsolutePath().normalize());
        String main;
        try (JarFile launcher = new JarFile("fabric-server-launch.jar")) {
            main = launcher.getManifest().getMainAttributes().getValue("Main-Class");
        }
        if (main == null || main.equals(ClaimTrustRestart.class.getName())) {
            throw new IllegalStateException("Invalid original Fabric launcher.");
        }
        System.setProperty("java.class.path", Path.of("fabric-server-launch.jar").toAbsolutePath().toString());
        System.out.println("[ClaimTrustRestart] Starting original Fabric launcher.");
        try {
            Class.forName(main).getMethod("main", String[].class).invoke(null, (Object)args);
        } catch (java.lang.reflect.InvocationTargetException failure) {
            throw failure.getCause();
        }
    }

    static void install(Path root) throws Exception {
        Properties previous = properties("previous.properties");
        Properties next = properties("files.properties");
        List<String> deny = denyEntries();
        for (String key : List.of(OLD_GOLDCLAIM, OLD_NEON, NEON_TAG)) {
            if (!previous.containsKey(key)) throw new IllegalStateException("Incomplete restart manifest: " + key);
        }
        for (String key : List.of(NEW_GOLDCLAIM, NEW_NEON, NEON_TAG)) {
            if (!next.containsKey(key)) throw new IllegalStateException("Incomplete restart manifest: " + key);
            try (InputStream stream = payload(key)) {
                if (!digest(stream).equals(next.getProperty(key))) {
                    throw new IllegalStateException("Corrupt payload: " + key);
                }
            }
        }
        check(root.resolve("fabric-server-launch.jar"), FABRIC_LAUNCHER_SHA256);
        try (FileChannel channel = FileChannel.open(root.resolve("world/session.lock"), StandardOpenOption.WRITE);
             var lock = channel.tryLock()) {
            if (lock == null) throw new IllegalStateException("World is still running; no update applied.");
            Path backup = root.resolve(BACKUP);
            Path marker = backup.resolve("APPLIED.txt");
            requireOnlyActive(root, "goldclaim-", OLD_GOLDCLAIM, NEW_GOLDCLAIM);
            requireOnlyActive(root, "neon-wall-wonders", OLD_NEON, NEW_NEON);
            if (Files.exists(marker)) {
                verifyInstalled(root, previous, next, deny);
                System.out.println("[ClaimTrustRestart] Update already installed and verified.");
                return;
            }

            // Validate every file before changing anything; a partial earlier run is resumable.
            checkReplacement(root, OLD_GOLDCLAIM, NEW_GOLDCLAIM, previous, next);
            checkReplacement(root, OLD_NEON, NEW_NEON, previous, next);
            checkInPlace(root, NEON_TAG, previous, next);
            Path overrides = root.resolve(OVERRIDES);
            String overridesText = decode(Files.readAllBytes(overrides));
            String mergedOverrides = addDenyEntries(overridesText, deny);

            Files.createDirectories(backup);
            for (String old : List.of(OLD_GOLDCLAIM, OLD_NEON, NEON_TAG)) {
                Path source = root.resolve(old);
                Path saved = backup.resolve(old);
                if (Files.exists(source) && hash(source).equals(previous.getProperty(old)) && !Files.exists(saved)) {
                    Files.createDirectories(saved.getParent());
                    Files.copy(source, saved);
                }
                if (Files.exists(saved)) check(saved, previous.getProperty(old));
            }
            Path savedOverrides = backup.resolve(OVERRIDES);
            if (!Files.exists(savedOverrides)) {
                Files.createDirectories(savedOverrides.getParent());
                Files.copy(overrides, savedOverrides);
            }

            for (String target : List.of(NEW_GOLDCLAIM, NEW_NEON, NEON_TAG)) {
                if (!Files.exists(root.resolve(target)) || !hash(root.resolve(target)).equals(next.getProperty(target))) {
                    try (InputStream stream = payload(target)) {
                        write(root.resolve(target), stream.readAllBytes(), next.getProperty(target));
                    }
                }
            }
            if (!mergedOverrides.equals(overridesText)) {
                byte[] bytes = mergedOverrides.getBytes(StandardCharsets.UTF_8);
                write(overrides, bytes, digest(bytes));
            }
            Files.deleteIfExists(root.resolve(OLD_GOLDCLAIM));
            Files.deleteIfExists(root.resolve(OLD_NEON));
            verifyInstalled(root, previous, next, deny);
            Files.writeString(marker, APPLIED + "overrides_backup_sha256=" + hash(savedOverrides) + "\n",
                StandardOpenOption.CREATE_NEW);
            System.out.println("[ClaimTrustRestart] Update installed; originals saved in " + BACKUP);
        }
    }

    static void requireOnlyActive(Path root, String prefix, String old, String current) throws Exception {
        String oldName = Path.of(old).getFileName().toString();
        String currentName = Path.of(current).getFileName().toString();
        try (var files = Files.list(root.resolve("mods"))) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                if (name.startsWith(prefix) && name.endsWith(".jar")
                        && !name.equals(oldName) && !name.equals(currentName)) {
                    throw new IllegalStateException("Unexpected active mod: " + name);
                }
            }
        }
    }

    static void checkReplacement(Path root, String old, String current, Properties previous, Properties next)
            throws Exception {
        Path oldFile = root.resolve(old);
        Path newFile = root.resolve(current);
        Path saved = root.resolve(BACKUP).resolve(old);
        if (Files.exists(oldFile)) {
            if (!hash(oldFile).equals(previous.getProperty(old))) {
                throw new IllegalStateException("File changed since staging: " + old);
            }
        } else if (!Files.exists(saved) || !hash(saved).equals(previous.getProperty(old))) {
            throw new IllegalStateException("Original file and backup are missing: " + old);
        }
        if (Files.exists(newFile) && !hash(newFile).equals(next.getProperty(current))) {
            throw new IllegalStateException("Unexpected target file: " + current);
        }
    }

    static void checkInPlace(Path root, String file, Properties previous, Properties next) throws Exception {
        Path path = root.resolve(file);
        Path saved = root.resolve(BACKUP).resolve(file);
        if (!Files.exists(path)) throw new IllegalStateException("Missing file: " + file);
        String actual = hash(path);
        if (actual.equals(previous.getProperty(file))) return;
        if (actual.equals(next.getProperty(file)) && Files.exists(saved)
                && hash(saved).equals(previous.getProperty(file))) {
            return;
        }
        throw new IllegalStateException("File changed since staging: " + file);
    }

    static void verifyInstalled(Path root, Properties previous, Properties next, List<String> deny) throws Exception {
        check(root.resolve(NEW_GOLDCLAIM), next.getProperty(NEW_GOLDCLAIM));
        check(root.resolve(NEW_NEON), next.getProperty(NEW_NEON));
        check(root.resolve(NEON_TAG), next.getProperty(NEON_TAG));
        if (Files.exists(root.resolve(OLD_GOLDCLAIM)) || Files.exists(root.resolve(OLD_NEON))) {
            throw new IllegalStateException("Old GoldClaim or Neon jar is still active.");
        }
        check(root.resolve(BACKUP).resolve(OLD_GOLDCLAIM), previous.getProperty(OLD_GOLDCLAIM));
        check(root.resolve(BACKUP).resolve(OLD_NEON), previous.getProperty(OLD_NEON));
        String overrides = decode(Files.readAllBytes(root.resolve(OVERRIDES)));
        Set<String> keys = objectKeys(overrides, findTopLevelValue(overrides, "denyItems", '{'));
        for (String key : deny) {
            if (!keys.contains(key)) throw new IllegalStateException("Missing shop deny entry: " + key);
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

    static List<String> denyEntries() throws Exception {
        List<String> entries = new ArrayList<>();
        try (InputStream stream = payload("deny.txt")) {
            for (String line : new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) entries.add(trimmed);
            }
        }
        if (entries.isEmpty()) throw new IllegalStateException("Empty Neon deny list.");
        return entries;
    }

    static void write(Path destination, byte[] bytes, String expected) throws Exception {
        if (!digest(bytes).equals(expected)) throw new IllegalStateException("Unexpected bytes for " + destination);
        Files.createDirectories(destination.getParent());
        Path temporary = destination.resolveSibling(destination.getFileName() + ".claim-trust-tmp");
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
        InputStream stream = ClaimTrustRestart.class.getResourceAsStream("/payload/" + name);
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
            return digest(stream);
        }
    }

    static String digest(InputStream stream) throws Exception {
        return digest(stream.readAllBytes());
    }

    static String digest(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
