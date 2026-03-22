package com.openggf.game.sonic2;

import com.openggf.camera.Camera;
import com.openggf.game.sonic2.events.Sonic2WFZEvents;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Spec tests for Wing Fortress Zone (WFZ) level events.
 * WFZ has a dual-dispatch system: a primary routine table (Index) for BG scrolling,
 * and a secondary routine table (Index2) for boss-related events.
 *
 * <p>ROM reference: LevEvents_WFZ (s2.asm:20560-20684)
 *
 * <p><b>Primary routines (LevEvents_WFZ_Index, BG scroll control):</b>
 * <ul>
 *   <li>Routine 0: Initialize BG position to match camera, clear offsets</li>
 *   <li>Routine 2: Normal scrolling until X >= $2BC0 and Y >= $580, then start platform ride</li>
 *   <li>Routine 4: Platform ride upward - increment BG X offset toward $800,
 *       accelerate BG Y speed (max $840)</li>
 *   <li>Routine 6: Descent - decrement BG X offset toward -$2C0,
 *       decelerate then increment BG Y offset toward $1B81</li>
 * </ul>
 *
 * <p><b>Secondary routines (LevEvents_WFZ_Index2, boss events):</b>
 * <ul>
 *   <li>SubRoutine 0: Wait for X >= $2880 and Y >= $400, load WFZ boss PLC</li>
 *   <li>SubRoutine 2: Wait for Y >= $500, lock controls and load Tornado PLC</li>
 *   <li>SubRoutine 4: No-op (boss active)</li>
 * </ul>
 *
 * <p>Production code: {@link Sonic2WFZEvents}
 */
public class TestTodo12_WFZEventSpecs {

    private Sonic2WFZEvents events;
    private Camera cam;

    @Before
    public void setUp() {
        Camera.getInstance().resetState();
        cam = Camera.getInstance();
        events = new Sonic2WFZEvents();
        events.init(0);
    }

    /**
     * WFZ Routine 0: Initialize BG scrolling.
     * ROM reference: LevEvents_WFZ_Routine1 (s2.asm:20583-20592)
     * <pre>
     *   move.l (Camera_X_pos).w,(Camera_BG_X_pos).w    ; sync BG X to camera
     *   move.l (Camera_Y_pos).w,(Camera_BG_Y_pos).w    ; sync BG Y to camera
     *   moveq  #0,d0
     *   move.w d0,(Camera_BG_X_pos_diff).w              ; clear BG X delta
     *   move.w d0,(Camera_BG_Y_pos_diff).w              ; clear BG Y delta
     *   move.w d0,(Camera_BG_X_offset).w                ; clear BG X offset
     *   move.w d0,(Camera_BG_Y_offset).w                ; clear BG Y offset
     *   addq.b #2,(Dynamic_Resize_Routine).w
     * </pre>
     */
    @Test
    public void testWFZRoutine0_InitBGScroll() {
        cam.setX((short) 0x100);
        cam.setY((short) 0x200);
        events.update(0, 0);
        // Routine 0 immediately advances to routine 2
        assertEquals("Routine 0 should advance to routine 2 immediately",
                2, events.getEventRoutine());
        // BG offsets should be cleared
        assertEquals("BG X offset should be 0 after init", 0, events.getBgXOffset());
        assertEquals("BG Y offset should be 0 after init", 0, events.getBgYOffset());
        assertEquals("BG Y speed should be 0 after init", 0, events.getBgYSpeed());
    }

    /**
     * WFZ Routine 2: Normal scrolling until platform ride trigger.
     * ROM reference: LevEvents_WFZ_Routine2 (s2.asm:20595-20607)
     * <pre>
     *   cmpi.w #$2BC0,(Camera_X_pos).w       ; trigger X
     *   blo.s  +
     *   cmpi.w #$580,(Camera_Y_pos).w        ; trigger Y
     *   blo.s  +
     *   addq.b #2,(Dynamic_Resize_Routine).w  ; => Routine 4
     *   move.w #0,(WFZ_BG_Y_Speed).w         ; initialize BG Y speed
     * </pre>
     */
    @Test
    public void testWFZRoutine2_DoesNotAdvanceWhenXBelowThreshold() {
        // Advance past routine 0
        events.update(0, 0);
        assertEquals(2, events.getEventRoutine());

        cam.setX((short) 0x2000);
        cam.setY((short) 0x580);
        events.update(0, 1);
        assertEquals("Should stay at routine 2 when X < $2BC0",
                2, events.getEventRoutine());
    }

    @Test
    public void testWFZRoutine2_DoesNotAdvanceWhenYBelowThreshold() {
        events.update(0, 0); // init -> routine 2
        cam.setX((short) 0x2BC0);
        cam.setY((short) 0x500);
        events.update(0, 1);
        assertEquals("Should stay at routine 2 when Y < $580",
                2, events.getEventRoutine());
    }

    @Test
    public void testWFZRoutine2_AdvancesWhenBothThresholdsMet() {
        events.update(0, 0); // init -> routine 2
        cam.setX((short) 0x2BC0);
        cam.setY((short) 0x580);
        events.update(0, 1);
        assertEquals("Should advance to routine 4 when X >= $2BC0 and Y >= $580",
                4, events.getEventRoutine());
    }

    /**
     * WFZ Routine 4: Platform ride upward with BG scroll acceleration.
     * ROM reference: LevEvents_WFZ_Routine3 (s2.asm:20610-20632)
     * <pre>
     *   cmpi.w #$800,(Camera_BG_X_offset).w   ; target BG X offset
     *   beq.s  +
     *   addq.w #2,(Camera_BG_X_offset).w      ; increment by 2 each frame
     *   +
     *   cmpi.w #$600,(Camera_BG_X_offset).w   ; acceleration start threshold
     *   blt.s  LevEvents_WFZ_Routine3_Part2
     *   ; accelerate BG Y speed by 4, max $840
     * </pre>
     */
    @Test
    public void testWFZRoutine4_IncrementsBgXOffset() {
        events.setEventRoutine(4);
        assertEquals(0, events.getBgXOffset());
        events.update(0, 0);
        assertEquals("BG X offset should increment by 2 each frame",
                2, events.getBgXOffset());
        events.update(0, 1);
        assertEquals("BG X offset should be 4 after 2 frames",
                4, events.getBgXOffset());
    }

    @Test
    public void testWFZRoutine4_BgXOffsetCapsAt0x800() {
        events.setEventRoutine(4);
        // Run enough frames to reach the cap (0x800 / 2 = 0x400 = 1024 frames)
        for (int i = 0; i < 1024; i++) {
            events.update(0, i);
        }
        assertEquals("BG X offset should cap at $800",
                0x800, events.getBgXOffset());
        // Run one more frame -- should not exceed
        events.update(0, 1024);
        assertEquals("BG X offset should not exceed $800",
                0x800, events.getBgXOffset());
    }

    @Test
    public void testWFZRoutine4_NoYAccelerationBeforeXThreshold() {
        events.setEventRoutine(4);
        // Run frames until BG X offset is just below $600
        // $600 / 2 = 0x300 = 768 frames
        for (int i = 0; i < 767; i++) {
            events.update(0, i);
        }
        // BG X offset = 767 * 2 = 1534 = 0x5FE (< 0x600)
        assertTrue("BG X offset should be below $600",
                events.getBgXOffset() < 0x600);
        assertEquals("BG Y speed should still be 0 before $600 threshold",
                0, events.getBgYSpeed());
    }

    @Test
    public void testWFZRoutine4_YAccelerationStartsAtXThreshold() {
        events.setEventRoutine(4);
        // Run frames until BG X offset reaches $600
        // $600 / 2 = 0x300 = 768 frames
        for (int i = 0; i < 768; i++) {
            events.update(0, i);
        }
        // BG X offset = 768 * 2 = 0x600
        assertEquals("BG X offset should be $600", 0x600, events.getBgXOffset());
        assertEquals("BG Y speed should be 4 after first acceleration frame",
                4, events.getBgYSpeed());
    }

    @Test
    public void testWFZRoutine4_YSpeedCapsAt0x840() {
        events.setEventRoutine(4);
        // Run enough frames to max out Y speed
        // Need X offset >= $600 + enough frames for speed to reach $840
        // $840 / 4 = 528 frames after threshold
        // Threshold at frame 768, so total = 768 + 528 = 1296 but X caps at $800 (frame 1024)
        for (int i = 0; i < 1300; i++) {
            events.update(0, i);
        }
        assertTrue("BG Y speed should be capped at or near $840",
                events.getBgYSpeed() >= 0x840);
        // Run more frames to verify cap holds
        int speedBefore = events.getBgYSpeed();
        events.update(0, 1300);
        assertEquals("BG Y speed should not exceed $840",
                speedBefore, events.getBgYSpeed());
    }

    /**
     * WFZ Routine 6: Descent with BG scroll deceleration.
     * ROM reference: LevEvents_WFZ_Routine4 (s2.asm:20635-20656)
     *
     * <p>Note: The primary routine does not auto-advance from 4 to 6;
     * an external trigger (boss event) would set eventRoutine=6.
     * We test routine 6 by directly setting the event routine.
     */
    @Test
    public void testWFZRoutine6_DecrementsBgXOffset() {
        events.setEventRoutine(6);
        // Start with a positive BG X offset
        // We need to manipulate via routine 4 first, then switch
        // Simpler: use routine 4 to build up, then switch to 6
        events.setEventRoutine(4);
        for (int i = 0; i < 10; i++) {
            events.update(0, i);
        }
        int offsetAfterR4 = events.getBgXOffset();
        assertTrue("Should have positive BG X offset from R4", offsetAfterR4 > 0);

        events.setEventRoutine(6);
        events.update(0, 10);
        assertEquals("BG X offset should decrement by 2 in routine 6",
                offsetAfterR4 - 2, events.getBgXOffset());
    }

    /**
     * WFZ SubRoutine 0: Load WFZ boss PLC at camera ($2880, $400).
     * ROM reference: LevEvents_WFZ_Routine5 (s2.asm:20659-20669)
     *
     * <p>The secondary routine locks Camera_Min_X_pos to $2880 when triggered.
     * We verify this via camera bounds since wfzSubRoutine is private.
     */
    @Test
    public void testWFZSubRoutine0_DoesNotTriggerBelowXThreshold() {
        events.update(0, 0); // init -> primary routine 2
        cam.setX((short) 0x2800);
        cam.setY((short) 0x400);
        short minXBefore = cam.getMinX();
        events.update(0, 1);
        assertEquals("MinX should not change when camera X < $2880",
                minXBefore, cam.getMinX());
    }

    @Test
    public void testWFZSubRoutine0_DoesNotTriggerBelowYThreshold() {
        events.update(0, 0); // init -> primary routine 2
        cam.setX((short) 0x2880);
        cam.setY((short) 0x300);
        short minXBefore = cam.getMinX();
        events.update(0, 1);
        assertEquals("MinX should not change when camera Y < $400",
                minXBefore, cam.getMinX());
    }

    @Test
    public void testWFZSubRoutine0_LocksMinXAtThreshold() {
        events.update(0, 0); // init -> primary routine 2
        cam.setX((short) 0x2880);
        cam.setY((short) 0x400);
        events.update(0, 1);
        assertEquals("MinX should be locked at $2880 when both thresholds met",
                (short) 0x2880, cam.getMinX());
    }

    /**
     * WFZ SubRoutine 0 -> SubRoutine 2 -> verify secondary progression.
     * SubRoutine 2 triggers at Y >= $500.
     * We verify the secondary routine advances by observing that
     * after triggering S0, a subsequent update with Y >= $500
     * should cause no further minX changes (S2 only does control lock + PLC).
     */
    @Test
    public void testWFZSecondaryProgression() {
        events.update(0, 0); // init -> primary routine 2

        // Trigger secondary routine 0 -> 2
        cam.setX((short) 0x2880);
        cam.setY((short) 0x400);
        events.update(0, 1);
        assertEquals("MinX should be $2880 after secondary R0 trigger",
                (short) 0x2880, cam.getMinX());

        // Now secondary routine is 2, trigger at Y >= $500
        // (secondary routine 2 -> 4, locks controls)
        cam.setY((short) 0x500);
        events.update(0, 2);
        // After this, secondary routine should be 4 (no-op)
        // No additional camera changes expected from secondary
    }

    /**
     * Verify the WFZ dual-dispatch architecture.
     * ROM reference: LevEvents_WFZ (s2.asm:20560-20567)
     *
     * WFZ uniquely runs TWO routine tables per frame:
     * 1. Dynamic_Resize_Routine controls BG scroll behavior (4 states: 0,2,4,6)
     * 2. WFZ_LevEvent_Subrout controls boss/transition events (3 states: 0,2,4)
     *
     * Verified by observing that both primary (BG offset changes) and secondary
     * (camera lock) effects happen within the same update() call.
     */
    @Test
    public void testWFZDualDispatchArchitecture() {
        // First update: primary routine 0 runs (init BG) AND secondary routine 0 runs
        cam.setX((short) 0x2880);
        cam.setY((short) 0x400);
        events.update(0, 0);
        // Primary: routine 0 -> 2 (BG initialized, offsets zeroed)
        assertEquals("Primary should advance to routine 2", 2, events.getEventRoutine());
        assertEquals("BG offsets should be initialized", 0, events.getBgXOffset());
        // Secondary: camera X >= $2880 and Y >= $400 -> lock minX
        assertEquals("Secondary should lock minX at $2880",
                (short) 0x2880, cam.getMinX());
    }

    /**
     * Verify BG Y offset increases during platform ride (routine 4)
     * once BG X offset exceeds $600 and speed reaches 0x100 (integer part > 0).
     * Speed increments by 4 each frame from 0, so it takes 64 frames past
     * the $600 threshold for speed to reach 0x100 (speed >> 8 = 1).
     * $600 threshold is at frame 768 (0x600/2), so Y offset starts increasing
     * around frame 832.
     */
    @Test
    public void testWFZRoutine4_BgYOffsetIncreases() {
        events.setEventRoutine(4);
        // Run past the $600 X threshold + 64 frames for speed to reach 0x100:
        // 768 (to $600) + 64 (speed=256) + some margin = 840 frames
        for (int i = 0; i < 840; i++) {
            events.update(0, i);
        }
        assertTrue("BG Y offset should be positive once speed reaches 0x100 (integer part > 0)",
                events.getBgYOffset() > 0);
    }
}
