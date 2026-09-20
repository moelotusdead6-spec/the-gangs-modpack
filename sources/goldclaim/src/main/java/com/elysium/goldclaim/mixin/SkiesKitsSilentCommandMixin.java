package com.elysium.goldclaim.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Forces SkiesKits' own COMMAND_CONSOLE dispatch to run silently, regardless of the
// server-wide sendCommandFeedback gamerule, so kit claims never spam "Executed N command(s)".
// @Pseudo lets this mixin quietly no-op if SkiesKits isn't installed or changes its class layout.
@Pseudo
@Mixin(targets = "com.pokeskies.skieskits.config.actions.types.CommandConsole", remap = false)
public class SkiesKitsSilentCommandMixin {

    @Redirect(method = "executeAction", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;getCommandSource()Lnet/minecraft/server/command/ServerCommandSource;"))
    private ServerCommandSource goldclaim$forceSilentKitCommands(MinecraftServer server) {
        return server.getCommandSource().withSilent();
    }
}
