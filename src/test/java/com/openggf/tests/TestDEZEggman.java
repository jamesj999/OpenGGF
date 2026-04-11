package com.openggf.tests;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.openggf.game.sonic2.objects.bosses.Sonic2DEZEggmanInstance;
import com.openggf.level.objects.TestObjectServices;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the DEZ Eggman transition object (ObjC6 State2).
 * Tests initial state, ROM constant accuracy, state machine transitions,
 * and animation parameters.
 * No ROM or OpenGL required.
 *
 * ROM values used in assertions are verified directly against the disassembly
 * (s2.asm:81568-81696) rather than referencing the implementation constants.
 */
public class TestDEZEggman {

    /** ROM: Eggman spawn position from DEZ_1.bin object layout ($440, $168).
     *  Note: ($3F8, $160) is the solid wall child position, NOT Eggman's position. */
    private static final int SPAWN_X = 0x440;
    private static final int SPAWN_Y = 0x168;

    private Sonic2DEZEggmanInstance eggman;

    @BeforeEach
    public void setUp() {
        eggman = new Sonic2DEZEggmanInstance(SPAWN_X, SPAWN_Y);
        eggman.setServices(new TestObjectServices());
    }

    // ========================================================================
    // INITIAL STATE
    // ========================================================================

    @Test
    public void initialStateIsWaitPlayer() {
        // Constructor sets STATE_INIT (0), then updateInit() is called on first update
        // which advances to STATE_WAIT_PLAYER (2). But the constructor itself sets
        // routineSecondary = STATE_INIT = 0. The first update() call will advance it.
        // Let's call update once to trigger the init->wait transition.
        eggman.update(0, null);
        int routine = getIntField("routineSecondary");
        assertEquals(2, routine, "After first update, routineSecondary should be STATE_WAIT_PLAYER (2)");
    }

    @Test
    public void initialFrameIsStanding() {
        assertEquals(0, getIntField("currentFrame"), "Initial frame should be FRAME_STANDING (0)");
    }

    // ========================================================================
    // OBJECT ID & SPAWN
    // ========================================================================

    @Test
    public void objectIdIs0xC6() {
        assertEquals(0xC6, eggman.getSpawn().objectId(), "Object ID should be 0xC6");
    }

    @Test
    public void priorityBucketIsFive() {
        // ROM: move.b #5,priority(a0)
        assertEquals(5, eggman.getPriorityBucket(), "Priority bucket should be 5");
    }

    @Test
    public void isPersistent() {
        assertTrue(eggman.isPersistent(), "Eggman should be persistent (survives screen transitions)");
    }

    @Test
    public void spawnCoordinatesMatchConstructorArgs() {
        assertEquals(SPAWN_X, eggman.getSpawn().x(), "Spawn X should match constructor arg");
        assertEquals(SPAWN_Y, eggman.getSpawn().y(), "Spawn Y should match constructor arg");
    }

    @Test
    public void initialPositionMatchesSpawn() {
        assertEquals(SPAWN_X, eggman.getX(), "Initial X should match spawn");
        assertEquals(SPAWN_Y, eggman.getY(), "Initial Y should match spawn");
    }

    // ========================================================================
    // ROM CONSTANT VERIFICATION
    // ========================================================================

    @Test
    public void runVelocityMatchesRom() throws Exception {
        // ROM: move.w #$200,x_vel(a0)
        int value = getStaticIntField("RUN_VELOCITY");
        assertEquals(0x200, value, "RUN_VELOCITY should be 0x200");
    }

    @Test
    public void jumpXVelMatchesRom() throws Exception {
        // ROM: move.w #$80,x_vel(a0)
        int value = getStaticIntField("JUMP_X_VEL");
        assertEquals(0x80, value, "JUMP_X_VEL should be 0x80");
    }

    @Test
    public void jumpYVelMatchesRom() throws Exception {
        // ROM: move.w #-$200,y_vel(a0)
        int value = getStaticIntField("JUMP_Y_VEL");
        assertEquals(-0x200, value, "JUMP_Y_VEL should be -0x200");
    }

    @Test
    public void gravityMatchesRom() throws Exception {
        // ROM: addi.w #$10,y_vel(a0) at loc_3D01E
        int value = getStaticIntField("GRAVITY");
        assertEquals(0x10, value, "GRAVITY should be 0x10");
    }

    @Test
    public void pauseTimerMatchesRom() throws Exception {
        // ROM: move.w #$18,objoff_2A(a0)
        int value = getStaticIntField("PAUSE_TIMER");
        assertEquals(0x18, value, "PAUSE_TIMER should be 0x18");
    }

    @Test
    public void jumpTimerMatchesRom() throws Exception {
        // ROM: move.w #$50,objoff_2A(a0)
        int value = getStaticIntField("JUMP_TIMER");
        assertEquals(0x50, value, "JUMP_TIMER should be 0x50");
    }

    @Test
    public void proximityRadiusMatchesRom() throws Exception {
        // ROM: addi.w #$5C,d2 / cmpi.w #$B8,d2
        int value = getStaticIntField("PROXIMITY_RADIUS");
        assertEquals(0x5C, value, "PROXIMITY_RADIUS should be 0x5C");
    }

    @Test
    public void jumpThresholdXMatchesRom() throws Exception {
        // ROM: cmpi.w #$810,x_pos(a0)
        int value = getStaticIntField("JUMP_THRESHOLD_X");
        assertEquals(0x810, value, "JUMP_THRESHOLD_X should be 0x810");
    }

    // ========================================================================
    // RUNNING ANIMATION
    // ========================================================================

    @Test
    public void runningFramesMatchRom() throws Exception {
        // ROM: Ani_objC5_objC6 anim 0: frames {2, 3, 4}
        int[] frames = getStaticIntArrayField("RUNNING_FRAMES");
        assertArrayEquals(new int[]{2, 3, 4}, frames, "RUNNING_FRAMES should be {2, 3, 4}");
    }

    @Test
    public void runningAnimSpeedMatchesRom() throws Exception {
        // ROM: Ani_objC5_objC6 anim 0 speed = 5
        int value = getStaticIntField("RUNNING_ANIM_SPEED");
        assertEquals(5, value, "RUNNING_ANIM_SPEED should be 5");
    }

    // ========================================================================
    // PAUSE TIMER COUNTDOWN
    // ========================================================================

    @Test
    public void pauseStateCountsDown() {
        // Force into PAUSE state with known timer
        setIntField("routineSecondary", 4); // STATE_PAUSE
        setIntField("timer", 3);

        // Step 3 frames - timer should count down 3->2->1->0
        eggman.update(0, null);
        assertEquals(2, getIntField("timer"), "Timer should be 2 after first update");

        eggman.update(1, null);
        assertEquals(1, getIntField("timer"), "Timer should be 1 after second update");

        eggman.update(2, null);
        assertEquals(0, getIntField("timer"), "Timer should be 0 after third update");

        // Still in pause state because timer >= 0 after decrement (timer-- gives 0, not < 0)
        assertEquals(4, getIntField("routineSecondary"), "Should still be in PAUSE state (timer=0 is not < 0)");

        // One more frame: timer-- gives -1, which IS < 0, so transition to RUN
        eggman.update(3, null);
        assertEquals(6, getIntField("routineSecondary"), "Should transition to RUN state after timer goes negative");
    }

    // ========================================================================
    // REFLECTION HELPERS
    // ========================================================================

    private int getIntField(String fieldName) {
        try {
            Field field = Sonic2DEZEggmanInstance.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.getInt(eggman);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read field: " + fieldName, e);
        }
    }

    private void setIntField(String fieldName, int value) {
        try {
            Field field = Sonic2DEZEggmanInstance.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.setInt(eggman, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field: " + fieldName, e);
        }
    }

    private static int getStaticIntField(String fieldName) throws Exception {
        Field field = Sonic2DEZEggmanInstance.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(null);
    }

    private static int[] getStaticIntArrayField(String fieldName) throws Exception {
        Field field = Sonic2DEZEggmanInstance.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (int[]) field.get(null);
    }
}


