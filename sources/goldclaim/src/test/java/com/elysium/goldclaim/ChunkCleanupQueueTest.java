package com.elysium.goldclaim;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class ChunkCleanupQueueTest {
    @Test
    public void loadsAreDeferredDeduplicatedAndBoundedPerWorld() {
        var queue = new ChunkCleanupQueue<String, Integer>();
        var cleaned = new ArrayList<Integer>();
        for (int chunk = 0; chunk < 6; chunk++) {
            queue.enqueue("overworld", chunk);
            queue.enqueue("overworld", chunk);
        }
        queue.enqueue("rsw", 99);
        assertTrue(cleaned.isEmpty());
        queue.drain("overworld", 4, cleaned::add);
        assertEquals(List.of(0, 1, 2, 3), cleaned);
        queue.drain("overworld", 4, cleaned::add);
        assertEquals(List.of(0, 1, 2, 3, 4, 5), cleaned);
        queue.drain("overworld", 4, cleaned::add);
        assertEquals(6, cleaned.size());
        queue.drain("rsw", 4, cleaned::add);
        assertEquals(99, (int)cleaned.get(6));
    }

    @Test
    public void unloadAndShutdownCancelStaleChunkReferences() {
        var queue = new ChunkCleanupQueue<String, Integer>();
        var cleaned = new ArrayList<Integer>();
        queue.enqueue("world", 1);
        queue.enqueue("world", 2);
        queue.cancel("world", 1);
        queue.drain("world", 4, cleaned::add);
        assertEquals(List.of(2), cleaned);
        queue.enqueue("world", 3);
        queue.clear();
        queue.drain("world", 4, cleaned::add);
        assertEquals(List.of(2), cleaned);
    }

    @Test
    public void cleanupTriggeredLoadsWaitForAnotherTickAndUnloadCancelsTheBatch() {
        var queue = new ChunkCleanupQueue<String, Integer>();
        var cleaned = new ArrayList<Integer>();
        queue.enqueue("world", 1);
        queue.enqueue("world", 2);
        queue.drain("world", 4, chunk -> {
            cleaned.add(chunk);
            queue.enqueue("world", 3);
            queue.cancel("world", 2);
        });
        assertEquals(List.of(1), cleaned);
        queue.drain("world", 4, cleaned::add);
        assertEquals(List.of(1, 3), cleaned);
    }
}
