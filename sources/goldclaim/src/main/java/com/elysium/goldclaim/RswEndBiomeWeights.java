package com.elysium.goldclaim;

import java.util.List;

final class RswEndBiomeWeights {
    static final float BETTER_END_SHARE = 0.875f;

    record Entry(String id, float weight) {}

    static float[] normalize(List<Entry> entries) {
        double better = 0;
        double other = 0;
        for (Entry entry : entries) {
            if (!Float.isFinite(entry.weight()) || entry.weight() < 0) {
                throw new IllegalArgumentException("Invalid RSW End biome weight: " + entry);
            }
            if (entry.id().startsWith("betterend:")) better += entry.weight();
            else other += entry.weight();
        }
        if (better <= 0 || other <= 0) {
            throw new IllegalStateException("RSW End requires both BetterEnd and other land biomes");
        }
        float[] weights = new float[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            boolean isBetter = entry.id().startsWith("betterend:");
            weights[i] = (float)(entry.weight() / (isBetter ? better : other)
                * (isBetter ? BETTER_END_SHARE : 1 - BETTER_END_SHARE));
        }
        return weights;
    }
}
