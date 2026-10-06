package com.elysium.goldclaim.mixin;

import com.elysium.goldclaim.GoldClaimMod;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public abstract class PvpWorldTimeMixin {
    @Inject(method = "getTimeOfDay", at = @At("HEAD"), cancellable = true)
    private void goldclaim$fixedPvpNoon(CallbackInfoReturnable<Long> callbackInfo) {
        Object world = this;
        if (world instanceof ServerWorld serverWorld && GoldClaimMod.isPvpWorld(serverWorld)) {
            callbackInfo.setReturnValue(6000L);
        }
    }
}