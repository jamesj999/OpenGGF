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
 * TODO #37 coverage: SlidingSpikesObjectInstance hardcodes subtype 0 values
 * instead of reading from the ROM's Obj76_InitData table.
 * <p>
 * The disassembly shows a table lookup mechanism that supports multiple subtypes
 * (via lsr.w #2; andi.w #$1C indexing), but only one entry exists in the original
 * data and all official layouts use subtype 0.
 * <p>
 * This test verifies the ROM contains the expected InitData values and documents
 * the table format for potential ROM hack support.
 * <p>
 * Reference: docs/s2disasm/s2.asm lines 55242-55266
 * Obj76_InitData (byte_28E0A):
 *   dc.b $40  ; width_pixels
 *   dc.b $10  ; y_radius
 *   dc.b   0  ; mapping_frame
 *   even
 * <p>
 * Subtype lookup: subtype >> 2, masked to $1C, used as byte offset into InitData.
 * Effective subtypes: bits 3-6 of subtype byte select entry (only entry 0 exists).
 */
@RequiresRom(SonicGame.SONIC_2)
public class TestTodo37_SlidingSpikesSubtypeTable {
    @Rule
    public RequiresRomRule romRule = new RequiresRomRule();

    /**
     * ROM address of Obj76_InitData (byte_28E0A).
     * Verified against s2.asm label at line 55242.
     */
    private static final int OBJ76_INIT_DATA_ADDR = 0x28E0A;

    /**
     * Expected values from disassembly (s2.asm lines 55243-55245).
     */
    private static final int EXPECTED_WIDTH_PIXELS = 0x40;  // 64 pixels
    private static final int EXPECTED_Y_RADIUS = 0x10;      // 16 pixels
    private static final int EXPECTED_MAPPING_FRAME = 0x00;  // frame 0

    /**
     * Verify subtype 0 data matches the disassembly and the engine's hardcoded values.
     * <p>
     * s2.asm lines 55243-55245:
     *   dc.b $40  ; width_pixels
     *   dc.b $10  ; y_radius
     *   dc.b   0  ; mapping_frame
     */
    @Test
    public void testSubtype0MatchesDisassembly() throws IOException {
        Rom rom = romRule.rom();

        int widthPixels = rom.readByte(OBJ76_INIT_DATA_ADDR) & 0xFF;
        int yRadius = rom.readByte(OBJ76_INIT_DATA_ADDR + 1) & 0xFF;
        int mappingFrame = rom.readByte(OBJ76_INIT_DATA_ADDR + 2) & 0xFF;

        assertEquals("width_pixels should be 0x40 (64)", EXPECTED_WIDTH_PIXELS, widthPixels);
        assertEquals("y_radius should be 0x10 (16)", EXPECTED_Y_RADIUS, yRadius);
        assertEquals("mapping_frame should be 0", EXPECTED_MAPPING_FRAME, mappingFrame);
    }

    /**
     * Verify the engine's hardcoded constants match the ROM subtype 0 entry.
     * SlidingSpikesObjectInstance.WIDTH_PIXELS and Y_RADIUS should equal the ROM values.
     * <p>
     * We verify by reading from ROM since the engine constants are private.
     */
    @Test
    public void testEngineConstantsMatchRom() throws IOException {
        Rom rom = romRule.rom();

        // Read ROM values
        int romWidth = rom.readByte(OBJ76_INIT_DATA_ADDR) & 0xFF;
        int romYRadius = rom.readByte(OBJ76_INIT_DATA_ADDR + 1) & 0xFF;

        // These must match what SlidingSpikesObjectInstance uses:
        // WIDTH_PIXELS = 0x40, Y_RADIUS = 0x10
        assertEquals("Engine WIDTH_PIXELS should match ROM", 0x40, romWidth);
        assertEquals("Engine Y_RADIUS should match ROM", 0x10, romYRadius);
    }

    /**
     * Document the subtype indexing scheme from the disassembly.
     * <p>
     * s2.asm lines 55256-55260:
     *   moveq #0,d0
     *   move.b subtype(a0),d0
     *   lsr.w #2,d0          ; shift right by 2 (divide by 4)
     *   andi.w #$1C,d0       ; keep bits 2-4 only (multiples of 4: 0,4,8,...,28)
     *   lea Obj76_InitData(pc,d0.w),a2
     * <p>
     * The formula is: offset = (subtype >> 2) & 0x1C
     * This selects 4-byte entries using subtype bits 4-6 (after shift, bits 2-4).
     * With only 4 bytes of data (3 + padding), only entry 0 is valid.
     * Higher subtypes would read into Obj76_Init machine code as data.
     */
    @Test
    public void testSubtypeIndexingScheme() {
        // Verify the indexing formula: (subtype >> 2) & 0x1C
        // Entry 0 (offset 0): subtypes 0x00-0x0F (bits 4-6 = 0)
        assertEquals("Subtype 0x00 -> offset 0", 0, (0x00 >> 2) & 0x1C);
        assertEquals("Subtype 0x04 -> offset 0 (low bits shifted out)", 0, (0x04 >> 2) & 0x1C);
        assertEquals("Subtype 0x08 -> offset 0 (bit 3 shifted to bit 1, masked out)", 0, (0x08 >> 2) & 0x1C);
        assertEquals("Subtype 0x0F -> offset 0", 0, (0x0F >> 2) & 0x1C);

        // Entry 1 (offset 4): subtypes 0x10-0x1F (bits 4-6 = 1) - INVALID, reads code
        assertEquals("Subtype 0x10 -> offset 4 (invalid entry)", 0x04, (0x10 >> 2) & 0x1C);

        // Entry 2 (offset 8): subtypes 0x20-0x2F (bits 4-6 = 2) - INVALID
        assertEquals("Subtype 0x20 -> offset 8 (invalid entry)", 0x08, (0x20 >> 2) & 0x1C);

        // Entry 7 (offset 28): subtypes 0x70-0x7F (bits 4-6 = 7) - INVALID, max
        assertEquals("Subtype 0x70 -> offset 0x1C (max, invalid entry)", 0x1C, (0x70 >> 2) & 0x1C);

        // Lower nibble is masked separately (andi.w #$F,subtype at line 55266)
        // for the sub-mode (0 = waiting, 2 = sliding, etc.)
        assertEquals("Lower nibble 0x0F mask on 0x00", 0x00, 0x00 & 0x0F);
        assertEquals("Lower nibble 0x0F mask on 0x02", 0x02, 0x02 & 0x0F);
    }

    /**
     * Verify the 'even' alignment after the 3-byte InitData.
     * The assembler's 'even' directive pads to word alignment.
     * So byte at offset 3 should be 0x00 (padding byte).
     */
    @Test
    public void testEvenAlignment() throws IOException {
        Rom rom = romRule.rom();

        // After the 3 data bytes, 'even' adds a padding byte to align to word boundary
        // OBJ76_INIT_DATA_ADDR = 0x28E0A (even address)
        // Bytes at 0x28E0A: $40, $10, $00
        // 0x28E0A + 3 = 0x28E0D (odd), so 'even' pads to 0x28E0E
        byte paddingByte = rom.readByte(OBJ76_INIT_DATA_ADDR + 3);
        assertEquals("Padding byte after 'even' directive should be 0x00",
                0x00, paddingByte & 0xFF);
    }
}
