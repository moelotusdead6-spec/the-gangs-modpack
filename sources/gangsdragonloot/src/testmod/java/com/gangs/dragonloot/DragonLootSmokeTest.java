package com.gangs.dragonloot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;

public final class DragonLootSmokeTest implements DedicatedServerModInitializer {
    private static final TagKey<net.minecraft.item.Item> UNIQUES =
        TagKey.of(RegistryKeys.ITEM, new Identifier("simplyswords", "uniques"));

    @Override
    public void onInitializeServer() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                verify(server.getOverworld());
                Files.writeString(Path.of("dragon-loot-smoke-passed.txt"),
                    "PASS: 2000 identical-seed rolls, distinct pairs, all eligible uniques reached, "
                    + "non-weapon/other-entity loot preserved, actual dragon deaths, doMobLoot, "
                    + "native Alex's Mobs Farseer biome exclusion.\n");
            } catch (Exception failure) {
                throw new IllegalStateException("Dragon/Farseer smoke failed", failure);
            }
            server.stop(false);
        });
    }

    private static void verify(ServerWorld world) throws Exception {
        var candidates = Registries.ITEM.stream()
            .filter(item -> Registries.ITEM.getId(item).getNamespace().equals("simplyswords")
                && item.getDefaultStack().isIn(UNIQUES)).toList();
        require(candidates.size() >= 40, "real Simply Swords uniques must be loaded");
        var dragon = new EnderDragonEntity(EntityType.ENDER_DRAGON, world);
        var parameters = parameters(world, dragon);
        var table = LootTable.builder()
            .pool(LootPool.builder().rolls(ConstantLootNumberProvider.create(1))
                .with(ItemEntry.builder(Items.DIAMOND)))
            .pool(LootPool.builder().rolls(ConstantLootNumberProvider.create(1))
                .with(ItemEntry.builder(candidates.get(0))))
            .build();
        var reached = new HashSet<net.minecraft.item.Item>();
        var pairs = new HashSet<String>();
        for (int i = 0; i < 2000; i++) {
            List<ItemStack> drops = new ArrayList<>();
            DragonLoot.generate(table, parameters, 12345L, drops::add);
            require(drops.size() == 3 && drops.get(0).isOf(Items.DIAMOND),
                "non-weapon loot must be retained without the previous unique");
            var first = drops.get(1);
            var second = drops.get(2);
            require(first.isIn(UNIQUES) && second.isIn(UNIQUES)
                && first.getItem() != second.getItem() && first.getCount() == 1 && second.getCount() == 1,
                "exactly two different uniques required");
            reached.add(first.getItem());
            reached.add(second.getItem());
            pairs.add(Registries.ITEM.getId(first.getItem()) + "/" + Registries.ITEM.getId(second.getItem()));
        }
        require(reached.size() == candidates.size() && pairs.size() > 500,
            "identical saved seeds must not freeze the pair or exclude eligible weapons");

        var zombie = EntityType.ZOMBIE.create(world);
        List<ItemStack> expected = new ArrayList<>();
        List<ItemStack> actual = new ArrayList<>();
        table.generateLoot(parameters(world, zombie), 12345L, expected::add);
        DragonLoot.generate(table, parameters(world, zombie), 12345L, actual::add);
        require(expected.size() == actual.size(), "other entities' loot count changed");
        for (int i = 0; i < actual.size(); i++) {
            require(ItemStack.areEqual(expected.get(i), actual.get(i)), "other entities' loot changed");
        }

        var box = new Box(-8, 92, -8, 8, 108, 8);
        world.getChunk(0, 0);
        var actualPairs = new HashSet<String>();
        for (int i = 0; i < 12; i++) {
            clearItems(world, box);
            var dead = new EnderDragonEntity(EntityType.ENDER_DRAGON, world);
            dead.setPosition(0, 100, 0);
            NbtCompound nbt = new NbtCompound();
            dead.writeNbt(nbt);
            nbt.putLong("DeathLootTableSeed", 12345L);
            dead.readNbt(nbt);
            dead.onDeath(world.getDamageSources().generic());
            var uniques = world.getEntitiesByClass(ItemEntity.class, box,
                entity -> entity.getStack().isIn(UNIQUES));
            require(uniques.size() == 2, "actual dragon death did not drop exactly two uniques");
            require(uniques.get(0).getStack().getItem() != uniques.get(1).getStack().getItem(),
                "actual dragon death produced a duplicate");
            actualPairs.add(uniques.stream().map(entity -> Registries.ITEM.getId(
                entity.getStack().getItem()).toString()).sorted().toList().toString());
        }
        require(actualPairs.size() > 1, "actual dragon deaths repeated a fixed pair");
        clearItems(world, box);
        world.getGameRules().get(GameRules.DO_MOB_LOOT).set(false, world.getServer());
        var disabled = new EnderDragonEntity(EntityType.ENDER_DRAGON, world);
        disabled.setPosition(0, 100, 0);
        disabled.onDeath(world.getDamageSources().generic());
        require(world.getEntitiesByClass(ItemEntity.class, box,
            entity -> entity.getStack().isIn(UNIQUES)).isEmpty(), "doMobLoot=false was ignored");
        world.getGameRules().get(GameRules.DO_MOB_LOOT).set(true, world.getServer());

        Class<?> data = Class.forName("com.github.alexthe666.alexsmobs.citadel.config.biome.SpawnBiomeData");
        Class<?> config = Class.forName("com.github.alexthe666.alexsmobs.citadel.config.biome.SpawnBiomeConfig");
        var gson = (com.google.gson.Gson) config.getField("GSON").get(null);
        Object rules = gson.fromJson(Files.readString(Path.of("config", "alexsmobs", "farseer.json")), data);
        var matches = data.getMethod("matches", net.minecraft.registry.entry.RegistryEntry.class, Identifier.class);
        for (var entry : world.getRegistryManager().get(RegistryKeys.BIOME).getEntrySet()) {
            require(!(boolean) matches.invoke(rules,
                world.getRegistryManager().get(RegistryKeys.BIOME).entryOf(entry.getKey()), entry.getKey().getValue()),
                "Farseer still matches a registered biome");
        }
    }

    private static LootContextParameterSet parameters(ServerWorld world, net.minecraft.entity.Entity entity) {
        return new LootContextParameterSet.Builder(world)
            .add(LootContextParameters.THIS_ENTITY, entity)
            .add(LootContextParameters.ORIGIN, Vec3d.ZERO)
            .add(LootContextParameters.DAMAGE_SOURCE, world.getDamageSources().generic())
            .build(LootContextTypes.ENTITY);
    }

    private static void clearItems(ServerWorld world, Box box) {
        world.getEntitiesByClass(ItemEntity.class, box, entity -> true).forEach(ItemEntity::discard);
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
