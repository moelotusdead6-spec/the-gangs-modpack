package com.gangs.gangstrials.mixin.backport;

import com.drd.trickytrialsbackport.block.TrialSpawnerBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The backport registers the trial spawner with hardness 0 (instant break); vanilla uses 50. */
@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class TrialSpawnerHardnessMixin {
    @Shadow
    public abstract Block getBlock();

    @Inject(method = "getHardness", at = @At("HEAD"), cancellable = true)
    private void gangstrials$trialSpawnerHardness(BlockView world, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        if (this.getBlock() instanceof TrialSpawnerBlock) {
            cir.setReturnValue(50.0F);
        }
    }
}
