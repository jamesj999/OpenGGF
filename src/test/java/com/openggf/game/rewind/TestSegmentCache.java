package com.openggf.game.rewind;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TestSegmentCache {

    private static CompositeSnapshot snap(int marker) {
        var e = new LinkedHashMap<String, Object>();
        e.put("marker", marker);
        return new CompositeSnapshot(e);
    }

    @Test
    void firstAccessExpandsSegmentAndCachesIt() {
        SegmentCache cache = new SegmentCache(60);
        AtomicInteger steps = new AtomicInteger();
        AtomicInteger restores = new AtomicInteger();
        SegmentCache.Stepper stepper = () -> snap(steps.incrementAndGet());

        // K=0, request frame 5: expand segment [0, 60) up to offset 5
        var got = cache.snapshotAt(5, snap(0), 0,
                restores::incrementAndGet, stepper);
        assertEquals(1, restores.get(), "must restore keyframe on cold expand");
        assertEquals(5, steps.get(), "must step 5 frames forward (1..5)");
        assertEquals(5, got.get("marker"));
    }

    @Test
    void secondAccessSameSegmentIsCacheHit() {
        SegmentCache cache = new SegmentCache(60);
        AtomicInteger steps = new AtomicInteger();
        AtomicInteger restores = new AtomicInteger();
        SegmentCache.Stepper stepper = () -> snap(steps.incrementAndGet());

        cache.snapshotAt(5, snap(0), 0, restores::incrementAndGet, stepper);
        // request frame 3 — already cached, no re-expansion
        var got = cache.snapshotAt(3, snap(0), 0, restores::incrementAndGet, stepper);
        assertEquals(1, restores.get(), "no re-expand on cached frame");
        assertEquals(5, steps.get(), "no extra steps");
        assertEquals(3, got.get("marker"));
    }

    @Test
    void crossingSegmentBoundaryRebuilds() {
        SegmentCache cache = new SegmentCache(60);
        AtomicInteger steps = new AtomicInteger();
        AtomicInteger restores = new AtomicInteger();
        SegmentCache.Stepper stepper = () -> snap(steps.incrementAndGet());

        // Segment 1 [0, 60): expand up to 30
        cache.snapshotAt(30, snap(0), 0, restores::incrementAndGet, stepper);
        int stepsAfterSeg1 = steps.get();
        // Segment 2 [60, 120): expand up to 75
        cache.snapshotAt(75, snap(60), 60, restores::incrementAndGet, stepper);
        assertEquals(2, restores.get(), "second segment requires keyframe restore");
        assertEquals(stepsAfterSeg1 + 15, steps.get(), "stepped forward to offset 15");
    }

    @Test
    void invalidateForcesNextAccessToRebuild() {
        SegmentCache cache = new SegmentCache(60);
        AtomicInteger steps = new AtomicInteger();
        AtomicInteger restores = new AtomicInteger();
        SegmentCache.Stepper stepper = () -> snap(steps.incrementAndGet());

        cache.snapshotAt(5, snap(0), 0, restores::incrementAndGet, stepper);
        cache.invalidate();
        cache.snapshotAt(5, snap(0), 0, restores::incrementAndGet, stepper);
        assertEquals(2, restores.get());
    }
}
