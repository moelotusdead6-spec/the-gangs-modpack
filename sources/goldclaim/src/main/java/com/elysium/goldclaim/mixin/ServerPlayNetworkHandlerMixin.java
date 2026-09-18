/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  net.minecraft.entity.ai.pathing.WaterPathNodeMaker68
 *  net.minecraft.item.Items
 *  net.minecraft.util.math.BlockPos
 *  net.minecraft.text.Text
 *  net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket
 *  net.minecraft.server.network.ServerPlayerEntity
 *  net.minecraft.server.network.ServerPlayNetworkHandler
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.elysium.goldclaim.mixin;

import com.elysium.goldclaim.GoldClaimMod;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={ServerPlayNetworkHandler.class})
public abstract class ServerPlayNetworkHandlerMixin {
    @Shadow
    @Final
    private ServerPlayerEntity player;

    @Inject(method={"onPlayerInteractItem"}, at={@At(value="HEAD")}, cancellable=true)
    private void goldclaim$identifyWithArrow(PlayerInteractItemC2SPacket packet, CallbackInfo ci) {
        if (!packet.getHand().equals(Hand.MAIN_HAND)) {
            return;
        }
        if (!this.player.getStackInHand(packet.getHand()).isOf(Items.ARROW)) {
            return;
        }
        GoldClaimMod mod = GoldClaimMod.getInstance();
        if (mod == null) {
            return;
        }
        mod.identifyClaim(this.player, new BlockPos(this.player.getBlockX(), this.player.getBlockY(), this.player.getBlockZ()));
        ci.cancel();
    }
}
