package com.elysium.goldclaim;

import com.mojang.datafixers.util.Either;
import com.elysium.goldclaim.mixin.ServerChunkManagerInvoker;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ChunkLevels;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.chunk.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.gen.structure.Structure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class RswEndCityService {
    static final int REQUIRED_CITIES = 7;
    private static final Logger LOGGER = LoggerFactory.getLogger("GoldClaim/RSW");
    private static final ChunkTicketType<ChunkPos> TICKET = ChunkTicketType.create(
        "goldclaim_end_city", Comparator.comparingLong(ChunkPos::toLong));
    private ServerWorld world;
    private Structure city;
    private final ArrayDeque<ChunkPos> candidates = new ArrayDeque<>();
    private final ArrayDeque<ChunkPos> pendingChunks = new ArrayDeque<>();
    private final List<BlockBox> completed = new ArrayList<>();
    private StructureStart pending;
    private CompletableFuture<Either<Chunk, ChunkHolder.Unloaded>> loading;
    private ChunkPos ticketPos;
    private int ticketRadius;
    private boolean saved;

    // Called only while a new reset is still closed, never for an existing playable End.
    boolean prepare(ServerWorld end) {
        if (!end.getRegistryKey().getValue().toString().equals(GoldClaimMod.RSW_THE_END_DIMENSION)) {
            throw new IllegalArgumentException("City preparation requires RSW End");
        }
        if (this.world != end) {
            this.initialize(end);
        }
        if (this.completed.size() >= REQUIRED_CITIES) {
            return this.finish();
        }
        if (!this.pendingChunks.isEmpty()) {
            if (this.load(this.pendingChunks.getFirst(), ChunkStatus.FULL) == null) return false;
            this.pendingChunks.removeFirst();
            if (this.pendingChunks.isEmpty()) {
                this.completed.add(this.pending.getBoundingBox());
                LOGGER.info("RSW End city {}/{} fully generated inside border at {}: {}",
                    this.completed.size(), REQUIRED_CITIES, this.pending.getPos(), this.pending.getBoundingBox());
                this.pending = null;
                if (this.completed.size() == REQUIRED_CITIES) {
                    return this.finish();
                }
            }
            return false;
        }
        if (this.candidates.isEmpty()) {
            throw new IllegalStateException("Only " + this.completed.size() + "/" + REQUIRED_CITIES
                + " reachable End cities found for seed " + end.getSeed() + "; RSW remains closed");
        }
        Chunk chunk = this.load(this.candidates.getFirst(), ChunkStatus.STRUCTURE_STARTS);
        if (chunk == null) return false;
        this.candidates.removeFirst();
        StructureStart start = chunk.getStructureStart(this.city);
        if (start == null || !start.hasChildren() || !fits(start.getBoundingBox(), end.getWorldBorder())
                || start.getBoundingBox().getMinY() < end.getBottomY()
                || start.getBoundingBox().getMaxY() >= end.getTopY()
                || this.completed.stream().anyMatch(box -> tooClose(box, start.getBoundingBox()))) {
            return false;
        }
        this.pending = start;
        BlockBox box = start.getBoundingBox();
        for (int x = Math.floorDiv(box.getMinX(), 16); x <= Math.floorDiv(box.getMaxX(), 16); x++) {
            for (int z = Math.floorDiv(box.getMinZ(), 16); z <= Math.floorDiv(box.getMaxZ(), 16); z++) {
                this.pendingChunks.add(new ChunkPos(x, z));
            }
        }
        return false;
    }

    private boolean finish() {
        // A failed save must be retried before the reset journal can declare this world ready.
        if (!this.saved) {
            this.world.getChunkManager().save(true);
            this.saved = true;
        }
        return true;
    }

    private Chunk load(ChunkPos pos, ChunkStatus status) {
        if (this.loading == null) {
            var manager = this.world.getChunkManager();
            this.ticketPos = pos;
            this.ticketRadius = 33 - ChunkLevels.getLevelFromStatus(status);
            manager.addTicket(TICKET, pos, this.ticketRadius, pos);
            // The public SyncOnMainThread API waits even though it returns a future.
            this.loading = ((ServerChunkManagerInvoker)manager).goldclaim$requestChunk(pos.x, pos.z, status, true);
        }
        if (!this.loading.isDone()) return null;
        try {
            return this.loading.join().left()
                .orElseThrow(() -> new IllegalStateException("RSW End chunk could not load: " + pos));
        } finally {
            this.world.getChunkManager().removeTicket(TICKET, this.ticketPos, this.ticketRadius, this.ticketPos);
            this.ticketPos = null;
            this.loading = null;
        }
    }

    private void initialize(ServerWorld end) {
        this.world = end;
        this.city = end.getRegistryManager().get(RegistryKeys.STRUCTURE)
            .getOrThrow(net.minecraft.registry.RegistryKey.of(RegistryKeys.STRUCTURE,
                new Identifier("minecraft", "end_city")));
        this.candidates.clear();
        this.pendingChunks.clear();
        this.completed.clear();
        this.pending = null;
        this.loading = null;
        this.saved = false;
        var set = end.getRegistryManager().get(RegistryKeys.STRUCTURE_SET)
            .get(new Identifier("minecraft", "end_cities"));
        if (set == null || !(set.placement() instanceof RandomSpreadStructurePlacement placement)) {
            throw new IllegalStateException("End City random-spread placement is missing");
        }
        int spacing = placement.getSpacing();
        WorldBorder border = end.getWorldBorder();
        List<ChunkPos> positions = new ArrayList<>();
        int minX = Math.floorDiv((int)Math.floor(border.getBoundWest()), 16 * spacing);
        int maxX = Math.floorDiv((int)Math.ceil(border.getBoundEast()), 16 * spacing);
        int minZ = Math.floorDiv((int)Math.floor(border.getBoundNorth()), 16 * spacing);
        int maxZ = Math.floorDiv((int)Math.ceil(border.getBoundSouth()), 16 * spacing);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                ChunkPos pos = placement.getStartChunk(end.getChunkManager().getStructurePlacementCalculator()
                    .getStructureSeed(), x * spacing, z * spacing);
                if (fits(new BlockBox(pos.getStartX(), 0, pos.getStartZ(),
                        pos.getEndX(), 255, pos.getEndZ()), border)) {
                    positions.add(pos);
                }
            }
        }
        // A seed-stable shuffle spreads the reserved cities across the playable outer islands.
        positions.sort(Comparator.comparingLong(pos -> mix(pos.toLong() ^ end.getSeed())));
        this.candidates.addAll(positions);
        LOGGER.info("Preparing at least {} natural RSW End cities from {} in-border candidates for seed {}",
            REQUIRED_CITIES, positions.size(), end.getSeed());
    }

    static boolean fits(BlockBox box, WorldBorder border) {
        double nearestX = Math.max(box.getMinX(), Math.min(0, box.getMaxX()));
        double nearestZ = Math.max(box.getMinZ(), Math.min(0, box.getMaxZ()));
        return nearestX * nearestX + nearestZ * nearestZ >= 1024.0 * 1024.0
            && box.getMinX() >= border.getBoundWest() + 16
            && box.getMaxX() < border.getBoundEast() - 16
            && box.getMinZ() >= border.getBoundNorth() + 16
            && box.getMaxZ() < border.getBoundSouth() - 16;
    }

    private static boolean tooClose(BlockBox first, BlockBox second) {
        int dx = Math.max(0, Math.max(first.getMinX() - second.getMaxX(), second.getMinX() - first.getMaxX()));
        int dz = Math.max(0, Math.max(first.getMinZ() - second.getMaxZ(), second.getMinZ() - first.getMaxZ()));
        return (long)dx * dx + (long)dz * dz < 512L * 512L;
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    List<BlockBox> completedCities() {
        return List.copyOf(this.completed);
    }
}
