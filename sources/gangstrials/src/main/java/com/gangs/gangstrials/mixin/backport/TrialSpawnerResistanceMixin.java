package com.gangs.gangstrials.mixin.backport;

import com.drd.trickytrialsbackport.block.TrialSpawnerBlock;
import net.minecraft.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
public abstract class TrialSpawnerResistanceMixin {
    @Inject(method = "getBlastResistance", at = @At("HEAD"), cancellable = true)
    private void gangstrials$trialSpawnerResistance(CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof TrialSpawnerBlock) {
            cir.setReturnValue(50.0F);
        }
    }
}
