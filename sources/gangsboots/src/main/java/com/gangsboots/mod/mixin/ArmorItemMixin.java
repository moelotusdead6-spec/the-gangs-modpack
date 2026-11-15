package com.gangsboots.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/**
 * Resolves the JVM IncompatibleClassChangeError caused by two conflicting
 * interface default methods on armor items:
 *   - com.github.alexmodguy.alexscaves.forge_shim.common.IForgeItem#onArmorTick
 *   - net.p3pp3rf1y.sophisticatedcore.extensions.item.SophisticatedItem#onArmorTick
 *
 * Adding a concrete PUBLIC method directly on the ArmorItem class makes it the
 * maximally-specific implementation, so the VM no longer needs to pick between
 * the two interface defaults. The body intentionally delegates to neither side;
 * armor-tick side effects from those two mods are not required for this pack.
 */
@Mixin(ArmorItem.class)
public abstract class ArmorItemMixin {
    public void onArmorTick(ItemStack stack, World world, PlayerEntity player) {
        // no-op: concrete class-level method that resolves the default-method conflict
    }
}
