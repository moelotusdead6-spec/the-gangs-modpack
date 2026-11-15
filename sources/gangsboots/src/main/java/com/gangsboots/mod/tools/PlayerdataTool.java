package com.gangsboots.mod.tools;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class PlayerdataTool {
	private static final DateTimeFormatter BACKUP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

	private PlayerdataTool() {
	}

	public static void main(String[] args) throws Exception {
		if (args.length < 3) {
			throw new IllegalArgumentException("Usage: <scan|clearBackpackArmor> <playerdata.dat> <report.txt>");
		}

		String mode = args[0];
		Path playerdataPath = Path.of(args[1]);
		Path reportPath = Path.of(args[2]);

		NbtCompound root = NbtIo.readCompressed(playerdataPath.toFile());
		List<String> report = new ArrayList<>();

		if ("scan".equalsIgnoreCase(mode)) {
			scanPlayerdata(root, report);
			Files.write(reportPath, report, StandardCharsets.UTF_8);
			return;
		}

		if ("clearBackpackArmor".equalsIgnoreCase(mode) || "clearConflictingArmor".equalsIgnoreCase(mode)) {
			int patched = clearConflictingArmor(root, report);
			Path backup = playerdataPath.resolveSibling(playerdataPath.getFileName() + ".backup_" + LocalDateTime.now().format(BACKUP_FORMAT) + "_backpackarmorfix");
			Files.copy(playerdataPath, backup);
			NbtIo.writeCompressed(root, playerdataPath.toFile());
			report.add(0, "Patched backpack armor items: " + patched);
			report.add(1, "Backup: " + backup);
			Files.write(reportPath, report, StandardCharsets.UTF_8);
			return;
		}

		if ("pruneAirEntries".equalsIgnoreCase(mode)) {
			int patched = pruneAirEntries(root, report);
			Path backup = playerdataPath.resolveSibling(playerdataPath.getFileName() + ".backup_" + LocalDateTime.now().format(BACKUP_FORMAT) + "_airprune");
			Files.copy(playerdataPath, backup);
			NbtIo.writeCompressed(root, playerdataPath.toFile());
			report.add(0, "Pruned air entries: " + patched);
			report.add(1, "Backup: " + backup);
			Files.write(reportPath, report, StandardCharsets.UTF_8);
			return;
		}

		throw new IllegalArgumentException("Unknown mode: " + mode);
	}

	private static void scanPlayerdata(NbtCompound root, List<String> report) {
		NbtList inventory = root.getList("Inventory", NbtElement.COMPOUND_TYPE);
		for (int index = 0; index < inventory.size(); index++) {
			NbtCompound stack = inventory.getCompound(index);
			String itemId = stack.getString("id");
			report.add("Inventory[" + index + "] slot=" + stack.getByte("Slot") + " id=" + itemId);
			if (isBackpackId(itemId)) {
				scanBackpack(stack, "Inventory[" + index + "]", report);
			}
		}
	}

	private static int clearConflictingArmor(NbtCompound root, List<String> report) {
		int patched = 0;
		NbtList inventory = root.getList("Inventory", NbtElement.COMPOUND_TYPE);
		for (int index = 0; index < inventory.size(); index++) {
			NbtCompound stack = inventory.getCompound(index);
			String itemId = stack.getString("id");
			byte slot = stack.getByte("Slot");
			if (isEquippedArmorSlot(slot) && isArmorLikeItem(itemId)) {
				neutralizeItem(stack, slot);
				report.add("Inventory[" + index + "] equipped slot=" + slot + " patched from " + itemId + " to minecraft:air");
				patched++;
				continue;
			}
			if (!isBackpackId(itemId)) {
				continue;
			}
			patched += patchBackpack(stack, "Inventory[" + index + "]", report);
		}
		return patched;
	}

	private static int pruneAirEntries(NbtCompound root, List<String> report) {
		int patched = 0;
		NbtList inventory = root.getList("Inventory", NbtElement.COMPOUND_TYPE);
		for (int index = 0; index < inventory.size(); index++) {
			NbtCompound stack = inventory.getCompound(index);
			patched += pruneAirEntries(stack, "Inventory[" + index + "]", report);
		}
		return patched;
	}

	private static void scanBackpack(NbtCompound backpack, String path, List<String> report) {
		walkContainedItems(backpack, path, report, false);
	}

	private static int patchBackpack(NbtCompound backpack, String path, List<String> report) {
		return walkContainedItems(backpack, path, report, true);
	}

	private static int walkContainedItems(NbtElement element, String path, List<String> report, boolean patch) {
		int patched = 0;
		if (element instanceof NbtCompound compound) {
			if (compound.contains("id", NbtElement.STRING_TYPE)) {
				String itemId = compound.getString("id");
				report.add(path + " -> " + itemId);
				if (patch && isArmorLikeItem(itemId) && !isBackpackId(itemId)) {
					byte slot = compound.contains("Slot", NbtElement.BYTE_TYPE) ? compound.getByte("Slot") : 0;
					neutralizeItem(compound, slot);
					report.add(path + " patched to minecraft:air");
					patched++;
				}
			}

			for (String key : compound.getKeys()) {
				patched += walkContainedItems(compound.get(key), path + "." + key, report, patch);
			}
			return patched;
		}

		if (element instanceof NbtList list) {
			for (int index = 0; index < list.size(); index++) {
				patched += walkContainedItems(list.get(index), path + "[" + index + "]", report, patch);
			}
		}
		return patched;
	}

	private static int pruneAirEntries(NbtElement element, String path, List<String> report) {
		int patched = 0;
		if (element instanceof NbtCompound compound) {
			for (String key : new ArrayList<>(compound.getKeys())) {
				patched += pruneAirEntries(compound.get(key), path + "." + key, report);
			}
			return patched;
		}

		if (element instanceof NbtList list) {
			for (int index = list.size() - 1; index >= 0; index--) {
				NbtElement child = list.get(index);
				if (child instanceof NbtCompound compound
					&& compound.contains("id", NbtElement.STRING_TYPE)
					&& "minecraft:air".equals(compound.getString("id"))) {
					list.remove(index);
					report.add(path + "[" + index + "] removed minecraft:air entry");
					patched++;
					continue;
				}
				patched += pruneAirEntries(child, path + "[" + index + "]", report);
			}
		}
		return patched;
	}

	private static boolean isBackpackId(String itemId) {
		return itemId.startsWith("sophisticatedbackpacks:") && itemId.endsWith("_backpack");
	}

	private static boolean isArmorLikeItem(String itemId) {
		return itemId.endsWith("_helmet")
			|| itemId.endsWith("_chestplate")
			|| itemId.endsWith("_leggings")
			|| itemId.endsWith("_boots")
			|| itemId.equals("gangsboots:boots_of_swiftness");
	}

	private static boolean isEquippedArmorSlot(byte slot) {
		return slot >= 100 && slot <= 103;
	}

	private static void neutralizeItem(NbtCompound compound, byte slot) {
		for (String key : new ArrayList<>(compound.getKeys())) {
			compound.remove(key);
		}
		compound.putString("id", "minecraft:air");
		compound.putByte("Count", (byte) 0);
		compound.putByte("Slot", slot);
	}
}