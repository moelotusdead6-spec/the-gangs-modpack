package com.elysium.goldclaim;

import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

final class NeonChestLoot {
    private static final float NEON_SIGN_CHANCE = 0.005F;
    private static final Identifier NEON_FOX = new Identifier("neon-wall-wonders-gwbzt3mk", "neon_fox");
    private static final Identifier NEON_OWL = new Identifier("neon-wall-wonders-gwbzt3mk", "neon_owl");

    private NeonChestLoot() {}

    static void register() {
        LootTableEvents.MODIFY.register((resourceManager, lootManager, tableId, tableBuilder, source) -> {
            if (!tableId.getPath().startsWith("chests/")) return;
            addRareItem(tableBuilder, NEON_FOX);
            addRareItem(tableBuilder, NEON_OWL);
        });
    }

    private static void addRareItem(net.minecraft.loot.LootTable.Builder tableBuilder, Identifier itemId) {
        tableBuilder.pool(LootPool.builder()
            .rolls(ConstantLootNumberProvider.create(1.0F))
            .with(ItemEntry.builder(Registries.ITEM.get(itemId))
                .conditionally(RandomChanceLootCondition.builder(NEON_SIGN_CHANCE))));
    }
}
