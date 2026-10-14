package com.elysium.goldclaim;

import java.util.Optional;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Resolves where players arrive in RSW when leaving the RSW End. */
public final class RswEndExit {
    private RswEndExit() {}

    static boolean isRswSpawn(String spawnDimension, BlockPos spawnPos) {
        return spawnPos != null && GoldClaimMod.RSW_DIMENSION.equals(spawnDimension);
    }

    /** Never returns a position below the world floor or outside the border; anchors are not charged. */
    static boolean usableLanding(double y, int bottomY, int topY) {
        return y > bottomY && y < topY;
    }

    static Vec3d validSpawn(ServerPlayerEntity player, ServerWorld rsw) {
        BlockPos pos = player.getSpawnPointPosition();
        if (!isRswSpawn(player.getSpawnPointDimension().getValue().toString(), pos)) return null;
        if (!rsw.getWorldBorder().contains(pos) || !rsw.isInBuildLimit(pos)) return null;
        Optional<Vec3d> found = PlayerEntity.findRespawnPosition(rsw, pos, player.getSpawnAngle(),
            player.isSpawnForced(), true);
        if (found.isEmpty()) return null;
        Vec3d landing = found.get();
        if (!usableLanding(landing.y, rsw.getBottomY(), rsw.getTopY())
                || !rsw.getWorldBorder().contains(BlockPos.ofFloored(landing))) {
            return null;
        }
        return landing;
    }
}
