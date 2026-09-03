/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.Gson
 *  com.google.gson.GsonBuilder
 *  com.google.gson.JsonElement
 *  com.google.gson.JsonObject
 *  net.fabricmc.loader.api.FabricLoader
 *  net.minecraft.util.Identifier
 */
package com.gangs.gangshop.shop;

import com.gangs.gangshop.GangShopMod;
import com.gangs.gangshop.shop.ShopCategory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;

public class PriceConfigService {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, Long> itemSellPrices = new HashMap<String, Long>();
    private final Map<String, Long> itemBuyPrices = new HashMap<String, Long>();
    private final Map<ShopCategory, Double> categoryMultipliers = new HashMap<ShopCategory, Double>();
    private final Map<String, Long> itemSellOverrides = new HashMap<String, Long>();
    private final Set<String> denyItems = new HashSet<String>();
    private double globalSellMultiplier = 1.0;
    private double moddedBlocksSellMultiplier = 2.0;
    private Path configDir;
    private Path pricesFile;
    private Path overridesFile;

    public void load() {
        this.configDir = FabricLoader.getInstance().getConfigDir().resolve("gangshop");
        this.pricesFile = this.configDir.resolve("prices.json");
        this.overridesFile = this.configDir.resolve("overrides.json");
        this.itemSellPrices.clear();
        this.categoryMultipliers.clear();
        this.itemSellOverrides.clear();
        this.denyItems.clear();
        this.globalSellMultiplier = 1.0;
        this.moddedBlocksSellMultiplier = 2.0;
        for (ShopCategory category : ShopCategory.vanillaCategories()) {
            this.categoryMultipliers.put(category, 1.0);
        }
        this.denyItems.add("sarosplayerplushiemod:player_plushie");
        this.denyItems.add("sarosplayerplushiemod:player_plushie_box");
        try {
            Files.createDirectories(this.configDir, new FileAttribute[0]);
            this.loadOrCreatePrices();
            this.loadOrCreateOverrides();
        }
        catch (Exception e) {
            GangShopMod.LOGGER.error("Failed to load Gang Shop price configs", (Throwable)e);
        }
    }

    private void loadOrCreatePrices() throws Exception {
        if (!Files.exists(this.pricesFile, new LinkOption[0])) {
            JsonObject root = new JsonObject();
            root.addProperty("version", (Number)2);
            root.add("items", (JsonElement)new JsonObject());
            try (BufferedWriter writer = Files.newBufferedWriter(this.pricesFile, StandardCharsets.UTF_8, new OpenOption[0]);){
                GSON.toJson((JsonElement)root, (Appendable)writer);
            }
        }
        try (BufferedReader reader = Files.newBufferedReader(this.pricesFile, StandardCharsets.UTF_8);){
            JsonObject root = (JsonObject)GSON.fromJson((Reader)reader, JsonObject.class);
            if (root != null && root.has("items") && root.get("items").isJsonObject()) {
                JsonObject items = root.getAsJsonObject("items");
                for (Map.Entry entry : items.entrySet()) {
                    JsonElement value = (JsonElement)entry.getValue();
                    if (!value.isJsonObject()) continue;
                    JsonObject item = value.getAsJsonObject();
                    long sell = item.has("sell") ? Math.max(1L, item.get("sell").getAsLong()) : 1L;
                    long buy = item.has("buy") ? Math.max(1L, item.get("buy").getAsLong()) : this.buyFromSell(sell);
                    this.itemSellPrices.put((String)entry.getKey(), sell);
                    this.itemBuyPrices.put((String)entry.getKey(), buy);
                }
            }
        }
    }

    private void loadOrCreateOverrides() throws Exception {
        JsonObject deny;
        if (!Files.exists(this.overridesFile, new LinkOption[0])) {
            JsonObject root = new JsonObject();
            root.addProperty("version", (Number)1);
            JsonObject multipliers = new JsonObject();
            for (ShopCategory category : ShopCategory.vanillaCategories()) {
                multipliers.addProperty(category.getId(), (Number)1.0);
            }
            root.add("categoryMultipliers", (JsonElement)multipliers);
            root.add("itemSellOverrides", (JsonElement)new JsonObject());
            root.addProperty("globalSellMultiplier", (Number)1.0);
            root.addProperty("moddedBlocksSellMultiplier", (Number)2.0);
            deny = new JsonObject();
            deny.addProperty("sarosplayerplushiemod:player_plushie", Boolean.valueOf(true));
            deny.addProperty("sarosplayerplushiemod:player_plushie_box", Boolean.valueOf(true));
            root.add("denyItems", (JsonElement)deny);
            try (BufferedWriter writer = Files.newBufferedWriter(this.overridesFile, StandardCharsets.UTF_8, new OpenOption[0]);){
                GSON.toJson((JsonElement)root, (Appendable)writer);
            }
        }
        try (BufferedReader reader = Files.newBufferedReader(this.overridesFile, StandardCharsets.UTF_8);){
            JsonObject root = (JsonObject)GSON.fromJson((Reader)reader, JsonObject.class);
            if (root == null) {
                return;
            }
            if (root.has("categoryMultipliers") && root.get("categoryMultipliers").isJsonObject()) {
                JsonObject multipliers = root.getAsJsonObject("categoryMultipliers");
                for (ShopCategory category : ShopCategory.vanillaCategories()) {
                    if (!multipliers.has(category.getId())) continue;
                    this.categoryMultipliers.put(category, Math.max(0.01, multipliers.get(category.getId()).getAsDouble()));
                }
            }
            if (root.has("itemSellOverrides") && root.get("itemSellOverrides").isJsonObject()) {
                JsonObject overrides = root.getAsJsonObject("itemSellOverrides");
                for (Map.Entry entry : overrides.entrySet()) {
                    this.itemSellOverrides.put((String)entry.getKey(), Math.max(1L, ((JsonElement)entry.getValue()).getAsLong()));
                }
            }
            if (root.has("globalSellMultiplier")) {
                this.globalSellMultiplier = Math.max(0.01, root.get("globalSellMultiplier").getAsDouble());
            }
            if (root.has("moddedBlocksSellMultiplier")) {
                this.moddedBlocksSellMultiplier = Math.max(0.01, root.get("moddedBlocksSellMultiplier").getAsDouble());
            }
            if (root.has("denyItems") && root.get("denyItems").isJsonObject()) {
                deny = root.getAsJsonObject("denyItems");
                for (Map.Entry entry : deny.entrySet()) {
                    if (!((JsonElement)entry.getValue()).getAsBoolean()) continue;
                    this.denyItems.add((String)entry.getKey());
                }
            }
        }
    }

    public boolean isDenied(Identifier id) {
        String key = id.toString();
        if (this.denyItems.contains(key)) {
            return true;
        }
        String path = id.getPath().toLowerCase();
        return path.contains("plushie") || path.contains("plushy") || path.contains("plushie_box") || path.contains("plush_box");
    }

    public long ensureSellPrice(Identifier id, ShopCategory category, long defaultSell) {
        long base;
        String key = id.toString();
        if ("openblocks:elevator_block".equals(key)) {
            this.itemSellPrices.put(key, 5000L);
            this.itemBuyPrices.put(key, 5000L);
            return 5000L;
        }
        if ("minecraft".equals(id.getNamespace()) && ("shulker_box".equals(id.getPath()) || id.getPath().endsWith("_shulker_box"))) {
            this.itemSellPrices.put(key, 5000L);
            this.itemBuyPrices.put(key, 5000L);
            return 5000L;
        }
        if (this.itemSellOverrides.containsKey(key)) {
            base = this.itemSellOverrides.get(key);
        } else {
            long configured = this.itemSellPrices.getOrDefault(key, -1L);
            if (configured > 0L) {
                return configured;
            }
            base = Math.max(1L, defaultSell);
        }
        double categoryMultiplier = this.categoryMultipliers.getOrDefault((Object)category, 1.0);
        double moddedMultiplier = category.isModded() ? this.moddedBlocksSellMultiplier : 1.0;
        long adjusted = Math.max(1L, Math.round((double)base * categoryMultiplier * this.globalSellMultiplier * moddedMultiplier));
        this.itemSellPrices.put(key, adjusted);
        this.itemBuyPrices.putIfAbsent(key, this.buyFromSell(adjusted));
        return adjusted;
    }

    public long ensureBuyPrice(Identifier id, long sellValue) {
        String key = id.toString();
        if ("openblocks:elevator_block".equals(key)) {
            this.itemBuyPrices.put(key, 5000L);
            return 5000L;
        }
        if ("minecraft".equals(id.getNamespace()) && ("shulker_box".equals(id.getPath()) || id.getPath().endsWith("_shulker_box"))) {
            this.itemBuyPrices.put(key, 5000L);
            return 5000L;
        }
        long buy = this.itemBuyPrices.getOrDefault(key, -1L);
        if (buy <= 0L) {
            buy = this.buyFromSell(sellValue);
        }
        this.itemBuyPrices.put(key, buy);
        return buy;
    }

    public void setItemPrice(Identifier id, long sell, long buy) {
        String key = id.toString();
        this.itemSellPrices.put(key, Math.max(1L, sell));
        this.itemBuyPrices.put(key, Math.max(1L, buy));
        this.savePrices();
    }

    public long getSellPrice(Identifier id) {
        return this.itemSellPrices.getOrDefault(id.toString(), 1L);
    }

    public long getBuyPrice(Identifier id) {
        return this.itemBuyPrices.getOrDefault(id.toString(), this.buyFromSell(this.getSellPrice(id)));
    }

    public long buyFromSell(long sell) {
        return Math.max(1L, Math.round((double)sell * 1.33));
    }

    public void savePrices() {
        if (this.pricesFile == null) {
            return;
        }
        JsonObject root = new JsonObject();
        root.addProperty("version", (Number)2);
        JsonObject items = new JsonObject();
        this.itemSellPrices.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            JsonObject item = new JsonObject();
            item.addProperty("sell", (Number)entry.getValue());
            item.addProperty("buy", (Number)this.itemBuyPrices.getOrDefault(entry.getKey(), this.buyFromSell((Long)entry.getValue())));
            items.add((String)entry.getKey(), (JsonElement)item);
        });
        root.add("items", (JsonElement)items);
        try (BufferedWriter writer = Files.newBufferedWriter(this.pricesFile, StandardCharsets.UTF_8, new OpenOption[0]);){
            GSON.toJson((JsonElement)root, (Appendable)writer);
        }
        catch (Exception e) {
            GangShopMod.LOGGER.error("Failed to save prices.json", (Throwable)e);
        }
    }
}

