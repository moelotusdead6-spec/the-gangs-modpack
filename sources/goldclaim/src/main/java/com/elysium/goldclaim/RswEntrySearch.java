package com.elysium.goldclaim;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
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
        final long started = System.nanoTime();
        TeleportChunkArea area;

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
        this.pending.put(player.getUuid(), entry);
        source.sendFeedback(() -> Text.literal("Finding safe RSW ground near 0,0. You will teleport when it is ready."), false);
        return 1;
    }

    boolean isPending(UUID player) {
        return this.pending.containsKey(player);
    }

    /** Queues a search that only advances from tick(), so callers inside travel code never teleport re-entrantly. */
    boolean requestDeferred(ServerCommandSource source, ServerPlayerEntity player, ServerWorld world, int radius) {
        if (this.pending.containsKey(player.getUuid())) return false;
        Entry entry;
        try {
            entry = new Entry(source, player, world, this.resets.generation(), radius);
        } catch (IllegalArgumentException invalidConfiguration) {
            LoggerFactory.getLogger("GoldClaim/RSW").error("Invalid entry search configuration", invalidConfiguration);
            return false;
        }
        this.pending.put(player.getUuid(), entry);
        source.sendFeedback(() -> Text.literal("[RSW] No valid RSW spawn point; finding safe RSW ground near 0,0."), false);
        return true;
    }

    void tick() {
        Iterator<Entry> iterator = this.pending.values().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            boolean finished = false;
            try {
                if (entry.player.isDisconnected()) {
                    finished = true;
                } else if (this.resets.isClosed() || this.resets.generation() != entry.generation
                        || entry.world.getServer().getWorld(entry.world.getRegistryKey()) != entry.world
                        || entry.player.getServerWorld() != entry.previousWorld
                        || entry.player.isRemoved() || !entry.player.isAlive()) {
                    entry.source.sendError(Text.literal("RSW entry cancelled because the world or your destination changed."));
                    finished = true;
                } else if (System.nanoTime() - entry.started > 120_000_000_000L) {
                    LoggerFactory.getLogger("GoldClaim/RSW").warn("Entry terrain timed out for {}",
                        entry.player.getUuid());
                    entry.source.sendError(Text.literal("RSW terrain loading timed out. Try again."));
                    finished = true;
                } else {
                    finished = this.advance(entry);
                }
            } catch (CompletionException | IllegalStateException failure) {
                LoggerFactory.getLogger("GoldClaim/RSW").error("Entry terrain loading failed", failure);
                entry.source.sendError(Text.literal("RSW terrain loading failed. Please notify an operator."));
                finished = true;
            } finally {
                if (finished) {
                    if (entry.area != null) entry.area.close();
                    iterator.remove();
                }
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
            if (entry.area == null) entry.area = new TeleportChunkArea(entry.world, x, z, 1);
            if (!entry.area.ready()) return false;
            int y = entry.world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos feet = new BlockPos(x, y, z);
            if (entry.world.isInBuildLimit(feet.up()) && this.safe.test(entry.world, feet)
                    && RswLanding.isSafe(entry.world, new Vec3d(x + 0.5, y, z + 0.5),
                        entry.player.getWidth(), Math.max(1.8, entry.player.getHeight()))) {
                entry.landing = feet;
                this.teleport.accept(entry);
                return true;
            }
            entry.column = null;
            entry.area.close();
            entry.area = null;
        }
        return false;
    }
}
