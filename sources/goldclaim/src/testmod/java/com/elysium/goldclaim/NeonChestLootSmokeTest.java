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
            for (String item : new String[] {"neon_sword", "neon_shield"}) {
                require(Registries.ITEM.containsId(new Identifier(namespace + item)), "Missing actual neon item: " + item);
            }
            for (String name : new String[] {"desert_pyramid", "end_city_treasure", "village/village_armorer"}) {
                var table = server.getLootManager().getLootTable(new Identifier("minecraft", "chests/" + name));
                require(table.pools.length >= 2, "No neon bonus pools: " + name);
                for (int i = 0; i < 2; i++) {
                    var pool = gson.toJsonTree(table.pools[table.pools.length - 2 + i], LootPool.class).getAsJsonObject();
                    require(pool.get("rolls").getAsFloat() == 1.0F, "Neon roll count changed");
                    var entries = pool.getAsJsonArray("entries");
                    require(entries.size() == 1, "Neon rewards share a weighted pool");
                    var entry = entries.get(0).getAsJsonObject();
                    require(entry.get("name").getAsString().equals(namespace + (i == 0 ? "neon_sword" : "neon_shield")),
                        "Wrong neon reward: " + entry);
                    var conditions = entry.getAsJsonArray("conditions");
                    require(conditions.size() == 1
                        && conditions.get(0).getAsJsonObject().get("condition").getAsString().equals("minecraft:random_chance")
                        && conditions.get(0).getAsJsonObject().get("chance").getAsFloat() == 0.0025F,
                        "Neon chance is not exactly 0.25% per independent roll");
                }
            }
            try {
                Files.writeString(Path.of("neon-chest-smoke-passed.txt"),
                    "PASS: actual Neon Sword/Shield registry IDs; three loaded chest tables; two independent "
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
