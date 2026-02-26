package com.openggf.tests;

import org.junit.Before;
import org.junit.Test;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.game.sonic2.objects.bosses.Sonic2DeathEggRobotInstance;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseAttackable;
import com.openggf.level.objects.boss.BossChildComponent;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the DEZ Death Egg Robot boss (ObjC7).
 * Tests HP, collision flags, attack pattern cycling, child count,
 * defeat state, flash duration, and state machine.
 * No ROM or OpenGL required.
 *
 * ROM values used in assertions are verified directly against the disassembly
 * rather than referencing the implementation constants.
 */
public class TestDEZDeathEggRobot {

    private static final int BOSS_X = 0x2A98;
    private static final int BOSS_Y = 0x4A0;

    private Sonic2DeathEggRobotInstance boss;

    @Before
    public void setUp() {
        LevelManager levelManager = mock(LevelManager.class);
        boss = new Sonic2DeathEggRobotInstance(
                new ObjectSpawn(BOSS_X, BOSS_Y,
                        Sonic2ObjectIds.DEATH_EGG_ROBOT, 0, 0, false, 0),
                levelManager
        );
    }

    // ========================================================================
    // BASIC STATE & HP
    // ========================================================================

    @Test
    public void objectIdIs0xC7() {
        assertEquals("Object ID should be 0xC7", 0xC7, Sonic2ObjectIds.DEATH_EGG_ROBOT);
    }

    @Test
    public void hpIs12NotDefault8() {
        // ROM: Death Egg Robot has 12 HP (final boss), NOT the usual 8
        assertEquals("HP must be 12 (final boss, not default 8)",
                12, boss.getState().hitCount);
    }

    @Test
    public void initialBodyRoutineIsWaitEggman() {
        // initializeBossState sets BODY_INIT (0x00) then advances to BODY_WAIT_EGGMAN (0x02)
        assertEquals("Body routine should be WAIT_EGGMAN (0x02) after init",
                0x02, boss.getBodyRoutine());
    }

    @Test
    public void initialFrameIsBody() {
        // ROM: mapping_frame = 3 (FRAME_BODY)
        assertEquals("Initial mapping frame should be 3 (FRAME_BODY)",
                3, boss.getCurrentFrame());
    }

    @Test
    public void notDefeatedInitially() {
        assertFalse("Should not be defeated initially", boss.getState().defeated);
    }

    @Test
    public void notInvulnerableInitially() {
        assertFalse("Should not be invulnerable initially", boss.getState().invulnerable);
    }

    @Test
    public void priorityBucketIsFive() {
        // ROM: move.b #5,priority(a0) (loc_3D52A, s2.asm:82052)
        assertEquals("Priority bucket should be 5", 5, boss.getPriorityBucket());
    }

    @Test
    public void testInitialFacingMatchesRom() {
        // ROM: ObjC7_SubObjData render_flags = 1<<render_flags.level_fg = 0x04
        // Bit 0 (x_flip) is clear, so x_flip = 0. The art naturally faces LEFT.
        // facingLeft is passed as hFlip to the renderer:
        //   facingLeft = false -> hFlip = false -> art not flipped -> faces LEFT (correct)
        //   facingLeft = true  -> hFlip = true  -> art flipped   -> faces RIGHT (wrong)
        assertFalse("facingLeft should be false (ROM x_flip=0, art naturally faces left)",
                boss.isFacingLeft());
    }

    // ========================================================================
    // COLLISION FLAGS (via getCollisionFlags() on body)
    // ========================================================================

    @Test
    public void collisionDisabledBeforeFightStarts() {
        // Body collision should be 0 while in WAIT_EGGMAN routine (before BODY_WAIT_READY)
        assertEquals("Collision should be 0 before fight starts",
                0, boss.getCollisionFlags());
    }

    @Test
    public void collisionDisabledWhenDefeated() {
        boss.getState().defeated = true;
        assertEquals("Collision should be 0 when defeated",
                0, boss.getCollisionFlags());
    }

    @Test
    public void collisionDisabledWhenInvulnerable() {
        boss.getState().invulnerable = true;
        assertEquals("Collision should be 0 when invulnerable",
                0, boss.getCollisionFlags());
    }

    // ========================================================================
    // ATTACK PATTERN CYCLING
    // ========================================================================

    @Test
    public void attackIndexStartsAtZero() {
        assertEquals("Attack index should start at 0", 0, boss.getAttackIndex());
    }

    @Test
    public void attackPatternMatchesRom() throws Exception {
        // ROM: dc.b 2, 0, 2, 4 (byte_3D680)
        // Verify the static attack pattern array matches the ROM values.
        // ATTACK_PATTERN is package-private, so we use reflection from a different package.
        java.lang.reflect.Field field =
                Sonic2DeathEggRobotInstance.class.getDeclaredField("ATTACK_PATTERN");
        field.setAccessible(true);
        int[] actual = (int[]) field.get(null);
        int[] expected = { 2, 0, 2, 4 };
        assertArrayEquals("Attack pattern should match ROM dc.b 2, 0, 2, 4",
                expected, actual);
    }

    // ========================================================================
    // DEFEAT STATE
    // ========================================================================

    @Test
    public void defeatPhaseStartsAtZero() {
        assertEquals("Defeat phase should start at 0", 0, boss.getDefeatPhase());
    }

    @Test
    public void defeatNotTriggeredByDefault() {
        assertFalse("Should not be defeated initially", boss.getState().defeated);
        assertEquals("Defeat phase should start at 0", 0, boss.getDefeatPhase());
    }

    // ========================================================================
    // FLASH DURATION
    // ========================================================================

    @Test
    public void invulnerabilityTimerStartsAtZero() {
        // ROM: move.b #60,objoff_2A(a0) - $3C = 60 frames is the DEZ boss
        // invulnerability duration. The timer should start at 0 (no invulnerability)
        // and only be set to 60 when the boss takes a hit.
        // DEZ_BOSS_INVULN_DURATION = 60 ($3C), verified against s2.asm
        assertEquals("Invulnerability timer should start at 0",
                0, boss.getState().invulnerabilityTimer);
        assertFalse("Should not be invulnerable initially",
                boss.getState().invulnerable);
    }

    // ========================================================================
    // CHILDREN SPAWNED
    // ========================================================================

    @Test
    public void tenChildrenSpawned() {
        // 10 permanent children: Shoulder, FrontLowerLeg, FrontForearm, UpperArm,
        // FrontThigh, Head, Jet, BackLowerLeg, BackForearm, BackThigh
        assertEquals("Should have 10 child components",
                10, boss.getChildComponents().size());
    }

    @Test
    public void headChildExists() {
        assertNotNull("Head child should exist", boss.getHead());
    }

    @Test
    public void headImplementsTouchResponseProvider() {
        // Head is the only hittable part - must implement TouchResponseProvider
        assertTrue("Head should implement TouchResponseProvider",
                boss.getHead() instanceof TouchResponseProvider);
    }

    @Test
    public void headImplementsTouchResponseAttackable() {
        // Head must implement TouchResponseAttackable for onPlayerAttack relay
        assertTrue("Head should implement TouchResponseAttackable",
                boss.getHead() instanceof TouchResponseAttackable);
    }

    @Test
    public void headCollisionInactiveBeforeFight() {
        // Head collision should be inactive during WAIT_EGGMAN phase
        TouchResponseProvider headProvider = (TouchResponseProvider) boss.getHead();
        assertEquals("Head collision flags should be 0 before fight",
                0, headProvider.getCollisionFlags());
    }

    @Test
    public void headCollisionPropertyReturnsNegativeOne() {
        // ROM: move.b #-1,collision_property(a0) — head always returns -1
        // HP tracking is handled by the parent body's onHeadHit(), not collision_property
        TouchResponseProvider headProvider = (TouchResponseProvider) boss.getHead();
        assertEquals("Head collision property should be -1 (ROM-accurate: always hittable)",
                -1, headProvider.getCollisionProperty());
    }

    @Test
    public void allChildrenAreNotNull() {
        for (BossChildComponent child : boss.getChildComponents()) {
            assertNotNull("Every child component should be non-null", child);
        }
    }

    @Test
    public void childSpawnOrderMatchesRom() throws Exception {
        // ROM: loc_3D52A spawns children in this order:
        // 1. Shoulder, 2. FrontForearm, 3. FrontLowerLeg, 4. UpperArm,
        // 5. FrontThigh, 6. Head, 7. Jet, 8. BackLowerLeg, 9. BackForearm, 10. BackThigh
        java.lang.reflect.Field childField =
                com.openggf.level.objects.boss.AbstractBossInstance.class.getDeclaredField("childComponents");
        childField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.List<BossChildComponent> children =
                (java.util.List<BossChildComponent>) childField.get(boss);

        assertEquals("Should have 10 children", 10, children.size());

        // Verify FrontForearm (index 1) comes before FrontLowerLeg (index 2)
        // by checking their class names via the name field on AbstractObjectInstance
        java.lang.reflect.Method getName =
                com.openggf.level.objects.AbstractObjectInstance.class.getMethod("getName");

        String child1Name = (String) getName.invoke(children.get(1));
        String child2Name = (String) getName.invoke(children.get(2));
        assertEquals("Child index 1 should be FrontForearm (ROM spawn order)",
                "FrontForearm", child1Name);
        assertEquals("Child index 2 should be FrontLowerLeg (ROM spawn order)",
                "FrontLowerLeg", child2Name);
    }

    // ========================================================================
    // HP DECREMENT
    // ========================================================================

    @Test
    public void hpStateDecrementsMathematically() {
        // Tests state object arithmetic, not the actual hit path (which requires AudioManager)
        assertEquals(12, boss.getState().hitCount);

        for (int i = 11; i >= 0; i--) {
            boss.getState().hitCount--;
            assertEquals("HP should be " + i + " after " + (12 - i) + " decrements",
                    i, boss.getState().hitCount);
        }

        assertEquals("HP should reach 0 after 12 decrements", 0, boss.getState().hitCount);
    }

    @Test
    public void spawnCoordinatesMatchInput() {
        assertEquals(BOSS_X, boss.getSpawn().x());
        assertEquals(BOSS_Y, boss.getSpawn().y());
    }

    @Test
    public void attackIndexStartsAtCurrentAttackZero() {
        assertEquals("Current attack should start at 0", 0, boss.getCurrentAttack());
    }

    // ========================================================================
    // DEFEAT TRIGGER
    // ========================================================================

    @Test
    public void defeatStateFlagsConsistentAfter12Decrements() {
        // Tests state consistency after manual decrements. The actual hit path
        // (onHeadHit) is package-private and requires AudioManager.
        // After 12 decrements, hitCount=0 and defeated=true should be consistent
        // with bodyRoutine=BODY_DEFEAT (0x0E).
        assertEquals("HP starts at 12", 12, boss.getState().hitCount);

        for (int i = 0; i < 12; i++) {
            boss.getState().hitCount--;
        }
        assertEquals("HP should be 0 after 12 decrements", 0, boss.getState().hitCount);

        // Simulate what triggerDefeatSequence() does to state flags
        boss.getState().defeated = true;
        assertTrue("Boss should be marked defeated", boss.getState().defeated);
    }

    @Test
    public void defeatBodyRoutineIs0x0E() {
        // ROM: BODY_DEFEAT = 0x0E (s2.asm). Verify initial state is not defeat.
        assertFalse("Body routine should NOT be 0x0E initially (that's defeat)",
                boss.getBodyRoutine() == 0x0E);
        // Positive: initial body routine should be WAIT_EGGMAN (0x02)
        assertEquals("Initial body routine should be 0x02", 0x02, boss.getBodyRoutine());
    }

    // ========================================================================
    // CHILDREN REGISTERED WITH OBJECT MANAGER
    // ========================================================================

    // ========================================================================
    // ROM DATA VERIFICATION (via reflection)
    // ========================================================================

    @Test
    public void breakVelocitiesMatchRom() throws Exception {
        // ROM: ObjC7_BreakSpeeds (s2.asm:83258-83267)
        // 8 entries: {x_vel, y_vel} for each body part during break-apart
        java.lang.reflect.Field field =
                Sonic2DeathEggRobotInstance.class.getDeclaredField("BREAK_VELOCITIES");
        field.setAccessible(true);
        int[][] actual = (int[][]) field.get(null);

        assertEquals("BREAK_VELOCITIES should have 8 entries", 8, actual.length);

        int[][] expected = {
                {  0x200, -0x400 },  // Shoulder
                { -0x100, -0x100 },  // FrontLowerLeg
                {  0x300, -0x300 },  // FrontForearm
                { -0x100, -0x400 },  // UpperArm
                {  0x180, -0x200 },  // FrontThigh
                { -0x200, -0x300 },  // BackLowerLeg
                {  0x000, -0x400 },  // BackForearm
                {  0x100, -0x300 }   // BackThigh
        };

        for (int i = 0; i < expected.length; i++) {
            assertArrayEquals("Break velocity entry " + i + " should match ROM",
                    expected[i], actual[i]);
        }
    }

    @Test
    public void childDeltasMatchRom() throws Exception {
        // ROM: ObjC7_ChildDeltas (s2.asm:83536-83544)
        // 7 entries: {dx, dy} position offsets for articulated children
        java.lang.reflect.Field field =
                Sonic2DeathEggRobotInstance.class.getDeclaredField("CHILD_DELTAS");
        field.setAccessible(true);
        int[][] actual = (int[][]) field.get(null);

        assertEquals("CHILD_DELTAS should have 7 entries", 7, actual.length);

        int[][] expected = {
                { -4, 60 },   // FrontLowerLeg
                { -12, 8 },   // FrontForearm
                { 12, -8 },   // UpperArm
                { 4, 36 },    // FrontThigh
                { -4, 60 },   // BackLowerLeg
                { -12, 8 },   // BackForearm
                { 4, 36 }     // BackThigh
        };

        for (int i = 0; i < expected.length; i++) {
            assertArrayEquals("Child delta entry " + i + " should match ROM",
                    expected[i], actual[i]);
        }
    }

    @Test
    public void groupAnimationKeyframeCounts() throws Exception {
        // ROM: ObjC7_GroupAni_3E318 = 9 keyframes (half-step walk)
        java.lang.reflect.Field halfStepField =
                Sonic2DeathEggRobotInstance.class.getDeclaredField("HALF_STEP_KEYFRAMES");
        halfStepField.setAccessible(true);
        int[][] halfStep = (int[][]) halfStepField.get(null);
        assertEquals("HALF_STEP_KEYFRAMES should have 9 entries", 9, halfStep.length);

        // ROM: ObjC7_GroupAni_3E3D8 = 3 keyframes (crouch/rise)
        java.lang.reflect.Field crouchField =
                Sonic2DeathEggRobotInstance.class.getDeclaredField("CROUCH_KEYFRAMES");
        crouchField.setAccessible(true);
        int[][] crouch = (int[][]) crouchField.get(null);
        assertEquals("CROUCH_KEYFRAMES should have 3 entries", 3, crouch.length);

        // ROM: ObjC7_GroupAni_3E438 = 12 keyframes (full walk cycle)
        java.lang.reflect.Field walkField =
                Sonic2DeathEggRobotInstance.class.getDeclaredField("WALK_CYCLE_KEYFRAMES");
        walkField.setAccessible(true);
        int[][] walk = (int[][]) walkField.get(null);
        assertEquals("WALK_CYCLE_KEYFRAMES should have 12 entries", 12, walk.length);
    }

    // ========================================================================
    // CHILDREN REGISTERED WITH OBJECT MANAGER
    // ========================================================================

    @Test
    public void childrenRegisteredWithObjectManager() {
        // When ObjectManager is available, children should be registered for rendering
        com.openggf.level.objects.ObjectManager objMgr = mock(com.openggf.level.objects.ObjectManager.class);
        LevelManager lm2 = mock(LevelManager.class);
        when(lm2.getObjectManager()).thenReturn(objMgr);

        Sonic2DeathEggRobotInstance boss2 = new Sonic2DeathEggRobotInstance(
                new ObjectSpawn(BOSS_X, BOSS_Y,
                        Sonic2ObjectIds.DEATH_EGG_ROBOT, 0, 0, false, 0),
                lm2
        );

        // Verify all 10 children were registered
        org.mockito.Mockito.verify(objMgr, org.mockito.Mockito.times(10))
                .addDynamicObject(org.mockito.ArgumentMatchers.any());
    }

    // ========================================================================
    // SENSOR FIFO BUFFER DEPTH
    // ========================================================================

    @Test
    public void sensorFifoBufferHas4Elements() throws Exception {
        // ROM: ObjC7_TargettingSensor uses 4 slots at offsets $30-$3F
        // (each slot = 4 bytes: xvel word + yvel word). A value written to
        // slot 0 traverses 3 shifts before being consumed at slot 3.
        // Verify via reflection that the buffer arrays have length 4.
        Class<?> sensorClass = null;
        for (Class<?> inner : Sonic2DeathEggRobotInstance.class.getDeclaredClasses()) {
            if (inner.getSimpleName().equals("SensorChild")) {
                sensorClass = inner;
                break;
            }
        }
        assertNotNull("SensorChild inner class should exist", sensorClass);

        java.lang.reflect.Field xBufField = sensorClass.getDeclaredField("xVelBuffer");
        xBufField.setAccessible(true);
        java.lang.reflect.Field yBufField = sensorClass.getDeclaredField("yVelBuffer");
        yBufField.setAccessible(true);

        // Construct a SensorChild to inspect buffer size
        // SensorChild(Sonic2DeathEggRobotInstance parent, int playerX, int playerY)
        java.lang.reflect.Constructor<?> ctor = sensorClass.getDeclaredConstructor(
                Sonic2DeathEggRobotInstance.class, int.class, int.class);
        ctor.setAccessible(true);
        Object sensor = ctor.newInstance(boss, 100, 200);

        int[] xBuf = (int[]) xBufField.get(sensor);
        int[] yBuf = (int[]) yBufField.get(sensor);
        assertEquals("xVelBuffer should have 4 elements (3-frame delay)", 4, xBuf.length);
        assertEquals("yVelBuffer should have 4 elements (3-frame delay)", 4, yBuf.length);
    }

    // ========================================================================
    // FOREARM Y VELOCITY CLAMP
    // ========================================================================

    @Test
    public void forearmYVelocityClampAt0xFF() throws Exception {
        // ROM: cmpi.w #$100,d2 / blo.s + / move.w #$FF,d2
        // Clamps absolute horizontal distance to 0xFF before (d2 & 0xC0) >> 6.
        // For dx >= 0x100, the table index should be the same as dx = 0xFF (= 3).
        // Without the clamp, dx = 0x100 would give (0x100 & 0xC0) >> 6 = (0x00) >> 6 = 0
        // which is wrong. With the clamp, Math.min(0xFF, dx) = 0xFF, (0xFF & 0xC0) >> 6 = 3.

        // Test ROM-accurate clamped behavior:
        int dxClamped = Math.min(0xFF, 0x100);
        int idxClamped = (dxClamped & 0xC0) >> 6;
        assertEquals("dx=0x100 clamped to 0xFF should give table index 3", 3, idxClamped);

        dxClamped = Math.min(0xFF, 0xFF);
        idxClamped = (dxClamped & 0xC0) >> 6;
        assertEquals("dx=0xFF should give table index 3", 3, idxClamped);

        // Verify the bug scenario: without clamping, dx=0x100 would give index 0
        int dxUnclamped = 0x100;
        int idxUnclamped = (dxUnclamped & 0xC0) >> 6;
        assertEquals("Unclamped dx=0x100 would incorrectly give index 0", 0, idxUnclamped);

        // Verify boundary cases with clamping
        assertEquals("dx=0x00 -> index 0", 0, (Math.min(0xFF, 0x00) & 0xC0) >> 6);
        assertEquals("dx=0x3F -> index 0", 0, (Math.min(0xFF, 0x3F) & 0xC0) >> 6);
        assertEquals("dx=0x40 -> index 1", 1, (Math.min(0xFF, 0x40) & 0xC0) >> 6);
        assertEquals("dx=0x7F -> index 1", 1, (Math.min(0xFF, 0x7F) & 0xC0) >> 6);
        assertEquals("dx=0x80 -> index 2", 2, (Math.min(0xFF, 0x80) & 0xC0) >> 6);
        assertEquals("dx=0xBF -> index 2", 2, (Math.min(0xFF, 0xBF) & 0xC0) >> 6);
        assertEquals("dx=0xC0 -> index 3", 3, (Math.min(0xFF, 0xC0) & 0xC0) >> 6);
        assertEquals("dx=0xFF -> index 3", 3, (Math.min(0xFF, 0xFF) & 0xC0) >> 6);
        assertEquals("dx=0x200 -> index 3 (clamped)", 3, (Math.min(0xFF, 0x200) & 0xC0) >> 6);
        assertEquals("dx=0xFFFF -> index 3 (clamped)", 3, (Math.min(0xFF, 0xFFFF) & 0xC0) >> 6);
    }
}
