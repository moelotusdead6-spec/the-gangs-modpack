package com.elysium.goldclaim;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class RswEndBiomeWeightsTest {
    @Test
    public void normalizesNamespacesAndPreservesWithinGroupRatios() {
        var weights = RswEndBiomeWeights.normalize(List.of(
            new RswEndBiomeWeights.Entry("betterend:forest", 3),
            new RswEndBiomeWeights.Entry("betterend:lake", 1),
            new RswEndBiomeWeights.Entry("minecraft:end_highlands", 100),
            new RswEndBiomeWeights.Entry("nullscape:crystal_peaks", 100)));
        assertEquals(0.875, weights[0] + weights[1], 0.000001);
        assertEquals(0.125, weights[2] + weights[3], 0.000001);
        assertEquals(3, weights[0] / weights[1], 0.000001);
        assertEquals(weights[2], weights[3], 0);
    }

    @Test(expected = IllegalStateException.class)
    public void missingBetterEndCannotSilentlyFallBack() {
        RswEndBiomeWeights.normalize(List.of(new RswEndBiomeWeights.Entry("minecraft:end_highlands", 1)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidWeights() {
        RswEndBiomeWeights.normalize(List.of(new RswEndBiomeWeights.Entry("betterend:forest", Float.NaN)));
    }
}
