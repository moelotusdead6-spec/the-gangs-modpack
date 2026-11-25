package com.gangs.mounts.mixin;

import com.cosmeticsmod.morecosmetics.gui.core.GuiComponent;
import com.cosmeticsmod.morecosmetics.gui.core.box.BoxManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = BoxManager.class, remap = false)
public interface BoxManagerAccess {
	@Accessor("selectedCategory")
	void gangsmounts$selectedCategory(int index);

	@Accessor("selectedEntry")
	void gangsmounts$selectedEntry(GuiComponent entry);
}
