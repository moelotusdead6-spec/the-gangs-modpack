package com.elysium.goldclaim.mixin;

import com.elysium.goldclaim.HubTeleportPolicy;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class HubRandomTeleportMixin {
    @Inject(method = "teleport(DDDZ)Z", at = @At("HEAD"), cancellable = true)
    private void goldclaim$denyRandomTeleport(double x, double y, double z, boolean particles,
            CallbackInfoReturnable<Boolean> cir) {
        if (HubTeleportPolicy.deny((LivingEntity)(Object)this)) cir.setReturnValue(false);
    }
}
