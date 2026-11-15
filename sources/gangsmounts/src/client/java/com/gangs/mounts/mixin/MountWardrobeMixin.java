package com.gangs.mounts.mixin;

import java.util.List;
import com.cosmeticsmod.morecosmetics.gui.WardrobeGui;
import com.cosmeticsmod.morecosmetics.gui.core.box.BoxManager;
import com.cosmeticsmod.morecosmetics.gui.core.box.utils.BoxCategory;
import com.gangs.mounts.client.MountClient;
import com.gangs.mounts.client.MountElement;
import com.gangs.mounts.client.MountKeyElement;
import com.gangs.mounts.client.MountBackElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = WardrobeGui.class, remap = false)
public abstract class MountWardrobeMixin {
	@Inject(method = "fillGui", at = @At("RETURN"))
	private void addMountCategory(List<BoxCategory> categories, CallbackInfo ci) {
		MountKeyElement.cancelCapture();
		for (BoxCategory category : categories) {
			if (category.getName().equals("Settings") && category.isListCategory()) {
				category.getEntries().add(new MountKeyElement("Summon Last Mount", MountClient.summonKey()));
				category.getEntries().add(new MountKeyElement("Recall Mount", MountClient.recallKey()));
				break;
			}
		}
		if (MountClient.ids().isEmpty()) return;
		BoxCategory mounts = new BoxCategory("Mounts", "pets").setTitle("Mounts");
		String type = MountClient.variantType();
		if (type == null) {
			MountClient.types().forEach(id -> mounts.getEntries().add(new MountElement(MountClient.variants(id).get(0), true)));
		} else {
			mounts.setTitle("Mount variants");
			mounts.getEntries().add(new MountBackElement());
			MountClient.variants(type).forEach(id -> mounts.getEntries().add(new MountElement(id)));
		}
		int index = categories.indexOf(BoxManager.SEPARATION_LINE);
		categories.add(index < 0 ? categories.size() : index, mounts);
	}
}
