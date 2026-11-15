package com.gangs.mounts.mixin;

import com.gangs.mounts.MountService;
import net.minecraft.entity.Entity;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "software.bernie.geckolib.core.animation.AnimationState", remap = false)
public abstract class MountAnimationMixin {
	@Shadow @Final private GeoAnimatable animatable;

	@Inject(method = "isMoving", at = @At("RETURN"), cancellable = true)
	private void includeRiddenDisplacement(CallbackInfoReturnable<Boolean> ci) {
		if (!(animatable instanceof Entity entity) || !MountService.isMythicMount(entity) || !entity.hasPassengers()) return;
		double x = entity.getX() - entity.prevX;
		double z = entity.getZ() - entity.prevZ;
		// Remote ridden entities interpolate their position without a matching velocity.
		ci.setReturnValue(x * x + z * z > 0.0001);
	}
}
