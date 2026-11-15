package com.gangsboots.mod;

import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.recipe.Ingredient;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

public class SwiftnessArmorMaterial implements ArmorMaterial {
    public static final SwiftnessArmorMaterial INSTANCE = new SwiftnessArmorMaterial();

    private SwiftnessArmorMaterial() {
    }

    @Override
    public int getDurability(ArmorItem.Type type) {
        if (type == ArmorItem.Type.BOOTS) {
            return 1000;
        }
        return 1;
    }

    @Override
    public int getProtection(ArmorItem.Type type) {
        if (type == ArmorItem.Type.BOOTS) {
            return 3;
        }
        return 0;
    }

    @Override
    public int getEnchantability() {
        return 0;
    }

    @Override
    public SoundEvent getEquipSound() {
        return SoundEvents.ITEM_ARMOR_EQUIP_DIAMOND;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.EMPTY;
    }

    @Override
    public String getName() {
        return "gangsboots_swiftness";
    }

    @Override
    public float getToughness() {
        return 2.0f;
    }

    @Override
    public float getKnockbackResistance() {
        return 0.0f;
    }
}
