package com.gangs.ouxcompat.mixin;

import com.gangs.ouxcompat.ChatBadges;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Oux Badges only decorates the one-argument addMessage; player chat arrives through this overload. */
@Mixin(ChatHud.class)
public abstract class ChatHudMixin {
    @ModifyVariable(
        method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
        at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Text gangsOux$addBadge(Text message) {
        return ChatBadges.decorate(message, MinecraftClient.getInstance());
    }
}
