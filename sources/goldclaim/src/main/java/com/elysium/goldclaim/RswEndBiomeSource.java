package com.elysium.goldclaim;

import com.elysium.goldclaim.mixin.BiomePickerAccessor;
import com.elysium.goldclaim.mixin.EndBiomeSourceAccessor;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import org.betterx.bclib.api.v2.generator.BCLChunkGenerator;
import org.betterx.bclib.api.v2.generator.BCLibEndBiomeSource;
import org.betterx.bclib.api.v2.generator.BiomePicker;
import org.betterx.bclib.api.v2.generator.config.BCLEndBiomeSourceConfig;
import org.betterx.bclib.api.v2.levelgen.biomes.BiomeAPI;
import org.betterx.bclib.util.WeighTree;
import org.betterx.bclib.util.WeightedList;

public final class RswEndBiomeSource extends BCLibEndBiomeSource {
    public static final String GENERATOR_ID = "gangs:rsw_end";
    public static final Codec<RswEndBiomeSource> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.LONG.fieldOf("seed").forGetter((RswEndBiomeSource source) -> source.currentSeed),
        BCLEndBiomeSourceConfig.CODEC.fieldOf("config").forGetter(RswEndBiomeSource::getTogetherConfig)
    ).apply(instance, RswEndBiomeSource::new));

    public RswEndBiomeSource(long seed, BCLEndBiomeSourceConfig config) {
        super(config);
        this.setSeed(seed);
        this.reloadBiomes();
    }

    @Override
    protected void onInitMap(long seed) {
        super.onInitMap(seed);
        var maps = (EndBiomeSourceAccessor)(BCLibEndBiomeSource)this;
        // Nullscape puts real land in vanilla "void" and barrens noise regions too.
        maps.goldclaim$setVoidMap(maps.goldclaim$landMap());
        maps.goldclaim$setBarrensMap(maps.goldclaim$landMap());
    }

    static void registerCodec() {
        Registry.register(Registries.BIOME_SOURCE, new Identifier(GENERATOR_ID), CODEC);
    }

    static ChunkGenerator createGenerator(ServerWorld end) {
        var original = end.getChunkManager().getChunkGenerator();
        if (!(original instanceof NoiseChunkGenerator noise)
                || !(original.getBiomeSource() instanceof BCLibEndBiomeSource source)) {
            throw new IllegalStateException("RSW End biome policy requires the installed BCLib End generator");
        }
        return new BCLChunkGenerator(new RswEndBiomeSource(end.getSeed(), source.getTogetherConfig()),
            noise.getSettings());
    }

    @Override
    protected void onFinishBiomeRebuild(Map<BiomeAPI.BiomeType, BiomePicker> pickers) {
        super.onFinishBiomeRebuild(pickers);
        if (!this.wasBound()) return;
        var accessor = (BiomePickerAccessor)pickers.get(BiomeAPI.BiomeType.END_LAND);
        var biomes = accessor.goldclaim$biomes().stream().filter(biome -> biome.isValid).toList();
        var weights = RswEndBiomeWeights.normalize(biomes.stream()
            .map(biome -> new RswEndBiomeWeights.Entry(biome.key.getValue().toString(),
                biome.bclBiome.settings.getGenChance())).toList());
        WeightedList<BiomePicker.ActualBiome> weighted = new WeightedList<>();
        for (int i = 0; i < biomes.size(); i++) weighted.add(biomes.get(i), weights[i]);
        accessor.goldclaim$setTree(new WeighTree<>(weighted));
    }

    @Override
    protected Codec<? extends BiomeSource> getCodec() {
        return CODEC;
    }
}
