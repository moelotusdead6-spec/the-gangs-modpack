package com.gangs.gangstrials.mixin.backport;

import com.drd.trickytrialsbackport.block.entity.trialspawner.TrialSpawner;
import com.gangs.gangstrials.TrialSpawnerFixes;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TrialSpawner.class, remap = false)
public abstract class TrialSpawnerMixin {
    @Inject(method = "spawnMob", at = @At("HEAD"))
    private void gangstrials$beginSpawn(ServerWorld level, BlockPos pos, CallbackInfoReturnable<Optional<UUID>> cir) {
        TrialSpawnerFixes.beginTrialSpawn();
    }

    @Inject(method = "spawnMob", at = @At("RETURN"))
    private void gangstrials$endSpawn(ServerWorld level, BlockPos pos, CallbackInfoReturnable<Optional<UUID>> cir) {
        TrialSpawnerFixes.endTrialSpawn();
    }
}
