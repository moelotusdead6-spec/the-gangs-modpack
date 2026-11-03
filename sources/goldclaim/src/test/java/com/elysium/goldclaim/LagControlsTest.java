package com.elysium.goldclaim;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class LagControlsTest {
    @Test
    public void borderClampPullsOutsidePositionsJustInsideAndLeavesInsidePositionsAlone() {
        assertArrayEquals(new double[]{3497.0, 100.0}, BorderClamp.inside(5000, 100, -3500, 3500, -3500, 3500, 3), 1e-9);
        assertArrayEquals(new double[]{-3497.0, 3497.0}, BorderClamp.inside(-9000, 4000, -3500, 3500, -3500, 3500, 3), 1e-9);
        assertArrayEquals(new double[]{12.5, -40.0}, BorderClamp.inside(12.5, -40, -3500, 3500, -3500, 3500, 3), 1e-9);
        assertArrayEquals(new double[]{0.0, 0.0}, BorderClamp.inside(50, 50, -2, 2, -2, 2, 3), 1e-9);
    }

    @Test
    public void groundCleanupWarnsAtOneMinuteThirtySecondsAndCountsDownEveryFifteenMinutes() {
        assertEquals(900_000L, GroundCleanupSchedule.PERIOD_MS);
        GroundCleanupSchedule schedule = new GroundCleanupSchedule(0);
        for (int cycle = 0; cycle < 2; cycle++) {
            long start = cycle * GroundCleanupSchedule.PERIOD_MS;
            List<Integer> heard = new ArrayList<>();
            for (long t = start + 50; t <= start + GroundCleanupSchedule.PERIOD_MS; t += 50) {
                heard.addAll(schedule.poll(t));
            }
            assertEquals(List.of(60, 30, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, GroundCleanupSchedule.CLEAR), heard);
        }
    }

    @Test
    public void groundCleanupStallAnnouncesOnlyLatestWarningAndStillClears() {
        GroundCleanupSchedule schedule = new GroundCleanupSchedule(0);
        assertEquals(List.of(), schedule.poll(300_000));
        assertEquals(List.of(), schedule.poll(600_000));
        assertEquals(List.of(5), schedule.poll(895_000));
        assertEquals(List.of(GroundCleanupSchedule.CLEAR), schedule.poll(910_000));
        assertEquals(List.of(), schedule.poll(910_050));
        assertEquals(List.of(), schedule.poll(1_210_000));
        assertEquals(List.of(GroundCleanupSchedule.CLEAR), schedule.poll(1_810_000));
    }

    @Test
    public void mobCapsAreCutAboutTwelveAndAHalfPercent() {
        int[] vanilla = {70, 10, 15, 5, 5, 5, 20};
        int[] expected = {61, 9, 13, 4, 4, 4, 17};
        int before = 0, after = 0;
        for (int i = 0; i < vanilla.length; i++) {
            assertEquals(expected[i], MobCapPolicy.capacity(vanilla[i]));
            before += vanilla[i];
            after += expected[i];
        }
        assertEquals(-1, MobCapPolicy.capacity(-1));
        double cut = 1 - after / (double)before;
        assertTrue(cut >= 0.10 && cut <= 0.15);
    }

    @Test
    public void rswPregeneratesCentralSixtyPercentOfSevenThousandBorderArea() {
        assertEquals(7000.0, GoldClaimMod.RSW_OVERWORLD_BORDER, 0);
        assertEquals(2711, RswPregenerator.PREGEN_RADIUS);
        double share = Math.pow(2.0 * RswPregenerator.PREGEN_RADIUS, 2) / Math.pow(GoldClaimMod.RSW_OVERWORLD_BORDER, 2);
        assertEquals(0.6, share, 0.001);
    }
}
