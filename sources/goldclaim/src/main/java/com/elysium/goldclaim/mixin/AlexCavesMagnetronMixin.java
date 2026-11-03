package com.elysium.goldclaim.mixin;

import com.elysium.goldclaim.AlexCavesProtection;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "com.github.alexmodguy.alexscaves.server.entity.living.MagnetronEntity", remap = false)
public abstract class AlexCavesMagnetronMixin {
    @WrapMethod(method = "startForming")
    private void goldclaim$preventBlockTheft(Operation<Void> original) {
        AlexCavesProtection.whileActing((Entity)(Object)this, original::call);
    }
}
