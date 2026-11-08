package com.elysium.goldclaimmap;

import java.nio.file.Path;
import java.util.Set;

public final class RswMapPaths {
    private static final String DIRECTORY = "gangs-rsw-map";
    private static final Set<String> DIMENSIONS = Set.of("multiworld:rsw", "multiworld:rsw_nether", "multiworld:rsw_the_end");
    private static volatile Long generation;

    private RswMapPaths() {}

    public static void setGeneration(long value) {
        if (value < 0) throw new IllegalArgumentException("Negative RSW map generation");
        generation = value;
    }

    public static void disconnect() {
        generation = null;
    }

    public static Path dimensionPath(Path original, String dimension) {
        if (!DIMENSIONS.contains(dimension)) return original;
        Path parent = original.getParent();
        return parent == null ? original : worldRoot(parent, dimension).resolve(original.getFileName());
    }

    public static Path worldRoot(Path original, String dimension) {
        if (!DIMENSIONS.contains(dimension)) return original;
        // Old region objects and background writes must remain pinned to their original epoch.
        Path parent = original.getParent();
        if (parent != null && parent.getFileName() != null && DIRECTORY.equals(parent.getFileName().toString())
                && original.getFileName().toString().matches("generation-[0-9]+")) return original;
        Long current = generation;
        return current == null ? original : original.resolve(DIRECTORY).resolve("generation-" + current);
    }
}
