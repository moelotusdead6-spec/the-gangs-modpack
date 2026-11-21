package com.gangs.gangshop;

import com.mojang.authlib.GameProfile;
import gang_shop_ozsoihjx.api.ShopBackend;
import gang_shop_ozsoihjx.api.ShopBackendRegistry;
import java.util.UUID;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class ShopSmokeTest implements DedicatedServerModInitializer {
    @Override
    public void onInitializeServer() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            verify(server);
            System.out.println("GANGSHOP_SMOKE_PASS");
            server.stop(false);
        });
    }

    private static void verify(MinecraftServer server) {
        ShopBackend backend = ShopBackendRegistry.get();
        require(backend != null, "Backend was not registered");
        Fixture player = new Fixture(server);
        Identifier stone = new Identifier("minecraft:stone");
        Identifier bedrock = new Identifier("minecraft:bedrock");
        require(backend.findEntry(player, stone) != null, "Stone missing");
        require(backend.findEntry(player, bedrock) == null, "Excluded bedrock is visible");
        require(!backend.edit(player, new ShopBackend.AdminRequest("item_add", "minecraft:bedrock",
                "stone", "1", "1")).success(), "Excluded item added");

        String oldCategory = backend.findEntry(player, stone).category();
        require(backend.edit(player, new ShopBackend.AdminRequest("category_create", "Smoke Category",
                "minecraft:chest", "", "")).success(), "Category creation failed");
        require(backend.edit(player, new ShopBackend.AdminRequest("item_update", stone.toString(),
                "10", "4", "Smoke Category")).success(), "Move and price edit failed");
        require(!backend.edit(player, new ShopBackend.AdminRequest("category_delete", "Smoke Category",
                "", "", "")).success(), "Nonempty category deleted");
        GangShopMod.reloadAll();
        require(backend.findEntry(player, stone).category().equals("Smoke Category"), "Category did not persist");
        require(backend.findEntry(player, stone).buyPrice() == 10, "Price did not persist");

        GangShopMod.ECONOMY.set(player, 1000);
        require(backend.buy(player, stone, ShopBackend.TradeAmount.ONE).success(), "Buy failed");
        require(GangShopMod.ECONOMY.getBalance(player) == 990, "Buy charged wrong balance");
        require(player.getInventory().count(Items.STONE) == 1, "Bought item missing");
        require(backend.sell(player, stone, ShopBackend.TradeAmount.ONE).success(), "Sell failed");
        require(GangShopMod.ECONOMY.getBalance(player) == 994, "Sell credited wrong balance");
        require(player.getInventory().count(Items.STONE) == 0, "Sold item not removed");
        require(!backend.sell(player, stone, ShopBackend.TradeAmount.FILL).success(), "Invalid sale allowed");
        require(!backend.buy(player, stone, ShopBackend.TradeAmount.ALL).success(), "Invalid purchase allowed");

        ItemStack tagged = new ItemStack(Items.STONE, 2);
        tagged.getOrCreateNbt().putString("test", "preserve");
        player.getInventory().setStack(0, tagged);
        require(!backend.sell(player, stone, ShopBackend.TradeAmount.ALL).success(), "Modified items were sold");
        require(player.getInventory().getStack(0).getCount() == 2, "Modified item was lost");
        player.getInventory().clear();
        require(backend.edit(player, new ShopBackend.AdminRequest("item_update", stone.toString(),
                Long.toString(Long.MAX_VALUE), "1", "Smoke Category")).success(), "High price edit failed");
        GangShopMod.ECONOMY.set(player, Long.MAX_VALUE);
        require(!backend.buy(player, stone, ShopBackend.TradeAmount.STACK).success(), "Overflowing purchase succeeded");
        require(GangShopMod.ECONOMY.getBalance(player) == Long.MAX_VALUE, "Overflowing buy changed balance");
        require(player.getInventory().count(Items.STONE) == 0, "Overflowing buy gave items");
        player.getInventory().setStack(0, new ItemStack(Items.STONE, 1));
        require(!backend.sell(player, stone, ShopBackend.TradeAmount.ONE).success(), "Overflowing sale succeeded");
        require(player.getInventory().count(Items.STONE) == 1, "Overflowing sale removed items");
        require(backend.edit(player, new ShopBackend.AdminRequest("item_update", stone.toString(),
                "0", "4", oldCategory)).success(), "Move back failed");
        require(backend.findEntry(player, stone).buyPrice() == 10000, "Zero-price fallback missing");
        require(backend.edit(player, new ShopBackend.AdminRequest("category_delete", "Smoke Category",
                "", "", "")).success(), "Empty category deletion failed");
        require(backend.edit(player, new ShopBackend.AdminRequest("item_remove", stone.toString(),
                "", "", "")).success(), "Item removal failed");
        require(backend.findEntry(player, stone) == null, "Removed item remains visible");
        require(backend.edit(player, new ShopBackend.AdminRequest("item_add", stone.toString(),
                oldCategory, "10", "4")).success(), "Eligible item could not be re-added");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static final class Fixture extends ServerPlayerEntity {
        Fixture(MinecraftServer server) {
            super(server, server.getOverworld(), new GameProfile(UUID.randomUUID(), "ShopSmoke"));
            networkHandler = new ServerPlayNetworkHandler(server, new ClientConnection(NetworkSide.SERVERBOUND), this);
        }

        @Override public boolean hasPermissionLevel(int permissionLevel) { return true; }
        @Override public void sendMessage(Text message, boolean overlay) { }
    }
}
