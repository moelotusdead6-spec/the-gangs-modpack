package com.gangs.gangstrials.smoke;

import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import me.isaiah.multiworld.MultiworldMod;
import me.isaiah.multiworld.command.CreateCommand;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.structure.Structure;

final class RswStructureAudit {
    private static final String[] WORLDS = {"rsw", "rsw_nether", "rsw_the_end"};
    private static final Map<String, Object> report = new LinkedHashMap<>();
    private static final List<Map<String, Object>> results = new ArrayList<>();
    private static List<String> groups;
    private static int worldIndex;
    private static int groupIndex;

    static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            require(Files.exists(Path.of("LOCAL_TEST_ONLY")), "Missing local-only fixture marker");
            var generator = CreateCommand.get_chunk_gen(server, "gangs:rsw_end");
            var dimension = CreateCommand.get_dim_id("THE_END");
            require(generator != null && dimension != null, "GoldClaim RSW End generator not registered");
            var world = MultiworldMod.create_world("multiworld:rsw_the_end", dimension, generator,
                server.getSaveProperties().getDifficulty(), 123456789L);
            require(world != null, "Could not create fixture RSW End");
            CreateCommand.make_config(world, "THE_END", 123456789L, "gangs:rsw_end");
        });
        ServerTickEvents.END_SERVER_TICK.register(RswStructureAudit::tick);
    }

    private static void tick(MinecraftServer server) {
        if (groups == null) {
            for (String id : WORLDS) if (world(server, id) == null) return;
            require(server.getDataPackManager().getEnabledNames().contains("gangs_fixes"),
                "Paxi gangs_fixes datapack is not enabled");
            var registry = server.getRegistryManager().get(RegistryKeys.STRUCTURE);
            var names = new TreeSet<String>();
            registry.getIds().forEach(id -> {
                if (!id.getNamespace().equals("minecraft")) names.add(id.getNamespace());
            });
            names.add("minecraft:trial_chambers");
            groups = List.copyOf(names);
            report.put("status", "running");
            report.put("loadedMods", FabricLoader.getInstance().getAllMods().stream()
                .map(mod -> mod.getMetadata().getId() + "@" + mod.getMetadata().getVersion()).sorted().toList());
            report.put("enabledDataPacks", server.getDataPackManager().getEnabledNames());
            report.put("searchRadiusChunks", 180);
            report.put("results", results);
            writeReport();
        }
        ServerWorld world = world(server, WORLDS[worldIndex]);
        String group = groups.get(groupIndex);
        var registry = server.getRegistryManager().get(RegistryKeys.STRUCTURE);
        var availableBiomes = world.getChunkManager().getChunkGenerator().getBiomeSource().getBiomes();
        List<RegistryEntry<Structure>> eligible = registry.streamEntries()
            .filter(entry -> {
                Identifier id = entry.registryKey().getValue();
                return group.equals("minecraft:trial_chambers")
                    ? id.toString().equals(group) : id.getNamespace().equals(group);
            })
            .filter(entry -> availableBiomes.stream().anyMatch(entry.value().getValidBiomes()::contains))
            .map(entry -> (RegistryEntry<Structure>) entry)
            .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dimension", world.getRegistryKey().getValue().toString());
        result.put("generator", world.getChunkManager().getChunkGenerator().getClass().getName());
        result.put("biomeSource", world.getChunkManager().getChunkGenerator().getBiomeSource().getClass().getName());
        result.put("seed", world.getSeed());
        result.put("group", group);
        result.put("eligibleDefinitions", eligible.size());
        System.out.println("RSW_AUDIT_SEARCH " + result);
        if (eligible.isEmpty()) {
            result.put("status", "no eligible registered biomes");
        } else {
            var found = world.getChunkManager().getChunkGenerator().locateStructure(world,
                RegistryEntryList.of(eligible), BlockPos.ORIGIN, 180, false);
            if (found == null) {
                result.put("status", "not located within search radius");
            } else {
                BlockPos pos = found.getFirst();
                var start = world.getChunk(pos.getX() >> 4, pos.getZ() >> 4)
                    .getStructureStart(found.getSecond().value());
                result.put("structure", found.getSecond().getKey().orElseThrow().getValue().toString());
                result.put("x", pos.getX());
                result.put("z", pos.getZ());
                result.put("withinBorder", Math.abs(pos.getX()) <= (worldIndex == 0 ? 3500 : 5000)
                    && Math.abs(pos.getZ()) <= (worldIndex == 0 ? 3500 : 5000));
                result.put("status", start != null && start.hasChildren()
                    ? "generated structure start" : "located but no structure start in located chunk");
                if (start != null) result.put("pieces", start.getChildren().size());
            }
        }
        results.add(result);
        System.out.println("RSW_AUDIT_RESULT " + result);
        if (++groupIndex == groups.size()) {
            groupIndex = 0;
            worldIndex++;
        }
        if (worldIndex == WORLDS.length) {
            report.put("status", "complete");
            writeReport();
            server.stop(false);
        } else {
            writeReport();
        }
    }

    private static ServerWorld world(MinecraftServer server, String name) {
        return server.getWorld(RegistryKey.of(RegistryKeys.WORLD, new Identifier("multiworld", name)));
    }

    private static void writeReport() {
        try {
            Files.writeString(Path.of("rsw-structure-audit.json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot save structure audit", failure);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
