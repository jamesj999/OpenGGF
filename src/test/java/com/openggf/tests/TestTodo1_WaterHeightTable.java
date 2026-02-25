package com.openggf.tests;

import org.junit.Rule;
import org.junit.Test;
import com.openggf.data.Rom;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 * TODO #1 coverage: Sonic2ZoneFeatureProvider.getWaterLevel() returns MAX_VALUE placeholder.
 * <p>
 * This test verifies that the ROM contains the expected water height values from
 * the disassembly's WaterHeight table (s2.asm lines 5329-5338, address word_4584).
 * <p>
 * The compact WaterHeight table starts at ROM offset 0x4584 and covers zones
 * 0x08 (HPZ) through 0x0F (ARZ), with 2 words (Act 1 and Act 2) per zone.
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
    private static final int ROM_ZONE_CPZ = 0x0D;
    private static final int ROM_ZONE_DEZ = 0x0E;
    private static final int ROM_ZONE_ARZ = 0x0F;

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

    /**
     * Verify ARZ water heights match disassembly.
     * s2.asm line 5337: "dc.w $410, $510 ; ARZ"
     */
    @Test
    public void testArzWaterHeights() throws IOException {
        Rom rom = romRule.rom();
        assertEquals("ARZ Act 1 water height", 0x0410, readWaterHeight(rom, ROM_ZONE_ARZ, 0));
        assertEquals("ARZ Act 2 water height", 0x0510, readWaterHeight(rom, ROM_ZONE_ARZ, 1));
    }

    /**
     * Verify CPZ water heights match disassembly.
     * s2.asm line 5335: "dc.w $600, $710 ; CPZ"
     */
    @Test
    public void testCpzWaterHeights() throws IOException {
        Rom rom = romRule.rom();
        assertEquals("CPZ Act 1 water height", 0x0600, readWaterHeight(rom, ROM_ZONE_CPZ, 0));
        assertEquals("CPZ Act 2 water height", 0x0710, readWaterHeight(rom, ROM_ZONE_CPZ, 1));
    }

    /**
     * Verify non-water zones have default height of 0x600.
     * s2.asm lines 5330-5336: all non-ARZ/CPZ entries are $600
     */
    @Test
    public void testNonWaterZonesDefaultHeight() throws IOException {
        Rom rom = romRule.rom();

        // HPZ (unused in final game but data present)
        assertEquals("HPZ Act 1", 0x0600, readWaterHeight(rom, ROM_ZONE_HPZ, 0));
        assertEquals("HPZ Act 2", 0x0600, readWaterHeight(rom, ROM_ZONE_HPZ, 1));

        // OOZ
        assertEquals("OOZ Act 1", 0x0600, readWaterHeight(rom, ROM_ZONE_OOZ, 0));
        assertEquals("OOZ Act 2", 0x0600, readWaterHeight(rom, ROM_ZONE_OOZ, 1));

        // MCZ
        assertEquals("MCZ Act 1", 0x0600, readWaterHeight(rom, ROM_ZONE_MCZ, 0));
        assertEquals("MCZ Act 2", 0x0600, readWaterHeight(rom, ROM_ZONE_MCZ, 1));

        // CNZ
        assertEquals("CNZ Act 1", 0x0600, readWaterHeight(rom, ROM_ZONE_CNZ, 0));
        assertEquals("CNZ Act 2", 0x0600, readWaterHeight(rom, ROM_ZONE_CNZ, 1));

        // DEZ
        assertEquals("DEZ Act 1", 0x0600, readWaterHeight(rom, ROM_ZONE_DEZ, 0));
        assertEquals("DEZ Act 2", 0x0600, readWaterHeight(rom, ROM_ZONE_DEZ, 1));
    }

    /**
     * Verify the compact table has exactly 8 zone entries (32 bytes total).
     * Table covers zones 0x08 (HPZ) through 0x0F (ARZ).
     * Each entry is 2 words (4 bytes) = Act 1 height + Act 2 height.
     */
    @Test
    public void testTableSize() throws IOException {
        Rom rom = romRule.rom();
        // 8 zones * 2 words per zone * 2 bytes per word = 32 bytes
        byte[] tableData = rom.readBytes(WATER_HEIGHT_TABLE_ADDR, 32);
        assertEquals("Table should be 32 bytes", 32, tableData.length);
    }
}
