package com.elysium.goldclaim;

import org.junit.Test;
import static org.junit.Assert.*;

public class RswMapSyncTest {
    @Test
    public void encodesOnlyProtocolAndCompletedGeneration() {
        var packet = RswMapSync.encode(123);
        try {
            assertEquals(1, packet.readVarInt());
            assertEquals(123, packet.readLong());
            assertFalse(packet.isReadable());
        } finally { packet.release(); }
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNegativeGeneration() {
        RswMapSync.encode(-1);
    }
}
