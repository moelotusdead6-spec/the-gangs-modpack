package com.elysium.goldclaim;

import com.elysium.goldclaim.maintenance.LegacyTeleportPurge;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import net.kyori.adventure.nbt.BinaryTagIO;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.BinaryTagTypes;
import net.kyori.adventure.nbt.ListBinaryTag;
import net.kyori.adventure.nbt.StringBinaryTag;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class LegacyTeleportPurgeTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void onlyVanillaNetherAndEndAreClosed() {
        for (String id : new String[]{"minecraft:the_nether", "minecraft:the_end"}) {
            assertTrue(LegacyDimensionPolicy.isClosed(id));
            assertTrue(LegacyTeleportPurge.closed(id));
        }
        for (String id : new String[]{"minecraft:overworld", "multiworld:rsw",
                "multiworld:rsw_nether", "multiworld:rsw_the_end", "gangs:hub"}) {
            assertFalse(LegacyDimensionPolicy.isClosed(id));
            assertFalse(LegacyTeleportPurge.closed(id));
        }
    }

    @Test public void homesWarpsAndBackAreSelective() {
        CompoundBinaryTag closed = location("minecraft:the_nether");
        CompoundBinaryTag allowed = location("multiworld:rsw_the_end");
        CompoundBinaryTag data = CompoundBinaryTag.builder()
            .put("homes", CompoundBinaryTag.builder().put("old-home", closed).put("rsw-home", allowed).build())
            .put("warps", CompoundBinaryTag.builder().put("old-warp", closed).put("rsw-warp", allowed).build())
            .put("previousLocation", closed).putString("nickname", "Preserve me").putInt("rtpCooldown", 42).build();
        var original = CompoundBinaryTag.builder().put("data", data).putInt("DataVersion", 3337).build();
        var result = LegacyTeleportPurge.cleanLocations(original);
        assertEquals(allowed, result.getCompound("data").getCompound("homes").getCompound("rsw-home"));
        assertNull(result.getCompound("data").getCompound("homes").get("old-home"));
        assertNull(result.getCompound("data").getCompound("warps").get("old-warp"));
        assertNull(result.getCompound("data").get("previousLocation"));
        assertEquals("Preserve me", result.getCompound("data").getString("nickname"));
        assertEquals(42, result.getCompound("data").getInt("rtpCooldown"));
        assertEquals(3337, result.getInt("DataVersion"));
        assertEquals(result, LegacyTeleportPurge.cleanLocations(result));
    }

    @Test public void olderListFormatHomesAreAlsoPurged() {
        var keep = location("minecraft:overworld").putString("homeName", "wild");
        var old = location("minecraft:the_end").putString("homeName", "old");
        var original = CompoundBinaryTag.builder().put("homes",
            ListBinaryTag.of(BinaryTagTypes.COMPOUND, java.util.List.of(old, keep))).build();
        var result = LegacyTeleportPurge.cleanLocations(original);
        assertEquals(1, result.getList("homes", BinaryTagTypes.COMPOUND).size());
        assertEquals(keep, result.getList("homes", BinaryTagTypes.COMPOUND).get(0));
    }

    @Test public void originalPlayerInventoryAndSavedLoginDimensionRemainUntouched() {
        CompoundBinaryTag original = CompoundBinaryTag.builder().putString("Dimension", "minecraft:the_end")
            .putString("SpawnDimension", "minecraft:the_nether").putInt("SpawnX", 100)
            .put("LastDeathLocation", CompoundBinaryTag.builder().putString("dimension", "minecraft:the_end").build())
            .putByteArray("InventoryFixture", new byte[]{1, 2, 3})
            .putInt("XpLevel", 30).putString("UnknownModData", "untouched").build();
        var result = LegacyTeleportPurge.cleanPlayer(original, Set.of());
        assertEquals("minecraft:the_end", result.getString("Dimension"));
        assertArrayEquals(original.getByteArray("InventoryFixture"), result.getByteArray("InventoryFixture"));
        assertEquals(30, result.getInt("XpLevel"));
        assertEquals("untouched", result.getString("UnknownModData"));
        assertNull(result.get("SpawnDimension"));
        assertNull(result.get("SpawnX"));
        assertNull(result.get("LastDeathLocation"));
    }

    @Test public void jsonHomesAndReturnsPreserveAllowedDimensions() {
        var homes = JsonParser.parseString("[{\"worldId\":\"minecraft:the_end\",\"name\":\"old\"},"
            + "{\"worldId\":\"minecraft:overworld\",\"name\":\"wild\"},"
            + "{\"worldId\":\"multiworld:rsw_nether\",\"name\":\"rsw\"}]");
        LegacyTeleportPurge.cleanJson(homes);
        assertEquals(2, homes.getAsJsonArray().size());
        assertEquals("wild", homes.getAsJsonArray().get(0).getAsJsonObject().get("name").getAsString());
        var returns = JsonParser.parseString("{\"old\":{\"worldId\":\"minecraft:the_nether\"},"
            + "\"allowed\":{\"worldId\":\"minecraft:overworld\"},\"other\":42}");
        LegacyTeleportPurge.cleanJson(returns);
        assertFalse(returns.getAsJsonObject().has("old"));
        assertTrue(returns.getAsJsonObject().has("allowed"));
        assertEquals(42, returns.getAsJsonObject().get("other").getAsInt());
    }

    @Test public void offlinePurgeBacksUpThenVerifiesAndIsIdempotent() throws Exception {
        Path game = temporary.newFolder("server").toPath();
        Files.writeString(game.resolve("server.properties"), "level-name=world\n");
        Path player = game.resolve("world").resolve("modplayerdata").resolve("offline.dat");
        Files.createDirectories(player.getParent());
        var original = CompoundBinaryTag.builder().put("data", CompoundBinaryTag.builder()
            .put("homes", CompoundBinaryTag.builder().put("old", location("minecraft:the_end"))
                .put("wild", location("minecraft:overworld")).build()).putString("nickname", "Keep me").build()).build();
        BinaryTagIO.writer().write(original, player, BinaryTagIO.Compression.GZIP);
        byte[] before = Files.readAllBytes(player);
        LegacyTeleportPurge.main(new String[]{"--dry-run", game.toString()});
        assertArrayEquals(before, Files.readAllBytes(player));
        Path backup = game.resolve("backups").resolve("policy-v1.1.7-test");
        LegacyTeleportPurge.main(new String[]{"--apply", game.toString(), backup.toString()});
        assertArrayEquals(before, Files.readAllBytes(backup.resolve(game.relativize(player))));
        assertEquals(LegacyTeleportPurge.cleanLocations(original),
            BinaryTagIO.reader().read(player, BinaryTagIO.Compression.GZIP));
        byte[] after = Files.readAllBytes(player);
        LegacyTeleportPurge.main(new String[]{"--apply", game.toString(), backup.toString()});
        assertArrayEquals(after, Files.readAllBytes(player));
    }

    @Test public void registryAndOfflineActivatedListsArePurgedTogether() throws Exception {
        Path game = temporary.newFolder("waystones-server").toPath();
        Files.writeString(game.resolve("server.properties"), "level-name=world\n");
        Path registry = game.resolve("world").resolve("data").resolve("waystones.dat");
        Files.createDirectories(registry.getParent());
        String oldId = "12345678-1234-5678-1234-567812345678";
        String keepId = "87654321-4321-8765-4321-876543218765";
        CompoundBinaryTag oldStone = CompoundBinaryTag.builder().putString("World", "minecraft:the_end")
            .putString("WaystoneUid", oldId).put("BlockPos", CompoundBinaryTag.builder()
                .putInt("X", 10).putInt("Y", 100).putInt("Z", 10).build()).build();
        CompoundBinaryTag keepStone = oldStone.putString("World", "minecraft:overworld").putString("WaystoneUid", keepId);
        CompoundBinaryTag originalRegistry = CompoundBinaryTag.builder().putInt("DataVersion", 3337)
            .put("data", CompoundBinaryTag.builder().put("Waystones",
                ListBinaryTag.of(BinaryTagTypes.COMPOUND, java.util.List.of(oldStone, keepStone))).build()).build();
        BinaryTagIO.writer().write(originalRegistry, registry, BinaryTagIO.Compression.GZIP);
        Path player = game.resolve("world").resolve("playerdata").resolve("offline.dat");
        Files.createDirectories(player.getParent());
        CompoundBinaryTag originalPlayer = CompoundBinaryTag.builder().putInt("XpLevel", 20)
            .put("BalmData", CompoundBinaryTag.builder().put("WaystonesData", CompoundBinaryTag.builder()
                .putInt("Cooldown", 123).put("Waystones", ListBinaryTag.of(BinaryTagTypes.STRING,
                    java.util.List.of(StringBinaryTag.stringBinaryTag(oldId), StringBinaryTag.stringBinaryTag(keepId))))
                .build()).build()).build();
        BinaryTagIO.writer().write(originalPlayer, player, BinaryTagIO.Compression.GZIP);
        Path region = game.resolve("world").resolve("DIM1").resolve("region").resolve("r.0.0.mca");
        Files.createDirectories(region.getParent());
        byte[] regionData = new byte[12288];
        regionData[2] = 2;
        regionData[3] = 1;
        Files.write(region, regionData);
        Path backup = game.resolve("backups").resolve("policy-v1.1.7-test");
        LegacyTeleportPurge.main(new String[]{"--apply", game.toString(), backup.toString()});
        var result = BinaryTagIO.reader().read(player, BinaryTagIO.Compression.GZIP);
        assertEquals(20, result.getInt("XpLevel"));
        var data = result.getCompound("BalmData").getCompound("WaystonesData");
        assertEquals(123, data.getInt("Cooldown"));
        assertEquals(java.util.List.of(StringBinaryTag.stringBinaryTag(keepId)),
            data.getList("Waystones", BinaryTagTypes.STRING).stream().toList());
        var stones = BinaryTagIO.reader().read(registry, BinaryTagIO.Compression.GZIP)
            .getCompound("data").getList("Waystones", BinaryTagTypes.COMPOUND);
        assertEquals(1, stones.size());
        assertEquals(keepStone, stones.get(0));
        assertArrayEquals(regionData, Files.readAllBytes(backup.resolve(game.relativize(region))));
        byte[] after = Files.readAllBytes(player);
        LegacyTeleportPurge.main(new String[]{"--apply", game.toString(), backup.toString()});
        assertArrayEquals(after, Files.readAllBytes(player));
    }

    private static CompoundBinaryTag location(String dimension) {
        return CompoundBinaryTag.builder().putString("WorldRegistryKey", dimension)
            .putDouble("x", 12.5).putDouble("y", 70).putDouble("z", 32.5).build();
    }
}
