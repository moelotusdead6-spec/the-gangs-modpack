package com.elysium.goldclaim;

import java.util.function.Supplier;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.Entity;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.TypedActionResult;

public final class HubTeleportPolicy {
    private static final ThreadLocal<ServerPlayerEntity> ITEM_USER = new ThreadLocal<>();

    private HubTeleportPolicy() {}

    public static boolean deny(Entity entity) {
        if (!(entity instanceof ServerPlayerEntity player) || !GoldClaimMod.isHubWorld(player.getServerWorld())) {
            return false;
        }
        player.sendMessage(Text.literal("Short-distance teleportation is disabled in the hub."), false);
        return true;
    }

    public static boolean denyItemTeleport(ServerPlayerEntity player, ServerWorld target) {
        return ITEM_USER.get() == player && target == player.getServerWorld() && deny(player);
    }

    public static ActionResult useItem(ServerPlayerEntity player, Supplier<ActionResult> action) {
        ServerPlayerEntity previous = ITEM_USER.get();
        ITEM_USER.set(player);
        try {
            return action.get();
        } finally {
            if (previous == null) ITEM_USER.remove();
            else ITEM_USER.set(previous);
        }
    }

    static void register() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            var stack = player.getStackInHand(hand);
            if ((stack.isOf(Items.ENDER_PEARL) || stack.isOf(Items.CHORUS_FRUIT)) && deny(player)) {
                return TypedActionResult.fail(stack);
            }
            return TypedActionResult.pass(stack);
        });
    }
}
