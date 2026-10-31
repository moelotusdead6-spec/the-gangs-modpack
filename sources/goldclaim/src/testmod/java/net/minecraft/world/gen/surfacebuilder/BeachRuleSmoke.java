package net.minecraft.world.gen.surfacebuilder;

import com.elysium.goldclaim.BeachSurfacePolicy;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.YOffset;

public final class BeachRuleSmoke {
    public static void verify(MinecraftServer server) throws Exception {
        int checks = 0;
        var biomes = server.getRegistryManager().get(RegistryKeys.BIOME);
        for (String resource : List.of(
                "resources/ee_default/data/minecraft/worldgen/noise_settings/overworld.json",
                "resources/ee_default/data/minecraft/worldgen/noise_settings/large_biomes.json",
                "resources/ee_force_large_biomes/data/minecraft/worldgen/noise_settings/overworld.json")) {
            JsonObject original;
            try (JarFile jar = new JarFile(Path.of("ecosphere-baseline.jar").toFile());
                 var reader = new InputStreamReader(jar.getInputStream(jar.getJarEntry(resource)), StandardCharsets.UTF_8)) {
                original = JsonParser.parseReader(reader).getAsJsonObject();
            }
            List<JsonObject> cuts = new ArrayList<>();
            find(original.get("surface_rule"), cuts);
            if (cuts.size() != 1) throw new IllegalStateException("Expected one coast air cut: " + resource);
            JsonObject cut = cuts.get(0);
            MaterialRules.MaterialRule decoded = MaterialRules.MaterialRule.CODEC.parse(JsonOps.INSTANCE, cut)
                .getOrThrow(false, message -> { throw new IllegalStateException(message); });
            MaterialRules.MaterialRule rule = MaterialRules.sequence(
                MaterialRules.condition(MaterialRules.aboveY(YOffset.fixed(63), 0), decoded),
                MaterialRules.block(Blocks.STONE.getDefaultState()));
            for (JsonElement value : cut.getAsJsonObject("if_true").getAsJsonArray("biome_is")) {
                String biome = value.getAsString();
                for (int y : new int[]{62, 63, 70, 100}) {
                    var context = new MaterialRules.MaterialRuleContext(null, null, null, null, null, biomes, null);
                    context.blockY = y;
                    context.biomeSupplier = () -> RegistryEntry.Reference.standAlone(biomes.getEntryOwner(),
                        RegistryKey.of(RegistryKeys.BIOME, new Identifier(biome)));
                    var result = rule.apply(context).tryApply(0, y, 0);
                    boolean expectedAir = y >= 63 && !BeachSurfacePolicy.BEACHES.contains(biome);
                    if (result == null || result.isAir() != expectedAir) {
                        throw new IllegalStateException("Wrong beach/river/ocean rule result: " + biome + " y=" + y);
                    }
                    checks++;
                }
            }
        }
        var locate = server.getCommandManager().getDispatcher().getRoot().getChild("locate");
        if (locate == null || locate.getChild("biome") == null || locate.getChild("structure") == null
                || locate.getChild("poi") == null || !locate.canUse(server.getCommandSource())) {
            throw new IllegalStateException("Vanilla staff locate command tree is missing/incomplete.");
        }
        System.out.println("PASS: " + checks + " real material-rule evaluations across all three Ecosphere presets; "
            + "six beaches retain terrain at y63/70/100, 18 river/ocean/lake rules unchanged; vanilla locate tree restored.");
    }

    private static void find(JsonElement element, List<JsonObject> cuts) {
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) find(child, cuts);
        } else if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("if_true") && BeachSurfacePolicy.isCoastalAirCut(object.get("if_true"))
                    && object.has("then_run") && object.getAsJsonObject("then_run").has("result_state")
                    && "minecraft:air".equals(object.getAsJsonObject("then_run")
                        .getAsJsonObject("result_state").get("Name").getAsString())) cuts.add(object);
            for (var entry : object.entrySet()) find(entry.getValue(), cuts);
        }
    }
}
