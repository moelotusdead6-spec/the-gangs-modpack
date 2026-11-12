package com.gangs.gangshop.shop;

import net.minecraft.item.Item;
import net.minecraft.util.Identifier;
import org.junit.Test;
import static org.junit.Assert.*;

public class CatalogServiceTest {
    @Test
    public void furnitureNamespacesAreDeniedBeforeAnyItemOrPriceProcessing() throws Exception {
        var catalog = new CatalogService(null);
        var allowed = CatalogService.class.getDeclaredMethod("isAllowedItem", Item.class, Identifier.class);
        allowed.setAccessible(true);
        for (String namespace : new String[]{"bf_blockpack", "immersive_furniture", "urban_decor",
                "justoutdoorstuffs", "runiclib", "botania", "gangshats"}) {
            for (String path : new String[]{"chair", "crafting_material", "food", "copper_ingot"}) {
                assertEquals(namespace + ":" + path, Boolean.FALSE,
                    allowed.invoke(catalog, null, new Identifier(namespace, path)));
            }
        }
    }
}
