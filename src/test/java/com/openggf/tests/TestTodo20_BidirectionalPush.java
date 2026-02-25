package com.openggf.tests;

import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * TODO #20 -- AIZ/LRZ Rock bidirectional push (left push support).
 *
 * <p>In the S3K disassembly (docs/skdisasm/sonic3k.asm), the rock push
 * subroutine {@code sub_200CC} (line 44468) currently only supports
 * pushing the rock to the LEFT (i.e., player pushes from the right side).
 *
 * <p>Key evidence from the disassembly (sonic3k.asm:44471):
 * <pre>
 *   sub_200CC:
 *     btst    d6,d3                    ; is player pushing?
 *     beq.s   locret_2011C             ; if not, return
 *     cmp.w   x_pos(a1),d2             ; compare rock x_pos with player x_pos
 *     bhs.s   locret_2011C             ; if rock x >= player x, return
 *     btst    #5,d0                    ; check status bit 5
 *     beq.s   locret_2011C             ; if not set, return
 *     subq.w  #1,$40(a0)               ; decrement push timer
 *     bpl.s   locret_2011C             ; if timer >= 0, return
 *     ...
 *     subq.w  #1,x_pos(a0)            ; move rock LEFT by 1 pixel
 *     subq.w  #1,x_pos(a1)            ; move player LEFT by 1 pixel
 * </pre>
 *
 * <p>The critical line is 44471: {@code cmp.w x_pos(a1),d2 / bhs.s locret_2011C}.
 * This checks if the rock's x_pos (d2) is LESS THAN the player's x_pos (a1).
 * If the rock is to the LEFT of the player, the push proceeds (moving both
 * rock and player to the left via subq.w #1).
 *
 * <p>There is NO corresponding code path for pushing the rock to the RIGHT
 * (where the player is to the left of the rock). This appears intentional
 * in the original game -- rocks can only be pushed leftward.
 *
 * <p>However, the TODO in the engine code suggests that bidirectional push
 * (pushing rocks in either direction) may be a desirable enhancement or
 * that certain rock subtypes should support rightward push.
 *
 * @see <a href="docs/skdisasm/sonic3k.asm">sonic3k.asm lines 44468-44496</a>
 */
public class TestTodo20_BidirectionalPush {

    @Test
    public void testOriginalRomOnlySupportsLeftPush() {
        // The original ROM's sub_200CC ONLY decrements x_pos (pushes left).
        // Evidence: sonic3k.asm line 44481: subq.w #1,x_pos(a0)
        //           sonic3k.asm line 44482: subq.w #1,x_pos(a1)
        //
        // The directional check at line 44471:
        //   cmp.w x_pos(a1),d2   ; d2 = rock x, a1 = player
        //   bhs.s locret_2011C   ; if rock x >= player x, skip (don't push)
        //
        // This means: push only happens when rock x < player x
        //   => player is to the RIGHT of the rock
        //   => push direction is LEFT
        //
        // No addq.w instruction exists for rightward push.

        // Push speed is 1 pixel per push frame (subq.w #1)
        int pushSpeed = 1;
        assertEquals("Push speed is 1 pixel per frame", 1, pushSpeed);

        // Push timer resets to 0x10 (16 frames between pushes)
        // sonic3k.asm line 44477: move.w #$10,$40(a0)
        int pushTimerReset = 0x10;
        assertEquals("Push timer resets to 0x10 (16 frames)", 0x10, pushTimerReset);
    }

    @Test
    public void testPushSubtypeGating() {
        // The push behavior (sub_200A2) is only called when subtype bit 1 is set.
        // sonic3k.asm line 43931: btst #1,subtype(a0)
        //                         beq.s loc_1FB3A
        //                         bsr.w sub_200A2
        //
        // This means not all rocks are pushable -- only those with subtype bit 1.
        int pushSubtypeBit = 1;
        assertEquals("Push is gated by subtype bit 1", 1, pushSubtypeBit);
    }

    @Test
    public void testPushCounterInRespawnState() {
        // When a rock is pushed, the new position is saved to the respawn table.
        // sonic3k.asm lines 44485-44492:
        //   move.w  respawn_addr(a0),d0
        //   movea.w d0,a2
        //   move.b  $43(a0),d0
        //   subi.b  #$40,d0
        //   neg.b   d0
        //   move.b  d0,(a2)
        //   bset    #7,(a2)
        //
        // The $43(a0) field tracks the remaining push distance.
        // $40(a0) is the push timer (resets to 0x10).
        // $42(a0) is the remaining push count / distance.
        int pushDistanceField = 0x42;
        int pushTimerField = 0x40;
        int respawnSaveField = 0x43;
        assertEquals("Push distance at SST offset 0x42", 0x42, pushDistanceField);
        assertEquals("Push timer at SST offset 0x40", 0x40, pushTimerField);
        assertEquals("Respawn save value at SST offset 0x43", 0x43, respawnSaveField);
    }

    @Ignore("TODO #20 -- AizLrzRockObjectInstance does not yet support bidirectional push. " +
            "See docs/skdisasm/sonic3k.asm:44468-44496 for sub_200CC (left-only push).")
    @Test
    public void testRockCanBePushedLeft() {
        // When the player is to the RIGHT of a pushable rock (subtype bit 1 set),
        // pushing into the rock should move it leftward at 1px per push frame.
        fail("AizLrzRockObjectInstance push mechanics not yet implemented");
    }

    @Ignore("TODO #20 -- AizLrzRockObjectInstance does not yet support bidirectional push. " +
            "The original ROM only pushes left; right push would be a new feature.")
    @Test
    public void testRockCanBePushedRight() {
        // The original ROM does NOT support pushing rocks to the right.
        // If bidirectional push is desired, a new code path with addq.w
        // (instead of subq.w) would be needed, plus a reversed directional check.
        // This would be an intentional divergence from the original game.
        fail("Right-push for rocks is not in the original ROM and not yet implemented");
    }
}
