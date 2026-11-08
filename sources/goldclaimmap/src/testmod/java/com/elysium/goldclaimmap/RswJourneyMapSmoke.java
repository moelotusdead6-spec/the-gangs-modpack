package com.elysium.goldclaimmap;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

public final class RswJourneyMapSmoke implements ClientModInitializer {
    private boolean tested;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (this.tested || client.getOverlay() != null) return;
            this.tested = true;
            try {
                Path root = client.runDirectory.toPath().resolve("fixture-map");
                Files.createDirectories(root);
                var journeyMap = Class.forName("journeymap.client.JourneymapClient");
                journeyMap.getMethod("loadConfigProperties").invoke(journeyMap.getMethod("getInstance").invoke(null));
                var fileHandler = Class.forName("journeymap.client.io.FileHandler");
                var regionClass = Class.forName("journeymap.client.model.region.RegionCoord");
                var keyClass = Class.forName("journeymap.client.model.region.RegionImageSet$Key");
                var imageClass = Class.forName("journeymap.client.model.region.RegionImageSet");
                var getPath = fileHandler.getMethod("getDimPath", File.class, RegistryKey.class);
                var constructor = regionClass.getConstructor(File.class, int.class, int.class, RegistryKey.class);
                var createKey = keyClass.getMethod("from", regionClass);
                var wild = RegistryKey.of(RegistryKeys.WORLD, new Identifier("minecraft", "overworld"));
                Path wildBefore = (Path) getPath.invoke(null, root.toFile(), wild);
                RswMapPaths.setGeneration(40);
                for (String id : List.of("multiworld:rsw", "multiworld:rsw_nether", "multiworld:rsw_the_end")) {
                    RegistryKey<World> dimension = RegistryKey.of(RegistryKeys.WORLD, new Identifier(id));
                    Path first = (Path) getPath.invoke(null, root.toFile(), dimension);
                    require(first.startsWith(root.resolve("gangs-rsw-map").resolve("generation-40")),
                        "Actual JourneyMap path did not rotate: " + first);
                    Files.createDirectories(first);
                    Files.writeString(first.resolve("old-tile.png"), "old");
                    Object region = constructor.newInstance(root.toFile(), 0, 0, dimension);
                    Object key = createKey.invoke(null, region);
                    require(((File) regionClass.getField("worldDir").get(region)).toPath()
                        .equals(root.resolve("gangs-rsw-map").resolve("generation-40")), "Region world root is not pinned");
                    require(regionClass.getField("dimDir").get(region).equals(first), "Region path diverged from tile storage");
                    RswMapPaths.setGeneration(41);
                    Path next = (Path) getPath.invoke(null, root.toFile(), dimension);
                    require(!first.equals(next) && !Files.exists(next), "Reset did not select a blank map/cache directory");
                    Object freshRegion = constructor.newInstance(root.toFile(), 0, 0, dimension);
                    Object freshKey = createKey.invoke(null, freshRegion);
                    require(!key.equals(freshKey), "In-memory region-image cache reused the old generation");
                    Object oldImageSet = imageClass.getConstructor(keyClass).newInstance(key);
                    Object pinned = imageClass.getMethod("getRegionCoord").invoke(oldImageSet);
                    require(regionClass.getField("dimDir").get(pinned).equals(first),
                        "Old background image writes escaped into the new generation");
                    RswMapPaths.setGeneration(40);
                }
                require(getPath.invoke(null, root.toFile(), wild).equals(wildBefore), "Wild map storage changed");
                for (String id : List.of("minecraft:the_nether", "minecraft:the_end", "multiworld:pvp", "other:rsw")) {
                    var dimension = RegistryKey.of(RegistryKeys.WORLD, new Identifier(id));
                    RswMapPaths.disconnect();
                    Object before = getPath.invoke(null, root.toFile(), dimension);
                    RswMapPaths.setGeneration(42);
                    require(before.equals(getPath.invoke(null, root.toFile(), dimension)), "Non-RSW map changed: " + id);
                }
                List<Path> cleanup;
                try (var files = Files.walk(root)) { cleanup = files.sorted(java.util.Comparator.reverseOrder()).toList(); }
                for (Path path : cleanup) Files.delete(path);
                Files.writeString(client.runDirectory.toPath().resolve("rsw-map-smoke-passed.txt"),
                    "PASS: pinned JourneyMap FileHandler and RegionCoord mixins; all three RSW dimensions start blank; "
                    + "image cache keys separate epochs; old image writes stay pinned; wild/other maps unchanged.\n");
                client.scheduleStop();
            } catch (ReflectiveOperationException | java.io.IOException failure) {
                throw new IllegalStateException("JourneyMap RSW storage smoke failed", failure);
            }
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
