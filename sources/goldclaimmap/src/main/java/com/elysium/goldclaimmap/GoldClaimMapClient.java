package com.elysium.goldclaimmap;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GoldClaimMapClient implements ClientModInitializer {
    // Must match com.elysium.goldclaim.ClaimMapSync on the server.
    public static final Identifier CHANNEL = new Identifier("goldclaim", "claim_map");
    private static final int PROTOCOL_VERSION = 1;
    private static final int MAX_NAME_LENGTH = 64;
    private static final Logger LOGGER = LoggerFactory.getLogger("goldclaimmap");

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(CHANNEL, (client, handler, buf, sender) -> {
            List<ClaimRegion> claims = decode(buf);
            if (claims != null) {
                client.execute(() -> GoldClaimJourneyMapPlugin.setClaims(claims));
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                client.execute(() -> GoldClaimJourneyMapPlugin.setClaims(List.of())));
    }

    private static List<ClaimRegion> decode(PacketByteBuf buf) {
        int protocol = buf.readVarInt();
        if (protocol != PROTOCOL_VERSION) {
            LOGGER.warn("Ignoring claim map data with unsupported protocol {}", protocol);
            return null;
        }
        int count = buf.readVarInt();
        List<ClaimRegion> claims = new ArrayList<>(Math.min(count, 1024));
        for (int i = 0; i < count; i++) {
            String dimension = buf.readString();
            int minX = buf.readInt();
            int maxX = buf.readInt();
            int minZ = buf.readInt();
            int maxZ = buf.readInt();
            boolean admin = buf.readBoolean();
            String owner = buf.readString(MAX_NAME_LENGTH);
            Identifier dimensionId = Identifier.tryParse(dimension);
            if (dimensionId != null) {
                claims.add(new ClaimRegion(RegistryKey.of(RegistryKeys.WORLD, dimensionId), minX, maxX, minZ, maxZ, admin, owner));
            }
        }
        return claims;
    }
}
