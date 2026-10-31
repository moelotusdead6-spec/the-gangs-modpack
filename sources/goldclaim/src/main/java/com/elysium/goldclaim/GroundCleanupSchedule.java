package com.elysium.goldclaim;

import java.util.ArrayList;
import java.util.List;

/** Wall-clock schedule for the dropped-item cleanup; immune to tick lag drift. */
final class GroundCleanupSchedule {
    static final long PERIOD_MS = 15 * 60 * 1000L;
    static final int CLEAR = 0;
    private static final int[] WARNINGS = {60, 30, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1};

    private long clearAt;
    private int nextWarning;

    GroundCleanupSchedule(long nowMs) {
        this.clearAt = nowMs + PERIOD_MS;
    }

    /** Returns seconds-remaining announcements due now; {@link #CLEAR} means clear immediately. */
    List<Integer> poll(long nowMs) {
        List<Integer> due = new ArrayList<>();
        long remaining = this.clearAt - nowMs;
        // Announce only the latest crossed warning so a long stall does not spam the countdown.
        int latest = -1;
        while (this.nextWarning < WARNINGS.length && remaining <= WARNINGS[this.nextWarning] * 1000L) {
            latest = WARNINGS[this.nextWarning++];
        }
        if (remaining <= 0) {
            due.add(CLEAR);
            this.clearAt = nowMs + PERIOD_MS;
            this.nextWarning = 0;
        } else if (latest > 0) {
            due.add(latest);
        }
        return due;
    }
}
