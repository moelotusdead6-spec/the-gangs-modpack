package com.gangsboots.mod.mixin;

import com.gangsboots.mod.GangsBootsMod;
import net.minecraft.inventory.Inventory;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameterSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LootTable.class)
public abstract class LootTableMixin {
    @Inject(method = "supplyInventory", at = @At("TAIL"))
    private void gangsboots$rollBoots(Inventory inventory, LootContextParameterSet parameters, long seed, CallbackInfo ci) {
        GangsBootsMod.rollPlayerBoots((LootTable) (Object) this, inventory, parameters);
    }
}
