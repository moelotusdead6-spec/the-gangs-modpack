package com.gangs.gangshop.shop;

import net.minecraft.item.Item;
import net.minecraft.util.Identifier;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public class CatalogServiceTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void furnitureNamespacesAreDeniedBeforeAnyItemOrPriceProcessing() throws Exception {
        var catalog = new CatalogService(null);
        var allowed = CatalogService.class.getDeclaredMethod("isAllowedItem", Item.class, Identifier.class);
        allowed.setAccessible(true);
        for (String namespace : new String[]{"bf_blockpack", "immersive_furniture", "urban_decor",
                "justoutdoorstuffs", "runiclib", "botania", "gangshats", "gangsmounts", "mythicmounts"}) {
            for (String path : new String[]{"chair", "crafting_material", "food", "copper_ingot"}) {
                assertEquals(namespace + ":" + path, Boolean.FALSE,
                    allowed.invoke(catalog, null, new Identifier(namespace, path)));
            }
        }
    }

            @Test
            @SuppressWarnings("unchecked")
            public void editorAppliesMovesPricesAndHiddenItemsWithoutAddingExcludedEntries() throws Exception {
                var edits = new CatalogEdits(folder.getRoot().toPath().resolve("editor.json"));
                edits.update(data -> {
                    data.categories.put("custom:test", new CatalogEdits.CategoryEdit("My Category", "minecraft:chest", false));
                    data.assignments.put("minecraft:stone", "custom:test");
                    data.prices.put("minecraft:stone", new CatalogEdits.Price(25, 7));
                    data.hidden.add("minecraft:dirt");
                    data.assignments.put("minecraft:bedrock", "custom:test");
                });
                var catalog = new CatalogService(new PriceConfigService());
                Field editsField = CatalogService.class.getDeclaredField("edits");
                editsField.setAccessible(true);
                editsField.set(catalog, edits);
                Field categoriesField = CatalogService.class.getDeclaredField("byCategory");
                categoriesField.setAccessible(true);
                Map<ShopCategory, List<ShopEntry>> categories =
                        (Map<ShopCategory, List<ShopEntry>>) categoriesField.get(catalog);
                categories.put(ShopCategory.STONE, new ArrayList<>());
                Field eligibleField = CatalogService.class.getDeclaredField("eligibleById");
                eligibleField.setAccessible(true);
                Map<Identifier, ShopEntry> eligible = (Map<Identifier, ShopEntry>) eligibleField.get(catalog);
                Identifier stone = new Identifier("minecraft:stone");
                Identifier dirt = new Identifier("minecraft:dirt");
                eligible.put(stone, new ShopEntry(stone, null, ShopCategory.STONE, 1, 0));
                eligible.put(dirt, new ShopEntry(dirt, null, ShopCategory.STONE, 1, 2));
                Method apply = CatalogService.class.getDeclaredMethod("applyEdits");
                apply.setAccessible(true);
                apply.invoke(catalog);
                assertEquals("My Category", catalog.getEntry(stone).category().getDisplayName());
                assertEquals(25, catalog.getEntry(stone).buyPrice());
                assertEquals(7, catalog.getEntry(stone).sellPrice());
                assertNull(catalog.getEntry(dirt));
                assertNull(catalog.getEntry(new Identifier("minecraft:bedrock")));
                assertThrows(IllegalArgumentException.class, () ->
                        catalog.editItem(new Identifier("minecraft:bedrock"), "My Category", 1, 1, true));
                assertThrows(IllegalArgumentException.class, () -> catalog.deleteCategory("My Category"));
            }

            @Test
            @SuppressWarnings("unchecked")
            public void zeroBuyPriceHasTemporaryPositivePrice() throws Exception {
                var catalog = new CatalogService(new PriceConfigService());
                Field editsField = CatalogService.class.getDeclaredField("edits");
                editsField.setAccessible(true);
                editsField.set(catalog, new CatalogEdits(folder.getRoot().toPath().resolve("editor.json")));
                Field eligibleField = CatalogService.class.getDeclaredField("eligibleById");
                eligibleField.setAccessible(true);
                Map<Identifier, ShopEntry> eligible = (Map<Identifier, ShopEntry>) eligibleField.get(catalog);
                Identifier stone = new Identifier("minecraft:stone");
                eligible.put(stone, new ShopEntry(stone, null, ShopCategory.STONE, 0, 0));
                Method apply = CatalogService.class.getDeclaredMethod("applyEdits");
                apply.setAccessible(true);
                apply.invoke(catalog);
                assertEquals(10000L, catalog.getEntry(stone).buyPrice());
                assertTrue(catalog.isBuyingEnabled(ShopCategory.STONE));
    }
}
