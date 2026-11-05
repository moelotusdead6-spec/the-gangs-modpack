package com.github.alexmodguy.alexscaves.gangs.smoke;

import com.github.alexmodguy.alexscaves.server.item.CaveMapItem;
import com.github.alexmodguy.alexscaves.server.level.biome.ACBiomeRarity;
import com.github.alexmodguy.alexscaves.server.level.biome.MultiNoiseBiomeSourceAccessor;
import com.github.alexmodguy.alexscaves.server.level.map.CaveBiomeMapWorldWorker;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reproduces the RSW setup: Multiworld creates multiworld:rsw from the overworld's own ChunkGenerator
 * with a different seed, so both levels share one MultiNoiseBiomeSource.
 */
public final class LevelSeedSmokeTest implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("AC LevelSeed Smoke");
    private static final long RSW_SEED = 8_675_309_123L;
    private static final int GRID_RADIUS = 4096;
    private static final int GRID_STEP = 32;
    private static final int SAMPLE_Y = -30;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            List<String> lines = new ArrayList<>();
            boolean passed;
            try {
                passed = run(server, lines);
            } catch (Throwable t) {
                LOGGER.error("Smoke test crashed", t);
                lines.add("ERROR " + t);
                passed = false;
            }
            lines.add(0, passed ? "PASS" : "FAIL");
            lines.forEach(LOGGER::info);
            try {
                Files.write(Path.of("levelseed-smoke-result.txt"), lines);
            } catch (IOException e) {
                LOGGER.error("Could not write result", e);
            }
            server.halt(false);
        });
    }

    private static boolean run(MinecraftServer server, List<String> out) throws Exception {
        ServerLevel overworld = server.overworld();
        ServerLevel rsw = createRsw(server, overworld);
        // Generating a fresh chunk runs the ChunkStatus hook for each level, like RSW landing/pregen.
        rsw.getChunk(0, 0, ChunkStatus.FULL, true);
        overworld.getChunk(200, 200, ChunkStatus.FULL, true);

        BiomeSource source = overworld.getChunkSource().getGenerator().getBiomeSource();
        boolean shared = source == rsw.getChunkSource().getGenerator().getBiomeSource();
        out.add("shared biome source: " + shared + " (" + source.getClass().getName() + ")");
        out.add("seeds: overworld=" + overworld.getSeed() + " rsw=" + rsw.getSeed());
        boolean ok = shared && overworld.getSeed() != rsw.getSeed();

        MultiNoiseBiomeSourceAccessor state = (MultiNoiseBiomeSourceAccessor) source;
        ServerLevel[] levels = {overworld, rsw};
        Map<ResourceKey<Level>, Map<Long, ResourceKey<Biome>>> baselines = new HashMap<>();
        for (int i = 0; i < 2; i++) {
            ServerLevel level = levels[i];
            ServerLevel other = levels[1 - i];
            setSharedState(state, level);
            Map<Long, ResourceKey<Biome>> baseline = sampleGrid(source, level);
            setSharedState(state, other);
            Map<Long, ResourceKey<Biome>> cross = sampleGrid(source, level);
            int acCount = (int) baseline.values().stream().filter(LevelSeedSmokeTest::isCaveBiome).count();
            int mismatches = 0;
            for (Map.Entry<Long, ResourceKey<Biome>> entry : baseline.entrySet()) {
                if (!entry.getValue().equals(cross.get(entry.getKey()))) {
                    mismatches++;
                }
            }
            out.add(level.dimension().location() + ": cave-biome samples=" + acCount
                + ", changed while the other level owns the shared state=" + mismatches);
            ok &= acCount > 0 && mismatches == 0;
            baselines.put(level.dimension(), baseline);
        }

        for (int i = 0; i < 2; i++) {
            ok &= checkCaveMap(levels[i], levels[1 - i], state, baselines.get(levels[i].dimension()), out);
        }
        return ok;
    }

    private static boolean checkCaveMap(ServerLevel level, ServerLevel other, MultiNoiseBiomeSourceAccessor state,
                                        Map<Long, ResourceKey<Biome>> baseline, List<String> out) {
        Map<ResourceKey<Biome>, Integer> counts = new HashMap<>();
        baseline.values().stream().filter(LevelSeedSmokeTest::isCaveBiome).forEach(k -> counts.merge(k, 1, Integer::sum));
        ResourceKey<Biome> target = counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        if (target == null) {
            out.add(level.dimension().location() + ": no cave biome to map");
            return false;
        }
        FakePlayer player = FakePlayer.get(level);
        player.setPos(0.5D, SAMPLE_Y, 0.5D);
        ItemStack map = CaveMapItem.createMap(target);
        CaveBiomeMapWorldWorker worker = new CaveBiomeMapWorldWorker(map, level, new BlockPos(0, SAMPLE_Y, 0), player, UUID.randomUUID());
        int steps = 0;
        boolean working = true;
        while (working && steps < 200_000) {
            // Another dimension generating chunks keeps rewriting the shared biome-source state.
            setSharedState(state, other);
            working = worker.doWork();
            steps++;
        }
        CompoundTag tag = map.getOrCreateTag();
        if (!tag.getBoolean("Filled")) {
            out.add(level.dimension().location() + ": map for " + target.location() + " NOT filled after " + steps + " steps");
            return false;
        }
        BlockPos found = new BlockPos(tag.getInt("BiomeX"), tag.getInt("BiomeY"), tag.getInt("BiomeZ"));
        boolean real = false;
        for (int dx = -16; dx <= 16 && !real; dx += 8) {
            for (int dy = -16; dy <= 16 && !real; dy += 8) {
                for (int dz = -16; dz <= 16 && !real; dz += 8) {
                    BlockPos pos = found.offset(dx, dy, dz);
                    level.getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, true);
                    real = level.getBiome(pos).is(target);
                }
            }
        }
        Holder<Biome> atFound = level.getBiome(found);
        out.add(level.dimension().location() + ": map for " + target.location() + " -> " + found.toShortString()
            + ", generated biome there=" + atFound.unwrapKey().map(k -> k.location().toString()).orElse("?")
            + ", target present within 16 blocks=" + real + " (" + steps + " steps)");
        return real;
    }

    private static Map<Long, ResourceKey<Biome>> sampleGrid(BiomeSource source, ServerLevel level) {
        Climate.Sampler sampler = level.getChunkSource().randomState().sampler();
        Map<Long, ResourceKey<Biome>> result = new HashMap<>();
        int quartY = QuartPos.fromBlock(SAMPLE_Y);
        for (int x = -GRID_RADIUS; x <= GRID_RADIUS; x += GRID_STEP) {
            for (int z = -GRID_RADIUS; z <= GRID_RADIUS; z += GRID_STEP) {
                int quartX = QuartPos.fromBlock(x);
                int quartZ = QuartPos.fromBlock(z);
                if (!ACBiomeRarity.isQuartInRareBiome(level.getSeed(), quartX, quartZ)) {
                    continue;
                }
                ResourceKey<Biome> key = source.getNoiseBiome(quartX, quartY, quartZ, sampler).unwrapKey().orElseThrow();
                result.put(BlockPos.asLong(x, SAMPLE_Y, z), key);
            }
        }
        return result;
    }

    private static void setSharedState(MultiNoiseBiomeSourceAccessor state, ServerLevel level) {
        state.setLastSampledSeed(level.getSeed());
        state.setLastSampledDimension(level.dimension());
    }

    private static boolean isCaveBiome(ResourceKey<Biome> key) {
        return key.location().getNamespace().equals("alexscaves");
    }

    private static ServerLevel createRsw(MinecraftServer server, ServerLevel overworld) throws Exception {
        Class<?> multiworld = Class.forName("me.isaiah.multiworld.MultiworldMod");
        Method create = null;
        for (Method method : multiworld.getMethods()) {
            if (method.getName().equals("create_world") && method.getParameterCount() == 5) {
                create = method;
            }
        }
        if (create == null) {
            throw new IllegalStateException("Multiworld create_world not found");
        }
        // Our SERVER_STARTED listener can run before Multiworld's own, which assigns this field.
        java.lang.reflect.Field mc = multiworld.getField("mc");
        if (mc.get(null) == null) {
            mc.set(null, server);
        }
        create.invoke(null, "multiworld:rsw", new ResourceLocation("minecraft", "overworld"),
            overworld.getChunkSource().getGenerator(), server.getWorldData().getDifficulty(), RSW_SEED);
        ServerLevel rsw = server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation("multiworld", "rsw")));
        if (rsw == null) {
            throw new IllegalStateException("multiworld:rsw was not created");
        }
        return rsw;
    }
}
