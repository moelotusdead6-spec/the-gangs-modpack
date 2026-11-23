package com.gangs.gangsgems;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.UUID;

final class KnownPlayerLookup {
    private static final DateTimeFormatter CACHE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT);

    private KnownPlayerLookup() {}

    static UUID find(Path path, String username, Instant now) throws IOException {
        if (!Files.exists(path)) return null;
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonArray()) throw new IOException("Invalid player cache");
            UUID found = null;
            for (JsonElement element : root.getAsJsonArray()) {
                if (!element.isJsonObject()) throw new IOException("Invalid player cache entry");
                JsonObject entry = element.getAsJsonObject();
                if (!entry.has("name") || !entry.get("name").isJsonPrimitive()
                        || !entry.get("name").getAsJsonPrimitive().isString()) {
                    throw new IOException("Invalid player cache name");
                }
                if (!username.equalsIgnoreCase(entry.get("name").getAsString())) continue;
                UUID uuid = UUID.fromString(entry.get("uuid").getAsString());
                Instant expires = OffsetDateTime.parse(entry.get("expiresOn").getAsString(), CACHE_DATE).toInstant();
                if (!expires.isAfter(now)) continue;
                if (found != null && !found.equals(uuid)) return null;
                found = uuid;
            }
            return found;
        } catch (IllegalArgumentException | IllegalStateException | NullPointerException
                | DateTimeParseException | JsonParseException failure) {
            throw new IOException("Invalid player cache", failure);
        }
    }
}
