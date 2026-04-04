package com.openggf.game.sonic3k;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for {@link Sonic3kWaterDataProvider}: zone water detection and
 * starting water heights from StartingWaterHeights.bin.
 */
public class TestSonic3kWaterDataProvider {

    private Sonic3kWaterDataProvider provider;

    @Before
    public void setUp() {
        provider = new Sonic3kWaterDataProvider();
    }

    // =====================================================================
    // hasWater tests
    // =====================================================================

    @Test
    public void aiz1HasWater() {
        assertTrue("AIZ1 Sonic should have water",
                provider.hasWater(Sonic3kZoneIds.ZONE_AIZ, 0, PlayerCharacter.SONIC_AND_TAILS));
        assertTrue("AIZ1 Knuckles should have water",
                provider.hasWater(Sonic3kZoneIds.ZONE_AIZ, 0, PlayerCharacter.KNUCKLES));
    }

    @Test
    public void aiz2SonicHasWater() {
        assertTrue("AIZ2 Sonic should have water",
                provider.hasWater(Sonic3kZoneIds.ZONE_AIZ, 1, PlayerCharacter.SONIC_AND_TAILS));
    }

    @Test
    public void aiz2KnucklesHasNoWaterOnDirectLoad() {
        // ROM CheckLevelForWater (sonic3k.asm:9754-9759): Knuckles excluded from AIZ2 water
        // when Apparent_zone_and_act == Current_zone_and_act (direct load / level select).
        assertFalse("AIZ2 Knuckles should NOT have water (direct load)",
                provider.hasWater(Sonic3kZoneIds.ZONE_AIZ, 1, PlayerCharacter.KNUCKLES));
        assertFalse("AIZ2 Knuckles should NOT have water (seamless=false)",
                provider.hasWater(Sonic3kZoneIds.ZONE_AIZ, 1, PlayerCharacter.KNUCKLES, false));
    }

    @Test
    public void aiz2KnucklesHasWaterOnSeamlessTransition() {
        // ROM: During seamless AIZ1→AIZ2 transition, Apparent_zone_and_act still points to
        // AIZ1 (0), not AIZ2 (1). CheckLevelForWater: Apparent != Current → water enabled
        // even for Knuckles (sonic3k.asm:9756-9757: bne.s loc_78F2).
        assertTrue("AIZ2 Knuckles should have water during seamless transition from AIZ1",
                provider.hasWater(Sonic3kZoneIds.ZONE_AIZ, 1, PlayerCharacter.KNUCKLES, true));
    }

    @Test
    public void hczHasWater() {
        assertTrue("HCZ1 should have water",
                provider.hasWater(Sonic3kZoneIds.ZONE_HCZ, 0, PlayerCharacter.SONIC_AND_TAILS));
        assertTrue("HCZ2 should have water",
                provider.hasWater(Sonic3kZoneIds.ZONE_HCZ, 1, PlayerCharacter.KNUCKLES));
    }

    @Test
    public void lbz2HasWater() {
        // Only LBZ2 has water — LBZ1 does NOT (sonic3k.asm:9772-9773)
        assertFalse("LBZ1 should NOT have water",
                provider.hasWater(Sonic3kZoneIds.ZONE_LBZ, 0, PlayerCharacter.SONIC_AND_TAILS));
        assertTrue("LBZ2 should have water",
                provider.hasWater(Sonic3kZoneIds.ZONE_LBZ, 1, PlayerCharacter.SONIC_AND_TAILS));
    }

    @Test
    public void mgzHasNoWater() {
        assertFalse("MGZ should not have water",
                provider.hasWater(Sonic3kZoneIds.ZONE_MGZ, 0, PlayerCharacter.SONIC_AND_TAILS));
    }

    @Test
    public void cnz1HasNoWater() {
        assertFalse("CNZ1 should not have water",
                provider.hasWater(Sonic3kZoneIds.ZONE_CNZ, 0, PlayerCharacter.SONIC_AND_TAILS));
    }

    @Test
    public void cnz2SonicHasWater() {
        // CNZ2 Sonic/Tails: water (sonic3k.asm:9764-9767)
        assertTrue("CNZ2 Sonic should have water",
                provider.hasWater(Sonic3kZoneIds.ZONE_CNZ, 1, PlayerCharacter.SONIC_AND_TAILS));
    }

    @Test
    public void cnz2KnucklesHasNoWater() {
        // CNZ2 Knuckles: no water (falls through in ROM)
        assertFalse("CNZ2 Knuckles should NOT have water",
                provider.hasWater(Sonic3kZoneIds.ZONE_CNZ, 1, PlayerCharacter.KNUCKLES));
    }

    @Test
    public void fbzHasNoWater() {
        assertFalse("FBZ should not have water",
                provider.hasWater(Sonic3kZoneIds.ZONE_FBZ, 0, PlayerCharacter.SONIC_AND_TAILS));
    }

    @Test
    public void mhzHasNoWater() {
        assertFalse("MHZ should not have water",
                provider.hasWater(Sonic3kZoneIds.ZONE_MHZ, 0, PlayerCharacter.SONIC_AND_TAILS));
    }

    @Test
    public void icz1HasNoWater() {
        assertFalse("ICZ1 should not have water",
                provider.hasWater(Sonic3kZoneIds.ZONE_ICZ, 0, PlayerCharacter.SONIC_AND_TAILS));
    }

    @Test
    public void icz2HasWater() {
        // ICZ2: water (sonic3k.asm:9770-9771)
        assertTrue("ICZ2 should have water",
                provider.hasWater(Sonic3kZoneIds.ZONE_ICZ, 1, PlayerCharacter.SONIC_AND_TAILS));
    }

    @Test
    public void sozHasNoWater() {
        assertFalse("SOZ should not have water",
                provider.hasWater(Sonic3kZoneIds.ZONE_SOZ, 0, PlayerCharacter.SONIC_AND_TAILS));
    }

    @Test
    public void lrzHasNoWater() {
        assertFalse("LRZ should not have water",
                provider.hasWater(Sonic3kZoneIds.ZONE_LRZ, 0, PlayerCharacter.SONIC_AND_TAILS));
    }

    @Test
    public void sszHasNoWater() {
        assertFalse("SSZ should not have water",
                provider.hasWater(Sonic3kZoneIds.ZONE_SSZ, 0, PlayerCharacter.SONIC_AND_TAILS));
    }

    @Test
    public void dezHasNoWater() {
        assertFalse("DEZ should not have water",
                provider.hasWater(Sonic3kZoneIds.ZONE_DEZ, 0, PlayerCharacter.SONIC_AND_TAILS));
    }

    @Test
    public void ddzHasNoWater() {
        assertFalse("DDZ should not have water",
                provider.hasWater(Sonic3kZoneIds.ZONE_DDZ, 0, PlayerCharacter.SONIC_AND_TAILS));
    }

    // =====================================================================
    // Starting water height tests
    // =====================================================================

    @Test
    public void aiz1StartingHeight() {
        assertEquals("AIZ1 starting height should be 0x0504",
                0x0504, provider.getStartingWaterLevel(Sonic3kZoneIds.ZONE_AIZ, 0));
    }

    @Test
    public void aiz2StartingHeight() {
        assertEquals("AIZ2 starting height should be 0x0528",
                0x0528, provider.getStartingWaterLevel(Sonic3kZoneIds.ZONE_AIZ, 1));
    }

    @Test
    public void hcz1StartingHeight() {
        assertEquals("HCZ1 starting height should be 0x0500",
                0x0500, provider.getStartingWaterLevel(Sonic3kZoneIds.ZONE_HCZ, 0));
    }

    @Test
    public void hcz2StartingHeight() {
        assertEquals("HCZ2 starting height should be 0x0700",
                0x0700, provider.getStartingWaterLevel(Sonic3kZoneIds.ZONE_HCZ, 1));
    }

    @Test
    public void lbz1StartingHeight() {
        assertEquals("LBZ1 starting height should be 0x0A80 (ROM verified)",
                0x0A80, provider.getStartingWaterLevel(Sonic3kZoneIds.ZONE_LBZ, 0));
    }

    @Test
    public void lbz2StartingHeight() {
        assertEquals("LBZ2 starting height should be 0x065E (ROM verified)",
                0x065E, provider.getStartingWaterLevel(Sonic3kZoneIds.ZONE_LBZ, 1));
    }

    @Test
    public void nonWaterZoneReturnsDefaultHeight() {
        // Non-water zones use 0x0600 (off-screen)
        assertEquals(0x0600, provider.getStartingWaterLevel(Sonic3kZoneIds.ZONE_MGZ, 0));
        assertEquals(0x0600, provider.getStartingWaterLevel(Sonic3kZoneIds.ZONE_DEZ, 1));
    }

    @Test
    public void outOfBoundsZoneReturnsDefaultHeight() {
        assertEquals("Zone -1 should return default",
                0x0600, provider.getStartingWaterLevel(-1, 0));
        assertEquals("Zone 99 should return default",
                0x0600, provider.getStartingWaterLevel(99, 0));
    }
}
