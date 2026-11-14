package com.elysium.goldclaim;

import com.elysium.goldclaim.data.Claim;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class ClaimPermissionsTest {
    @Rule public final TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void interactionTrustDoesNotGrantFullAccess() {
        ClaimManager claims = manager(100);
        UUID owner = UUID.randomUUID();
        UUID visitor = UUID.randomUUID();
        assertTrue(claims.createClaim(owner, "Owner", "minecraft:overworld", 0, 4, 0, 4).success());
        assertFalse(claims.canInteract(visitor, "Visitor", false, "minecraft:overworld", 1, 1));
        assertFalse(claims.canModify(visitor, "Visitor", false, "minecraft:overworld", 1, 1));
        assertTrue(claims.trustPlayerAt(owner, visitor, "Visitor", "minecraft:overworld",
            1, 1, true, false, true).success());
        assertTrue(claims.canInteract(visitor, "Visitor", false, "minecraft:overworld", 1, 1));
        assertFalse(claims.canModify(visitor, "Visitor", false, "minecraft:overworld", 1, 1));
        assertTrue(claims.trustPlayerAt(owner, visitor, "Visitor", "minecraft:overworld",
            1, 1, true, false).success());
        assertTrue(claims.canModify(visitor, "Visitor", false, "minecraft:overworld", 1, 1));
        assertTrue(claims.canModify(owner, "Owner", false, "minecraft:overworld", 1, 1));
        assertTrue(claims.canModify(visitor, "Visitor", false, "minecraft:overworld", 30, 30));
    }

    @Test
    public void publicInteractionDoesNotGrantMachineOrBuildingAccess() {
        ClaimManager claims = manager(100);
        UUID owner = UUID.randomUUID();
        UUID guest = UUID.randomUUID();
        assertTrue(claims.createClaim(owner, "Owner", "minecraft:overworld", 0, 4, 0, 4).success());
        assertTrue(claims.setPublicTrustAt(owner, "minecraft:overworld", 1, 1,
            Claim.TrustLevel.INTERACT, true).success());
        assertTrue(claims.canInteract(guest, "Guest", false, "minecraft:overworld", 1, 1));
        assertFalse(claims.canModify(guest, "Guest", false, "minecraft:overworld", 1, 1));
        assertTrue(claims.setPublicTrustAt(owner, "minecraft:overworld", 1, 1,
            Claim.TrustLevel.FULL, true).success());
        assertTrue(claims.canModify(guest, "Guest", false, "minecraft:overworld", 1, 1));
    }

    @Test
    public void claimBlockUsageAndLimitsArePerOwnerAndDimension() {
        ClaimManager claims = manager(40);
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        assertTrue(claims.createClaim(owner, "Owner", "minecraft:overworld", 0, 3, 0, 4).success());
        assertTrue(claims.createClaim(owner, "Owner", "minecraft:overworld", 10, 12, 0, 3).success());
        assertTrue(claims.createClaim(owner, "Owner", "minecraft:the_nether", 0, 2, 0, 2).success());
        assertTrue(claims.createClaim(other, "Other", "minecraft:overworld", 20, 21, 0, 1).success());
        assertEquals(32, claims.getTotalClaimBlocks(owner, "minecraft:overworld"));
        assertEquals(9, claims.getTotalClaimBlocks(owner, "minecraft:the_nether"));
        assertEquals(4, claims.getTotalClaimBlocks(other, "minecraft:overworld"));
        var overLimit = claims.createClaim(owner, "Owner", "minecraft:overworld", 30, 32, 0, 2);
        assertFalse(overLimit.success());
        assertEquals(ClaimManager.CreateError.CLAIM_LIMIT_EXCEEDED, overLimit.error());
    }

    @Test
    public void adminClaimTrustIsManagedOnlyByAdminsAndPreservesInteractionLimits() {
        ClaimManager claims = manager(1);
        UUID admin = UUID.randomUUID();
        UUID manager = UUID.randomUUID();
        UUID visitor = UUID.randomUUID();
        String dimension = "minecraft:overworld";
        assertTrue(claims.createAdminClaim(dimension, 0, 9, 0, 9).success());
        assertFalse(claims.trustPlayerAt(manager, visitor, "Visitor", dimension, 1, 1, true, false, true).success());
        assertTrue(claims.trustPlayerAt(admin, visitor, "Visitor", dimension, 1, 1, true, true, true).success());
        assertTrue(claims.canInteract(visitor, "Visitor", false, dimension, 1, 1));
        assertFalse(claims.canModify(visitor, "Visitor", false, dimension, 1, 1));
        assertTrue(claims.managePlayerAt(admin, manager, "Manager", dimension, 1, 1, true, true).success());
        assertTrue(claims.canModify(manager, "Manager", false, dimension, 1, 1));
        assertFalse(claims.trustPlayerAt(manager, visitor, "Visitor", dimension, 1, 1, true, false).success());
        assertFalse(claims.setPublicTrustAt(manager, dimension, 1, 1, Claim.TrustLevel.INTERACT, true, false).success());
        assertTrue(claims.setPublicTrustAt(admin, dimension, 1, 1, Claim.TrustLevel.INTERACT, true, true).success());
        assertTrue(claims.canInteract(UUID.randomUUID(), "Guest", false, dimension, 1, 1));
        assertTrue(claims.expandClaimAt(admin, dimension, 1, 1, 1,
            net.minecraft.util.math.Direction.EAST, false, true).success());
    }

    private ClaimManager manager(int maximum) {
        GoldClaimConfig config = new GoldClaimConfig();
        config.minimumClaimWidth = 1;
        config.minimumClaimDepth = 1;
        config.maxClaimBlocksPerPlayerPerDimension = maximum;
        return new ClaimManager(temp.getRoot().toPath().resolve("claims.json"), config);
    }
}
