/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  net.minecraft.util.math.BlockPos
 *  net.minecraft.client.gui.screen.advancement.AdvancementTabType8
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package com.elysium.goldclaim.mixin;

import com.elysium.goldclaim.GoldClaimMod;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets={"net.minecraft.block.AbstractBlock$AbstractBlockState"})
public abstract class AbstractBlockStateMixin {
    @Inject(method={"canPlaceAt"}, at={@At(value="HEAD")}, cancellable=true)
    private void goldclaim$preserveUnsupportedHubBlocks(WorldView world, BlockPos pos, CallbackInfoReturnable<Boolean> callbackInfo) {
        if (GoldClaimMod.shouldPreserveUnsupportedHubBlocks(world)) {
            callbackInfo.setReturnValue(true);
        }
    }
}
