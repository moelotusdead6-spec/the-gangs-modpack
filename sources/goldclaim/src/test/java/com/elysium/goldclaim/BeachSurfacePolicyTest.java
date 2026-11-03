package com.elysium.goldclaim;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class BeachSurfacePolicyTest {
    @Test
    public void onlyExactExpandedEcosphereAirCutConditionMatches() {
        JsonArray biomes = new JsonArray();
        for (String id : new String[]{"wythers:coastal_mangroves", "wythers:black_beach", "wythers:black_river",
                "wythers:tropical_beach", "minecraft:beach", "wythers:jungle_river", "minecraft:warm_ocean",
                "minecraft:lukewarm_ocean", "minecraft:ocean", "minecraft:cold_ocean", "wythers:tropical_forest_river",
                "minecraft:river", "wythers:badlands_river", "wythers:gravelly_river", "wythers:lantern_river",
                "minecraft:frozen_river", "wythers:icy_river", "wythers:gravelly_beach", "minecraft:snowy_beach",
                "wythers:salt_lakes_white", "wythers:salt_lakes_pink", "wythers:salt_lakes_turquoise",
                "wythers:desert_river", "wythers:deep_desert_river"}) biomes.add(id);
        JsonObject condition = new JsonObject();
        condition.addProperty("type", "minecraft:biome");
        condition.add("biome_is", biomes);
        assertTrue(BeachSurfacePolicy.isCoastalAirCut(condition));
        biomes.add("another_mod:beach");
        assertFalse(BeachSurfacePolicy.isCoastalAirCut(condition));
        condition.addProperty("type", "minecraft:not");
        assertFalse(BeachSurfacePolicy.isCoastalAirCut(condition));
        assertFalse(BeachSurfacePolicy.isCoastalAirCut(new JsonObject()));
        assertEquals(6, BeachSurfacePolicy.BEACHES.size());
        assertFalse(BeachSurfacePolicy.BEACHES.contains("minecraft:river"));
        assertFalse(BeachSurfacePolicy.BEACHES.contains("minecraft:ocean"));
    }
}
