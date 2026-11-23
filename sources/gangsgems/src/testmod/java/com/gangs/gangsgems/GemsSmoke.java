package com.gangs.gangsgems;

import com.gangs.gangsgems.mixin.PlayerManagerInvoker;
import com.mojang.authlib.GameProfile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.conczin.immersive_furniture.data.FurnitureData;
import net.conczin.immersive_furniture.item.FurnitureItem;
import net.conczin.immersive_furniture.network.c2s.CraftRequest;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class GemsSmoke implements ModInitializer {
    private boolean ran;

    @Override public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (ran) return;
            ran = true;
            try {
                run(server);
                Files.writeString(Path.of("gangsgems-smoke-pass"), "passed");
                System.out.println("GANGS_GEMS_SMOKE_PASS");
                server.stop(false);
            } catch (IOException failure) {
                throw new IllegalStateException("Gang Gems smoke test failed", failure);
            }
        });
    }

    private static void run(MinecraftServer server) throws IOException {
        if (!GangGems.furnitureCraftingEnabled()) {
            runOriginalCrafting(server);
            return;
        }
        for (String id : new String[]{"crafting_material", "furniture_duplication", "recycling"}) {
            require(server.getRecipeManager().get(new Identifier("immersive_furniture", id)).isEmpty(),
                "Old material recipe is still enabled: " + id);
        }
        WalletStore wallet = GangGems.wallet();
        require(wallet != null, "Wallet not initialized");
        var player = new FixturePlayer(server, server.getOverworld());
        var furniture = net.conczin.immersive_furniture.item.Items.FURNITURE;
        var data = new FurnitureData();
        int cost = data.getCost();
        require(cost > 0, "Invalid fixture cost");

        new CraftRequest(data, false).handle(player);
        require(player.getInventory().count(furniture) == 0, "Craft without Gems succeeded");
        wallet.creditPayment("smoke-payment-" + player.getUuid(), player.getUuid(), 100);
        var material = net.conczin.immersive_furniture.item.Items.CRAFTING_MATERIAL;
        player.getInventory().setStack(0, new ItemStack(material, 64));
        new CraftRequest(data, false).handle(player);
        require(player.getInventory().count(furniture) == 1, "Craft did not deliver furniture");
        require(wallet.balance(player.getUuid()) == 100 - cost, "Craft debit was incorrect");
        require(player.getInventory().count(material) == 64, "Gem crafting consumed crafting materials");
        require(wallet.pendingCrafts(player.getUuid()).isEmpty(), "Delivered craft remains pending");

        player.getInventory().clear();
        for (int i = 0; i < player.getInventory().main.size(); i++) {
            player.getInventory().setStack(i, new ItemStack(Items.STONE, 64));
        }
        new CraftRequest(data, false).handle(player);
        require(wallet.pendingCrafts(player.getUuid()).size() == 1, "Full-inventory craft not retained");
        player.getInventory().setStack(0, ItemStack.EMPTY);
        CraftDelivery.deliverPending(player);
        require(player.getInventory().count(furniture) == 1, "Pending craft was not delivered");
        require(wallet.pendingCrafts(player.getUuid()).isEmpty(), "Pending craft not cleared");

        player.getInventory().clear();
        ItemStack stack = new ItemStack(furniture);
        FurnitureItem.setData(stack, data);
        WalletStore.Craft craft = wallet.reserveCraft(player.getUuid(), cost, stack.writeNbt(new NbtCompound()).toString());
        String receipt = "gangsgems_craft:" + craft.id();
        require(player.addCommandTag(receipt), "Could not set receipt fixture");
        require(player.getInventory().insertStack(stack), "Could not insert receipt fixture");
        ((PlayerManagerInvoker) server.getPlayerManager()).gangsgems$savePlayerData(player);
        CraftDelivery.deliverPending(player);
        require(player.getInventory().count(furniture) == 1, "Receipt recovery duplicated furniture");
        require(wallet.pendingCrafts(player.getUuid()).isEmpty(), "Recovered craft remains pending");

        server.getUserCache().add(player.getGameProfile());
        long balance = wallet.balance(player.getUuid());
        String command = "ganggems credit smoke-order-" + player.getUuid() + " GemsFixture 100";
        require(server.getCommandManager().executeWithPrefix(server.getCommandSource().withLevel(0), command) == 0,
            "Non-operator could credit Gems");
        require(wallet.balance(player.getUuid()) == balance, "Unauthorized credit changed the balance");
        require(server.getCommandManager().executeWithPrefix(server.getCommandSource(), command) == 1,
            "Operator credit failed");
        require(server.getCommandManager().executeWithPrefix(server.getCommandSource(), command) == 1,
            "Duplicate credit was not acknowledged");
        require(wallet.balance(player.getUuid()) == balance + 100, "Duplicate credit changed the balance");
        require(server.getCommandManager().executeWithPrefix(server.getCommandSource(), command.replace(" 100", " 200")) == 0,
            "Conflicting credit was accepted");
        require(new WalletStore(server.getSavePath(net.minecraft.util.WorldSavePath.ROOT)
            .resolve("gangsgems").resolve("wallet.json")).balance(player.getUuid()) == balance + 100,
            "Credit was not persisted correctly");
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("luckperms")) {
            StaffPermissionsSmoke.run(server, player, wallet);
        } else {
            require(server.getCommandManager().executeWithPrefix(player.getCommandSource().withLevel(4),
                "ganggems grant GemsFixture 25 unauthorized") == 0,
                "Player staff access did not fail closed without LuckPerms");
        }
        if (Boolean.getBoolean("gangsgems.testDiscord")) DiscordGemsSmoke.run(server);
    }

    private static void runOriginalCrafting(MinecraftServer server) {
        for (String id : new String[]{"crafting_material", "furniture_duplication", "recycling"}) {
            require(server.getRecipeManager().get(new Identifier("immersive_furniture", id)).isPresent(),
                "Original recipe is missing: " + id);
        }
        var player = new FixturePlayer(server, server.getOverworld(), true);
        var data = new FurnitureData();
        int cost = data.getCost();
        require(cost > 0 && cost <= 64, "Invalid original crafting fixture cost");
        var material = net.conczin.immersive_furniture.item.Items.CRAFTING_MATERIAL;
        player.getInventory().setStack(0, new ItemStack(material, cost));
        new CraftRequest(data, false).handle(player);
        require(player.getInventory().count(net.conczin.immersive_furniture.item.Items.FURNITURE) == 1,
            "Original furniture crafting was intercepted");
        require(player.getInventory().count(material) == 0, "Original crafting did not consume materials");
    }

    static final class FixturePlayer extends ServerPlayerEntity {
        private final boolean operator;

        FixturePlayer(MinecraftServer server, ServerWorld world) {
            this(server, world, false);
        }

        FixturePlayer(MinecraftServer server, ServerWorld world, boolean operator) {
            super(server, world, new GameProfile(UUID.randomUUID(), "GemsFixture"));
            this.operator = operator;
        }
        @Override public boolean hasPermissionLevel(int level) { return operator || level <= 0; }
        @Override public void sendMessage(Text message) {}
        @Override public void sendMessage(Text message, boolean overlay) {}
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
