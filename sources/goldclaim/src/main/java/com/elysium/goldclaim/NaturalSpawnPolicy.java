package com.elysium.goldclaim;

import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

public final class NaturalSpawnPolicy {
    private NaturalSpawnPolicy() {}

    public static boolean allow(MobEntity mob, World world) {
        return allow(mob, world, world.getRandom());
    }

    public static boolean allow(MobEntity mob, World world, Random random) {
        return allow(world.getRegistryKey().getValue().toString(),
            Registries.ENTITY_TYPE.getId(mob.getType()).toString(),
            mob instanceof Monster || mob.getType().getSpawnGroup() == SpawnGroup.MONSTER,
            isReduced(Registries.ENTITY_TYPE.getId(mob.getType()).toString())
                ? random.nextInt(100) : 99);
    }

    static boolean allow(String dimension, String entity, boolean hostile, int roll) {
        if ("minecraft:overworld".equals(dimension) && hostile) {
            return false;
        }
        // Chunk population retries each animal up to four times: 1 - 0.95^4 < 0.3.
        return !isReduced(entity) || roll >= 95;
    }

    static boolean isReduced(String entity) {
        return entity.equals("crittersandcompanions:roly_poly")
            || entity.equals("crittersandcompanions:snail")
            || entity.equals("naturalist:snail");
    }
}
