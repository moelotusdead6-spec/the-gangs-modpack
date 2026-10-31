package com.elysium.goldclaim;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Comparator;
import java.util.function.Consumer;
import com.elysium.goldclaim.mixin.ServerChunkManagerInvoker;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkStatus;
import org.popcraft.chunky.Chunky;
import org.popcraft.chunky.ChunkyFabric;
import org.popcraft.chunky.GenerationTask;
import org.popcraft.chunky.Selection;
import org.popcraft.chunky.event.task.GenerationTaskUpdateEvent;
import org.popcraft.chunky.platform.FabricWorld;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class RswPregenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger("GoldClaim/Chunky");
    // Pregenerate the central 60% of the 7,000-block border's area: 7000 * sqrt(0.6) / 2 = 2711.
    static final int PREGEN_RADIUS = (int)Math.floor(GoldClaimMod.RSW_OVERWORLD_BORDER * Math.sqrt(0.6) / 2.0);
    private GenerationTask task;
    private CheckedWorld world;
    private volatile boolean complete;
    private long lastProgress;
    private final Consumer<GenerationTaskUpdateEvent> listener = this::progress;

    private static Chunky chunky() {
        return FabricLoader.getInstance().getEntrypoints("main", ModInitializer.class).stream()
            .filter(ChunkyFabric.class::isInstance).map(ChunkyFabric.class::cast)
            .map(ChunkyFabric::getChunky).filter(java.util.Objects::nonNull).findFirst()
            .orElseThrow(() -> new IllegalStateException("Chunky 1.3.146 must be installed and initialized; RSW remains closed."));
    }

    static void requireNoRunningTasks() {
        if (!FabricLoader.getInstance().isModLoaded("chunky")) {
            throw new IllegalStateException("Chunky is missing; RSW cannot safely reset and pregenerate.");
        }
        Chunky chunky = chunky();
        for (String sub : RswResetService.RSW_SUB_DIMENSIONS) {
            if (chunky.getApi().isRunning("multiworld:" + sub)) {
                throw new IllegalStateException("Stop the existing Chunky task for multiworld:" + sub
                    + " before resetting RSW.");
            }
        }
    }

    boolean prepare(ServerWorld target) {
        Chunky chunky = chunky();
        if (this.task == null) {
            String name = target.getRegistryKey().getValue().toString();
            if (chunky.getApi().isRunning(name)) {
                throw new IllegalStateException("An unrelated Chunky task is running for " + name
                    + "; RSW remains closed until it stops.");
            }
            // Never reuse task counts or the generated-chunk cache from an older seed.
            chunky.getTaskLoader().cancelTask(new FabricWorld(target));
            chunky.getRegionCache().clear(name);
            this.world = new CheckedWorld(target);
            var border = target.getWorldBorder();
            double radius = Math.min(PREGEN_RADIUS, border.getSize() / 2);
            Selection selection = Selection.builder(chunky, this.world).shape("square")
                .center(border.getCenterX(), border.getCenterZ())
                .radiusX(radius).radiusZ(radius).build();
            this.complete = false;
            this.task = new GenerationTask(chunky, selection);
            chunky.getEventBus().subscribe(GenerationTaskUpdateEvent.class, this.listener);
            chunky.getGenerationTasks().put(name, this.task);
            chunky.getScheduler().runTask(this.task);
            LOGGER.info("Pregenerating {}: square radius {} blocks (border {}). RSW remains closed.", name, radius, border.getSize());
            return false;
        }
        if (this.world.getServerWorld() != target) {
            throw new IllegalStateException("RSW world changed while Chunky was generating.");
        }
        Throwable failure = this.world.failure.get();
        if (failure != null) {
            this.task.stop(false);
            chunky.getEventBus().unsubscribe(GenerationTaskUpdateEvent.class, this.listener);
            throw new IllegalStateException("Chunky encountered a chunk read/generation failure; RSW remains closed.", failure);
        }
        if (!this.complete) {
            if (!chunky.getApi().isRunning(target.getRegistryKey().getValue().toString())
                    && this.task.getChunkIterator().hasNext()) {
                throw new IllegalStateException("RSW Chunky task was paused/cancelled. Restart to rescan saved chunks; RSW remains closed.");
            }
            return false;
        }
        target.save(null, true, false);
        chunky.getEventBus().unsubscribe(GenerationTaskUpdateEvent.class, this.listener);
        LOGGER.info("RSW Overworld pregeneration completed and saved: {} chunks.", this.task.getCount());
        return true;
    }

    private void progress(GenerationTaskUpdateEvent event) {
        if (event.generationTask() != this.task) return;
        var progress = event.generationTask().getProgress();
        if (progress.isComplete()) {
            this.complete = true;
        } else if (System.currentTimeMillis() - this.lastProgress >= 60000) {
            this.lastProgress = System.currentTimeMillis();
            LOGGER.info("RSW pregeneration: {}%, {} chunks/s, ETA {}h {}m {}s; RSW closed.",
                progress.getPercentComplete(), progress.getRate(),
                progress.getHours(), progress.getMinutes(), progress.getSeconds());
        }
    }

    private static final class CheckedWorld extends FabricWorld {
        private static final ChunkTicketType<ChunkPos> TICKET = ChunkTicketType.create(
            "goldclaim_pregeneration", Comparator.comparingLong(ChunkPos::toLong));
        final AtomicReference<Throwable> failure = new AtomicReference<>();

        CheckedWorld(ServerWorld world) { super(world); }

        private <T> CompletableFuture<T> check(CompletableFuture<T> future) {
            return future.whenComplete((value, error) -> {
                if (error != null) this.failure.compareAndSet(null, error);
            });
        }

        @Override public CompletableFuture<Boolean> isChunkGenerated(int x, int z) {
            return check(super.isChunkGenerated(x, z));
        }

        @Override public CompletableFuture<Void> getChunkAtAsync(int x, int z) {
            var server = getServerWorld().getServer();
            return check(server.submit(() -> {
                var manager = getServerWorld().getChunkManager();
                ChunkPos pos = new ChunkPos(x, z);
                manager.addTicket(TICKET, pos, 0, pos);
                try {
                    return ((ServerChunkManagerInvoker)manager)
                        .goldclaim$requestChunk(x, z, ChunkStatus.FULL, true)
                        .<Void>thenApply(result -> {
                            if (result.left().isEmpty()) {
                                throw new IllegalStateException("Chunky did not produce FULL chunk " + x + "," + z);
                            }
                            return null;
                        }).whenCompleteAsync((ignored, error) -> manager.removeTicket(TICKET, pos, 0, pos), server);
                } catch (RuntimeException | Error error) {
                    manager.removeTicket(TICKET, pos, 0, pos);
                    throw error;
                }
            }).thenCompose(future -> future));
        }
    }
}
