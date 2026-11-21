package com.gangsboots.mod.mixin;

import com.gangsboots.mod.ModItems;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RepairItemRecipe;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RepairItemRecipe.class)
public abstract class RepairItemRecipeMixin {
    @Inject(method = "matches", at = @At("HEAD"), cancellable = true)
    private void gangsboots$rejectLightnessBootRepair(@Coerce Inventory inventory, World world, CallbackInfoReturnable<Boolean> cir) {
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isOf(ModItems.BOOTS_OF_LIGHTNESS)) {
                cir.setReturnValue(false);
                return;
            }
        }
    }
}