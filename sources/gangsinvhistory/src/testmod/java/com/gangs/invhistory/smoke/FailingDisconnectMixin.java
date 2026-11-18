package com.gangs.invhistory.smoke;

import com.gangs.invhistory.HistorySmokeTest;
import net.minecraft.server.PlayerManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerManager.class)
abstract class FailingDisconnectMixin {
    @Inject(method = "disconnectAllPlayers", at = @At("HEAD"))
    private void gangsinvhistorySmoke$failDisconnect(CallbackInfo ci) {
        HistorySmokeTest.injectShutdownFailure();
    }
}
