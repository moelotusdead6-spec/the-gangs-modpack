package com.elysium.goldclaim.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "org.betterx.betterend.world.generator.GeneratorOptions", remap = false)
public abstract class BetterEndCityFrequencyMixin {
    @ModifyReturnValue(method = "getEndCityFailChance", at = @At("RETURN"), remap = false)
    private static int goldclaim$removeExtraCityVeto(int configured) {
        return Math.min(configured, 1);
    }
}
