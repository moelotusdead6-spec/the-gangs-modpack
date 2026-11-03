package com.elysium.goldclaim.mixin;

import com.elysium.goldclaim.AlexCavesProtection;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LivingEntity.class)
public abstract class AlexCavesVictimMixin {
    @WrapMethod(method = "damage")
    private boolean goldclaim$runVictimLogicUnguarded(DamageSource source, float amount, Operation<Boolean> original) {
        return AlexCavesProtection.asVictim((LivingEntity)(Object)this, () -> original.call(source, amount));
    }
}
