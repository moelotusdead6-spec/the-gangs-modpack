package com.elysium.goldclaim.mixin;

import com.elysium.goldclaim.BetterEndLootrService;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mixin(ChestBlock.class)
public abstract class BetterEndChestOpeningMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("GoldClaim/Lootr");

    @Inject(method = "onUse", at = @At("HEAD"), cancellable = true)
    private void goldclaim$convertBeforeLootIsConsumed(BlockState state, World world, BlockPos pos,
            PlayerEntity player, Hand hand, BlockHitResult hit, CallbackInfoReturnable<ActionResult> ci) {
        if (!(world instanceof ServerWorld serverWorld) || player.isSpectator()
                || !FabricLoader.getInstance().isModLoaded("lootr")) return;
        boolean converted;
        try {
            converted = BetterEndLootrService.convertBeforeOpening(serverWorld, pos, state);
        } catch (RuntimeException failure) {
            // A chest must never become unusable because personal-loot conversion failed.
            LOGGER.error("Lootr first-open conversion failed at {}; opening the original chest", pos, failure);
            return;
        }
        if (converted) {
            ci.setReturnValue(world.getBlockState(pos).onUse(world, player, hand, hit));
        }
    }
}
