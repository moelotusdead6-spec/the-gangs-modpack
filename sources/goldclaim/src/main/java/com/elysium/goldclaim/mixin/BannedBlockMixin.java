package com.elysium.goldclaim.mixin;

import com.elysium.goldclaim.BannedContent;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.ProtoChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({WorldChunk.class, ProtoChunk.class})
public abstract class BannedBlockMixin {
    @Inject(method = "setBlockState", at = @At("HEAD"), cancellable = true)
    private void goldclaim$rejectBinPlacement(BlockPos pos, BlockState state, boolean moved,
                                              CallbackInfoReturnable<BlockState> cir) {
        if (BannedContent.isBanned(state)) {
            BannedContent.report("block placement/write rejected");
            cir.setReturnValue(null);
        }
    }
}
