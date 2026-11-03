package com.terraformersmc.biolith.impl.biome;

import com.terraformersmc.biolith.api.biome.SubBiomeMatcher;
import com.terraformersmc.biolith.impl.config.BiolithState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import org.joml.Vector2fc;

public final class BiolithConcurrencySmokeTest {
    private static final SubBiomeMatcher MATCH_ALL = new SubBiomeMatcher() {
        @Override
        public SubBiomeMatcher addCriterion(Criterion criterion) {
            throw new UnsupportedOperationException("Fixed test matcher");
        }

        @Override
        public void sort() {}

        @Override
        public boolean matches(BiolithFittestNodes<RegistryEntry<Biome>> nodes,
                               DimensionBiomePlacement placement, MultiNoiseUtil.NoiseValuePoint noise,
                               Vector2fc range, float replacementNoise) {
            return true;
        }
    };

    private BiolithConcurrencySmokeTest() {}

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            var world = server.getOverworld();
            var lookup = world.getRegistryManager().get(RegistryKeys.BIOME).getReadOnlyWrapper();
            try {
                verifyDistribution(world, lookup);
                var placement = fixture(world, lookup, 12345L);
                verifyPublication(placement, lookup, false);
                verifyPublication(placement, lookup, true);
                verifyFailure(placement, lookup);
                verifyConcurrentReads(placement, lookup);
                Files.writeString(Path.of("policy-smoke-passed.txt"),
                    "PASS: Biolith replacement/sub-biome immutable publication; blocked rebuilds; "
                    + "failed rebuild retains prior snapshot; 100 seeds x 1001 unchanged selections; "
                    + "800000 parallel selection pairs during 2000 world reinitializations; "
                    + "End vanilla placeholder remains unresolved and valid.\n");
            } catch (Exception failure) {
                throw new IllegalStateException("Biolith concurrency regression failed", failure);
            }
            server.stop(false);
        });
    }

    private static OverworldBiomePlacement fixture(ServerWorld world, RegistryEntryLookup<Biome> lookup, long seed) {
        var placement = new OverworldBiomePlacement();
        placement.state = new BiolithState(world, "concurrency_test");
        placement.seedRandom = new Random(seed);
        placement.addReplacement(BiomeKeys.PLAINS, BiomeKeys.DESERT, 0.3D);
        placement.addReplacement(BiomeKeys.PLAINS, BiomeKeys.FOREST, 0.6D);
        placement.addSubBiome(BiomeKeys.PLAINS, BiomeKeys.FOREST, MATCH_ALL);
        placement.addSubBiome(BiomeKeys.PLAINS, BiomeKeys.DESERT, MATCH_ALL);
        placement.replacementRequests.get(BiomeKeys.PLAINS).complete(lookup);
        placement.subBiomeRequests.get(BiomeKeys.PLAINS).complete(lookup);
        return placement;
    }

    private static void verifyDistribution(ServerWorld world, RegistryEntryLookup<Biome> lookup) {
        for (int seed = 0; seed < 100; seed++) {
            var placement = fixture(world, lookup, seed);
            var actual = placement.replacementRequests.get(BiomeKeys.PLAINS);
            var expected = new ArrayList<DimensionBiomePlacement.ReplacementRequest>();
            expected.add(DimensionBiomePlacement.ReplacementRequest.of(BiomeKeys.DESERT, 0.3D));
            expected.add(DimensionBiomePlacement.ReplacementRequest.of(BiomeKeys.FOREST, 0.6D));
            expected.add(DimensionBiomePlacement.ReplacementRequest.of(DimensionBiomePlacement.VANILLA_PLACEHOLDER, 0.4D));
            Collections.shuffle(expected, new Random(seed));
            // Saved world ordering remains authoritative, including after world reloads.
            var order = placement.state.getBiomeReplacements(BiomeKeys.PLAINS).toList();
            expected.sort(Comparator.comparingInt(request -> order.indexOf(request.biome())));
            double locus = 0D;
            for (int i = 0; i < expected.size(); i++) {
                var request = expected.get(i);
                expected.set(i, request.complete(lookup, locus, locus += request.rate() / (0.3D + 0.6D + 0.4D)));
            }
            require(actual.requests.size() == expected.size(), "Replacement count changed");
            for (int i = 0; i < expected.size(); i++) {
                var before = expected.get(i);
                var after = actual.requests.get(i);
                require(before.biome().equals(after.biome()) && before.start() == after.start()
                    && before.end() == after.end() && before.biomeEntry() == after.biomeEntry(),
                    "Seed/order/range/registry entry changed");
                require(after.biome().equals(DimensionBiomePlacement.VANILLA_PLACEHOLDER)
                    == (after.biomeEntry() == null), "End placeholder fix regressed");
            }
            for (int sample = 0; sample <= 1000; sample++) {
                double noise = sample / 1000D;
                var reference = expected.stream().filter(request -> request.end() > noise).findFirst().orElse(null);
                require(actual.selectReplacement(noise) == (reference == null ? null
                    : actual.requests.get(expected.indexOf(reference))), "Biome selection changed");
            }
            var first = actual.requests;
            actual.complete(lookup);
            require(first.equals(actual.requests), "Reload changed saved biome ordering");
            expectImmutable(first);
            expectImmutable(placement.subBiomeRequests.get(BiomeKeys.PLAINS).requests);
        }
    }

    private static void verifyPublication(OverworldBiomePlacement placement, RegistryEntryLookup<Biome> lookup,
                                          boolean subBiome) throws Exception {
        var replacements = placement.replacementRequests.get(BiomeKeys.PLAINS);
        var subBiomes = placement.subBiomeRequests.get(BiomeKeys.PLAINS);
        var previous = subBiome ? subBiomes.requests : replacements.requests;
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var blocked = new ForwardingLookup(lookup) {
            @Override
            public Optional<RegistryEntry.Reference<Biome>> getOptional(RegistryKey<Biome> key) {
                entered.countDown();
                try {
                    require(release.await(10, TimeUnit.SECONDS), "Blocked rebuild timed out");
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted registry lookup", failure);
                }
                return super.getOptional(key);
            }
        };
        ExecutorService writer = Executors.newSingleThreadExecutor();
        Future<?> rebuild = writer.submit(() -> {
            if (subBiome) subBiomes.complete(blocked);
            else replacements.complete(blocked);
        });
        try {
            require(entered.await(10, TimeUnit.SECONDS), "Rebuild never reached registry lookup");
            require(previous == (subBiome ? subBiomes.requests : replacements.requests),
                "Mutable/incomplete list was published during " + (subBiome ? "sub-biome" : "replacement") + " rebuild");
            expectImmutable(previous);
        } finally {
            release.countDown();
            try {
                rebuild.get(10, TimeUnit.SECONDS);
            } finally {
                writer.shutdownNow();
                require(writer.awaitTermination(10, TimeUnit.SECONDS), "Rebuild worker did not terminate");
            }
        }
        require(previous != (subBiome ? subBiomes.requests : replacements.requests), "New snapshot not published");
    }

    private static void verifyFailure(OverworldBiomePlacement placement, RegistryEntryLookup<Biome> lookup) {
        var replacements = placement.replacementRequests.get(BiomeKeys.PLAINS);
        var previous = replacements.requests;
        var subBiomes = placement.subBiomeRequests.get(BiomeKeys.PLAINS);
        var previousSub = subBiomes.requests;
        var failing = new ForwardingLookup(lookup) {
            @Override
            public Optional<RegistryEntry.Reference<Biome>> getOptional(RegistryKey<Biome> key) {
                throw new IllegalArgumentException("Expected missing-registry fixture");
            }
        };
        for (boolean subBiome : new boolean[] {false, true}) {
            try {
                if (subBiome) subBiomes.complete(failing);
                else replacements.complete(failing);
                throw new IllegalStateException("Registry error was swallowed");
            } catch (IllegalArgumentException expected) {
                require(expected.getMessage().equals("Expected missing-registry fixture"), "Unexpected registry error");
            }
        }
        require(replacements.requests == previous && subBiomes.requests == previousSub,
            "Failed rebuild replaced valid snapshot");
    }

    private static void verifyConcurrentReads(OverworldBiomePlacement placement, RegistryEntryLookup<Biome> lookup)
        throws Exception {
        var replacements = placement.replacementRequests.get(BiomeKeys.PLAINS);
        var subBiomes = placement.subBiomeRequests.get(BiomeKeys.PLAINS);
        var start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(9);
        List<Future<?>> tasks = new ArrayList<>();
        try {
            tasks.add(workers.submit(() -> {
                await(start);
                for (int i = 0; i < 2000; i++) {
                    placement.serverReplaced(placement.state, 12345L + i);
                }
            }));
            for (int reader = 0; reader < 8; reader++) {
                tasks.add(workers.submit(() -> {
                    await(start);
                    for (int i = 0; i < 100000; i++) {
                        double noise = (i % 1000) / 1000D;
                        var request = replacements.selectReplacement(noise);
                        require(request != null && request.start() <= noise && request.end() > noise,
                            "Reader saw incomplete replacement range");
                        require(request.biome().equals(DimensionBiomePlacement.VANILLA_PLACEHOLDER)
                            || request.biomeEntry() != null, "Reader saw unresolved replacement");
                        var sub = subBiomes.selectSubBiome(null, null, null, noise);
                        require(sub != null && sub.biomeEntry() != null, "Reader saw unresolved sub-biome");
                    }
                }));
            }
            start.countDown();
            for (var task : tasks) task.get(30, TimeUnit.SECONDS);
        } finally {
            workers.shutdownNow();
            require(workers.awaitTermination(10, TimeUnit.SECONDS), "Stress workers did not terminate");
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            require(latch.await(10, TimeUnit.SECONDS), "Stress start timed out");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted stress worker", failure);
        }
    }

    private static void expectImmutable(List<?> list) {
        try {
            list.clear();
            throw new IllegalStateException("Published list is mutable");
        } catch (UnsupportedOperationException expected) {
            // An immutable snapshot must reject structural changes.
        }
    }

    private static class ForwardingLookup implements RegistryEntryLookup<Biome> {
        private final RegistryEntryLookup<Biome> delegate;

        ForwardingLookup(RegistryEntryLookup<Biome> delegate) {
            this.delegate = delegate;
        }

        @Override
        public Optional<RegistryEntry.Reference<Biome>> getOptional(RegistryKey<Biome> key) {
            return delegate.getOptional(key);
        }

        @Override
        public Optional<RegistryEntryList.Named<Biome>> getOptional(TagKey<Biome> tag) {
            return delegate.getOptional(tag);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
