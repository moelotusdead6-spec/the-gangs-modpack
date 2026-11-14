package com.gangs.mounts.mixin;

import com.gangs.mounts.MountService;
import com.thegangs.gangshats.GangsHats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerWorld.class)
public abstract class MountSpawnMixin {
	@Inject(method = "spawnEntity", at = @At("HEAD"), cancellable = true)
	private void requireCosmeticSummon(Entity entity, CallbackInfoReturnable<Boolean> ci) {
		if (MountService.isMythicMount(entity) && entity instanceof AbstractHorseEntity horse
				&& (!MountService.managed(horse) || MountService.active(horse.getOwnerUuid()) != horse)) {
			GangsHats.LOGGER.debug("Blocked non-cosmetic Mythic mount spawn: {}", entity.getType());
			ci.setReturnValue(false);
		}
	}
}
