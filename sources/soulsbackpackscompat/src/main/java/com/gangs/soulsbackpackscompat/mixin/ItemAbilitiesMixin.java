package com.gangs.soulsbackpackscompat.mixin;

import net.minecraft.item.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

// Apply after Soulslike Weaponry has added its getter to Item.
@Mixin(value = Item.class, priority = 900)
public abstract class ItemAbilitiesMixin {
	@Unique
	private static final Logger soulsbackpackscompat$LOGGER = LoggerFactory.getLogger("soulsbackpackscompat");

	@Unique
	private List<?> soulsbackpackscompat$repairedAbilities;

	@Inject(method = "getAbilities()Ljava/util/List;", at = @At("RETURN"), cancellable = true, remap = false)
	private void soulsbackpackscompat$repairAbilities(CallbackInfoReturnable<List<?>> cir) {
		if (cir.getReturnValue() != null) {
			return;
		}
		if (soulsbackpackscompat$repairedAbilities == null) {
			soulsbackpackscompat$repairedAbilities = new ArrayList<>();
			soulsbackpackscompat$LOGGER.warn("Initialized missing Soulslike Weaponry ability list for item class {}",
					getClass().getName());
		}
		cir.setReturnValue(soulsbackpackscompat$repairedAbilities);
	}
}
