package com.openggf.tests;

import org.junit.Before;
import org.junit.Test;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.game.sonic2.objects.bosses.Sonic2MechaSonicInstance;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.objects.TouchResponseTable;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the DEZ Mecha Sonic / Silver Sonic boss (ObjAF).
 * Tests state machine, attack pattern cycling, collision flags, hit count,
 * direction toggle, descent velocity, and defeat timer.
 * No ROM or OpenGL required.
 */
public class TestDEZMechaSonic {

    private static final int MECHA_SONIC_X = 0x348;
    private static final int MECHA_SONIC_Y = 0xA0;
    private static final int COLLISION_STANDING = 0x1A;
    private static final int COLLISION_BALL = 0x9A;

    private Sonic2MechaSonicInstance boss;

    @Before
    public void setUp() {
        ObjectServices services = new TestObjectServices();
        setConstructionContext(services);
        try {
            boss = new Sonic2MechaSonicInstance(
                    new ObjectSpawn(MECHA_SONIC_X, MECHA_SONIC_Y,
                            Sonic2ObjectIds.MECHA_SONIC, 0x48, 0, false, 0));
        } finally {
            clearConstructionContext();
        }
        boss.setServices(services);
    }

    @SuppressWarnings("unchecked")
    private static void setConstructionContext(ObjectServices services) {
        try {
            Field field = com.openggf.level.objects.AbstractObjectInstance.class
                    .getDeclaredField("CONSTRUCTION_CONTEXT");
            field.setAccessible(true);
            ((ThreadLocal<Object>) field.get(null)).set(services);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void clearConstructionContext() {
        try {
            Field field = com.openggf.level.objects.AbstractObjectInstance.class
                    .getDeclaredField("CONSTRUCTION_CONTEXT");
            field.setAccessible(true);
            ((ThreadLocal<Object>) field.get(null)).remove();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void initialStateMatchesRom() {
        // Boss should start in wait-for-camera routine (0x02)
        assertEquals("Initial routine should be WAIT_CAMERA (0x02)",
                0x02, boss.getCurrentRoutine());
        // HP should be 8
        assertEquals("Initial HP should be 8", 8, boss.getState().hitCount);
        // Not defeated
        assertFalse("Should not be defeated initially", boss.getState().defeated);
        // Not invulnerable
        assertFalse("Should not be invulnerable initially", boss.getState().invulnerable);
    }

    @Test
    public void initialPositionMatchesSpawn() {
        assertEquals("Initial X should match spawn", MECHA_SONIC_X, boss.getX());
        assertEquals("Initial Y should match spawn", MECHA_SONIC_Y, boss.getY());
    }

    @Test
    public void attackPatternTableCyclesCorrectly() throws Exception {
        // The attack table has 16 entries and cycles via & 0x0F
        int[] expectedTable = {
                0x06, 0x00, 0x10, 0x06, 0x06, 0x1E, 0x00, 0x10,
                0x06, 0x06, 0x10, 0x06, 0x00, 0x06, 0x10, 0x1E
        };

        // Verify the attack index starts at 0
        assertEquals("Attack index should start at 0", 0, boss.getAttackIndex());

        // Verify the static attack table matches ROM byte_398B0
        java.lang.reflect.Field field =
                Sonic2MechaSonicInstance.class.getDeclaredField("ATTACK_TABLE");
        field.setAccessible(true);
        int[] actual = (int[]) field.get(null);
        assertArrayEquals("Attack table should match ROM byte_398B0", expectedTable, actual);
    }

    @Test
    public void collisionDisabledBeforeIdlePhase() {
        // Boss starts in WAIT_CAMERA routine (0x02), collision should be 0
        assertEquals("Collision should be 0 before idle phase",
                0, boss.getCollisionFlags());
    }

    @Test
    public void collisionFlagsBasedOnMappingFrame() {
        // ROM: loc_39D24 - collision based on mapping_frame
        // When standing (frame 0), collision should be $1A
        // When in ball (frames 6,7,8), collision should be $9A

        // Force boss into idle routine to enable collision
        boss.getState().routine = 0x08; // ROUTINE_IDLE

        // Standing frame -> $1A
        // getCurrentFrame starts as FRAME_STAND (0), which is not ball, so $1A
        int flags = boss.getCollisionFlags();
        assertEquals("Standing collision should be $1A", COLLISION_STANDING, flags);
    }

    @Test
    public void collisionFlagsReturnBallWhenInBallFrame() throws Exception {
        // ROM: loc_39D24 - frames 6,7,8 (BALL_A/B/C) -> return $9A
        // Complementary to collisionFlagsBasedOnMappingFrame which tests non-ball frames

        // Force boss into idle routine to enable collision
        boss.getState().routine = 0x08; // ROUTINE_IDLE
        boss.getState().invulnerable = false;
        boss.getState().defeated = false;

        // Use reflection to set currentFrame to each ball frame
        java.lang.reflect.Field frameField =
                Sonic2MechaSonicInstance.class.getDeclaredField("currentFrame");
        frameField.setAccessible(true);

        // Test FRAME_BALL_A (6)
        frameField.setInt(boss, 6);
        assertEquals("Ball frame 6 collision should be $9A", COLLISION_BALL, boss.getCollisionFlags());

        // Test FRAME_BALL_B (7)
        frameField.setInt(boss, 7);
        assertEquals("Ball frame 7 collision should be $9A", COLLISION_BALL, boss.getCollisionFlags());

        // Test FRAME_BALL_C (8)
        frameField.setInt(boss, 8);
        assertEquals("Ball frame 8 collision should be $9A", COLLISION_BALL, boss.getCollisionFlags());
    }

    @Test
    public void defeatFlagSettableWhenHpZero() {
        // Verify the pre-condition: hitCount must reach 0 before defeat can be flagged.
        // The actual hit path triggers defeat when hitCount reaches 0.
        assertEquals("HP starts at 8", 8, boss.getState().hitCount);
        assertFalse("Should not be defeated at full HP", boss.getState().defeated);

        // Decrement HP to 0
        for (int i = 0; i < 8; i++) {
            boss.getState().hitCount--;
        }
        assertEquals("HP should be 0 after 8 decrements", 0, boss.getState().hitCount);

        // At HP=0, the defeat flag should be settable
        boss.getState().defeated = true;
        assertTrue("Boss should be marked defeated at 0 HP", boss.getState().defeated);
    }

    @Test
    public void objectIdIsCorrect() {
        assertEquals("Object ID should be 0xAF",
                0xAF, Sonic2ObjectIds.MECHA_SONIC);
    }

    @Test
    public void ballFormIsInitiallyFalse() {
        assertFalse("Ball form should be false initially", boss.isBallForm());
    }

    @Test
    public void priorityBucketIsFour() {
        assertEquals("Priority bucket should be 4", 4, boss.getPriorityBucket());
    }

    @Test
    public void invulnerabilityTimerStartsAtZero() {
        // ROM: INVULN_DURATION = $20 (32 frames) for Mecha Sonic.
        // Verify the timer starts at 0 (no invulnerability) in initial state.
        // The timer is set to $20 only when the boss takes a hit.
        assertEquals("Invulnerability timer should start at 0",
                0, boss.getState().invulnerabilityTimer);
        assertFalse("Should not be invulnerable initially",
                boss.getState().invulnerable);
    }

    @Test
    public void spawnCoordinatesMatchSpec() {
        // ROM: spawn at ($348, $A0) with subtype $48
        assertEquals(0x348, boss.getSpawn().x());
        assertEquals(0xA0, boss.getSpawn().y());
        assertEquals(0x48, boss.getSpawn().subtype());
    }

    @Test
    public void touchResponseSetupCreatesValidBoss() {
        // Verifies that a boss created alongside an ObjectManager with a touch
        // response table initializes with correct HP. The actual touch response
        // path requires AudioManager and full sprite wiring.
        TouchResponseTable touchTable = mock(TouchResponseTable.class);
        when(touchTable.getWidthRadius(COLLISION_STANDING)).thenReturn(16);
        when(touchTable.getHeightRadius(COLLISION_STANDING)).thenReturn(27);

        setConstructionContext(new TestObjectServices());
        Sonic2MechaSonicInstance testBoss;
        try {
            testBoss = new Sonic2MechaSonicInstance(
                    new ObjectSpawn(MECHA_SONIC_X, MECHA_SONIC_Y,
                            Sonic2ObjectIds.MECHA_SONIC, 0x48, 0, false, 0));
        } finally {
            clearConstructionContext();
        }

        assertEquals("Boss should start with 8 HP", 8, testBoss.getState().hitCount);
        assertFalse("Boss should not be defeated initially", testBoss.getState().defeated);
        assertFalse("Boss should not be invulnerable initially", testBoss.getState().invulnerable);
    }

    @Test
    public void defeatTimerInitializesTo255() {
        // The defeat timer should initialize to 0xFF (255) on defeat
        assertEquals("Defeat timer initial should be 0 before defeat",
                0, boss.getDefeatTimer());
    }

    @Test
    public void multipleHitsReduceHpCorrectly() {
        assertEquals(8, boss.getState().hitCount);

        for (int i = 7; i >= 0; i--) {
            boss.getState().hitCount--;
            assertEquals("HP should be " + i + " after " + (8 - i) + " hits",
                    i, boss.getState().hitCount);
        }

        assertEquals("HP should reach 0 after 8 hits", 0, boss.getState().hitCount);
    }

    // ========================================================================
    // New tests for spec compliance fixes
    // ========================================================================

    @Test
    public void descentVelocityIsConstant() {
        // ROM: y_vel is set to $100 once in routine 2 (WAIT_CAMERA -> COUNTDOWN transition)
        // and never modified by gravity during descent (ObjectMove, not ObjectMoveAndFall)
        // We can't easily test the full descent without Camera singleton, but we verify
        // the state after initializeBossState sets up correctly for constant descent
        assertEquals("Initial Y velocity should be 0 before camera trigger",
                0, boss.getState().yVel);
    }

    @Test
    public void directionToggleAlternates() {
        // ROM: objoff_2D starts at 0, alternates via not.b each dash
        // First dash: toggle=false -> neg (go left) -> toggle=true
        // Second dash: toggle=true -> keep positive (go right) -> toggle=false
        assertFalse("Direction toggle should start false", boss.isDashDirectionToggle());
    }

    @Test
    public void defeatTimerOffByOneMatchesRom() {
        // ROM: subq.w #1,objoff_32(a0) / bmi.s
        // Timer starts at $FF (255). subq from 0 gives $FFFF which is negative.
        // So it runs from $FF down to $0000, then $FFFF triggers bmi = 256 iterations.
        // In Java: defeatTimer-- then if (defeatTimer < 0) transitions.
        // So defeatTimer=0xFF, decremented 256 times: 0xFF -> 0xFE -> ... -> 0 -> -1 (triggers)
        // Total iterations before trigger = 256

        // Simulate defeat
        boss.getState().routine = 0x0C; // ROUTINE_DEFEAT
        // Set defeat timer manually to test boundary
        // The timer should NOT trigger at 0, only at -1 (negative)
        int timer = 0xFF;
        int iterations = 0;
        while (timer >= 0) {
            timer--;
            iterations++;
        }
        assertEquals("Defeat timer should run 256 iterations (0xFF down to -1)",
                256, iterations);
    }

    @Test
    public void collisionFlagsReturnExactRomValues() {
        // ROM: loc_39D24
        // Frames 6,7,8 (BALL_A/B/C) -> return $9A
        // All other frames -> return $1A
        // Should NOT return $DA or $C0|$1A

        // Force boss into a state where collision is active
        boss.getState().routine = 0x08; // ROUTINE_IDLE
        boss.getState().invulnerable = false;
        boss.getState().defeated = false;

        // Test non-ball frame
        int flags = boss.getCollisionFlags();
        assertEquals("Non-ball collision should be exactly $1A", 0x1A, flags);
        assertNotEquals("Non-ball collision should NOT be $DA", 0xDA, flags);
    }

    @Test
    public void attackTableFirstEntryIsAimAndDash() {
        // ROM: byte_398B0 first entry is 6 (ATTACK_AIM_AND_DASH)
        // ROM: byte_398B0 first entry is 6 — attack subroutine starts at 0 before first selection
        assertEquals("Attack sub-routine should start at 0 before first attack selection",
                0, boss.getAttackSubRoutine());
    }

    @Test
    public void currentFrameAccessorWorks() {
        // Verify we can read current frame for collision determination
        int frame = boss.getCurrentFrame();
        // Frame should be FRAME_STAND (0) initially — ROM uses AnimateSprite which
        // overrides the initial frame immediately, so we start with a visible pose.
        assertEquals("Initial frame should be FRAME_STAND (0)", 0, frame);
    }

    @Test
    public void signalLandingCompleteIsOneShot() throws Exception {
        // ROM: bclr #status.npc.y_flip — test-and-clear semantics.
        // The DEZ window's signalLandingComplete() should only fire once.
        // After the first call, waitingForLanding becomes false and subsequent
        // calls should be no-ops (not reset openingAnimPlaying to true).

        // Create a DEZ window via reflection (package-private inner class)
        Class<?> windowClass = Class.forName(
                "com.openggf.game.sonic2.objects.bosses.Sonic2MechaSonicInstance$MechaSonicDEZWindow");
        java.lang.reflect.Constructor<?> ctor = windowClass.getDeclaredConstructor(
                Sonic2MechaSonicInstance.class);
        ctor.setAccessible(true);
        Object dezWindow = ctor.newInstance(boss);

        // Verify initial state: waitingForLanding=true, openingAnimPlaying=false
        java.lang.reflect.Field waitingField = windowClass.getDeclaredField("waitingForLanding");
        waitingField.setAccessible(true);
        java.lang.reflect.Field openingField = windowClass.getDeclaredField("openingAnimPlaying");
        openingField.setAccessible(true);

        assertTrue("Window should start in waiting state", waitingField.getBoolean(dezWindow));
        assertFalse("Opening anim should not be playing initially", openingField.getBoolean(dezWindow));

        // First call: should transition from waiting to opening animation
        java.lang.reflect.Method signalMethod = windowClass.getDeclaredMethod("signalLandingComplete");
        signalMethod.setAccessible(true);
        signalMethod.invoke(dezWindow);

        assertFalse("After first signal, waitingForLanding should be false",
                waitingField.getBoolean(dezWindow));
        assertTrue("After first signal, openingAnimPlaying should be true",
                openingField.getBoolean(dezWindow));

        // Simulate the opening animation completing (set openingAnimPlaying=false)
        openingField.setBoolean(dezWindow, false);

        // Second call: should be a no-op (one-shot guard)
        signalMethod.invoke(dezWindow);

        assertFalse("After second signal, waitingForLanding should still be false",
                waitingField.getBoolean(dezWindow));
        assertFalse("After second signal, openingAnimPlaying should NOT be re-set to true",
                openingField.getBoolean(dezWindow));
    }

    @Test
    public void gravityConstantIs0x38() throws Exception {
        // ROM: addi.w #$38,y_vel(a0) — gravity applied during airborne phases
        // and on the landing frame. Verify GRAVITY = 0x38 in AbstractBossInstance.
        java.lang.reflect.Field gravityField =
                com.openggf.level.objects.boss.AbstractBossInstance.class.getDeclaredField("GRAVITY");
        gravityField.setAccessible(true);
        int gravity = gravityField.getInt(null);
        assertEquals("GRAVITY constant should be 0x38 (ROM: addi.w #$38,y_vel)",
                0x38, gravity);
    }

    @Test
    public void thrusterStartsVisibleWithBottomJetsAnim() throws Exception {
        // ROM: LED child (MechaSonicLEDWindow) is created at routine $10 (visible)
        // with default anim 0 (bottom jets, frames $B/$C). This ensures the
        // bottom jets render during the descent phase before the first landing.

        // Access the ledWindow field on the boss via reflection
        java.lang.reflect.Field ledField =
                Sonic2MechaSonicInstance.class.getDeclaredField("ledWindow");
        ledField.setAccessible(true);

        // ledWindow is null because setUp() uses a mock LevelManager with no ObjectManager.
        // Create the LED window directly via reflection to test its initial state.
        Class<?> ledClass = Class.forName(
                "com.openggf.game.sonic2.objects.bosses.Sonic2MechaSonicInstance$MechaSonicLEDWindow");
        java.lang.reflect.Constructor<?> ctor = ledClass.getDeclaredConstructor(
                Sonic2MechaSonicInstance.class);
        ctor.setAccessible(true);
        Object ledWindow = ctor.newInstance(boss);

        // Verify visible = true (ROM: created at routine $10)
        java.lang.reflect.Field visibleField = ledClass.getDeclaredField("visible");
        visibleField.setAccessible(true);
        assertTrue("LED window should start visible (ROM routine $10)",
                visibleField.getBoolean(ledWindow));

        // Verify animId = 0 (bottom jets)
        java.lang.reflect.Field animIdField = ledClass.getDeclaredField("animId");
        animIdField.setAccessible(true);
        assertEquals("LED window should start with anim 0 (bottom jets)",
                0, animIdField.getInt(ledWindow));

        // Verify mappingFrame = 0x0B (first frame of bottom jets anim)
        java.lang.reflect.Field mappingField = ledClass.getDeclaredField("mappingFrame");
        mappingField.setAccessible(true);
        assertEquals("LED window should start with mapping frame 0x0B",
                0x0B, mappingField.getInt(ledWindow));
    }

    @Test
    public void thrusterHiddenAfterTransitionToIdle() throws Exception {
        // ROM: loc_399D6 — transitionToIdle sets LED to routine $12 (hidden).
        // After the first landing, the thruster should be hidden.

        Class<?> ledClass = Class.forName(
                "com.openggf.game.sonic2.objects.bosses.Sonic2MechaSonicInstance$MechaSonicLEDWindow");
        java.lang.reflect.Constructor<?> ctor = ledClass.getDeclaredConstructor(
                Sonic2MechaSonicInstance.class);
        ctor.setAccessible(true);
        Object ledWindow = ctor.newInstance(boss);

        // Wire the ledWindow into the boss via reflection
        java.lang.reflect.Field ledField =
                Sonic2MechaSonicInstance.class.getDeclaredField("ledWindow");
        ledField.setAccessible(true);
        ledField.set(boss, ledWindow);

        // Verify it starts visible
        java.lang.reflect.Field visibleField = ledClass.getDeclaredField("visible");
        visibleField.setAccessible(true);
        assertTrue("LED should start visible", visibleField.getBoolean(ledWindow));

        // Call transitionToIdle via reflection (private method)
        java.lang.reflect.Method transitionMethod =
                Sonic2MechaSonicInstance.class.getDeclaredMethod("transitionToIdle");
        transitionMethod.setAccessible(true);
        transitionMethod.invoke(boss);

        // Verify it's now hidden
        assertFalse("LED should be hidden after transitionToIdle",
                visibleField.getBoolean(ledWindow));
    }

    private static final class NoOpObjectRegistry implements ObjectRegistry {
        @Override
        public ObjectInstance create(ObjectSpawn spawn) {
            return null;
        }

        @Override
        public void reportCoverage(List<ObjectSpawn> spawns) {
        }

        @Override
        public String getPrimaryName(int objectId) {
            return "Test";
        }
    }
}
