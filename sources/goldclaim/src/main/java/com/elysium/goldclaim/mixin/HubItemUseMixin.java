package com.elysium.goldclaim.mixin;

import com.elysium.goldclaim.HubTeleportPolicy;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ServerPlayerInteractionManager.class)
public abstract class HubItemUseMixin {
    @WrapMethod(method = "interactItem")
    private ActionResult goldclaim$itemUse(ServerPlayerEntity player, World world, ItemStack stack, Hand hand,
            Operation<ActionResult> original) {
        return HubTeleportPolicy.useItem(player, () -> original.call(player, world, stack, hand));
    }

    @WrapMethod(method = "interactBlock")
    private ActionResult goldclaim$blockItemUse(ServerPlayerEntity player, World world, ItemStack stack, Hand hand,
            BlockHitResult hit, Operation<ActionResult> original) {
        return HubTeleportPolicy.useItem(player, () -> original.call(player, world, stack, hand, hit));
    }
}
