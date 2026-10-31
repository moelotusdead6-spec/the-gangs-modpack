package com.elysium.goldclaim.mixin;

import com.elysium.goldclaim.HubTeleportPolicy;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnderPearlEntity.class)
public abstract class HubPearlTeleportMixin {
    @Inject(method = "onCollision", at = @At("HEAD"), cancellable = true)
    private void goldclaim$denyPearlTeleport(HitResult hit, CallbackInfo ci) {
        EnderPearlEntity pearl = (EnderPearlEntity)(Object)this;
        if (HubTeleportPolicy.deny(pearl.getOwner())) {
            pearl.discard();
            ci.cancel();
        }
    }
}
