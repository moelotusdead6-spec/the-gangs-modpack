/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  net.minecraft.advancement.CriterionMerger6
 *  net.minecraft.item.map.MapState44
 *  net.minecraft.block.Blocks
 *  net.minecraft.block.TallPlantBlock
 *  net.minecraft.block.DoorBlock
 *  net.minecraft.util.math.BlockPos
 *  net.minecraft.util.math.Direction
 *  net.minecraft.scoreboard.Team0
 *  net.minecraft.client.gui.screen.advancement.AdvancementTabType8
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package com.elysium.goldclaim.mixin;

import com.elysium.goldclaim.GoldClaimMod;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={BedBlock.class, DoorBlock.class, TallPlantBlock.class})
public abstract class PairedBlockMixin {
    @Inject(method={"getStateForNeighborUpdate"}, at={@At(value="RETURN")}, cancellable=true)
    private void goldclaim$preserveSplitHubBlocks(BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos, CallbackInfoReturnable<BlockState> callbackInfo) {
        if (((BlockState)callbackInfo.getReturnValue()).isOf(Blocks.AIR) && GoldClaimMod.shouldPreserveUnsupportedHubBlocks((WorldView)world)) {
            callbackInfo.setReturnValue(state);
        }
    }
}
