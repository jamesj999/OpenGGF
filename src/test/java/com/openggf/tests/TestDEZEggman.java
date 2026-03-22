package com.openggf.tests;

import org.junit.Before;
import org.junit.Test;
import com.openggf.game.sonic2.objects.bosses.Sonic2DEZEggmanInstance;
import com.openggf.level.objects.DefaultObjectServices;

import java.lang.reflect.Field;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

    @Before
    public void setUp() {
        eggman = new Sonic2DEZEggmanInstance(SPAWN_X, SPAWN_Y);
        eggman.setServices(new DefaultObjectServices());
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
        assertEquals("After first update, routineSecondary should be STATE_WAIT_PLAYER (2)",
                2, routine);
    }

    @Test
    public void initialFrameIsStanding() {
        assertEquals("Initial frame should be FRAME_STANDING (0)",
                0, getIntField("currentFrame"));
    }

    // ========================================================================
    // OBJECT ID & SPAWN
    // ========================================================================

    @Test
    public void objectIdIs0xC6() {
        assertEquals("Object ID should be 0xC6",
                0xC6, eggman.getSpawn().objectId());
    }

    @Test
    public void priorityBucketIsFive() {
        // ROM: move.b #5,priority(a0)
        assertEquals("Priority bucket should be 5", 5, eggman.getPriorityBucket());
    }

    @Test
    public void isPersistent() {
        assertTrue("Eggman should be persistent (survives screen transitions)",
                eggman.isPersistent());
    }

    @Test
    public void spawnCoordinatesMatchConstructorArgs() {
        assertEquals("Spawn X should match constructor arg",
                SPAWN_X, eggman.getSpawn().x());
        assertEquals("Spawn Y should match constructor arg",
                SPAWN_Y, eggman.getSpawn().y());
    }

    @Test
    public void initialPositionMatchesSpawn() {
        assertEquals("Initial X should match spawn", SPAWN_X, eggman.getX());
        assertEquals("Initial Y should match spawn", SPAWN_Y, eggman.getY());
    }

    // ========================================================================
    // ROM CONSTANT VERIFICATION
    // ========================================================================

    @Test
    public void runVelocityMatchesRom() throws Exception {
        // ROM: move.w #$200,x_vel(a0)
        int value = getStaticIntField("RUN_VELOCITY");
        assertEquals("RUN_VELOCITY should be 0x200", 0x200, value);
    }

    @Test
    public void jumpXVelMatchesRom() throws Exception {
        // ROM: move.w #$80,x_vel(a0)
        int value = getStaticIntField("JUMP_X_VEL");
        assertEquals("JUMP_X_VEL should be 0x80", 0x80, value);
    }

    @Test
    public void jumpYVelMatchesRom() throws Exception {
        // ROM: move.w #-$200,y_vel(a0)
        int value = getStaticIntField("JUMP_Y_VEL");
        assertEquals("JUMP_Y_VEL should be -0x200", -0x200, value);
    }

    @Test
    public void gravityMatchesRom() throws Exception {
        // ROM: addi.w #$10,y_vel(a0) at loc_3D01E
        int value = getStaticIntField("GRAVITY");
        assertEquals("GRAVITY should be 0x10", 0x10, value);
    }

    @Test
    public void pauseTimerMatchesRom() throws Exception {
        // ROM: move.w #$18,objoff_2A(a0)
        int value = getStaticIntField("PAUSE_TIMER");
        assertEquals("PAUSE_TIMER should be 0x18", 0x18, value);
    }

    @Test
    public void jumpTimerMatchesRom() throws Exception {
        // ROM: move.w #$50,objoff_2A(a0)
        int value = getStaticIntField("JUMP_TIMER");
        assertEquals("JUMP_TIMER should be 0x50", 0x50, value);
    }

    @Test
    public void proximityRadiusMatchesRom() throws Exception {
        // ROM: addi.w #$5C,d2 / cmpi.w #$B8,d2
        int value = getStaticIntField("PROXIMITY_RADIUS");
        assertEquals("PROXIMITY_RADIUS should be 0x5C", 0x5C, value);
    }

    @Test
    public void jumpThresholdXMatchesRom() throws Exception {
        // ROM: cmpi.w #$810,x_pos(a0)
        int value = getStaticIntField("JUMP_THRESHOLD_X");
        assertEquals("JUMP_THRESHOLD_X should be 0x810", 0x810, value);
    }

    // ========================================================================
    // RUNNING ANIMATION
    // ========================================================================

    @Test
    public void runningFramesMatchRom() throws Exception {
        // ROM: Ani_objC5_objC6 anim 0: frames {2, 3, 4}
        int[] frames = getStaticIntArrayField("RUNNING_FRAMES");
        assertArrayEquals("RUNNING_FRAMES should be {2, 3, 4}",
                new int[]{2, 3, 4}, frames);
    }

    @Test
    public void runningAnimSpeedMatchesRom() throws Exception {
        // ROM: Ani_objC5_objC6 anim 0 speed = 5
        int value = getStaticIntField("RUNNING_ANIM_SPEED");
        assertEquals("RUNNING_ANIM_SPEED should be 5", 5, value);
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
        assertEquals("Timer should be 2 after first update", 2, getIntField("timer"));

        eggman.update(1, null);
        assertEquals("Timer should be 1 after second update", 1, getIntField("timer"));

        eggman.update(2, null);
        assertEquals("Timer should be 0 after third update", 0, getIntField("timer"));

        // Still in pause state because timer >= 0 after decrement (timer-- gives 0, not < 0)
        assertEquals("Should still be in PAUSE state (timer=0 is not < 0)",
                4, getIntField("routineSecondary"));

        // One more frame: timer-- gives -1, which IS < 0, so transition to RUN
        eggman.update(3, null);
        assertEquals("Should transition to RUN state after timer goes negative",
                6, getIntField("routineSecondary"));
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
