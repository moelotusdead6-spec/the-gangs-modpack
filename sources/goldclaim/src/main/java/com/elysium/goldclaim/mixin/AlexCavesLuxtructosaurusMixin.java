package com.elysium.goldclaim.mixin;

import com.elysium.goldclaim.AlexCavesProtection;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.github.alexmodguy.alexscaves.server.entity.living.LuxtructosaurusEntity", remap = false)
public abstract class AlexCavesLuxtructosaurusMixin {
    @Inject(method = "tickStompAttack", at = @At("HEAD"), cancellable = true)
    private void goldclaim$preventStompTerrainChanges(CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "placeFissureBlock", at = @At("HEAD"), cancellable = true)
    private void goldclaim$preventFissures(BlockPos.Mutable pos, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }

    @WrapMethod(method = "burnWithBreath")
    private void goldclaim$preventBreathFire(float distance, Operation<Void> original) {
        AlexCavesProtection.whileActing((Entity)(Object)this, () -> original.call(distance));
    }
}
