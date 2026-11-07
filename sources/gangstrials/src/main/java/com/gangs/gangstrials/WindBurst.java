package com.gangs.gangstrials;

import net.minecraft.enchantment.ProtectionEnchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;

/** Vanilla 1.21 wind charge burst: radius 1.2 explosion knockback with a 1.22 multiplier, no damage. */
public final class WindBurst {
    private static final double POWER = 1.2;
    private static final double KNOCKBACK_MULTIPLIER = 1.22;

    private WindBurst() {
    }

    /** Applies knockback around the charge and returns whether its owner was pushed. */
    public static boolean apply(Entity charge) {
        World world = charge.getWorld();
        Vec3d center = charge.getPos();
        Entity owner = charge instanceof ProjectileEntity projectile ? projectile.getOwner() : null;
        double reach = POWER * 2.0;
        Box area = new Box(center.x - reach - 1, center.y - reach - 1, center.z - reach - 1,
            center.x + reach + 1, center.y + reach + 1, center.z + reach + 1);
        boolean ownerPushed = false;
        for (Entity entity : world.getOtherEntities(charge, area)) {
            if (entity.isImmuneToExplosion() || entity.isSpectator()) {
                continue;
            }
            if (entity instanceof PlayerEntity player && player.isCreative() && player.getAbilities().flying) {
                continue;
            }
            double distance = Math.sqrt(entity.squaredDistanceTo(center)) / reach;
            if (distance > 1.0) {
                continue;
            }
            double dx = entity.getX() - center.x;
            double dy = entity.getEyeY() - center.y;
            double dz = entity.getZ() - center.z;
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (length == 0.0) {
                continue;
            }
            double strength = (1.0 - distance) * Explosion.getExposure(center, entity) * KNOCKBACK_MULTIPLIER;
            if (entity instanceof LivingEntity living) {
                strength = ProtectionEnchantment.transformExplosionKnockback(living, strength);
            }
            if (strength <= 0.0) {
                continue;
            }
            entity.setVelocity(entity.getVelocity().add(dx / length * strength, dy / length * strength, dz / length * strength));
            entity.velocityModified = true;
            if (entity == owner) {
                ownerPushed = true;
            }
        }
        return ownerPushed;
    }
}
