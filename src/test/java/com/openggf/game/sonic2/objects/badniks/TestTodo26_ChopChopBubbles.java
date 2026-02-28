package com.openggf.game.sonic2.objects.badniks;

import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for ChopChop (Obj91) bubble spawning timer.
 * The ChopChop badnik periodically spawns small bubbles while patrolling.
 *
 * <p>ROM reference: Obj91_MakeBubble (s2.asm:73257-73274)
 * <pre>
 *   Obj91_MakeBubble:
 *     move.w #$50,Obj91_bubble_timer(a0)     ; reset timer to $50 = 80 frames
 *     jsrto  JmpTo19_AllocateObject
 *     bne.s  return_36EB0
 *     _move.b #ObjID_SmallBubbles,id(a1)     ; load bubble object
 *     move.b #6,subtype(a1)                   ; subtype 6 = Obj90_SubObjData2
 *     move.w x_pos(a0),x_pos(a1)             ; copy X position
 *     moveq  #$14,d0                          ; X offset = +$14
 *     btst   #render_flags.x_flip,render_flags(a0)
 *     beq.s  +
 *     neg.w  d0                               ; mirror offset if facing left
 *     +
 *     add.w  d0,x_pos(a1)                     ; apply X offset
 *     move.w y_pos(a0),y_pos(a1)             ; copy Y position
 *     addq.w #6,y_pos(a1)                     ; Y offset = +6
 * </pre>
 *
 * <p>Bubble timer decrement: Obj91_Main (s2.asm:73191-73194)
 * <pre>
 *   subq.b #1,Obj91_bubble_timer(a0)        ; decrement timer each frame
 *   bne.s  +                                 ; if not zero, skip
 *   bsr.w  Obj91_MakeBubble                  ; spawn bubble and reset timer
 * </pre>
 *
 * <p>TODO source: ChopChopBadnikInstance.java line 116
 */
public class TestTodo26_ChopChopBubbles {

    /**
     * Verify bubble spawning is integrated into ChopChop's patrol behavior.
     * The timer decrements each frame and spawns a bubble when it reaches zero.
     *
     * ROM reference: Obj91_Main (s2.asm:73191-73194)
     * <pre>
     *   subq.b #1,Obj91_bubble_timer(a0)  ; decrement
     *   bne.s  +                           ; branch if not zero
     *   bsr.w  Obj91_MakeBubble           ; spawn bubble and reset to $50
     * </pre>
     */
    @Ignore("TODO #26 -- ChopChop bubble spawning not implemented: requires SmallBubbles object support, " +
            "see docs/s2disasm/s2.asm:73191 and ChopChopBadnikInstance.java:116")
    @Test
    public void testBubbleSpawningDuringPatrol() {
        // The ChopChop should:
        // 1. Decrement bubble_timer each frame during PATROLLING state
        // 2. When timer reaches 0, spawn a SmallBubbles object (ObjID_SmallBubbles)
        //    at (chopchop_x + 20, chopchop_y + 6), mirrored if facing left
        // 3. Reset timer to 80 frames
        //
        // This requires the SmallBubbles object (from underwater bubbles system)
        // to be available for spawning.
        fail("Bubble spawning requires SmallBubbles object support");
    }

}
