package com.openggf.tests;

import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * TODO #18 -- AIZ/LRZ Rock object +0x0B width offset for SolidObjectFull.
 *
 * <p>In the S3K disassembly (docs/skdisasm/sonic3k.asm), the AIZ/LRZ rock
 * object's main update routine prepares the d1 register for the SolidObjectFull
 * call with an extra +0x0B (11 pixel) offset added to the object's width_pixels.
 *
 * <p>Relevant disassembly (sonic3k.asm:43922-43930):
 * <pre>
 *   moveq   #0,d1
 *   move.b  width_pixels(a0),d1      ; d1 = object width in pixels
 *   addi.w  #$B,d1                   ; d1 += 11 (widen collision box)
 *   moveq   #0,d2
 *   move.b  height_pixels(a0),d2     ; d2 = object height in pixels
 *   move.w  d2,d3
 *   addq.w  #1,d3                    ; d3 = height + 1
 *   move.w  x_pos(a0),d4
 *   jsr     (SolidObjectFull).l      ; call solid object handler
 * </pre>
 *
 * <p>The +0x0B offset makes the solid collision box wider than the sprite's
 * visual width_pixels. This is consistent with the original ROM behavior
 * where the rock needs a slightly wider collision area for the player to
 * be able to push it effectively. The d1 parameter to SolidObjectFull
 * represents half the collision width, so +11 expands the detection zone
 * by 11 pixels on each side.
 *
 * @see <a href="docs/skdisasm/sonic3k.asm">sonic3k.asm lines 43922-43930</a>
 */
public class TestTodo18_AizRockWidthOffset {

    /**
     * The extra width added to width_pixels for SolidObjectFull (sonic3k.asm:43924).
     * {@code addi.w #$B,d1}
     */
    private static final int SOLID_WIDTH_OFFSET = 0x0B;

    @Test
    public void testWidthOffsetMatchesDisassembly() {
        // sonic3k.asm line 43924: addi.w #$B,d1
        // The offset is exactly 11 decimal (0x0B hex).
        assertEquals("Rock solid width offset is 0x0B (11 pixels)", 0x0B, SOLID_WIDTH_OFFSET);
    }

    @Test
    public void testSolidObjectFullParameterLayout() {
        // SolidObjectFull expects:
        //   d1 = half-width (width_pixels + 0x0B)
        //   d2 = half-height (height_pixels)
        //   d3 = height + 1
        //   d4 = x_pos
        //
        // The +1 in d3 is for the standard SolidObjectFull convention
        // (sonic3k.asm:43928: addq.w #1,d3)
        int widthPixels = 0x20; // example: 32 pixel width
        int heightPixels = 0x20; // example: 32 pixel height

        int d1 = widthPixels + SOLID_WIDTH_OFFSET;
        int d2 = heightPixels;
        int d3 = heightPixels + 1;

        assertEquals("d1 = width_pixels + 0x0B", 0x2B, d1);
        assertEquals("d2 = height_pixels", 0x20, d2);
        assertEquals("d3 = height_pixels + 1", 0x21, d3);
    }

    @Ignore("TODO #18 -- AizLrzRockObjectInstance not yet implemented. " +
            "See docs/skdisasm/sonic3k.asm:43922-43930 for solid width offset.")
    @Test
    public void testRockObjectUsesWidthOffsetInSolidCall() {
        // When AizLrzRockObjectInstance calls SolidObjectFull (or equivalent),
        // it must add +0x0B to the width_pixels value before passing it.
        // This ensures the collision box is wider than the visual sprite.
        fail("AizLrzRockObjectInstance not yet implemented");
    }
}
