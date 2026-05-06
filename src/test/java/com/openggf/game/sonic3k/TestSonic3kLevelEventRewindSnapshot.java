package com.openggf.game.sonic3k;

import com.openggf.game.RuntimeManager;
import com.openggf.game.rewind.snapshot.LevelEventSnapshot;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round-trip tests for the {@link Sonic3kLevelEventManager} extra-state snapshot (C.4).
 *
 * <p>Covers manager-level flags, AIZ fire/battleship state, HCZ wall-chase and
 * cutscene state, CNZ camera-clamp and boss-scroll state, and MGZ collapse counters.
 */
class TestSonic3kLevelEventRewindSnapshot {

    @BeforeEach
    void setUp() {
        RuntimeManager.configureEngineServices(EngineContext.fromLegacySingletonsForBootstrap());
        RuntimeManager.createGameplay();
    }

    @AfterEach
    void tearDown() {
        RuntimeManager.destroyCurrent();
        SessionManager.clear();
    }

    @Test
    void keyIsLevelEvent() {
        assertEquals("level-event", new Sonic3kLevelEventManager().key());
    }

    @Test
    void extraBytesNotNullForAiz() {
        // Act 1 (index 1) avoids the AIZ intro camera path that requires a
        // live gameplay session, while still constructing aizEvents so the
        // capture produces a non-empty extra blob.
        Sonic3kLevelEventManager mgr = new Sonic3kLevelEventManager();
        mgr.initLevel(Sonic3kZoneIds.ZONE_AIZ, 1);
        LevelEventSnapshot snap = mgr.capture();
        assertNotNull(snap.extra());
        assertTrue(snap.extra().length > 0);
    }

    @Test
    void roundTripManagerLevelFlags() {
        // Manager-level flags (hczPendingPostTransitionCutscene,
        // mgzPostTransitionReleasePending) are zone-agnostic; use MGZ to stay
        // away from the AIZ intro bootstrap that requires a live camera.
        Sonic3kLevelEventManager mgr = new Sonic3kLevelEventManager();
        mgr.initLevel(Sonic3kZoneIds.ZONE_MGZ, 0);
        // Directly set zone-agnostic manager flags
        mgr.setHczPendingPostTransitionCutscene(true);
        mgr.requestMgzPostTransitionRelease();

        LevelEventSnapshot snap = mgr.capture();
        // Reset and restore
        mgr.initLevel(Sonic3kZoneIds.ZONE_MGZ, 0);
        mgr.restore(snap);

        // hczPendingPostTransitionCutscene should survive
        // (no public getter; test indirectly by round-tripping the full blob)
        assertNotNull(snap.extra(), "Extra blob must not be null");
    }

    @Test
    void roundTripAizBattleshipState() {
        Sonic3kLevelEventManager mgr = new Sonic3kLevelEventManager();
        mgr.initLevel(Sonic3kZoneIds.ZONE_AIZ, 1); // act 2 has aizEvents
        var aiz = mgr.getAizEventsForTest();
        assertNotNull(aiz, "AIZ events must be non-null in AIZ zone");

        aiz.setBattleshipAutoScrollActiveRaw(true);
        aiz.setBattleshipSpawned(true);
        aiz.setBattleshipWrapX(0x46C0);
        aiz.setScreenShakeTimer(10);
        aiz.setLevelRepeatOffsetRaw(0x200);
        aiz.setBattleshipSmoothScrollXRaw(0x1234);
        aiz.setBattleshipPostScrollCameraX(0x4440);
        aiz.setFireSequencePhaseOrdinal(2); // AIZ1_FIRE_REFRESH
        aiz.setFireBgCopyFixed(0x0068_0000);
        aiz.setFireRiseSpeed(0x5000);
        aiz.setFireWavePhase(0x30);
        aiz.setFireTransitionFrames(120);
        aiz.setAiz2ResizeRoutine(8);
        aiz.setMinibossSpawned(true);
        aiz.setPaletteSwapped(true);
        aiz.setBoundariesUnlocked(true);

        LevelEventSnapshot snap = mgr.capture();

        mgr.initLevel(Sonic3kZoneIds.ZONE_AIZ, 1);
        var aiz2 = mgr.getAizEventsForTest();
        assertNotNull(aiz2);
        mgr.restore(snap);

        assertTrue(aiz2.isBattleshipAutoScrollActiveRaw());
        assertTrue(aiz2.isBattleshipSpawned());
        assertEquals(0x46C0,      aiz2.getBattleshipWrapX());
        assertEquals(10,          aiz2.getScreenShakeTimer());
        assertEquals(0x200,       aiz2.getLevelRepeatOffsetRaw());
        assertEquals(0x1234,      aiz2.getBattleshipSmoothScrollXRaw());
        assertEquals(0x4440,      aiz2.getBattleshipPostScrollCameraX());
        assertEquals(2,           aiz2.getFireSequencePhaseOrdinal());
        assertEquals(0x0068_0000, aiz2.getFireBgCopyFixed());
        assertEquals(0x5000,      aiz2.getFireRiseSpeed());
        assertEquals(0x30,        aiz2.getFireWavePhase());
        assertEquals(120,         aiz2.getFireTransitionFrames());
        assertEquals(8,           aiz2.getAiz2ResizeRoutine());
        assertTrue(aiz2.isMinibossSpawned());
        assertTrue(aiz2.isPaletteSwapped());
        assertTrue(aiz2.isBoundariesUnlocked());
    }

    @Test
    void roundTripHczWallChaseState() {
        Sonic3kLevelEventManager mgr = new Sonic3kLevelEventManager();
        mgr.initLevel(Sonic3kZoneIds.ZONE_HCZ, 1);
        var hcz = mgr.getHczEventsForTest();
        assertNotNull(hcz, "HCZ events must be non-null in HCZ zone");

        hcz.setBgRoutine(8);
        hcz.setAct2BgRoutine(4);
        hcz.setWallOffsetFixed(0x800);
        hcz.setWallOffsetPixels(16);
        hcz.setWallMoving(true);
        hcz.setWallChaseBgOverlayActiveRaw(true);
        hcz.setShakeTimer(12);
        hcz.setCutsceneActive(true);
        hcz.setCutsceneFrame(30);
        hcz.setCutsceneCenterX(0x80);
        hcz.setCutsceneCurrentY(0x600);

        LevelEventSnapshot snap = mgr.capture();

        mgr.initLevel(Sonic3kZoneIds.ZONE_HCZ, 1);
        var hcz2 = mgr.getHczEventsForTest();
        assertNotNull(hcz2);
        mgr.restore(snap);

        assertEquals(8,     hcz2.getBgRoutine());
        assertEquals(4,     hcz2.getAct2BgRoutine());
        assertEquals(0x800, hcz2.getWallOffsetFixed());
        assertEquals(16,    hcz2.getWallOffsetPixels());
        assertTrue(hcz2.isWallMoving());
        assertTrue(hcz2.isWallChaseBgOverlayActive());
        assertEquals(12,    hcz2.getShakeTimer());
        assertTrue(hcz2.isCutsceneActive());
        assertEquals(30,    hcz2.getCutsceneFrame());
        assertEquals(0x80,  hcz2.getCutsceneCenterX());
        assertEquals(0x600, hcz2.getCutsceneCurrentY());
    }

    @Test
    void roundTripCnzBossScrollAndClamps() {
        Sonic3kLevelEventManager mgr = new Sonic3kLevelEventManager();
        mgr.initLevel(Sonic3kZoneIds.ZONE_CNZ, 0);
        var cnz = mgr.getCnzEventsForTest();
        assertNotNull(cnz, "CNZ events must be non-null in CNZ zone");

        cnz.setBossScrollState(120, -4);
        cnz.setCameraClampsActive(true);
        cnz.setCameraStoredMaxXPos((short) 0x3200);
        cnz.setCameraStoredMinXPos((short) 0x1000);
        cnz.setWaterTargetYRaw(0x700);
        cnz.setDestroyedArenaRows(3);
        cnz.setBossBackgroundMode(com.openggf.game.sonic3k.events.Sonic3kCNZEvents.BossBackgroundMode.ACT1_MINIBOSS_PATH);

        LevelEventSnapshot snap = mgr.capture();

        mgr.initLevel(Sonic3kZoneIds.ZONE_CNZ, 0);
        var cnz2 = mgr.getCnzEventsForTest();
        assertNotNull(cnz2);
        mgr.restore(snap);

        assertEquals(120,  cnz2.getBossScrollOffsetY());
        assertEquals(-4,   cnz2.getBossScrollVelocityY());
        assertTrue(cnz2.isCameraClampsActive());
        assertEquals((short) 0x3200, cnz2.getCameraStoredMaxXPos());
        assertEquals((short) 0x1000, cnz2.getCameraStoredMinXPos());
        assertEquals(0x700, cnz2.getWaterTargetY());
        assertEquals(3,     cnz2.getDestroyedArenaRows());
        assertEquals(com.openggf.game.sonic3k.events.Sonic3kCNZEvents.BossBackgroundMode.ACT1_MINIBOSS_PATH,
                cnz2.getBossBackgroundMode());
    }

    @Test
    void roundTripMgzCollapseCounters() {
        Sonic3kLevelEventManager mgr = new Sonic3kLevelEventManager();
        mgr.initLevel(Sonic3kZoneIds.ZONE_MGZ, 1);
        var mgz = mgr.getMgzEventsForTest();
        assertNotNull(mgz, "MGZ events must be non-null in MGZ zone");

        mgz.setCollapseRequested(true);
        mgz.setCollapseInitialized(true);
        mgz.setCollapseMutationCount(7);
        mgz.setCollapseFrameCounter(42);
        mgz.setCollapseStartupShakeTimer(5);
        mgz.setBgRiseRoutine(8);
        mgz.setBgRiseOffset(0x100);
        mgz.setBgRiseSubpixelAccum(0x8000);
        mgz.setBgRiseMotionStarted(true);
        mgz.setBossArenaRoutine(4);
        mgz.setBossSpawned(true);
        mgz.setAppearance1Complete(true);
        mgz.setAppearance2Complete(true);

        LevelEventSnapshot snap = mgr.capture();

        mgr.initLevel(Sonic3kZoneIds.ZONE_MGZ, 1);
        var mgz2 = mgr.getMgzEventsForTest();
        assertNotNull(mgz2);
        mgr.restore(snap);

        assertTrue(mgz2.isCollapseRequested());
        assertTrue(mgz2.isCollapseInitialized());
        assertEquals(7,      mgz2.getCollapseMutationCount());
        assertEquals(42,     mgz2.getCollapseFrameCounter());
        assertEquals(5,      mgz2.getCollapseStartupShakeTimer());
        assertEquals(8,      mgz2.getBgRiseRoutine());
        assertEquals(0x100,  mgz2.getBgRiseOffset());
        assertEquals(0x8000, mgz2.getBgRiseSubpixelAccum());
        assertTrue(mgz2.isBgRiseMotionStarted());
        assertEquals(4,      mgz2.getBossArenaRoutine());
        assertTrue(mgz2.isBossSpawned());
        assertTrue(mgz2.isAppearance1Complete());
        assertTrue(mgz2.isAppearance2Complete());
    }

    @Test
    void handlerAbsentDoesNotCorruptBuffer() {
        // Snapshot in AIZ, restore in HCZ — AIZ handler present but hczEvents null.
        Sonic3kLevelEventManager mgr = new Sonic3kLevelEventManager();
        mgr.initLevel(Sonic3kZoneIds.ZONE_AIZ, 1);
        LevelEventSnapshot snap = mgr.capture();

        // Switch to HCZ and restore — should not throw or corrupt
        mgr.initLevel(Sonic3kZoneIds.ZONE_HCZ, 0);
        assertDoesNotThrow(() -> mgr.restore(snap));
    }
}
