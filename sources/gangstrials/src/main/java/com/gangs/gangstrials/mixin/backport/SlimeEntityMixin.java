package com.gangs.gangstrials.mixin.backport;

import com.gangs.gangstrials.TrialSpawnerFixes;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Difficulty;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SlimeEntity.class)
public abstract class SlimeEntityMixin {
    @Inject(method = "canSpawn", at = @At("HEAD"), cancellable = true)
    private static void gangstrials$trialSlimesIgnoreNaturalRestrictions(EntityType<SlimeEntity> type,
            WorldAccess world, SpawnReason reason, BlockPos pos, Random random,
            CallbackInfoReturnable<Boolean> cir) {
        if (TrialSpawnerFixes.ignoresLight() && reason == SpawnReason.SPAWNER) {
            cir.setReturnValue(world.getDifficulty() != Difficulty.PEACEFUL);
        }
    }
}
