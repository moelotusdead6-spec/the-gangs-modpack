package com.elysium.goldclaim;

import dev.corgitaco.enhancedcelestials.EnhancedCelestials;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class CelestialPolicy {
    private static final Logger LOGGER = LoggerFactory.getLogger("GoldClaim/Celestials");
    private static final java.util.Set<String> MISSING_LOGGED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    static void register() {
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            String dimension = world.getRegistryKey().getValue().toString();
            boolean rsw = GoldClaimMod.RSW_DIMENSION.equals(dimension);
            if (!rsw && world.getRegistryKey() != World.OVERWORLD) return;
            var state = world.getPersistentStateManager().getOrCreate(
                ForecastMigration::read, ForecastMigration::new, "goldclaim_celestial_policy");
            if (state.complete) return;
            if (!updateForecast(world, rsw)) {
                if (MISSING_LOGGED.add(dimension)) {
                    LOGGER.warn("No Enhanced Celestials forecast for {} yet; lunar policy migration deferred.", dimension);
                }
                return;
            }
            state.complete = true;
            state.markDirty();
            LOGGER.info("Migrated lunar forecast for {}; {}.", dimension,
                rsw ? "all RSW events enabled" : "queued Blood Moons removed; other Wild events preserved");
        });
    }

    static boolean updateForecast(ServerWorld world, boolean rsw) {
        var data = EnhancedCelestials.lunarForecastWorldData(world);
        if (data.isEmpty()) return false;
        var forecast = data.get();
        if (rsw) {
            // Saved forecasts span 100 days and otherwise retain the old blood-only event pool.
            forecast.recomputeForecast();
        } else {
            forecast.removeFromForecastIf(event -> {
                var id = event.getLunarEventKey().getValue();
                return id.getNamespace().equals("enhancedcelestials")
                    && (id.getPath().equals("blood_moon") || id.getPath().equals("super_blood_moon"));
            });
        }
        return true;
    }

    private static final class ForecastMigration extends PersistentState {
        private boolean complete;

        static ForecastMigration read(NbtCompound nbt) {
            var state = new ForecastMigration();
            state.complete = nbt.getBoolean("complete");
            return state;
        }

        @Override
        public NbtCompound writeNbt(NbtCompound nbt) {
            nbt.putBoolean("complete", this.complete);
            return nbt;
        }
    }
}
