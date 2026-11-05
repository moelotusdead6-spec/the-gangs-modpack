package com.gangs.ouxcompat;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class OuxBadgeKeysTest {
    @Test
    public void mapsTeamsToGlyphsAndTabOrder() {
        assertEquals(0, OuxBadgeKeys.index("oux_badges_1_owner"));
        assertEquals(3, OuxBadgeKeys.index("oux_badges_4_drifter"));
        assertEquals(-1, OuxBadgeKeys.index("oux_badges_5_other"));
        assertEquals(-1, OuxBadgeKeys.index(null));
        assertEquals(1, OuxBadgeKeys.priority("oux_badges_2_mod"));
        assertEquals(4, OuxBadgeKeys.priority("red"));
        assertEquals("\uE002", OuxBadgeKeys.glyph(2));
        assertTrue(OuxBadgeKeys.hasBadge("<\uE000 Oux_y> hi"));
        assertFalse(OuxBadgeKeys.hasBadge("<[Owner] Oux_y> hi"));
    }

    @Test
    public void shrinksOnlyOversizedImages() {
        assertNull(OuxBadgeKeys.shrunkSize(150, 64));
        assertArrayEquals(new int[]{158, 64}, OuxBadgeKeys.shrunkSize(480, 194));
    }

    @Test
    public void shrinkKeepsTransparentEdgesFromDarkening() {
        int red = 0xFF0000FF;
        int[] shrunk = OuxBadgeKeys.shrink(new int[]{red, 0, 0, 0}, 2, 2, 1, 1);
        assertEquals(0x40, shrunk[0] >>> 24);
        assertEquals(0xFF, shrunk[0] & 0xFF);
        assertEquals(0, shrunk[0] >>> 8 & 0xFFFF);
    }
}
