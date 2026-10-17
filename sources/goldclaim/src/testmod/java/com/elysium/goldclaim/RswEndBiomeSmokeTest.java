package com.elysium.goldclaim;

import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.biome.source.BiomeSource;

final class RswEndBiomeSmokeTest {
    private static boolean started;
    private static boolean prepared;
    private static int cursor;
    private static int land;
    private static int better;
    private static int vanillaFingerprint;
    private static long vanillaSeed;
    private static BiomeSource original;
    private static RswEndBiomeSource decoded;
    private static int cities;

    static void register() {
        boolean restart = "true".equals(System.getenv("GOLDCLAIM_BIOME_RESTART"));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            require(server.getTicks() < 12000, "RSW biome/reset fixture timed out");
            var end = server.getWorld(RegistryKey.of(RegistryKeys.WORLD,
                new Identifier(GoldClaimMod.RSW_THE_END_DIMENSION)));
            if (end == null) return;
            var vanilla = server.getWorld(World.END);
            if (!started) {
                original = end.getChunkManager().getChunkGenerator().getBiomeSource();
                vanillaSeed = vanilla.getSeed();
                vanillaFingerprint = fingerprint(vanilla);
                require(!(vanilla.getChunkManager().getChunkGenerator().getBiomeSource()
                    instanceof RswEndBiomeSource), "Vanilla End received the RSW biome policy");
                if (!restart) {
                    require(!(original instanceof RswEndBiomeSource), "Existing End changed before its reset");
                    RswResetSmokeTest.start(server);
                }
                started = true;
                return;
            }
            if (!prepared) {
                if (!restart && !RswResetSmokeTest.verify(server)) return;
                var source = end.getChunkManager().getChunkGenerator().getBiomeSource();
                require(source instanceof RswEndBiomeSource, "Reset/reload did not use the RSW-only biome source");
                require(restart || source != original, "The reset reused the current End's biome source");
                require(vanilla.getSeed() == vanillaSeed && fingerprint(vanilla) == vanillaFingerprint,
                    "RSW reset changed vanilla End biome selection");
                var encoded = BiomeSource.CODEC.encodeStart(JsonOps.INSTANCE, source).getOrThrow(false,
                    error -> { throw new IllegalStateException(error); });
                var roundTrip = BiomeSource.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow(false,
                    error -> { throw new IllegalStateException(error); });
                require(roundTrip instanceof RswEndBiomeSource, "Codec lost the RSW-only biome policy");
                decoded = (RswEndBiomeSource)roundTrip;
                require(decoded.getTogetherConfig().innerVoidRadiusSquared == 1048576,
                    "Central island boundary changed");
                try {
                    require(Files.readString(Path.of("config", "multiworld", "worlds", "multiworld",
                        "rsw_the_end.yml")).contains("custom_generator: " + RswEndBiomeSource.GENERATOR_ID),
                        "Multiworld did not persist the RSW biome generator");
                } catch (IOException failure) {
                    throw new IllegalStateException("Cannot read persisted fixture generator", failure);
                }
                prepared = true;
            }
            var generator = end.getChunkManager().getChunkGenerator();
            var noise = end.getChunkManager().getNoiseConfig();
            for (int batch = 0; batch < 24 && cursor < 5625; batch++, cursor++) {
                int x = -4736 + (cursor / 75) * 128;
                int z = -4736 + (cursor % 75) * 128;
                if ((long)x * x + (long)z * z < 1100L * 1100L) continue;
                int height = generator.getHeight(x, z, Heightmap.Type.WORLD_SURFACE_WG, end, noise);
                if (height <= end.getBottomY()) continue;
                var biome = end.getGeneratorStoredBiome(x >> 2, (height - 1) >> 2, z >> 2);
                require(biome.equals(((BiomeSource)decoded).getBiome(x >> 2, (height - 1) >> 2, z >> 2,
                    noise.getMultiNoiseSampler())), "Codec changed deterministic biome selection");
                land++;
                if (biome.getKey().orElseThrow().getValue().getNamespace().equals("betterend")) better++;
            }
            if (cursor < 5625) return;
            double share = (double)better / land;
            require(land >= 1000, "Too few actual land samples: " + land);
            require(share >= 0.85 && share <= 0.90, "BetterEnd surface-land share outside 85-90%: " + share);
            if (!restart) {
                try {
                    var instance = GoldClaimMod.class.getDeclaredField("INSTANCE");
                    instance.setAccessible(true);
                    var field = GoldClaimMod.class.getDeclaredField("rswEndCities");
                    field.setAccessible(true);
                    var service = (RswEndCityService)field.get(instance.get(null));
                    var boxes = service.completedCities();
                    require(boxes.size() == 7, "Reset reopened without seven saved cities");
                    if (cities < boxes.size()) {
                        EndCitySmokeTest.verifyCity(end, boxes.get(cities++));
                        return;
                    }
                } catch (ReflectiveOperationException failure) {
                    throw new IllegalStateException("Cannot verify actual reset cities", failure);
                }
            }
            try {
                Files.writeString(Path.of("biome-smoke-passed.txt"), "PASS: " + (restart ? "restart" : "full reset")
                    + "; BetterEnd surface land=" + better + "/" + land + " (" + share * 100 + "%)"
                    + "; seed=" + end.getSeed() + "; current End unchanged until reset; vanilla End unchanged; "
                    + "custom generator persisted; codec deterministic; seven-city reset gate passed.\n");
            } catch (IOException failure) {
                throw new IllegalStateException("Cannot save biome fixture result", failure);
            }
            server.stop(false);
        });
    }

    private static int fingerprint(ServerWorld world) {
        int hash = 1;
        for (int x = -4096; x <= 4096; x += 1024) {
            for (int z = -4096; z <= 4096; z += 1024) {
                hash = 31 * hash + world.getGeneratorStoredBiome(x >> 2, 24, z >> 2).getKey().orElseThrow().hashCode();
            }
        }
        return hash;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
