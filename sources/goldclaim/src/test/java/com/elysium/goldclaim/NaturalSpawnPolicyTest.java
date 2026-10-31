package com.elysium.goldclaim;

import org.junit.Test;
import static org.junit.Assert.*;

public class NaturalSpawnPolicyTest {
    @Test
    public void wildBlocksVanillaAndModdedHostilesButNotAnimalsOrOtherWorlds() {
        for (String entity : new String[]{"minecraft:zombie", "modded:monster"}) {
            assertFalse(NaturalSpawnPolicy.allow("minecraft:overworld", entity, true, 99));
            for (String dimension : new String[]{"multiworld:rsw", "multiworld:rsw_nether",
                    "multiworld:rsw_the_end", "minecraft:the_nether", "gangs:hub"}) {
                assertTrue(NaturalSpawnPolicy.allow(dimension, entity, true, 0));
            }
        }
        assertTrue(NaturalSpawnPolicy.allow("minecraft:overworld", "minecraft:cow", false, 0));
    }

    @Test
    public void targetedAnimalsLoseAtLeastSeventyPercentEvenWithFourChunkGenerationRetries() {
        for (String dimension : new String[]{"minecraft:overworld", "multiworld:rsw",
                "multiworld:rsw_nether", "multiworld:rsw_the_end", "other:world"}) {
            for (String entity : new String[]{"crittersandcompanions:roly_poly",
                    "crittersandcompanions:snail", "naturalist:snail"}) {
                int accepted = 0;
                for (int roll = 0; roll < 100; roll++) {
                    if (NaturalSpawnPolicy.allow(dimension, entity, false, roll)) accepted++;
                }
                assertEquals(5, accepted);
                assertTrue(1 - Math.pow(1 - accepted / 100.0, 4) <= 0.3);
            }
            assertTrue(NaturalSpawnPolicy.allow(dimension, "other:snail", false, 0));
            assertTrue(NaturalSpawnPolicy.allow(dimension, "crittersandcompanions:otter", false, 0));
        }
    }
}
