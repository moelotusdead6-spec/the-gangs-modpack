package com.gangs.mounts.mixin;

import com.cosmeticsmod.morecosmetics.gui.core.box.BoxManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import v1_20.morecosmetics.gui.screen.BaseUI;

@Mixin(value = BaseUI.class, remap = false)
public interface WardrobeManagerAccess {
	@Accessor("boxManager")
	BoxManager gangsmounts$boxManager();
}
