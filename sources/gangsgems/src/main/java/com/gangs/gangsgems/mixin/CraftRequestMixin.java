package com.gangs.gangsgems.mixin;

import com.gangs.gangsgems.CraftDelivery;
import com.gangs.gangsgems.GangGems;
import net.conczin.immersive_furniture.data.FurnitureData;
import net.conczin.immersive_furniture.network.c2s.CraftRequest;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CraftRequest.class, remap = false)
public abstract class CraftRequestMixin {
    @Shadow @Final private FurnitureData data;
    @Shadow @Final private boolean shift;

    @Inject(method = "handle", at = @At("HEAD"), cancellable = true, remap = false)
    private void gangsgems$craftWithGems(PlayerEntity player, CallbackInfo ci) {
        if (!GangGems.furnitureCraftingEnabled()) return;
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return;
        if (serverPlayer.isCreative() && serverPlayer.hasPermissionLevel(2)) return;
        ci.cancel();
        CraftDelivery.craft(serverPlayer, data, shift);
    }
}
