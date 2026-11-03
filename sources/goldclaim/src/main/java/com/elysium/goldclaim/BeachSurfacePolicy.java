package com.elysium.goldclaim;

import com.google.gson.JsonElement;
import java.util.HashSet;
import java.util.Set;

public final class BeachSurfacePolicy {
    public static final Set<String> BEACHES = Set.of("wythers:coastal_mangroves", "wythers:black_beach",
        "wythers:tropical_beach", "wythers:gravelly_beach", "minecraft:beach", "minecraft:snowy_beach");
    private static final Set<String> FLATTENED = Set.of(
        "wythers:coastal_mangroves", "wythers:black_beach", "wythers:black_river", "wythers:tropical_beach",
        "minecraft:beach", "wythers:jungle_river", "minecraft:warm_ocean", "minecraft:lukewarm_ocean",
        "minecraft:ocean", "minecraft:cold_ocean", "wythers:tropical_forest_river", "minecraft:river",
        "wythers:badlands_river", "wythers:gravelly_river", "wythers:lantern_river", "minecraft:frozen_river",
        "wythers:icy_river", "wythers:gravelly_beach", "minecraft:snowy_beach", "wythers:salt_lakes_white",
        "wythers:salt_lakes_pink", "wythers:salt_lakes_turquoise", "wythers:desert_river", "wythers:deep_desert_river");

    private BeachSurfacePolicy() {}

    public static boolean isCoastalAirCut(JsonElement condition) {
        if (!condition.isJsonObject()) return false;
        var object = condition.getAsJsonObject();
        if (!object.has("type") || !"minecraft:biome".equals(object.get("type").getAsString())
                || !object.has("biome_is") || !object.get("biome_is").isJsonArray()) return false;
        Set<String> biomes = new HashSet<>();
        for (JsonElement value : object.getAsJsonArray("biome_is")) biomes.add(value.getAsString());
        return biomes.equals(FLATTENED);
    }
}
