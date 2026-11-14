package com.gangs.mounts.mixin;

import com.gangs.mounts.MountService;
import com.thegangs.gangshats.GangsHats;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GangsHats.class, remap = false)
public abstract class MountSelectionMixin {
	@Inject(method = "handleSelectRequest", at = @At("HEAD"), cancellable = true)
	private static void handleMount(ServerPlayerEntity player, String slot, String id, CallbackInfo ci) {
		if (!MountService.SLOT.equals(slot)) return;
		if (id.isEmpty()) MountService.recall(player);
		else MountService.summon(player, id);
		GangsHats.sendWardrobe(player);
		ci.cancel();
	}

	@Inject(method = {
			"clearCosmetics(Lnet/minecraft/server/network/ServerPlayerEntity;)V",
			"clearCosmetics(Lnet/minecraft/class_3222;)V"
	}, at = @At("HEAD"))
	private static void clearMount(ServerPlayerEntity player, CallbackInfo ci) {
		MountService.recall(player);
	}
}
