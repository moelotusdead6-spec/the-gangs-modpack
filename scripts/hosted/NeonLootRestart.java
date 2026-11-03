import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CodingErrorAction;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarFile;

public final class NeonLootRestart {
    static final Path BACKUP = Path.of("backups/neon-loot-restart-1.0.64");
    static final String OLD_MOD = "mods/goldclaim-1.0.62.jar";
    static final String NEW_MOD = "mods/goldclaim-1.0.63.jar";
    static final String MONTHLY = "config/paxi/datapacks/gangs_kits/data/gangs/loot_tables/kits/monthly.json";
    static final String NEON_POOL = "\"rolls\": 2,\n"
        + "    \"entries\": [\n"
        + "      {\n"
        + "        \"type\": \"minecraft:tag\",\n"
        + "        \"name\": \"gangs:neon_signs\",\n"
        + "        \"expand\": true\n"
        + "      }\n"
        + "    ]";
    static final List<String> LOOT_FILES = List.of(
        "config/paxi/datapacks/gangs_kits/data/gangs/loot_tables/kits/weekly_1.json",
        "config/paxi/datapacks/gangs_kits/data/gangs/loot_tables/kits/weekly_2.json",
        "config/paxi/datapacks/gangs_kits/data/gangs/loot_tables/kits/weekly_3.json",
        "config/paxi/datapacks/gangs_kits/data/gangs/loot_tables/kits/monthly.json",
        "config/paxi/datapacks/gangs_kits/data/gangs/tags/items/neon_signs.json");
    static final String APPLIED = "GoldClaim 1.0.63; weekly and monthly neon kit rewards; "
        + "Neon Fox and Neon Owl chest rewards.\n";

    public static void main(String[] args) throws Throwable {
        install(Path.of(".").toAbsolutePath().normalize());
        String main;
        try (JarFile launcher = new JarFile("fabric-server-launch.jar")) {
            main = launcher.getManifest().getMainAttributes().getValue("Main-Class");
        }
        if (main == null || main.equals(NeonLootRestart.class.getName())) {
            throw new IllegalStateException("Invalid original Fabric launcher.");
        }
        System.setProperty("java.class.path", Path.of("fabric-server-launch.jar").toAbsolutePath().toString());
        System.out.println("[NeonLootRestart] Starting original Fabric launcher.");
        try {
            Class.forName(main).getMethod("main", String[].class).invoke(null, (Object)args);
        } catch (java.lang.reflect.InvocationTargetException failure) {
            throw failure.getCause();
        }
    }

    static void install(Path root) throws Exception {
        Properties previous = properties("previous.properties");
        Properties next = properties("files.properties");
        if (previous.size() != LOOT_FILES.size() + 1
                || next.size() != LOOT_FILES.size() + 1
                || !previous.containsKey(OLD_MOD) || !next.containsKey(NEW_MOD)) {
            throw new IllegalStateException("Incomplete neon loot restart manifest.");
        }
        for (String file : LOOT_FILES) {
            if (!previous.containsKey(file) || !next.containsKey(file)) {
                throw new IllegalStateException("Missing loot file in restart manifest: " + file);
            }
        }
        check(root.resolve("fabric-server-launch.jar"),
            "6f921315fe2f570c331ca877d1e815c29b93fdc4ad04ce0f0f5cdf4300710028");
        for (String file : next.stringPropertyNames()) {
            try (InputStream stream = payload(file)) {
                if (!digest(stream).equals(next.getProperty(file))) {
                    throw new IllegalStateException("Corrupt payload: " + file);
                }
            }
        }
        try (FileChannel channel = FileChannel.open(root.resolve("world/session.lock"), StandardOpenOption.WRITE);
             var lock = channel.tryLock()) {
            if (lock == null) throw new IllegalStateException("World is still running; no update applied.");
            Path backup = root.resolve(BACKUP);
            Path marker = backup.resolve("APPLIED.txt");
            for (Path file : Files.list(root.resolve("mods")).toList()) {
                String name = file.getFileName().toString();
                if (name.matches("goldclaim-.*\\.jar")
                        && !name.equals(Path.of(OLD_MOD).getFileName().toString())
                        && !name.equals(Path.of(NEW_MOD).getFileName().toString())) {
                    throw new IllegalStateException("Unexpected active GoldClaim mod: " + name);
                }
            }
            Properties effectivePrevious = copy(previous);
            Properties effectiveNext = copy(next);
            Map<String, byte[]> replacements = new HashMap<>();
            if (Files.exists(marker)) {
                Properties applied = new Properties();
                try (var reader = Files.newBufferedReader(marker, StandardCharsets.UTF_8)) {
                    applied.load(reader);
                }
                String monthlyHash = applied.getProperty("monthly_sha256");
                String backupHash = applied.getProperty("monthly_backup_sha256");
                if (monthlyHash != null) effectiveNext.setProperty(MONTHLY, monthlyHash);
                if (backupHash != null) check(backup.resolve(MONTHLY), backupHash);
                checkInstalled(root, effectiveNext);
                System.out.println("[NeonLootRestart] Update already installed and verified.");
                return;
            }
            Path monthly = root.resolve(MONTHLY);
            String monthlyHash = hash(monthly);
            if (!monthlyHash.equals(previous.getProperty(MONTHLY))
                    && !monthlyHash.equals(next.getProperty(MONTHLY))) {
                byte[] original = Files.readAllBytes(monthly);
                String merged = appendNeonPool(StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(original)).toString());
                byte[] replacement = merged.getBytes(StandardCharsets.UTF_8);
                effectivePrevious.setProperty(MONTHLY, monthlyHash);
                effectiveNext.setProperty(MONTHLY, digest(replacement));
                replacements.put(MONTHLY, replacement);
            }
            for (String old : effectivePrevious.stringPropertyNames()) {
                String expected = effectivePrevious.getProperty(old);
                Path source = root.resolve(old);
                Path saved = backup.resolve(old);
                boolean exists = Files.exists(source);
                if (expected.equals("ABSENT")) {
                    if (exists && !hash(source).equals(effectiveNext.getProperty(old))) {
                        throw new IllegalStateException("Unexpected file appeared since staging: " + old);
                    }
                    if (Files.exists(saved)) {
                        throw new IllegalStateException("Unexpected backup for previously absent file: " + old);
                    }
                    continue;
                }
                if (Files.exists(saved)) check(saved, expected);
                if (exists) {
                    String actual = hash(source);
                    if (!actual.equals(expected)
                            && (!actual.equals(effectiveNext.getProperty(old)) || !Files.exists(saved))) {
                        throw new IllegalStateException("File changed since staging: " + old);
                    }
                } else if (!Files.exists(saved)) {
                    throw new IllegalStateException("Original file and backup are missing: " + old);
                }
            }
            for (String target : effectiveNext.stringPropertyNames()) {
                Path destination = root.resolve(target);
                if (Files.exists(destination) && !hash(destination).equals(effectiveNext.getProperty(target))) {
                    String oldHash = effectivePrevious.getProperty(target);
                    if (oldHash == null || oldHash.equals("ABSENT")
                            || !hash(destination).equals(oldHash)) {
                        throw new IllegalStateException("Unexpected target file: " + target);
                    }
                }
            }
            Files.createDirectories(backup);
            for (String old : effectivePrevious.stringPropertyNames()) {
                if (effectivePrevious.getProperty(old).equals("ABSENT")) continue;
                Path source = root.resolve(old);
                Path saved = backup.resolve(old);
                if (Files.exists(source) && hash(source).equals(effectivePrevious.getProperty(old))) {
                    Files.createDirectories(saved.getParent());
                    if (!Files.exists(saved)) Files.copy(source, saved);
                    check(saved, effectivePrevious.getProperty(old));
                }
            }
            for (String target : effectiveNext.stringPropertyNames()) {
                byte[] replacement = replacements.get(target);
                if (replacement == null) {
                    write(root, target, effectiveNext.getProperty(target));
                } else {
                    write(root, target, effectiveNext.getProperty(target), replacement);
                }
            }
            Files.deleteIfExists(root.resolve(OLD_MOD));
            checkInstalled(root, effectiveNext);
            String markerText = APPLIED
                + "monthly_sha256=" + effectiveNext.getProperty(MONTHLY) + "\n"
                + "monthly_backup_sha256=" + effectivePrevious.getProperty(MONTHLY) + "\n";
            Files.writeString(marker, markerText, StandardOpenOption.CREATE_NEW);
            System.out.println("[NeonLootRestart] Update installed; originals saved in " + BACKUP);
        }
    }

    private static Properties copy(Properties source) {
        Properties result = new Properties();
        result.putAll(source);
        return result;
    }

    static String appendNeonPool(String json) {
        if (json.startsWith("\uFEFF")) return "\uFEFF" + appendNeonPool(json.substring(1));
        int poolsOpen = findTopLevelArray(json, "pools");
        int poolsClose = matchingEnd(json, poolsOpen);
        if (json.substring(poolsOpen, poolsClose).contains("\"gangs:neon_signs\"")) return json;
        int contentEnd = poolsClose;
        while (contentEnd > poolsOpen + 1 && Character.isWhitespace(json.charAt(contentEnd - 1))) {
            contentEnd--;
        }
        boolean empty = contentEnd == poolsOpen + 1;
        int lineStart = json.lastIndexOf('\n', poolsClose - 1) + 1;
        String indent = json.substring(lineStart, poolsClose);
        if (!indent.chars().allMatch(ch -> ch == ' ' || ch == '\t')) indent = "";
        String addition = (empty ? "" : ",") + "\n" + indent + "  {\n"
            + indent + "    " + NEON_POOL.replace("\n", "\n" + indent + "    ") + "\n"
            + indent + "  }\n" + indent;
        return json.substring(0, contentEnd) + addition + json.substring(poolsClose);
    }

    static int findTopLevelArray(String json, String name) {
        int index = skipWhitespace(json, 0);
        if (index >= json.length() || json.charAt(index++) != '{') {
            throw new IllegalStateException("Monthly loot table is not a JSON object.");
        }
        int arrayIndex = -1;
        while (true) {
            index = skipWhitespace(json, index);
            if (index < json.length() && json.charAt(index) == '}') {
                index++;
                break;
            }
            int keyStart = index;
            int keyEnd = stringEnd(json, keyStart);
            String key = json.substring(keyStart + 1, keyEnd - 1);
            index = skipWhitespace(json, keyEnd);
            if (index >= json.length() || json.charAt(index++) != ':') {
                throw new IllegalStateException("Malformed monthly loot table key.");
            }
            index = skipWhitespace(json, index);
            if (key.equals(name)) {
                if (index >= json.length() || json.charAt(index) != '[') {
                    throw new IllegalStateException("Monthly loot table pools is not an array.");
                }
                if (arrayIndex != -1) {
                    throw new IllegalStateException("Monthly loot table has duplicate pools arrays.");
                }
                arrayIndex = index;
            }
            index = skipValue(json, index);
            index = skipWhitespace(json, index);
            if (index < json.length() && json.charAt(index) == ',') {
                index++;
            } else if (index < json.length() && json.charAt(index) == '}') {
                index++;
                break;
            } else {
                throw new IllegalStateException("Malformed monthly loot table object.");
            }
        }
        if (skipWhitespace(json, index) != json.length()) {
            throw new IllegalStateException("Unexpected content after monthly loot table.");
        }
        if (arrayIndex == -1) {
            throw new IllegalStateException("Monthly loot table has no top-level pools array.");
        }
        skipValue(json, arrayIndex);
        return arrayIndex;
    }

    private static int skipValue(String json, int index) {
        if (index >= json.length()) throw new IllegalStateException("Missing monthly loot table value.");
        char first = json.charAt(index);
        if (first == '"') return stringEnd(json, index);
        if (first == '{') return skipObject(json, index);
        if (first == '[') return skipArray(json, index);
        if (first == 't') return skipLiteral(json, index, "true");
        if (first == 'f') return skipLiteral(json, index, "false");
        if (first == 'n') return skipLiteral(json, index, "null");
        if (first == '-' || first >= '0' && first <= '9') return skipNumber(json, index);
        throw new IllegalStateException("Malformed monthly loot table value.");
    }

    private static int skipObject(String json, int index) {
        index = skipWhitespace(json, index + 1);
        if (index < json.length() && json.charAt(index) == '}') return index + 1;
        while (true) {
            index = stringEnd(json, index);
            index = skipWhitespace(json, index);
            if (index >= json.length() || json.charAt(index++) != ':') {
                throw new IllegalStateException("Malformed monthly loot table object.");
            }
            index = skipWhitespace(json, index);
            index = skipValue(json, index);
            index = skipWhitespace(json, index);
            if (index < json.length() && json.charAt(index) == '}') return index + 1;
            if (index >= json.length() || json.charAt(index++) != ',') {
                throw new IllegalStateException("Malformed monthly loot table object.");
            }
            index = skipWhitespace(json, index);
        }
    }

    private static int skipArray(String json, int index) {
        index = skipWhitespace(json, index + 1);
        if (index < json.length() && json.charAt(index) == ']') return index + 1;
        while (true) {
            index = skipValue(json, index);
            index = skipWhitespace(json, index);
            if (index < json.length() && json.charAt(index) == ']') return index + 1;
            if (index >= json.length() || json.charAt(index++) != ',') {
                throw new IllegalStateException("Malformed monthly loot table array.");
            }
            index = skipWhitespace(json, index);
        }
    }

    private static int skipLiteral(String json, int index, String literal) {
        if (!json.startsWith(literal, index)) {
            throw new IllegalStateException("Malformed monthly loot table literal.");
        }
        return index + literal.length();
    }

    private static int skipNumber(String json, int index) {
        int length = json.length();
        if (json.charAt(index) == '-') index++;
        if (index >= length) throw new IllegalStateException("Malformed monthly loot table number.");
        if (json.charAt(index) == '0') {
            index++;
            if (index < length && Character.isDigit(json.charAt(index))) {
                throw new IllegalStateException("Malformed monthly loot table number.");
            }
        } else if (json.charAt(index) >= '1' && json.charAt(index) <= '9') {
            do { index++; } while (index < length && json.charAt(index) >= '0' && json.charAt(index) <= '9');
        } else {
            throw new IllegalStateException("Malformed monthly loot table number.");
        }
        if (index < length && json.charAt(index) == '.') {
            index++;
            int fractionStart = index;
            while (index < length && json.charAt(index) >= '0' && json.charAt(index) <= '9') index++;
            if (index == fractionStart) throw new IllegalStateException("Malformed monthly loot table number.");
        }
        if (index < length && (json.charAt(index) == 'e' || json.charAt(index) == 'E')) {
            index++;
            if (index < length && (json.charAt(index) == '+' || json.charAt(index) == '-')) index++;
            int exponentStart = index;
            while (index < length && json.charAt(index) >= '0' && json.charAt(index) <= '9') index++;
            if (index == exponentStart) throw new IllegalStateException("Malformed monthly loot table number.");
        }
        return index;
    }

    static int matchingEnd(String json, int openIndex) {
        ArrayDeque<Character> closing = new ArrayDeque<>();
        for (int index = openIndex; index < json.length(); index++) {
            char current = json.charAt(index);
            if (current == '"') {
                index = stringEnd(json, index) - 1;
            } else if (current == '{') {
                closing.push('}');
            } else if (current == '[') {
                closing.push(']');
            } else if (current == '}' || current == ']') {
                if (closing.isEmpty() || closing.pop() != current) {
                    throw new IllegalStateException("Malformed monthly loot table structure.");
                }
                if (closing.isEmpty()) return index;
            }
        }
        throw new IllegalStateException("Unclosed monthly loot table structure.");
    }

    private static int stringEnd(String json, int openIndex) {
        if (openIndex >= json.length() || json.charAt(openIndex) != '"') {
            throw new IllegalStateException("Malformed monthly loot table string.");
        }
        for (int index = openIndex + 1; index < json.length(); index++) {
            char current = json.charAt(index);
            if (current == '\\') {
                if (++index >= json.length()) {
                    throw new IllegalStateException("Unclosed monthly loot table escape.");
                }
                char escaped = json.charAt(index);
                if (escaped == 'u') {
                    for (int digit = 0; digit < 4; digit++) {
                        if (++index >= json.length() || Character.digit(json.charAt(index), 16) < 0) {
                            throw new IllegalStateException("Malformed monthly loot table unicode escape.");
                        }
                    }
                } else if ("\"\\/bfnrt".indexOf(escaped) == -1) {
                    throw new IllegalStateException("Malformed monthly loot table escape.");
                }
            } else if (current == '"') {
                return index + 1;
            } else if (current < 0x20) {
                throw new IllegalStateException("Invalid control character in monthly loot table string.");
            }
        }
        throw new IllegalStateException("Unclosed monthly loot table string.");
    }

    private static int skipWhitespace(String json, int index) {
        while (index < json.length() && Character.isWhitespace(json.charAt(index))) index++;
        return index;
    }

    private static void checkInstalled(Path root, Properties files) throws Exception {
        for (String file : files.stringPropertyNames()) {
            check(root.resolve(file), files.getProperty(file));
        }
        if (Files.exists(root.resolve(OLD_MOD))) {
            throw new IllegalStateException("Old GoldClaim jar remains after installation.");
        }
    }

    private static void write(Path root, String file, String expected) throws Exception {
        Path target = root.resolve(file);
        if (Files.exists(target) && hash(target).equals(expected)) return;
        Path pending = target.resolveSibling(target.getFileName() + ".neon-pending");
        Files.createDirectories(target.getParent());
        try (InputStream stream = payload(file)) {
            Files.copy(stream, pending, StandardCopyOption.REPLACE_EXISTING);
        }
        check(pending, expected);
        Files.move(pending, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        check(target, expected);
    }

    private static void write(Path root, String file, String expected, byte[] contents) throws Exception {
        Path target = root.resolve(file);
        if (Files.exists(target) && hash(target).equals(expected)) return;
        Path pending = target.resolveSibling(target.getFileName() + ".neon-pending");
        Files.createDirectories(target.getParent());
        Files.write(pending, contents, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
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
        InputStream stream = NeonLootRestart.class.getResourceAsStream("/payload/" + file);
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

    private static String digest(byte[] contents) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contents));
    }

    private static void check(Path file, String expected) throws Exception {
        if (!Files.isRegularFile(file) || !hash(file).equals(expected)) {
            throw new IllegalStateException("File missing or changed since staging: " + file);
        }
    }
}
