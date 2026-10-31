package com.elysium.goldclaim;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import org.junit.Test;
import static org.junit.Assert.*;

public class WaystonePolicyRulesTest {
    @Test
    public void existingRswStonesArePreservedAlongsideActivatedWildStonesAndOtherWorlds() {
        UUID known = UUID.randomUUID();
        UUID unused = UUID.randomUUID();
        for (String dimension : GoldClaimMod.RSW_DIMENSIONS) {
            assertFalse(WaystonePolicyRules.shouldRemove(dimension, known, Set.of(known)));
            assertFalse(WaystonePolicyRules.shouldRemove(dimension, unused, Set.of(known)));
        }
        assertFalse(WaystonePolicyRules.shouldRemove("minecraft:overworld", known, Set.of(known)));
        assertTrue(WaystonePolicyRules.shouldRemove("minecraft:overworld", unused, Set.of(known)));
        assertFalse(WaystonePolicyRules.shouldRemove("multiworld:hub", unused, Set.of(known)));
        assertFalse(WaystonePolicyRules.shouldRemove("minecraft:the_end", unused, Set.of()));
    }

    @Test
    public void activationUnionIncludesOfflinePlayersAndDoesNotModifyPlayerData() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        var players = new NbtList();
        for (UUID id : new UUID[]{first, second, first}) {
            var player = new NbtCompound();
            var persistent = new NbtCompound();
            var data = new NbtCompound();
            var ids = new NbtList();
            ids.add(NbtString.of(id.toString()));
            data.put("Waystones", ids);
            persistent.put("WaystonesData", data);
            player.put("BalmData", persistent);
            players.add(player);
        }
        var original = players.copy();
        var activated = new HashSet<UUID>();
        WaystonePolicyRules.collectActivated(players, activated);
        assertEquals(Set.of(first, second), activated);
        assertEquals(original, players);
    }

    @Test(expected = IllegalArgumentException.class)
    public void malformedActivationDataMustAbortInsteadOfDeletingProtectedStones() {
        var root = new NbtCompound();
        var data = new NbtCompound();
        var ids = new NbtList();
        ids.add(NbtString.of("not-a-uuid"));
        data.put("Waystones", ids);
        root.put("WaystonesData", data);
        WaystonePolicyRules.collectActivated(root, new HashSet<>());
    }
}
