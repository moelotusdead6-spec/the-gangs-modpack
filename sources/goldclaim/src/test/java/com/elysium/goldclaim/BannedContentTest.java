package com.elysium.goldclaim;

import com.google.gson.JsonParser;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.Identifier;
import org.junit.Test;
import static org.junit.Assert.*;

public class BannedContentTest {
    private static NbtCompound stack(String item) {
        NbtCompound stack = new NbtCompound();
        stack.putString("id", item);
        stack.putByte("Count", (byte)1);
        return stack;
    }

    @Test
    public void onlyTheExactRestrictedItemsAreBanned() {
        assertTrue(BannedContent.isBanned(new Identifier("furniture:bin")));
        assertTrue(BannedContent.isBanned(new Identifier(BannedContent.INCEPTION_UPGRADE)));
        assertTrue(BannedContent.isBanned(new Identifier(BannedContent.EARTHDIVE_SPEAR)));
        assertTrue(BannedContent.isBanned(new Identifier(BannedContent.WITHERED_WABBAJACK)));
        assertFalse(BannedContent.isBanned(new Identifier("soulsweapons:moonlight_greatsword")));
        assertFalse(BannedContent.isBanned(new Identifier("another_mod:withered_wabbajack")));
        assertFalse(BannedContent.isBanned(new Identifier("bosses_of_mass_destruction:blazing_eye")));
        assertFalse(BannedContent.isBanned(new Identifier("sophisticatedbackpacks:backpack")));
        assertFalse(BannedContent.isBanned(new Identifier("sophisticatedbackpacks:stack_upgrade_tier_4")));
        assertFalse(BannedContent.isBanned(new Identifier("furniture:trash_bag")));
        assertFalse(BannedContent.isBanned(new Identifier("furniture:oak_cabinet")));
        assertFalse(BannedContent.isBanned(new Identifier("another_furniture:bin")));
        assertFalse(BannedContent.isBanned((Identifier)null));
    }

    @Test
    public void wabbajackRecipesAndNestedStacksAreRemovedWithoutRemovingOtherWeapons() {
        for (String result : new String[]{"\"" + BannedContent.WITHERED_WABBAJACK + "\"",
                "{\"item\":\"" + BannedContent.WITHERED_WABBAJACK + "\"}",
                "{\"id\":\"" + BannedContent.WITHERED_WABBAJACK + "\"}"}) {
            assertTrue(BannedContent.isBannedRecipe(JsonParser.parseString("{\"result\":" + result + "}")));
        }
        assertFalse(BannedContent.isBannedRecipe(JsonParser.parseString(
            "{\"result\":{\"item\":\"soulsweapons:moonlight_greatsword\"}}")));
        NbtCompound backpack = stack("sophisticatedbackpacks:backpack");
        NbtCompound tag = new NbtCompound();
        NbtList contents = new NbtList();
        contents.add(stack(BannedContent.WITHERED_WABBAJACK));
        contents.add(stack("soulsweapons:moonlight_greatsword"));
        tag.put("Items", contents);
        backpack.put("tag", tag);
        assertEquals(1, BannedContent.sanitize(backpack));
        assertEquals(1, contents.size());
        assertEquals("soulsweapons:moonlight_greatsword", contents.getCompound(0).getString("id"));
        NbtCompound held = stack(BannedContent.WITHERED_WABBAJACK);
        assertEquals(1, BannedContent.sanitize(held));
        assertEquals("minecraft:air", held.getString("id"));
        assertEquals(0, held.getByte("Count"));
    }

    @Test
    public void earthdiveRecipesAndNestedSavedStacksAreRemovedWithoutTouchingOtherLoot() {
        for (String result : new String[]{"\"" + BannedContent.EARTHDIVE_SPEAR + "\"",
                "{\"item\":\"" + BannedContent.EARTHDIVE_SPEAR + "\"}",
                "{\"id\":\"" + BannedContent.EARTHDIVE_SPEAR + "\"}"}) {
            assertTrue(BannedContent.isBannedRecipe(JsonParser.parseString("{\"result\":" + result + "}")));
        }
        var items = new NbtList();
        items.add(stack(BannedContent.EARTHDIVE_SPEAR));
        items.add(stack("minecraft:diamond"));
        assertEquals(1, BannedContent.sanitize(items));
        assertEquals(1, items.size());
        assertEquals("minecraft:diamond", items.getCompound(0).getString("id"));
    }

    @Test
    public void allStandardRecipeOutputShapesAreCoveredWithoutRemovingOtherRecipes() {
        for (String result : new String[]{"\"furniture:bin\"", "{\"item\":\"furniture:bin\",\"count\":2}",
                "{\"id\":\"furniture:bin\"}"}) {
            assertTrue(BannedContent.isBannedRecipe(JsonParser.parseString("{\"result\":" + result + "}")));
        }
        assertFalse(BannedContent.isBannedRecipe(JsonParser.parseString("{\"result\":{\"item\":\"furniture:trash_bag\"}}")));
        assertFalse(BannedContent.isBannedRecipe(JsonParser.parseString("{\"result\":{\"count\":2}}")));
        assertFalse(BannedContent.isBannedRecipe(JsonParser.parseString("{}")));
        for (String result : new String[]{"\"" + BannedContent.INCEPTION_UPGRADE + "\"",
                "{\"item\":\"" + BannedContent.INCEPTION_UPGRADE + "\"}",
                "{\"id\":\"" + BannedContent.INCEPTION_UPGRADE + "\"}"}) {
            assertTrue(BannedContent.isBannedRecipe(JsonParser.parseString("{\"result\":" + result + "}")));
        }
    }

    @Test
    public void nestedContainersLoseBinsButRetainSlotsAndOtherItemData() {
        NbtCompound chest = new NbtCompound();
        NbtList items = new NbtList();
        NbtCompound bin = stack("furniture:bin");
        bin.putByte("Slot", (byte)0);
        items.add(bin);
        NbtCompound diamond = stack("minecraft:diamond");
        diamond.putByte("Slot", (byte)1);
        items.add(diamond);
        NbtCompound shulker = stack("minecraft:shulker_box");
        NbtCompound tag = new NbtCompound();
        NbtCompound blockEntity = new NbtCompound();
        NbtList nested = new NbtList();
        nested.add(stack("furniture:bin"));
        nested.add(stack("furniture:oak_cabinet"));
        blockEntity.put("Items", nested);
        tag.put("BlockEntityTag", blockEntity);
        tag.putString("CustomName", "Keep this name");
        shulker.put("tag", tag);
        items.add(shulker);
        chest.put("Items", items);
        assertEquals(2, BannedContent.sanitize(chest));
        assertEquals(2, items.size());
        assertEquals(diamond, items.getCompound(0));
        assertEquals(1, diamond.getByte("Slot"));
        assertEquals(1, nested.size());
        assertEquals("furniture:oak_cabinet", nested.getCompound(0).getString("id"));
        assertEquals("Keep this name", tag.getString("CustomName"));
        assertEquals(0, BannedContent.sanitize(chest));
    }

    @Test
    public void directStackIsEmptiedButNonItemMetadataIsNotRemoved() {
        NbtCompound bin = stack("furniture:bin");
        assertEquals(1, BannedContent.sanitize(bin));
        assertEquals("minecraft:air", bin.getString("id"));
        assertEquals(0, bin.getByte("Count"));
        NbtCompound metadata = new NbtCompound();
        metadata.putString("id", "furniture:bin");
        assertEquals(0, BannedContent.sanitize(metadata));
        assertEquals("furniture:bin", metadata.getString("id"));
    }

    @Test
    public void installedInceptionIsRemovedWithoutDeletingBackpackContents() {
        NbtCompound contents = new NbtCompound();
        NbtList upgrades = new NbtList();
        upgrades.add(stack(BannedContent.INCEPTION_UPGRADE));
        upgrades.add(stack("sophisticatedbackpacks:stack_upgrade_tier_4"));
        NbtList inventory = new NbtList();
        NbtCompound backpack = stack("sophisticatedbackpacks:diamond_backpack");
        backpack.putString("contentsUuid", "preserve-backpack-reference");
        inventory.add(backpack);
        inventory.add(stack("minecraft:diamond"));
        contents.put("upgrades", upgrades);
        contents.put("inventory", inventory);
        assertEquals(1, BannedContent.sanitize(contents));
        assertEquals(1, upgrades.size());
        assertEquals("sophisticatedbackpacks:stack_upgrade_tier_4", upgrades.getCompound(0).getString("id"));
        assertEquals(2, inventory.size());
        assertEquals("preserve-backpack-reference", inventory.getCompound(0).getString("contentsUuid"));
        assertEquals(0, BannedContent.sanitize(contents));
    }
}
