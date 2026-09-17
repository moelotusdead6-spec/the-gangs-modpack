package com.thegangs.gangshats;

import java.util.Map;
import java.util.function.Supplier;

import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class Bouncepads {
	private static final Map<String, Supplier<Block>> DEFINITIONS = Map.of(
			"red", () -> Blocks.RED_CONCRETE,
			"orange", () -> Blocks.ORANGE_CONCRETE,
			"yellow", () -> Blocks.YELLOW_CONCRETE,
			"green", () -> Blocks.GREEN_CONCRETE,
			"blue", () -> Blocks.BLUE_CONCRETE,
			"cyan", () -> Blocks.CYAN_CONCRETE,
			"purple", () -> Blocks.PURPLE_CONCRETE,
			"white", () -> Blocks.WHITE_CONCRETE);
	private static final Map<String, Block> BLOCKS = new java.util.HashMap<>();

	private Bouncepads() {
	}

	public static void register() {
		DEFINITIONS.forEach((color, base) -> {
			Block block = Registry.register(Registries.BLOCK,
					new Identifier(GangsHats.MOD_ID, "bouncepad_" + color),
					new BouncepadBlock(Block.Settings.copy(base.get()).strength(-1.0F, 3600000.0F).dropsNothing()));
			Registry.register(Registries.ITEM, new Identifier(GangsHats.MOD_ID, "bouncepad_" + color),
					new BlockItem(block, new FabricItemSettings().maxCount(1)));
			BLOCKS.put(color, block);
		});
	}

	public static Block get(String color) {
		return BLOCKS.get(color);
	}

	public static boolean is(Block block) {
		return BLOCKS.containsValue(block);
	}
}
