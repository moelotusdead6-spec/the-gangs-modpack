package com.gangs.dragonloot.mixin;

import com.gangs.dragonloot.DragonLoot;
import java.util.function.Consumer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameterSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntity.class)
public abstract class DragonLootMixin {
    @Redirect(method = "dropLoot", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/loot/LootTable;generateLoot(Lnet/minecraft/loot/context/LootContextParameterSet;JLjava/util/function/Consumer;)V"))
    private void gangs$randomDragonUniques(LootTable table, LootContextParameterSet parameters,
                                          long seed, Consumer<ItemStack> output) {
        DragonLoot.generate(table, parameters, seed, output);
    }
}
