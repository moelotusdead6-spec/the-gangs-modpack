package com.elysium.goldclaim;

import com.mojang.datafixers.util.Either;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import org.slf4j.LoggerFactory;

final class RswEntrySearch {
    private final RswResetService resets;
    private final BiPredicate<ServerWorld, BlockPos> safe;
    private final Consumer<Entry> teleport;
    private final Map<UUID, Entry> pending = new HashMap<>();

    static final class Entry {
        final ServerCommandSource source;
        final ServerPlayerEntity player;
        final ServerWorld world;
        final ServerWorld previousWorld;
        final long generation;
        final int radius;
        final RswEntryLocator.Search search;
        BlockPos column;
        BlockPos landing;
        CompletableFuture<Either<Chunk, ChunkHolder.Unloaded>> chunk;

        Entry(ServerCommandSource source, ServerPlayerEntity player, ServerWorld world, long generation, int radius) {
            this.source = source;
            this.player = player;
            this.world = world;
            this.previousWorld = player.getServerWorld();
            this.generation = generation;
            this.radius = radius;
            this.search = new RswEntryLocator.Search(radius);
        }
    }

    RswEntrySearch(RswResetService resets, BiPredicate<ServerWorld, BlockPos> safe, Consumer<Entry> teleport) {
        this.resets = resets;
        this.safe = safe;
        this.teleport = teleport;
    }

    int request(ServerCommandSource source, ServerPlayerEntity player, ServerWorld world, int radius) {
        if (this.pending.containsKey(player.getUuid())) {
            source.sendError(Text.literal("Your RSW entry search is already in progress."));
            return 0;
        }
        Entry entry;
        try {
            entry = new Entry(source, player, world, this.resets.generation(), radius);
        } catch (IllegalArgumentException invalidConfiguration) {
            LoggerFactory.getLogger("GoldClaim/RSW").error("Invalid entry search configuration", invalidConfiguration);
            source.sendError(Text.literal(invalidConfiguration.getMessage()));
            return 0;
        }
        if (!this.advance(entry)) {
            this.pending.put(player.getUuid(), entry);
            source.sendFeedback(() -> Text.literal("Finding safe RSW ground near 0,0. You will teleport when it is ready."), false);
        }
        return entry.landing != null || this.pending.containsKey(player.getUuid()) ? 1 : 0;
    }

    void tick() {
        Iterator<Entry> iterator = this.pending.values().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (entry.player.isDisconnected()) {
                iterator.remove();
            } else if (this.resets.isClosed() || this.resets.generation() != entry.generation
                    || entry.player.getServerWorld() != entry.previousWorld
                    || entry.player.isRemoved() || !entry.player.isAlive()) {
                entry.source.sendError(Text.literal("RSW entry cancelled because the world or your destination changed."));
                iterator.remove();
            } else if (this.advance(entry)) {
                iterator.remove();
            }
        }
    }

    private boolean advance(Entry entry) {
        for (int attempt = 0; attempt < 64; attempt++) {
            if (entry.column == null) {
                entry.column = entry.search.next();
                if (entry.column == null) {
                    entry.source.sendError(Text.literal("No safe RSW ground was found within " + entry.radius
                        + " blocks of 0,0. Please notify an operator."));
                    return true;
                }
                if (!entry.world.getWorldBorder().contains(entry.column)) {
                    entry.column = null;
                    continue;
                }
            }
            int x = entry.column.getX();
            int z = entry.column.getZ();
            if (entry.world.getChunkManager().getWorldChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16)) == null) {
                if (entry.chunk == null) {
                    entry.chunk = entry.world.getChunkManager().getChunkFutureSyncOnMainThread(
                        Math.floorDiv(x, 16), Math.floorDiv(z, 16), ChunkStatus.FULL, true);
                }
                if (!entry.chunk.isDone()) {
                    return false;
                }
                try {
                    if (entry.chunk.join().left().isEmpty()) {
                        entry.source.sendError(Text.literal("RSW terrain could not be loaded safely. Try again."));
                        return true;
                    }
                } catch (CompletionException failure) {
                    LoggerFactory.getLogger("GoldClaim/RSW").error("Entry terrain loading failed", failure);
                    entry.source.sendError(Text.literal("RSW terrain loading failed. Please notify an operator."));
                    return true;
                }
            }
            int y = entry.world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos feet = new BlockPos(x, y, z);
            if (entry.world.isInBuildLimit(feet.up()) && this.safe.test(entry.world, feet)) {
                entry.landing = feet;
                this.teleport.accept(entry);
                return true;
            }
            entry.column = null;
            entry.chunk = null;
        }
        return false;
    }
}
