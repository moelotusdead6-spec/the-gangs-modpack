package com.gangs.gangsgems;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class FurnitureConfigurationTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void defaultsOffAndRequiresExplicitBoolean() throws Exception {
        var config = folder.getRoot().toPath().resolve("gangsgems-furniture.json");
        assertFalse(FurnitureConfiguration.enabled(config));
        Files.writeString(config, "{\"enabled\":false}");
        assertFalse(FurnitureConfiguration.enabled(config));
        Files.writeString(config, "{\"enabled\":true}");
        assertTrue(FurnitureConfiguration.enabled(config));
        Files.writeString(config, "{\"enabled\":\"true\"}");
        assertThrows(IOException.class, () -> FurnitureConfiguration.enabled(config));
        Files.writeString(config, "{");
        assertThrows(IOException.class, () -> FurnitureConfiguration.enabled(config));
    }
}
