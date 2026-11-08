package com.elysium.goldclaimmap;

import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class RswMapNetworking {
    static final Identifier CHANNEL = new Identifier("goldclaim", "rsw_map_generation");
    private static final int PROTOCOL = 1;
    private static final Logger LOGGER = LoggerFactory.getLogger("GoldClaimMap/RSW");

    static void register() {
        ClientLoginNetworking.registerGlobalReceiver(CHANNEL, (client, handler, buf, callbacks) -> {
            try {
                RswMapPaths.setGeneration(decode(buf));
                return CompletableFuture.completedFuture(PacketByteBufs.create());
            } catch (IllegalArgumentException | IndexOutOfBoundsException failure) {
                LOGGER.error("Invalid RSW map login data; refusing an unsafe map session.", failure);
                return CompletableFuture.failedFuture(failure);
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(CHANNEL, (client, handler, buf, sender) -> {
            try {
                long generation = decode(buf);
                client.execute(() -> {
                    RswMapPaths.setGeneration(generation);
                    LOGGER.info("JourneyMap RSW maps now use generation {}.", generation);
                });
            } catch (IllegalArgumentException | IndexOutOfBoundsException failure) {
                LOGGER.error("Invalid RSW map generation update.", failure);
                client.execute(() -> handler.getConnection().disconnect(Text.literal("Invalid RSW map generation data.")));
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> RswMapPaths.disconnect());
    }

    static long decode(PacketByteBuf buf) {
        if (buf.readVarInt() != PROTOCOL) throw new IllegalArgumentException("Unsupported RSW map protocol");
        long generation = buf.readLong();
        if (generation < 0 || buf.isReadable()) throw new IllegalArgumentException("Invalid RSW map generation payload");
        return generation;
    }
}
