package com.elysium.goldclaim.mixin;

import com.elysium.goldclaim.GoldClaimMod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BucketItem;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BucketItem.class)
public abstract class ClaimBucketUseMixin {
    @WrapOperation(method = "use", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/World;canPlayerModifyAt(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/math/BlockPos;)Z"))
    private boolean goldclaim$protectBucketSource(World world, PlayerEntity player, BlockPos pos,
                                                  Operation<Boolean> original) {
        return original.call(world, player, pos) && goldclaim$canUseBucket(player, world, pos);
    }

    @Inject(method = "placeFluid", at = @At("HEAD"), cancellable = true)
    private void goldclaim$protectBucketDestination(PlayerEntity player, World world, BlockPos pos,
                                                    BlockHitResult hit, CallbackInfoReturnable<Boolean> result) {
        if (!goldclaim$canUseBucket(player, world, pos)) {
            result.setReturnValue(false);
        }
    }

    private static boolean goldclaim$canUseBucket(PlayerEntity player, World world, BlockPos pos) {
        if (world.isClient || !(player instanceof ServerPlayerEntity serverPlayer)) {
            return true;
        }
        GoldClaimMod mod = GoldClaimMod.getInstance();
        if (mod == null) {
            throw new IllegalStateException("GoldClaim is not initialized");
        }
        return mod.canUseItemAtClaim(serverPlayer, world, pos);
    }
}
