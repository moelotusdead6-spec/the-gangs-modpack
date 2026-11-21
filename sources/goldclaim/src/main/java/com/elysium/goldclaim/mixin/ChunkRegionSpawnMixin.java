package com.elysium.goldclaim.mixin;

import com.elysium.goldclaim.AlexCavesProtection;
import net.minecraft.entity.Entity;
import net.minecraft.world.ChunkRegion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Alex's Caves spawns cave creatures during chunk generation straight into the region, skipping SpawnHelper's checks. */
@Mixin(ChunkRegion.class)
public abstract class ChunkRegionSpawnMixin {
    @Inject(method = "spawnEntity", at = @At("HEAD"), cancellable = true)
    private void goldclaim$denyWildAlexCavesGeneration(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (AlexCavesProtection.blocksWildGenerationSpawn(entity, ((ChunkRegion)(Object)this).toServerWorld())) {
            cir.setReturnValue(false);
        }
    }
}
