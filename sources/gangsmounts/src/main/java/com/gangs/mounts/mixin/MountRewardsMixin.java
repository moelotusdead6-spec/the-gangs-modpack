package com.gangs.mounts.mixin;

import java.util.ArrayList;
import java.util.List;
import com.gangs.mounts.MountService;
import com.thegangs.gangshats.CosmeticRewards;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CosmeticRewards.class, remap = false)
public abstract class MountRewardsMixin {
	@Inject(method = "allIds", at = @At("RETURN"), cancellable = true)
	private static void addMounts(CallbackInfoReturnable<List<String>> ci) {
		List<String> rewards = new ArrayList<>(ci.getReturnValue());
		rewards.addAll(MountService.ids());
		ci.setReturnValue(List.copyOf(rewards));
	}

	@Inject(method = "slotFor", at = @At("HEAD"), cancellable = true)
	private static void mountSlot(String id, CallbackInfoReturnable<String> ci) {
		if (MountService.contains(id)) ci.setReturnValue(MountService.SLOT);
	}

	@Inject(method = "displayName", at = @At("HEAD"), cancellable = true)
	private static void mountName(String id, CallbackInfoReturnable<Text> ci) {
		if (MountService.contains(id)) ci.setReturnValue(MountService.name(id));
	}
}
