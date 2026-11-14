package com.elysium.goldclaim;

import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class StaffLocatePolicyTest {
    @Test
    public void onlyStaffGroupsAndOperatorsReceiveLocate() {
        for (String group : Set.of("mod", "moderator", "admin", "owner")) {
            assertTrue(group, StaffLocatePolicy.allowed(false, Set.of("default", group)));
        }
        for (String group : Set.of("default", "player", "gang", "drifters", "mod-helper", "administrator")) {
            assertFalse(group, StaffLocatePolicy.allowed(false, Set.of(group)));
        }
        assertFalse(StaffLocatePolicy.allowed(false, Set.of()));
        assertTrue(StaffLocatePolicy.allowed(true, Set.of()));
    }

    @Test
    public void onlyAdminOwnerAndOperatorsManageAdminClaims() {
        assertTrue(StaffLocatePolicy.adminAllowed(false, Set.of("admin")));
        assertTrue(StaffLocatePolicy.adminAllowed(false, Set.of("owner")));
        assertTrue(StaffLocatePolicy.adminAllowed(true, Set.of()));
        assertFalse(StaffLocatePolicy.adminAllowed(false, Set.of("mod", "moderator")));
        assertFalse(StaffLocatePolicy.adminAllowed(false, Set.of("default")));
    }
}
