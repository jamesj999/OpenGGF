package com.openggf.tests;

import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * TODO #19 -- AIZ/LRZ Rock debris piece positions and velocities.
 *
 * <p>In the S3K disassembly (docs/skdisasm/sonic3k.asm), when the AIZ/LRZ
 * rock is destroyed, {@code sub_2011E} (line 44502) spawns debris child
 * objects using two data tables:
 *
 * <ul>
 *   <li>{@code off_2026E} (line 44643) -- Position offsets table (x,y pairs per piece)</li>
 *   <li>{@code off_202E4} (line 44703) -- Velocity table (x_vel, y_vel pairs per piece)</li>
 * </ul>
 *
 * <p>The position table is indexed by mapping_frame. Each entry is:
 * <pre>
 *   dc.w (count-1)          ; number of debris pieces minus 1
 *   dc.b x_offset, y_offset ; per piece (signed bytes)
 * </pre>
 *
 * <p>Example for mapping_frame 0 (word_2027E at line 44652):
 * <pre>
 *   dc.w 8-1                 ; 8 pieces
 *   dc.b -8, -$18            ; piece 0: x=-8, y=-24
 *   dc.b $B, -$1C            ; piece 1: x=+11, y=-28
 *   dc.b -4, -$C             ; piece 2: x=-4, y=-12
 *   dc.b $C, -4              ; piece 3: x=+12, y=-4
 *   dc.b -$C, 4              ; piece 4: x=-12, y=+4
 *   dc.b 4, $C               ; piece 5: x=+4, y=+12
 *   dc.b -$C, $1C            ; piece 6: x=-12, y=+28
 *   dc.b $C, $1C             ; piece 7: x=+12, y=+28
 * </pre>
 *
 * <p>The velocity table (word_202F4 at line 44712) for mapping_frame 0:
 * <pre>
 *   dc.w -$300, -$300        ; piece 0 velocity
 *   dc.w -$2C0, -$280        ; piece 1 velocity
 *   ...
 *   dc.w -$200, -$100        ; piece 7 velocity
 * </pre>
 *
 * @see <a href="docs/skdisasm/sonic3k.asm">sonic3k.asm lines 44502-44722</a>
 */
public class TestTodo19_AizRockDebris {

    // Position data for mapping_frame 0 (word_2027E, sonic3k.asm:44652-44661)
    private static final int FRAME_0_PIECE_COUNT = 8;
    private static final int[][] FRAME_0_POSITIONS = {
            {-8, -0x18},   // piece 0
            {0x0B, -0x1C}, // piece 1
            {-4, -0x0C},   // piece 2
            {0x0C, -4},    // piece 3
            {-0x0C, 4},    // piece 4
            {4, 0x0C},     // piece 5
            {-0x0C, 0x1C}, // piece 6
            {0x0C, 0x1C},  // piece 7
    };

    // Velocity data for mapping_frame 0 (word_202F4, sonic3k.asm:44712-44720)
    private static final int[][] FRAME_0_VELOCITIES = {
            {-0x300, -0x300}, // piece 0
            {-0x2C0, -0x280}, // piece 1
            {-0x2C0, -0x280}, // piece 2
            {-0x280, -0x200}, // piece 3
            {-0x280, -0x180}, // piece 4
            {-0x240, -0x180}, // piece 5
            {-0x240, -0x100}, // piece 6
            {-0x200, -0x100}, // piece 7
    };

    @Ignore("TODO #19 -- AizLrzRockObjectInstance debris spawning not yet implemented. " +
            "See docs/skdisasm/sonic3k.asm:44502-44510 for sub_2011E.")
    @Test
    public void testDebrisSpawnsCorrectNumberOfPieces() {
        // sub_2011E reads mapping_frame and indexes into off_2026E
        // to determine how many debris pieces to spawn.
        // Each piece is created as a child object with position offset
        // relative to the rock's x_pos/y_pos.
        fail("AizLrzRockObjectInstance debris spawning not yet implemented");
    }

    @Ignore("TODO #19 -- AizLrzRockObjectInstance debris spawning not yet implemented. " +
            "See docs/skdisasm/sonic3k.asm:44712-44720 for velocity data.")
    @Test
    public void testDebrisPiecesHaveCorrectVelocities() {
        // The velocity table (off_202E4) assigns x_vel and y_vel to each
        // debris piece. Pieces fly outward from the destroyed rock position.
        fail("AizLrzRockObjectInstance debris velocities not yet implemented");
    }
}
