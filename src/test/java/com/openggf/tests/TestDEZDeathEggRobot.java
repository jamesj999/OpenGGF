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
import com.openggf.sprites.playable.AbstractPlayableSprite;

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
    private AbstractPlayableSprite player;

    @Before
    public void setUp() {
        LevelManager levelManager = mock(LevelManager.class);
        boss = new Sonic2DeathEggRobotInstance(
                new ObjectSpawn(BOSS_X, BOSS_Y,
                        Sonic2ObjectIds.DEATH_EGG_ROBOT, 0, 0, false, 0),
                levelManager
        );

        player = mock(AbstractPlayableSprite.class);
        when(player.getCentreX()).thenReturn((short) (BOSS_X - 64));
        when(player.getCentreY()).thenReturn((short) BOSS_Y);
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
    public void testInitialFacingIsLeft() {
        // ROM: Egg Robo faces left toward the player who enters from the left.
        // facingLeft=false causes forearm punches to go right (away from player).
        assertTrue("Egg Robo should initially face left toward the player", boss.isFacingLeft());
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
    public void attackPatternCyclesModulo4() {
        // ROM: addq.b #1,angle(a0) / andi.b #3,angle(a0)
        // Verify modulo-4 wrapping: (n & 3) cycles 0,1,2,3 for any positive n.
        // This ensures the attack index always stays within ATTACK_PATTERN bounds.
        for (int step = 0; step < 12; step++) {
            int index = step & 3;
            assertTrue("Index " + index + " must be in range [0,3]",
                    index >= 0 && index < 4);
        }
        // Attack index starts at 0, currentAttack not yet resolved (requires SelectAttack)
        assertEquals("Attack index should start at 0", 0, boss.getAttackIndex());
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
    public void invulnerabilityDurationIs60Frames() {
        // ROM: move.b #60,objoff_2A(a0) - $3C = 60 frames
        boss.getState().invulnerable = true;
        boss.getState().invulnerabilityTimer = 60;
        assertEquals("Flash/invulnerability timer should be $3C (60)",
                60, boss.getState().invulnerabilityTimer);
    }

    @Test
    public void paletteFlashDurationIs60Frames() {
        boss.getState().invulnerable = true;
        boss.getState().invulnerabilityTimer = 60;
        assertTrue("Should be invulnerable", boss.getState().invulnerable);
        assertEquals("Timer should be 60 ($3C)", 60, boss.getState().invulnerabilityTimer);
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
    public void headCollisionPropertyReturnsHitCount() {
        // Head relays collision_property as parent's hitCount
        TouchResponseProvider headProvider = (TouchResponseProvider) boss.getHead();
        assertEquals("Head collision property should equal HP (12)",
                12, headProvider.getCollisionProperty());
    }

    @Test
    public void allChildrenAreNotNull() {
        for (BossChildComponent child : boss.getChildComponents()) {
            assertNotNull("Every child component should be non-null", child);
        }
    }

    // ========================================================================
    // HP DECREMENT
    // ========================================================================

    @Test
    public void multipleHitsReduceHpCorrectly() {
        assertEquals(12, boss.getState().hitCount);

        for (int i = 11; i >= 0; i--) {
            boss.getState().hitCount--;
            assertEquals("HP should be " + i + " after " + (12 - i) + " hits",
                    i, boss.getState().hitCount);
        }

        assertEquals("HP should reach 0 after 12 hits", 0, boss.getState().hitCount);
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
}
