package com.elysium.goldclaim;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ExitPortalPlacementTest {
    @Test
    public void medianIgnoresOutliers() {
        assertEquals(64, ExitPortalPlacement.median(new int[] {64, 64, 63, 65, 120, 10, 64}));
        assertEquals(Integer.MIN_VALUE, ExitPortalPlacement.median(new int[0]));
    }

    @Test
    public void portalBelowSurfaceIsBuried() {
        assertTrue(ExitPortalPlacement.isBuried(40, 64));
        assertTrue(ExitPortalPlacement.isBuried(62, 64));
    }

    @Test
    public void portalOnSurfaceIsKept() {
        assertFalse(ExitPortalPlacement.isBuried(64, 64));
        assertFalse(ExitPortalPlacement.isBuried(64, 65));
        assertFalse(ExitPortalPlacement.isBuried(64, Integer.MIN_VALUE));
    }
}
