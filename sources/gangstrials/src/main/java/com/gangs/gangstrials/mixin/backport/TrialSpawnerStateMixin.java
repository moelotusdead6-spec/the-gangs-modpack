package com.gangs.gangstrials.mixin.backport;

import com.drd.trickytrialsbackport.block.entity.trialspawner.GangsTrialSpawnerLogic;
import com.drd.trickytrialsbackport.block.entity.trialspawner.TrialSpawner;
import com.drd.trickytrialsbackport.block.entity.trialspawner.TrialSpawnerState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Gradual vanilla spawning, per-player weighted rewards and ominous conversion during cooldown. */
@Mixin(value = TrialSpawnerState.class, remap = false)
public abstract class TrialSpawnerStateMixin {
    @Shadow
    private void spawnOminousOminousItemSpawner(ServerWorld level, BlockPos pos, TrialSpawner spawner) {
        throw new AssertionError();
    }

    @Inject(method = "tickAndGetNext", at = @At("HEAD"), cancellable = true)
    private void gangstrials$vanillaTick(BlockPos pos, TrialSpawner spawner, ServerWorld level, CallbackInfoReturnable<TrialSpawnerState> cir) {
        TrialSpawnerState next = GangsTrialSpawnerLogic.tick((TrialSpawnerState) (Object) this, pos, spawner, level,
            () -> this.spawnOminousOminousItemSpawner(level, pos, spawner));
        if (next != null) {
            cir.setReturnValue(next);
        }
    }
}
