package com.gangs.gangstrials.mixin.backport;

import com.drd.trickytrialsbackport.entity.OminousItemSpawner;
import com.drd.trickytrialsbackport.entity.projectile.windcharge.WindCharge;
import com.drd.trickytrialsbackport.registry.ModEntities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.SmallFireballEntity;
import net.minecraft.entity.projectile.SpectralArrowEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ThrowablePotionItem;
import net.minecraft.potion.PotionUtil;
import net.minecraft.potion.Potions;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla ominous item spawners fire potions, arrows, fire and wind charges down instead of dropping them as items. */
@Mixin(value = OminousItemSpawner.class, remap = false)
public abstract class OminousItemSpawnerMixin {
    @Unique
    private static final Identifier WIND_CHARGE_ID = new Identifier("minecraft", "wind_charge");

    @Shadow
    public abstract ItemStack getItem();

    @Shadow
    private void setItem(ItemStack stack) {
        throw new AssertionError();
    }

    @Inject(method = "spawnItem", at = @At("HEAD"), cancellable = true)
    private void gangstrials$fireProjectile(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        World world = self.getWorld();
        ItemStack stack = this.getItem();
        if (stack.isEmpty()) {
            return;
        }
        Entity projectile = gangstrials$createProjectile(world, self, stack);
        if (projectile == null) {
            return;
        }
        world.spawnEntity(projectile);
        world.syncWorldEvent(3021, self.getBlockPos(), 1);
        world.emitGameEvent(projectile, GameEvent.PROJECTILE_SHOOT, self.getPos());
        this.setItem(ItemStack.EMPTY);
        ci.cancel();
    }

    @Unique
    private static Entity gangstrials$createProjectile(World world, Entity at, ItemStack stack) {
        double x = at.getX();
        double y = at.getY();
        double z = at.getZ();
        if (stack.getItem() instanceof ThrowablePotionItem) {
            PotionEntity potion = new PotionEntity(world, x, y, z);
            potion.setItem(stack.copy());
            gangstrials$shootDown(potion, 1.375F, 3.0F);
            return potion;
        }
        if (stack.isOf(Items.ARROW) || stack.isOf(Items.TIPPED_ARROW)) {
            ArrowEntity arrow = new ArrowEntity(world, x, y, z);
            if (PotionUtil.getPotion(stack) != Potions.EMPTY || !PotionUtil.getCustomPotionEffects(stack).isEmpty()) {
                ItemStack tipped = new ItemStack(Items.TIPPED_ARROW);
                tipped.setNbt(stack.getNbt() == null ? null : stack.getNbt().copy());
                arrow.initFromStack(tipped);
            } else {
                arrow.initFromStack(stack);
            }
            arrow.pickupType = PersistentProjectileEntity.PickupPermission.ALLOWED;
            gangstrials$shootDown(arrow, 1.1F, 6.0F);
            return arrow;
        }
        if (stack.isOf(Items.SPECTRAL_ARROW)) {
            SpectralArrowEntity arrow = new SpectralArrowEntity(world, x, y, z);
            arrow.pickupType = PersistentProjectileEntity.PickupPermission.ALLOWED;
            gangstrials$shootDown(arrow, 1.1F, 6.0F);
            return arrow;
        }
        if (stack.isOf(Items.FIRE_CHARGE)) {
            double dx = world.random.nextGaussian() * 0.05;
            double dz = world.random.nextGaussian() * 0.05;
            SmallFireballEntity fireball = new SmallFireballEntity(world, x, y, z, dx, -1.0, dz);
            fireball.setItem(stack.copyWithCount(1));
            return fireball;
        }
        if (WIND_CHARGE_ID.equals(Registries.ITEM.getId(stack.getItem()))) {
            WindCharge charge = new WindCharge(ModEntities.WIND_CHARGE.get(), world);
            charge.refreshPositionAndAngles(x, y, z, 0F, 0F);
            gangstrials$shootDown(charge, 1.1F, 6.0F);
            return charge;
        }
        return null;
    }

    @Unique
    private static void gangstrials$shootDown(ProjectileEntity projectile, float power, float uncertainty) {
        projectile.setVelocity(0.0, -1.0, 0.0, power, uncertainty);
    }
}
