package com.openggf.tests.trace;
import com.openggf.trace.*;

import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RomObjectSnapshot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TraceObjectSnapshotBinder}.
 *
 * <p>Uses a minimal {@link FakeObjectInstance} that records the last snapshot it
 * was hydrated with, so tests can verify the binder picked the right candidate
 * without booting the whole engine.
 */
public class TestTraceObjectSnapshotBinder {

    @Test
    public void exactTripleMatchesStationaryObject() {
        FakeObjectInstance buzzA = new FakeObjectInstance(0x80, 100, 200);
        FakeObjectInstance buzzB = new FakeObjectInstance(0x80, 300, 400);

        TraceEvent.ObjectStateSnapshot snap = snapshotAt(16, 0x80, 100, 200);

        TraceObjectSnapshotBinder.Result result = TraceObjectSnapshotBinder.apply(
                List.of(buzzA, buzzB), List.of(snap));

        assertEquals(1, result.matched());
        assertSame(snap.fields(), buzzA.lastSnapshot);
        assertNull(buzzB.lastSnapshot);
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    public void sameXFallbackMatchesMovedCoconuts() {
        // Coconuts spawns at (0x208, 0x150); ROM has moved it to (0x208, 0x158).
        // Binder must still match on X alone.
        FakeObjectInstance coconuts = new FakeObjectInstance(0x9D, 0x208, 0x150);

        TraceEvent.ObjectStateSnapshot snap = snapshotAt(16, 0x9D, 0x208, 0x158);

        TraceObjectSnapshotBinder.Result result = TraceObjectSnapshotBinder.apply(
                List.of(coconuts), List.of(snap));

        assertEquals(1, result.matched());
        assertSame(snap.fields(), coconuts.lastSnapshot);
    }

    @Test
    public void singleSameTypeFallbackMatchesWhenXAlsoMoves() {
        // Only one engine instance of this type — claim it despite no position match.
        FakeObjectInstance only = new FakeObjectInstance(0x0C, 512, 1024);

        TraceEvent.ObjectStateSnapshot snap = snapshotAt(16, 0x0C, 999, 2222);

        TraceObjectSnapshotBinder.Result result = TraceObjectSnapshotBinder.apply(
                List.of(only), List.of(snap));

        assertEquals(1, result.matched());
        assertSame(snap.fields(), only.lastSnapshot);
    }

    @Test
    public void unmatchedSnapshotProducesWarning() {
        FakeObjectInstance buzz = new FakeObjectInstance(0x80, 100, 200);
        TraceEvent.ObjectStateSnapshot snap = snapshotAt(16, 0x9D, 500, 600);

        TraceObjectSnapshotBinder.Result result = TraceObjectSnapshotBinder.apply(
                List.of(buzz), List.of(snap));

        assertEquals(0, result.matched());
        assertEquals(1, result.warnings().size());
        assertNull(buzz.lastSnapshot);
        assertTrue(result.warnings().get(0).contains("0x9D"),
                "warning should include object type: " + result.warnings().get(0));
    }

    @Test
    public void snapshotClaimsEngineInstanceExactlyOnce() {
        // Two ROM snapshots of same type, one engine instance.
        // First snapshot should claim the instance; second should warn (no dup).
        FakeObjectInstance single = new FakeObjectInstance(0x80, 100, 200);

        TraceEvent.ObjectStateSnapshot snapA = snapshotAt(16, 0x80, 100, 200);
        TraceEvent.ObjectStateSnapshot snapB = snapshotAt(17, 0x80, 300, 400);

        TraceObjectSnapshotBinder.Result result = TraceObjectSnapshotBinder.apply(
                List.of(single), List.of(snapA, snapB));

        assertEquals(1, result.matched());
        assertEquals(1, result.warnings().size());
        assertSame(snapA.fields(), single.lastSnapshot);
    }

    @Test
    public void emptySnapshotListReturnsZero() {
        FakeObjectInstance buzz = new FakeObjectInstance(0x80, 100, 200);
        TraceObjectSnapshotBinder.Result result = TraceObjectSnapshotBinder.apply(
                List.of(buzz), Collections.emptyList());
        assertEquals(0, result.attempted());
        assertEquals(0, result.matched());
    }

    @Test
    public void nullObjectManagerHandledGracefully() {
        TraceObjectSnapshotBinder.Result result = TraceObjectSnapshotBinder.apply(
                (com.openggf.level.objects.ObjectManager) null,
                List.of(snapshotAt(16, 0x80, 0, 0)));
        assertEquals(0, result.matched());
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private static TraceEvent.ObjectStateSnapshot snapshotAt(int slot, int type, int x, int y) {
        Map<Integer, Integer> bytes = new LinkedHashMap<>();
        Map<Integer, Integer> words = new LinkedHashMap<>();
        words.put(0x08, x & 0xFFFF);
        words.put(0x0C, y & 0xFFFF);
        bytes.put(0x00, type & 0xFF);
        RomObjectSnapshot fields = new RomObjectSnapshot(bytes, words);
        return new TraceEvent.ObjectStateSnapshot(-1, slot, type & 0xFF, fields);
    }

    /** Minimal AbstractObjectInstance that records the snapshot it was hydrated with. */
    private static class FakeObjectInstance extends AbstractObjectInstance {
        RomObjectSnapshot lastSnapshot;

        FakeObjectInstance(int objectId, int x, int y) {
            super(new ObjectSpawn(x, y, objectId, 0, 0, false, y), "Fake");
        }

        @Override
        public void hydrateFromRomSnapshot(RomObjectSnapshot snapshot) {
            this.lastSnapshot = snapshot;
        }

        @Override public int getX() { return spawn.x(); }
        @Override public int getY() { return spawn.y(); }
        @Override public void appendRenderCommands(List<GLCommand> commands) { /* unused */ }
        @Override public int getPriorityBucket() { return 0; }
    }
}
