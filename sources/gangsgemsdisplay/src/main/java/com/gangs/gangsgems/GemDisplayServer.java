package com.gangs.gangsgems;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;

public final class GemDisplayServer implements ModInitializer {
    public static final Identifier CHANNEL = new Identifier("gangsgemsdisplay", "balance");

    @Override
    public void onInitialize() {
        if (!FabricLoader.getInstance().isModLoaded("gangsgems")) return;
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 20 != 0) return;
            WalletStore wallet = GangGems.wallet();
            boolean enabled = GangGems.furnitureCraftingEnabled();
            for (var player : server.getPlayerManager().getPlayerList()) {
                if (!ServerPlayNetworking.canSend(player, CHANNEL)) continue;
                var packet = PacketByteBufs.create();
                packet.writeBoolean(enabled);
                if (enabled) {
                    packet.writeBoolean(wallet != null);
                    if (wallet != null) packet.writeLong(wallet.balance(player.getUuid()));
                }
                ServerPlayNetworking.send(player, CHANNEL, packet);
            }
        });
    }
}
