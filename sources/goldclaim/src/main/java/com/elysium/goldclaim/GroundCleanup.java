package com.elysium.goldclaim;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.ItemEntity;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.TypeFilter;

/** Clears every loaded dropped item every five minutes with chat warnings. */
final class GroundCleanup {
    private static GroundCleanupSchedule schedule;

    private GroundCleanup() {}

    static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> schedule = new GroundCleanupSchedule(System.currentTimeMillis()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> schedule = null);
        ServerTickEvents.END_SERVER_TICK.register(GroundCleanup::tick);
    }

    private static void tick(MinecraftServer server) {
        if (schedule == null) return;
        for (int seconds : schedule.poll(System.currentTimeMillis())) {
            if (seconds == GroundCleanupSchedule.CLEAR) {
                int removed = clear(server);
                broadcast(server, Text.literal("Ground cleanup removed " + removed + " items.").formatted(Formatting.GREEN, Formatting.BOLD));
            } else if (seconds == 60) {
                broadcast(server, Text.literal("[Ground Cleanup] Dropped items will be cleared in 1 minute!").formatted(Formatting.YELLOW));
            } else if (seconds == 30) {
                broadcast(server, Text.literal("[Ground Cleanup] Dropped items will be cleared in 30 seconds!").formatted(Formatting.GOLD));
            } else {
                broadcast(server, Text.literal("Clean-up in " + seconds).formatted(Formatting.RED, Formatting.BOLD));
            }
        }
    }

    static int clear(MinecraftServer server) {
        int removed = 0;
        for (ServerWorld world : server.getWorlds()) {
            List<ItemEntity> items = new ArrayList<>(world.getEntitiesByType(TypeFilter.instanceOf(ItemEntity.class), EntityPredicates.VALID_ENTITY));
            for (ItemEntity item : items) {
                removed += item.getStack().getCount();
                item.discard();
            }
        }
        return removed;
    }

    private static void broadcast(MinecraftServer server, Text message) {
        server.getPlayerManager().broadcast(message, false);
    }
}
