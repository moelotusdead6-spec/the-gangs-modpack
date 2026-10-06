package com.elysium.goldclaim.mixin;

import com.elysium.goldclaim.GoldClaimMod;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Explosion.class)
public abstract class PvpExplosionMixin {
    @Shadow @Final private World world;

    @Shadow public abstract void clearAffectedBlocks();

    @Inject(method = "affectWorld", at = @At("HEAD"))
    private void goldclaim$preservePvpTerrain(boolean particles, CallbackInfo callbackInfo) {
        if (this.world instanceof ServerWorld serverWorld && GoldClaimMod.isPvpWorld(serverWorld)) {
            this.clearAffectedBlocks();
        }
    }
}