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
    public void onlyTheExactFurnitureBinIsBanned() {
        assertTrue(BannedContent.isBanned(new Identifier("furniture:bin")));
        assertFalse(BannedContent.isBanned(new Identifier("furniture:trash_bag")));
        assertFalse(BannedContent.isBanned(new Identifier("furniture:oak_cabinet")));
        assertFalse(BannedContent.isBanned(new Identifier("another_furniture:bin")));
        assertFalse(BannedContent.isBanned((Identifier)null));
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
}
