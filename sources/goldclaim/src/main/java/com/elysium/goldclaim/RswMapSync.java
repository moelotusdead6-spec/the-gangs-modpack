package com.elysium.goldclaim;

import java.util.function.LongSupplier;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.S2CPlayChannelEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class RswMapSync {
    static final Identifier CHANNEL = new Identifier("goldclaim", "rsw_map_generation");
    static final int PROTOCOL = 1;
    private static final Logger LOGGER = LoggerFactory.getLogger("GoldClaim/RSWMap");
    private final LongSupplier generation;
    private volatile long currentGeneration;

    RswMapSync(LongSupplier generation) {
        this.generation = generation;
    }

    void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> this.currentGeneration = this.generation.getAsLong());
        ServerLoginNetworking.registerGlobalReceiver(CHANNEL, (server, handler, understood, buf, synchronizer, sender) -> {
            if (!understood) LOGGER.debug("Client does not support RSW map generations; its JourneyMap requires a client update.");
        });
        // Establish the map epoch before any client world or mapping tasks are created.
        ServerLoginConnectionEvents.QUERY_START.register((handler, server, sender, synchronizer) ->
            sender.sendPacket(CHANNEL, encode(this.currentGeneration)));
        S2CPlayChannelEvents.REGISTER.register((handler, sender, server, channels) -> {
            if (channels.contains(CHANNEL)) server.execute(() ->
                ServerPlayNetworking.send(handler.getPlayer(), CHANNEL, encode(this.currentGeneration)));
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long updated = this.generation.getAsLong();
            if (updated == this.currentGeneration) return;
            this.currentGeneration = updated;
            for (var player : server.getPlayerManager().getPlayerList()) {
                if (ServerPlayNetworking.canSend(player, CHANNEL)) {
                    ServerPlayNetworking.send(player, CHANNEL, encode(updated));
                }
            }
            LOGGER.info("RSW map generation advanced to {}; updated connected map clients.", updated);
        });
    }

    static PacketByteBuf encode(long generation) {
        if (generation < 0) throw new IllegalArgumentException("Negative RSW map generation");
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(PROTOCOL);
        buf.writeLong(generation);
        return buf;
    }
}
