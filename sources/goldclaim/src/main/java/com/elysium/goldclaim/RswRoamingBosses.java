package com.elysium.goldclaim;

import com.brutalbosses.entity.BossType;
import com.brutalbosses.entity.BossTypeManager;
import com.brutalbosses.entity.capability.BossCapEntity;
import com.brutalbosses.entity.capability.BossCapability;
import java.util.Comparator;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public final class RswRoamingBosses {
    public static final String TAG = "goldclaim_rsw_roaming_boss";

    private RswRoamingBosses() {}

    public static boolean eligible(MobEntity mob, SpawnReason reason) {
        if (reason != SpawnReason.NATURAL || !(mob instanceof Monster)
                || !(mob.getWorld() instanceof ServerWorld world)) return false;
        String dimension = world.getRegistryKey().getValue().toString();
        return GoldClaimMod.RSW_DIMENSION.equals(dimension)
            || GoldClaimMod.RSW_NETHER_DIMENSION.equals(dimension)
            || GoldClaimMod.RSW_THE_END_DIMENSION.equals(dimension);
    }

    public static void initialize(MobEntity mob, SpawnReason reason) {
        if (!eligible(mob, reason) || !(mob instanceof BossCapEntity holder) || holder.getBossCap() != null) return;
        EntityType<?> bossEntity = mob instanceof EndermanEntity ? EntityType.ENDERMAN : mob.getType();
        var candidates = BossTypeManager.instance.bosses.values().stream()
            .filter(type -> type.getEntityType() == bossEntity && !type.getID().getPath().equals("dummyboss"))
            .sorted(Comparator.comparing(type -> type.getID().toString())).toList();
        if (candidates.isEmpty() || mob.getRandom().nextInt(100) != 0) return;
        promote(mob, candidates.get(mob.getRandom().nextInt(candidates.size())));
    }

    static void promote(MobEntity mob, BossType type) {
        BossCapEntity holder = (BossCapEntity)mob;
        BossCapability capability = new BossCapability(mob);
        capability.setBossType(type);
        BlockPos pos = mob.getBlockPos().toImmutable();
        capability.setSpawnPos(pos);
        holder.setBossCap(capability);
        mob.addCommandTag(TAG);
        type.initForEntity(mob);
        mob.setHealth(mob.getMaxHealth());
        holder.setPersistence(false);
    }
}
