package com.elysium.goldclaim.mixin;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.village.ZombieSiegeManager;
import net.minecraft.world.World;
import net.minecraft.world.spawner.PatrolSpawner;
import net.minecraft.world.spawner.PhantomSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({PhantomSpawner.class, PatrolSpawner.class, ZombieSiegeManager.class})
public abstract class WildSpecialSpawnerMixin {
    @Inject(method = "spawn", at = @At("HEAD"), cancellable = true)
    private void goldclaim$disableWildNaturalHostiles(ServerWorld world, boolean monsters, boolean animals,
                                                     CallbackInfoReturnable<Integer> callback) {
        if (world.getRegistryKey() == World.OVERWORLD) callback.setReturnValue(0);
    }
}
