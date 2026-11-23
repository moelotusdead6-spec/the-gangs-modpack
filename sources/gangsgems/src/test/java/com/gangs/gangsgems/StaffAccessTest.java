package com.gangs.gangsgems;

import static org.junit.Assert.*;

import java.util.List;
import org.junit.Test;

public class StaffAccessTest {
    @Test
    public void moderatorMayMonitorButCannotRemove() {
        assertTrue(StaffAccess.matches(List.of("moderator"), false));
        assertFalse(StaffAccess.matches(List.of("moderator"), true));
        assertTrue(StaffAccess.matches(List.of("admin", "mod"), true));
        assertFalse(StaffAccess.matches(List.of("default"), false));
    }
}
