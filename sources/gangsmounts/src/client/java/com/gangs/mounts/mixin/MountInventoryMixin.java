package com.gangs.mounts.mixin;

import com.gangs.mounts.MountService;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public abstract class MountInventoryMixin {
	@Inject(method = "openRidingInventory", at = @At("HEAD"), cancellable = true)
	private void openPlayerInventory(CallbackInfo ci) {
		ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
		if (player.getVehicle() == null || !MountService.isMythicMount(player.getVehicle()) || Screen.hasControlDown()) return;
		MinecraftClient.getInstance().setScreen(new InventoryScreen(player));
		ci.cancel();
	}
}
