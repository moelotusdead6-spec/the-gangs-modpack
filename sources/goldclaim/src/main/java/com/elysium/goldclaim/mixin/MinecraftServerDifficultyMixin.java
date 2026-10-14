package com.elysium.goldclaim.mixin;

import com.elysium.goldclaim.DifficultySync;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerDifficultyMixin {
    @Inject(method = "setDifficulty", at = @At("TAIL"))
    private void goldclaim$applyDifficultyEverywhere(Difficulty difficulty, boolean forceUpdate, CallbackInfo ci) {
        DifficultySync.onServerDifficultyChanged((MinecraftServer)(Object)this);
    }
}
