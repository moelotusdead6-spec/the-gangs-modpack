package com.gangs.gangshop.shop;

import com.gangs.gangshop.GangShopMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.network.ServerPlayerEntity;

public final class ShopAccess {
    private boolean restricted = true;
    private Set<UUID> testers = Set.of();

    public void load(Path path) {
        if (!Files.exists(path)) {
            restricted = true;
            testers = Set.of();
            GangShopMod.LOGGER.warn("Shop access configuration is missing; only operators may use the shop: {}", path);
            return;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (!root.has("restricted") || !root.get("restricted").isJsonPrimitive()
                    || !root.getAsJsonPrimitive("restricted").isBoolean()
                    || !root.has("testers") || !root.get("testers").isJsonArray()) {
                throw new IllegalArgumentException("Expected restricted boolean and testers UUID array");
            }
            JsonArray values = root.getAsJsonArray("testers");
            Set<UUID> parsed = new HashSet<>();
            for (var value : values) {
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException("Tester UUID must be a string");
                }
                parsed.add(UUID.fromString(value.getAsString()));
            }
            restricted = root.get("restricted").getAsBoolean();
            testers = Set.copyOf(parsed);
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot read shop access configuration " + path, exception);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Invalid shop access configuration " + path, exception);
        }
    }

    public boolean allows(ServerPlayerEntity player) {
        return allows(player.hasPermissionLevel(2), player.getUuid());
    }

    boolean allows(boolean operator, UUID playerId) {
        return !restricted || operator || testers.contains(playerId);
    }
}
