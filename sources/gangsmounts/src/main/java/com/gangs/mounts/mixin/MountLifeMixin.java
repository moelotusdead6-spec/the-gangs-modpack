package com.gangs.mounts.mixin;

import com.gangs.mounts.MountService;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class MountLifeMixin {
	@Inject(method = "setHealth", at = @At("HEAD"), cancellable = true)
	private void protectHealth(float health, CallbackInfo ci) {
		LivingEntity entity = (LivingEntity) (Object) this;
		if (MountService.managed(entity) && (!Float.isFinite(health) || health < entity.getHealth())) ci.cancel();
	}
	@Inject(method = "damage", at = @At("HEAD"), cancellable = true)
	private void rejectDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> ci) {
		if (MountService.managed((LivingEntity) (Object) this)) ci.setReturnValue(false);
	}
	@Inject(method = "kill", at = @At("HEAD"), cancellable = true)
	private void protectKill(CallbackInfo ci) {
		if (MountService.managed((LivingEntity) (Object) this)) ci.cancel();
	}
}
