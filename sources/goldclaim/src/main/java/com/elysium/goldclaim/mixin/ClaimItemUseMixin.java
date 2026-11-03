package com.elysium.goldclaim.mixin;

import com.elysium.goldclaim.GoldClaimMod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerPlayerInteractionManager.class)
public abstract class ClaimItemUseMixin {
    @WrapOperation(method = "interactBlock", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/item/ItemStack;useOnBlock(Lnet/minecraft/item/ItemUsageContext;)Lnet/minecraft/util/ActionResult;"))
    private ActionResult goldclaim$protectClaimItemUse(ItemStack stack, ItemUsageContext context,
                                                       Operation<ActionResult> original) {
        if (context.getPlayer() instanceof ServerPlayerEntity player) {
            GoldClaimMod mod = GoldClaimMod.getInstance();
            if (mod == null) {
                throw new IllegalStateException("GoldClaim is not initialized");
            }
            if (!mod.canUseItemOnClaim(player, context)) {
                return ActionResult.FAIL;
            }
        }
        return original.call(stack, context);
    }
}
