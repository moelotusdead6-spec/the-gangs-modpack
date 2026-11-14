package com.gangs.mounts.mixin;

import com.gangs.mounts.MountService;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public abstract class MountTravelMixin {
	@Inject(method = "moveToWorld", at = @At("HEAD"))
	private void recallBeforePortal(ServerWorld destination, CallbackInfoReturnable<Entity> ci) { recall(destination); }
	@Inject(method = "teleport(Lnet/minecraft/server/world/ServerWorld;DDDFF)V", at = @At("HEAD"))
	private void recallBeforeTeleport(ServerWorld destination, double x, double y, double z, float yaw, float pitch, CallbackInfo ci) {
		recall(destination);
	}
	private void recall(ServerWorld destination) {
		ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
		if (player.getWorld() != destination) MountService.recall(player);
	}
}
