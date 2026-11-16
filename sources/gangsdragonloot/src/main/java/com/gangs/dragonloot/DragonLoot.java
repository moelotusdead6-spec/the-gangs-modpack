package com.gangs.dragonloot;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

public final class DragonLoot {
    private static final TagKey<Item> UNIQUES =
        TagKey.of(RegistryKeys.ITEM, new Identifier("simplyswords", "uniques"));
    private static final TagKey<Item> MORE_UNIQUES =
        TagKey.of(RegistryKeys.ITEM, new Identifier("simplymore", "uniques"));

    private DragonLoot() {}

    public static void generate(LootTable table, LootContextParameterSet parameters, long seed,
                                Consumer<ItemStack> output) {
        if (!(parameters.getOptional(LootContextParameters.THIS_ENTITY) instanceof EnderDragonEntity)) {
            table.generateLoot(parameters, seed, output);
            return;
        }

        List<Item> candidates = Registries.ITEM.stream()
            .filter(item -> Registries.ITEM.getId(item).getNamespace().equals("simplyswords")
                && item.getDefaultStack().isIn(UNIQUES))
            .toList();
        if (candidates.size() < 2) {
            throw new IllegalStateException("Dragon rewards require at least two Simply Swords uniques.");
        }

        // Do not reuse a dragon's saved DeathLootTableSeed for the weapon selection.
        var random = parameters.getWorld().getRandom();
        int first = random.nextInt(candidates.size());
        int second = random.nextInt(candidates.size() - 1);
        if (second >= first) second++;
        table.generateLoot(parameters, seed, stack -> {
            if (!stack.isIn(UNIQUES) && !stack.isIn(MORE_UNIQUES)) output.accept(stack);
        });
        output.accept(new ItemStack(candidates.get(first)));
        output.accept(new ItemStack(candidates.get(second)));
    }
}
