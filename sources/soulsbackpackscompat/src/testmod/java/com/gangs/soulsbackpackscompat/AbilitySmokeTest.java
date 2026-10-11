package com.gangs.soulsbackpackscompat;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class AbilitySmokeTest implements DedicatedServerModInitializer {
	@Override
	public void onInitializeServer() {
		try {
			Method getter = Item.class.getMethod("getAbilities");
			Field abilities = Item.class.getDeclaredField("abilities");
			abilities.setAccessible(true);
			List<?> original = list(getter, Items.DIAMOND);
			require(original != null, "Normally constructed items must have a list");
			require(list(getter, Items.DIAMOND) == original, "Existing list identity must be preserved");
			abilities.set(Items.DIAMOND, null);
			List<?> repaired = list(getter, Items.DIAMOND);
			require(repaired != null && repaired.isEmpty(), "A missing list must be repaired");
			repaired.add(null);
			require(list(getter, Items.DIAMOND) == repaired && repaired.size() == 1,
					"Repaired lists must be mutable and retained");
			repaired.clear();
			Items.DIAMOND.inventoryTick(new ItemStack(Items.DIAMOND), null, null, 0, false);
			Class<?> mathHelper = Class.forName("net.p3pp3rf1y.sophisticatedcore.util.MathHelper");
			Method multiply = mathHelper.getMethod("intMaxCappedMultiply", int.class, int.class);
			require((Integer) multiply.invoke(null, 0, 8) == 0,
					"Zero item max counts must be guarded before division");

			int weapons = 0;
			int backpacks = 0;
			for (Item item : Registries.ITEM) {
				String namespace = Registries.ITEM.getId(item).getNamespace();
				if (namespace.equals("soulsweapons")) {
					List<?> existing = list(getter, item);
					require(existing != null, "Soulslike items must have a list");
					if (!existing.isEmpty()) {
						Object first = existing.get(0);
						require(list(getter, item) == existing && existing.get(0) == first,
								"Weapon abilities must be preserved");
						weapons++;
					}
				} else if (namespace.equals("sophisticatedbackpacks")
						&& item.getClass().getSimpleName().equals("BackpackItem")) {
					ItemStack stack = new ItemStack(item);
					NbtCompound tag = new NbtCompound();
					tag.putString("compat_smoke", "unchanged");
					stack.setNbt(tag);
					NbtCompound before = stack.writeNbt(new NbtCompound());
					abilities.set(item, null);
					require(list(getter, item).isEmpty(), "Backpacks with missing lists must be repaired");
					require(before.equals(stack.writeNbt(new NbtCompound())), "Backpack NBT must not change");
					backpacks++;
				}
			}
			require(weapons > 0, "The test must exercise real registered weapon abilities");
			require(backpacks > 0, "The test must exercise real registered backpack items");
			LoggerFactory.getLogger("soulsbackpackscompat_smoke").info(
					"PASS: transformed Item getter, null repair, mutable stable lists, inventory tick, {} weapons, {} backpacks, unchanged NBT",
					weapons, backpacks);
			Files.writeString(Path.of("ability-smoke-passed.txt"), "PASS: " + weapons + " weapons, "
					+ backpacks + " backpacks, null repair, stable mutable lists, inventory tick, unchanged NBT\n");
		} catch (ReflectiveOperationException | IOException failure) {
			throw new IllegalStateException("Real-mod ability compatibility smoke test failed", failure);
		}
		// This isolated fixture exits before starting a server or creating a world.
		System.exit(0);
	}

	private static List<?> list(Method getter, Item item) throws ReflectiveOperationException {
		return (List<?>) getter.invoke(item);
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new IllegalStateException(message);
		}
	}
}
