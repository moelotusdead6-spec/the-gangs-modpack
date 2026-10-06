package com.elysium.goldclaim.mixin;

import com.elysium.goldclaim.GoldClaimMod;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public abstract class RswPlayerTravelMixin {
    @Inject(method = "teleport(Lnet/minecraft/server/world/ServerWorld;DDDFF)V", at = @At("HEAD"))
    private void goldclaim$rememberBeforeTeleport(ServerWorld target, double x, double y, double z,
                                                 float yaw, float pitch, CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity)(Object)this;
        if (target != player.getServerWorld()) {
            GoldClaimMod.rememberRswLocation(player);
        }
    }

    @Inject(method = "moveToWorld", at = @At("HEAD"))
    private void goldclaim$rememberBeforePortal(ServerWorld target, CallbackInfoReturnable<Entity> cir) {
        GoldClaimMod.rememberRswLocation((ServerPlayerEntity)(Object)this);
    }
}
