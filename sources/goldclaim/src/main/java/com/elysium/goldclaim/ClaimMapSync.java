package com.elysium.goldclaim;

import com.elysium.goldclaim.data.Claim;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.S2CPlayChannelEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Pushes claim rectangles to clients that run the optional GoldClaim Map mod.
 * Clients without it never register the channel and receive nothing.
 */
public final class ClaimMapSync {
    public static final Identifier CHANNEL = new Identifier("goldclaim", "claim_map");
    public static final int PROTOCOL_VERSION = 1;
    private static final int MAX_NAME_LENGTH = 64;

    private final ClaimManager claimManager;
    private final AtomicBoolean broadcastQueued = new AtomicBoolean();
    private volatile MinecraftServer server;

    public ClaimMapSync(ClaimManager claimManager) {
        this.claimManager = claimManager;
    }

    public void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(started -> this.server = started);
        ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> this.server = null);
        S2CPlayChannelEvents.REGISTER.register((handler, sender, server, channels) -> {
            if (channels.contains(CHANNEL)) {
                server.execute(() -> this.send(handler.getPlayer(), this.encode()));
            }
        });
        this.claimManager.setChangeListener(this::queueBroadcast);
    }

    private void queueBroadcast() {
        MinecraftServer current = this.server;
        if (current != null && this.broadcastQueued.compareAndSet(false, true)) {
            current.execute(() -> {
                this.broadcastQueued.set(false);
                this.broadcast(current);
            });
        }
    }

    private void broadcast(MinecraftServer current) {
        PacketByteBuf payload = null;
        for (ServerPlayerEntity player : current.getPlayerManager().getPlayerList()) {
            if (!ServerPlayNetworking.canSend(player, CHANNEL)) {
                continue;
            }
            if (payload == null) {
                payload = this.encode();
            }
            this.send(player, payload);
        }
    }

    private void send(ServerPlayerEntity player, PacketByteBuf payload) {
        if (ServerPlayNetworking.canSend(player, CHANNEL)) {
            ServerPlayNetworking.send(player, CHANNEL, PacketByteBufs.copy(payload));
        }
    }

    private PacketByteBuf encode() {
        List<Claim> claims = this.claimManager.getAllClaims().stream()
                .filter(claim -> claim.dimension != null)
                .toList();
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(PROTOCOL_VERSION);
        buf.writeVarInt(claims.size());
        for (Claim claim : claims) {
            buf.writeString(claim.dimension);
            buf.writeInt(claim.minX);
            buf.writeInt(claim.maxX);
            buf.writeInt(claim.minZ);
            buf.writeInt(claim.maxZ);
            buf.writeBoolean(claim.adminClaim);
            String owner = claim.ownerName == null ? "" : claim.ownerName;
            buf.writeString(owner.length() > MAX_NAME_LENGTH ? owner.substring(0, MAX_NAME_LENGTH) : owner, MAX_NAME_LENGTH);
        }
        return buf;
    }
}
