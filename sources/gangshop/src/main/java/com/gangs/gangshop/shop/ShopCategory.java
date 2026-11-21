package com.gangs.gangshop.shop;

import java.util.List;

public final class ShopCategory {
    public static final ShopCategory WOOD = vanilla("WOOD", "Wood");
    public static final ShopCategory STONE = vanilla("STONE", "Stone");
    public static final ShopCategory MINERALS = vanilla("MINERALS", "Minerals");
    public static final ShopCategory REDSTONE = vanilla("REDSTONE", "Redstone");
    public static final ShopCategory LIGHTING = vanilla("LIGHTING", "Lighting");
    public static final ShopCategory STAIRS_SLABS = vanilla("STAIRS_SLABS", "Stairs & Slabs");
    public static final ShopCategory COLOR_MATERIALS = vanilla("COLOR_MATERIALS", "Concrete/Clay/Wool");
    public static final ShopCategory SAND_GLASS = vanilla("SAND_GLASS", "Sand/Glass");
    public static final ShopCategory VEGETATION = vanilla("VEGETATION", "Vegetation");
    public static final ShopCategory OCEAN = vanilla("OCEAN", "Ocean");
    public static final ShopCategory NETHER = vanilla("NETHER", "Nether");
    public static final ShopCategory END = vanilla("END", "End");
    public static final ShopCategory MOB_DROPS = vanilla("MOB_DROPS", "Mob Drops");
    public static final ShopCategory FOODS = vanilla("FOODS", "Foods");
    public static final ShopCategory METALS = vanilla("METALS", "Metals");
    public static final ShopCategory CRAFTED_ITEMS = vanilla("CRAFTED_ITEMS", "Crafted Items");
    public static final ShopCategory MISC = vanilla("MISC", "Misc");
    private static final List<ShopCategory> VANILLA_CATEGORIES = List.of(WOOD, STONE, MINERALS, REDSTONE, LIGHTING, STAIRS_SLABS, COLOR_MATERIALS, SAND_GLASS, VEGETATION, OCEAN, NETHER, END, MOB_DROPS, FOODS, METALS, CRAFTED_ITEMS, MISC);

    private final String id;
    private final String displayName;
    private final boolean modded;

    private ShopCategory(String id, String displayName, boolean modded) {
        this.id = id;
        this.displayName = displayName;
        this.modded = modded;
    }

    private static ShopCategory vanilla(String id, String displayName) {
        return new ShopCategory(id, displayName, false);
    }

    public static ShopCategory forMod(String namespace, String displayName) {
        if (namespace.startsWith("mcw")) {
            return new ShopCategory("macaws", "Macaw's", true);
        }
        return new ShopCategory(namespace, displayName, true);
    }

    static ShopCategory withDisplayName(ShopCategory category, String displayName) {
        return new ShopCategory(category.id, displayName, category.modded);
    }

    static ShopCategory custom(String id, String displayName) {
        return new ShopCategory("custom:" + id, displayName, true);
    }

    public static List<ShopCategory> vanillaCategories() {
        return VANILLA_CATEGORIES;
    }

    public String getId() {
        return this.id;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public boolean isModded() {
        return this.modded;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ShopCategory category && this.id.equals(category.id) && this.modded == category.modded;
    }

    @Override
    public int hashCode() {
        return 31 * this.id.hashCode() + Boolean.hashCode(this.modded);
    }
}
