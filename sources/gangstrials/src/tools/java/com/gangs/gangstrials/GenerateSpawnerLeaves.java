package com.gangs.gangstrials;

import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;

/**
 * Collapse the backport's connector -> contents chain into terminal pieces.
 * Otherwise connectors placed at the jigsaw depth limit never place their spawner.
 */
public final class GenerateSpawnerLeaves {
    private static final Map<String, List<String>> MOBS = new LinkedHashMap<>();
    static {
        MOBS.put("melee", List.of("husk", "spider", "zombie"));
        MOBS.put("ranged", List.of("poison_skeleton", "skeleton", "stray"));
        MOBS.put("slow_ranged", List.of("poison_skeleton", "skeleton", "stray"));
        MOBS.put("small_melee", List.of("baby_zombie", "cave_spider", "silverfish", "slime"));
        MOBS.put("breeze", List.of("breeze"));
    }

    public static void main(String[] args) throws IOException {
        Path output = Path.of(args[1]);
        try (ZipFile zip = new ZipFile(args[0])) {
            Map<String, List<Map<String, Object>>> pools = new LinkedHashMap<>();
            for (var group : MOBS.entrySet()) {
                List<Map<String, Object>> elements = new ArrayList<>();
                NbtCompound connector = read(zip, "spawner/connectors/" + group.getKey());
                for (String mob : group.getValue()) {
                    String name = group.getKey() + "/" + mob;
                    NbtCompound leaf = compose(connector, read(zip, "spawner/" + name));
                    Path target = output.resolve("data/gangstrials/structures/spawner/" + name + ".nbt");
                    Files.createDirectories(target.getParent());
                    NbtIo.writeCompressed(leaf, target.toFile());
                    elements.add(element(name, 1));
                }
                pools.put(group.getKey(), elements);
            }
            List<Map<String, Object>> all = new ArrayList<>();
            for (String group : List.of("melee", "ranged", "small_melee")) {
                for (String mob : MOBS.get(group)) {
                    all.add(element(group + "/" + mob, group.equals("small_melee") ? 3 : 4));
                }
            }
            pools.put("all", all);
            var gson = new GsonBuilder().setPrettyPrinting().create();
            for (var pool : pools.entrySet()) {
                Path last = output.resolve("data/gangstrials/worldgen/template_pool/spawner/fallback/" + pool.getKey() + ".json");
                Files.createDirectories(last.getParent());
                Files.writeString(last, gson.toJson(Map.of("elements", pool.getValue(), "fallback", "minecraft:empty")) + "\n");
            }
        }
    }

    private static Map<String, Object> element(String name, int weight) {
        return Map.of("weight", weight, "element", Map.of(
            "element_type", "minecraft:single_pool_element", "location", "gangstrials:spawner/" + name,
            "projection", "rigid", "processors", Map.of("processors", List.of())));
    }

    private static NbtCompound read(ZipFile zip, String name) throws IOException {
        String path = "data/minecraft/structures/trial_chambers/" + name + ".nbt";
        var entry = zip.getEntry(path);
        if (entry == null) throw new IOException("Missing pinned backport template: " + path);
        try (var input = zip.getInputStream(entry)) {
            return NbtIo.readCompressed(input);
        }
    }

    static NbtCompound compose(NbtCompound connector, NbtCompound contents) {
        if (!connector.getList("size", NbtElement.INT_TYPE).equals(ints(3, 1, 3))
                || !contents.getList("size", NbtElement.INT_TYPE).equals(ints(3, 2, 3))) {
            throw new IllegalArgumentException("Pinned spawner geometry changed; recheck connector alignment before rebuilding");
        }
        NbtCompound result = contents.copy();
        NbtList palette = contents.getList("palette", NbtElement.COMPOUND_TYPE).copy();
        NbtList blocks = new NbtList();
        NbtCompound air = new NbtCompound();
        air.putString("Name", "minecraft:air");
        int airIndex = palette.size();
        palette.add(air);
        for (NbtElement element : contents.getList("blocks", NbtElement.COMPOUND_TYPE)) {
            NbtCompound block = ((NbtCompound) element).copy();
            if (block.getCompound("nbt").getString("name").equals("minecraft:spawner")) {
                block.putInt("state", airIndex);
                block.remove("nbt");
            }
            blocks.add(block);
        }
        int offset = palette.size();
        NbtList connectorPalette = connector.getList("palette", NbtElement.COMPOUND_TYPE);
        for (NbtElement state : connectorPalette) palette.add(state.copy());
        for (NbtElement element : connector.getList("blocks", NbtElement.COMPOUND_TYPE)) {
            NbtCompound block = ((NbtCompound) element).copy();
            NbtList pos = block.getList("pos", NbtElement.INT_TYPE);
            // Contents' upward connector (y=1) attaches below the downward connector (y=0).
            block.put("pos", ints(pos.getInt(0), pos.getInt(1) + 2, pos.getInt(2)));
            if (block.getCompound("nbt").getString("target").equals("minecraft:spawner")) {
                block.putInt("state", airIndex);
                block.remove("nbt");
            } else {
                block.putInt("state", block.getInt("state") + offset);
            }
            blocks.add(block);
        }
        result.put("palette", palette);
        result.put("blocks", blocks);
        result.put("size", ints(3, 3, 3));
        return result;
    }

    private static NbtList ints(int x, int y, int z) {
        NbtList list = new NbtList();
        list.add(NbtInt.of(x));
        list.add(NbtInt.of(y));
        list.add(NbtInt.of(z));
        return list;
    }
}
