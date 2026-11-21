package com.elysium.goldclaim.mixin;

import com.elysium.goldclaim.AlexCavesProtection;
import com.github.alexmodguy.alexscaves.server.entity.living.UnderzealotEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Underzealots never begin the Vesper sacrifice that summons the Forsaken while in /wild. */
@Pseudo
@Mixin(targets = "com.github.alexmodguy.alexscaves.server.entity.ai.UnderzealotSacrificeGoal", remap = false)
public abstract class AlexCavesSacrificeMixin {
    @Shadow
    @Final
    private UnderzealotEntity entity;

    // @Pseudo targets are not remapped by the annotation processor: list the dev and intermediary names of Goal#canStart.
    @Inject(method = {"canStart", "method_6264"}, at = @At("HEAD"), cancellable = true)
    private void goldclaim$noWildForsakenRitual(CallbackInfoReturnable<Boolean> cir) {
        if (AlexCavesProtection.isWild(this.entity.getWorld())) {
            cir.setReturnValue(false);
        }
    }
}
