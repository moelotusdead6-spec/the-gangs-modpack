package com.gangs.gangsales;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.gangs.gangshop.GangShopMod;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class GangSalesGui {
	private static final int ROWS = 6;
	private static final int SIZE = 9 * ROWS;
	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

	private GangSalesGui() {
	}

	public static void open(ServerPlayerEntity player, View view, int page) {
		open(player, view, page, null);
	}

	public static void openConfirmation(ServerPlayerEntity player, SaleListing listing, int page) {
		open(player, View.CONFIRM, page, listing);
	}

	private static void open(ServerPlayerEntity player, View view, int page, SaleListing selectedListing) {
		NamedScreenHandlerFactory factory = new SimpleNamedScreenHandlerFactory((syncId, inventory, ignored) -> new MenuHandler(syncId, inventory, player, view, Math.max(0, page), selectedListing), whiteText(view == View.CONFIRM ? "Confirm Purchase" : "GangSales"));
		player.openHandledScreen(factory);
	}

	private static Text whiteText(String text) {
		return Text.literal(text).formatted(Formatting.WHITE);
	}

	public enum View {
		BROWSE, HISTORY, CONFIRM
	}

	private static final class MenuHandler extends ScreenHandler {
		private final ServerPlayerEntity player;
		private final View view;
		private final int page;
		private final SaleListing selectedListing;
		private final Inventory menu = new SimpleInventory(SIZE);
		private final Map<Integer, SaleListing> actions = new HashMap<>();

		private MenuHandler(int syncId, PlayerInventory playerInventory, ServerPlayerEntity player, View view, int page, SaleListing selectedListing) {
			super(ScreenHandlerType.GENERIC_9X6, syncId);
			this.player = player;
			this.view = view;
			this.page = page;
			this.selectedListing = selectedListing;
			populate();
			for (int row = 0; row < ROWS; row++) for (int column = 0; column < 9; column++) addSlot(new ReadOnlySlot(menu, column + row * 9, 8 + column * 18, 18 + row * 18));
			for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 140 + row * 18));
			for (int column = 0; column < 9; column++) addSlot(new Slot(playerInventory, column, 8 + column * 18, 198));
		}

		private void populate() {
			if (view == View.CONFIRM) {
				populateConfirmation();
				return;
			}
			put(0, Items.ARROW, "Previous page");
			put(1, Items.GOLD_INGOT, "Wallet: " + GangShopMod.ECONOMY.getBalance(player.getUuid()) + " Gang Bucks");
			put(2, Items.EMERALD, "Browse sales");
			put(4, Items.BOOK, "My history");
			put(8, Items.ARROW, "Next page");
			List<SaleListing> listings = view == View.BROWSE ? GangSalesMod.SALES.activeListings() : GangSalesMod.SALES.historyFor(player.getUuid());
			int perPage = view == View.HISTORY ? 18 : 45;
			int start = page * perPage;
			if (view == View.HISTORY && listings.isEmpty()) put(31, Items.BOOK, "No GangSales history yet.");
			for (int index = start; index < Math.min(start + perPage, listings.size()); index++) {
				int displayIndex = index - start;
				int slot = view == View.HISTORY ? 9 + displayIndex % 9 + (displayIndex / 9) * 27 : 9 + displayIndex;
				SaleListing listing = listings.get(index);
				menu.setStack(slot, listingStack(listing));
				actions.put(slot, listing);
				if (view == View.HISTORY) {
					int statusSlot = slot + 9;
					menu.setStack(statusSlot, historyStack(listing));
					actions.put(statusSlot, listing);
				}
			}
		}

		private void populateConfirmation() {
			if (selectedListing == null) return;
			for (int slot : new int[] { 19, 20, 21, 28, 29, 30 }) put(slot, Items.GREEN_STAINED_GLASS_PANE, "Accept purchase");
			for (int slot : new int[] { 23, 24, 25, 32, 33, 34 }) put(slot, Items.RED_STAINED_GLASS_PANE, "Decline purchase");
			ItemStack item = listingStack(selectedListing);
			item.setCustomName(whiteText(selectedListing.getStack().getName().getString() + " x" + selectedListing.getStack().getCount()));
			menu.setStack(22, item);
			put(31, Items.GOLD_INGOT, "Price: " + selectedListing.getPrice() + " Gang Bucks");
		}

		private ItemStack listingStack(SaleListing listing) {
			ItemStack stack = listing.getStack();
			stack.setCustomName(whiteText(stack.getName().getString() + " | " + listing.getPrice() + " Gang Bucks"));
			return stack;
		}

		private ItemStack historyStack(SaleListing listing) {
			Item icon = switch (listing.getStatus()) {
				case SOLD -> Items.RED_STAINED_GLASS_PANE;
				case EXPIRED -> Items.NETHER_STAR;
				case ACTIVE -> Items.BLACK_STAINED_GLASS_PANE;
				case CLAIMED -> Items.BLACK_STAINED_GLASS_PANE;
			};
			if (player.getUuid().equals(listing.getBuyerId())) icon = Items.GREEN_STAINED_GLASS_PANE;
			ItemStack stack = new ItemStack(icon);
			String status = player.getUuid().equals(listing.getBuyerId()) ? "Bought" : listing.getStatus().name();
			long time = listing.getCompletedAt() == 0 ? listing.getCreatedAt() : listing.getCompletedAt();
			stack.setCustomName(whiteText(status + ": " + listing.getStack().getName().getString() + " x" + listing.getStack().getCount() + " | " + DATE_TIME.format(Instant.ofEpochMilli(time))));
			return stack;
		}

		private void put(int slot, Item item, String name) {
			ItemStack stack = new ItemStack(item);
			stack.setCustomName(whiteText(name));
			menu.setStack(slot, stack);
		}

		@Override
		public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity clickingPlayer) {
			if (slotIndex < 0 || slotIndex >= SIZE || actionType != SlotActionType.PICKUP) return;
			if (view == View.CONFIRM) {
				handleConfirmation(slotIndex);
				return;
			}
			if (slotIndex == 0) open(player, view, Math.max(0, page - 1));
			else if (slotIndex == 2) open(player, View.BROWSE, 0);
			else if (slotIndex == 4) open(player, View.HISTORY, 0);
			else if (slotIndex == 8) open(player, view, page + 1);
			else if (actions.containsKey(slotIndex)) handleAction(actions.get(slotIndex));
		}

		private void handleAction(SaleListing listing) {
			if (view == View.BROWSE) {
				openConfirmation(player, listing, page);
			} else if (view == View.HISTORY && listing.getSellerId().equals(player.getUuid()) && (listing.getStatus() == ListingStatus.ACTIVE || listing.getStatus() == ListingStatus.EXPIRED)) {
				boolean retrieved = GangSalesMod.SALES.retrieveListing(player, listing.getId());
				player.sendMessage(Text.literal(retrieved ? "Listing returned to your inventory." : "Your inventory does not have room for this item."), false);
				open(player, View.HISTORY, page);
			}
		}

		private void handleConfirmation(int slotIndex) {
			if (slotIndex >= 19 && slotIndex <= 21 || slotIndex >= 28 && slotIndex <= 30) {
				SalesStore.PurchaseResult result = selectedListing == null ? SalesStore.PurchaseResult.NOT_AVAILABLE : GangSalesMod.SALES.purchase(player, selectedListing.getId());
				player.sendMessage(Text.literal(purchaseMessage(result)), false);
			}
			open(player, View.BROWSE, page);
		}

		private String purchaseMessage(SalesStore.PurchaseResult result) {
			return switch (result) {
				case SUCCESS -> "Purchase complete.";
				case INVENTORY_FULL -> "Your inventory is full; the sale was not completed.";
				case INSUFFICIENT_FUNDS -> "You do not have enough Gang Bucks.";
				case OWN_LISTING -> "You cannot buy your own listing.";
				case NOT_AVAILABLE -> "That listing is no longer available.";
			};
		}

		@Override
		public ItemStack quickMove(PlayerEntity player, int slot) {
			return ItemStack.EMPTY;
		}

		@Override
		public boolean canUse(PlayerEntity player) {
			return true;
		}
	}

	private static final class ReadOnlySlot extends Slot {
		private ReadOnlySlot(Inventory inventory, int index, int x, int y) {
			super(inventory, index, x, y);
		}

		@Override
		public boolean canTakeItems(PlayerEntity playerEntity) {
			return false;
		}
	}
}