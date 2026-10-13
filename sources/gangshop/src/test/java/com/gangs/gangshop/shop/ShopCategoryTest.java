package com.gangs.gangshop.shop;

import org.junit.Test;
import static org.junit.Assert.*;

public class ShopCategoryTest {
    @Test
    public void allMacawsNamespacesShareOneCategoryAndAdminSettingsKey() {
        var expected = ShopCategory.forMod("mcwdoors", "Macaw's Doors");
        for (String namespace : new String[]{"mcwdoors", "mcwwindows", "mcwfurnitures", "mcwroofs",
                "mcwtrpdoors", "mcwbridges", "mcwfences", "mcwlights", "mcwpaths", "mcwpaintings"}) {
            var category = ShopCategory.forMod(namespace, namespace);
            assertEquals(expected, category);
            assertEquals(expected.hashCode(), category.hashCode());
            assertEquals("macaws", category.getId());
            assertEquals("Macaw's", category.getDisplayName());
            assertTrue(category.isModded());
        }
    }

    @Test
    public void unrelatedModsAndVanillaCategoriesAreUnchanged() {
        var category = ShopCategory.forMod("supplementaries", "Supplementaries");
        assertEquals("supplementaries", category.getId());
        assertEquals("Supplementaries", category.getDisplayName());
        assertNotEquals(category, ShopCategory.forMod("mcwdoors", "Macaw's Doors"));
        assertFalse(ShopCategory.WOOD.isModded());
    }
}
