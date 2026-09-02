package com.gangs.gangshop.shop;

import java.util.List;

public final class ShopCategory {
    public static final ShopCategory WOOD = vanilla("WOOD", "wood");
    public static final ShopCategory STONE = vanilla("STONE", "stone");
    public static final ShopCategory MINERALS = vanilla("MINERALS", "minerals");
    public static final ShopCategory REDSTONE = vanilla("REDSTONE", "redstone");
    public static final ShopCategory LIGHTING = vanilla("LIGHTING", "lighting");
    public static final ShopCategory STAIRS_SLABS = vanilla("STAIRS_SLABS", "stairs & slabs");
    public static final ShopCategory COLOR_MATERIALS = vanilla("COLOR_MATERIALS", "concrete/clay/wool");
    public static final ShopCategory SAND_GLASS = vanilla("SAND_GLASS", "sand/glass");
    public static final ShopCategory FURNITURE = vanilla("FURNITURE", "furniture");
    public static final ShopCategory VEGETATION = vanilla("VEGETATION", "vegetation");
    public static final ShopCategory OCEAN = vanilla("OCEAN", "ocean");
    public static final ShopCategory NETHER = vanilla("NETHER", "nether");
    public static final ShopCategory END = vanilla("END", "end");
    public static final ShopCategory MOB_DROPS = vanilla("MOB_DROPS", "mob drops");
    public static final ShopCategory CRAFTED_ITEMS = vanilla("CRAFTED_ITEMS", "crafted items");
    public static final ShopCategory MISC = vanilla("MISC", "misc");
    private static final List<ShopCategory> VANILLA_CATEGORIES = List.of(WOOD, STONE, MINERALS, REDSTONE, LIGHTING, STAIRS_SLABS, COLOR_MATERIALS, SAND_GLASS, FURNITURE, VEGETATION, OCEAN, NETHER, END, MOB_DROPS, CRAFTED_ITEMS, MISC);

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
        return new ShopCategory(namespace, displayName, true);
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

