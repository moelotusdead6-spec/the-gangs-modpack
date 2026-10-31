package com.elysium.goldclaim;

import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

final class RswLanding {
    private RswLanding() {
    }

    static boolean isSafe(ServerWorld world, Vec3d target, double width, double height) {
        double half = width / 2.0;
        Box bounds = new Box(target.x - half, target.y, target.z - half,
            target.x + half, target.y + height, target.z + half);
        BlockPos feet = BlockPos.ofFloored(target);
        BlockPos head = BlockPos.ofFloored(target.x, bounds.maxY, target.z);
        BlockPos ground = feet.down();
        var floor = world.getBlockState(ground);
        var border = world.getWorldBorder();
        return world.isInBuildLimit(feet) && world.isInBuildLimit(head)
            && bounds.minX >= border.getBoundWest() && bounds.maxX <= border.getBoundEast()
            && bounds.minZ >= border.getBoundNorth() && bounds.maxZ <= border.getBoundSouth()
            && floor.isSolidBlock(world, ground) && floor.getFluidState().isEmpty()
            && !floor.isOf(Blocks.MAGMA_BLOCK) && !floor.isOf(Blocks.CAMPFIRE)
            && !floor.isOf(Blocks.SOUL_CAMPFIRE)
            && world.isSpaceEmpty(null, bounds) && !world.containsFluid(bounds);
    }
}
