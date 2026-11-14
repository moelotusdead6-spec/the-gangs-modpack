package com.gangs.mounts.mixin;

import com.gangs.mounts.MountService;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnimalEntity.class)
public abstract class MountBreedingMixin {
	@Inject(method = "lovePlayer", at = @At("HEAD"), cancellable = true)
	private void preventLove(PlayerEntity player, CallbackInfo ci) {
		if (MountService.managed((AnimalEntity) (Object) this)) ci.cancel();
	}
	@Inject(method = "setLoveTicks", at = @At("HEAD"), cancellable = true)
	private void preventLoveTicks(int ticks, CallbackInfo ci) {
		if (ticks > 0 && MountService.managed((AnimalEntity) (Object) this)) ci.cancel();
	}
}
