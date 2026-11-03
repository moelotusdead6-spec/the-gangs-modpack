package com.elysium.goldclaim.mixin;

import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.github.alexmodguy.alexscaves.server.entity.living.TremorzillaEntity", remap = false)
public abstract class AlexCavesTremorzillaMixin {
    @Inject(method = "breakBlocksAround", at = @At("HEAD"), cancellable = true)
    private void goldclaim$preventBeamAndAttackDestruction(Vec3d center, float radius, boolean square,
            boolean triggerExplosions, float dropChance, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }

    @Inject(method = "breakBlocksInBoundingBox", at = @At("HEAD"), cancellable = true)
    private void goldclaim$preventMovementDestruction(float dropChance, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
