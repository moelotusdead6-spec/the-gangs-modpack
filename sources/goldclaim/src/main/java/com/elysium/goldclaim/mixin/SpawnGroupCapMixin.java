package com.elysium.goldclaim.mixin;

import com.elysium.goldclaim.MobCapPolicy;
import net.minecraft.entity.SpawnGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SpawnGroup.class)
public abstract class SpawnGroupCapMixin {
    // Both the global and per-player natural spawn caps read this value.
    @Inject(method = "getCapacity", at = @At("RETURN"), cancellable = true)
    private void goldclaim$stricterCap(CallbackInfoReturnable<Integer> callback) {
        callback.setReturnValue(MobCapPolicy.capacity(callback.getReturnValueI()));
    }
}
