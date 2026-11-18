package com.gangs.invhistory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Read-only chest view of one snapshot. Only navigation clicks are handled. */
final class SnapshotScreen extends GenericContainerScreenHandler {
    static final int CONTENT = 45;
    static final int BACK = 45;
    static final int ENDER = 46;
    static final int PREVIOUS = 48;
    static final int INFO = 49;
    static final int NEXT = 50;

    private record Page(String title, List<ItemStack> stacks, int page) {}

    private final SimpleInventory display;
    private final NbtCompound snapshot;
    private final String owner;
    private final Map<UUID, NbtCompound> backpacks;
    private final Deque<Page> history = new ArrayDeque<>();
    private final ItemStack[] shown = new ItemStack[CONTENT];
    private Page current;

    private SnapshotScreen(int syncId, PlayerInventory viewer, SimpleInventory display, NbtCompound snapshot) {
        super(ScreenHandlerType.GENERIC_9X6, syncId, viewer, display, 6);
        this.display = display;
        this.snapshot = snapshot;
        this.owner = snapshot.getString("PlayerName");
        this.backpacks = SnapshotView.archivedBackpacks(snapshot);
        show(new Page("Inventory", Arrays.asList(SnapshotView.mainInventory(snapshot)), 0));
    }

    static void open(ServerPlayerEntity viewer, NbtCompound snapshot, Text title) {
        viewer.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, inventory, player) -> new SnapshotScreen(syncId, inventory, new SimpleInventory(54), snapshot),
            title));
    }

    String pageTitle() {
        return current.title();
    }

    private void show(Page page) {
        current = page;
        display.clear();
        Arrays.fill(shown, ItemStack.EMPTY);
        int start = page.page() * CONTENT;
        for (int i = 0; i < CONTENT && start + i < page.stacks().size(); i++) {
            shown[i] = page.stacks().get(start + i);
            display.setStack(i, displayCopy(shown[i]));
        }
        ItemStack filler = button(Items.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = CONTENT; i < 54; i++) display.setStack(i, filler.copy());
        if (!history.isEmpty()) display.setStack(BACK, button(Items.ARROW, "Back"));
        if (history.isEmpty()) display.setStack(ENDER, button(Items.ENDER_CHEST, "Ender chest"));
        if (page.page() > 0) display.setStack(PREVIOUS, button(Items.SPECTRAL_ARROW, "Previous page"));
        if (start + CONTENT < page.stacks().size()) display.setStack(NEXT, button(Items.SPECTRAL_ARROW, "Next page"));
        display.setStack(INFO, info(page));
        sendContentUpdates();
    }

    private ItemStack displayCopy(ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack copy = stack.copy();
        var lore = new ArrayList<Text>();
        int limit = Math.min(64, copy.getMaxCount());
        if (copy.getCount() > limit) {
            lore.add(Text.literal("Stored count: " + stack.getCount()).formatted(Formatting.GOLD));
            copy.setCount(limit);
        }
        UUID backpack = SnapshotView.backpackUuid(stack);
        if (backpack != null) {
            lore.add(Text.literal(backpacks.containsKey(backpack)
                ? "Click: view archived contents" : "Contents were not archived").formatted(Formatting.AQUA));
        }
        if (!lore.isEmpty()) appendLore(copy, lore);
        return copy;
    }

    private ItemStack info(Page page) {
        ItemStack paper = button(Items.PAPER, owner + " - " + snapshot.getString("Event"));
        var lore = new ArrayList<Text>();
        lore.add(Text.literal(InvHistoryCommand.formatTime(snapshot.getLong("SavedAt"))).formatted(Formatting.GRAY));
        lore.add(Text.literal(snapshot.getString("Dimension") + " " + InvHistoryCommand.position(snapshot))
            .formatted(Formatting.GRAY));
        lore.add(Text.literal("Viewing: " + page.title() + " (page " + (page.page() + 1) + "/"
            + Math.max(1, (page.stacks().size() + CONTENT - 1) / CONTENT) + ")").formatted(Formatting.GRAY));
        if (snapshot.contains("BackpackContentsComplete") && !snapshot.getBoolean("BackpackContentsComplete")) {
            lore.add(Text.literal("Some backpack contents were missing when saved").formatted(Formatting.RED));
        }
        appendLore(paper, lore);
        return paper;
    }

    @Override
    public void onSlotClick(int slot, int button, SlotActionType action, PlayerEntity player) {
        if (action == SlotActionType.PICKUP && player instanceof ServerPlayerEntity viewer && slot >= 0 && slot < 54) {
            navigate(slot, viewer);
        }
        // Nothing moves in this view: resend the authoritative state to undo client-side prediction.
        syncState();
    }

    private void navigate(int slot, ServerPlayerEntity viewer) {
        if (slot >= CONTENT) {
            if (slot == BACK && !history.isEmpty()) show(history.pop());
            else if (slot == ENDER && history.isEmpty()) {
                history.push(current);
                show(new Page("Ender chest", Arrays.asList(SnapshotView.enderChest(snapshot)), 0));
            } else if (slot == PREVIOUS && current.page() > 0) {
                show(new Page(current.title(), current.stacks(), current.page() - 1));
            } else if (slot == NEXT && (current.page() + 1) * CONTENT < current.stacks().size()) {
                show(new Page(current.title(), current.stacks(), current.page() + 1));
            }
            return;
        }
        ItemStack stack = shown[slot];
        UUID backpack = SnapshotView.backpackUuid(stack);
        if (backpack == null) return;
        NbtCompound contents = backpacks.get(backpack);
        if (contents == null) {
            viewer.sendMessage(Text.literal("This backpack's contents were not archived in this snapshot.")
                .formatted(Formatting.RED));
            return;
        }
        history.push(current);
        show(new Page(stack.getName().getString(), SnapshotView.backpackItems(contents), 0));
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    private static ItemStack button(net.minecraft.item.Item item, String name) {
        ItemStack stack = new ItemStack(item);
        stack.setCustomName(Text.literal(name).styled(style -> style.withItalic(false)));
        return stack;
    }

    private static void appendLore(ItemStack stack, List<Text> lines) {
        NbtCompound displayTag = stack.getOrCreateSubNbt("display");
        NbtList lore = displayTag.getList("Lore", NbtString.STRING_TYPE);
        for (Text line : lines) {
            lore.add(NbtString.of(Text.Serializer.toJson(Text.empty().append(line)
                .styled(style -> style.withItalic(false)))));
        }
        displayTag.put("Lore", lore);
    }
}
