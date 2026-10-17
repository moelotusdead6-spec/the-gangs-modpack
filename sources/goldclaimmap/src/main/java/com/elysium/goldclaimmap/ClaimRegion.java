package com.elysium.goldclaimmap;

import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

/** A claim rectangle in block coordinates; both min and max bounds are inclusive. */
public record ClaimRegion(RegistryKey<World> dimension, int minX, int maxX, int minZ, int maxZ, boolean admin, String ownerName) {
}
