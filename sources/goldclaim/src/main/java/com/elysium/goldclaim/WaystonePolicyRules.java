package com.elysium.goldclaim;

import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

final class WaystonePolicyRules {
    private WaystonePolicyRules() {}

    static boolean shouldRemove(String dimension, UUID id, Set<UUID> activated) {
        return "minecraft:overworld".equals(dimension) && !activated.contains(id);
    }

    static void collectActivated(NbtElement element, Set<UUID> activated) {
        if (element instanceof NbtCompound compound) {
            if (compound.contains("WaystonesData")) {
                if (!(compound.get("WaystonesData") instanceof NbtCompound data)) {
                    throw new IllegalArgumentException("Invalid WaystonesData compound.");
                }
                if (data.contains("Waystones")) {
                    if (!(data.get("Waystones") instanceof NbtList list)) {
                        throw new IllegalArgumentException("Invalid activated Waystones list.");
                    }
                    for (NbtElement entry : list) {
                        if (!(entry instanceof NbtString)) {
                            throw new IllegalArgumentException("Activated waystone ID is not a string.");
                        }
                        activated.add(UUID.fromString(entry.asString()));
                    }
                }
            }
            for (String key : compound.getKeys()) {
                collectActivated(compound.get(key), activated);
            }
        } else if (element instanceof NbtList list) {
            for (NbtElement child : list) {
                collectActivated(child, activated);
            }
        }
    }
}
