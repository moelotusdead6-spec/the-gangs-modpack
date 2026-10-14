package com.elysium.goldclaim;

import com.elysium.goldclaim.maintenance.PlayerRecovery;
import com.elysium.goldclaim.maintenance.PlayerRecovery.Pick;
import com.elysium.goldclaim.maintenance.PlayerRecovery.Plan;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.nbt.BinaryTag;
import net.kyori.adventure.nbt.BinaryTagTypes;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class PlayerRecoveryTest {
    private static final String UUID = "07337021-ea67-4593-b127-73f4261b1134";
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private static int[] uuidInts() {
        var id = java.util.UUID.fromString(UUID);
        return new int[]{(int) (id.getMostSignificantBits() >> 32), (int) id.getMostSignificantBits(),
            (int) (id.getLeastSignificantBits() >> 32), (int) id.getLeastSignificantBits()};
    }

    private static CompoundBinaryTag stack(int slot, String id, int[] backpack) {
        var builder = CompoundBinaryTag.builder().putByte("Slot", (byte) slot).putString("id", id).putByte("Count", (byte) 1);
        if (backpack != null) builder.put("tag", CompoundBinaryTag.builder().putIntArray("contentsUuid", backpack)
            .putInt("inventorySlots", 120).build());
        else builder.put("tag", CompoundBinaryTag.builder().putInt("Damage", slot).build());
        return builder.build();
    }

    private static CompoundBinaryTag player(List<CompoundBinaryTag> inventory, List<CompoundBinaryTag> ender,
            CompoundBinaryTag trinket) {
        var trinkets = CompoundBinaryTag.builder().put("chest", CompoundBinaryTag.builder().put("back",
            CompoundBinaryTag.builder().put("Items", ListBinaryTag.of(BinaryTagTypes.COMPOUND,
                trinket == null ? List.of() : List.of(trinket))).build()).build()).build();
        return CompoundBinaryTag.builder().putIntArray("UUID", uuidInts()).putInt("XpLevel", 30)
            .putString("Dimension", "multiworld:rsw_the_end")
            .put("Inventory", ListBinaryTag.of(BinaryTagTypes.COMPOUND, new ArrayList<BinaryTag>(inventory)))
            .put("EnderItems", ListBinaryTag.of(BinaryTagTypes.COMPOUND, new ArrayList<BinaryTag>(ender)))
            .put("cardinal_components", CompoundBinaryTag.builder().put("trinkets:trinkets", trinkets).build())
            .putString("UnknownModData", "untouched").build();
    }

    private Path server(CompoundBinaryTag current, byte[] snapshot) throws Exception {
        Path game = temporary.newFolder("server").toPath();
        Files.writeString(game.resolve("server.properties"), "level-name=world\n");
        Files.createDirectories(game.resolve("world/playerdata"));
        Files.write(game.resolve("world/playerdata/" + UUID + ".dat"), PlayerRecovery.write(current));
        Path source = game.resolve("backups/policy-v1.1.7-20261013-195936-1202/world/playerdata/" + UUID + ".dat");
        Files.createDirectories(source.getParent());
        Files.write(source, snapshot);
        return game;
    }

    @Test public void restoresOnlyPlannedStacksIntoFreeEnderSlotsAndIsIdempotent() throws Exception {
        var snapshot = player(List.of(stack(1, "gobber2:sword", null), stack(2, "gobber2:kept", null),
            stack(10, "sophisticatedbackpacks:netherite_backpack", new int[]{1, 2, 3, 4}), stack(103, "gobber2:helmet", null)),
            List.of(), stack(0, "sophisticatedbackpacks:netherite_backpack", new int[]{5, 6, 7, 8}).remove("Slot"));
        byte[] snapshotBytes = PlayerRecovery.write(snapshot);
        var current = player(List.of(stack(0, "minecraft:dirt", null)),
            List.of(stack(0, "minecraft:stone", null), stack(1, "minecraft:stone", null)), null);
        Path game = server(current, snapshotBytes);
        Plan plan = new Plan("Fixture", UUID, PlayerRecovery.sha256(snapshotBytes), "fixture.done", List.of(
            new Pick(null, null, 1, "gobber2:sword"), new Pick(null, null, 10, "sophisticatedbackpacks:netherite_backpack"),
            new Pick(null, null, 103, "gobber2:helmet"), new Pick("chest", "back", 0, "sophisticatedbackpacks:netherite_backpack")));
        Path backup = game.resolve("backups/policy-v1.1.8-20261014-000000-0000");
        Files.createDirectories(backup);

        assertEquals(0, PlayerRecovery.run(plan, game, null));
        assertEquals(current, PlayerRecovery.read(Files.readAllBytes(game.resolve("world/playerdata/" + UUID + ".dat"))));
        assertEquals(0, PlayerRecovery.run(plan, game, backup));
        var result = PlayerRecovery.read(Files.readAllBytes(game.resolve("world/playerdata/" + UUID + ".dat")));
        assertTrue(PlayerRecovery.onlyContainerChanged(current, result));
        var ender = result.getList("EnderItems", BinaryTagTypes.COMPOUND);
        assertEquals(3, ender.size());
        var box = ender.getCompound(2);
        assertEquals(2, box.getByte("Slot"));
        assertEquals("minecraft:shulker_box", box.getString("id"));
        var items = box.getCompound("tag").getCompound("BlockEntityTag").getList("Items", BinaryTagTypes.COMPOUND);
        assertEquals(4, items.size());
        assertEquals("gobber2:sword", items.getCompound(0).getString("id"));
        assertEquals(1, items.getCompound(0).getCompound("tag").getInt("Damage"));
        assertEquals(snapshot.getList("Inventory", BinaryTagTypes.COMPOUND).getCompound(2).getCompound("tag"),
            items.getCompound(1).getCompound("tag"));
        assertTrue(Files.exists(game.resolve("world/goldclaim-recovery/fixture.done")));
        assertTrue(Files.exists(backup.resolve("player-recovery/" + UUID + ".dat")));
        assertArrayEquals(snapshotBytes, Files.readAllBytes(backup.resolve("player-recovery/" + UUID + ".source.dat")));

        byte[] after = Files.readAllBytes(game.resolve("world/playerdata/" + UUID + ".dat"));
        assertEquals(0, PlayerRecovery.run(plan, game, backup));
        assertArrayEquals(after, Files.readAllBytes(game.resolve("world/playerdata/" + UUID + ".dat")));
        Files.delete(game.resolve("world/goldclaim-recovery/fixture.done"));
        assertEquals(0, PlayerRecovery.run(plan, game, backup));
        assertArrayEquals("Existing recovery boxes must prevent a second delivery.", after,
            Files.readAllBytes(game.resolve("world/playerdata/" + UUID + ".dat")));
    }

    @Test public void skipsBackpacksHeldByAnotherPlayerAndRejectsWrongSnapshot() throws Exception {
        var snapshot = player(List.of(stack(10, "sophisticatedbackpacks:netherite_backpack", new int[]{9, 9, 9, 9}),
            stack(11, "gobber2:ring", null)), List.of(), null);
        byte[] snapshotBytes = PlayerRecovery.write(snapshot);
        var current = player(List.of(), List.of(), null);
        Path game = server(current, snapshotBytes);
        var other = player(List.of(stack(0, "sophisticatedbackpacks:netherite_backpack", new int[]{9, 9, 9, 9})), List.of(), null);
        Files.write(game.resolve("world/playerdata/00000000-0000-0000-0000-000000000001.dat"), PlayerRecovery.write(other));
        Plan plan = new Plan("Fixture", UUID, PlayerRecovery.sha256(snapshotBytes), "fixture.done", List.of(
            new Pick(null, null, 10, "sophisticatedbackpacks:netherite_backpack"), new Pick(null, null, 11, "gobber2:ring")));
        Path backup = game.resolve("backups/policy-v1.1.8-20261014-000000-0000");
        Files.createDirectories(backup);
        assertEquals(0, PlayerRecovery.run(plan, game, backup));
        var box = PlayerRecovery.read(Files.readAllBytes(game.resolve("world/playerdata/" + UUID + ".dat")))
            .getList("EnderItems", BinaryTagTypes.COMPOUND).getCompound(0);
        var items = box.getCompound("tag").getCompound("BlockEntityTag").getList("Items", BinaryTagTypes.COMPOUND);
        assertEquals(1, items.size());
        assertEquals("gobber2:ring", items.getCompound(0).getString("id"));

        Plan wrongHash = new Plan("Other", UUID, "00", "other.done", plan.picks());
        assertEquals(PlayerRecovery.EXIT_SOURCE_MISSING, PlayerRecovery.run(wrongHash, game, backup));
        assertFalse(Files.exists(game.resolve("world/goldclaim-recovery/other.done")));
    }

    @Test public void fullEnderChestFallsBackToInventoryThenRefuses() {
        List<CompoundBinaryTag> full = new ArrayList<>();
        for (int i = 0; i < 27; i++) full.add(stack(i, "minecraft:stone", null));
        var boxes = PlayerRecovery.boxes(List.of(stack(0, "a:b", null), stack(1, "a:c", null)), "Fixture recovery ");
        var placed = PlayerRecovery.place(player(List.of(), full, null), boxes);
        assertEquals(1, placed.getList("Inventory", BinaryTagTypes.COMPOUND).size());
        List<CompoundBinaryTag> fullInventory = new ArrayList<>();
        for (int i = 0; i < 36; i++) fullInventory.add(stack(i, "minecraft:stone", null));
        assertNull(PlayerRecovery.place(player(fullInventory, full, null), boxes));
    }

    @Test public void twentyEightStacksSplitIntoTwoBalancedBoxes() {
        List<CompoundBinaryTag> items = new ArrayList<>();
        for (int i = 0; i < 28; i++) items.add(stack(i, "a:" + i, null).remove("Slot"));
        var boxes = PlayerRecovery.boxes(items, "Fixture recovery ");
        assertEquals(2, boxes.size());
        assertEquals(14, boxes.get(0).getCompound("tag").getCompound("BlockEntityTag").getList("Items").size());
        assertEquals(14, boxes.get(1).getCompound("tag").getCompound("BlockEntityTag").getList("Items").size());
        assertTrue(boxes.get(1).getCompound("tag").getCompound("display").getString("Name").contains("Fixture recovery 2/2"));
        assertEquals(28, PlayerRecovery.KUNOISBACK.picks().size());
    }
}
