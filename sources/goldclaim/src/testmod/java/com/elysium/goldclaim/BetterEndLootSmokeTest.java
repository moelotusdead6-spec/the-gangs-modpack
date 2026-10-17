package com.elysium.goldclaim;

import com.mojang.authlib.GameProfile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate.StructureBlockInfo;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.gen.structure.Structure;
import org.betterx.betterend.world.features.BuildingListFeature;
import org.betterx.betterend.world.generator.GeneratorOptions;

final class BetterEndLootSmokeTest {
    private record Loot(BlockPos pos, String table, long seed) {}
    private static final List<Loot> loot = new ArrayList<>();
    private static int ticks;
    private static boolean placed;
    private static BlockPos storage;

    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            require(++ticks < 1200, "BetterEnd natural structure conversion timed out");
            ServerWorld world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD,
                new Identifier(GoldClaimMod.RSW_THE_END_DIMENSION)));
            if (world == null) return;
            if (!placed) {
                placeStructures(world);
                placed = true;
                return;
            }
            if (loot.stream().anyMatch(entry ->
                    !Registries.BLOCK.getId(world.getBlockState(entry.pos()).getBlock())
                        .getNamespace().equals("lootr"))) return;
            verify(server, world);
            try {
                Files.writeString(Path.of("policy-smoke-passed.txt"),
                    "PASS: " + loot.size() + " natural BetterEnd structure containers retained deferred loot "
                    + "and converted in RSW End; loot tables/seeds and independent player inventories "
                    + "preserved; player storage unchanged; normal End City biome/terrain checks passed"
                    + ".\n");
            } catch (IOException failure) {
                throw new IllegalStateException("Cannot record BetterEnd loot smoke result", failure);
            }
            server.stop(false);
        });
    }

    private static void placeStructures(ServerWorld world) {
        verifyProcessor(world);
        int failChance = GeneratorOptions.getEndCityFailChance();
        require(failChance <= 1, "BetterEnd still applies its extra city rejection");
        Random cityRandom = Random.create(17);
        for (int attempt = 0; attempt < 1000; attempt++) {
            require(failChance <= 0 || cityRandom.nextInt(failChance) == 0,
                "An End City attempt was rejected by BetterEnd's extra veto");
        }
        Structure city = world.getRegistryManager().get(RegistryKeys.STRUCTURE)
            .get(new Identifier("minecraft", "end_city"));
        require(city != null, "End City structure is not registered");
        var generator = world.getChunkManager().getChunkGenerator();
        boolean cityFound = false;
        for (int x = 80; x <= 240 && !cityFound; x += 8) {
            for (int z = 80; z <= 240 && !cityFound; z += 8) {
                var context = new Structure.Context(world.getRegistryManager(), generator,
                    generator.getBiomeSource(), world.getChunkManager().getNoiseConfig(),
                    world.getStructureTemplateManager(), world.getSeed(), new ChunkPos(x, z),
                    world, city.getValidBiomes()::contains);
                cityFound = city.getValidStructurePosition(context).isPresent();
            }
        }
        require(cityFound, "No viable End City generation position found inside the RSW border");
        String[] templates = {"biome/lantern_woods/cabin", "biome/blossoming_spires/house",
            "biome/foggy_mushroomland/tree_house"};
        for (int index = 0; index < templates.length; index++) {
            var template = world.getStructureTemplateManager().getTemplate(
                new Identifier("betterend", templates[index])).orElseThrow();
            BlockPos origin = new BlockPos(1600 + index * 128, 180, 1600);
            var size = template.getSize();
            for (int x = (origin.getX() >> 4) - 2; x <= ((origin.getX() + size.getX()) >> 4) + 2; x++) {
                for (int z = (origin.getZ() >> 4) - 2; z <= ((origin.getZ() + size.getZ()) >> 4) + 2; z++) {
                    world.setChunkForced(x, z, true);
                    world.getChunk(x, z);
                }
            }
            StructurePlacementData placement = new StructurePlacementData().setIgnoreEntities(true)
                .setRandom(Random.create(42 + index));
            try {
                var method = BuildingListFeature.class.getDeclaredMethod("addStructureData", StructurePlacementData.class);
                method.setAccessible(true);
                method.invoke(new BuildingListFeature(), placement);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Cannot invoke real BetterEnd structure processor", failure);
            }
            require(template.place(world, origin, origin, placement, Random.create(42 + index),
                Block.NOTIFY_LISTENERS), "BetterEnd template placement failed");
            int found = 0;
            for (BlockPos pos : BlockPos.iterate(origin, origin.add(size.getX() - 1, size.getY() - 1, size.getZ() - 1))) {
                if (world.getBlockEntity(pos) instanceof LootableContainerBlockEntity container) {
                    NbtCompound nbt = container.createNbt();
                    String id = Registries.BLOCK.getId(world.getBlockState(pos).getBlock()).toString();
                    if (!id.startsWith("betterend:")) continue;
                    if (!(world.getBlockState(pos).getBlock() instanceof ChestBlock) && !nbt.contains("LootTable")) continue;
                    require(nbt.contains("LootTable"), "Natural " + templates[index] + " container " + id
                        + " at " + pos + " lost deferred loot: " + nbt);
                    loot.add(new Loot(pos.toImmutable(), nbt.getString("LootTable"), nbt.getLong("LootTableSeed")));
                    found++;
                }
            }
            require(found > 0, "Natural template has no loot containers: " + templates[index]);
        }
        storage = new BlockPos(1600, 200, 1584);
        world.setBlockState(storage, Registries.BLOCK.get(new Identifier("betterend", "lucernia_chest"))
            .getDefaultState(), Block.NOTIFY_LISTENERS);
        ((LootableContainerBlockEntity) world.getBlockEntity(storage)).setStack(0, new ItemStack(Items.DIAMOND, 3));
    }

    private static void verifyProcessor(ServerWorld world) {
        var processor = new BetterEndLootProcessor();
        BlockPos pos = new BlockPos(1600, 180, 1600);
        var chest = Registries.BLOCK.get(new Identifier("betterend", "lucernia_chest")).getDefaultState();
        var placement = new StructurePlacementData().setRandom(Random.create(91));
        NbtCompound empty = new NbtCompound();
        empty.put("Items", new net.minecraft.nbt.NbtList());
        var original = new StructureBlockInfo(pos, chest, empty);
        var processed = processor.process(world, pos, pos, original, original, placement);
        require(processed.nbt().contains("LootTable") && !processed.nbt().contains("Items"),
            "Empty natural chest did not receive deferred loot");
        require(empty.contains("Items") && !empty.contains("LootTable"),
            "Loot processor mutated reusable template NBT");
        NbtCompound explicit = new NbtCompound();
        explicit.putString("LootTable", "minecraft:chests/end_city_treasure");
        explicit.putLong("LootTableSeed", 123456789L);
        var configured = new StructureBlockInfo(pos, chest, explicit);
        require(processor.process(world, pos, pos, configured, configured, placement).nbt().equals(explicit),
            "Loot processor replaced an explicit loot table or seed");
        var barrel = new StructureBlockInfo(pos,
            Registries.BLOCK.get(new Identifier("betterend", "lucernia_barrel")).getDefaultState(), empty);
        require(processor.process(world, pos, pos, barrel, barrel, placement) == barrel,
            "Loot processor changed a decorative barrel");
    }

    private static void verify(MinecraftServer server, ServerWorld world) {
        try {
            Class<?> api = Class.forName("net.zestyblaze.lootr.api.blockentity.ILootBlockEntity");
            for (Loot entry : loot) {
                Object entity = world.getBlockEntity(entry.pos());
                require(api.isInstance(entity)
                    && new Identifier(entry.table()).equals(api.getMethod("getTable").invoke(entity))
                    && Long.valueOf(entry.seed()).equals(api.getMethod("getSeed").invoke(entity)),
                    "Natural container conversion changed loot table/seed at " + entry.pos());
            }
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot inspect converted natural loot", failure);
        }
        require(Registries.BLOCK.getId(world.getBlockState(storage).getBlock()).getNamespace().equals("betterend"),
            "Player storage was converted");
        var inventory = (LootableContainerBlockEntity) world.getBlockEntity(storage);
        require(inventory.getStack(0).isOf(Items.DIAMOND) && inventory.getStack(0).getCount() == 3,
            "Player storage contents changed");
        BlockPos pos = loot.get(0).pos();
        List<GenericContainerScreenHandler> menus = new ArrayList<>();
        for (String name : List.of("NaturalLootA", "NaturalLootB")) {
            var player = new ServerPlayerEntity(server, world, new GameProfile(UUID.randomUUID(), name));
            player.networkHandler = new ServerPlayNetworkHandler(server, new ClientConnection(NetworkSide.SERVERBOUND), player);
            world.getBlockState(pos).onUse(world, player, Hand.MAIN_HAND,
                new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false));
            require(player.currentScreenHandler instanceof GenericContainerScreenHandler, "No personal loot menu");
            menus.add((GenericContainerScreenHandler) player.currentScreenHandler);
        }
        require(!menus.get(0).getInventory().isEmpty() && !menus.get(1).getInventory().isEmpty()
            && menus.get(0).getInventory() != menus.get(1).getInventory(), "Personal loot inventories are not independent");
        menus.get(0).getInventory().clear();
        require(!menus.get(1).getInventory().isEmpty(), "First player depleted second player's natural loot");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
