/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  net.minecraft.util.math.BlockPos
 */
package com.elysium.goldclaim.data;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.util.math.BlockPos;

public class SelectionManager {
    private final Map<UUID, BlockPos> firstCornerByPlayer = new ConcurrentHashMap<UUID, BlockPos>();
    private final Map<UUID, BlockPos> portalBaseByPlayer = new ConcurrentHashMap<UUID, BlockPos>();

    public Optional<BlockPos> getFirstCorner(UUID playerUuid) {
        return Optional.ofNullable(this.firstCornerByPlayer.get(playerUuid));
    }

    public void setFirstCorner(UUID playerUuid, BlockPos pos) {
        this.firstCornerByPlayer.put(playerUuid, pos.toImmutable());
    }

    public void clear(UUID playerUuid) {
        this.firstCornerByPlayer.remove(playerUuid);
        this.portalBaseByPlayer.remove(playerUuid);
    }

    public Optional<BlockPos> getPortalBase(UUID playerUuid) {
        return Optional.ofNullable(this.portalBaseByPlayer.get(playerUuid));
    }

    public void setPortalBase(UUID playerUuid, BlockPos pos) {
        this.portalBaseByPlayer.put(playerUuid, pos.toImmutable());
    }

    public void clearPortalBase(UUID playerUuid) {
        this.portalBaseByPlayer.remove(playerUuid);
    }
}
