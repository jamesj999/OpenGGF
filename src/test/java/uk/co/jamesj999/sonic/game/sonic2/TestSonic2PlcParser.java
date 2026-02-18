package uk.co.jamesj999.sonic.game.sonic2;

import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2Constants;
import uk.co.jamesj999.sonic.level.resources.PlcParser.PlcDefinition;
import uk.co.jamesj999.sonic.level.resources.PlcParser.PlcEntry;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Verifies that the Sonic 2 PLC (Pattern Load Cue) parser correctly reads
 * art loading data from the ROM's ArtLoadCues table.
 */
public class TestSonic2PlcParser {

    private static Rom rom;

    @BeforeClass
    public static void loadRom() {
        String romPath = System.getProperty("s2.rom.path",
                "Sonic The Hedgehog 2 (W) (REV01) [!].gen");
        File romFile = new File(romPath);
        Assume.assumeTrue("Sonic 2 ROM not found at: " + romFile.getAbsolutePath(),
                romFile.exists());
        rom = new Rom();
        assertTrue("Failed to open ROM", rom.open(romFile.getAbsolutePath()));
    }

    @Test
    public void testParseStd1() throws IOException {
        PlcDefinition plc = Sonic2PlcLoader.parsePlc(rom, Sonic2Constants.PLC_STD1);
        assertNotNull(plc);
        assertFalse("Std1 PLC should have entries", plc.entries().isEmpty());

        // Std1 loads HUD, life icon, ring art, numbers - verify known addresses appear
        Set<Integer> addrs = collectAddresses(plc);
        assertTrue("Std1 should contain HUD art address 0x" + Integer.toHexString(Sonic2Constants.ART_NEM_HUD_ADDR),
                addrs.contains(Sonic2Constants.ART_NEM_HUD_ADDR));
        assertTrue("Std1 should contain Sonic life art address",
                addrs.contains(Sonic2Constants.ART_NEM_SONIC_LIFE_ADDR));
    }

    @Test
    public void testParseStd2() throws IOException {
        PlcDefinition plc = Sonic2PlcLoader.parsePlc(rom, Sonic2Constants.PLC_STD2);
        assertNotNull(plc);
        assertFalse("Std2 PLC should have entries", plc.entries().isEmpty());

        // Std2 loads checkpoint, signpost, monitors, shield, stars, explosion
        Set<Integer> addrs = collectAddresses(plc);
        assertTrue("Std2 should contain checkpoint art",
                addrs.contains(Sonic2Constants.ART_NEM_CHECKPOINT_ADDR));
        assertTrue("Std2 should contain monitor art",
                addrs.contains(Sonic2Constants.ART_NEM_MONITOR_ADDR));
    }

    @Test
    public void testParseEhz1() throws IOException {
        PlcDefinition plc = Sonic2PlcLoader.parsePlc(rom, Sonic2Constants.PLC_EHZ1);
        assertNotNull(plc);
        assertFalse("EHZ1 PLC should have entries", plc.entries().isEmpty());

        // EHZ1 loads waterfall, bridge, buzzer, coconuts, masher
        Set<Integer> addrs = collectAddresses(plc);
        assertTrue("EHZ1 should contain waterfall art",
                addrs.contains(Sonic2Constants.ART_NEM_EHZ_WATERFALL_ADDR));
        assertTrue("EHZ1 should contain bridge art",
                addrs.contains(Sonic2Constants.ART_NEM_BRIDGE_ADDR));
        assertTrue("EHZ1 should contain buzzer badnik art",
                addrs.contains(Sonic2Constants.ART_NEM_BUZZER_ADDR));
    }

    @Test
    public void testParseAllPlcsNoErrors() throws IOException {
        // Parse all 67 PLC IDs - should not throw
        for (int i = 0; i < Sonic2Constants.ART_LOAD_CUES_ENTRY_COUNT; i++) {
            PlcDefinition plc = Sonic2PlcLoader.parsePlc(rom, i);
            assertNotNull("PLC " + i + " should not be null", plc);
            // All valid PLCs should have at least one entry (some unused zones may be empty)
        }
    }

    @Test
    public void testGetZonePlcIdsEhz() throws IOException {
        // EHZ is zone index 0 in the LevelArtPointers table
        int[] plcIds = Sonic2PlcLoader.getZonePlcIds(rom, 0);
        assertEquals(2, plcIds.length);
        assertEquals("EHZ primary PLC should be PLC_EHZ1 (" + Sonic2Constants.PLC_EHZ1 + ")",
                Sonic2Constants.PLC_EHZ1, plcIds[0]);
        assertEquals("EHZ secondary PLC should be PLC_EHZ2 (" + Sonic2Constants.PLC_EHZ2 + ")",
                Sonic2Constants.PLC_EHZ2, plcIds[1]);
    }

    @Test
    public void testOutOfRangePlcReturnsEmpty() throws IOException {
        PlcDefinition plc = Sonic2PlcLoader.parsePlc(rom, 99);
        assertNotNull(plc);
        assertTrue("Out-of-range PLC should return empty entries", plc.entries().isEmpty());
    }

    @Test
    public void testAllPlcEntriesHaveValidAddresses() throws IOException {
        for (int i = 0; i < Sonic2Constants.ART_LOAD_CUES_ENTRY_COUNT; i++) {
            PlcDefinition plc = Sonic2PlcLoader.parsePlc(rom, i);
            for (PlcEntry entry : plc.entries()) {
                assertTrue(String.format("PLC %d entry has invalid ROM address: 0x%06X",
                        i, entry.romAddr()),
                        entry.romAddr() > 0 && entry.romAddr() < Sonic2Constants.DEFAULT_ROM_SIZE);
                assertTrue(String.format("PLC %d entry has invalid tile index: 0x%03X",
                        i, entry.tileIndex()),
                        entry.tileIndex() >= 0 && entry.tileIndex() < 0x800);
            }
        }
    }

    private Set<Integer> collectAddresses(PlcDefinition plc) {
        Set<Integer> addrs = new HashSet<>();
        for (PlcEntry entry : plc.entries()) {
            addrs.add(entry.romAddr());
        }
        return addrs;
    }
}
