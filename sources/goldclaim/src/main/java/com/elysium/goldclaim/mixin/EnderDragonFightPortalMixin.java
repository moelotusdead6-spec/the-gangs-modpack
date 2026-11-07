package com.elysium.goldclaim.mixin;

import com.elysium.goldclaim.ExitPortalPlacement;
import net.minecraft.entity.boss.dragon.EnderDragonFight;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.EndPortalFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnderDragonFight.class)
public abstract class EnderDragonFightPortalMixin {
    private static final Logger GOLDCLAIM$LOGGER = LoggerFactory.getLogger("GoldClaim/ExitPortal");

    @Shadow @Final private ServerWorld world;
    @Shadow @Final private BlockPos origin;
    @Shadow private BlockPos exitPortalLocation;

    @Inject(method = "generateEndPortal", at = @At("HEAD"), cancellable = true)
    private void goldclaim$placeOnSurface(boolean previouslyKilled, CallbackInfo ci) {
        // BetterEnd moves every exit portal to one per-save position; non-vanilla Ends build their own fountain.
        if (this.world.getRegistryKey() == World.END) return;
        BlockPos center = this.exitPortalLocation != null ? this.exitPortalLocation : EndPortalFeature.offsetOrigin(this.origin);
        int surface = ExitPortalPlacement.surfaceY(this.world, center);
        if (surface == Integer.MIN_VALUE) return;
        BlockPos old = this.exitPortalLocation;
        BlockPos target = old != null && !ExitPortalPlacement.isBuried(old.getY(), surface)
            ? old : new BlockPos(center.getX(), surface, center.getZ());
        if (!target.equals(old)) {
            GOLDCLAIM$LOGGER.info("Placing exit portal in {} at {} (was {})", this.world.getRegistryKey().getValue(), target, old);
        }
        this.exitPortalLocation = target;
        ExitPortalPlacement.clearStrayPortals(this.world, target);
        ExitPortalPlacement.placeFountain(this.world, target, previouslyKilled);
        ci.cancel();
    }
}
