package com.gangs.invhistory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

public record HistoryConfig(int retentionDays) {
    static final int DEFAULT_RETENTION_DAYS = 7;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public Duration retention() {
        return Duration.ofDays(retentionDays);
    }

    static HistoryConfig load(Path file) {
        if (!Files.exists(file)) {
            var config = new HistoryConfig(DEFAULT_RETENTION_DAYS);
            JsonObject json = new JsonObject();
            json.addProperty("retentionDays", DEFAULT_RETENTION_DAYS);
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, GSON.toJson(json) + "\n", StandardCharsets.UTF_8);
            } catch (IOException failure) {
                InvHistoryMod.LOGGER.warn("Cannot write default config {}.", file, failure);
            }
            return config;
        }
        try {
            JsonObject json = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonObject.class);
            int days = json != null && json.has("retentionDays")
                ? json.get("retentionDays").getAsInt() : DEFAULT_RETENTION_DAYS;
            if (days < 1 || days > 365) {
                InvHistoryMod.LOGGER.error("retentionDays must be 1-365 in {}; using {}.", file, DEFAULT_RETENTION_DAYS);
                days = DEFAULT_RETENTION_DAYS;
            }
            return new HistoryConfig(days);
        } catch (IOException | JsonParseException | IllegalStateException | UnsupportedOperationException failure) {
            InvHistoryMod.LOGGER.error("Invalid config {}; using {} day retention.", file, DEFAULT_RETENTION_DAYS, failure);
            return new HistoryConfig(DEFAULT_RETENTION_DAYS);
        }
    }
}
