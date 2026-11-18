package com.gangs.invhistory.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Vanilla saves player data, disconnects players, then saves worlds. A mod throwing from a disconnect
 * listener (LuckPerms after its scheduler stopped) used to abort shutdown before the world save, leaving
 * player inventories newer than chunks and backpack storage.
 */
@Mixin(MinecraftServer.class)
abstract class ShutdownSaveMixin {
    @Unique
    private static final Logger GANGSINVHISTORY_LOGGER = LoggerFactory.getLogger("gangsinvhistory");

    @WrapOperation(method = "shutdown",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/PlayerManager;disconnectAllPlayers()V"))
    private void gangsinvhistory$keepSavingWorlds(PlayerManager players, Operation<Void> original) {
        try {
            original.call(players);
        } catch (RuntimeException failure) {
            GANGSINVHISTORY_LOGGER.error(
                "A mod failed while disconnecting players during shutdown; continuing so worlds are saved", failure);
            for (ServerPlayerEntity player : List.copyOf(players.getPlayerList())) {
                try {
                    player.networkHandler.disconnect(Text.translatable("multiplayer.disconnect.server_shutdown"));
                } catch (RuntimeException again) {
                    GANGSINVHISTORY_LOGGER.error("Could not disconnect {} during shutdown", player.getGameProfile().getName(), again);
                }
            }
        }
    }
}
