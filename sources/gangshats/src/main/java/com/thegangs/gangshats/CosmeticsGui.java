package com.thegangs.gangshats;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class CosmeticsGui {
	private static final int SIZE = 54;
	private static final int[] CONTENT_SLOTS = {
			9, 10, 11, 12, 13, 14, 15, 16, 17,
			18, 19, 20, 21, 22, 23, 24, 25, 26,
			27, 28, 29, 30, 31, 32, 33, 34, 35,
			36, 37, 38, 39, 40, 41, 42, 43, 44
	};

	private CosmeticsGui() {
	}

	public static void open(ServerPlayerEntity player, boolean petsOnly) {
		if (player == null) {
			return;
		}
		player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
				(syncId, inventory, ignored) -> new MenuHandler(syncId, inventory, player, petsOnly),
				Text.literal(petsOnly ? "Pets" : "Cosmetics")));
	}

	public static void recall(ServerPlayerEntity player) {
		if (player == null) {
			return;
		}
		CosmeticUnlockState state = CosmeticUnlockState.get(player.getServer());
		boolean hadPet = state.selected(player.getUuid(), "pet") != null;
		state.clear(player.getUuid(), "pet");
		PetService.despawn(player);
		player.sendMessage(Text.literal(hadPet ? "Pet dismissed." : "No active pet to dismiss."), false);
	}

	private static final class MenuHandler extends ScreenHandler {
		private final ServerPlayerEntity player;
		private final boolean petsOnly;
		private final SimpleInventory menu = new SimpleInventory(SIZE);
		private final List<Item> entries = new ArrayList<>();

		private MenuHandler(int syncId, PlayerInventory playerInventory, ServerPlayerEntity player, boolean petsOnly) {
			super(ScreenHandlerType.GENERIC_9X6, syncId);
			this.player = player;
			this.petsOnly = petsOnly;
			populate();
			for (int row = 0; row < 6; row++) {
				for (int column = 0; column < 9; column++) {
					addSlot(new ReadOnlySlot(menu, column + row * 9, 8 + column * 18, 18 + row * 18));
				}
			}
			for (int row = 0; row < 3; row++) {
				for (int column = 0; column < 9; column++) {
					addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 140 + row * 18));
				}
			}
			for (int column = 0; column < 9; column++) {
				addSlot(new Slot(playerInventory, column, 8 + column * 18, 198));
			}
		}

		private void populate() {
			CosmeticUnlockState state = CosmeticUnlockState.get(player.getServer());
			for (Item item : CosmeticItems.all()) {
				String path = net.minecraft.registry.Registries.ITEM.getId(item).getPath();
				if (petsOnly != path.startsWith("pet_")) {
					continue;
				}
				entries.add(item);
			}
			for (int index = 0; index < Math.min(entries.size(), CONTENT_SLOTS.length); index++) {
				Item item = entries.get(index);
				String id = net.minecraft.registry.Registries.ITEM.getId(item).toString();
				boolean unlocked = state.hasUnlock(player.getUuid(), id);
				ItemStack display = unlocked ? new ItemStack(item) : new ItemStack(Items.GRAY_DYE);
				display.setCustomName(Text.literal(unlocked ? item.getName().getString() : "Locked Cosmetic")
						.formatted(unlocked ? Formatting.GREEN : Formatting.DARK_GRAY));
				menu.setStack(CONTENT_SLOTS[index], display);
			}
			menu.setStack(4, named(Items.BARRIER, petsOnly ? "Dismiss Pet" : "Close Collection"));
			menu.setStack(49, named(Items.BOOK, entries.size() + " catalog item(s)"));
		}

		@Override
		public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity clickingPlayer) {
			if (!(clickingPlayer instanceof ServerPlayerEntity serverPlayer) || actionType != SlotActionType.PICKUP) {
				return;
			}
			if (slotIndex == 4 && petsOnly) {
				CosmeticsGui.recall(serverPlayer);
				serverPlayer.closeHandledScreen();
				return;
			}
			if (slotIndex < 0 || slotIndex >= SIZE) {
				return;
			}
			int entryIndex = -1;
			for (int index = 0; index < CONTENT_SLOTS.length; index++) {
				if (CONTENT_SLOTS[index] == slotIndex) {
					entryIndex = index;
					break;
				}
			}
			if (entryIndex < 0 || entryIndex >= entries.size()) {
				return;
			}
			Item item = entries.get(entryIndex);
			String id = net.minecraft.registry.Registries.ITEM.getId(item).toString();
			CosmeticUnlockState state = CosmeticUnlockState.get(serverPlayer.getServer());
			if (!state.hasUnlock(serverPlayer.getUuid(), id)) {
				serverPlayer.sendMessage(Text.literal("That cosmetic is locked."), false);
				return;
			}
			String path = net.minecraft.registry.Registries.ITEM.getId(item).getPath();
			String slot = path.startsWith("pet_") ? "pet" : path.startsWith("hat_") ? "hat"
					: path.startsWith("halo_") ? "halo" : path.startsWith("wing_") ? "back" : "weapon";
			state.select(serverPlayer.getUuid(), slot, id);
			GangsHats.sendSelection(serverPlayer, slot, id);
			if (slot.equals("pet")) {
				PetService.spawn(serverPlayer, id);
			}
			serverPlayer.sendMessage(Text.literal("Selected cosmetic: ").append(item.getName()), false);
		}

		@Override
		public ItemStack quickMove(PlayerEntity player, int slot) {
			return ItemStack.EMPTY;
		}

		@Override
		public boolean canUse(PlayerEntity player) {
			return true;
		}

		private static ItemStack named(Item item, String name) {
			ItemStack stack = new ItemStack(item);
			stack.setCustomName(Text.literal(name).formatted(Formatting.YELLOW));
			return stack;
		}
	}

	private static final class ReadOnlySlot extends Slot {
		private ReadOnlySlot(SimpleInventory inventory, int index, int x, int y) {
			super(inventory, index, x, y);
		}

		@Override
		public boolean canInsert(ItemStack stack) {
			return false;
		}

		@Override
		public boolean canTakeItems(PlayerEntity player) {
			return false;
		}
	}
}
