package com.elysium.goldclaim.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.kwpugh.gobber2.items.armor.DragonArmor", remap = false)
public abstract class GobberDragonFlightMixin {
    @Shadow private boolean enableFlying;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void goldclaim$disableUnlimitedFlight(CallbackInfo ci) {
        this.enableFlying = false;
    }
}
