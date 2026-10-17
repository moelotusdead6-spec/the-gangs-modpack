package com.elysium.goldclaim;

import com.elysium.goldclaim.mixin.ChestInventoryAccessor;
import com.mojang.authlib.GameProfile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.EndCityGenerator;
import net.minecraft.text.Text;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.zestyblaze.lootr.api.blockentity.ILootBlockEntity;
import net.zestyblaze.lootr.config.ConfigManager;

final class EndCityLootSmokeTest {
    private static final String[] PIECES = {"ship", "fat_tower_top", "third_floor_2"};
    private static int pieceIndex;
    private static int chests;

    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            require(server.getTicks() < 1200, "City chest fixture timed out");
            var end = server.getWorld(RegistryKey.of(RegistryKeys.WORLD,
                new Identifier(GoldClaimMod.RSW_THE_END_DIMENSION)));
            if (end == null) return;
            if (pieceIndex < PIECES.length) {
                verifyPiece(end, PIECES[pieceIndex], 1600 + 128 * pieceIndex++);
                return;
            }
            require(chests == 17, "Expected all 17 BetterEnd chests from actual city templates, got " + chests);
            verifyStorage(end);
            try {
                Files.writeString(Path.of("city-loot-smoke-passed.txt"),
                    "PASS: all 17 BetterEnd lacugrove chests in actual modded ship, tower and floor templates "
                    + "opened as Lootr before background conversion; original tables/seeds preserved; "
                    + "independent populated player inventories; already-opened and player storage untouched.\n");
            } catch (IOException failure) {
                throw new IllegalStateException("Cannot save city chest fixture result", failure);
            }
            server.stop(false);
        });
    }

    private static void verifyPiece(ServerWorld world, String name, int x) {
        BlockPos origin = new BlockPos(x, 180, 1600);
        var piece = new EndCityGenerator.Piece(world.getStructureTemplateManager(), name, origin,
            BlockRotation.NONE, true);
        var box = piece.getBoundingBox();
        for (int cx = box.getMinX() >> 4; cx <= box.getMaxX() >> 4; cx++) {
            for (int cz = box.getMinZ() >> 4; cz <= box.getMaxZ() >> 4; cz++) {
                world.setChunkForced(cx, cz, true);
                world.getChunk(cx, cz);
            }
        }
        piece.generate(world, world.getStructureAccessor(), world.getChunkManager().getChunkGenerator(),
            Random.create(42), box, new ChunkPos(origin), origin);
        for (BlockPos mutable : BlockPos.iterate(box.getMinX(), box.getMinY(), box.getMinZ(),
                box.getMaxX(), box.getMaxY(), box.getMaxZ())) {
            BlockPos pos = mutable.toImmutable();
            if (!Registries.BLOCK.getId(world.getBlockState(pos).getBlock()).toString()
                    .equals("betterend:lacugrove_chest")) continue;
            var before = ((LootableContainerBlockEntity)world.getBlockEntity(pos)).createNbt();
            require(before.getString("LootTable").equals("minecraft:chests/end_city_treasure"),
                "Real city marker lost its deferred treasure table: " + name + " " + pos + " " + before);
            List<GenericContainerScreenHandler> menus = new ArrayList<>();
            for (String playerName : List.of("CityLootA", "CityLootB")) {
                var player = new ServerPlayerEntity(world.getServer(), world,
                    new GameProfile(UUID.randomUUID(), playerName));
                player.networkHandler = new ServerPlayNetworkHandler(world.getServer(),
                    new ClientConnection(NetworkSide.SERVERBOUND), player);
                world.getBlockState(pos).onUse(world, player, Hand.MAIN_HAND,
                    new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false));
                require(player.currentScreenHandler instanceof GenericContainerScreenHandler,
                    "City chest did not open a personal menu");
                menus.add((GenericContainerScreenHandler)player.currentScreenHandler);
            }
            require(world.getBlockEntity(pos) instanceof ILootBlockEntity, "City chest still uses a shared inventory");
            var converted = (ILootBlockEntity)world.getBlockEntity(pos);
            require(converted.getTable().toString().equals(before.getString("LootTable"))
                && converted.getSeed() == before.getLong("LootTableSeed"), "City chest loot table/seed changed");
            UUID id = converted.getTileId();
            require(!BetterEndLootrService.convertBeforeOpening(world, pos, world.getBlockState(pos))
                && id.equals(converted.getTileId()), "Existing Lootr identity was replaced");
            require(!menus.get(0).getInventory().isEmpty() && !menus.get(1).getInventory().isEmpty()
                && menus.get(0).getInventory() != menus.get(1).getInventory(), "City loot is not personal");
            menus.get(0).getInventory().clear();
            require(!menus.get(1).getInventory().isEmpty(), "One player depleted another player's city loot");
            chests++;
        }
    }

    private static void verifyStorage(ServerWorld world) {
        BlockPos pos = new BlockPos(1600, 240, 1600);
        world.setBlockState(pos, Registries.BLOCK.get(new Identifier("betterend", "lacugrove_chest"))
            .getDefaultState(), Block.NOTIFY_LISTENERS);
        var chest = (LootableContainerBlockEntity)world.getBlockEntity(pos);
        chest.setStack(0, new ItemStack(Items.DIAMOND, 3));
        require(!BetterEndLootrService.convertBeforeOpening(world, pos, world.getBlockState(pos)),
            "Player storage or an already-opened chest was converted");
        require(chest.getStack(0).isOf(Items.DIAMOND) && chest.getStack(0).getCount() == 3,
            "Stored items changed");
        chest.setLootTable(new Identifier("minecraft", "chests/end_city_treasure"), 42L);
        chest.getStack(0);
        var opened = chest.createNbt();
        require(!opened.contains("LootTable")
            && !BetterEndLootrService.convertBeforeOpening(world, pos, world.getBlockState(pos))
            && opened.equals(chest.createNbt()), "Already-generated shared loot was reset");
        var table = new Identifier("minecraft", "chests/end_city_treasure");
        chest.setLootTable(table, 42L);
        require(!BetterEndLootrService.convertBeforeOpening(world, pos, world.getBlockState(pos)),
            "Nonempty chest with a reassigned table was converted");
        ((ChestInventoryAccessor)chest).goldclaim$inventory().clear();
        var conversion = ConfigManager.get().conversion;
        boolean wasDisabled = conversion.disable;
        try {
            conversion.disable = true;
            require(!BetterEndLootrService.convertBeforeOpening(world, pos, world.getBlockState(pos)),
                "Lootr's disabled-conversion setting was ignored");
        } finally {
            conversion.disable = wasDisabled;
        }
        var blacklist = ConfigManager.getLootBlacklist();
        require(!blacklist.contains(table), "Fixture table is unexpectedly blacklisted");
        try {
            blacklist.add(table);
            require(!BetterEndLootrService.convertBeforeOpening(world, pos, world.getBlockState(pos)),
                "Lootr's table blacklist was ignored");
        } finally {
            blacklist.remove(table);
        }
        var vanilla = world.getServer().getWorld(net.minecraft.world.World.END);
        vanilla.setBlockState(pos, world.getBlockState(pos), Block.NOTIFY_LISTENERS);
        var other = (LootableContainerBlockEntity)vanilla.getBlockEntity(pos);
        other.setLootTable(table, 42L);
        require(!BetterEndLootrService.convertBeforeOpening(vanilla, pos, vanilla.getBlockState(pos))
            && other.createNbt().contains("LootTable"), "Non-RSW dimension was changed");
        chest.setCustomName(Text.literal("Named natural chest"));
        require(BetterEndLootrService.convertBeforeOpening(world, pos, world.getBlockState(pos)),
            "Eligible unopened chest did not convert");
        require(Text.literal("Named natural chest").equals(world.getBlockEntity(pos)
            instanceof LootableContainerBlockEntity named ? named.getCustomName() : null),
            "Custom chest name was lost");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
