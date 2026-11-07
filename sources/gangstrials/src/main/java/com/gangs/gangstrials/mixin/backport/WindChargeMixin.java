package com.gangs.gangstrials.mixin.backport;

import com.drd.trickytrialsbackport.entity.projectile.windcharge.WindCharge;
import com.gangs.gangstrials.WindBurst;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = WindCharge.class, remap = false)
public abstract class WindChargeMixin {
    @Unique
    private static final int OWNER_FALL_GUARD_TICKS = 200;

    @Shadow
    private boolean exploded;

    @Unique
    private int gangstrials$burstAge = -1;

    /** The backport launches the thrower upward on the first tick of every throw. */
    @Inject(method = "applyRecoil", at = @At("HEAD"), cancellable = true)
    private void gangstrials$noThrowRecoil(CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "explode", at = @At("HEAD"), cancellable = true)
    private void gangstrials$explodeOnce(CallbackInfo ci) {
        if (this.exploded) {
            ci.cancel();
        }
    }

    @Inject(method = "explode", at = @At("TAIL"))
    private void gangstrials$burstOnImpact(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self.getWorld().isClient || !this.exploded) {
            return;
        }
        // The backport keeps an exploded charge alive to cancel its owner's fall damage until landing;
        // only keep it when the burst actually launched the owner.
        if (WindBurst.apply(self)) {
            this.gangstrials$burstAge = self.age;
        } else {
            self.discard();
        }
    }

    // Intermediary name of Entity.tick(); the target is a mod class, so it is not remapped.
    @Inject(method = "method_5773", at = @At("TAIL"))
    private void gangstrials$expireLingeringBurst(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (!self.getWorld().isClient && !self.isRemoved() && this.gangstrials$burstAge >= 0
                && self.age - this.gangstrials$burstAge > OWNER_FALL_GUARD_TICKS) {
            self.discard();
        }
    }
}
