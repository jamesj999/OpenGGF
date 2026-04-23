package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.RuntimeManager;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kCNZEvents;
import com.openggf.game.sonic3k.objects.CnzMinibossInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless end-to-end test for the CNZ1 miniboss encounter. Drives
 * Sonic to the arena threshold X, lets the fight state machine run,
 * and confirms that the fight eventually terminates within the
 * trace-budget frame window.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kCnzMinibossHeadless {

    @AfterEach
    void tearDown() {
        RuntimeManager.destroyCurrent();
        com.openggf.game.session.SessionManager.clear();
    }

    @Test
    void arenaEntryFiresBossFlagAtThreshold() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();
        GameServices.camera().setX((short) Sonic3kConstants.CNZ_MINIBOSS_ARENA_MIN_X);
        fixture.stepFrame(false, false, false, false, false);

        assertTrue(getCnzEvents().isBossFlag(),
                "Boss_flag must be set when camera reaches arena min X");
    }

    @Test
    void bossSpawnsAndRunsStateMachine() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();
        GameServices.camera().setX((short) Sonic3kConstants.CNZ_MINIBOSS_ARENA_MIN_X);
        for (int i = 0; i < 60; i++) fixture.stepFrame(false, false, false, false, false);

        Optional<CnzMinibossInstance> boss = findBoss();
        assertTrue(boss.isPresent(), "CNZ miniboss instance must exist within 60 frames of arena entry");
        assertTrue(boss.get().getCurrentRoutine() >= 2,
                "Boss must leave routine 0 (Init) within 60 frames");
    }

    @Test
    void fightResolvesWithin400FramesOfArenaEntry() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();
        GameServices.camera().setX((short) Sonic3kConstants.CNZ_MINIBOSS_ARENA_MIN_X);

        // Stub six hits across the fight window by nudging the boss's simulateHitForTest
        // at roughly the ROM's expected hit cadence. This is a headless smoke test, not a
        // bit-perfect replay: the goal is to confirm the defeat path completes.
        Optional<CnzMinibossInstance> boss = Optional.empty();
        for (int i = 0; i < 400 && getCnzEvents().isBossFlag(); i++) {
            fixture.stepFrame(false, false, false, false, false);
            if (boss.isEmpty()) boss = findBoss();
            if (boss.isPresent() && i % 60 == 0) boss.get().simulateHitForTest();
        }
        assertFalse(getCnzEvents().isBossFlag(),
                "Boss_flag must be cleared (defeat path) within 400 frames of arena entry");
    }

    /**
     * Carry-forward from T8 reviewer (Minor 5): after Boss_flag falls, the arena
     * camera clamps must be released so the camera can pan naturally past the
     * arena boundaries.
     *
     * <p>The falling-edge release is driven by {@code Sonic3kCNZEvents.update()},
     * which detects the 1->0 transition of {@code bossFlag} and calls
     * {@code releaseArenaCameraClamps()}, restoring the camera bounds that were
     * snapshotted in {@code enterMinibossArena()}.
     */
    @Test
    void minibossDefeatRestoresCameraClampsOnFallingEdge() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();

        // Trigger arena entry so the camera bounds are locked to the arena box.
        GameServices.camera().setX((short) Sonic3kConstants.CNZ_MINIBOSS_ARENA_MIN_X);
        fixture.stepFrame(false, false, false, false, false);

        // Confirm arena lock is active and that the locked max X really is the arena
        // constant (catches a future regression where enterMinibossArena stops
        // applying CNZ_MINIBOSS_ARENA_MAX_X — the release assertion below would
        // silently pass otherwise if the natural CNZ1 maxX coincidentally equalled
        // 0x3260).
        assertTrue(getCnzEvents().isBossFlag(), "Precondition: Boss_flag must be set after arena entry");
        short lockedMaxX = GameServices.camera().getMaxX();
        assertEquals(
                (int) (short) Sonic3kConstants.CNZ_MINIBOSS_ARENA_MAX_X,
                (int) lockedMaxX,
                "Precondition: arena entry must clamp camera maxX to CNZ_MINIBOSS_ARENA_MAX_X");

        // Simulate defeat: clear the boss flag directly (mirrors CnzMinibossInstance.onEndGo).
        getCnzEvents().setBossFlag(false);

        // Step one frame so Sonic3kCNZEvents.update() detects the falling edge and
        // calls releaseArenaCameraClamps().
        fixture.stepFrame(false, false, false, false, false);

        // After release the camera max X must differ from the locked (arena) value.
        // Anchoring against the captured lockedMaxX (rather than re-deriving the
        // constant) makes the assertion read as "the value moved" and keeps the
        // intent explicit: release-watchdog must mutate the bound away from where
        // it was during the fight.
        short releasedMaxX = GameServices.camera().getMaxX();
        assertNotEquals(
                (int) lockedMaxX,
                (int) releasedMaxX,
                "Camera maxX must be restored to the pre-arena level bound after Boss_flag falls");
    }

    private static Sonic3kCNZEvents getCnzEvents() {
        Sonic3kLevelEventManager events =
                (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        return events.getCnzEvents();
    }

    private static Optional<CnzMinibossInstance> findBoss() {
        ObjectManager mgr = GameServices.level().getObjectManager();
        if (mgr == null) {
            return Optional.empty();
        }
        return mgr.getActiveObjects().stream()
                .filter(CnzMinibossInstance.class::isInstance)
                .map(CnzMinibossInstance.class::cast)
                .findFirst();
    }
}
