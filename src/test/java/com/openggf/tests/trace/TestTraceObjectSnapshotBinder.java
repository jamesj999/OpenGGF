package com.openggf.tests.trace;

import com.openggf.level.objects.RomObjectSnapshot;
import com.openggf.trace.TraceEvent;
import com.openggf.trace.TraceObjectSnapshotBinder;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the read-only snapshot binder compatibility wrapper.
 */
public class TestTraceObjectSnapshotBinder {

    @Test
    public void snapshotsAreReportedButNeverHydrated() {
        TraceEvent.ObjectStateSnapshot snap = snapshotAt(16, 0x80, 100, 200);

        TraceObjectSnapshotBinder.Result result = TraceObjectSnapshotBinder.apply(
                List.of(), List.of(snap));

        assertEquals(1, result.attempted());
        assertEquals(0, result.matched());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    public void nullSnapshotListReturnsZero() {
        TraceObjectSnapshotBinder.Result result = TraceObjectSnapshotBinder.apply(
                (com.openggf.level.objects.ObjectManager) null,
                null);

        assertEquals(0, result.attempted());
        assertEquals(0, result.matched());
        assertTrue(result.warnings().isEmpty());
    }

    private static TraceEvent.ObjectStateSnapshot snapshotAt(int slot, int type, int x, int y) {
        Map<Integer, Integer> bytes = new LinkedHashMap<>();
        Map<Integer, Integer> words = new LinkedHashMap<>();
        words.put(0x08, x & 0xFFFF);
        words.put(0x0C, y & 0xFFFF);
        bytes.put(0x00, type & 0xFF);
        RomObjectSnapshot fields = new RomObjectSnapshot(bytes, words);
        return new TraceEvent.ObjectStateSnapshot(-1, slot, type & 0xFF, fields);
    }
}
