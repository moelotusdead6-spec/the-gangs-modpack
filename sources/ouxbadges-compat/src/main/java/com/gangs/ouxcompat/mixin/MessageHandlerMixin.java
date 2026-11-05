package com.gangs.ouxcompat.mixin;

import com.gangs.ouxcompat.ChatBadges;
import com.mojang.authlib.GameProfile;
import java.time.Instant;
import net.minecraft.client.network.message.MessageHandler;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Tells ChatBadges who sent the player message that is about to reach the chat HUD. */
@Mixin(MessageHandler.class)
public abstract class MessageHandlerMixin {
    @Inject(method = "processChatMessageInternal", at = @At("HEAD"))
    private void gangsOux$rememberSender(MessageType.Parameters params, SignedMessage message, Text decorated,
            GameProfile sender, boolean onlyShowSecureChat, Instant receptionTimestamp,
            CallbackInfoReturnable<Boolean> callback) {
        ChatBadges.beginPlayerMessage(sender.getId());
    }

    @Inject(method = "processChatMessageInternal", at = @At("RETURN"))
    private void gangsOux$forgetSender(MessageType.Parameters params, SignedMessage message, Text decorated,
            GameProfile sender, boolean onlyShowSecureChat, Instant receptionTimestamp,
            CallbackInfoReturnable<Boolean> callback) {
        ChatBadges.endPlayerMessage();
    }
}
