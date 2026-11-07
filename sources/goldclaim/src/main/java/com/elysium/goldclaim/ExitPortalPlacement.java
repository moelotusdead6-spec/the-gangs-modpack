package com.elysium.goldclaim;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;

import java.util.Arrays;

/** Keeps the End exit fountain on the island surface instead of inside the terrain. */
public final class ExitPortalPlacement {
    static final int RADIUS = 6;
    static final int BURIED_MARGIN = 2;

    private ExitPortalPlacement() {}

    /** Y of the portal layer for a fountain centred on {@code center}, or {@code Integer.MIN_VALUE} if no ground. */
    public static int surfaceY(ServerWorld world, BlockPos center) {
        int[] tops = new int[(2 * RADIUS + 1) * (2 * RADIUS + 1)];
        int count = 0;
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                if (dx * dx + dz * dz > RADIUS * RADIUS) continue;
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                // getChunk forces the chunk to FULL status; heightmaps of unloaded chunks report the world bottom.
                Chunk chunk = world.getChunk(x >> 4, z >> 4);
                int top = Integer.MIN_VALUE;
                for (int y = world.getTopY() - 1; y >= world.getBottomY(); y--) {
                    pos.set(x, y, z);
                    if (isGround(chunk.getBlockState(pos), world, pos)) {
                        top = y;
                        break;
                    }
                }
                if (top != Integer.MIN_VALUE) tops[count++] = top;
            }
        }
        return median(Arrays.copyOf(tops, count));
    }

    static int median(int[] tops) {
        if (tops.length == 0) return Integer.MIN_VALUE;
        int[] sorted = tops.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

    public static boolean isBuried(int portalY, int surfaceY) {
        return surfaceY != Integer.MIN_VALUE && surfaceY >= portalY + BURIED_MARGIN;
    }

    public static boolean isBuried(ServerWorld world, BlockPos portal) {
        return isBuried(portal.getY(), surfaceY(world, portal));
    }

    private static boolean isGround(BlockState state, ServerWorld world, BlockPos pos) {
        if (state.isAir() || !state.getFluidState().isEmpty()) return false;
        // Fountain blocks are ignored so an existing fountain doesn't count as terrain.
        if (state.isOf(Blocks.BEDROCK) || state.isOf(Blocks.END_PORTAL) || state.isIn(BlockTags.LEAVES)) return false;
        return state.isSolidBlock(world, pos);
    }

    /** Removes portal blocks left at an abandoned (buried) fountain so only one exit remains. */
    public static void clearOldPortal(ServerWorld world, BlockPos portal) {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                pos.set(portal.getX() + dx, portal.getY(), portal.getZ() + dz);
                if (world.getBlockState(pos).isOf(Blocks.END_PORTAL)) {
                    world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
                }
            }
        }
    }
}
