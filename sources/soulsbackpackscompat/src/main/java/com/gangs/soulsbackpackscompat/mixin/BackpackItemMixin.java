package com.gangs.soulsbackpackscompat.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

import java.util.Collections;
import java.util.List;

@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackItem", remap = false)
public abstract class BackpackItemMixin {
	public List<?> getAbilities() {
		return Collections.emptyList();
	}
}
