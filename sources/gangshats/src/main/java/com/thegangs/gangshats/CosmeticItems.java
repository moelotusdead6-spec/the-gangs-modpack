package com.thegangs.gangshats;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class CosmeticItems {
	public static final Item HALO_BLUE = register("halo_blue");
	public static final Item HALO_GREEN = register("halo_green");
	public static final Item HALO_ORANGE = register("halo_orange");
	public static final Item HALO_PINK = register("halo_pink");
	public static final Item HALO_RED = register("halo_red");
	public static final Item HALO_WHITE = register("halo_white");
	public static final Item HALO_YELLOW = register("halo_yellow");

	public static final Item SWORD_SKYMETEOR = register("sword_skymeteor");
	public static final Item SWORD_TREE = register("sword_tree");
	public static final Item SWORD_VINE = register("sword_vine");

	public static final Item COSMETIC_KEY = register("cosmetic_key", new FabricItemSettings().maxCount(16));
	public static final Item COSMETIC_VOUCHER = register("cosmetic_voucher", new FabricItemSettings().maxCount(16));

	public static final ItemGroup GROUP = Registry.register(Registries.ITEM_GROUP,
			new Identifier(GangsHats.MOD_ID, "cosmetics"), FabricItemGroup.builder()
					.displayName(Text.translatable("itemGroup.gangshats.cosmetics"))
					.icon(() -> new ItemStack(HALO_BLUE))
					.entries((context, entries) -> {
						entries.add(COSMETIC_KEY);
						entries.add(COSMETIC_VOUCHER);
						all().forEach(entries::add);
					})
					.build());

	private CosmeticItems() {
	}

	// Must run during mod init; registering lazily from gameplay code hits a frozen registry.
	public static void init() {
		Objects.requireNonNull(GROUP);
	}

	private static Item register(String name) {
		return register(name, new FabricItemSettings().maxCount(1));
	}

	private static Item register(String name, FabricItemSettings settings) {
		return Registry.register(Registries.ITEM, new Identifier(GangsHats.MOD_ID, name), new Item(settings));
	}

	public static List<Item> all() {
		List<Item> items = new ArrayList<>();
		items.addAll(List.of(HALO_BLUE, HALO_GREEN, HALO_ORANGE, HALO_PINK, HALO_RED, HALO_WHITE, HALO_YELLOW));
		items.addAll(List.of(SWORD_SKYMETEOR, SWORD_TREE, SWORD_VINE));
		return items;
	}
}
