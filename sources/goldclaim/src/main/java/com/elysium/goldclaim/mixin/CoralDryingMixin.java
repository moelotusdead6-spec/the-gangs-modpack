/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  net.minecraft.item.map.MapState97
 *  net.minecraft.block.CoralWallFanBlock
 *  net.minecraft.block.CoralBlock
 *  net.minecraft.util.math.BlockPos
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
import net.minecraft.block.CoralBlock;
import net.minecraft.block.CoralFanBlock;
import net.minecraft.block.CoralWallFanBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={CoralBlock.class, CoralFanBlock.class, CoralWallFanBlock.class})
public abstract class CoralDryingMixin {
    @Inject(method={"scheduledTick"}, at={@At(value="HEAD")}, cancellable=true)
    private void goldclaim$preserveHubCoral(BlockState state, ServerWorld world, BlockPos pos, Random random, CallbackInfo callbackInfo) {
        if (GoldClaimMod.isHubWorld(world)) {
            callbackInfo.cancel();
        }
    }
}
