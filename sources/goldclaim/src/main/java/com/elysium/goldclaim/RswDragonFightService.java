package com.elysium.goldclaim;

import net.minecraft.entity.boss.dragon.EnderDragonFight;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;

public class RswDragonFightService {
    private static final Logger LOGGER = LoggerFactory.getLogger("GoldClaim/RswDragonFight");
    private static final String FIGHT_FILE = "rsw_dragon_fight.dat";

    public static void ensureDragonFight(ServerWorld world) {
        if (world == null || !GoldClaimMod.RSW_THE_END_DIMENSION.equals(world.getRegistryKey().getValue().toString())) {
            return;
        }
        if (world.getEnderDragonFight() != null) {
            return;
        }
        try {
            File file = getFightFile(world);
            NbtCompound nbt = null;
            if (file.exists()) {
                try {
                    nbt = NbtIo.readCompressed(file);
                } catch (Exception e) {
                    LOGGER.error("Failed to read RSW dragon fight data from {}", file, e);
                }
            }
            EnderDragonFight.Data data = EnderDragonFight.Data.DEFAULT;
            if (nbt != null && nbt.contains("DragonFight")) {
                var parseResult = EnderDragonFight.Data.CODEC.parse(NbtOps.INSTANCE, nbt.get("DragonFight"));
                if (parseResult.result().isPresent()) {
                    data = parseResult.result().get();
                }
            }
            EnderDragonFight fight = new EnderDragonFight(world, world.getSeed(), data, BlockPos.ORIGIN);
            if (nbt != null) {
                try {
                    Class<?> idragonFightClass = Class.forName("com.yungnickyoung.minecraft.betterendisland.world.IDragonFight");
                    if (idragonFightClass.isInstance(fight)) {
                        if (nbt.contains("BEI_firstExitPortalSpawn")) {
                            Method mSetFirst = idragonFightClass.getMethod("betterendisland$setFirstExitPortalSpawn", boolean.class);
                            mSetFirst.invoke(fight, nbt.getBoolean("BEI_firstExitPortalSpawn"));
                        }
                        if (nbt.contains("BEI_hasDragonEverSpawned")) {
                            Method mSetEver = idragonFightClass.getMethod("betterendisland$setHasDragonEverSpawned", boolean.class);
                            mSetEver.invoke(fight, nbt.getBoolean("BEI_hasDragonEverSpawned"));
                        }
                        if (nbt.contains("BEI_numTimesDragonKilled")) {
                            Method mSetKilled = idragonFightClass.getMethod("betterendisland$setNumTimesDragonKilled", int.class);
                            mSetKilled.invoke(fight, nbt.getInt("BEI_numTimesDragonKilled"));
                        }
                    }
                } catch (Throwable ignored) {}
            }
            world.setEnderDragonFight(fight);
            LOGGER.info("Initialized RSW EnderDragonFight for dimension {}", world.getRegistryKey().getValue());
        } catch (Throwable t) {
            LOGGER.error("Failed to initialize RSW EnderDragonFight", t);
        }
    }

    public static void tickDragonFight(ServerWorld world) {
        if (world == null || !GoldClaimMod.RSW_THE_END_DIMENSION.equals(world.getRegistryKey().getValue().toString())) {
            return;
        }
        EnderDragonFight fight = world.getEnderDragonFight();
        if (fight == null) {
            ensureDragonFight(world);
            fight = world.getEnderDragonFight();
            if (fight == null) return;
        }

        try {
            Class<?> idragonFightClass = Class.forName("com.yungnickyoung.minecraft.betterendisland.world.IDragonFight");
            if (idragonFightClass.isInstance(fight)) {
                Method bellMethod = idragonFightClass.getMethod("betterendisland$tickBellSound");
                bellMethod.invoke(fight);

                Method everSpawnedMethod = idragonFightClass.getMethod("betterendisland$hasDragonEverSpawned");
                boolean everSpawned = (boolean) everSpawnedMethod.invoke(fight);

                Method stageMethod = idragonFightClass.getMethod("betterendisland$getDragonRespawnStage");
                Object stage = stageMethod.invoke(fight);

                if (!everSpawned && stage == null && (world.getTime() % 5L == 0L) && !world.getPlayers().isEmpty()) {
                    for (ServerPlayerEntity player : world.getPlayers()) {
                        if (!player.isSpectator() && player.getBlockPos().isWithinDistance(BlockPos.ORIGIN, 32.0)) {
                            Method initialRespawn = idragonFightClass.getMethod("betterendisland$initialRespawn");
                            initialRespawn.invoke(fight);
                            LOGGER.info("Triggered YUNG's Better End Island initial respawn in RSW The End for player {}", player.getName().getString());
                            break;
                        }
                    }
                }
            }
        } catch (ClassNotFoundException ignored) {
        } catch (Throwable t) {
            LOGGER.warn("Failed to tick Better End Island integration in RSW The End", t);
        }
    }

    private static File getFightFile(ServerWorld world) {
        Path dimDir = world.getServer().getSavePath(WorldSavePath.ROOT)
            .resolve("dimensions").resolve("multiworld").resolve("rsw_the_end");
        return dimDir.resolve(FIGHT_FILE).toFile();
    }

    public static void saveFightData(ServerWorld world, EnderDragonFight.Data data) {
        if (data == null) return;
        File file = getFightFile(world);
        try {
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            NbtCompound nbt = new NbtCompound();
            var encoded = EnderDragonFight.Data.CODEC.encodeStart(NbtOps.INSTANCE, data);
            encoded.result().ifPresent(tag -> nbt.put("DragonFight", tag));

            EnderDragonFight fight = world.getEnderDragonFight();
            if (fight != null) {
                try {
                    Class<?> idragonFightClass = Class.forName("com.yungnickyoung.minecraft.betterendisland.world.IDragonFight");
                    if (idragonFightClass.isInstance(fight)) {
                        Method mFirst = idragonFightClass.getMethod("betterendisland$firstExitPortalSpawn");
                        Method mEver = idragonFightClass.getMethod("betterendisland$hasDragonEverSpawned");
                        Method mKilled = idragonFightClass.getMethod("betterendisland$numTimesDragonKilled");
                        nbt.putBoolean("BEI_firstExitPortalSpawn", (boolean) mFirst.invoke(fight));
                        nbt.putBoolean("BEI_hasDragonEverSpawned", (boolean) mEver.invoke(fight));
                        nbt.putInt("BEI_numTimesDragonKilled", (int) mKilled.invoke(fight));
                    }
                } catch (Throwable ignored) {}
            }

            NbtIo.writeCompressed(nbt, file);
        } catch (Exception e) {
            LOGGER.error("Failed to save RSW dragon fight data to {}", file, e);
        }
    }
}
