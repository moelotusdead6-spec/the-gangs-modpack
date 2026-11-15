package com.gangsboots.mod.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SkullItem;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(SkullItem.class)
public abstract class SkullItemMixin {
    public void onArmorTick(ItemStack stack, World world, PlayerEntity player) {
    }
}