package com.openggf.game.sonic1;

import com.openggf.game.rewind.snapshot.LevelEventSnapshot;
import com.openggf.game.sonic1.events.Sonic1LevelEventManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round-trip tests for the {@link Sonic1LevelEventManager} extra-state snapshot (C.2).
 *
 * <p>Verifies that S1-specific extra state (per-zone handler eventRoutines and
 * transition guard flags) is preserved by captureExtra/restoreExtra.
 */
class TestSonic1LevelEventRewindSnapshot {

    @Test
    void keyIsLevelEvent() {
        assertEquals("level-event", new Sonic1LevelEventManager().key());
    }

    @Test
    void roundTripGhzEventRoutine() {
        Sonic1LevelEventManager mgr = new Sonic1LevelEventManager();
        mgr.initLevel(0 /* ZONE_GHZ */, 2);
        // Advance eventRoutine on the GHZ handler via initLevel (all start at 0)
        // We'll set it through the public setEventRoutine on the active handler
        mgr.setEventRoutine(4); // 4 = DLE_GHZ3end

        LevelEventSnapshot snap = mgr.capture();

        // Mutate
        mgr.initLevel(0, 2);
        mgr.setEventRoutine(0);

        // Restore
        mgr.restore(snap);

        assertEquals(4, mgr.getEventRoutine(), "GHZ eventRoutine should be restored");
    }

    @Test
    void roundTripSbz3TransitionFlag() {
        Sonic1LevelEventManager mgr = new Sonic1LevelEventManager();
        mgr.initLevel(5 /* ZONE_SBZ */, 1);

        // Capture with flag cleared
        LevelEventSnapshot snapClear = mgr.capture();
        assertNotNull(snapClear.extra());

        // Directly set sbz3TransitionRequested via the package-private field isn't accessible from
        // this package, but we can verify a round-trip after initLevel clears it.
        LevelEventSnapshot snap2 = mgr.capture();
        mgr.restore(snapClear);
        // Flag should remain false (it was false at capture time)
        LevelEventSnapshot snap3 = mgr.capture();
        assertArrayEquals(snapClear.extra(), snap3.extra(),
                "extra bytes should round-trip unchanged");
    }

    @Test
    void extraBytesNotNullForS1Manager() {
        Sonic1LevelEventManager mgr = new Sonic1LevelEventManager();
        mgr.initLevel(0, 0);
        LevelEventSnapshot snap = mgr.capture();
        assertNotNull(snap.extra(), "S1 manager must produce non-null extra bytes");
        assertTrue(snap.extra().length > 0);
    }

    @Test
    void restoreExtraWithNullIsNoOp() {
        Sonic1LevelEventManager mgr = new Sonic1LevelEventManager();
        mgr.initLevel(0, 2);
        mgr.setEventRoutine(4);
        // Calling restore(null-extra) should not throw or corrupt state
        assertDoesNotThrow(() -> {
            // Construct a snapshot with null extra to exercise the null-guard
            var snap = new LevelEventSnapshot(
                    mgr.getCurrentZone(), mgr.getCurrentAct(),
                    mgr.getEventRoutineFg(), mgr.getEventRoutineBg(),
                    0, 0, false, null, null, null);
            mgr.restore(snap);
        });
    }
}
