package com.elysium.goldclaim.mixin;

import com.elysium.goldclaim.WaystonePolicy;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkRegion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkRegion.class)
public abstract class RswWaystoneGenerationMixin {
    @Inject(method = "setBlockState", at = @At("HEAD"), cancellable = true)
    private void goldclaim$rejectRswWaystoneGeneration(BlockPos pos, BlockState state, int flags, int maxUpdateDepth,
            CallbackInfoReturnable<Boolean> cir) {
        if (WaystonePolicy.isWaystone(state) && WaystonePolicy.isRsw(((ChunkRegion)(Object)this).toServerWorld())) {
            cir.setReturnValue(false);
        }
    }
}
