package com.gangs.gangstrials;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import org.junit.Test;
import static org.junit.Assert.*;

public final class SpawnerLeavesTest {
    private static final Path ROOT = Path.of("build", "generated", "spawnerLeaves", "data");

    @Test
    public void terminalTemplatesPreserveSpawnerAndRemoveExpansionHop() throws Exception {
        try (var files = Files.walk(ROOT.resolve("gangstrials/structures/spawner"))) {
            var templates = files.filter(path -> path.toString().endsWith(".nbt")).toList();
            assertEquals(14, templates.size());
            for (Path template : templates) {
                var tag = NbtIo.readCompressed(template.toFile());
                var palette = tag.getList("palette", NbtElement.COMPOUND_TYPE);
                int spawners = 0;
                int jigsaws = 0;
                for (var element : tag.getList("blocks", NbtElement.COMPOUND_TYPE)) {
                    var block = (net.minecraft.nbt.NbtCompound) element;
                    String name = palette.getCompound(block.getInt("state")).getString("Name");
                    var pos = block.getList("pos", NbtElement.INT_TYPE);
                    if (name.equals("minecraft:trial_spawner")) {
                        spawners++;
                        assertEquals(1, pos.getInt(0));
                        assertEquals(1, pos.getInt(1));
                        assertEquals(1, pos.getInt(2));
                        assertFalse(block.getCompound("nbt").getCompound("normal_config")
                            .getList("spawn_potentials", NbtElement.COMPOUND_TYPE).isEmpty());
                    } else if (name.equals("minecraft:jigsaw")) {
                        jigsaws++;
                        assertEquals("minecraft:spawner_connector", block.getCompound("nbt").getString("name"));
                        assertEquals("minecraft:empty", block.getCompound("nbt").getString("pool"));
                        assertEquals(2, pos.getInt(1));
                        assertEquals(2, pos.getInt(2));
                    }
                }
                assertEquals(template.toString(), 1, spawners);
                assertEquals(template.toString(), 1, jigsaws);
            }
        }
    }

    @Test
    public void depthLimitFallbacksContainRealTerminalPieces() throws Exception {
        for (String group : new String[]{"all", "melee", "ranged", "slow_ranged", "small_melee", "breeze"}) {
            var primary = JsonParser.parseString(Files.readString(Path.of(
                "../../config/paxi/datapacks/gangs_fixes/data/minecraft/worldgen/template_pool/trial_chambers/spawner",
                group + ".json"))).getAsJsonObject();
            var fallback = JsonParser.parseString(Files.readString(ROOT.resolve(
                "gangstrials/worldgen/template_pool/spawner/fallback/" + group + ".json"))).getAsJsonObject();
            assertEquals("gangstrials:spawner/fallback/" + group, primary.get("fallback").getAsString());
            assertEquals("minecraft:empty", fallback.get("fallback").getAsString());
            assertFalse(fallback.getAsJsonArray("elements").isEmpty());
            assertEquals(primary.get("elements"), fallback.get("elements"));
        }
    }
}
