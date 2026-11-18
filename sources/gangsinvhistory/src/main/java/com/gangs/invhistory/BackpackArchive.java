package com.gangs.invhistory;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackStorage;

/** Only loaded when Sophisticated Backpacks is installed. */
final class BackpackArchive {
    private BackpackArchive() {}

    static boolean addTo(NbtCompound snapshot) {
        return addTo(snapshot, BackpackStorage.get().writeNbt(new NbtCompound()));
    }

    /** Copies the external storage of every backpack referenced by the player, including nested backpacks. */
    static boolean addTo(NbtCompound snapshot, NbtCompound storage) {
        Map<UUID, NbtCompound> entries = new HashMap<>();
        for (NbtElement element : storage.getList("backpackContents", NbtElement.COMPOUND_TYPE)) {
            NbtCompound entry = (NbtCompound) element;
            if (entry.containsUuid("uuid")) entries.put(entry.getUuid("uuid"), entry);
        }
        Set<UUID> references = new LinkedHashSet<>();
        collectReferences(snapshot.getCompound("Player"), references);
        var pending = new ArrayDeque<>(references);
        NbtList saved = new NbtList();
        NbtList missing = new NbtList();
        while (!pending.isEmpty()) {
            UUID uuid = pending.removeFirst();
            NbtCompound entry = entries.get(uuid);
            if (entry == null) {
                NbtCompound absent = new NbtCompound();
                absent.putUuid("uuid", uuid);
                missing.add(absent);
                continue;
            }
            saved.add(entry.copy());
            Set<UUID> nested = new LinkedHashSet<>();
            collectReferences(entry.getCompound("contents"), nested);
            for (UUID child : nested) {
                if (references.add(child)) pending.addLast(child);
            }
        }
        snapshot.put("BackpackContents", saved);
        snapshot.put("MissingBackpackContents", missing);
        snapshot.putBoolean("BackpackContentsComplete", missing.isEmpty());
        return missing.isEmpty();
    }

    private static boolean isBackpack(NbtCompound item) {
        return SnapshotView.isBackpack(item);
    }

    private static void collectReferences(NbtElement element, Set<UUID> references) {
        if (element instanceof NbtCompound compound) {
            if (isBackpack(compound)) references.add(compound.getCompound("tag").getUuid("contentsUuid"));
            for (String key : compound.getKeys()) collectReferences(compound.get(key), references);
        } else if (element instanceof NbtList list) {
            for (NbtElement child : list) collectReferences(child, references);
        }
    }
}
