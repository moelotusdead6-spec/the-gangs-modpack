package com.gangs.gangsgems;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class FurnitureConfiguration {
    private FurnitureConfiguration() {}

    static boolean enabled(Path config) throws IOException {
        if (Files.notExists(config)) return false;
        try (var reader = Files.newBufferedReader(config)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (!root.has("enabled") || !root.get("enabled").isJsonPrimitive()
                    || !root.get("enabled").getAsJsonPrimitive().isBoolean()) {
                throw new IllegalArgumentException("Furniture configuration requires an enabled flag");
            }
            return root.get("enabled").getAsBoolean();
        } catch (JsonParseException | IllegalStateException | IllegalArgumentException failure) {
            throw new IOException("Invalid Gang Gems furniture configuration", failure);
        }
    }
}
