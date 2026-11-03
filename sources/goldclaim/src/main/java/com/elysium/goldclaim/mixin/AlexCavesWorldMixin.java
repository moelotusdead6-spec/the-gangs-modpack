package com.elysium.goldclaim.mixin;

import com.elysium.goldclaim.AlexCavesProtection;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public abstract class AlexCavesWorldMixin {
    @Inject(method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z",
        at = @At("HEAD"), cancellable = true)
    private void goldclaim$preventBossBlockChanges(BlockPos pos, BlockState state, int flags, int depth,
            CallbackInfoReturnable<Boolean> cir) {
        if (AlexCavesProtection.preventBlockChanges(null)
                && !AlexCavesProtection.allowsBlockChange(((World)(Object)this).getBlockState(pos), state)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "breakBlock(Lnet/minecraft/util/math/BlockPos;ZLnet/minecraft/entity/Entity;I)Z",
        at = @At("HEAD"), cancellable = true)
    private void goldclaim$preventBossBlockDrops(BlockPos pos, boolean drop, Entity breaker, int depth,
            CallbackInfoReturnable<Boolean> cir) {
        if (AlexCavesProtection.preventBlockChanges(breaker)) cir.setReturnValue(false);
    }

    @Inject(method = "removeBlock", at = @At("HEAD"), cancellable = true)
    private void goldclaim$preventBossBlockRemoval(BlockPos pos, boolean move,
            CallbackInfoReturnable<Boolean> cir) {
        if (AlexCavesProtection.preventBlockChanges(null)) cir.setReturnValue(false);
    }

    @Inject(method = "removeBlockEntity", at = @At("HEAD"), cancellable = true)
    private void goldclaim$preventBossBlockEntityRemoval(BlockPos pos, CallbackInfo ci) {
        if (AlexCavesProtection.preventBlockChanges(null)) ci.cancel();
    }
}
