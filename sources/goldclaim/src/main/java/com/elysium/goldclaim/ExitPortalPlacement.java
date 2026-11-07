package com.elysium.goldclaim;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.WallTorchBlock;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.Chunk;

import java.util.Arrays;

/** Keeps the End exit fountain on the island surface instead of inside the terrain. */
public final class ExitPortalPlacement {
    static final int RADIUS = 6;
    static final int BURIED_MARGIN = 2;
    static final int STRAY_RADIUS = 8;

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

    /** Removes every exit-portal block in the central column, e.g. BetterEnd's buried shared-position portal. */
    public static void clearStrayPortals(ServerWorld world, BlockPos center) {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int dx = -STRAY_RADIUS; dx <= STRAY_RADIUS; dx++) {
            for (int dz = -STRAY_RADIUS; dz <= STRAY_RADIUS; dz++) {
                for (int y = world.getBottomY(); y < world.getTopY(); y++) {
                    pos.set(center.getX() + dx, y, center.getZ() + dz);
                    if (world.getBlockState(pos).isOf(Blocks.END_PORTAL)) {
                        world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                    }
                }
            }
        }
    }

    /** Places the vanilla exit fountain, matching EndPortalFeature so vanilla pattern scans still recognise it. */
    public static void placeFountain(ServerWorld world, BlockPos portal, boolean open) {
        for (BlockPos pos : BlockPos.iterate(portal.add(-4, -1, -4), portal.add(4, 32, 4))) {
            boolean inner = pos.isWithinDistance(portal, 2.5);
            if (!inner && !pos.isWithinDistance(portal, 3.5)) continue;
            BlockState state;
            if (pos.getY() < portal.getY()) {
                state = inner ? Blocks.BEDROCK.getDefaultState() : Blocks.END_STONE.getDefaultState();
            } else if (pos.getY() > portal.getY()) {
                state = Blocks.AIR.getDefaultState();
            } else if (!inner) {
                state = Blocks.BEDROCK.getDefaultState();
            } else {
                state = open ? Blocks.END_PORTAL.getDefaultState() : Blocks.AIR.getDefaultState();
            }
            world.setBlockState(pos, state, Block.NOTIFY_ALL);
        }
        for (int i = 0; i < 4; i++) {
            world.setBlockState(portal.up(i), Blocks.BEDROCK.getDefaultState(), Block.NOTIFY_ALL);
        }
        BlockPos torches = portal.up(2);
        for (Direction direction : Direction.Type.HORIZONTAL) {
            world.setBlockState(torches.offset(direction),
                Blocks.WALL_TORCH.getDefaultState().with(WallTorchBlock.FACING, direction), Block.NOTIFY_ALL);
        }
    }

    /** True when a vanilla fountain's bedrock pillar and rim stand at {@code portal}. */
    public static boolean hasFountain(ServerWorld world, BlockPos portal) {
        if (!world.getBlockState(portal).isOf(Blocks.BEDROCK) || !world.getBlockState(portal.up(3)).isOf(Blocks.BEDROCK)) {
            return false;
        }
        for (Direction direction : Direction.Type.HORIZONTAL) {
            if (!world.getBlockState(portal.offset(direction, 3)).isOf(Blocks.BEDROCK)) return false;
        }
        return true;
    }
}
