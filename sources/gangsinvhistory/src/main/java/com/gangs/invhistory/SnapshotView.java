package com.gangs.invhistory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;

/** Pure NBT decoding of snapshot files; never links optional mod classes. */
final class SnapshotView {
    /** Display order: main rows, hotbar, head, chest, legs, feet, offhand. */
    static final int MAIN_SIZE = 41;
    private static final int[] ARMOR_SLOTS = {103, 102, 101, 100};
    private static final int OFFHAND_SLOT = 150;

    private SnapshotView() {}

    static ItemStack[] mainInventory(NbtCompound snapshot) {
        ItemStack[] result = empty(MAIN_SIZE);
        for (NbtElement element : snapshot.getCompound("Player").getList("Inventory", NbtElement.COMPOUND_TYPE)) {
            NbtCompound item = (NbtCompound) element;
            int slot = item.getByte("Slot") & 255;
            int index = -1;
            if (slot >= 9 && slot < 36) index = slot - 9;
            else if (slot < 9) index = 27 + slot;
            else if (slot == OFFHAND_SLOT) index = 40;
            else {
                for (int i = 0; i < ARMOR_SLOTS.length; i++) if (ARMOR_SLOTS[i] == slot) index = 36 + i;
            }
            if (index >= 0) result[index] = ItemStack.fromNbt(item);
        }
        return result;
    }

    static ItemStack[] enderChest(NbtCompound snapshot) {
        ItemStack[] result = empty(27);
        for (NbtElement element : snapshot.getCompound("Player").getList("EnderItems", NbtElement.COMPOUND_TYPE)) {
            NbtCompound item = (NbtCompound) element;
            int slot = item.getByte("Slot") & 255;
            if (slot < result.length) result[slot] = ItemStack.fromNbt(item);
        }
        return result;
    }

    static Map<UUID, NbtCompound> archivedBackpacks(NbtCompound snapshot) {
        Map<UUID, NbtCompound> result = new HashMap<>();
        for (NbtElement element : snapshot.getList("BackpackContents", NbtElement.COMPOUND_TYPE)) {
            NbtCompound entry = (NbtCompound) element;
            if (entry.containsUuid("uuid")) result.put(entry.getUuid("uuid"), entry.getCompound("contents"));
        }
        return result;
    }

    /** Items of a Sophisticated Backpacks storage entry, with stack-upgrade counts from {@code realCount}. */
    static List<ItemStack> backpackItems(NbtCompound contents) {
        NbtCompound inventory = contents.getCompound("inventory");
        var items = inventory.getList("Items", NbtElement.COMPOUND_TYPE);
        int size = inventory.getInt("Size");
        for (NbtElement element : items) size = Math.max(size, ((NbtCompound) element).getInt("Slot") + 1);
        var result = new ArrayList<ItemStack>(size);
        for (int i = 0; i < size; i++) result.add(ItemStack.EMPTY);
        for (NbtElement element : items) {
            NbtCompound item = (NbtCompound) element;
            int slot = item.getInt("Slot");
            if (slot < 0) continue;
            ItemStack stack = ItemStack.fromNbt(item);
            if (item.contains("realCount", NbtElement.INT_TYPE)) stack.setCount(item.getInt("realCount"));
            result.set(slot, stack);
        }
        return result;
    }

    static boolean isBackpack(NbtCompound item) {
        return item.getString("id").startsWith("sophisticatedbackpacks:")
            && item.getCompound("tag").containsUuid("contentsUuid");
    }

    static UUID backpackUuid(ItemStack stack) {
        if (stack.isEmpty() || !stack.hasNbt()) return null;
        NbtCompound item = stack.writeNbt(new NbtCompound());
        return isBackpack(item) ? item.getCompound("tag").getUuid("contentsUuid") : null;
    }

    private static ItemStack[] empty(int size) {
        ItemStack[] result = new ItemStack[size];
        java.util.Arrays.fill(result, ItemStack.EMPTY);
        return result;
    }
}
