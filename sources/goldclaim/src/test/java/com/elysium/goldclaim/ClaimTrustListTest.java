package com.elysium.goldclaim;

import com.elysium.goldclaim.data.Claim;
import java.util.List;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class ClaimTrustListTest {
    @Test
    public void resolvesNameAndUuidOnceAtTheHighestPermission() {
        Claim claim = new Claim(UUID.randomUUID(), "minecraft:overworld", 0, 10, 0, 10);
        UUID player = UUID.randomUUID();
        claim.addInteract(player);
        claim.addInteractName("PlayerOne");
        claim.addTrusted(player);
        claim.addTrustedName("PlayerOne");
        claim.addManager(player);
        claim.addManagerName("PlayerOne");

        List<ClaimTrustList.Entry> entries = ClaimTrustList.entries(claim, id -> "PlayerOne");

        assertEquals(1, entries.size());
        assertEquals("PlayerOne (" + player + ")", entries.get(0).display());
        assertEquals(Claim.TrustLevel.MANAGER, entries.get(0).level());
        assertFalse(entries.get(0).unresolved());
    }

    @Test
    public void reportsEffectivePublicPermissionsAndNameOnlyGrants() {
        Claim claim = new Claim(UUID.randomUUID(), "minecraft:overworld", 0, 10, 0, 10);
        UUID player = UUID.randomUUID();
        claim.addInteract(player);
        claim.addInteractName("Visitor");
        claim.addTrustedName("LegacyUser");
        claim.setPublicTrust(Claim.TrustLevel.FULL, true);

        List<ClaimTrustList.Entry> entries = ClaimTrustList.entries(claim, id -> "Visitor");

        assertEquals(Claim.TrustLevel.FULL, ClaimTrustList.publicLevel(claim));
        assertEquals(2, entries.size());
        assertTrue(entries.stream().allMatch(entry -> entry.level() == Claim.TrustLevel.FULL));
        assertTrue(entries.stream().anyMatch(entry -> entry.display().equals("legacyuser")));
        assertTrue(entries.stream().anyMatch(entry -> entry.display().equals("Visitor (" + player + ")")));
    }

    @Test
    public void keepsUnresolvedLegacyGrantsVisibleWithoutGuessingIdentity() {
        Claim claim = new Claim(UUID.randomUUID(), "minecraft:overworld", 0, 10, 0, 10);
        UUID player = UUID.randomUUID();
        claim.addInteract(player);
        claim.addInteractName("OfflinePlayer");
        claim.managerPlayers.add("not-a-uuid");

        List<ClaimTrustList.Entry> entries = ClaimTrustList.entries(claim, id -> null);

        assertEquals(3, entries.size());
        assertTrue(entries.stream().anyMatch(entry -> entry.display().equals(player.toString()) && entry.unresolved()));
        assertTrue(entries.stream().anyMatch(entry -> entry.display().equals("offlineplayer")));
        assertTrue(entries.stream().anyMatch(entry -> entry.display().contains("invalid UUID") && entry.unresolved()));
    }

    @Test
    public void marksNonCanonicalGrantsInactiveInsteadOfInventingPermissions() {
        Claim claim = new Claim(UUID.randomUUID(), "minecraft:overworld", 0, 10, 0, 10);
        claim.trustedPlayers.add("1-1-1-1-1");
        claim.managerPlayerNames.add("MixedCase");

        List<ClaimTrustList.Entry> entries = ClaimTrustList.entries(claim, id -> "ShouldNotResolve");

        assertEquals(2, entries.size());
        assertTrue(entries.stream().allMatch(entry -> entry.unresolved() && entry.display().contains("inactive")));
        assertTrue(entries.stream().noneMatch(entry -> entry.display().contains("ShouldNotResolve")));
    }
}
