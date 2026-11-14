package com.gangs.mounts.mixin;

import com.gangs.mounts.client.MountKeyElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import v1_20.morecosmetics.gui.screen.ScreenWrapper;

@Mixin(value = ScreenWrapper.class, remap = false)
public abstract class MountKeyScreenMixin {
	@Inject(method = {"keyPressed(III)Z", "method_25404(III)Z"}, at = @At("HEAD"), cancellable = true)
	private void captureMountKey(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> ci) {
		if (MountKeyElement.captureKey(keyCode, scanCode)) ci.setReturnValue(true);
	}

	@Inject(method = {"mouseClicked(DDI)Z", "method_25402(DDI)Z"}, at = @At("HEAD"))
	private void cancelPreviousCapture(double x, double y, int button, CallbackInfoReturnable<Boolean> ci) {
		MountKeyElement.cancelCapture();
	}

	@Inject(method = {"removed()V", "method_25432()V"}, at = @At("HEAD"))
	private void cancelOnClose(CallbackInfo ci) {
		MountKeyElement.cancelCapture();
	}
}
