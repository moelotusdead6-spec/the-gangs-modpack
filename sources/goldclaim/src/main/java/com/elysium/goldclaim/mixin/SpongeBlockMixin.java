package com.elysium.goldclaim.mixin;

import net.minecraft.block.SpongeBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(SpongeBlock.class)
public abstract class SpongeBlockMixin {
    @ModifyConstant(method = "absorbWater", constant = @Constant(intValue = 65))
    private int goldclaim$doubleWaterCapacity(int original) {
        // The traversal budget includes the origin, not just water blocks.
        return (original - 1) * 2 + 1;
    }
}
