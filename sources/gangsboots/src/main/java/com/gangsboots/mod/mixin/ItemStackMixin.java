package com.gangsboots.mod.mixin;

import com.gangsboots.mod.ModItems;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    @Inject(method = "damage(ILnet/minecraft/util/math/random/Random;Lnet/minecraft/server/network/ServerPlayerEntity;)Z",
        at = @At("HEAD"), cancellable = true)
    private void gangsboots$preventArmorWear(int amount, Random random, ServerPlayerEntity player,
            CallbackInfoReturnable<Boolean> callback) {
        if (amount > 0 && ((ItemStack) (Object) this).isOf(ModItems.BOOTS_OF_LIGHTNESS)) {
            callback.setReturnValue(false);
        }
    }
}
