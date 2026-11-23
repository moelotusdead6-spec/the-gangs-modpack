package com.gangs.gangsgems;

import static org.junit.Assert.*;

import java.nio.file.Files;
import java.time.Instant;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class KnownPlayerLookupTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void resolvesOnlyUnexpiredUnambiguousCachedNames() throws Exception {
        var path = folder.getRoot().toPath().resolve("usercache.json");
        UUID player = UUID.randomUUID();
        Files.writeString(path, "[{\"name\":\"GiftPlayer\",\"uuid\":\"" + player
            + "\",\"expiresOn\":\"2099-12-01 12:00:00 +0000\"}]");
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        assertEquals(player, KnownPlayerLookup.find(path, "giftplayer", now));
        assertNull(KnownPlayerLookup.find(path, "OtherPlayer", now));
        assertNull(KnownPlayerLookup.find(path, "GiftPlayer", Instant.parse("2100-01-01T00:00:00Z")));
        Files.writeString(path, "[{\"name\":\"GiftPlayer\",\"uuid\":\"" + player
            + "\",\"expiresOn\":\"2099-12-01 12:00:00 +0000\"},"
            + "{\"name\":\"giftplayer\",\"uuid\":\"" + UUID.randomUUID()
            + "\",\"expiresOn\":\"2099-12-01 12:00:00 +0000\"}]");
        assertNull(KnownPlayerLookup.find(path, "GiftPlayer", now));
    }

    @Test
    public void malformedCacheIsNotTreatedAsAnUnknownPlayer() throws Exception {
        var path = folder.getRoot().toPath().resolve("usercache.json");
        Files.writeString(path, "[{\"name\":\"GiftPlayer\",\"uuid\":\"not-a-uuid\"}]");
        assertThrows(java.io.IOException.class, () -> KnownPlayerLookup.find(path, "GiftPlayer", Instant.now()));
    }
}
