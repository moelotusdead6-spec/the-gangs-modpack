package com.elysium.goldclaim;

import java.util.Set;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.RangedWeaponItem;
import net.minecraft.item.TridentItem;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.Identifier;

public final class HubProjectilePolicy {
    private static final Set<Identifier> EXTRA_PROJECTILES = Set.of(
        new Identifier("minecraft", "eye_of_ender"),
        new Identifier("alexscaves", "wave"),
        new Identifier("alexscaves", "desolate_dagger"),
        new Identifier("alexscaves", "dinosaur_spirit"),
        new Identifier("alexscaves", "spinning_peppermint"),
        new Identifier("alexscaves", "sugar_staff_hex"),
        new Identifier("alexscaves", "thrown_waste_drum"),
        new Identifier("alexscaves", "magnetic_weapon"));

    private HubProjectilePolicy() {}

    static boolean isProjectile(Entity entity) {
        return entity instanceof ProjectileEntity || EXTRA_PROJECTILES.contains(Registries.ENTITY_TYPE.getId(entity.getType()));
    }

    public static boolean remove(Entity entity, ServerWorld world) {
        if (!GoldClaimMod.isHubWorld(world) || !isProjectile(entity)) {
            return false;
        }
        entity.discard();
        return true;
    }

    private static boolean launchesProjectile(ItemStack stack) {
        return stack.getItem() instanceof RangedWeaponItem || stack.getItem() instanceof TridentItem
            || stack.isOf(Items.SNOWBALL) || stack.isOf(Items.EGG) || stack.isOf(Items.ENDER_PEARL)
            || stack.isOf(Items.EXPERIENCE_BOTTLE) || stack.isOf(Items.SPLASH_POTION)
            || stack.isOf(Items.LINGERING_POTION) || stack.isOf(Items.FIREWORK_ROCKET)
            || stack.isOf(Items.ENDER_EYE) || stack.isOf(Items.FISHING_ROD);
    }

    static void register() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (world instanceof ServerWorld serverWorld && GoldClaimMod.isHubWorld(serverWorld)
                    && launchesProjectile(stack)) {
                player.sendMessage(Text.literal("Projectiles are disabled in the hub."), false);
                return TypedActionResult.fail(stack);
            }
            return TypedActionResult.pass(stack);
        });
    }
}
