package com.gangs.mounts.mixin;

import java.util.HashMap;
import java.util.Map;
import com.google.gson.JsonElement;
import com.thegangs.gangshats.GangsHats;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(RecipeManager.class)
public abstract class StaffRecipeMixin {
	@ModifyVariable(method = "apply(Ljava/util/Map;Lnet/minecraft/resource/ResourceManager;Lnet/minecraft/util/profiler/Profiler;)V",
			at = @At("HEAD"), argsOnly = true)
	private Map<Identifier, JsonElement> removeStaffRecipe(Map<Identifier, JsonElement> recipes) {
		Map<Identifier, JsonElement> filtered = new HashMap<>(recipes);
		if (filtered.remove(new Identifier("mythicmounts", "summoning_staff")) != null) {
			GangsHats.LOGGER.info("Disabled Mythic Mounts Summoning Staff recipe");
		}
		return filtered;
	}
}
