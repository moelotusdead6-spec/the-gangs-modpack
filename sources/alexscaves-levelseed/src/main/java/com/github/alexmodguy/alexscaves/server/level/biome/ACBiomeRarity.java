package com.github.alexmodguy.alexscaves.server.level.biome;

import com.github.alexmodguy.alexscaves.AlexsCaves;
import com.github.alexmodguy.alexscaves.server.config.BiomeGenerationConfig;
import com.github.alexmodguy.alexscaves.server.misc.VoronoiGenerator;
import com.google.common.collect.ImmutableList;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.synth.PerlinSimplexNoise;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ACBiomeRarity {
    private static long lastTestedSeed = 0;
    private static final List<Integer> BIOME_OCTAVES = ImmutableList.of(0);
    private static final PerlinSimplexNoise NOISE_X = new PerlinSimplexNoise(new XoroshiroRandomSource(1234L), BIOME_OCTAVES);
    private static final PerlinSimplexNoise NOISE_Z = new PerlinSimplexNoise(new XoroshiroRandomSource(4321L), BIOME_OCTAVES);

    private static volatile double voronoiOffsetAmount = 1.0D;
    private static double biomeSize;
    private static double seperationDistance;

    /*
     * Levels can share one biome source (Multiworld reuses the overworld generator for other
     * overworld-type dimensions), but each level has its own RandomState and therefore its own
     * Climate.Sampler. Keying the seed by sampler keeps every level on its own seed.
     */
    private static volatile SamplerEntry[] samplerEntries = new SamplerEntry[0];

    public record LevelContext(long seed, ResourceKey<Level> dimension) {
    }

    private record SamplerEntry(WeakReference<Climate.Sampler> sampler, LevelContext context) {
    }

    /**
     * Called after config reset
     */
    public static void init() {
        voronoiOffsetAmount = AlexsCaves.COMMON_CONFIG.caveBiomeSpacingRandomness.get();
        biomeSize = AlexsCaves.COMMON_CONFIG.caveBiomeMeanWidth.get() * 0.25D;
        seperationDistance = biomeSize + AlexsCaves.COMMON_CONFIG.caveBiomeMeanSeparation.get() * 0.25D;
    }

    public static void registerLevel(ServerLevel level) {
        registerSampler(level.getChunkSource().randomState().sampler(), level.getSeed(), level.dimension());
    }

    public static void registerSampler(Climate.Sampler sampler, long seed, ResourceKey<Level> dimension) {
        for (SamplerEntry entry : samplerEntries) {
            if (entry.sampler().get() == sampler && entry.context().seed() == seed && Objects.equals(entry.context().dimension(), dimension)) {
                return;
            }
        }
        synchronized (ACBiomeRarity.class) {
            List<SamplerEntry> kept = new ArrayList<>();
            for (SamplerEntry entry : samplerEntries) {
                Climate.Sampler existing = entry.sampler().get();
                if (existing != null && existing != sampler) {
                    kept.add(entry);
                }
            }
            kept.add(new SamplerEntry(new WeakReference<>(sampler), new LevelContext(seed, dimension)));
            samplerEntries = kept.toArray(new SamplerEntry[0]);
        }
    }

    @Nullable
    public static LevelContext getLevelContext(Climate.Sampler sampler) {
        for (SamplerEntry entry : samplerEntries) {
            if (entry.sampler().get() == sampler) {
                return entry.context();
            }
        }
        return null;
    }

    /**
     * Does the heavy lifting of finding if x and z quads should contain a rare biome.
     *
     * @param worldSeed seed of the world for Voroni generation
     * @param x         xQuad being tested
     * @param z         zQuad being tested
     * @return the voroni info if a rare biome is present
     */
    @Nullable
    public static VoronoiGenerator.VoronoiInfo getRareBiomeInfoForQuad(long worldSeed, int x, int z) {
        // A per-call generator: worldgen threads for different seeds must not share a mutable seed.
        VoronoiGenerator voronoiGenerator = new VoronoiGenerator(worldSeed);
        voronoiGenerator.setOffsetAmount(voronoiOffsetAmount);
        double sampleX = x / seperationDistance;
        double sampleZ = z / seperationDistance;
        double positionOffsetX = AlexsCaves.COMMON_CONFIG.caveBiomeWidthRandomness.get() * NOISE_X.getValue(sampleX, sampleZ, false);
        double positionOffsetZ = AlexsCaves.COMMON_CONFIG.caveBiomeWidthRandomness.get() * NOISE_Z.getValue(sampleX, sampleZ, false);
        VoronoiGenerator.VoronoiInfo info = voronoiGenerator.get2(sampleX + positionOffsetX, sampleZ + positionOffsetZ);
        if (info.distance() < (biomeSize / seperationDistance)) {
            return info;
        } else {
            return null;
        }
    }

    /**
     * gets the center of the biome pertaining to the voronoiInfo. Result is in quad coordinates.
     *
     * @param voronoiInfo the info of the sampled biome
     * @return a coordinate
     */
    @Nullable
    public static Vec3 getRareBiomeCenter(VoronoiGenerator.VoronoiInfo voronoiInfo) {
        return voronoiInfo.cellPos().scale(seperationDistance);
    }

    /**
     * gets the 'rarityOffset' for voroniInfo, which is essentially an internal biome ID.
     *
     * @param voronoiInfo the info of the sampled biome
     * @return the rarityOffset of the info
     */
    @Nullable
    public static int getRareBiomeOffsetId(VoronoiGenerator.VoronoiInfo voronoiInfo) {
        return (int) (((voronoiInfo.hash() + 1D) * 0.5D) * (double) BiomeGenerationConfig.getBiomeCount());
    }

    public static boolean isQuartInRareBiome(long worldSeed, int x, int z) {
        return ACBiomeRarity.getRareBiomeInfoForQuad(worldSeed, x, z) != null;
    }
}
