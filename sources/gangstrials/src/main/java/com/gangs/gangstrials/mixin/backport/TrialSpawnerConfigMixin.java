package com.gangs.gangstrials.mixin.backport;

import com.drd.trickytrialsbackport.block.entity.trialspawner.TrialSpawnerConfig;
import com.gangs.gangstrials.TrialSpawnerFixes;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TrialSpawnerConfig.class, remap = false)
public abstract class TrialSpawnerConfigMixin {
    @Inject(method = "fromTag", at = @At("HEAD"), cancellable = true)
    private static void gangstrials$parseVanillaConfig(NbtCompound tag, CallbackInfoReturnable<TrialSpawnerConfig> cir) {
        cir.setReturnValue(TrialSpawnerFixes.parseConfig(tag));
    }
}
