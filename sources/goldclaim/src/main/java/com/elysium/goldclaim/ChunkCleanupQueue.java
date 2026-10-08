package com.elysium.goldclaim;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.function.Consumer;

final class ChunkCleanupQueue<W, C> {
    private final Map<W, LinkedHashSet<C>> pending = new HashMap<>();

    void enqueue(W world, C chunk) {
        pending.computeIfAbsent(world, ignored -> new LinkedHashSet<>()).add(chunk);
    }

    void cancel(W world, C chunk) {
        LinkedHashSet<C> chunks = pending.get(world);
        if (chunks != null) {
            chunks.remove(chunk);
            if (chunks.isEmpty()) {
                pending.remove(world);
            }
        }
    }

    void drain(W world, int limit, Consumer<C> cleanup) {
        LinkedHashSet<C> chunks = pending.get(world);
        if (chunks == null) {
            return;
        }
        // Snapshot the batch so chunks loaded by cleanup wait until the next tick.
        var batch = chunks.stream().limit(limit).toList();
        for (C chunk : batch) {
            if (chunks.remove(chunk)) {
                cleanup.accept(chunk);
            }
        }
        if (chunks.isEmpty()) {
            pending.remove(world, chunks);
        }
    }

    void clear() {
        pending.clear();
    }
}
