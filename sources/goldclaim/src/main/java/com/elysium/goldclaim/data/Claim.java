/*
 * Decompiled with CFR 0.152.
 */
package com.elysium.goldclaim.data;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class Claim {
    public String ownerUuid;
    public String ownerName;
    public boolean adminClaim;
    public String dimension;
    public int minX;
    public int maxX;
    public int minZ;
    public int maxZ;
    public Set<String> trustedPlayers = new HashSet<String>();
    public Set<String> trustedPlayerNames = new HashSet<String>();

    public Claim() {
    }

    public Claim(UUID ownerUuid, String dimension, int minX, int maxX, int minZ, int maxZ) {
        this.ownerUuid = ownerUuid.toString();
        this.ownerName = null;
        this.adminClaim = false;
        this.dimension = dimension;
        this.minX = minX;
        this.maxX = maxX;
        this.minZ = minZ;
        this.maxZ = maxZ;
    }

    public Claim(String ownerUuid, boolean adminClaim, String dimension, int minX, int maxX, int minZ, int maxZ) {
        this.ownerUuid = ownerUuid;
        this.ownerName = null;
        this.adminClaim = adminClaim;
        this.dimension = dimension;
        this.minX = minX;
        this.maxX = maxX;
        this.minZ = minZ;
        this.maxZ = maxZ;
    }

    public Claim(UUID ownerUuid, String ownerName, String dimension, int minX, int maxX, int minZ, int maxZ) {
        this.ownerUuid = ownerUuid.toString();
        this.ownerName = ownerName;
        this.adminClaim = false;
        this.dimension = dimension;
        this.minX = minX;
        this.maxX = maxX;
        this.minZ = minZ;
        this.maxZ = maxZ;
    }

    public boolean contains(int x, int z) {
        return x >= this.minX && x <= this.maxX && z >= this.minZ && z <= this.maxZ;
    }

    public boolean intersects(int otherMinX, int otherMaxX, int otherMinZ, int otherMaxZ) {
        return otherMaxX >= this.minX && otherMinX <= this.maxX && otherMaxZ >= this.minZ && otherMinZ <= this.maxZ;
    }

    public boolean isOwner(UUID playerUuid) {
        return !this.adminClaim && this.ownerUuid.equals(playerUuid.toString());
    }

    public boolean isTrusted(UUID playerUuid) {
        return this.trustedPlayers.contains(playerUuid.toString());
    }

    public boolean isTrustedName(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return false;
        }
        return this.trustedPlayerNames.contains(playerName.toLowerCase(Locale.ROOT));
    }

    public void addTrusted(UUID playerUuid) {
        this.trustedPlayers.add(playerUuid.toString());
    }

    public void addTrustedName(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return;
        }
        this.trustedPlayerNames.add(playerName.toLowerCase(Locale.ROOT));
    }

    public void removeTrusted(UUID playerUuid) {
        this.trustedPlayers.remove(playerUuid.toString());
    }

    public void removeTrustedName(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return;
        }
        this.trustedPlayerNames.remove(playerName.toLowerCase(Locale.ROOT));
    }

    public int width() {
        return this.maxX - this.minX + 1;
    }

    public int depth() {
        return this.maxZ - this.minZ + 1;
    }

    public int area() {
        return this.width() * this.depth();
    }
}
