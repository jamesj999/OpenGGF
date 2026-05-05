package com.openggf.level;

import com.openggf.game.rewind.snapshot.WaterSystemSnapshot;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestWaterSystemRewindSnapshot {

    @Test
    void roundTripPreservesWaterEnteredCounter() {
        WaterSystem ws = new WaterSystem();
        ws.incrementWaterEnteredCounter();
        ws.incrementWaterEnteredCounter();
        WaterSystemSnapshot snap = ws.capture();
        ws.reset();
        ws.restore(snap);
        assertEquals(2, ws.getWaterEnteredCounter());
    }

    @Test
    void keyIsWater() {
        assertEquals("water", new WaterSystem().key());
    }

    @Test
    void captureWithNoDynamicStateIsEmpty() {
        WaterSystem ws = new WaterSystem();
        WaterSystemSnapshot snap = ws.capture();
        assertEquals(0, snap.waterEnteredCounter());
        assertTrue(snap.dynamicStates().isEmpty());
    }

    @Test
    void restoreWithNoMatchingEntryIsNoOp() {
        WaterSystem ws = new WaterSystem();
        // Restore a snapshot with an entry that doesn't exist in the current system
        Map<String, WaterSystemSnapshot.DynamicWaterEntry> entries = Map.of(
                "99_0", new WaterSystemSnapshot.DynamicWaterEntry(100, 200, 150, true, 1, false, 0)
        );
        WaterSystemSnapshot snap = new WaterSystemSnapshot(5, entries);
        assertDoesNotThrow(() -> ws.restore(snap));
        assertEquals(5, ws.getWaterEnteredCounter());
    }
}
