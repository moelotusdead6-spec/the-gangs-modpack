package com.gangs.gangstrials.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Enhanced Celestials 2 targets Minecraft 26.x only, so its join notice can never apply to this 1.20.1 pack. */
@Pseudo
@Mixin(targets = "dev.corgitaco.enhancedcelestials.client.EC2AnnouncementTrigger", remap = false)
public abstract class EC2AnnouncementMixin {
    @Inject(method = "onLogin", at = @At("HEAD"), cancellable = true, require = 0)
    private static void gangstrials$skipAnnouncement(CallbackInfo ci) {
        ci.cancel();
    }
}
