package com.elysium.goldclaim;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.Identifier;

public final class RecipePolicy {
    public static final String PLAYER_PLUSHIE = "sarosplayerplushiemod:plushie";

    private RecipePolicy() {}

    public static boolean isPlushieRecipe(JsonElement recipe) {
        if (!recipe.isJsonObject()) {
            return false;
        }
        JsonElement result = recipe.getAsJsonObject().get("result");
        if (result != null && result.isJsonObject()) {
            JsonObject output = result.getAsJsonObject();
            result = output.has("item") ? output.get("item") : output.get("id");
        }
        if (result == null || !result.isJsonPrimitive() || !result.getAsJsonPrimitive().isString()) {
            return false;
        }
        Identifier item = Identifier.tryParse(result.getAsString());
        return item != null && item.getPath().contains("plush")
            && !PLAYER_PLUSHIE.equals(item.toString());
    }
}
