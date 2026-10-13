package com.elysium.goldclaim.mixin;

import com.elysium.goldclaim.GoldClaimMod;
import net.minecraft.block.AbstractFireBlock;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractFireBlock.class)
public abstract class AbstractFireBlockMixin {
    @Inject(method = "isOverworldOrNether", at = @At("RETURN"), cancellable = true)
    private static void goldclaim$allowRswNetherPortals(World world, CallbackInfoReturnable<Boolean> cir) {
        if (!Boolean.TRUE.equals(cir.getReturnValue()) && world != null) {
            String dimId = world.getRegistryKey().getValue().toString();
            if (GoldClaimMod.RSW_DIMENSION.equals(dimId) || GoldClaimMod.RSW_NETHER_DIMENSION.equals(dimId)) {
                cir.setReturnValue(true);
            }
        }
    }
}
