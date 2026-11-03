package com.elysium.goldclaim.mixin;

import com.elysium.goldclaim.HubTeleportPolicy;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public abstract class HubLocalTeleportMixin {
    @Inject(method = {"requestTeleport", "requestTeleportAndDismount", "requestTeleportOffset"},
        at = @At("HEAD"), cancellable = true)
    private void goldclaim$denyLocalTeleport(double x, double y, double z, CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity)(Object)this;
        if (HubTeleportPolicy.denyItemTeleport(player, player.getServerWorld())) ci.cancel();
    }
}
