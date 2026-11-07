package com.gangs.gangstrials;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Config-driven allowlist: structures listed here may only generate (and be
 * located) in the listed dimensions. Structures not listed are unaffected.
 */
public final class StructureDimensionRules {
    private static final Logger LOGGER = LoggerFactory.getLogger("gangstrials");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String KEY = "structure_dimension_allowlist";
    private static final String KEY_CHANCE = "spawner_key_chance";
    private static volatile Map<String, Set<String>> allowlist = defaultRules();

    private StructureDimensionRules() {
    }

    private static Map<String, Set<String>> defaultRules() {
        Map<String, Set<String>> rules = new HashMap<>();
        rules.put("minecraft:trial_chambers", Set.of("multiworld:rsw"));
        return rules;
    }

    public static void load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("gangstrials.json");
        try {
            if (Files.exists(file)) {
                try (Reader reader = Files.newBufferedReader(file)) {
                    JsonObject root = GSON.fromJson(reader, JsonObject.class);
                    Map<String, Set<String>> rules = new HashMap<>();
                    if (root != null && root.has(KEY)) {
                        JsonObject entries = root.getAsJsonObject(KEY);
                        for (Map.Entry<String, JsonElement> entry : entries.entrySet()) {
                            Set<String> dims = new HashSet<>();
                            for (JsonElement dim : entry.getValue().getAsJsonArray()) {
                                dims.add(dim.getAsString());
                            }
                            rules.put(entry.getKey(), dims);
                        }
                    }
                    allowlist = rules;
                    TrialSpawnerFixes.setKeyChance(root != null && root.has(KEY_CHANCE)
                        ? root.get(KEY_CHANCE).getAsFloat()
                        : TrialSpawnerFixes.DEFAULT_KEY_CHANCE);
                }
            } else {
                Files.createDirectories(file.getParent());
                JsonObject root = new JsonObject();
                JsonObject entries = new JsonObject();
                for (Map.Entry<String, Set<String>> rule : allowlist.entrySet()) {
                    JsonArray dims = new JsonArray();
                    rule.getValue().forEach(dims::add);
                    entries.add(rule.getKey(), dims);
                }
                root.add(KEY, entries);
                root.addProperty(KEY_CHANCE, TrialSpawnerFixes.DEFAULT_KEY_CHANCE);
                try (Writer writer = Files.newBufferedWriter(file)) {
                    GSON.toJson(root, writer);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load gangstrials.json, using defaults", e);
            allowlist = defaultRules();
        }
        LOGGER.info("Structure dimension allowlist: {}, spawner key chance: {}", allowlist, TrialSpawnerFixes.keyChance());
    }

    /**
     * @return false only when the structure is restricted and the dimension is
     *         not in its allowlist (or the dimension is unknown).
     */
    public static boolean isAllowed(String structureId, String dimensionId) {
        if (structureId == null) {
            return true;
        }
        Set<String> dims = allowlist.get(structureId);
        if (dims == null) {
            return true;
        }
        return dimensionId != null && dims.contains(dimensionId);
    }
}
