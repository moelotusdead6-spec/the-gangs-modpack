package com.elysium.goldclaim;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.slf4j.LoggerFactory;

final class TeleportPreparation {
    private final RswResetService resets;
    private final Map<UUID, Pending> pending = new HashMap<>();

    record Destination(BlockPos column, BooleanSupplier complete) {}

    private static final class Pending {
        final ServerPlayerEntity player;
        final ServerWorld origin;
        final ServerWorld world;
        final long generation;
        final long started = System.nanoTime();
        final Supplier<Destination> next;
        final BooleanSupplier valid;
        final String success;
        Destination destination;
        TeleportChunkArea area;

        Pending(ServerPlayerEntity player, ServerWorld world, long generation,
                Supplier<Destination> next, BooleanSupplier valid, String success) {
            this.player = player;
            this.origin = player.getServerWorld();
            this.world = world;
            this.generation = generation;
            this.next = next;
            this.valid = valid;
            this.success = success;
        }
    }

    TeleportPreparation(RswResetService resets) {
        this.resets = resets;
    }

    boolean request(ServerPlayerEntity player, ServerWorld world, Supplier<Destination> next,
            BooleanSupplier valid, String success) {
        if (this.pending.containsKey(player.getUuid())) {
            player.sendMessage(Text.literal("Your teleport destination is already being prepared."), false);
            return false;
        }
        if (GoldClaimMod.RSW_DIMENSIONS.contains(world.getRegistryKey().getValue().toString())
                && this.resets.isClosed()) {
            player.sendMessage(Text.literal("RSW is resetting; teleport is temporarily unavailable."), false);
            return false;
        }
        this.pending.put(player.getUuid(), new Pending(player, world, this.resets.generation(), next, valid, success));
        player.sendMessage(Text.literal("Preparing teleport terrain. You will teleport when it is ready."), false);
        return true;
    }

    boolean isPending(UUID player) {
        return this.pending.containsKey(player);
    }

    void tick() {
        Iterator<Pending> iterator = this.pending.values().iterator();
        while (iterator.hasNext()) {
            Pending entry = iterator.next();
            boolean finished = false;
            try {
                boolean rsw = GoldClaimMod.RSW_DIMENSIONS.contains(entry.world.getRegistryKey().getValue().toString());
                if (entry.player.isDisconnected() || entry.player.isRemoved() || !entry.player.isAlive()
                        || entry.player.getServerWorld() != entry.origin
                        || entry.world.getServer().getWorld(entry.world.getRegistryKey()) != entry.world
                        || (rsw && (this.resets.isClosed() || entry.generation != this.resets.generation()))
                        || !entry.valid.getAsBoolean()) {
                    entry.player.sendMessage(Text.literal("Teleport cancelled because a player or destination changed."), false);
                    finished = true;
                } else if (System.nanoTime() - entry.started > 120_000_000_000L) {
                    LoggerFactory.getLogger("GoldClaim").warn("Teleport terrain timed out for {} in {}",
                        entry.player.getUuid(), entry.world.getRegistryKey().getValue());
                    entry.player.sendMessage(Text.literal("Teleport terrain loading timed out. Try again."), false);
                    finished = true;
                } else {
                    finished = this.advance(entry);
                }
            } catch (CompletionException | IllegalStateException failure) {
                LoggerFactory.getLogger("GoldClaim").error("Teleport terrain preparation failed for {}",
                    entry.player.getUuid(), failure);
                entry.player.sendMessage(Text.literal("Teleport terrain loading failed. Please notify an operator."), false);
                finished = true;
            } finally {
                if (finished) {
                    if (entry.area != null) entry.area.close();
                    iterator.remove();
                }
            }
        }
    }

    private boolean advance(Pending entry) {
        if (entry.destination == null) {
            entry.destination = entry.next.get();
            if (entry.destination == null) {
                entry.player.sendMessage(Text.literal("Could not find a safe random teleport location. Try again."), false);
                return true;
            }
            BlockPos column = entry.destination.column();
            entry.area = new TeleportChunkArea(entry.world, column.getX(), column.getZ(), 1);
        }
        if (!entry.area.ready()) return false;
        if (entry.destination.complete().getAsBoolean()) {
            entry.player.sendMessage(Text.literal(entry.success), false);
            return true;
        }
        entry.area.close();
        entry.area = null;
        entry.destination = null;
        return false;
    }
}
