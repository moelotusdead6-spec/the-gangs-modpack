package com.gangs.gangstrials.mixin.backport;

import com.drd.trickytrialsbackport.entity.monster.breeze.Breeze;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla breezes only attack players and iron golems; the backport lets them target any visible mob, including each other. */
@Mixin(LivingEntity.class)
public abstract class BreezeTargetMixin {
    @Inject(method = "canTarget(Lnet/minecraft/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
    private void gangstrials$breezeTargetsPlayersOnly(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Breeze && !(target instanceof PlayerEntity) && !(target instanceof IronGolemEntity)) {
            cir.setReturnValue(false);
        }
    }
}
