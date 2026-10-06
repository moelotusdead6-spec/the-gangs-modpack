package com.elysium.goldclaim;

import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.util.math.BlockPos;
import org.junit.Test;
import static org.junit.Assert.*;

public class RswEntryLocatorTest {
    @Test
    public void safeOriginIsUsedWithoutSearchingOrChangingTerrain() {
        AtomicInteger calls = new AtomicInteger();
        assertEquals(new BlockPos(0, 70, 0), RswEntryLocator.nearestSafe(256, (x, z) -> {
            calls.incrementAndGet();
            return new BlockPos(x, 70, z);
        }));
        assertEquals(1, calls.get());
    }

    @Test
    public void findsEuclideanNearestGroundRatherThanTheFirstSquareRingCandidate() {
        assertEquals(new BlockPos(2, 80, 0), RswEntryLocator.nearestSafe(20, (x, z) ->
            (x == -2 && z == -2) || (x == 2 && z == 0)
                ? new BlockPos(x, 80, z) : null));
    }

    @Test
    public void closerCardinalGroundWinsOverAnEarlierRingCorner() {
        assertEquals(new BlockPos(4, 80, 0), RswEntryLocator.nearestSafe(20, (x, z) ->
            (x == -3 && z == -3) || (x == 4 && z == 0)
                ? new BlockPos(x, 80, z) : null));
    }

    @Test
    public void unsafeSearchIsBoundedAndBadConfigurationIsExplicit() {
        AtomicInteger calls = new AtomicInteger();
        assertNull(RswEntryLocator.nearestSafe(2, (x, z) -> {
            calls.incrementAndGet();
            return null;
        }));
        assertEquals(13, calls.get());
        assertThrows(IllegalArgumentException.class, () -> RswEntryLocator.nearestSafe(-1, (x, z) -> null));
        assertThrows(IllegalArgumentException.class, () -> RswEntryLocator.nearestSafe(513, (x, z) -> null));
    }
}
