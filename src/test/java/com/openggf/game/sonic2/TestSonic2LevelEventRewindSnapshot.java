package com.openggf.game.sonic2;

import com.openggf.game.rewind.snapshot.LevelEventSnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round-trip tests for the {@link Sonic2LevelEventManager} extra-state snapshot (C.3).
 *
 * <p>Covers HTZ earthquake counters, CPZ water-trigger flag, CNZ wall coords,
 * and the per-handler eventRoutine / bossSpawnDelay scalars.
 */
class TestSonic2LevelEventRewindSnapshot {

    @Test
    void keyIsLevelEvent() {
        assertEquals("level-event", new Sonic2LevelEventManager().key());
    }

    @Test
    void extraBytesNotNull() {
        Sonic2LevelEventManager mgr = new Sonic2LevelEventManager();
        mgr.initLevel(Sonic2LevelEventManager.ZONE_EHZ, 1);
        LevelEventSnapshot snap = mgr.capture();
        assertNotNull(snap.extra());
        assertTrue(snap.extra().length > 0);
    }

    @Test
    void roundTripEhzEventRoutineAndBossDelay() {
        Sonic2LevelEventManager mgr = new Sonic2LevelEventManager();
        mgr.initLevel(Sonic2LevelEventManager.ZONE_EHZ, 1);
        mgr.setEventRoutine(4);
        mgr.getEhzEventsForTest().setBossSpawnDelay(77);

        LevelEventSnapshot snap = mgr.capture();

        mgr.initLevel(Sonic2LevelEventManager.ZONE_EHZ, 1);
        mgr.restore(snap);

        assertEquals(4,  mgr.getEventRoutine());
        assertEquals(77, mgr.getEhzEventsForTest().getBossSpawnDelay());
    }

    @Test
    void roundTripHtzEarthquakeState() {
        Sonic2LevelEventManager mgr = new Sonic2LevelEventManager();
        mgr.initLevel(Sonic2LevelEventManager.ZONE_HTZ, 0);
        var htz = mgr.getHtzEvents();
        htz.setCameraBgYOffset(280);
        htz.setHtzTerrainSinking(true);
        htz.setHtzTerrainDelay(55);
        htz.setEarthquakeActiveRaw(true);
        htz.setHtzCurrentRisenLimit(320);
        htz.setHtzCurrentSunkenLimit(224);
        htz.setHtzCurrentBgXOffset(-0x680);

        LevelEventSnapshot snap = mgr.capture();

        mgr.initLevel(Sonic2LevelEventManager.ZONE_HTZ, 0);
        mgr.restore(snap);

        assertEquals(280,    htz.getCameraBgYOffsetRaw());
        assertTrue(htz.isHtzTerrainSinking());
        assertEquals(55,     htz.getHtzTerrainDelay());
        assertTrue(htz.isEarthquakeActiveRaw());
        assertEquals(320,    htz.getHtzCurrentRisenLimit());
        assertEquals(224,    htz.getHtzCurrentSunkenLimit());
        assertEquals(-0x680, htz.getHtzCurrentBgXOffset());
    }

    @Test
    void roundTripCpzWaterTriggered() {
        Sonic2LevelEventManager mgr = new Sonic2LevelEventManager();
        mgr.initLevel(Sonic2LevelEventManager.ZONE_CPZ, 1);
        mgr.getCpzEventsForTest().setCpzWaterTriggered(true);

        LevelEventSnapshot snap = mgr.capture();

        mgr.initLevel(Sonic2LevelEventManager.ZONE_CPZ, 1);
        mgr.restore(snap);

        assertTrue(mgr.getCpzEventsForTest().isCpzWaterTriggered());
    }

    @Test
    void roundTripCnzWallCoords() {
        Sonic2LevelEventManager mgr = new Sonic2LevelEventManager();
        mgr.initLevel(Sonic2LevelEventManager.ZONE_CNZ, 1);
        var cnz = mgr.getCnzEventsForTest();
        cnz.setCnzLeftWallX(80);
        cnz.setCnzLeftWallY(12);
        cnz.setCnzRightWallX(84);
        cnz.setCnzRightWallY(12);

        LevelEventSnapshot snap = mgr.capture();

        mgr.initLevel(Sonic2LevelEventManager.ZONE_CNZ, 1);
        mgr.restore(snap);

        assertEquals(80, cnz.getCnzLeftWallX());
        assertEquals(12, cnz.getCnzLeftWallY());
        assertEquals(84, cnz.getCnzRightWallX());
        assertEquals(12, cnz.getCnzRightWallY());
    }
}
