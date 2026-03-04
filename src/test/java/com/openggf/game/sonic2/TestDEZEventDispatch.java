package com.openggf.game.sonic2;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * Verifies zone constants in Sonic2LevelEventManager match
 * the Sonic2ZoneRegistry ordering (0-10).
 * The rest of the codebase (ParallaxManager, BackgroundCamera,
 * LevelSelectConstants, Sonic2ZoneConstants) all agree:
 * SCZ=8, WFZ=9, DEZ=10.
 */
public class TestDEZEventDispatch {

    @Test
    public void dezZoneConstantMatchesRegistry() {
        assertEquals("ZONE_DEZ must be 10 to match ZoneRegistry",
                10, Sonic2LevelEventManager.ZONE_DEZ);
    }

    @Test
    public void wfzZoneConstantMatchesRegistry() {
        assertEquals("ZONE_WFZ must be 9 to match ZoneRegistry",
                9, Sonic2LevelEventManager.ZONE_WFZ);
    }

    @Test
    public void sczZoneConstantMatchesRegistry() {
        assertEquals("ZONE_SCZ must be 8 to match ZoneRegistry",
                8, Sonic2LevelEventManager.ZONE_SCZ);
    }

    @Test
    public void cpzZoneConstantMatchesRegistry() {
        assertEquals("ZONE_CPZ must be 1 to match ZoneRegistry",
                1, Sonic2LevelEventManager.ZONE_CPZ);
    }
}
