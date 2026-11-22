package com.elysium.goldclaim;

import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

final class NeonChestLoot {
    private static final float NEON_SIGN_CHANCE = 0.0025F;
    private static final Identifier NEON_ENERGY_SABERS = new Identifier("neon-wall-wonders-gwbzt3mk", "neon_energy_sabers");
    private static final Identifier NEON_TEDDY_BEAR = new Identifier("neon-wall-wonders-gwbzt3mk", "neon_teddy_bear");

    private NeonChestLoot() {}

    static void register() {
        LootTableEvents.MODIFY.register((resourceManager, lootManager, tableId, tableBuilder, source) -> {
            if (!tableId.getPath().startsWith("chests/")) return;
            addRareItem(tableBuilder, NEON_ENERGY_SABERS);
            addRareItem(tableBuilder, NEON_TEDDY_BEAR);
        });
    }

    private static void addRareItem(net.minecraft.loot.LootTable.Builder tableBuilder, Identifier itemId) {
        tableBuilder.pool(LootPool.builder()
            .rolls(ConstantLootNumberProvider.create(1.0F))
            .with(ItemEntry.builder(Registries.ITEM.get(itemId))
                .conditionally(RandomChanceLootCondition.builder(NEON_SIGN_CHANCE))));
    }
}
