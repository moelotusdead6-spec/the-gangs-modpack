package com.gangs.gangshop.gui;

import com.gangs.gangshop.GangShopMod;
import com.gangs.gangshop.shop.ShopCategory;
import com.gangs.gangshop.shop.ShopEntry;
import com.gangs.gangshop.shop.TradeMath;
import gang_shop_ozsoihjx.api.ShopBackend;
import gang_shop_ozsoihjx.api.ShopBackendRegistry;
import gang_shop_ozsoihjx.api.ShopGui;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class GuiIntegration implements ShopBackend {
    private GuiIntegration() {
    }

    public static void register() {
        ShopBackendRegistry.register(new GuiIntegration());
    }

    public static boolean openShop(ServerPlayerEntity player) {
        return ShopGui.openShop(player);
    }

    public static boolean openSell(ServerPlayerEntity player) {
        return ShopGui.openSell(player);
    }

    public static boolean openAdmin(ServerPlayerEntity player) {
        return ShopGui.openAdmin(player);
    }

    @Override
    public List<Category> categories(ServerPlayerEntity player) {
        if (!GangShopMod.ACCESS.allows(player)) {
            player.sendMessage(net.minecraft.text.Text.literal("The shop is temporarily restricted to staff and approved testers."), false);
            return List.of();
        }
        List<Category> result = new ArrayList<>();
        for (ShopCategory category : GangShopMod.CATALOG.categories()) {
            Identifier icon = Registries.ITEM.getId(GangShopMod.GUI.iconForCategory(category));
            result.add(new Category(category.getDisplayName(), icon));
        }
        return result;
    }

    @Override
    public List<CatalogEntry> catalog(ServerPlayerEntity player, String category) {
        if (!GangShopMod.ACCESS.allows(player)) return List.of();
        List<CatalogEntry> result = new ArrayList<>();
        for (ShopCategory shopCategory : GangShopMod.CATALOG.categories()) {
            if (!shopCategory.getDisplayName().equals(category)) continue;
            for (ShopEntry entry : GangShopMod.CATALOG.getEntries(shopCategory)) {
                result.add(convert(entry));
            }
        }
        return result;
    }

    @Override
    public List<CatalogEntry> searchResults(ServerPlayerEntity player, String category, String query) {
        if (!GangShopMod.ACCESS.allows(player)) return List.of();
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<CatalogEntry> result = new ArrayList<>();
        for (ShopCategory shopCategory : GangShopMod.CATALOG.categories()) {
            if (category != null && !shopCategory.getDisplayName().equals(category)) continue;
            for (ShopEntry entry : GangShopMod.CATALOG.getEntries(shopCategory)) {
                CatalogEntry converted = convert(entry);
                String haystack = (converted.displayName() + " " + entry.id()).toLowerCase(Locale.ROOT);
                if (haystack.contains(normalized)) result.add(converted);
            }
        }
        return result;
    }

    @Override
    public CatalogEntry findEntry(ServerPlayerEntity player, Identifier itemId) {
        if (!GangShopMod.ACCESS.allows(player)) return null;
        ShopEntry entry = GangShopMod.CATALOG.getEntry(itemId);
        return entry == null ? null : convert(entry);
    }

    private static CatalogEntry convert(ShopEntry entry) {
        String name = entry.item().getName().getString();
        return new CatalogEntry(entry.id(), name, entry.buyPrice(), entry.sellPrice(), "",
                entry.category().getDisplayName(), true);
    }

    @Override
    public long balance(ServerPlayerEntity player) {
        return GangShopMod.ECONOMY.getBalance(player);
    }

    @Override
    public OperationResult buy(ServerPlayerEntity player, Identifier itemId, TradeAmount amount) {
        if (!GangShopMod.ACCESS.allows(player)) return failure("The shop is temporarily restricted.");
        if (amount != TradeAmount.ONE && amount != TradeAmount.STACK && amount != TradeAmount.FILL) {
            return failure("Invalid purchase quantity.");
        }
        ShopEntry entry = GangShopMod.CATALOG.getEntry(itemId);
        if (entry == null) return failure("That item is not in the shop.");
        if (isRemovedGobberItem(itemId)) return failure("That item is disabled on this server.");
        if (new ItemStack(entry.item()).isEmpty()) return failure("That item is disabled on this server.");
        PlayerInventory inventory = player.getInventory();
        int room = availableSpace(inventory, entry.item());
        int quantity = amount == TradeAmount.ONE ? Math.min(1, room)
                : amount == TradeAmount.STACK ? Math.min(Math.min(64, entry.item().getMaxCount()), room) : room;
        if (quantity == 0) return failure("Not enough inventory space.");
        long total;
        try {
            total = TradeMath.total(entry.buyPrice(), quantity);
        } catch (ArithmeticException | IllegalArgumentException exception) {
            return failure("Purchase total is too large or invalid.");
        }
        long balance = GangShopMod.ECONOMY.getBalance(player);
        if (balance < total) return failure("Not enough Gang Bucks.");

        List<ItemStack> before = new ArrayList<>();
        for (ItemStack stack : inventory.main) before.add(stack.copy());
        if (!GangShopMod.ECONOMY.subtract(player, total)) return failure("Not enough Gang Bucks.");
        boolean committed = false;
        try {
            int remaining = quantity;
            while (remaining > 0) {
                int count = Math.min(remaining, entry.item().getMaxCount());
                ItemStack stack = new ItemStack(entry.item(), count);
                inventory.insertStack(stack);
                if (!stack.isEmpty()) return failure("Purchase could not fit in your inventory.");
                remaining -= count;
            }
            committed = true;
        } finally {
            if (!committed) {
                for (int i = 0; i < before.size(); i++) inventory.main.set(i, before.get(i));
                GangShopMod.ECONOMY.set(player, balance);
                inventory.markDirty();
            }
        }
        inventory.markDirty();
        return success("Purchased " + quantity + " for " + total + " Gang Bucks.");
    }

    @Override
    public OperationResult sell(ServerPlayerEntity player, Identifier itemId, TradeAmount amount) {
        if (!GangShopMod.ACCESS.allows(player)) return failure("The shop is temporarily restricted.");
        if (amount != TradeAmount.ONE && amount != TradeAmount.STACK && amount != TradeAmount.ALL) {
            return failure("Invalid sale quantity.");
        }
        ShopEntry entry = GangShopMod.CATALOG.getEntry(itemId);
        if (entry == null) return failure("That item is not in the shop.");
        if (isRemovedGobberItem(itemId)) return failure("That item is disabled on this server.");
        PlayerInventory inventory = player.getInventory();
        int owned = countSellable(inventory, entry.item());
        int quantity = amount == TradeAmount.ONE ? Math.min(1, owned)
                : amount == TradeAmount.STACK ? Math.min(Math.min(64, entry.item().getMaxCount()), owned) : owned;
        if (quantity == 0) return failure("You have no unmodified items of that type to sell.");
        long total;
        long newBalance;
        try {
            total = TradeMath.total(entry.sellPrice(), quantity);
            newBalance = TradeMath.balanceAfterSale(GangShopMod.ECONOMY.getBalance(player), total);
        } catch (ArithmeticException | IllegalArgumentException exception) {
            return failure("Sale total or resulting balance is too large or invalid.");
        }
        int remaining = quantity;
        for (int i = 0; i < inventory.size() && remaining > 0; i++) {
            ItemStack stack = inventory.getStack(i);
            if (!sellable(stack, entry.item())) continue;
            int count = Math.min(remaining, stack.getCount());
            stack.decrement(count);
            remaining -= count;
        }
        if (remaining != 0) throw new IllegalStateException("Inventory changed during a Gang Shop sale");
        GangShopMod.ECONOMY.set(player, newBalance);
        inventory.markDirty();
        return success("Sold " + quantity + " for " + total + " Gang Bucks.");
    }

    private static int availableSpace(PlayerInventory inventory, Item item) {
        ItemStack fresh = new ItemStack(item);
        int room = 0;
        for (ItemStack stack : inventory.main) {
            if (stack.isEmpty()) room += Math.min(item.getMaxCount(), inventory.getMaxCountPerStack());
            else if (ItemStack.canCombine(stack, fresh)) {
                room += Math.max(0, Math.min(stack.getMaxCount(), inventory.getMaxCountPerStack()) - stack.getCount());
            }
        }
        return room;
    }

    private static int countSellable(PlayerInventory inventory, Item item) {
        int count = 0;
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (sellable(stack, item)) count += stack.getCount();
        }
        return count;
    }

    private static boolean sellable(ItemStack stack, Item item) {
        return stack.isOf(item) && !stack.hasNbt();
    }

    private static boolean isRemovedGobberItem(Identifier id) {
        return id.getNamespace().equals("gobber2") && id.getPath().matches(
            "gobber2_(sword(_(nether|end|sniper))?|bow(_(nether|end))?|arrow(_(nether|end))?"
                + "|staff_sniper|(helmet|chestplate|leggings|boots)(_(nether|end|dragon))?)");
    }

    @Override
    public boolean canEdit(ServerPlayerEntity player) {
        return player.hasPermissionLevel(2);
    }

    @Override
    public OperationResult edit(ServerPlayerEntity player, AdminRequest request) {
        if (!canEdit(player)) return failure("You do not have permission to edit the shop.");
        if (request == null || request.action() == null) return failure("Invalid shop edit.");
        try {
            switch (request.action()) {
                case "category_create":
                    requireEmpty(request.third(), request.fourth());
                    GangShopMod.CATALOG.createCategory(request.first(), identifier(request.second()));
                    return new OperationResult(true, "Category created.", request.first());
                case "category_update":
                    requireEmpty(request.fourth());
                    GangShopMod.CATALOG.updateCategory(request.first(), request.second(), identifier(request.third()));
                    return new OperationResult(true, "Category updated.", request.second());
                case "category_delete":
                    requireEmpty(request.second(), request.third(), request.fourth());
                    GangShopMod.CATALOG.deleteCategory(request.first());
                    return success("Category deleted.");
                case "item_add":
                    GangShopMod.CATALOG.editItem(identifier(request.first()), request.second(),
                            parsePrice(request.third()), parsePrice(request.fourth()), true);
                    return new OperationResult(true, "Item added.", request.second());
                case "item_update":
                    GangShopMod.CATALOG.editItem(identifier(request.first()), request.fourth(),
                            parsePrice(request.second()), parsePrice(request.third()), false);
                    return new OperationResult(true, "Item updated.", request.fourth());
                case "item_remove":
                    requireEmpty(request.second(), request.third(), request.fourth());
                    GangShopMod.CATALOG.removeItem(identifier(request.first()));
                    return success("Item removed.");
                default:
                    return failure("Unknown shop edit.");
            }
        } catch (IllegalArgumentException exception) {
            return failure(exception.getMessage());
        }
    }

    private static Identifier identifier(String value) {
        Identifier id = value == null ? null : Identifier.tryParse(value);
        if (id == null) throw new IllegalArgumentException("Invalid item ID.");
        return id;
    }

    private static long parsePrice(String value) {
        if (value == null || !value.matches("[0-9]+")) throw new IllegalArgumentException("Prices must be whole nonnegative numbers.");
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Price is too large.");
        }
    }

    private static void requireEmpty(String... values) {
        for (String value : values) {
            if (value == null || !value.isEmpty()) throw new IllegalArgumentException("Unexpected shop edit fields.");
        }
    }

    private static OperationResult success(String message) {
        return new OperationResult(true, message);
    }

    private static OperationResult failure(String message) {
        return new OperationResult(false, message);
    }
}
