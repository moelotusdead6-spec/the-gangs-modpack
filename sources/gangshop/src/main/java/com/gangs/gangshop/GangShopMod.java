/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.api.ModInitializer
 *  net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
 *  net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
 *  net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
 *  net.minecraft.server.network.ServerPlayerEntity
 *  net.minecraft.text.Text
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.gangs.gangshop;

import com.gangs.gangshop.command.GangShopCommands;
import com.gangs.gangshop.economy.GangBucksService;
import com.gangs.gangshop.economy.WalletStore;
import com.gangs.gangshop.gui.ShopGuiService;
import com.gangs.gangshop.shop.CatalogService;
import com.gangs.gangshop.shop.PriceConfigService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GangShopMod
implements ModInitializer {
    public static final String MOD_ID = "gangshop";
    public static final String CURRENCY_NAME = "Gang Bucks";
    public static final long STARTER_BALANCE = 1000L;
    public static final Logger LOGGER = LoggerFactory.getLogger((String)"gangshop");
    public static final WalletStore WALLETS = new WalletStore();
    public static final GangBucksService ECONOMY = new GangBucksService(WALLETS);
    public static final PriceConfigService PRICES = new PriceConfigService();
    public static final CatalogService CATALOG = new CatalogService(PRICES);
    public static final ShopGuiService GUI = new ShopGuiService(CATALOG, ECONOMY);
    private static long saveTickCounter = 0L;

    public void onInitialize() {
        GangShopCommands.register();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            WALLETS.load(server);
            PRICES.load();
            CATALOG.reload();
            LOGGER.info("Gang Shop loaded. Catalog entries: {}", (Object)CATALOG.getEntryCount());
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++saveTickCounter >= 1200L) {
                saveTickCounter = 0L;
                WALLETS.save(server);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> WALLETS.save(server));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            if (ECONOMY.grantStarterIfAbsent(player, 1000L)) {
                player.sendMessage((Text)Text.literal((String)("Welcome! You received " + String.format("%,d", 1000L) + " Gang Bucks.")), false);
                WALLETS.save(server);
            }
        });
    }

    public static void reloadAll() {
        PRICES.load();
        CATALOG.reload();
    }
}

