package com.openggf.game.sonic1;

import com.openggf.game.PlayerCharacter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link Sonic1WaterDataProvider}.
 * Verifies water presence and starting heights match the ROM's
 * LZWaterFeatures.asm WaterHeight table (lines 49-52).
 */
public class TestSonic1WaterDataProvider {
    private final Sonic1WaterDataProvider provider = new Sonic1WaterDataProvider();

    private static final int ZONE_GHZ = 0x00;
    private static final int ZONE_LZ = 0x01;
    private static final int ZONE_MZ = 0x02;
    private static final int ZONE_SLZ = 0x03;
    private static final int ZONE_SYZ = 0x04;
    private static final int ZONE_SBZ = 0x05;

    // --- hasWater tests ---

    @Test
    public void testLzHasWater() {
        assertTrue(provider.hasWater(ZONE_LZ, 0, PlayerCharacter.SONIC_AND_TAILS));
        assertTrue(provider.hasWater(ZONE_LZ, 1, PlayerCharacter.SONIC_AND_TAILS));
        assertTrue(provider.hasWater(ZONE_LZ, 2, PlayerCharacter.SONIC_AND_TAILS));
    }

    @Test
    public void testSbz3HasWater() {
        assertTrue(provider.hasWater(ZONE_SBZ, 2, PlayerCharacter.SONIC_AND_TAILS));
    }

    @Test
    public void testSbz1NoWater() {
        assertFalse(provider.hasWater(ZONE_SBZ, 0, PlayerCharacter.SONIC_AND_TAILS));
    }

    @Test
    public void testSbz2NoWater() {
        assertFalse(provider.hasWater(ZONE_SBZ, 1, PlayerCharacter.SONIC_AND_TAILS));
    }

    @Test
    public void testGhzNoWater() {
        assertFalse(provider.hasWater(ZONE_GHZ, 0, PlayerCharacter.SONIC_AND_TAILS));
    }

    @Test
    public void testMzNoWater() {
        assertFalse(provider.hasWater(ZONE_MZ, 0, PlayerCharacter.SONIC_AND_TAILS));
    }

    @Test
    public void testSlzNoWater() {
        assertFalse(provider.hasWater(ZONE_SLZ, 0, PlayerCharacter.SONIC_AND_TAILS));
    }

    @Test
    public void testSyzNoWater() {
        assertFalse(provider.hasWater(ZONE_SYZ, 0, PlayerCharacter.SONIC_AND_TAILS));
    }

    // --- getStartingWaterLevel tests ---

    @Test
    public void testLz1Height() {
        assertEquals(0x00B8, provider.getStartingWaterLevel(ZONE_LZ, 0));
    }

    @Test
    public void testLz2Height() {
        assertEquals(0x0328, provider.getStartingWaterLevel(ZONE_LZ, 1));
    }

    @Test
    public void testLz3Height() {
        assertEquals(0x0900, provider.getStartingWaterLevel(ZONE_LZ, 2));
    }

    @Test
    public void testSbz3Height() {
        assertEquals(0x0228, provider.getStartingWaterLevel(ZONE_SBZ, 2));
    }

    @Test
    public void testNonWaterZoneReturnsDefault() {
        // Zones without water return the off-screen default (0x0600)
        assertEquals(0x0600, provider.getStartingWaterLevel(ZONE_GHZ, 0));
    }

    // --- getDynamicHandler tests ---

    @Test
    public void testNoDynamicHandler() {
        // S1 water is static; no dynamic handlers
        assertNull(provider.getDynamicHandler(ZONE_LZ, 0, PlayerCharacter.SONIC_AND_TAILS), "LZ should have no dynamic water handler");
        assertNull(provider.getDynamicHandler(ZONE_SBZ, 2, PlayerCharacter.SONIC_AND_TAILS), "SBZ3 should have no dynamic water handler");
    }

    // --- getWaterSpeed tests ---

    @Test
    public void testDefaultWaterSpeed() {
        // WaterDataProvider default is 1
        assertEquals(1, provider.getWaterSpeed(ZONE_LZ, 0));
    }
}


