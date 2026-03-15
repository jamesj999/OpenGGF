package com.openggf.game.sonic2;

import com.openggf.camera.Camera;
import com.openggf.game.sonic2.events.Sonic2MTZEvents;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Spec tests for Metropolis Zone (MTZ) level events.
 * MTZ has two separate event handlers: one for Acts 1-2 (no events) and one for Act 3 (boss).
 *
 * <p>ROM reference:
 * <ul>
 *   <li>LevEvents_MTZ (s2.asm:20471-20472): Acts 1/2 - immediate RTS (no events)</li>
 *   <li>LevEvents_MTZ3 (s2.asm:20476-20556): Act 3 - 5 routines for boss arena</li>
 * </ul>
 *
 * MTZ3 Routines (0-8, incremented by 2):
 * <ol start="0">
 *   <li>Routine 0: Wait for camera X >= $2530, set max Y to $450</li>
 *   <li>Routine 2: Wait for camera X >= $2980, lock left boundary, set max Y to $400</li>
 *   <li>Routine 4: Wait for camera X >= $2A80, lock arena at $2AB0, load boss PLC</li>
 *   <li>Routine 6: Lock min Y at $400, wait for boss spawn delay $5A, spawn MTZ boss</li>
 *   <li>Routine 8: Track camera position (boss active)</li>
 * </ol>
 *
 * <p>Production code: {@link Sonic2MTZEvents}
 */
public class TestTodo10_MTZEventSpecs {

    private Sonic2MTZEvents events;
    private Camera cam;

    @Before
    public void setUp() {
        Camera.resetInstance();
        cam = Camera.getInstance();
        events = new Sonic2MTZEvents();
        events.init(0);
    }

    /**
     * MTZ Acts 1 and 2 have no events (immediate RTS).
     * ROM reference: LevEvents_MTZ (s2.asm:20471-20472)
     * <pre>
     *   LevEvents_MTZ:
     *     rts
     * </pre>
     */
    @Test
    public void testMTZAct1_NoEvents() {
        cam.setX((short) 0x2530);  // Would trigger routine 0 in Act 3
        events.update(0, 0);  // act 0 = Act 1
        assertEquals("Act 1 should not advance routine", 0, events.getEventRoutine());
    }

    @Test
    public void testMTZAct2_NoEvents() {
        cam.setX((short) 0x2530);
        events.update(1, 0);  // act 1 = Act 2
        assertEquals("Act 2 should not advance routine", 0, events.getEventRoutine());
    }

    /**
     * MTZ3 Routine 0: Set initial Y boundary when camera reaches X >= $2530.
     * ROM reference: LevEvents_MTZ3_Routine1 (s2.asm:20491-20499)
     * <pre>
     *   cmpi.w #$2530,(Camera_X_pos).w
     *   blo.s  +
     *   move.w #$500,(Camera_Max_Y_pos).w            ; immediate Y max
     *   move.w #$450,(Camera_Max_Y_pos_target).w     ; target Y max
     *   move.w #$450,(Tails_Max_Y_pos).w
     *   addq.b #2,(Dynamic_Resize_Routine).w
     * </pre>
     */
    @Test
    public void testMTZ3Routine0_DoesNotAdvanceBelowThreshold() {
        cam.setX((short) 0x2000);
        events.update(2, 0);  // act 2 = Act 3
        assertEquals("Should stay at routine 0 when camera X < $2530",
                0, events.getEventRoutine());
    }

    @Test
    public void testMTZ3Routine0_AdvancesAndSetsYBounds() {
        cam.setX((short) 0x2530);
        events.update(2, 0);
        assertEquals("Should advance to routine 2 when camera X >= $2530",
                2, events.getEventRoutine());
        assertEquals("Camera_Max_Y_pos should be $500",
                (short) 0x500, cam.getMaxY());
        assertEquals("Camera_Max_Y_pos_target should be $450",
                (short) 0x450, cam.getMaxYTarget());
    }

    /**
     * MTZ3 Routine 2: Lock left boundary and lower max Y at X >= $2980.
     * ROM reference: LevEvents_MTZ3_Routine2 (s2.asm:20502-20511)
     * <pre>
     *   cmpi.w #$2980,(Camera_X_pos).w
     *   blo.s  +
     *   move.w (Camera_X_pos).w,(Camera_Min_X_pos).w   ; lock left
     *   move.w (Camera_X_pos).w,(Tails_Min_X_pos).w
     *   move.w #$400,(Camera_Max_Y_pos_target).w        ; new Y target
     *   move.w #$400,(Tails_Max_Y_pos).w
     *   addq.b #2,(Dynamic_Resize_Routine).w
     * </pre>
     */
    @Test
    public void testMTZ3Routine2_DoesNotAdvanceBelowThreshold() {
        events.setEventRoutine(2);
        cam.setX((short) 0x2900);
        events.update(2, 0);
        assertEquals("Should stay at routine 2 when camera X < $2980",
                2, events.getEventRoutine());
    }

    @Test
    public void testMTZ3Routine2_LocksLeftAndLowersY() {
        events.setEventRoutine(2);
        cam.setX((short) 0x2980);
        events.update(2, 0);
        assertEquals("Should advance to routine 4 when camera X >= $2980",
                4, events.getEventRoutine());
        assertEquals("MinX should be locked to camera X ($2980)",
                (short) 0x2980, cam.getMinX());
        assertEquals("Camera_Max_Y_pos_target should be $400",
                (short) 0x400, cam.getMaxYTarget());
    }

    /**
     * MTZ3 Routine 4: Lock boss arena at $2AB0 and load boss PLC.
     * ROM reference: LevEvents_MTZ3_Routine3 (s2.asm:20514-20529)
     * <pre>
     *   cmpi.w #$2A80,(Camera_X_pos).w
     *   blo.s  +
     *   move.w #$2AB0,(Camera_Min_X_pos).w    ; lock left at $2AB0
     *   move.w #$2AB0,(Camera_Max_X_pos).w    ; lock right at $2AB0
     *   move.w #$2AB0,(Tails_Min_X_pos).w
     *   move.w #$2AB0,(Tails_Max_X_pos).w
     *   addq.b #2,(Dynamic_Resize_Routine).w
     *   move.w #MusID_FadeOut,d0              ; fade out music
     *   clr.b  (Boss_spawn_delay).w           ; reset boss timer
     *   move.b #7,(Current_Boss_ID).w         ; MTZ boss ID = 7
     *   moveq  #PLCID_MtzBoss,d0
     * </pre>
     */
    @Test
    public void testMTZ3Routine4_DoesNotAdvanceBelowThreshold() {
        events.setEventRoutine(4);
        cam.setX((short) 0x2A00);
        events.update(2, 0);
        assertEquals("Should stay at routine 4 when camera X < $2A80",
                4, events.getEventRoutine());
    }

    @Test
    public void testMTZ3Routine4_LocksArena() {
        events.setEventRoutine(4);
        cam.setX((short) 0x2A80);
        events.update(2, 0);
        assertEquals("Should advance to routine 6 when camera X >= $2A80",
                6, events.getEventRoutine());
        assertEquals("Arena minX should be locked at $2AB0",
                (short) 0x2AB0, cam.getMinX());
        assertEquals("Arena maxX should be locked at $2AB0",
                (short) 0x2AB0, cam.getMaxX());
    }

    /**
     * MTZ3 Routine 6: Lock min Y at $400, spawn MTZ boss after 90-frame delay.
     * ROM reference: LevEvents_MTZ3_Routine4 (s2.asm:20532-20549)
     * <pre>
     *   cmpi.w #$400,(Camera_Y_pos).w
     *   blo.s  +
     *   move.w #$400,(Camera_Min_Y_pos).w     ; lock floor at $400
     *   move.w #$400,(Tails_Min_Y_pos).w
     *   +
     *   addq.b #1,(Boss_spawn_delay).w
     *   cmpi.b #$5A,(Boss_spawn_delay).w      ; wait 90 frames
     *   blo.s  ++
     *   move.b #ObjID_MTZBoss,id(a1)          ; spawn MTZ boss (Obj54)
     *   addq.b #2,(Dynamic_Resize_Routine).w
     *   move.w #MusID_Boss,d0                 ; play boss music
     * </pre>
     */
    @Test
    public void testMTZ3Routine6_LocksMinYWhenAboveThreshold() {
        events.setEventRoutine(6);
        cam.setY((short) 0x400);
        events.update(2, 0);
        assertEquals("MinY should be locked at $400 when camera Y >= $400",
                (short) 0x400, cam.getMinY());
    }

    @Test
    public void testMTZ3Routine6_DoesNotLockMinYBelowThreshold() {
        events.setEventRoutine(6);
        cam.setY((short) 0x300);
        short minYBefore = cam.getMinY();
        events.update(2, 0);
        assertEquals("MinY should not change when camera Y < $400",
                minYBefore, cam.getMinY());
    }

    @Test
    public void testMTZ3Routine6_DoesNotSpawnBossBeforeDelay() {
        events.setEventRoutine(6);
        cam.setY((short) 0x400);
        // Step 89 frames (0x59) -- one short of the $5A threshold
        for (int i = 0; i < 0x59; i++) {
            events.update(2, i);
        }
        assertEquals("Should stay at routine 6 before 90 frames ($5A)",
                6, events.getEventRoutine());
    }

    @Test
    public void testMTZ3Routine6_SpawnsBossAtDelay() {
        events.setEventRoutine(6);
        cam.setY((short) 0x400);
        // Step exactly 90 frames ($5A)
        for (int i = 0; i < 0x5A; i++) {
            events.update(2, i);
        }
        assertEquals("Should advance to routine 8 after 90 frames ($5A)",
                8, events.getEventRoutine());
    }

    /**
     * MTZ3 Routine 8: Track camera position (boss active).
     * ROM reference: LevEvents_MTZ3_Routine5 (s2.asm:20552-20556)
     * <pre>
     *   move.w (Camera_X_pos).w,(Camera_Min_X_pos).w
     *   move.w (Camera_Max_X_pos).w,(Tails_Max_X_pos).w
     *   move.w (Camera_X_pos).w,(Tails_Min_X_pos).w
     *   rts
     * </pre>
     */
    @Test
    public void testMTZ3Routine8_TracksCamera() {
        events.setEventRoutine(8);
        cam.setX((short) 0x2B00);
        cam.setMinX((short) 0x2A00);  // old minX
        events.update(2, 0);
        assertEquals("MinX should advance to camera X during boss fight",
                (short) 0x2B00, cam.getMinX());
        assertEquals("Should remain at routine 8", 8, events.getEventRoutine());
    }

    @Test
    public void testMTZ3Routine8_DoesNotRetreatMinX() {
        events.setEventRoutine(8);
        cam.setMinX((short) 0x2B00);
        cam.setX((short) 0x2A00);  // camera behind current minX
        events.update(2, 0);
        assertEquals("MinX should not retreat behind current value",
                (short) 0x2B00, cam.getMinX());
    }

    /**
     * Verify the full MTZ3 event sequence progresses correctly.
     */
    @Test
    public void testMTZ3FullSequence() {
        // Routine 0: trigger at X >= $2530
        cam.setX((short) 0x2530);
        events.update(2, 0);
        assertEquals(2, events.getEventRoutine());

        // Routine 2: trigger at X >= $2980
        cam.setX((short) 0x2980);
        events.update(2, 1);
        assertEquals(4, events.getEventRoutine());

        // Routine 4: trigger at X >= $2A80
        cam.setX((short) 0x2A80);
        events.update(2, 2);
        assertEquals(6, events.getEventRoutine());

        // Routine 6: 90-frame delay then spawn
        cam.setY((short) 0x400);
        for (int i = 0; i < 0x5A; i++) {
            events.update(2, 3 + i);
        }
        assertEquals(8, events.getEventRoutine());
    }
}
