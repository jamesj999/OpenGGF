package com.openggf.game.sonic2;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic2.scroll.Sonic2ZoneConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Sonic2WaterDataProvider}.
 * Verifies water zone detection and starting water heights against
 * known ROM values from the S2 Water_Height table.
 */
public class TestSonic2WaterDataProvider {
    private final Sonic2WaterDataProvider provider = new Sonic2WaterDataProvider();

    private static final int ZONE_EHZ = Sonic2ZoneConstants.ROM_ZONE_EHZ; // 0x00
    private static final int ZONE_CPZ = Sonic2ZoneConstants.ROM_ZONE_CPZ; // 0x0D
    private static final int ZONE_ARZ = Sonic2ZoneConstants.ROM_ZONE_ARZ; // 0x0F
    private static final int ZONE_HTZ = Sonic2ZoneConstants.ROM_ZONE_HTZ; // 0x07
    private static final int ZONE_MCZ = Sonic2ZoneConstants.ROM_ZONE_MCZ; // 0x0B
    private static final int ZONE_CNZ = Sonic2ZoneConstants.ROM_ZONE_CNZ; // 0x0C
    private static final int ZONE_OOZ = Sonic2ZoneConstants.ROM_ZONE_OOZ; // 0x0A
    private static final int ZONE_DEZ = Sonic2ZoneConstants.ROM_ZONE_DEZ; // 0x0E

    // =========================================================================
    // hasWater() tests
    // =========================================================================

    @Test
    public void testCpzWaterByAct() {
        assertFalse(provider.hasWater(ZONE_CPZ, 0, PlayerCharacter.SONIC_AND_TAILS));
        assertTrue(provider.hasWater(ZONE_CPZ, 1, PlayerCharacter.SONIC_AND_TAILS));
    }

    @Test
    public void testArzHasWater() {
        assertTrue(provider.hasWater(ZONE_ARZ, 0, PlayerCharacter.SONIC_AND_TAILS));
        assertTrue(provider.hasWater(ZONE_ARZ, 1, PlayerCharacter.SONIC_AND_TAILS));
    }

    @Test
    public void testHtzNoWater() {
        // HTZ lava is a background visual effect, not water (s2.asm Level_InitWater)
        assertFalse(provider.hasWater(ZONE_HTZ, 0, PlayerCharacter.SONIC_AND_TAILS));
        assertFalse(provider.hasWater(ZONE_HTZ, 1, PlayerCharacter.SONIC_AND_TAILS));
    }

    @Test
    public void testEhzNoWater() {
        assertFalse(provider.hasWater(ZONE_EHZ, 0, PlayerCharacter.SONIC_AND_TAILS));
    }

    @Test
    public void testMczNoWater() {
        assertFalse(provider.hasWater(ZONE_MCZ, 0, PlayerCharacter.SONIC_AND_TAILS));
    }

    @Test
    public void testCnzNoWater() {
        assertFalse(provider.hasWater(ZONE_CNZ, 0, PlayerCharacter.SONIC_AND_TAILS));
    }

    @Test
    public void testOozNoWater() {
        assertFalse(provider.hasWater(ZONE_OOZ, 0, PlayerCharacter.SONIC_AND_TAILS));
    }

    @Test
    public void testDezNoWater() {
        assertFalse(provider.hasWater(ZONE_DEZ, 0, PlayerCharacter.SONIC_AND_TAILS));
    }

    @Test
    public void testWaterIgnoresCharacter() {
        // Water zones are the same for all characters in S2
        assertTrue(provider.hasWater(ZONE_ARZ, 0, PlayerCharacter.SONIC_ALONE));
        assertTrue(provider.hasWater(ZONE_ARZ, 0, PlayerCharacter.TAILS_ALONE));
        assertTrue(provider.hasWater(ZONE_ARZ, 0, PlayerCharacter.KNUCKLES));
    }

    // =========================================================================
    // getStartingWaterLevel() tests
    // =========================================================================

    @Test
    public void testCpz2Height() {
        // ROM Water_Height table at 0x459A = 0x0710
        assertEquals(0x0710, provider.getStartingWaterLevel(ZONE_CPZ, 1));
    }

    @Test
    public void testArz1Height() {
        // ROM Water_Height table at 0x45A0 = 0x0410
        assertEquals(0x0410, provider.getStartingWaterLevel(ZONE_ARZ, 0));
    }

    @Test
    public void testArz2Height() {
        // ROM Water_Height table at 0x45A2 = 0x0510
        assertEquals(0x0510, provider.getStartingWaterLevel(ZONE_ARZ, 1));
    }

    @Test
    public void testCpz1HeightIsDefault() {
        // CPZ Act 1 has no specific ROM entry; uses default
        assertEquals(0, provider.getStartingWaterLevel(ZONE_CPZ, 0));
    }

    // =========================================================================
    // Default method / null-safe tests
    // =========================================================================

    @Test
    public void testUnderwaterPaletteReturnsNull() {
        // Palette loading deferred to existing WaterSystem
        assertNull(provider.getUnderwaterPalette(null, ZONE_ARZ, 0, PlayerCharacter.SONIC_AND_TAILS));
    }

    @Test
    public void testDynamicHandlerReturnsNull() {
        // Dynamic water handled by existing LevelEventManager
        assertNull(provider.getDynamicHandler(ZONE_CPZ, 1, PlayerCharacter.SONIC_AND_TAILS));
    }

    @Test
    public void testDefaultWaterSpeed() {
        // S2 uses default water speed of 1
        assertEquals(1, provider.getWaterSpeed(ZONE_ARZ, 0));
        assertEquals(1, provider.getWaterSpeed(ZONE_CPZ, 1));
    }
}


