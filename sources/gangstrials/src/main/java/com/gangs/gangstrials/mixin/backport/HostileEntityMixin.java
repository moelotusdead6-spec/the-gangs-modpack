package com.gangs.gangstrials.mixin.backport;

import com.gangs.gangstrials.TrialSpawnerFixes;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.ServerWorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HostileEntity.class)
public abstract class HostileEntityMixin {
    @Inject(method = "isSpawnDark", at = @At("HEAD"), cancellable = true)
    private static void gangstrials$trialSpawnerIgnoresLight(ServerWorldAccess world, BlockPos pos, Random random,
            CallbackInfoReturnable<Boolean> cir) {
        if (TrialSpawnerFixes.ignoresLight()) {
            cir.setReturnValue(true);
        }
    }
}
