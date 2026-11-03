package com.elysium.goldclaim.mixin;

import com.elysium.goldclaim.AlexCavesProtection;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.github.alexmodguy.alexscaves.server.entity.living.SauropodBaseEntity", remap = false)
public abstract class AlexCavesSauropodMixin {
    @Inject(method = "crushBlocksInRing", at = @At("HEAD"), cancellable = true)
    private void goldclaim$preventBossCrushing(int width, int x, int z, float dropChance, CallbackInfo ci) {
        if (AlexCavesProtection.isLuxtructosaurus((Entity)(Object)this)) ci.cancel();
    }
}
