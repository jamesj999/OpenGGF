package com.openggf.tests;

import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.level.LevelManager;
import com.openggf.level.SeamlessLevelTransitionRequest;
import com.openggf.level.SeamlessLevelTransitionRequest.TransitionType;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.rings.RingManager;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;
import org.junit.*;

import static org.junit.Assert.*;

/**
 * Integration tests for nonstandard level entry/return paths.
 * <p>
 * {@link TestActTransitionHeadless} and {@link TestActTransitionIntentionalSkips}
 * cover {@code executeActTransition()} directly. This class covers the other
 * level lifecycle entry points that manipulate zone/act counters and route
 * through {@code loadCurrentLevel()} or the seamless transition dispatcher:
 * <ul>
 *   <li>{@code nextAct()} — act increment with wrap</li>
 *   <li>{@code nextZone()} — zone increment with wrap</li>
 *   <li>{@code advanceToNextLevel()} — progression (act then zone)</li>
 *   <li>{@code advanceZoneActOnly()} — counter-only advance for special stage entry</li>
 *   <li>{@code consumeSpecialStageReturnLevelReloadRequest()} — flag set by above</li>
 *   <li>{@code applySeamlessTransition()} — dispatcher routing RELOAD_SAME_LEVEL and
 *       RELOAD_TARGET_LEVEL through executeActTransition</li>
 *   <li>{@code respawnPlayer()} — death respawn through loadCurrentLevel(false)</li>
 * </ul>
 */
@RequiresRom(SonicGame.SONIC_2)
public class TestLevelEntryPathsHeadless {

    @ClassRule public static RequiresRomRule romRule = new RequiresRomRule();

    private static final int ZONE_EHZ = 0;
    private static final int ACT_1 = 0;
    private static final int ACT_2 = 1;
    private static SharedLevel sharedLevel;

    @BeforeClass
    public static void loadLevel() throws Exception {
        sharedLevel = SharedLevel.load(SonicGame.SONIC_2, ZONE_EHZ, ACT_1);
    }

    @AfterClass
    public static void cleanup() {
        if (sharedLevel != null) sharedLevel.dispose();
    }

    private HeadlessTestFixture fixture;

    @Before
    public void setUp() throws Exception {
        fixture = HeadlessTestFixture.builder()
                .withSharedLevel(sharedLevel)
                .startPosition((short) 96, (short) 655)
                .build();

        GameServices.camera().setFocusedSprite(fixture.sprite());
        GameServices.camera().setFrozen(false);

        // Force zone/act counters to known state. resetPerTest() does NOT
        // reset these, so tests that call nextAct()/nextZone() leave dirty
        // counters for subsequent tests. loadZoneAndAct() sets the counters
        // and routes through loadCurrentLevel(), which the SharedLevel data
        // supports.
        LevelManager lm = GameServices.level();
        if (lm.getCurrentZone() != ZONE_EHZ || lm.getCurrentAct() != ACT_1) {
            lm.loadZoneAndAct(ZONE_EHZ, ACT_1);
        }
    }

    // ========== nextAct() ==========

    @Test
    public void nextActAdvancesActCounter() throws Exception {
        LevelManager lm = GameServices.level();
        assertEquals("Should start at act 0", ACT_1, lm.getCurrentAct());

        lm.nextAct();

        assertEquals("Act should advance to 1 after nextAct()", ACT_2, lm.getCurrentAct());
    }

    @Test
    public void nextActRebuildsManagers() throws Exception {
        LevelManager lm = GameServices.level();
        ObjectManager beforeOM = lm.getObjectManager();
        RingManager beforeRM = lm.getRingManager();

        lm.nextAct();

        assertNotSame("ObjectManager should be rebuilt after nextAct()",
                beforeOM, lm.getObjectManager());
        assertNotSame("RingManager should be rebuilt after nextAct()",
                beforeRM, lm.getRingManager());
    }

    @Test
    public void nextActWrapsToZeroWhenExhausted() throws Exception {
        LevelManager lm = GameServices.level();

        // EHZ has 2 acts — two nextAct() calls should wrap back to 0
        lm.nextAct(); // act 0 → 1
        lm.nextAct(); // act 1 → 0 (wrap)

        assertEquals("Act should wrap to 0 after exhausting all acts",
                ACT_1, lm.getCurrentAct());
    }

    // ========== nextZone() ==========

    @Test
    public void nextZoneAdvancesZoneAndResetsAct() throws Exception {
        LevelManager lm = GameServices.level();

        lm.nextZone();

        assertEquals("Zone should advance by 1", ZONE_EHZ + 1, lm.getCurrentZone());
        assertEquals("Act should reset to 0 on zone change", ACT_1, lm.getCurrentAct());
    }

    @Test
    public void nextZoneRebuildsManagers() throws Exception {
        LevelManager lm = GameServices.level();
        ObjectManager beforeOM = lm.getObjectManager();

        lm.nextZone();

        assertNotSame("ObjectManager should be rebuilt after nextZone()",
                beforeOM, lm.getObjectManager());
    }

    // ========== advanceToNextLevel() ==========

    @Test
    public void advanceToNextLevelGoesToAct2First() throws Exception {
        LevelManager lm = GameServices.level();

        lm.advanceToNextLevel();

        assertEquals("Should still be in same zone", ZONE_EHZ, lm.getCurrentZone());
        assertEquals("Should advance to act 1", ACT_2, lm.getCurrentAct());
    }

    @Test
    public void advanceToNextLevelAdvancesZoneWhenActsExhausted() throws Exception {
        LevelManager lm = GameServices.level();

        // EHZ has 2 acts — two advanceToNextLevel() calls should move to next zone
        lm.advanceToNextLevel(); // EHZ act 0 → EHZ act 1
        lm.advanceToNextLevel(); // EHZ act 1 → next zone act 0

        assertEquals("Should advance to next zone when acts exhausted",
                ZONE_EHZ + 1, lm.getCurrentZone());
        assertEquals("Act should reset to 0 on zone advance",
                ACT_1, lm.getCurrentAct());
    }

    // ========== advanceZoneActOnly() ==========

    @Test
    public void advanceZoneActOnlyAdvancesCountersWithoutLoading() {
        LevelManager lm = GameServices.level();
        ObjectManager beforeOM = lm.getObjectManager();

        lm.advanceZoneActOnly();

        // Counters advance
        assertEquals("Act should advance to 1", ACT_2, lm.getCurrentAct());

        // But managers are NOT rebuilt (no level load happened)
        assertSame("ObjectManager should NOT change — no level load occurred",
                beforeOM, lm.getObjectManager());
    }

    @Test
    public void advanceZoneActOnlySetsSpecialStageReturnFlag() {
        LevelManager lm = GameServices.level();
        // Drain any pre-existing flag
        lm.consumeSpecialStageReturnLevelReloadRequest();

        lm.advanceZoneActOnly();

        assertTrue("Flag should be true after advanceZoneActOnly",
                lm.consumeSpecialStageReturnLevelReloadRequest());
    }

    @Test
    public void consumeSpecialStageReturnClearsFlag() {
        LevelManager lm = GameServices.level();
        lm.advanceZoneActOnly();

        // First consume returns true
        assertTrue(lm.consumeSpecialStageReturnLevelReloadRequest());
        // Second consume returns false (consumed)
        assertFalse("Flag should be cleared after consumption",
                lm.consumeSpecialStageReturnLevelReloadRequest());
    }

    @Test
    public void advanceZoneActOnlyClearsCheckpoint() {
        LevelManager lm = GameServices.level();
        assertNotNull("Checkpoint state should exist", lm.getCheckpointState());

        lm.advanceZoneActOnly();

        assertFalse("Checkpoint should be cleared after advanceZoneActOnly",
                lm.getCheckpointState().isActive());
    }

    // ========== applySeamlessTransition() dispatcher ==========

    @Test
    public void applySeamlessTransitionReloadTargetRoutesToExecuteActTransition() throws Exception {
        LevelManager lm = GameServices.level();
        ObjectManager beforeOM = lm.getObjectManager();

        SeamlessLevelTransitionRequest request = SeamlessLevelTransitionRequest
                .builder(TransitionType.RELOAD_TARGET_LEVEL)
                .targetZoneAct(ZONE_EHZ, ACT_2)
                .preserveMusic(true)
                .build();

        lm.applySeamlessTransition(request);

        // Verify it routed through executeActTransition (managers rebuilt, zone/act updated)
        assertEquals("Zone should be EHZ", ZONE_EHZ, lm.getCurrentZone());
        assertEquals("Act should be 2", ACT_2, lm.getCurrentAct());
        assertNotSame("ObjectManager should be rebuilt via executeActTransition",
                beforeOM, lm.getObjectManager());
    }

    @Test
    public void applySeamlessTransitionReloadSameRoutesToExecuteActTransition() throws Exception {
        LevelManager lm = GameServices.level();
        int zoneBefore = lm.getCurrentZone();
        int actBefore = lm.getCurrentAct();
        ObjectManager beforeOM = lm.getObjectManager();

        SeamlessLevelTransitionRequest request = SeamlessLevelTransitionRequest
                .builder(TransitionType.RELOAD_SAME_LEVEL)
                .preserveMusic(true)
                .build();

        lm.applySeamlessTransition(request);

        // RELOAD_SAME_LEVEL builds an adjusted request targeting current zone/act
        assertEquals("Zone should remain unchanged", zoneBefore, lm.getCurrentZone());
        assertEquals("Act should remain unchanged", actBefore, lm.getCurrentAct());
        assertNotSame("ObjectManager should be rebuilt even for same-level reload",
                beforeOM, lm.getObjectManager());
    }

    @Test
    public void applySeamlessTransitionNullRequestIsNoOp() {
        LevelManager lm = GameServices.level();
        ObjectManager beforeOM = lm.getObjectManager();

        lm.applySeamlessTransition(null);

        assertSame("Null request should be a no-op",
                beforeOM, lm.getObjectManager());
    }

    // ========== respawnPlayer() ==========

    @Test
    public void respawnPlayerRebuildsManagers() throws Exception {
        LevelManager lm = GameServices.level();
        ObjectManager beforeOM = lm.getObjectManager();
        RingManager beforeRM = lm.getRingManager();

        lm.respawnPlayer();

        assertNotSame("ObjectManager should be rebuilt after respawn",
                beforeOM, lm.getObjectManager());
        assertNotSame("RingManager should be rebuilt after respawn",
                beforeRM, lm.getRingManager());
    }

    @Test
    public void respawnPlayerPreservesZoneAndAct() throws Exception {
        LevelManager lm = GameServices.level();
        int zoneBefore = lm.getCurrentZone();
        int actBefore = lm.getCurrentAct();

        lm.respawnPlayer();

        assertEquals("Zone should not change on respawn", zoneBefore, lm.getCurrentZone());
        assertEquals("Act should not change on respawn", actBefore, lm.getCurrentAct());
    }

    // ========== loadZoneAndAct() ==========

    @Test
    public void loadZoneAndActSetsCountersAndLoads() throws Exception {
        LevelManager lm = GameServices.level();
        ObjectManager beforeOM = lm.getObjectManager();

        lm.loadZoneAndAct(ZONE_EHZ, ACT_2);

        assertEquals("Zone should be set to target", ZONE_EHZ, lm.getCurrentZone());
        assertEquals("Act should be set to target", ACT_2, lm.getCurrentAct());
        assertNotSame("Managers should be rebuilt after loadZoneAndAct",
                beforeOM, lm.getObjectManager());
    }
}
