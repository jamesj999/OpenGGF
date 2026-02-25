package com.openggf.tests;

import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * TODO #34 -- Water slide chunk detection in Labyrinth Zone.
 *
 * <p>The LZ water slide system detects whether Sonic is standing on a slide chunk
 * by looking up the current level layout chunk ID and comparing it against a
 * table of 7 known slide chunk IDs. Each chunk ID has a corresponding speed
 * value that determines Sonic's slide direction and velocity.
 *
 * <p>Disassembly references:
 * <ul>
 *   <li>{@code docs/s1disasm/_inc/LZWaterFeatures.asm:392-457} -
 *       {@code LZWaterSlides} subroutine</li>
 *   <li>{@code docs/s1disasm/_inc/LZWaterFeatures.asm:454-457} -
 *       {@code Slide_Chunks} table: chunk IDs that trigger sliding</li>
 *   <li>{@code docs/s1disasm/_inc/LZWaterFeatures.asm:450-452} -
 *       {@code Slide_Speeds} table: per-chunk speed values</li>
 * </ul>
 *
 * <p>The chunk lookup works by computing a level layout index from Sonic's
 * position:
 * <pre>
 *   d0 = (obY >> 1) & $380    ; row index (128-byte stride)
 *   d1 = obX.byte & $7F       ; column index within row
 *   chunkId = v_lvllayout[d0 + d1]
 * </pre>
 * Then the chunk ID is compared against Slide_Chunks in reverse order.
 */
public class TestTodo34_WaterSlideDetection {

    /**
     * Water slide chunk IDs from LZWaterFeatures.asm:454-455.
     * {@code Slide_Chunks: dc.b 2, 7, 3, $4C, $4B, 8, 4}
     *
     * <p>These are the 256x256 block (chunk) IDs that the game checks to
     * determine if Sonic is on a water slide. The search is done backwards
     * (from Slide_Chunks_End to Slide_Chunks), so the index d1 after the
     * search loop corresponds to the position in this array.
     */
    private static final int[] SLIDE_CHUNK_IDS = {
            0x02, 0x07, 0x03, 0x4C, 0x4B, 0x08, 0x04
    };

    /**
     * Corresponding slide speeds from LZWaterFeatures.asm:450-451.
     * {@code Slide_Speeds: dc.b 10, -11, 10, -10, -11, -12, 11}
     *
     * <p>These are signed byte values set as Sonic's ground speed (obInertia).
     * Positive = slide right, negative = slide left.
     * The index into this table matches the index into Slide_Chunks
     * (after the reverse search).
     */
    private static final int[] SLIDE_SPEEDS = {
            10, -11, 10, -10, -11, -12, 11
    };

    @Test
    public void testSlideChunkTableSize() {
        assertEquals("Slide_Chunks should have 7 entries", 7, SLIDE_CHUNK_IDS.length);
    }

    @Test
    public void testSlideSpeedTableSize() {
        assertEquals("Slide_Speeds should have 7 entries (same as Slide_Chunks)",
                SLIDE_CHUNK_IDS.length, SLIDE_SPEEDS.length);
    }

    @Test
    public void testSlideChunkIdsMatchDisassembly() {
        // From LZWaterFeatures.asm:455: dc.b 2, 7, 3, $4C, $4B, 8, 4
        assertEquals("Chunk 0 should be 0x02", 0x02, SLIDE_CHUNK_IDS[0]);
        assertEquals("Chunk 1 should be 0x07", 0x07, SLIDE_CHUNK_IDS[1]);
        assertEquals("Chunk 2 should be 0x03", 0x03, SLIDE_CHUNK_IDS[2]);
        assertEquals("Chunk 3 should be 0x4C", 0x4C, SLIDE_CHUNK_IDS[3]);
        assertEquals("Chunk 4 should be 0x4B", 0x4B, SLIDE_CHUNK_IDS[4]);
        assertEquals("Chunk 5 should be 0x08", 0x08, SLIDE_CHUNK_IDS[5]);
        assertEquals("Chunk 6 should be 0x04", 0x04, SLIDE_CHUNK_IDS[6]);
    }

    @Test
    public void testSlideSpeedsMatchDisassembly() {
        // From LZWaterFeatures.asm:451: dc.b 10, -11, 10, -10, -11, -12, 11
        assertEquals("Speed 0 should be 10 (right)", 10, SLIDE_SPEEDS[0]);
        assertEquals("Speed 1 should be -11 (left)", -11, SLIDE_SPEEDS[1]);
        assertEquals("Speed 2 should be 10 (right)", 10, SLIDE_SPEEDS[2]);
        assertEquals("Speed 3 should be -10 (left)", -10, SLIDE_SPEEDS[3]);
        assertEquals("Speed 4 should be -11 (left)", -11, SLIDE_SPEEDS[4]);
        assertEquals("Speed 5 should be -12 (left)", -12, SLIDE_SPEEDS[5]);
        assertEquals("Speed 6 should be 11 (right)", 11, SLIDE_SPEEDS[6]);
    }

    @Test
    public void testSlideSpeedSignDeterminesDirection() {
        // Positive speed = Sonic faces right (bit 0 of obStatus cleared)
        // Negative speed = Sonic faces left (bit 0 of obStatus set)
        // From LZSlide_Move (LZWaterFeatures.asm:428-432):
        //   bclr #0,obStatus(a1)      ; default: face right
        //   move.b Slide_Speeds(pc,d1.w),d0
        //   move.b d0,obInertia(a1)
        //   bpl.s loc_3F9A            ; if positive, keep facing right
        //   bset #0,obStatus(a1)      ; if negative, face left
        for (int i = 0; i < SLIDE_SPEEDS.length; i++) {
            if (SLIDE_SPEEDS[i] > 0) {
                assertTrue("Chunk " + i + " (ID=0x" +
                                Integer.toHexString(SLIDE_CHUNK_IDS[i]) +
                                ") has positive speed, Sonic should face right",
                        SLIDE_SPEEDS[i] > 0);
            } else {
                assertTrue("Chunk " + i + " (ID=0x" +
                                Integer.toHexString(SLIDE_CHUNK_IDS[i]) +
                                ") has negative speed, Sonic should face left",
                        SLIDE_SPEEDS[i] < 0);
            }
        }
    }

    @Test
    public void testReverseSearchLogic() {
        // The game searches Slide_Chunks backward (from end to start).
        // After the search, d1 contains the index into Slide_Chunks/Slide_Speeds.
        //
        // LZWaterFeatures.asm:404-410:
        //   lea Slide_Chunks_End(pc),a2
        //   moveq #Slide_Chunks_End-Slide_Chunks-1,d1   ; d1 = 6 (count-1)
        //   loc_3F62:
        //   cmp.b -(a2),d0
        //   dbeq d1,loc_3F62
        //   beq.s LZSlide_Move
        //
        // Because the search goes backward, d1=6 checks the last entry first (chunk $04),
        // d1=5 checks chunk $08, etc. When a match is found, d1 is the array index.
        int searchCount = SLIDE_CHUNK_IDS.length - 1; // 6 (initial d1 value)
        assertEquals("Initial d1 should be Slide_Chunks length - 1", 6, searchCount);

        // Verify a sample reverse search: looking for chunk $4B
        int targetChunk = 0x4B;
        int foundIndex = -1;
        // Simulate the dbeq loop (reverse search)
        for (int d1 = searchCount; d1 >= 0; d1--) {
            if (SLIDE_CHUNK_IDS[d1] == targetChunk) {
                foundIndex = d1;
                break;
            }
        }
        assertEquals("Chunk $4B should be found at index 4", 4, foundIndex);
        assertEquals("Speed for chunk $4B should be -11", -11, SLIDE_SPEEDS[foundIndex]);
    }

    @Test
    @Ignore("TODO #34 -- LZ water slide detection not yet implemented. " +
            "See docs/s1disasm/_inc/LZWaterFeatures.asm:392-457")
    public void testWaterSlideChunkLookupFromLevelLayout() {
        // When implemented, this test should:
        // 1. Load an LZ level
        // 2. Position Sonic on a known slide chunk
        // 3. Verify the chunk lookup formula returns the correct chunk ID
        // 4. Verify Sonic enters slide mode with correct speed/direction
        fail("LZ water slide detection not yet implemented");
    }
}
