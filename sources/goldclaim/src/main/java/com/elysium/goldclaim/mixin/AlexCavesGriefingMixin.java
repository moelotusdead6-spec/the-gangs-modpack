package com.elysium.goldclaim.mixin;

import com.elysium.goldclaim.AlexCavesProtection;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.github.alexmodguy.alexscaves.forge_shim.event.ForgeEventFactory", remap = false)
public abstract class AlexCavesGriefingMixin {
    @Inject(method = "getMobGriefingEvent", at = @At("HEAD"), cancellable = true)
    private static void goldclaim$preventBossGriefing(World world, Entity entity,
            CallbackInfoReturnable<Boolean> cir) {
        if (AlexCavesProtection.isProtected(entity)) cir.setReturnValue(false);
    }
}
