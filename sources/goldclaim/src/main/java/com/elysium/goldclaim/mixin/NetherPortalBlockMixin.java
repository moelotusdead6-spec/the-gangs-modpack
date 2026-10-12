package com.elysium.goldclaim.mixin;

import com.elysium.goldclaim.GoldClaimMod;
import net.minecraft.block.BlockState;
import net.minecraft.block.NetherPortalBlock;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetherPortalBlock.class)
public abstract class NetherPortalBlockMixin {
    @Inject(method = "onEntityCollision", at = @At("HEAD"), cancellable = true)
    private void goldclaim$disableWildNetherPortalEntry(BlockState state, World world, BlockPos pos, Entity entity, CallbackInfo ci) {
        if (world.getRegistryKey() == World.OVERWORLD) {
            if (entity instanceof ServerPlayerEntity player) {
                GoldClaimMod.notifyWildPortalDisabled(player, "Nether");
            }
            ci.cancel();
        }
    }
}
