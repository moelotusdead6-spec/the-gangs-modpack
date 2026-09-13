/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  net.minecraft.advancement.CriterionMerger7
 *  net.minecraft.block.Blocks
 *  net.minecraft.util.math.BlockPos
 *  net.minecraft.block.FireBlock
 *  net.minecraft.scoreboard.Team0
 *  net.minecraft.server.world.ServerWorld
 *  net.minecraft.loot.LootTableReporter19
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.elysium.goldclaim.mixin;

import com.elysium.goldclaim.GoldClaimMod;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FireBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={FireBlock.class})
public abstract class FireBlockMixin {
    @Inject(method={"onBlockAdded"}, at={@At(value="HEAD")}, cancellable=true)
    private void goldclaim$preventHubFireIgnite(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify, CallbackInfo callbackInfo) {
        ServerWorld serverWorld;
        if (world instanceof ServerWorld && GoldClaimMod.shouldPreventHubFire(serverWorld = (ServerWorld)world)) {
            serverWorld.setBlockState(pos, Blocks.AIR.getDefaultState());
            callbackInfo.cancel();
        }
    }

    @Inject(method={"scheduledTick"}, at={@At(value="HEAD")}, cancellable=true)
    private void goldclaim$preventHubFireSpread(BlockState state, ServerWorld world, BlockPos pos, Random random, CallbackInfo callbackInfo) {
        if (GoldClaimMod.shouldPreventHubFire(world)) {
            world.setBlockState(pos, Blocks.AIR.getDefaultState());
            callbackInfo.cancel();
        }
    }
}
