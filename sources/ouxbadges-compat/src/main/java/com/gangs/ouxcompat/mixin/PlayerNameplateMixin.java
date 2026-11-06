package com.gangs.ouxcompat.mixin;

import com.gangs.ouxcompat.ChatBadges;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerNameplateMixin {
    @ModifyVariable(
        method = "renderLabelIfPresent(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/text/Text;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
        at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Text gangsOux$useNickname(Text original, AbstractClientPlayerEntity player, Text label,
            MatrixStack matrices, VertexConsumerProvider vertices, int light) {
        ClientPlayNetworkHandler network = MinecraftClient.getInstance().getNetworkHandler();
        PlayerListEntry entry = network == null ? null : network.getPlayerListEntry(player.getUuid());
        return ChatBadges.decorateNameplate(original, entry);
    }
}
