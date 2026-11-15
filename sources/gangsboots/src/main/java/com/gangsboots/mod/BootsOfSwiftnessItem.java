package com.gangsboots.mod;

import java.util.List;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

public class BootsOfSwiftnessItem extends ArmorItem {
    public BootsOfSwiftnessItem() {
        super(SwiftnessArmorMaterial.INSTANCE, Type.BOOTS, new Settings().maxCount(1).maxDamage(1000));
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public boolean canRepair(ItemStack stack, ItemStack ingredient) {
        return false;
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return true;
    }

    // Matches IForgeItem/SophisticatedItem default method signature (class_1657 = PlayerEntity in current Yarn)
    public void onArmorTick(ItemStack stack, World world, PlayerEntity player) {
        // Flight and durability are handled from the server tick callback in GangsBootsMod.
    }

    @Override
    public void appendTooltip(ItemStack stack, net.minecraft.world.World world, List<Text> tooltip, net.minecraft.client.item.TooltipContext context) {
        int remaining = stack.getMaxDamage() - stack.getDamage();
        tooltip.add(Text.translatable("item.gangsboots.boots_of_swiftness.tooltip.1").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.gangsboots.boots_of_swiftness.tooltip.2").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("Fuel: " + remaining + "/" + stack.getMaxDamage()).formatted(Formatting.AQUA));
        tooltip.add(Text.literal("Cannot be enchanted, repaired, or crafted").formatted(Formatting.RED));
    }
}
