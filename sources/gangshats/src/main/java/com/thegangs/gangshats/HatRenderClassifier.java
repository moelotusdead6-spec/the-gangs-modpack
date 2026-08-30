package com.thegangs.gangshats;

import java.util.Set;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

public final class HatRenderClassifier {
	public static final TagKey<net.minecraft.item.Item> CROWN_RENDER_OVERRIDE = TagKey.of(RegistryKeys.ITEM, new Identifier(GangsHats.MOD_ID, "crown_render_override"));
	private static final Set<String> PLUSHIE_NAMESPACES = Set.of("a_man_with_plushies", "perfectplushies", "sarosplayerplushiemod", "saros_player_plushie");

	private HatRenderClassifier() {
	}

	public static boolean shouldRenderAsCrown(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}

		Identifier itemId = net.minecraft.registry.Registries.ITEM.getId(stack.getItem());
		return stack.isIn(CROWN_RENDER_OVERRIDE) || PLUSHIE_NAMESPACES.contains(itemId.getNamespace()) || !(stack.getItem() instanceof BlockItem);
	}

	public static boolean shouldRenderAsPlacedBlock(ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() instanceof BlockItem && !shouldRenderAsCrown(stack);
	}
}