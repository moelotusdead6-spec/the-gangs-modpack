package com.elysium.goldclaim.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.github.alexmodguy.alexscaves.server.entity.living.HullbreakerEntity", remap = false)
public abstract class AlexCavesHullbreakerMixin {
    @Inject(method = "breakBlock", at = @At("HEAD"), cancellable = true)
    private void goldclaim$preventBashDestruction(CallbackInfo ci) {
        ci.cancel();
    }
}
