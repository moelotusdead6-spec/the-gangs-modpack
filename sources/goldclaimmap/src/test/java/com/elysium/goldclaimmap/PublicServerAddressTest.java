package com.elysium.goldclaimmap;

import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

public class PublicServerAddressTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    private static NbtCompound server(String address) {
        NbtCompound server = new NbtCompound();
        server.putString("name", "Personal name");
        server.putString("ip", address);
        server.putString("icon", "personal icon");
        server.putBoolean("acceptTextures", false);
        return server;
    }

    @Test
    public void migratesOldAddressAndPreservesEverythingElseWithBackup() throws Exception {
        Path game = temporary.newFolder().toPath();
        NbtCompound root = new NbtCompound();
        root.putString("custom", "preserved");
        NbtList servers = new NbtList();
        servers.add(server("other.example:25566"));
        servers.add(server("GANGS-ADVENTURE.PLAYIT.PLUS:25565"));
        root.put("servers", servers);
        Path saved = game.resolve("servers.dat");
        NbtIo.write(root, saved.toFile());
        byte[] original = Files.readAllBytes(saved);
        PublicServerAddress.update(game);
        NbtCompound migrated = NbtIo.read(saved.toFile());
        NbtList actual = migrated.getList("servers", 10);
        assertEquals(2, actual.size());
        assertEquals(servers.getCompound(0), actual.getCompound(0));
        NbtCompound expected = servers.getCompound(1).copy();
        expected.putString("ip", PublicServerAddress.ADDRESS);
        assertEquals(expected, actual.getCompound(1));
        assertEquals("preserved", migrated.getString("custom"));
        try (var files = Files.list(game)) {
            Path backup = files.filter(path -> path.toString().endsWith(".bak")).findFirst().orElseThrow();
            assertArrayEquals(original, Files.readAllBytes(backup));
        }
        PublicServerAddress.update(game);
        try (var files = Files.list(game)) {
            assertEquals(2, files.count());
        }
    }

    @Test
    public void addsMissingEntryWithoutReplacingOtherServers() throws Exception {
        NbtCompound root = new NbtCompound();
        NbtList servers = new NbtList();
        servers.add(server("another.example"));
        root.put("servers", servers);
        assertTrue(PublicServerAddress.migrate(root));
        assertEquals(2, servers.size());
        assertEquals("another.example", servers.getCompound(0).getString("ip"));
        assertEquals(PublicServerAddress.ADDRESS, servers.getCompound(1).getString("ip"));
        assertFalse(PublicServerAddress.migrate(root));
    }

    @Test
    public void createsFirstRunListAndRecognizesExistingNewAddress() throws Exception {
        Path game = temporary.newFolder().toPath();
        PublicServerAddress.update(game);
        NbtCompound root = NbtIo.read(game.resolve("servers.dat").toFile());
        assertEquals(PublicServerAddress.ADDRESS, root.getList("servers", 10).getCompound(0).getString("ip"));
        root.getList("servers", 10).getCompound(0).putString("ip", "TGMA.DEDIMC.IO:25565");
        assertFalse(PublicServerAddress.migrate(root));
    }

    @Test(expected = java.io.IOException.class)
    public void rejectsMalformedListRatherThanOverwritingIt() throws Exception {
        NbtCompound root = new NbtCompound();
        root.putString("servers", "not a list");
        PublicServerAddress.migrate(root);
    }
}
