package com.gangsboots.mod.mixin;

import com.gangsboots.mod.ModItems;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AnvilScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla anvil combining of two identical items (repair-by-duplicate and
 * enchanted book application) does not consult Item#canRepair or
 * Item#isEnchantable, so those overrides on BootsOfSwiftnessItem never fire
 * for anvil use. Cancel updateResult outright whenever either input slot
 * holds swiftness boots. Uses the public Slot API instead of @Shadow since
 * the input/output fields are declared on the ForgingScreenHandler
 * superclass, not on AnvilScreenHandler itself.
 */
@Mixin(AnvilScreenHandler.class)
public abstract class AnvilScreenHandlerMixin {
    @Inject(method = "updateResult", at = @At("HEAD"), cancellable = true)
    private void gangsboots$rejectBoots(CallbackInfo ci) {
        AnvilScreenHandler self = (AnvilScreenHandler) (Object) this;
        ItemStack left = self.getSlot(AnvilScreenHandler.INPUT_1_ID).getStack();
        ItemStack right = self.getSlot(AnvilScreenHandler.INPUT_2_ID).getStack();
        if (left.isOf(ModItems.BOOTS_OF_SWIFTNESS) || right.isOf(ModItems.BOOTS_OF_SWIFTNESS)) {
            self.getSlot(AnvilScreenHandler.OUTPUT_ID).setStack(ItemStack.EMPTY);
            ci.cancel();
        }
    }
}
