package com.gangs.gangshop.shop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

public class CatalogEditsTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void storesCategoryAssignmentAndPriceTogether() throws Exception {
        Path path = folder.getRoot().toPath().resolve("editor.json");
        CatalogEdits edits = new CatalogEdits(path);
        edits.update(data -> {
            data.categories.put("custom:one", new CatalogEdits.CategoryEdit("Custom", "minecraft:chest", false));
            data.assignments.put("minecraft:stone", "custom:one");
            data.prices.put("minecraft:stone", new CatalogEdits.Price(10000L, 3L));
        });
        CatalogEdits.State restored = new CatalogEdits(path).snapshot();
        assertEquals("Custom", restored.categories.get("custom:one").name);
        assertEquals("custom:one", restored.assignments.get("minecraft:stone"));
        assertEquals(10000L, restored.prices.get("minecraft:stone").buy);
    }

    @Test
    public void rejectedUpdateLeavesDiskAndMemoryUnchanged() throws Exception {
        Path path = folder.getRoot().toPath().resolve("editor.json");
        CatalogEdits edits = new CatalogEdits(path);
        edits.update(data -> data.hidden.add("minecraft:stone"));
        String original = Files.readString(path);
        assertThrows(IOException.class, () -> edits.update(data ->
                data.prices.put("minecraft:dirt", new CatalogEdits.Price(-1, 0))));
        assertEquals(original, Files.readString(path));
        assertTrue(edits.snapshot().hidden.contains("minecraft:stone"));
        assertTrue(edits.snapshot().prices.isEmpty());
    }

    @Test
    public void malformedFileFailsInsteadOfDiscardingSavedEdits() throws Exception {
        Path path = folder.getRoot().toPath().resolve("editor.json");
        Files.writeString(path, "{broken");
        assertThrows(IOException.class, () -> new CatalogEdits(path));
    }
}
