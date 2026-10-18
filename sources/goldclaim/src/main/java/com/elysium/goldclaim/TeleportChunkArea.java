package com.elysium.goldclaim;

import com.elysium.goldclaim.mixin.ServerChunkManagerInvoker;
import com.mojang.datafixers.util.Either;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;

final class TeleportChunkArea implements AutoCloseable {
    private static final ChunkTicketType<UUID> TICKET = ChunkTicketType.create(
        "goldclaim_teleport", Comparator.comparing(UUID::toString));
    private final ServerWorld world;
    private final UUID ticket = UUID.randomUUID();
    private final List<ChunkPos> positions = new ArrayList<>();
    private final List<ChunkPos> held = new ArrayList<>();
    private CompletableFuture<Either<Chunk, ChunkHolder.Unloaded>> loading;
    private int index;

    TeleportChunkArea(ServerWorld world, int x, int z, int radius) {
        this.world = world;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                this.positions.add(new ChunkPos(Math.floorDiv(x, 16) + dx, Math.floorDiv(z, 16) + dz));
            }
        }
    }

    boolean ready() {
        var manager = this.world.getChunkManager();
        while (this.index < this.positions.size()) {
            ChunkPos pos = this.positions.get(this.index);
            if (this.loading == null) {
                manager.addTicket(TICKET, pos, 0, this.ticket);
                this.held.add(pos);
                // SyncOnMainThread waits for terrain even though its return type is a future.
                this.loading = ((ServerChunkManagerInvoker)manager).goldclaim$requestChunk(
                    pos.x, pos.z, ChunkStatus.FULL, true);
            }
            if (!this.loading.isDone()) return false;
            if (this.loading.join().left().isEmpty()) {
                throw new IllegalStateException("Teleport chunk could not load: " + pos);
            }
            this.loading = null;
            this.index++;
        }
        return true;
    }

    @Override
    public void close() {
        for (ChunkPos pos : this.held) {
            this.world.getChunkManager().removeTicket(TICKET, pos, 0, this.ticket);
        }
        this.held.clear();
    }
}
