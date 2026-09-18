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
	public static final Item WING_ANGEL = register("wing_angel");
	public static final Item WING_ASTRONAUT = register("wing_astronaut");
	public static final Item WING_BLUEFIRE = register("wing_bluefire");
	public static final Item WING_BROWN = register("wing_brown");
	public static final Item WING_BUTTERFLY = register("wing_butterfly");
	public static final Item WING_DEMON = register("wing_demon");
	public static final Item WING_DRAGON = register("wing_dragon");
	public static final Item WING_EAGLE = register("wing_eagle");
	public static final Item WING_GOLDEN = register("wing_golden");
	public static final Item WING_OCEAN = register("wing_ocean");
	public static final Item WING_PHOENIX = register("wing_phoenix");
	public static final Item WING_PURPLE = register("wing_purple");

	public static final Item HAT_CHICKEN = register("hat_chicken_hat");
	public static final Item HAT_DIVING = register("hat_diving_helmet");
	public static final Item HAT_FLIGHT = register("hat_flight_hat");
	public static final Item HAT_MILITARY = register("hat_military_helmet");
	public static final Item HAT_MOTORCYCLE = register("hat_motorcycle_helmet");
	public static final Item HAT_POT = register("hat_pot_hat");

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

	public static final Item PET_GHOST = register("pet_ghost");
	public static final Item PET_CLANK = register("pet_clank");
	public static final Item PET_KIT = register("pet_kit");
	public static final Item PET_ASEXUAL_BEE = register("pet_asexual_bee");
	public static final Item PET_BI_BEE = register("pet_bi_bee");
	public static final Item PET_ENBY_BEE = register("pet_enby_bee");
	public static final Item PET_GAY_BEE = register("pet_gay_bee");
	public static final Item PET_LESBIAN_BEE = register("pet_lesbian_bee");
	public static final Item PET_PAN_BEE = register("pet_pan_bee");
	public static final Item PET_PRIDE_BEE = register("pet_pride_bee");
	public static final Item PET_TRANS_BEE = register("pet_trans_bee");

	public static final Item COSMETIC_KEY = register("cosmetic_key", new FabricItemSettings().maxCount(16));

	public static final ItemGroup GROUP = Registry.register(Registries.ITEM_GROUP,
			new Identifier(GangsHats.MOD_ID, "cosmetics"), FabricItemGroup.builder()
					.displayName(Text.translatable("itemGroup.gangshats.cosmetics"))
					.icon(() -> new ItemStack(HALO_BLUE))
					.entries((context, entries) -> {
						entries.add(COSMETIC_KEY);
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
		items.addAll(List.of(WING_ANGEL, WING_ASTRONAUT, WING_BLUEFIRE, WING_BROWN, WING_BUTTERFLY, WING_DEMON,
				WING_DRAGON, WING_EAGLE, WING_GOLDEN, WING_OCEAN, WING_PHOENIX, WING_PURPLE));
		items.addAll(List.of(HAT_CHICKEN, HAT_DIVING, HAT_FLIGHT, HAT_MILITARY, HAT_MOTORCYCLE, HAT_POT));
		items.addAll(List.of(HALO_BLUE, HALO_GREEN, HALO_ORANGE, HALO_PINK, HALO_RED, HALO_WHITE, HALO_YELLOW));
		items.addAll(List.of(SWORD_SKYMETEOR, SWORD_TREE, SWORD_VINE));
		items.addAll(List.of(PET_GHOST, PET_CLANK, PET_KIT, PET_ASEXUAL_BEE, PET_BI_BEE, PET_ENBY_BEE, PET_GAY_BEE,
				PET_LESBIAN_BEE, PET_PAN_BEE, PET_PRIDE_BEE, PET_TRANS_BEE));
		return items;
	}
}
