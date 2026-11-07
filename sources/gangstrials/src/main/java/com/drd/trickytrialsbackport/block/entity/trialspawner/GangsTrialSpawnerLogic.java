package com.drd.trickytrialsbackport.block.entity.trialspawner;

import com.drd.trickytrialsbackport.registry.ModSounds;
import com.drd.trickytrialsbackport.util.ModBuiltInLootTables;
import com.gangs.gangstrials.TrialSpawnerFixes;
import java.util.Optional;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.MobSpawnerEntry;

/**
 * Vanilla 1.21 trial spawner state machine, replacing the backport's wave logic.
 * Lives in the backport's package (same Knot classloader) to reach TrialSpawnerData's protected fields.
 * Returns null for states whose backport behaviour is kept.
 */
public final class GangsTrialSpawnerLogic {
    private static final float TIME_BETWEEN_EACH_EJECTION = 30.0F;
    private static final Identifier OMINOUS_CONSUMABLES = new Identifier("minecraft", "spawners/ominous/trial_chamber/consumables");

    private GangsTrialSpawnerLogic() {
    }

    public static TrialSpawnerState tick(TrialSpawnerState state, BlockPos pos, TrialSpawner spawner, ServerWorld level, Runnable spawnOminousItemSpawner) {
        TrialSpawnerData data = spawner.getData();
        long now = level.getTime();
        switch (state) {
            case WAITING_FOR_PLAYERS: {
                if (!data.hasMobToSpawn(spawner, level.random)) {
                    return TrialSpawnerState.INACTIVE;
                }
                data.tryDetectPlayers(level, pos, spawner);
                if (data.detectedPlayers.isEmpty()) {
                    return state;
                }
                data.totalMobsSpawned = 0;
                return TrialSpawnerState.ACTIVE;
            }
            case ACTIVE: {
                if (!data.hasMobToSpawn(spawner, level.random)) {
                    return TrialSpawnerState.INACTIVE;
                }
                data.currentMobs.removeIf(uuid -> {
                    Entity e = level.getEntity(uuid);
                    return e == null || !e.isAlive();
                });
                // Same as countAdditionalPlayers, minus its warning spam when nobody is detected.
                int additionalPlayers = Math.max(0, data.detectedPlayers.size() - 1);
                data.tryDetectPlayers(level, pos, spawner);
                if (spawner.isOminous()) {
                    spawnOminousItemSpawner.run();
                }
                TrialSpawnerConfig config = spawner.getConfig();
                if (data.totalMobsSpawned >= Math.max(1, config.calculateTargetTotalMobs(additionalPlayers))) {
                    if (data.currentMobs.isEmpty()) {
                        data.cooldownEndsAt = now + spawner.getTargetCooldownLength();
                        data.totalMobsSpawned = 0;
                        data.nextMobSpawnsAt = 0L;
                        return TrialSpawnerState.WAITING_FOR_REWARD_EJECTION;
                    }
                } else if (now >= data.nextMobSpawnsAt
                    && data.currentMobs.size() < Math.max(1, config.calculateTargetSimultaneousMobs(additionalPlayers))) {
                    spawner.spawnMob(level, pos).ifPresent(uuid -> {
                        data.currentMobs.add(uuid);
                        data.totalMobsSpawned++;
                        data.nextMobSpawnsAt = now + config.ticksBetweenSpawn();
                        config.spawnPotentialsDefinition().getOrEmpty(level.random).ifPresent(entry -> {
                            data.nextSpawnData = Optional.of((MobSpawnerEntry) entry.getData());
                            spawner.markUpdated();
                        });
                    });
                }
                return state;
            }
            case EJECTING_REWARD: {
                if (!data.isReadyToEjectItems(level, TIME_BETWEEN_EACH_EJECTION, spawner.getTargetCooldownLength())) {
                    return state;
                }
                if (data.detectedPlayers.isEmpty()) {
                    level.playSound(null, pos, ModSounds.TRIAL_SPAWNER_CLOSE_SHUTTER.get(), SoundCategory.BLOCKS);
                    data.ejectingLootTable = Optional.empty();
                    data.wavesCompleted = 0;
                    data.wavesRequired = 0;
                    data.mobsSpawnedThisWave = 0;
                    return TrialSpawnerState.COOLDOWN;
                }
                // One reward roll per player who took part.
                spawner.ejectReward(level, pos, chooseReward(spawner.isOminous(), level));
                data.detectedPlayers.remove(data.detectedPlayers.iterator().next());
                return state;
            }
            case COOLDOWN: {
                boolean wasOminous = spawner.isOminous();
                data.tryDetectPlayers(level, pos, spawner);
                if (!wasOminous && spawner.isOminous()) {
                    data.totalMobsSpawned = 0;
                    return TrialSpawnerState.ACTIVE;
                }
                data.detectedPlayers.clear();
                if (data.isCooldownFinished(level)) {
                    spawner.removeOminous(level, pos);
                    data.reset();
                    return TrialSpawnerState.WAITING_FOR_PLAYERS;
                }
                return state;
            }
            default:
                return null;
        }
    }

    private static Identifier chooseReward(boolean ominous, ServerWorld level) {
        boolean key = level.random.nextFloat() < TrialSpawnerFixes.keyChance();
        if (ominous) {
            return key ? ModBuiltInLootTables.SPAWNER_OMINOUS_TRIAL_CHAMBER_KEY : OMINOUS_CONSUMABLES;
        }
        return key ? ModBuiltInLootTables.SPAWNER_TRIAL_CHAMBER_KEY : ModBuiltInLootTables.SPAWNER_TRIAL_CHAMBER_CONSUMABLES;
    }
}
