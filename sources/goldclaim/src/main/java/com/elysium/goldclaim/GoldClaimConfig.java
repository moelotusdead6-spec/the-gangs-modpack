/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  com.google.gson.Gson
 *  com.google.gson.GsonBuilder
 *  net.fabricmc.loader.api.FabricLoader
 *  net.minecraft.item.Item
 *  net.minecraft.item.Items
 *  net.minecraft.client.render.VertexFormatElement0
 *  net.minecraft.loot.entry.LootPoolEntry23
 */
package com.elysium.goldclaim;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public class GoldClaimConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public int minimumClaimWidth = 5;
    public int minimumClaimDepth = 5;
    public String claimToolItem = "minecraft:golden_shovel";
    public int maxClaimBlocksPerPlayerPerDimension = 10000;
    public boolean allowOpsBypass = true;
    public boolean protectEntityInteractions = true;
    public boolean denyMessageInActionBar = true;
    public int visualizationParticleStep = 2;
    public int visualizationHeightOffset = 1;
    public int visualizationDurationTicks = 50;
    public String portalHubDimension = "gangs:hub";
    public int hubTeleportX = 0;
    public int hubTeleportY = 68;
    public int hubTeleportZ = 0;
    public float hubTeleportYaw = -90.0f;
    public float hubTeleportPitch = 0.0f;
    public boolean freezeHubFallingBlocks = true;
    public boolean freezeHubFluids = true;
    public boolean preserveUnsupportedHubBlocks = true;
    public String wildDimension = "minecraft:overworld";
    public boolean hubFullyInvulnerable = true;
    public boolean preventHubFire = true;
    public boolean hubKeepInventoryWhilePresent = false;
    public boolean giveClaimKitOnFirstJoin = true;
    public int randomTeleportRadius = 10000;
    public int randomTeleportAttempts = 32;

    public static GoldClaimConfig load() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("goldclaim.json");
        GoldClaimConfig config = new GoldClaimConfig();
        if (Files.exists(configPath, new LinkOption[0])) {
            try (BufferedReader reader2 = Files.newBufferedReader(configPath);){
                GoldClaimConfig loaded = (GoldClaimConfig)GSON.fromJson((Reader)reader2, GoldClaimConfig.class);
                if (loaded != null) {
                    config = loaded;
                }
            }
            catch (IOException reader2) {
                // empty catch block
            }
        }
        try {
            Files.createDirectories(configPath.getParent(), new FileAttribute[0]);
            try (BufferedWriter writer = Files.newBufferedWriter(configPath, new OpenOption[0]);){
                GSON.toJson((Object)config, (Appendable)writer);
            }
        }
        catch (IOException iOException) {
            // empty catch block
        }
        return config;
    }

    public Item getClaimToolItem() {
        Identifier id = Identifier.tryParse((String)this.claimToolItem);
        if (id == null) {
            return Items.GOLDEN_SHOVEL;
        }
        Item item = (Item)Registries.ITEM.get(id);
        if (item == null || item == Items.AIR) {
            return Items.GOLDEN_SHOVEL;
        }
        return item;
    }
}
