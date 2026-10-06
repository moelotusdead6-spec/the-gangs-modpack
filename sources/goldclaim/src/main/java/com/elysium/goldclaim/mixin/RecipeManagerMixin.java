package com.elysium.goldclaim.mixin;

import com.elysium.goldclaim.BannedContent;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
        allowed.entrySet().removeIf(entry -> isPlushieRecipe(entry.getValue()) || BannedContent.isBannedRecipe(entry.getValue()));
        LoggerFactory.getLogger("GoldClaim").info("Removed {} plushie/bin recipes; player plushie crafting remains enabled.", recipes.size() - allowed.size());
        return allowed;
    }

    private static boolean isPlushieRecipe(JsonElement recipe) {
        if (!recipe.isJsonObject()) {
            return false;
        }
        JsonElement result = recipe.getAsJsonObject().get("result");
        if (result == null) {
            return false;
        }
        if (result.isJsonObject()) {
            JsonObject output = result.getAsJsonObject();
            result = output.has("item") ? output.get("item") : output.get("id");
        }
        if (result == null || !result.isJsonPrimitive() || !result.getAsJsonPrimitive().isString()) {
            return false;
        }
        Identifier item = Identifier.tryParse(result.getAsString());
        return item != null && item.getPath().contains("plush")
            && !item.toString().equals("sarosplayerplushiemod:player_plushie");
    }
}