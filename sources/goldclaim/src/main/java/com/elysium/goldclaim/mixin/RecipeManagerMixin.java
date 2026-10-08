package com.elysium.goldclaim.mixin;

import com.elysium.goldclaim.BannedContent;
import com.elysium.goldclaim.RecipePolicy;
import com.google.gson.JsonElement;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.util.Identifier;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {
    @ModifyVariable(method = "apply(Ljava/util/Map;Lnet/minecraft/resource/ResourceManager;Lnet/minecraft/util/profiler/Profiler;)V", at = @At("HEAD"), argsOnly = true)
    private Map<Identifier, JsonElement> goldclaim$removePlushieRecipes(Map<Identifier, JsonElement> recipes) {
        Map<Identifier, JsonElement> allowed = new HashMap<>(recipes);
        allowed.entrySet().removeIf(entry -> RecipePolicy.isPlushieRecipe(entry.getValue()) || BannedContent.isBannedRecipe(entry.getValue()));
        LoggerFactory.getLogger("GoldClaim").info("Removed {} plushie/banned-item recipes; Saro's player plushie crafting remains enabled.", recipes.size() - allowed.size());
        return allowed;
    }
}