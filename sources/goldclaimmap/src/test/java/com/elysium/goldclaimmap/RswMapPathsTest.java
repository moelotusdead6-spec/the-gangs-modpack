package com.elysium.goldclaimmap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

public class RswMapPathsTest {
    @After
    public void disconnect() {
        RswMapPaths.disconnect();
    }

    @Test
    public void rotatesAllThreeRswMapsButNoOthers() {
        Path root = Path.of("journeymap", "data", "mp", "server");
        RswMapPaths.setGeneration(7);
        for (String dimension : List.of("multiworld:rsw", "multiworld:rsw_nether", "multiworld:rsw_the_end")) {
            Path original = root.resolve(dimension.replace(':', '~'));
            Path expected = root.resolve("gangs-rsw-map").resolve("generation-7").resolve(original.getFileName());
            assertEquals(expected, RswMapPaths.dimensionPath(original, dimension));
            RswMapPaths.setGeneration(8);
            assertNotEquals(expected, RswMapPaths.dimensionPath(original, dimension));
            RswMapPaths.setGeneration(7);
        }
        for (String dimension : List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end",
                "multiworld:pvp", "other:rsw", "multiworld:rsw_backup")) {
            Path original = root.resolve(dimension.replace(':', '~'));
            assertSame(original, RswMapPaths.dimensionPath(original, dimension));
        }
    }

    @Test
    public void pinsBackgroundRegionWritesAndDoesNotNestEpochs() {
        Path root = Path.of("server");
        RswMapPaths.setGeneration(0);
        Path epoch = RswMapPaths.worldRoot(root, "multiworld:rsw");
        Path oldRegion = epoch.resolve("multiworld~rsw");
        RswMapPaths.setGeneration(1);
        assertEquals(epoch, RswMapPaths.worldRoot(epoch, "multiworld:rsw"));
        assertEquals(oldRegion, RswMapPaths.dimensionPath(oldRegion, "multiworld:rsw"));
        RswMapPaths.disconnect();
        assertEquals(epoch, RswMapPaths.worldRoot(epoch, "multiworld:rsw"));
        assertEquals(root, RswMapPaths.worldRoot(root, "multiworld:rsw"));
    }

    @Test
    public void startsBlankWithoutChangingExistingMapOrWaypointFiles() throws Exception {
        Path root = Files.createTempDirectory("rsw-map-path-test");
        try {
            Path legacy = Files.createDirectory(root.resolve("multiworld~rsw"));
            Path tile = Files.writeString(legacy.resolve("old-map.png"), "old map");
            Path wild = Files.writeString(root.resolve("wild-map.png"), "wild");
            Path waypoints = Files.writeString(root.resolve("waypoints.json"), "waypoints");
            RswMapPaths.setGeneration(0);
            Path fresh = RswMapPaths.dimensionPath(legacy, "multiworld:rsw");
            assertFalse(Files.exists(fresh));
            assertEquals("old map", Files.readString(tile));
            assertEquals("wild", Files.readString(wild));
            assertEquals("waypoints", Files.readString(waypoints));
        } finally {
            List<Path> paths;
            try (var files = Files.walk(root)) { paths = files.sorted(java.util.Comparator.reverseOrder()).toList(); }
            for (Path path : paths) Files.delete(path);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNegativeEpoch() {
        RswMapPaths.setGeneration(-1);
    }
}
