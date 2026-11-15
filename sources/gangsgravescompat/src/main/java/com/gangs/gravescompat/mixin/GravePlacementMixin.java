package com.gangs.gravescompat.mixin;

import eu.pb4.graves.config.Config;
import eu.pb4.graves.config.ConfigManager;
import eu.pb4.graves.other.GraveUtils;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;
import net.minecraft.world.border.WorldBorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GraveUtils.class, remap = false)
public abstract class GravePlacementMixin {
    @Unique
    private static final Logger LOGGER = LoggerFactory.getLogger("gangsgravescompat");

    @Shadow
    private static GraveUtils.BlockResult isValidPos(ServerPlayerEntity player, ServerWorld world,
            WorldBorder border, BlockPos pos, boolean anyBlock, Config config) {
        throw new AssertionError("Mixin shadow");
    }

    @Inject(method = "findGravePosition", at = @At("HEAD"), cancellable = true)
    private static void safeHeight(ServerPlayerEntity player, ServerWorld world, BlockPos death,
                                   int maxDistance, boolean anyBlock,
                                   CallbackInfoReturnable<GraveUtils.BlockCheckResult> cir) {
        if (death.getY() >= world.getBottomY() && death.getY() < world.getTopY()) return;
        var border = world.getWorldBorder();
        int x = MathHelper.floor(MathHelper.clamp(death.getX(),
            border.getBoundWest() + 1, border.getBoundEast() - 1));
        int z = MathHelper.floor(MathHelper.clamp(death.getZ(),
            border.getBoundNorth() + 1, border.getBoundSouth() - 1));
        int surface = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        int preferredY = MathHelper.clamp(surface > world.getBottomY() ? surface + 1 : 64,
            world.getBottomY() + 1, world.getTopY() - 3);
        var config = ConfigManager.getConfig();
        for (int distance = 0; distance < world.getHeight(); distance++) {
            for (int sign : distance == 0 ? new int[]{1} : new int[]{1, -1}) {
                int y = preferredY + distance * sign;
                if (y <= world.getBottomY() || y > world.getTopY() - 3) continue;
                BlockPos pos = new BlockPos(x, y, z);
                if (!world.getBlockState(pos).isAir()
                        || !world.getBlockState(pos.up()).isAir()
                        || !world.getBlockState(pos.up(2)).isAir()) continue;
                var result = isValidPos(player, world, border, pos, false, config);
                if (!result.canCreate()) continue;
                LOGGER.info("Relocating {} grave from {} to {} in {}.",
                    player.getGameProfile().getName(), death, pos, world.getRegistryKey().getValue());
                cir.setReturnValue(new GraveUtils.BlockCheckResult(pos, result));
                return;
            }
        }
        LOGGER.error("No safe same-column grave position for {} at {} in {}; using upstream search.",
            player.getGameProfile().getName(), death, world.getRegistryKey().getValue());
    }
}
