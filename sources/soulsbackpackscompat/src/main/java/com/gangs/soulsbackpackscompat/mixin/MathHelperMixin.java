package com.gangs.soulsbackpackscompat.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedcore.util.MathHelper", remap = false)
public abstract class MathHelperMixin {
	@Inject(method = "intMaxCappedMultiply(II)I", at = @At("HEAD"), cancellable = true, remap = false)
	private static void soulsbackpackscompat$guardZeroStackCount(int itemMaxCount, int multiplier,
			CallbackInfoReturnable<Integer> cir) {
		if (itemMaxCount <= 0) {
			cir.setReturnValue(0);
		}
	}
}
