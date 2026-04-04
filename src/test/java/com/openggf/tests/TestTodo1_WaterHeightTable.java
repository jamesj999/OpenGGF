package com.openggf.tests;

import org.junit.Rule;
import org.junit.Test;
import com.openggf.data.Rom;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.WaterDataProvider;
import com.openggf.game.sonic2.scroll.Sonic2ZoneConstants;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Cross-references the ROM WaterHeight table (s2.asm word_4584) against
 * the engine's {@link com.openggf.game.sonic2.Sonic2WaterDataProvider}.
 * <p>
 * Sonic2WaterDataProvider uses hardcoded water heights, so these tests
 * guard against the engine values drifting out of sync with the ROM.
 * <p>
 * Reference: docs/s2disasm/s2.asm lines 5329-5338
 */
@RequiresRom(SonicGame.SONIC_2)
public class TestTodo1_WaterHeightTable {
    @Rule
    public RequiresRomRule romRule = new RequiresRomRule();

    /**
     * Base address of the compact WaterHeight table in ROM.
     * From s2.asm line 5329: "WaterHeight: ; word_4584"
     */
    private static final int WATER_HEIGHT_TABLE_ADDR = 0x4584;

    // ROM zone IDs that index this table (zone_id - 0x08 = table index)
    private static final int ROM_ZONE_HPZ = 0x08;
    private static final int ROM_ZONE_OOZ = 0x0A;
    private static final int ROM_ZONE_MCZ = 0x0B;
    private static final int ROM_ZONE_CNZ = 0x0C;
    private static final int ROM_ZONE_CPZ = Sonic2ZoneConstants.ROM_ZONE_CPZ; // 0x0D
    private static final int ROM_ZONE_DEZ = 0x0E;
    private static final int ROM_ZONE_ARZ = Sonic2ZoneConstants.ROM_ZONE_ARZ; // 0x0F

    /**
     * Read a 16-bit big-endian water height from the WaterHeight table for a given zone/act.
     */
    private int readWaterHeight(Rom rom, int romZoneId, int act) throws IOException {
        int tableIndex = romZoneId - ROM_ZONE_HPZ;
        int offset = WATER_HEIGHT_TABLE_ADDR + tableIndex * 4 + act * 2;
        int high = rom.readByte(offset) & 0xFF;
        int low = rom.readByte(offset + 1) & 0xFF;
        return (high << 8) | low;
    }

    private WaterDataProvider provider() {
        return GameModuleRegistry.getCurrent().getWaterDataProvider();
    }

    // ---- Cross-reference: engine values must match ROM ----

    /**
     * ARZ Act 1: engine starting water level must match ROM table value (0x0410).
     */
    @Test
    public void testEngineArzAct1WaterHeightMatchesRom() throws IOException {
        int romValue = readWaterHeight(romRule.rom(), ROM_ZONE_ARZ, 0);
        int engineValue = provider().getStartingWaterLevel(ROM_ZONE_ARZ, 0);
        assertEquals("ARZ Act 1 engine water height must match ROM", romValue, engineValue);
    }

    /**
     * ARZ Act 2: engine starting water level must match ROM table value (0x0510).
     */
    @Test
    public void testEngineArzAct2WaterHeightMatchesRom() throws IOException {
        int romValue = readWaterHeight(romRule.rom(), ROM_ZONE_ARZ, 1);
        int engineValue = provider().getStartingWaterLevel(ROM_ZONE_ARZ, 1);
        assertEquals("ARZ Act 2 engine water height must match ROM", romValue, engineValue);
    }

    /**
     * CPZ Act 2: engine starting water level must match ROM table value (0x0710).
     * CPZ Act 1 has no water in the ROM (Level_InitWater only sets water_flag for act 1).
     */
    @Test
    public void testEngineCpzAct2WaterHeightMatchesRom() throws IOException {
        int romValue = readWaterHeight(romRule.rom(), ROM_ZONE_CPZ, 1);
        int engineValue = provider().getStartingWaterLevel(ROM_ZONE_CPZ, 1);
        assertEquals("CPZ Act 2 engine water height must match ROM", romValue, engineValue);
    }

    // ---- hasWater correctness: only water zones report water ----

    /**
     * ARZ both acts should have water.
     */
    @Test
    public void testArzHasWater() {
        assertTrue("ARZ Act 1 should have water",
                provider().hasWater(ROM_ZONE_ARZ, 0, PlayerCharacter.SONIC_AND_TAILS));
        assertTrue("ARZ Act 2 should have water",
                provider().hasWater(ROM_ZONE_ARZ, 1, PlayerCharacter.SONIC_AND_TAILS));
    }

    /**
     * CPZ only Act 2 has water (Mega Mack). Act 1 does not.
     */
    @Test
    public void testCpzOnlyAct2HasWater() {
        assertFalse("CPZ Act 1 should NOT have water",
                provider().hasWater(ROM_ZONE_CPZ, 0, PlayerCharacter.SONIC_AND_TAILS));
        assertTrue("CPZ Act 2 should have water",
                provider().hasWater(ROM_ZONE_CPZ, 1, PlayerCharacter.SONIC_AND_TAILS));
    }

    /**
     * Non-water zones (EHZ, HTZ, OOZ, MCZ, CNZ, DEZ) should not report water.
     */
    @Test
    public void testNonWaterZonesHaveNoWater() {
        int[] nonWaterZones = {
            Sonic2ZoneConstants.ROM_ZONE_EHZ,
            Sonic2ZoneConstants.ROM_ZONE_HTZ,
            ROM_ZONE_OOZ,
            ROM_ZONE_MCZ,
            ROM_ZONE_CNZ,
            ROM_ZONE_DEZ,
        };
        for (int zone : nonWaterZones) {
            assertFalse("Zone 0x" + Integer.toHexString(zone) + " Act 1 should NOT have water",
                    provider().hasWater(zone, 0, PlayerCharacter.SONIC_AND_TAILS));
            assertFalse("Zone 0x" + Integer.toHexString(zone) + " Act 2 should NOT have water",
                    provider().hasWater(zone, 1, PlayerCharacter.SONIC_AND_TAILS));
        }
    }

    // ---- ROM baseline: verify ROM values match disassembly ----

    /**
     * Verify ROM WaterHeight table has expected ARZ values.
     * s2.asm line 5337: "dc.w $410, $510 ; ARZ"
     */
    @Test
    public void testRomArzWaterHeights() throws IOException {
        Rom rom = romRule.rom();
        assertEquals("ROM ARZ Act 1 water height", 0x0410, readWaterHeight(rom, ROM_ZONE_ARZ, 0));
        assertEquals("ROM ARZ Act 2 water height", 0x0510, readWaterHeight(rom, ROM_ZONE_ARZ, 1));
    }

    /**
     * Verify ROM WaterHeight table has expected CPZ values.
     * s2.asm line 5335: "dc.w $600, $710 ; CPZ"
     */
    @Test
    public void testRomCpzWaterHeights() throws IOException {
        Rom rom = romRule.rom();
        assertEquals("ROM CPZ Act 1 water height", 0x0600, readWaterHeight(rom, ROM_ZONE_CPZ, 0));
        assertEquals("ROM CPZ Act 2 water height", 0x0710, readWaterHeight(rom, ROM_ZONE_CPZ, 1));
    }

    /**
     * Verify non-water zones have default height of 0x600 in ROM.
     * s2.asm lines 5330-5336: all non-ARZ/CPZ entries are $600
     */
    @Test
    public void testRomNonWaterZonesDefaultHeight() throws IOException {
        Rom rom = romRule.rom();
        int[] defaultZones = { ROM_ZONE_HPZ, ROM_ZONE_OOZ, ROM_ZONE_MCZ, ROM_ZONE_CNZ, ROM_ZONE_DEZ };
        for (int zone : defaultZones) {
            assertEquals("ROM zone 0x" + Integer.toHexString(zone) + " Act 1",
                    0x0600, readWaterHeight(rom, zone, 0));
            assertEquals("ROM zone 0x" + Integer.toHexString(zone) + " Act 2",
                    0x0600, readWaterHeight(rom, zone, 1));
        }
    }
}
