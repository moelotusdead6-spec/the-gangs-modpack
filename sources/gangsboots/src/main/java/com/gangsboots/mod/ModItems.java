package com.gangsboots.mod;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModItems {
    public static final Item BOOTS_OF_SWIFTNESS = new BootsOfSwiftnessItem();

    private ModItems() {
    }

    public static void register() {
        Registry.register(Registries.ITEM, new Identifier(GangsBootsMod.MOD_ID, "boots_of_swiftness"), BOOTS_OF_SWIFTNESS);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> entries.add(BOOTS_OF_SWIFTNESS));
    }
}
