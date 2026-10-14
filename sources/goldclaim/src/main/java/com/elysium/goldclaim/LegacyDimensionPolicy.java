package com.elysium.goldclaim;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

public final class LegacyDimensionPolicy {
    public static final String MESSAGE =
        "The original Nether and End are closed. Use the RSW dimensions instead.";

    private LegacyDimensionPolicy() {}

    public static boolean isClosed(String dimension) {
        return "minecraft:the_nether".equals(dimension) || "minecraft:the_end".equals(dimension);
    }

    public static boolean isClosed(ServerWorld world) {
        return isClosed(world.getRegistryKey().getValue().toString());
    }

    public static boolean deny(ServerPlayerEntity player, ServerWorld destination) {
        if (!isClosed(destination)) return false;
        player.sendMessage(Text.literal(MESSAGE), false);
        return true;
    }
}
