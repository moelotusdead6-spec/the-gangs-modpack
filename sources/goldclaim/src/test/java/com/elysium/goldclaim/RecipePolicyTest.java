package com.elysium.goldclaim;

import com.google.gson.JsonParser;
import org.junit.Test;
import static org.junit.Assert.*;

public class RecipePolicyTest {
    @Test
    public void onlyTheActualSaroPlayerPlushieOutputIsAllowed() {
        for (String item : new String[]{"sarosplayerplushiemod:plushie",
                "a_man_with_plushies:plush_box", "a_man_with_plushies:golden_plush_box",
                "perfectplushies:bee_plushie", "sarosplayerplushiemod:player_plushie"}) {
            for (String output : new String[]{"\"" + item + "\"", "{\"item\":\"" + item + "\"}",
                    "{\"id\":\"" + item + "\"}"}) {
                assertEquals(item, !RecipePolicy.PLAYER_PLUSHIE.equals(item),
                    RecipePolicy.isPlushieRecipe(JsonParser.parseString("{\"result\":" + output + "}")));
            }
        }
    }

    @Test
    public void unrelatedOrMalformedOutputsAreNotPlushieRecipes() {
        for (String recipe : new String[]{"{}", "{\"result\":{\"count\":1}}",
                "{\"result\":1}", "{\"result\":\"not a valid identifier\"}",
                "{\"result\":{\"item\":\"minecraft:diamond\"}}"}) {
            assertFalse(RecipePolicy.isPlushieRecipe(JsonParser.parseString(recipe)));
        }
    }
}
