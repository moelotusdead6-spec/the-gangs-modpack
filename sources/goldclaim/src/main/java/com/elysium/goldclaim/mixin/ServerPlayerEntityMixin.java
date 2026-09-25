package com.elysium.goldclaim.mixin;

import com.elysium.goldclaim.GoldClaimMod;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class ServerPlayerEntityMixin {
    @Inject(method = "dropInventory", at = @At("HEAD"), cancellable = true)
    private void goldclaim$keepPvpInventory(CallbackInfo callbackInfo) {
        Object player = this;
        if (player instanceof ServerPlayerEntity serverPlayer && GoldClaimMod.shouldKeepInventoryOnDeath(serverPlayer)) {
            callbackInfo.cancel();
        }
    }
}