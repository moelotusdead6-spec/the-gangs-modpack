package com.gangs.gangshop.shop;

import java.nio.file.Files;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class ShopAccessTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void restrictsShopToOperatorsAndConfiguredTesters() throws Exception {
        UUID tester = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        var path = folder.getRoot().toPath().resolve("access.json");
        Files.writeString(path, "{\"restricted\":true,\"testers\":[\"" + tester + "\"]}");
        var access = new ShopAccess();
        access.load(path);
        assertTrue(access.allows(true, other));
        assertTrue(access.allows(false, tester));
        assertFalse(access.allows(false, other));

        Files.writeString(path, "{\"restricted\":false,\"testers\":[]}");
        access.load(path);
        assertTrue(access.allows(false, other));
    }

    @Test
    public void missingConfigurationFailsClosedAndMalformedConfigurationFailsExplicitly() throws Exception {
        var path = folder.getRoot().toPath().resolve("access.json");
        var access = new ShopAccess();
        access.load(path);
        assertFalse(access.allows(false, UUID.randomUUID()));
        Files.writeString(path, "{\"restricted\":\"false\",\"testers\":[]}");
        assertThrows(IllegalStateException.class, () -> access.load(path));
        Files.writeString(path, "{\"restricted\":true,\"testers\":[\"invalid\"]}");
        assertThrows(IllegalStateException.class, () -> access.load(path));
    }
}
