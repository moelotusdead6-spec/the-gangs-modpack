package com.elysium.goldclaim.mixin;

import com.elysium.goldclaim.AlexCavesProtection;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerWorld.class)
public abstract class AlexCavesEntityTickMixin {
    @WrapOperation(method = "tickEntity", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/entity/Entity;tick()V"))
    private void goldclaim$protectBossTick(Entity entity, Operation<Void> original) {
        AlexCavesProtection.whileActing(entity, () -> original.call(entity));
    }

    @WrapOperation(method = "tickPassenger", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/entity/Entity;tickRiding()V"))
    private void goldclaim$protectBossPassengerTick(Entity entity, Operation<Void> original) {
        AlexCavesProtection.whileActing(entity, () -> original.call(entity));
    }
}
