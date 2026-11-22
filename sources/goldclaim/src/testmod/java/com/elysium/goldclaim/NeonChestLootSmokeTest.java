package com.elysium.goldclaim;

import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.loot.LootGsons;
import net.minecraft.loot.LootPool;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

final class NeonChestLootSmokeTest {
    static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            var gson = LootGsons.getTableGsonBuilder().create();
            String namespace = "neon-wall-wonders-gwbzt3mk:";
            for (String item : new String[] {"neon_energy_sabers", "neon_teddy_bear"}) {
                require(Registries.ITEM.containsId(new Identifier(namespace + item)), "Missing actual neon item: " + item);
            }
            for (String name : new String[] {"desert_pyramid", "end_city_treasure", "village/village_armorer"}) {
                var table = server.getLootManager().getLootTable(new Identifier("minecraft", "chests/" + name));
                var found = new java.util.HashSet<String>();
                for (var lootPool : table.pools) {
                    var pool = gson.toJsonTree(lootPool, LootPool.class).getAsJsonObject();
                    var entries = pool.getAsJsonArray("entries");
                    if (entries == null) continue;
                    var matches = new java.util.ArrayList<com.google.gson.JsonObject>();
                    for (var value : entries) {
                        var entry = value.getAsJsonObject();
                        if (entry.has("name") && (entry.get("name").getAsString().equals(namespace + "neon_energy_sabers")
                                || entry.get("name").getAsString().equals(namespace + "neon_teddy_bear"))) matches.add(entry);
                    }
                    if (matches.isEmpty()) continue;
                    require(matches.size() == 1 && entries.size() == 1, "Neon rewards share a weighted pool");
                    require(pool.get("rolls").getAsFloat() == 1.0F, "Neon roll count changed");
                    var entry = matches.get(0);
                    require(found.add(entry.get("name").getAsString()), "Duplicate neon reward pool");
                    var conditions = entry.getAsJsonArray("conditions");
                    require(conditions.size() == 1
                        && conditions.get(0).getAsJsonObject().get("condition").getAsString().equals("minecraft:random_chance")
                        && conditions.get(0).getAsJsonObject().get("chance").getAsFloat() == 0.0025F,
                        "Neon chance is not exactly 0.25% per independent roll");
                }
                require(found.size() == 2, "Missing neon bonus pools: " + name);
            }
            try {
                Files.writeString(Path.of("neon-chest-smoke-passed.txt"),
                    "PASS: actual Neon Energy Sabers/Teddy Bear registry IDs; three loaded chest tables; two independent "
                    + "single-item, single-roll pools at exactly 0.25% each.\n");
            } catch (java.io.IOException failure) {
                throw new IllegalStateException("Cannot record neon smoke result", failure);
            }
            server.stop(false);
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
