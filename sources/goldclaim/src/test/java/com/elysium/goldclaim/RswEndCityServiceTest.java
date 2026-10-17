package com.elysium.goldclaim;

import net.minecraft.util.math.BlockBox;
import net.minecraft.world.border.WorldBorder;
import org.junit.Test;
import static org.junit.Assert.*;

public class RswEndCityServiceTest {
    @Test
    public void requiresWholeCityInsideBorderWithSafetyMargin() {
        WorldBorder border = new WorldBorder();
        border.setCenter(0, 0);
        border.setSize(10000);
        assertTrue(RswEndCityService.fits(new BlockBox(1200, 60, 1200, 1400, 180, 1400), border));
        assertFalse(RswEndCityService.fits(new BlockBox(4900, 60, 1200, 5100, 180, 1400), border));
        assertFalse(RswEndCityService.fits(new BlockBox(-5100, 60, 1200, -4900, 180, 1400), border));
        assertFalse(RswEndCityService.fits(new BlockBox(1200, 60, 4900, 1400, 180, 5100), border));
        assertFalse(RswEndCityService.fits(new BlockBox(1200, 60, -5100, 1400, 180, -4900), border));
        assertFalse(RswEndCityService.fits(new BlockBox(4900, 60, 1200, 4990, 180, 1400), border));
    }

    @Test
    public void entireCityMustBeOnOuterIslandsNotJustItsCenter() {
        WorldBorder border = new WorldBorder();
        border.setSize(10000);
        assertFalse(RswEndCityService.fits(new BlockBox(-100, 60, -100, 100, 180, 100), border));
        assertFalse(RswEndCityService.fits(new BlockBox(900, 60, -100, 1300, 180, 100), border));
        assertTrue(RswEndCityService.fits(new BlockBox(-1400, 60, -100, -1100, 180, 100), border));
        assertTrue(RswEndCityService.fits(new BlockBox(800, 60, 800, 1000, 180, 1000), border));
        assertEquals(7, RswEndCityService.REQUIRED_CITIES);
    }
}
