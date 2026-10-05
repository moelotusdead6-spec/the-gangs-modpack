package com.elysium.goldclaim;

import com.elysium.goldclaim.data.Claim;
import com.google.gson.Gson;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class GameplayPolicyTest {
    @Test
    public void resetRecoveryRejectsUnknownPhasesAndUnsafeBackupPaths() {
        RswResetService.State state = new RswResetService.State();
        RswResetService.validate(state);
        state.phase = "ARCHIVING";
        state.backup = "../other-world";
        assertThrows(IllegalArgumentException.class, () -> RswResetService.validate(state));
        state.backup = "reset-12345";
        RswResetService.validate(state);
        state.phase = "UNRECOGNIZED";
        assertThrows(IllegalArgumentException.class, () -> RswResetService.validate(state));
    }

    @Test
    public void rswAlwaysResetsAtTenUtcIncludingDaylightSavingDates() {
        for (String date : new String[]{"2026-03-08", "2026-10-05", "2026-11-01"}) {
            long deadline = java.time.Instant.parse(date + "T10:00:00Z").toEpochMilli();
            assertEquals(deadline, RswSchedule.nextReset(deadline - 1));
            assertEquals(deadline + 86400000L, RswSchedule.nextReset(deadline));
            assertEquals(deadline, RswSchedule.latestReset(deadline));
            assertArrayEquals(new int[]{900, 600, 300, 240, 180, 120, 60}, RswSchedule.WARNING_SECONDS);
        }
    }

    @Test
    public void kitsUnlockAtTheirConfirmedDaysAndResetWithoutOfflineDrift() {
        for (int kit = 0; kit < 4; kit++) {
            long threshold = kit * 7L * 86400L;
            assertFalse(KitService.eligible(kit, threshold - 1, false));
            assertTrue(KitService.eligible(kit, threshold, false));
            assertFalse(KitService.eligible(kit, threshold, true));
        }
        long start = 1700000000L;
        assertEquals(start, KitService.cycleStart(start, start + KitService.CYCLE_SECONDS - 1));
        assertEquals(start + KitService.CYCLE_SECONDS, KitService.cycleStart(start, start + KitService.CYCLE_SECONDS));
        assertEquals(start + 3 * KitService.CYCLE_SECONDS, KitService.cycleStart(start, start + 3 * KitService.CYCLE_SECONDS + 123));
    }

    @Test
    public void publicTrustCoversFuturePlayersWithoutGivingManagement() {
        Claim claim = new Claim(UUID.randomUUID(), "minecraft:overworld", 0, 10, 0, 10);
        claim.setPublicTrust(Claim.TrustLevel.INTERACT, true);
        UUID visitor = UUID.randomUUID();
        assertTrue(claim.canInteract(visitor));
        assertFalse(claim.isTrusted(visitor));
        assertFalse(claim.hasFullControl(visitor));
        claim.setPublicTrust(Claim.TrustLevel.FULL, true);
        assertTrue(claim.isTrusted(UUID.randomUUID()));
        assertFalse(claim.hasFullControl(visitor));
    }

    @Test
    public void managerAllCanBeRevokedWithoutLosingNamedTrust() {
        Claim claim = new Claim(UUID.randomUUID(), "minecraft:overworld", 0, 10, 0, 10);
        UUID named = UUID.randomUUID();
        UUID visitor = UUID.randomUUID();
        claim.addTrusted(named);
        claim.setPublicTrust(Claim.TrustLevel.MANAGER, true);
        assertTrue(claim.isTrusted(visitor));
        assertTrue(claim.hasFullControl(visitor));
        claim.removeTrusted(visitor);
        assertTrue(claim.isTrusted(visitor));
        claim.setPublicTrust(Claim.TrustLevel.FULL, false);
        assertFalse(claim.hasFullControl(visitor));
        assertFalse(claim.isTrusted(visitor));
        assertTrue(claim.isTrusted(named));
    }

    @Test
    public void publicFlagsPersistAndOldClaimsDefaultPrivate() {
        Gson gson = new Gson();
        Claim old = gson.fromJson("{\"ownerUuid\":\"" + UUID.randomUUID() + "\"}", Claim.class);
        assertFalse(old.trustAll);
        assertFalse(old.interactAll);
        assertFalse(old.managerAll);
        old.setPublicTrust(Claim.TrustLevel.MANAGER, true);
        Claim restored = gson.fromJson(gson.toJson(old), Claim.class);
        assertTrue(restored.managerAll);
        assertTrue(restored.hasFullControl(UUID.randomUUID()));
    }
}