package com.gangsboots.mod.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ElytraItem;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;

/**
 * ElytraItem extends Item directly (not ArmorItem), so it inherits BOTH
 * alexscaves' IForgeItem#onArmorTick and sophisticatedcore's
 * SophisticatedItem#onArmorTick defaults with nothing to resolve them.
 * Sophisticated Backpacks ticks every armor slot, so wearing an elytra threw
 * IncompatibleClassChangeError. A concrete class-level method resolves it.
 */
@Mixin(ElytraItem.class)
public abstract class ElytraItemMixin {
    public void onArmorTick(ItemStack stack, World world, PlayerEntity player) {
    }
}
