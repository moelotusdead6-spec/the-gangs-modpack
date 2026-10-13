package com.elysium.goldclaim;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

final class LootrSmokeTest {
    private static final List<BlockPos> loot = new ArrayList<>();
    private static final List<BlockPos> storage = new ArrayList<>();
    private static final Identifier TABLE = new Identifier("minecraft", "chests/end_city_treasure");
    private static final String[] WOODS = {"dragon_tree", "end_lotus", "helix_tree", "jellyshroom",
        "lacugrove", "lucernia", "mossy_glowshroom", "pythadendron", "tenanea", "umbrella_tree"};
    private static boolean initialized;
    private static boolean passed;

    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (initialized) return;
            ServerWorld world = server.getOverworld();
            int index = 0;
            for (String wood : WOODS) {
                for (String type : List.of("chest", "barrel")) {
                    Identifier id = new Identifier("betterend", wood + "_" + type);
                    if (!Registries.BLOCK.containsId(id)) throw new IllegalStateException("Missing container " + id);
                    BlockState state = Registries.BLOCK.get(id).getDefaultState();
                    BlockPos position = new BlockPos(4 * index++, 160, 32);
                    world.setBlockState(position, state, Block.NOTIFY_LISTENERS);
                    if (!(world.getBlockEntity(position) instanceof LootableContainerBlockEntity entity)) {
                        throw new IllegalStateException("Not a loot-bearing container: " + id);
                    }
                    entity.setLootTable(TABLE, 42L);
                    loot.add(position);
                    BlockPos personal = position.add(0, 0, 4);
                    world.setBlockState(personal, state, Block.NOTIFY_LISTENERS);
                    if (!(world.getBlockEntity(personal) instanceof LootableContainerBlockEntity chest)) {
                        throw new IllegalStateException("Missing player storage: " + id);
                    }
                    chest.setStack(0, new ItemStack(Items.DIAMOND, 3));
                    storage.add(personal);
                }
            }
            initialized = true;
        });
    }

    static boolean verify(MinecraftServer server) {
        if (passed) return true;
        if (!initialized) return false;
        ServerWorld world = server.getOverworld();
        if (loot.stream().anyMatch(pos ->
                !Registries.BLOCK.getId(world.getBlockState(pos).getBlock()).getNamespace().equals("lootr"))) {
            return false;
        }
        try {
            Class<?> api = Class.forName("net.zestyblaze.lootr.api.blockentity.ILootBlockEntity");
            for (BlockPos position : loot) {
                Object entity = world.getBlockEntity(position);
                if (!api.isInstance(entity) || !TABLE.equals(api.getMethod("getTable").invoke(entity))
                        || !Long.valueOf(42).equals(api.getMethod("getSeed").invoke(entity))) {
                    throw new IllegalStateException("Lootr did not preserve loot table/seed at " + position);
                }
            }
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot verify Lootr conversion API", failure);
        }
        for (BlockPos position : storage) {
            if (!Registries.BLOCK.getId(world.getBlockState(position).getBlock()).getNamespace().equals("betterend")
                    || !(world.getBlockEntity(position) instanceof LootableContainerBlockEntity chest)
                    || !chest.getStack(0).isOf(Items.DIAMOND) || chest.getStack(0).getCount() != 3) {
                throw new IllegalStateException("Lootr modified player storage at " + position);
            }
        }
        var first = new ServerPlayerEntity(server, world, new GameProfile(UUID.randomUUID(), "LootrFirst"));
        var second = new ServerPlayerEntity(server, world, new GameProfile(UUID.randomUUID(), "LootrSecond"));
        if (!(world.getBlockEntity(loot.get(0)) instanceof LootableContainerBlockEntity chest)) {
            throw new IllegalStateException("Converted chest has no loot inventory");
        }
        BlockPos position = loot.get(0);
        var hit = new BlockHitResult(Vec3d.ofCenter(position), Direction.UP, position, false);
        for (ServerPlayerEntity player : List.of(first, second)) {
            player.networkHandler = new ServerPlayNetworkHandler(server,
                new ClientConnection(NetworkSide.SERVERBOUND), player);
            world.getBlockState(position).onUse(world, player, Hand.MAIN_HAND, hit);
        }
        if (!(first.currentScreenHandler instanceof GenericContainerScreenHandler firstMenu)
                || !(second.currentScreenHandler instanceof GenericContainerScreenHandler secondMenu)) {
            throw new IllegalStateException("Lootr did not provide chest menus");
        }
        if (secondMenu.getInventory().isEmpty()
                || firstMenu.getInventory() == secondMenu.getInventory()) {
            throw new IllegalStateException("Lootr did not create independent populated inventories");
        }
        firstMenu.getInventory().clear();
        if (secondMenu.getInventory().isEmpty()) {
            throw new IllegalStateException("Looting one player's chest depleted another player's loot");
        }
        passed = true;
        return true;
    }
}
