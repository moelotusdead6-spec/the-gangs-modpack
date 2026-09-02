/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.Gson
 *  com.google.gson.GsonBuilder
 *  com.google.gson.JsonElement
 *  com.google.gson.JsonObject
 *  net.minecraft.server.MinecraftServer
 *  net.minecraft.util.WorldSavePath
 */
package com.gangs.gangshop.economy;

import com.gangs.gangshop.GangShopMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

public class WalletStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "gangshop_wallets.json";
    private final Map<UUID, Long> balances = new HashMap<UUID, Long>();

    public synchronized long getBalance(UUID playerId) {
        return this.balances.getOrDefault(playerId, 0L);
    }

    public synchronized void setBalance(UUID playerId, long amount) {
        this.balances.put(playerId, Math.max(0L, amount));
    }

    public synchronized void addBalance(UUID playerId, long delta) {
        if (delta == 0L) {
            return;
        }
        long next = Math.max(0L, this.getBalance(playerId) + delta);
        this.balances.put(playerId, next);
    }

    public synchronized boolean subtractBalance(UUID playerId, long amount) {
        if (amount < 0L) {
            return false;
        }
        long current = this.getBalance(playerId);
        if (current < amount) {
            return false;
        }
        this.balances.put(playerId, current - amount);
        return true;
    }

    public synchronized boolean grantStarterIfAbsent(UUID playerId, long starterAmount) {
        if (this.balances.containsKey(playerId)) {
            return false;
        }
        this.balances.put(playerId, Math.max(0L, starterAmount));
        return true;
    }

    public synchronized List<Map.Entry<UUID, Long>> topBalances(int limit) {
        ArrayList<Map.Entry<UUID, Long>> entries = new ArrayList<Map.Entry<UUID, Long>>(this.balances.entrySet());
        entries.sort(Comparator.comparingLong(Map.Entry<UUID, Long>::getValue).reversed());
        if (limit <= 0 || entries.size() <= limit) {
            return entries;
        }
        return new ArrayList<Map.Entry<UUID, Long>>(entries.subList(0, limit));
    }

    public synchronized void load(MinecraftServer server) {
        this.balances.clear();
        Path path = this.resolvePath(server);
        if (!Files.exists(path, new LinkOption[0])) {
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);){
            JsonObject root = (JsonObject)GSON.fromJson((Reader)reader, JsonObject.class);
            if (root == null || !root.has("wallets") || !root.get("wallets").isJsonObject()) {
                return;
            }
            JsonObject wallets = root.getAsJsonObject("wallets");
            for (String key : wallets.keySet()) {
                try {
                    UUID uuid = UUID.fromString(key);
                    long amount = Math.max(0L, wallets.get(key).getAsLong());
                    this.balances.put(uuid, amount);
                }
                catch (Exception exception) {}
            }
        }
        catch (Exception e) {
            GangShopMod.LOGGER.error("Failed loading gangshop wallets", (Throwable)e);
        }
    }

    public synchronized void save(MinecraftServer server) {
        Path path = this.resolvePath(server);
        Path tempPath = path.resolveSibling("gangshop_wallets.json.tmp");
        JsonObject root = new JsonObject();
        JsonObject wallets = new JsonObject();
        for (Map.Entry<UUID, Long> entry : this.balances.entrySet()) {
            wallets.addProperty(entry.getKey().toString(), (Number)entry.getValue());
        }
        root.add("wallets", (JsonElement)wallets);
        try {
            Files.createDirectories(path.getParent(), new FileAttribute[0]);
            try (BufferedWriter writer = Files.newBufferedWriter(tempPath, StandardCharsets.UTF_8, new OpenOption[0]);){
                GSON.toJson((JsonElement)root, (Appendable)writer);
            }
            Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
        catch (IOException atomicMoveFailure) {
            try {
                Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING);
            }
            catch (IOException e) {
                GangShopMod.LOGGER.error("Failed saving gangshop wallets", (Throwable)e);
            }
        }
    }

    private Path resolvePath(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve(FILE_NAME);
    }
}

