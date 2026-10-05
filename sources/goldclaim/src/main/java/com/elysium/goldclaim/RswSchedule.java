package com.elysium.goldclaim;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;

final class RswSchedule {
    static final int[] WARNING_SECONDS = {900, 600, 300, 240, 180, 120, 60};
    private static final ZoneOffset EST = ZoneOffset.ofHours(-5);

    static long nextReset(long nowMs) {
        var now = Instant.ofEpochMilli(nowMs).atOffset(EST);
        var next = now.toLocalDate().atTime(LocalTime.of(5, 0)).atOffset(EST);
        if (!next.isAfter(now)) {
            next = next.plusDays(1);
        }
        return next.toInstant().toEpochMilli();
    }

    static long latestReset(long nowMs) {
        return nextReset(nowMs) - 86400000L;
    }
}