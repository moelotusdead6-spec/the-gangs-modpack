/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.entity.player.PlayerEntity
 *  net.minecraft.entity.player.PlayerInventory
 *  net.minecraft.inventory.Inventory
 *  net.minecraft.inventory.SimpleInventory
 *  net.minecraft.item.Item
 *  net.minecraft.item.ItemConvertible
 *  net.minecraft.item.ItemStack
 *  net.minecraft.item.Items
 *  net.minecraft.nbt.NbtCompound
 *  net.minecraft.nbt.NbtElement
 *  net.minecraft.nbt.NbtList
 *  net.minecraft.nbt.NbtString
 *  net.minecraft.registry.Registries
 *  net.minecraft.screen.NamedScreenHandlerFactory
 *  net.minecraft.screen.ScreenHandler
 *  net.minecraft.screen.slot.SlotActionType
 *  net.minecraft.server.network.ServerPlayerEntity
 *  net.minecraft.sound.SoundEvent
 *  net.minecraft.sound.SoundEvents
 *  net.minecraft.text.Text
 *  net.minecraft.text.Text$Serializer
 *  net.minecraft.util.Formatting
 *  net.minecraft.util.Identifier
 */
package com.gangs.gangshop.gui;

import com.gangs.gangshop.GangShopMod;
import com.gangs.gangshop.economy.GangBucksService;
import com.gangs.gangshop.gui.GangShopScreenHandler;
import com.gangs.gangshop.shop.CatalogService;
import com.gangs.gangshop.shop.ShopCategory;
import com.gangs.gangshop.shop.ShopEntry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

public class ShopGuiService {
    private static final int PAGE_SIZE = 36;
    private static final int[] CONTENT_SLOTS = new int[]{9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44};
    private static final int SLOT_PREV = 45;
    private static final int SLOT_BACK = 49;
    private static final int SLOT_NEXT = 53;
    private static final int SLOT_BALANCE = 4;
    private static final int SLOT_PAGE_INFO = 48;
    private static final int SLOT_ITEM_PREVIEW = 22;
    private static final int SLOT_MINUS_1 = 18;
    private static final int SLOT_MINUS_64 = 19;
    private static final int SLOT_MINUS_999 = 20;
    private static final int SLOT_PLUS_1 = 24;
    private static final int SLOT_PLUS_64 = 25;
    private static final int SLOT_PLUS_999 = 26;
    private static final int SLOT_QUANTITY = 31;
    private static final int SLOT_SELL = 38;
    private static final int SLOT_BUY = 42;
    private static final int SLOT_CONFIRM_INFO = 22;
    private static final int SLOT_CONFIRM_ACCEPT = 30;
    private static final int SLOT_CONFIRM_CANCEL = 32;
    private static final int SLOT_ADMIN_PENDING = 31;
    private static final int[] ADMIN_SELL_MINUS_SLOTS = new int[]{0, 9, 18, 27};
    private static final int[] ADMIN_SELL_PLUS_SLOTS = new int[]{1, 10, 19, 28};
    private static final int[] ADMIN_BUY_MINUS_SLOTS = new int[]{7, 16, 25, 34};
    private static final int[] ADMIN_BUY_PLUS_SLOTS = new int[]{8, 17, 26, 35};
    private static final long[] ADMIN_PRICE_STEPS = new long[]{1L, 10L, 100L, 1000L};
    private final CatalogService catalog;
    private final GangBucksService economy;
    private final Map<UUID, Session> sessions = new HashMap<UUID, Session>();

    public ShopGuiService(CatalogService catalog, GangBucksService economy) {
        this.catalog = catalog;
        this.economy = economy;
    }

    public void openMainMenu(ServerPlayerEntity player) {
        this.openMainMenu(player, 0);
    }

    private void openMainMenu(ServerPlayerEntity player, int page) {
        this.openMainMenu(player, page, false);
    }

    private void openMainMenu(ServerPlayerEntity player, int page, boolean preserveCursor) {
        Session session = this.getOrCreateSession(player);
        session.view = View.MAIN;
        List<ShopCategory> categories = new java.util.ArrayList<>(this.catalog.categories());
        int pageCount = Math.max(1, (int)Math.ceil((double)categories.size() / CONTENT_SLOTS.length));
        session.menuPage = ShopGuiService.clamp(page, 0, pageCount - 1);
        session.slotCategory.clear();
        SimpleInventory inv = ShopGuiService.emptyMenu();
        int start = session.menuPage * CONTENT_SLOTS.length;
        int end = Math.min(categories.size(), start + CONTENT_SLOTS.length);
        for (int index = start; index < end; ++index) {
            ShopCategory category = categories.get(index);
            int slot = CONTENT_SLOTS[index - start];
            ItemStack categoryIcon = new ItemStack(this.iconForCategory(category));
            Text categoryTitle = Text.literal(ShopGuiService.cap(category.getDisplayName())).formatted(Formatting.GOLD);
            Text[] categoryLore = new Text[]{Text.literal("Category: " + ShopGuiService.cap(category.getDisplayName())).formatted(Formatting.YELLOW), Text.literal(ShopGuiService.categorySummary(category)).formatted(Formatting.GRAY), Text.literal("Click to open").formatted(Formatting.DARK_GRAY)};
            inv.setStack(slot, ShopGuiService.withLore(ShopGuiService.named(categoryIcon, categoryTitle), categoryLore));
            session.slotCategory.put(slot, category);
        }
        if (session.menuPage > 0) {
            inv.setStack(SLOT_PREV, ShopGuiService.named(new ItemStack(Items.ARROW), Text.literal("Previous Page").formatted(Formatting.YELLOW)));
        }
        if (session.menuPage < pageCount - 1) {
            inv.setStack(SLOT_NEXT, ShopGuiService.named(new ItemStack(Items.ARROW), Text.literal("Next Page").formatted(Formatting.YELLOW)));
        }
        inv.setStack(SLOT_PAGE_INFO, ShopGuiService.named(new ItemStack(Items.PAPER), Text.literal("Page " + (session.menuPage + 1) + "/" + pageCount).formatted(Formatting.GOLD)));
        inv.setStack(4, this.balanceToken(player));
        this.open(player, (Text)Text.literal((String)"Gang Shop - Menu"), inv, this::handleMainClick, preserveCursor);
    }

    public void openAdminEditor(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        Session session = this.getOrCreateSession(player);
        session.view = View.ADMIN_CATEGORY;
        session.adminMode = true;
        this.openAdminCategory(player, ShopCategory.MINERALS, 0);
    }

    private void openCategory(ServerPlayerEntity player, ShopCategory category, int page) {
        this.openCategory(player, category, page, false);
    }

    private void openCategory(ServerPlayerEntity player, ShopCategory category, int page, boolean preserveCursor) {
        Session session = this.getOrCreateSession(player);
        session.view = View.CATEGORY;
        session.category = category;
        List<ShopEntry> entries = this.catalog.getEntries(category);
        int pageCount = Math.max(1, (int)Math.ceil((double)entries.size() / 36.0));
        session.page = ShopGuiService.clamp(page, 0, pageCount - 1);
        session.slotEntry.clear();
        SimpleInventory inv = ShopGuiService.emptyMenu();
        int start = session.page * 36;
        int end = Math.min(entries.size(), start + 36);
        for (int i = start; i < end; ++i) {
            ShopEntry entry = entries.get(i);
            int slot = CONTENT_SLOTS[i - start];
            ItemStack item = new ItemStack((ItemConvertible)entry.item());
            ItemStack named = ShopGuiService.named(item, (Text)Text.literal((String)entry.id().toString()).formatted(Formatting.WHITE));
            named = ShopGuiService.withLore(named, new Text[]{Text.literal((String)("Sell: " + ShopGuiService.money(entry.sellPrice()) + " Gang Bucks")).formatted(Formatting.GREEN), Text.literal((String)("Buy: " + ShopGuiService.money(entry.buyPrice()) + " Gang Bucks")).formatted(Formatting.RED), Text.literal((String)"Click to open").formatted(Formatting.YELLOW)});
            inv.setStack(slot, named);
            session.slotEntry.put(slot, entry.id());
        }
        if (session.page > 0) {
            inv.setStack(45, ShopGuiService.withLore(ShopGuiService.named(new ItemStack((ItemConvertible)Items.ARROW), (Text)Text.literal((String)"Previous Page").formatted(Formatting.YELLOW)), new Text[]{Text.literal((String)("Go to page " + session.page)).formatted(Formatting.GRAY)}));
        }
        if (session.page < pageCount - 1) {
            inv.setStack(53, ShopGuiService.withLore(ShopGuiService.named(new ItemStack((ItemConvertible)Items.ARROW), (Text)Text.literal((String)"Next Page").formatted(Formatting.YELLOW)), new Text[]{Text.literal((String)("Go to page " + (session.page + 2))).formatted(Formatting.GRAY)}));
        }
        inv.setStack(49, ShopGuiService.named(new ItemStack((ItemConvertible)Items.BEACON), (Text)Text.literal((String)"Back To Menu").formatted(Formatting.AQUA)));
        inv.setStack(48, ShopGuiService.withLore(ShopGuiService.named(new ItemStack((ItemConvertible)Items.PAPER), (Text)Text.literal((String)("Page " + (session.page + 1) + "/" + pageCount)).formatted(Formatting.GOLD)), new Text[]{Text.literal((String)("Category: " + ShopGuiService.cap(category.getDisplayName()))).formatted(Formatting.GRAY)}));
        inv.setStack(4, this.balanceToken(player));
        this.open(player, (Text)Text.literal((String)("Gang Shop - " + ShopGuiService.cap(category.getDisplayName()))), inv, this::handleCategoryClick, preserveCursor);
    }

    private void openItemDetail(ServerPlayerEntity player, Identifier itemId, int quantity) {
        Session session = this.getOrCreateSession(player);
        ShopEntry entry = this.catalog.getEntry(itemId);
        if (entry == null) {
            this.openCategory(player, session.category, session.page);
            return;
        }
        session.view = View.DETAIL;
        session.itemId = itemId;
        session.quantity = ShopGuiService.clamp(quantity, 1, 9999);
        SimpleInventory inv = ShopGuiService.emptyMenu();
        ItemStack preview = ShopGuiService.named(new ItemStack((ItemConvertible)entry.item()), (Text)Text.literal((String)entry.id().toString()).formatted(Formatting.WHITE));
        inv.setStack(22, ShopGuiService.withLore(preview, new Text[]{Text.literal((String)("Sell each: " + ShopGuiService.money(entry.sellPrice()))).formatted(Formatting.GREEN), Text.literal((String)("Buy each: " + ShopGuiService.money(entry.buyPrice()))).formatted(Formatting.RED), Text.literal((String)"Only placeable block/decor items are listed.").formatted(Formatting.DARK_GRAY)}));
        inv.setStack(18, ShopGuiService.named(new ItemStack((ItemConvertible)Items.RED_STAINED_GLASS_PANE), (Text)Text.literal((String)"-1").formatted(Formatting.RED)));
        inv.setStack(19, ShopGuiService.named(new ItemStack((ItemConvertible)Items.RED_STAINED_GLASS_PANE), (Text)Text.literal((String)"-64").formatted(Formatting.RED)));
        inv.setStack(20, ShopGuiService.named(new ItemStack((ItemConvertible)Items.RED_STAINED_GLASS_PANE), (Text)Text.literal((String)"-999").formatted(Formatting.RED)));
        inv.setStack(24, ShopGuiService.named(new ItemStack((ItemConvertible)Items.LIME_STAINED_GLASS_PANE), (Text)Text.literal((String)"+1").formatted(Formatting.GREEN)));
        inv.setStack(25, ShopGuiService.named(new ItemStack((ItemConvertible)Items.LIME_STAINED_GLASS_PANE), (Text)Text.literal((String)"+64").formatted(Formatting.GREEN)));
        inv.setStack(26, ShopGuiService.named(new ItemStack((ItemConvertible)Items.LIME_STAINED_GLASS_PANE), (Text)Text.literal((String)"+999").formatted(Formatting.GREEN)));
        inv.setStack(31, ShopGuiService.withLore(ShopGuiService.named(new ItemStack((ItemConvertible)Items.PAPER), (Text)Text.literal((String)("Quantity: " + session.quantity)).formatted(Formatting.GOLD)), new Text[]{Text.literal((String)"Buy up to 9999 in one go").formatted(Formatting.GRAY), Text.literal((String)"Sell removes every matching item in your inventory").formatted(Formatting.GRAY)}));
        long buyTotal = entry.buyPrice() * (long)session.quantity;
        int sellQuantity = ShopGuiService.countItem(player, entry.item());
        long sellTotal = entry.sellPrice() * (long)sellQuantity;
        inv.setStack(42, ShopGuiService.withLore(ShopGuiService.named(new ItemStack((ItemConvertible)Items.GREEN_STAINED_GLASS_PANE), (Text)Text.literal((String)"Buy").formatted(Formatting.GREEN)), new Text[]{Text.literal((String)("Total: " + ShopGuiService.money(buyTotal) + " Gang Bucks")).formatted(Formatting.GRAY)}));
        inv.setStack(38, ShopGuiService.withLore(ShopGuiService.named(new ItemStack((ItemConvertible)Items.RED_STAINED_GLASS_PANE), (Text)Text.literal((String)"ALL").formatted(Formatting.RED)), new Text[]{Text.literal((String)("Sell all: " + sellQuantity)).formatted(Formatting.GRAY), Text.literal((String)("Total: " + ShopGuiService.money(sellTotal) + " Gang Bucks")).formatted(Formatting.GRAY)}));
        inv.setStack(49, ShopGuiService.named(new ItemStack((ItemConvertible)Items.BEACON), (Text)Text.literal((String)"Back To Category").formatted(Formatting.AQUA)));
        inv.setStack(4, this.balanceToken(player));
        this.open(player, (Text)Text.literal((String)"Gang Shop - Item"), inv, this::handleDetailClick);
    }

    private void openConfirm(ServerPlayerEntity player, boolean buyAction) {
        Session session = this.getOrCreateSession(player);
        ShopEntry entry = this.catalog.getEntry(session.itemId);
        if (entry == null) {
            this.openCategory(player, session.category, session.page);
            return;
        }
        session.view = View.CONFIRM;
        session.pendingBuy = buyAction;
        long total = (buyAction ? entry.buyPrice() : entry.sellPrice()) * (long)session.quantity;
        SimpleInventory inv = ShopGuiService.emptyMenu();
        ItemStack preview = ShopGuiService.named(new ItemStack((ItemConvertible)entry.item()), (Text)Text.literal((String)(buyAction ? "Confirm Buy" : "Confirm Sell")).formatted(Formatting.GOLD));
        inv.setStack(22, ShopGuiService.withLore(preview, new Text[]{Text.literal((String)("Item: " + String.valueOf(entry.id()))).formatted(Formatting.GRAY), Text.literal((String)("Amount: " + session.quantity)).formatted(Formatting.GRAY), Text.literal((String)("Total: " + ShopGuiService.money(total) + " Gang Bucks")).formatted(Formatting.GRAY)}));
        inv.setStack(30, ShopGuiService.named(new ItemStack((ItemConvertible)Items.LIME_STAINED_GLASS_PANE), (Text)Text.literal((String)"Accept").formatted(Formatting.GREEN)));
        inv.setStack(32, ShopGuiService.named(new ItemStack((ItemConvertible)Items.RED_STAINED_GLASS_PANE), (Text)Text.literal((String)"Cancel").formatted(Formatting.RED)));
        inv.setStack(4, this.balanceToken(player));
        this.open(player, (Text)Text.literal((String)"Gang Shop - Confirm"), inv, this::handleConfirmClick);
    }

    private void openAdminCategory(ServerPlayerEntity player, ShopCategory category, int page) {
        Session session = this.getOrCreateSession(player);
        session.view = View.ADMIN_CATEGORY;
        session.category = category;
        List<ShopEntry> entries = this.catalog.getEntries(category);
        int pageCount = Math.max(1, (int)Math.ceil((double)entries.size() / 36.0));
        session.page = ShopGuiService.clamp(page, 0, pageCount - 1);
        session.slotEntry.clear();
        SimpleInventory inv = ShopGuiService.emptyMenu();
        int start = session.page * 36;
        int end = Math.min(entries.size(), start + 36);
        for (int i = start; i < end; ++i) {
            ShopEntry entry = entries.get(i);
            int slot = CONTENT_SLOTS[i - start];
            ItemStack item = ShopGuiService.withLore(ShopGuiService.named(new ItemStack((ItemConvertible)entry.item()), (Text)Text.literal((String)entry.id().toString()).formatted(Formatting.WHITE)), new Text[]{Text.literal((String)("Sell: " + ShopGuiService.money(entry.sellPrice()) + " Gang Bucks")).formatted(Formatting.GREEN), Text.literal((String)("Buy: " + ShopGuiService.money(entry.buyPrice()) + " Gang Bucks")).formatted(Formatting.RED), Text.literal((String)"Click to edit").formatted(Formatting.YELLOW)});
            inv.setStack(slot, item);
            session.slotEntry.put(slot, entry.id());
        }
        if (session.page > 0) {
            inv.setStack(45, ShopGuiService.withLore(ShopGuiService.named(new ItemStack((ItemConvertible)Items.ARROW), (Text)Text.literal((String)"Previous Page").formatted(Formatting.YELLOW)), new Text[]{Text.literal((String)("Go to page " + session.page)).formatted(Formatting.GRAY)}));
        }
        if (session.page < pageCount - 1) {
            inv.setStack(53, ShopGuiService.withLore(ShopGuiService.named(new ItemStack((ItemConvertible)Items.ARROW), (Text)Text.literal((String)"Next Page").formatted(Formatting.YELLOW)), new Text[]{Text.literal((String)("Go to page " + (session.page + 2))).formatted(Formatting.GRAY)}));
        }
        inv.setStack(49, ShopGuiService.named(new ItemStack((ItemConvertible)Items.BEACON), (Text)Text.literal((String)"Back To Menu").formatted(Formatting.AQUA)));
        inv.setStack(48, ShopGuiService.withLore(ShopGuiService.named(new ItemStack((ItemConvertible)Items.PAPER), (Text)Text.literal((String)("Admin Edit - " + ShopGuiService.cap(category.getDisplayName()))).formatted(Formatting.GOLD)), new Text[]{Text.literal((String)("Page " + (session.page + 1) + "/" + pageCount)).formatted(Formatting.GRAY), Text.literal((String)"Choose an item to edit").formatted(Formatting.DARK_GRAY)}));
        inv.setStack(4, this.balanceToken(player));
        this.open(player, (Text)Text.literal((String)("Gang Shop Admin - " + ShopGuiService.cap(category.getDisplayName()))), inv, this::handleAdminCategoryClick);
    }

    private void openAdminItemDetail(ServerPlayerEntity player, Identifier itemId, boolean loadCatalogPrices) {
        Session session = this.getOrCreateSession(player);
        ShopEntry entry = this.catalog.getEntry(itemId);
        if (entry == null) {
            this.openAdminCategory(player, session.category, session.page);
            return;
        }
        session.view = View.ADMIN_DETAIL;
        session.itemId = itemId;
        if (loadCatalogPrices) {
            session.adminSell = entry.sellPrice();
            session.adminBuy = entry.buyPrice();
        }
        SimpleInventory inv = ShopGuiService.emptyMenu();
        inv.setStack(22, ShopGuiService.withLore(ShopGuiService.named(new ItemStack((ItemConvertible)entry.item()), (Text)Text.literal((String)entry.id().toString()).formatted(Formatting.WHITE)), new Text[]{Text.literal((String)("Sell: " + ShopGuiService.money(session.adminSell) + " Gang Bucks")).formatted(Formatting.GREEN), Text.literal((String)("Buy: " + ShopGuiService.money(session.adminBuy) + " Gang Bucks")).formatted(Formatting.RED), Text.literal((String)"Pending values update with every click").formatted(Formatting.GRAY)}));
        for (int i = 0; i < ADMIN_PRICE_STEPS.length; ++i) {
            long step = ADMIN_PRICE_STEPS[i];
            inv.setStack(ADMIN_SELL_MINUS_SLOTS[i], ShopGuiService.adminPriceButton("Sell -" + step, session.adminSell));
            inv.setStack(ADMIN_SELL_PLUS_SLOTS[i], ShopGuiService.adminPriceButton("Sell +" + step, session.adminSell));
            inv.setStack(ADMIN_BUY_MINUS_SLOTS[i], ShopGuiService.adminPriceButton("Buy -" + step, session.adminBuy));
            inv.setStack(ADMIN_BUY_PLUS_SLOTS[i], ShopGuiService.adminPriceButton("Buy +" + step, session.adminBuy));
        }
        inv.setStack(31, ShopGuiService.withLore(ShopGuiService.named(new ItemStack((ItemConvertible)Items.PAPER), (Text)Text.literal((String)"Pending values").formatted(Formatting.GOLD)), new Text[]{Text.literal((String)("Sell: " + ShopGuiService.money(session.adminSell))).formatted(Formatting.GREEN), Text.literal((String)("Buy: " + ShopGuiService.money(session.adminBuy))).formatted(Formatting.RED)}));
        inv.setStack(30, ShopGuiService.withLore(ShopGuiService.named(new ItemStack((ItemConvertible)Items.EMERALD), (Text)Text.literal((String)"Save Prices").formatted(Formatting.GREEN)), new Text[]{Text.literal((String)("Sell: " + ShopGuiService.money(session.adminSell))).formatted(Formatting.GREEN), Text.literal((String)("Buy: " + ShopGuiService.money(session.adminBuy))).formatted(Formatting.RED)}));
        inv.setStack(32, ShopGuiService.named(new ItemStack((ItemConvertible)Items.REDSTONE_BLOCK), (Text)Text.literal((String)"Discard Changes").formatted(Formatting.RED)));
        inv.setStack(49, ShopGuiService.named(new ItemStack((ItemConvertible)Items.BEACON), (Text)Text.literal((String)"Back To Category").formatted(Formatting.AQUA)));
        inv.setStack(4, this.balanceToken(player));
        this.open(player, (Text)Text.literal((String)"Gang Shop Admin - Edit"), inv, this::handleAdminDetailClick);
    }

    private void handleMainClick(ServerPlayerEntity player, int slot, SlotActionType actionType, int button) {
        Session session = this.getOrCreateSession(player);
        if (slot == SLOT_PREV) {
            this.openMainMenu(player, session.menuPage - 1, true);
            ShopGuiService.sound(player, true);
            return;
        }
        if (slot == SLOT_NEXT) {
            this.openMainMenu(player, session.menuPage + 1, true);
            ShopGuiService.sound(player, true);
            return;
        }
        ShopCategory category = session.slotCategory.get(slot);
        if (category != null) {
            session.slotCategory.clear();
            this.openCategory(player, category, 0);
            ShopGuiService.sound(player, true);
        }
    }

    private void handleCategoryClick(ServerPlayerEntity player, int slot, SlotActionType actionType, int button) {
        Session session = this.getOrCreateSession(player);
        if (slot == 45) {
            this.openCategory(player, session.category, session.page - 1, true);
            ShopGuiService.sound(player, true);
            return;
        }
        if (slot == 53) {
            this.openCategory(player, session.category, session.page + 1, true);
            ShopGuiService.sound(player, true);
            return;
        }
        if (slot == 49) {
            this.openMainMenu(player);
            ShopGuiService.sound(player, true);
            return;
        }
        Identifier itemId = session.slotEntry.get(slot);
        if (itemId != null) {
            this.openItemDetail(player, itemId, 1);
            ShopGuiService.sound(player, true);
        }
    }

    private void handleDetailClick(ServerPlayerEntity player, int slot, SlotActionType actionType, int button) {
        Session session = this.getOrCreateSession(player);
        int quantity = session.quantity;
        if (slot == 18) {
            --quantity;
        }
        if (slot == 19) {
            quantity -= 64;
        }
        if (slot == 20) {
            quantity -= 999;
        }
        if (slot == 24) {
            ++quantity;
        }
        if (slot == 25) {
            quantity += 64;
        }
        if (slot == 26) {
            quantity += 999;
        }
        if (slot == 42) {
            this.openConfirm(player, true);
            ShopGuiService.sound(player, true);
            return;
        }
        if (slot == 38) {
            session.quantity = ShopGuiService.countItem(player, this.catalog.getEntry(session.itemId).item());
            if (session.quantity <= 0) {
                player.sendMessage((Text)Text.literal((String)"You do not have any of this item to sell.").formatted(Formatting.RED), false);
                ShopGuiService.sound(player, false);
                return;
            }
            this.openConfirm(player, false);
            ShopGuiService.sound(player, true);
            return;
        }
        if (slot == 49) {
            this.openCategory(player, session.category, session.page);
            ShopGuiService.sound(player, true);
            return;
        }
        if (quantity != session.quantity) {
            this.openItemDetail(player, session.itemId, quantity);
            ShopGuiService.sound(player, true);
        }
    }

    private void handleConfirmClick(ServerPlayerEntity player, int slot, SlotActionType actionType, int button) {
        Session session = this.getOrCreateSession(player);
        if (slot == 32) {
            this.openItemDetail(player, session.itemId, session.quantity);
            ShopGuiService.sound(player, false);
            return;
        }
        if (slot != 30) {
            return;
        }
        ShopEntry entry = this.catalog.getEntry(session.itemId);
        if (entry == null) {
            player.sendMessage((Text)Text.literal((String)"Shop item no longer exists."), false);
            this.openCategory(player, session.category, session.page);
            ShopGuiService.sound(player, false);
            return;
        }
        if (session.pendingBuy) {
            this.performBuy(player, entry, session.quantity);
        } else {
            this.performSell(player, entry, session.quantity);
        }
        this.openItemDetail(player, session.itemId, session.quantity);
    }

    private void handleAdminCategoryClick(ServerPlayerEntity player, int slot, SlotActionType actionType, int button) {
        Session session = this.getOrCreateSession(player);
        if (slot == 45) {
            this.openAdminCategory(player, session.category, session.page - 1);
            ShopGuiService.sound(player, true);
            return;
        }
        if (slot == 53) {
            this.openAdminCategory(player, session.category, session.page + 1);
            ShopGuiService.sound(player, true);
            return;
        }
        if (slot == 49) {
            this.openMainMenu(player);
            ShopGuiService.sound(player, true);
            return;
        }
        Identifier itemId = session.slotEntry.get(slot);
        if (itemId != null) {
            this.openAdminItemDetail(player, itemId, true);
            ShopGuiService.sound(player, true);
        }
    }

    private void handleAdminDetailClick(ServerPlayerEntity player, int slot, SlotActionType actionType, int button) {
        Session session = this.getOrCreateSession(player);
        if (slot == 49) {
            this.openAdminCategory(player, session.category, session.page);
            ShopGuiService.sound(player, true);
            return;
        }
        if (slot == 32) {
            this.openAdminCategory(player, session.category, session.page);
            ShopGuiService.sound(player, false);
            return;
        }
        if (slot == 30) {
            GangShopMod.PRICES.setItemPrice(session.itemId, session.adminSell, session.adminBuy);
            GangShopMod.reloadAll();
            player.sendMessage((Text)Text.literal((String)("Updated " + String.valueOf(session.itemId) + " sell=" + ShopGuiService.money(session.adminSell) + ", buy=" + ShopGuiService.money(session.adminBuy) + " Gang Bucks.")).formatted(Formatting.GREEN), false);
            this.openAdminItemDetail(player, session.itemId, true);
            ShopGuiService.sound(player, true);
            return;
        }
        PriceAdjustment adjustment = ShopGuiService.adminPriceAdjustment(slot);
        if (adjustment == null) {
            return;
        }
        if (adjustment.sellPrice()) {
            session.adminSell = Math.max(1L, session.adminSell + adjustment.amount());
        } else {
            session.adminBuy = Math.max(1L, session.adminBuy + adjustment.amount());
        }
        ShopGuiService.sound(player, true);
        this.openAdminItemDetail(player, session.itemId, false);
    }

    private static ItemStack adminPriceButton(String label, long currentValue) {
        return ShopGuiService.withLore(ShopGuiService.named(new ItemStack((ItemConvertible)Items.PURPLE_STAINED_GLASS_PANE), (Text)Text.literal((String)label).formatted(Formatting.LIGHT_PURPLE)), new Text[]{Text.literal((String)("Current: " + ShopGuiService.money(currentValue))).formatted(Formatting.GRAY)});
    }

    private static PriceAdjustment adminPriceAdjustment(int slot) {
        for (int i = 0; i < ADMIN_PRICE_STEPS.length; ++i) {
            long step = ADMIN_PRICE_STEPS[i];
            if (slot == ADMIN_SELL_MINUS_SLOTS[i]) {
                return new PriceAdjustment(true, -step);
            }
            if (slot == ADMIN_SELL_PLUS_SLOTS[i]) {
                return new PriceAdjustment(true, step);
            }
            if (slot == ADMIN_BUY_MINUS_SLOTS[i]) {
                return new PriceAdjustment(false, -step);
            }
            if (slot != ADMIN_BUY_PLUS_SLOTS[i]) continue;
            return new PriceAdjustment(false, step);
        }
        return null;
    }

    private void performBuy(ServerPlayerEntity player, ShopEntry entry, int quantity) {
        if (quantity <= 0 || quantity > 9999) {
            player.sendMessage((Text)Text.literal((String)"Invalid quantity."), false);
            ShopGuiService.sound(player, false);
            return;
        }
        long total = entry.buyPrice() * (long)quantity;
        if (!this.economy.subtract(player, total)) {
            player.sendMessage((Text)Text.literal((String)"Not enough Gang Bucks.").formatted(Formatting.RED), false);
            ShopGuiService.sound(player, false);
            return;
        }
        ShopGuiService.giveOrDrop(player, entry.item(), quantity);
        player.sendMessage((Text)Text.literal((String)("Purchased " + quantity + "x " + String.valueOf(entry.id()) + " for " + ShopGuiService.money(total) + " Gang Bucks.")).formatted(Formatting.GREEN), false);
        ShopGuiService.sound(player, true);
    }

    private void performSell(ServerPlayerEntity player, ShopEntry entry, int quantity) {
        if (quantity <= 0) {
            player.sendMessage((Text)Text.literal((String)"You do not have any of this item to sell.").formatted(Formatting.RED), false);
            ShopGuiService.sound(player, false);
            return;
        }
        int available = ShopGuiService.countItem(player, entry.item());
        if (available < quantity) {
            player.sendMessage((Text)Text.literal((String)("You only have " + available + " of this block.")).formatted(Formatting.RED), false);
            ShopGuiService.sound(player, false);
            return;
        }
        if (!ShopGuiService.removeItem(player, entry.item(), quantity)) {
            player.sendMessage((Text)Text.literal((String)"Failed to remove items from inventory.").formatted(Formatting.RED), false);
            ShopGuiService.sound(player, false);
            return;
        }
        long total = entry.sellPrice() * (long)quantity;
        this.economy.add(player, total);
        player.sendMessage((Text)Text.literal((String)("Sold " + quantity + "x " + String.valueOf(entry.id()) + " for " + ShopGuiService.money(total) + " Gang Bucks.")).formatted(Formatting.GREEN), false);
        ShopGuiService.sound(player, true);
    }

    private static int countItem(ServerPlayerEntity player, Item item) {
        int count = 0;
        for (int i = 0; i < player.getInventory().size(); ++i) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isOf(item)) continue;
            count += stack.getCount();
        }
        return count;
    }

    private static boolean removeItem(ServerPlayerEntity player, Item item, int count) {
        int remaining = count;
        for (int i = 0; i < player.getInventory().size(); ++i) {
            if (remaining <= 0) {
                return true;
            }
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isOf(item)) continue;
            int take = Math.min(remaining, stack.getCount());
            stack.decrement(take);
            remaining -= take;
        }
        player.getInventory().markDirty();
        return remaining <= 0;
    }

    private static void giveOrDrop(ServerPlayerEntity player, Item item, int totalCount) {
        int give;
        int max = item.getMaxCount();
        for (int remaining = totalCount; remaining > 0; remaining -= give) {
            give = Math.min(max, remaining);
            ItemStack stack = new ItemStack((ItemConvertible)item, give);
            boolean inserted = player.getInventory().insertStack(stack);
            if (inserted && stack.isEmpty()) continue;
            ItemStack drop = stack.isEmpty() ? new ItemStack((ItemConvertible)item, give) : stack.copy();
            player.dropItem(drop, false);
        }
        player.getInventory().markDirty();
    }

    private ItemStack balanceToken(ServerPlayerEntity player) {
        long balance = this.economy.getBalance(player);
        return ShopGuiService.withLore(ShopGuiService.named(new ItemStack((ItemConvertible)ShopGuiService.itemById("alexsmobs:void_worm_eye", Items.ENDER_EYE)), (Text)Text.literal((String)"Gang Bucks").formatted(Formatting.GOLD)), new Text[]{Text.literal((String)("Balance: " + ShopGuiService.money(balance))).formatted(Formatting.YELLOW), Text.literal((String)"Use /bal or /balance too").formatted(Formatting.GRAY)});
    }

    private static String money(long value) {
        return String.format("%,d", value);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String cap(String text) {
        if (text.isEmpty()) {
            return text;
        }
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static ItemStack named(ItemStack stack, Text name) {
        ItemStack copy = stack.copy();
        copy.setCustomName(name);
        return copy;
    }

    private static ItemStack withLore(ItemStack stack, Text ... lines) {
        ItemStack copy = stack.copy();
        NbtCompound nbt = copy.getOrCreateNbt();
        NbtCompound display = nbt.getCompound("display");
        NbtList lore = new NbtList();
        for (Text line : lines) {
            lore.add(NbtString.of(Text.Serializer.toJson(line)));
        }
        display.put("Lore", (NbtElement)lore);
        nbt.put("display", (NbtElement)display);
        return copy;
    }

    private Item iconForCategory(ShopCategory category) {
        if (category.isModded()) {
            List<ShopEntry> entries = this.catalog.getEntries(category);
            return entries.isEmpty() ? Items.ALLIUM : entries.get(0).item();
        }
        if (category == ShopCategory.WOOD) return Items.OAK_LOG;
        if (category == ShopCategory.STONE) return Items.STONE;
        if (category == ShopCategory.MINERALS) return Items.IRON_INGOT;
        if (category == ShopCategory.REDSTONE) return Items.REDSTONE;
        if (category == ShopCategory.FURNITURE) return ShopGuiService.itemById("another_furniture:blue_sofa", Items.BLUE_WOOL);
        if (category == ShopCategory.LIGHTING) return Items.LANTERN;
        if (category == ShopCategory.STAIRS_SLABS) return Items.STONE_STAIRS;
        if (category == ShopCategory.COLOR_MATERIALS) return Items.WHITE_WOOL;
        if (category == ShopCategory.SAND_GLASS) return Items.GLASS;
        if (category == ShopCategory.VEGETATION) return Items.OAK_LEAVES;
        if (category == ShopCategory.OCEAN) return Items.SEA_PICKLE;
        if (category == ShopCategory.NETHER) return Items.NETHERRACK;
        if (category == ShopCategory.END) return Items.END_STONE;
        if (category == ShopCategory.MOB_DROPS) return Items.SKELETON_SKULL;
        if (category == ShopCategory.CRAFTED_ITEMS) return Items.CRAFTING_TABLE;
        return Items.BELL;
    }

    private static Item itemById(String id, Item fallback) {
        Item item = (Item)Registries.ITEM.get(new Identifier(id));
        return item == Items.AIR ? fallback : item;
    }

    private static String categorySummary(ShopCategory category) {
        if (category.isModded()) return "Placeable blocks from this mod";
        if (category == ShopCategory.WOOD) return "Logs, planks, stems, timber decor";
        if (category == ShopCategory.STONE) return "Stone, deepslate, bricks, hard blocks";
        if (category == ShopCategory.MINERALS) return "Coal, flint, precious ores/forms";
        if (category == ShopCategory.REDSTONE) return "Dust, wiring, logic, automation parts";
        if (category == ShopCategory.FURNITURE) return "Decorative furniture-style blocks";
        if (category == ShopCategory.LIGHTING) return "Torches, lanterns, lamps, candles";
        if (category == ShopCategory.STAIRS_SLABS) return "All stair and slab variants";
        if (category == ShopCategory.COLOR_MATERIALS) return "Concrete, terracotta, clay, wool, carpet";
        if (category == ShopCategory.SAND_GLASS) return "Sand, sandstone, glass and panes";
        if (category == ShopCategory.VEGETATION) return "Leaves, grass, flowers, vines, crops";
        if (category == ShopCategory.OCEAN) return "Coral, conduits, sponges, sea life decor";
        if (category == ShopCategory.NETHER) return "Nether blocks and nether materials";
        if (category == ShopCategory.END) return "End blocks and end materials";
        if (category == ShopCategory.MOB_DROPS) return "Vanilla mob drops from kills";
        if (category == ShopCategory.CRAFTED_ITEMS) return "Cut, polished, stairs/slabs variants";
        return "Everything else that is allowed";
    }

    private void open(ServerPlayerEntity player, final Text title, final SimpleInventory inventory, final GangShopScreenHandler.SlotClickHandler clickHandler) {
        this.open(player, title, inventory, clickHandler, false);
    }

    private void open(ServerPlayerEntity player, final Text title, final SimpleInventory inventory, final GangShopScreenHandler.SlotClickHandler clickHandler, boolean preserveCursor) {
        if (preserveCursor && player.currentScreenHandler instanceof GangShopScreenHandler) {
            GangShopScreenHandler screenHandler = (GangShopScreenHandler)player.currentScreenHandler;
            screenHandler.refresh(inventory, clickHandler);
            return;
        }
        NamedScreenHandlerFactory factory = new NamedScreenHandlerFactory(){

            public Text getDisplayName() {
                return title;
            }

            public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity ignored) {
                return new GangShopScreenHandler(syncId, playerInventory, (Inventory)inventory, clickHandler);
            }
        };
        player.openHandledScreen(factory);
    }

    private static SimpleInventory emptyMenu() {
        SimpleInventory inv = new SimpleInventory(54);
        ItemStack filler = ShopGuiService.named(new ItemStack((ItemConvertible)Items.GRAY_STAINED_GLASS_PANE), (Text)Text.literal((String)" "));
        for (int i = 45; i < 54; ++i) {
            inv.setStack(i, filler.copy());
        }
        return inv;
    }

    private Session getOrCreateSession(ServerPlayerEntity player) {
        return this.sessions.computeIfAbsent(player.getUuid(), ignored -> new Session());
    }

    private static void sound(ServerPlayerEntity player, boolean success) {
        player.playSound(success ? (SoundEvent)SoundEvents.UI_BUTTON_CLICK.value() : (SoundEvent)SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.8f, success ? 1.0f : 0.7f);
    }

    private static class Session {
        private View view = View.MAIN;
        private ShopCategory category = ShopCategory.WOOD;
        private int menuPage = 0;
        private int page = 0;
        private Identifier itemId = new Identifier("minecraft", "stone");
        private int quantity = 1;
        private boolean pendingBuy = true;
        private boolean adminMode = false;
        private long adminSell = 1L;
        private long adminBuy = 1L;
        private final Map<Integer, ShopCategory> slotCategory = new HashMap<Integer, ShopCategory>();
        private final Map<Integer, Identifier> slotEntry = new HashMap<Integer, Identifier>();

        private Session() {
        }
    }

    private static enum View {
        MAIN,
        CATEGORY,
        DETAIL,
        CONFIRM,
        ADMIN_CATEGORY,
        ADMIN_DETAIL;

    }

    private record PriceAdjustment(boolean sellPrice, long amount) {
    }
}

