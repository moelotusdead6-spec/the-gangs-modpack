package com.elysium.goldclaim;

import java.util.function.BiFunction;
import java.util.Comparator;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Set;
import net.minecraft.util.math.BlockPos;

final class RswEntryLocator {
    private RswEntryLocator() {}

    static BlockPos nearestSafe(int radius, BiFunction<Integer, Integer, BlockPos> candidate) {
        Search search = new Search(radius);
        BlockPos column;
        while ((column = search.next()) != null) {
            BlockPos found = candidate.apply(column.getX(), column.getZ());
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    static final class Search {
        private final long limit;
        private final Set<Long> visited = new HashSet<>();
        private final PriorityQueue<BlockPos> frontier = new PriorityQueue<>(
            Comparator.comparingLong(Search::distance).thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));

        Search(int radius) {
            if (radius < 0 || radius > 512) {
                throw new IllegalArgumentException("RSW entry search radius must be between 0 and 512 blocks.");
            }
            this.limit = (long)radius * radius;
            this.add(0, 0);
        }

        BlockPos next() {
            BlockPos next = this.frontier.poll();
            if (next != null) {
                this.add(next.getX() - 1, next.getZ());
                this.add(next.getX() + 1, next.getZ());
                this.add(next.getX(), next.getZ() - 1);
                this.add(next.getX(), next.getZ() + 1);
            }
            return next;
        }

        private static long distance(BlockPos pos) {
            return (long)pos.getX() * pos.getX() + (long)pos.getZ() * pos.getZ();
        }

        private void add(int x, int z) {
            BlockPos pos = new BlockPos(x, 0, z);
            if (distance(pos) <= this.limit && this.visited.add(pos.asLong())) {
                this.frontier.add(pos);
            }
        }
    }
}
