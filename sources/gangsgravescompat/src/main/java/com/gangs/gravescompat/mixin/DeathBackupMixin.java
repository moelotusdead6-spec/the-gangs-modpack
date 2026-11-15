package com.gangs.gravescompat.mixin;

import com.gangs.gravescompat.DeathBackup;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ServerPlayerEntity.class, priority = 2000)
public abstract class DeathBackupMixin {
    @Inject(method = "onDeath", at = @At("HEAD"))
    private void saveDeathInventory(DamageSource source, CallbackInfo ci) {
        DeathBackup.save((ServerPlayerEntity)(Object)this, source);
    }
}
