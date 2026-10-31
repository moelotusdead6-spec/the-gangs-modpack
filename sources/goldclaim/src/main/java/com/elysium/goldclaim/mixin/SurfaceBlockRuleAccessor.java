package com.elysium.goldclaim.mixin;

import net.minecraft.block.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.gen.surfacebuilder.MaterialRules$BlockMaterialRule")
public interface SurfaceBlockRuleAccessor {
    @Accessor("resultState")
    BlockState goldclaim$resultState();
}
