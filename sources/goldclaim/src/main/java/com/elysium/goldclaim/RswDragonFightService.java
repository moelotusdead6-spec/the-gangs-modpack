package com.elysium.goldclaim;

import net.minecraft.entity.boss.dragon.EnderDragonFight;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to read RSW dragon fight data from " + file, e);
                }
            }
            EnderDragonFight.Data data = EnderDragonFight.Data.DEFAULT;
            if (nbt != null && nbt.contains("DragonFight")) {
                var parseResult = EnderDragonFight.Data.CODEC.parse(NbtOps.INSTANCE, nbt.get("DragonFight"));
                data = parseResult.getOrThrow(false, message ->
                    LOGGER.error("Invalid RSW dragon fight data in {}: {}", file, message));
            }
            EnderDragonFight fight = new EnderDragonFight(world, world.getSeed(), data, BlockPos.ORIGIN);
            world.setEnderDragonFight(fight);
            LOGGER.info("Initialized RSW EnderDragonFight for dimension {}", world.getRegistryKey().getValue());
        } catch (RuntimeException failure) {
            throw new IllegalStateException("Failed to initialize RSW EnderDragonFight", failure);
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
            Files.createDirectories(file.toPath().getParent());
            NbtCompound nbt = new NbtCompound();
            var encoded = EnderDragonFight.Data.CODEC.encodeStart(NbtOps.INSTANCE, data);
            nbt.put("DragonFight", encoded.getOrThrow(false, message ->
                LOGGER.error("Cannot encode RSW dragon fight data: {}", message)));

            NbtIo.writeCompressed(nbt, file);
        } catch (Exception e) {
            LOGGER.error("Failed to save RSW dragon fight data to {}", file, e);
        }
    }
}
