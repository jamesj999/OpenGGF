package com.openggf.game.sonic2;

import org.junit.Rule;
import org.junit.Test;
import com.openggf.data.Rom;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.NemesisReader;

import java.io.IOException;
import java.nio.channels.FileChannel;

import static org.junit.Assert.*;

/**
 * Tests verifying that ROM addresses for art pieces are valid and decompressible.
 * These art assets are already loaded by the engine but serve as regression tests
 * to confirm that the ROM offsets produce valid Nemesis-compressed art data.
 *
 * <p>Items 21-23 from the TODO list reference "missing art sheets" in the object art system.
 * While the referenced Sonic2PlcArtRegistry file does not exist in the codebase,
 * these tests verify the underlying ROM data that the art system depends on:
 * <ul>
 *   <li>Item 21: EHZ Waterfall art (ArtNem_EhzWaterfall at $F02D6)</li>
 *   <li>Item 22: CPZ Platform art (ArtNem_CPZElevator at $82216)</li>
 *   <li>Item 23: WFZ Platform art (ArtNem_WfzFloatingPlatform at $8D96E)</li>
 * </ul>
 *
 * <p>All three addresses are defined in Sonic2Constants and loaded via
 * Sonic2ObjectArt.safeLoadNemesisPatterns() during zone initialization.
 */
@RequiresRom(SonicGame.SONIC_2)
public class TestTodo21_23_MissingArtSheets {

    @Rule
    public RequiresRomRule romRule = new RequiresRomRule();

    /**
     * Item 21: Verify EHZ Waterfall art decompresses from ROM.
     * ROM reference: ArtNem_EhzWaterfall
     * Address: Sonic2Constants.ART_NEM_EHZ_WATERFALL_ADDR = 0xF02D6
     *
     * <p>Used by Object 0x49 (EHZWaterfall) in Emerald Hill Zone.
     * The waterfall art includes top/bottom caps and repeating body sections.
     */
    @Test
    public void testEHZWaterfallArtDecompresses() throws IOException {
        Rom rom = romRule.rom();
        FileChannel channel = rom.getFileChannel();
        channel.position(Sonic2Constants.ART_NEM_EHZ_WATERFALL_ADDR);

        byte[] artData = NemesisReader.decompress(channel);
        assertNotNull("EHZ waterfall art should decompress successfully", artData);
        assertTrue("EHZ waterfall art should contain pattern data (multiple of 32 bytes)",
                artData.length > 0 && artData.length % 32 == 0);

        int tileCount = artData.length / 32;
        assertTrue("EHZ waterfall should have at least 8 tiles (cap + body pieces)",
                tileCount >= 8);
    }

    /**
     * Item 22: Verify CPZ Platform art decompresses from ROM.
     * ROM reference: ArtNem_CPZElevator
     * Address: Sonic2Constants.ART_NEM_CPZ_ELEVATOR_ADDR = 0x82216
     *
     * <p>Used by Object 0x19 (CPZPlatform / Generic Platform B) in CPZ, OOZ, and WFZ.
     * The platform art includes 4 frame sizes: large (32px), small (24px),
     * wide (64px), and medium (32px).
     */
    @Test
    public void testCPZPlatformArtDecompresses() throws IOException {
        Rom rom = romRule.rom();
        FileChannel channel = rom.getFileChannel();
        channel.position(Sonic2Constants.ART_NEM_CPZ_ELEVATOR_ADDR);

        byte[] artData = NemesisReader.decompress(channel);
        assertNotNull("CPZ platform art should decompress successfully", artData);
        assertTrue("CPZ platform art should contain pattern data (multiple of 32 bytes)",
                artData.length > 0 && artData.length % 32 == 0);

        int tileCount = artData.length / 32;
        // The CPZ platform needs at least 16 tiles for a 4x4 piece
        assertTrue("CPZ platform should have at least 16 tiles for frame 0",
                tileCount >= 16);
    }

    /**
     * Item 23: Verify WFZ Platform art decompresses from ROM.
     * ROM reference: ArtNem_WfzFloatingPlatform
     * Address: Sonic2Constants.ART_NEM_WFZ_PLATFORM_ADDR = 0x8D96E
     *
     * <p>Used by Object 0x19 with zone-specific art config in Wing Fortress Zone.
     * WFZ uses a different platform art set than CPZ/OOZ.
     */
    @Test
    public void testWFZPlatformArtDecompresses() throws IOException {
        Rom rom = romRule.rom();
        FileChannel channel = rom.getFileChannel();
        channel.position(Sonic2Constants.ART_NEM_WFZ_PLATFORM_ADDR);

        byte[] artData = NemesisReader.decompress(channel);
        assertNotNull("WFZ platform art should decompress successfully", artData);
        assertTrue("WFZ platform art should contain pattern data (multiple of 32 bytes)",
                artData.length > 0 && artData.length % 32 == 0);

        int tileCount = artData.length / 32;
        assertTrue("WFZ platform should have at least 8 tiles",
                tileCount >= 8);
    }

    /**
     * Verify OOZ Platform art (alternate zone art for Obj19) also decompresses.
     * ROM reference: ArtNem_OOZElevator
     * Address: Sonic2Constants.ART_NEM_OOZ_ELEVATOR_ADDR = 0x810B8
     */
    @Test
    public void testOOZPlatformArtDecompresses() throws IOException {
        Rom rom = romRule.rom();
        FileChannel channel = rom.getFileChannel();
        channel.position(Sonic2Constants.ART_NEM_OOZ_ELEVATOR_ADDR);

        byte[] artData = NemesisReader.decompress(channel);
        assertNotNull("OOZ platform art should decompress successfully", artData);
        assertTrue("OOZ platform art should contain pattern data (multiple of 32 bytes)",
                artData.length > 0 && artData.length % 32 == 0);
    }

    /**
     * Verify all three platform art addresses are distinct.
     * Each zone's platform should use unique art data.
     */
    @Test
    public void testPlatformArtAddressesAreDistinct() {
        assertNotEquals("CPZ and OOZ platform art should be at different addresses",
                Sonic2Constants.ART_NEM_CPZ_ELEVATOR_ADDR,
                Sonic2Constants.ART_NEM_OOZ_ELEVATOR_ADDR);
        assertNotEquals("CPZ and WFZ platform art should be at different addresses",
                Sonic2Constants.ART_NEM_CPZ_ELEVATOR_ADDR,
                Sonic2Constants.ART_NEM_WFZ_PLATFORM_ADDR);
        assertNotEquals("OOZ and WFZ platform art should be at different addresses",
                Sonic2Constants.ART_NEM_OOZ_ELEVATOR_ADDR,
                Sonic2Constants.ART_NEM_WFZ_PLATFORM_ADDR);
    }

    /**
     * Verify waterfall art tile base matches the expected VRAM tile index.
     * ROM reference: make_art_tile(ArtTile_ArtNem_EhzWaterfall,1,0)
     * ArtTile_ArtNem_EhzWaterfall = $39E
     */
    @Test
    public void testWaterfallArtTileBase() {
        assertEquals("EHZ waterfall art tile base should be $39E",
                0x39E, Sonic2Constants.ART_TILE_EHZ_WATERFALL);
    }
}
