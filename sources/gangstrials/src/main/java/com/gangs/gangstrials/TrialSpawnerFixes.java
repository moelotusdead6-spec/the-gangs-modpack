package com.gangs.gangstrials;

import com.drd.trickytrialsbackport.block.entity.trialspawner.TrialSpawnerConfig;
import java.util.Optional;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DataPool;
import net.minecraft.world.MobSpawnerEntry;

/**
 * Vanilla-compatible replacement for Tricky Trials Backport's TrialSpawnerConfig.fromTag.
 * The backport reads spawn_potentials[].data.entity but saves the entity directly under data,
 * so spawners lose their mob after a chunk save/load; it also ignores vanilla field defaults.
 */
public final class TrialSpawnerFixes {
    public static final float DEFAULT_KEY_CHANCE = 0.1F;
    private static volatile Thread trialSpawnThread;
    private static volatile float keyChance = DEFAULT_KEY_CHANCE;

    /** Chance that a per-player spawner reward is a key instead of consumables. */
    public static float keyChance() {
        return keyChance;
    }

    public static void setKeyChance(float chance) {
        keyChance = Math.max(0F, Math.min(1F, chance));
    }

    private TrialSpawnerFixes() {
    }

    public static TrialSpawnerConfig parseConfig(NbtCompound tag) {
        TrialSpawnerConfig defaults = TrialSpawnerConfig.DEFAULT;
        DataPool.Builder<MobSpawnerEntry> potentials = DataPool.builder();
        for (NbtElement element : tag.getList("spawn_potentials", NbtElement.COMPOUND_TYPE)) {
            NbtCompound entry = (NbtCompound) element;
            NbtCompound data = entry.getCompound("data");
            // Accept the vanilla {entity:{...}} form and the flat form the backport writes when saving.
            NbtCompound entity = data.contains("entity", NbtElement.COMPOUND_TYPE) ? data.getCompound("entity") : data;
            if (!entity.contains("id", NbtElement.STRING_TYPE)) {
                continue;
            }
            int weight = entry.contains("weight", NbtElement.NUMBER_TYPE) ? entry.getInt("weight") : 1;
            if (weight > 0) {
                potentials.add(new MobSpawnerEntry(entity.copy(), Optional.empty()), weight);
            }
        }

        DataPool<Identifier> lootTables = defaults.lootTablesToEject();
        if (tag.contains("loot_tables_to_eject", NbtElement.LIST_TYPE)) {
            DataPool.Builder<Identifier> loot = DataPool.builder();
            for (NbtElement element : tag.getList("loot_tables_to_eject", NbtElement.COMPOUND_TYPE)) {
                NbtCompound entry = (NbtCompound) element;
                Identifier id = Identifier.tryParse(entry.getString("data"));
                int weight = entry.contains("weight", NbtElement.NUMBER_TYPE) ? entry.getInt("weight") : 1;
                if (id != null && weight > 0) {
                    loot.add(id, weight);
                }
            }
            lootTables = loot.build();
        }

        Identifier ominousItems = tag.contains("items_to_drop_when_ominous", NbtElement.STRING_TYPE)
            ? Identifier.tryParse(tag.getString("items_to_drop_when_ominous"))
            : null;

        return new TrialSpawnerConfig(
            tag.contains("spawn_range", NbtElement.NUMBER_TYPE) ? tag.getInt("spawn_range") : defaults.spawnRange(),
            floatOr(tag, "total_mobs", defaults.totalMobs()),
            floatOr(tag, "simultaneous_mobs", defaults.simultaneousMobs()),
            floatOr(tag, "total_mobs_added_per_player", defaults.totalMobsAddedPerPlayer()),
            floatOr(tag, "simultaneous_mobs_added_per_player", defaults.simultaneousMobsAddedPerPlayer()),
            tag.contains("ticks_between_spawn", NbtElement.NUMBER_TYPE) ? tag.getInt("ticks_between_spawn") : defaults.ticksBetweenSpawn(),
            potentials.build(),
            lootTables,
            ominousItems != null ? ominousItems : defaults.itemsToDropWhenOminous());
    }

    private static float floatOr(NbtCompound tag, String key, float fallback) {
        return tag.contains(key, NbtElement.NUMBER_TYPE) ? tag.getFloat(key) : fallback;
    }

    public static void beginTrialSpawn() {
        trialSpawnThread = Thread.currentThread();
    }

    public static void endTrialSpawn() {
        trialSpawnThread = null;
    }

    /** Vanilla 1.21 trial spawners ignore monster light requirements. */
    public static boolean ignoresLight() {
        return trialSpawnThread == Thread.currentThread();
    }
}
