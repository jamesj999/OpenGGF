package com.openggf.level.rings;

import com.openggf.game.rewind.snapshot.RingSnapshot;
import com.openggf.game.session.EngineContext;
import com.openggf.game.RuntimeManager;
import com.openggf.game.session.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round-trip tests for {@link RingManager}'s {@link com.openggf.game.rewind.RewindSnapshottable}
 * implementation (Track D).
 *
 * <p>Tests cover the ring-collection BitSet, sparkle timers, lost-ring pool state,
 * and attracted-ring slots without requiring a full level load.
 */
class TestRingManagerRewindSnapshot {

    @BeforeEach
    void setUp() {
        RuntimeManager.configureEngineServices(EngineContext.fromLegacySingletonsForBootstrap());
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    /** Build a minimal RingManager with N rings; no renderer, no audio. */
    private static RingManager buildManager(int ringCount) {
        List<RingSpawn> spawns = new java.util.ArrayList<>();
        for (int i = 0; i < ringCount; i++) {
            spawns.add(new RingSpawn(i * 16, 256));
        }
        return new RingManager(spawns, null, null, null, null);
    }

    @Test
    void keyIsRings() {
        assertEquals("rings", buildManager(0).key());
    }

    @Test
    void roundTripCollectedBitSet() {
        RingManager mgr = buildManager(8);
        RingSnapshot base = mgr.capture();

        // Craft a snapshot with bits 0, 2, 5 set
        java.util.BitSet bits = new java.util.BitSet();
        bits.set(0); bits.set(2); bits.set(5);
        RingSnapshot modified = new RingSnapshot(
                bits,
                base.sparkleTimers(),
                base.placementCursorIndex(),
                base.placementLastCameraX(),
                base.lostRingActiveCount(),
                base.spillAnimCounter(),
                base.spillAnimAccum(),
                base.spillAnimFrame(),
                base.lostRingFrameCounter(),
                base.lostRings(),
                base.attractedRings());

        mgr.restore(modified);
        RingSnapshot after = mgr.capture();

        assertEquals(bits, after.collected(),
                "Collected BitSet must survive a round-trip");
    }

    @Test
    void captureStoresUncollectedRingsAsEmptyWordArray() {
        RingManager mgr = buildManager(8);

        RingSnapshot snapshot = mgr.capture();

        assertEquals(0, snapshot.collectedWords().length);
    }

    @Test
    void roundTripSparkleTimers() {
        RingManager mgr = buildManager(4);
        RingSnapshot beforeAny = mgr.capture();

        // Build a sparse snapshot with the modified sparkle timer.
        RingSnapshot modified = new RingSnapshot(
                beforeAny.collected(),
                new RingSnapshot.SparkleEntry[] {
                        new RingSnapshot.SparkleEntry(1, 42)
                },
                beforeAny.placementCursorIndex(),
                beforeAny.placementLastCameraX(),
                beforeAny.lostRingActiveCount(),
                beforeAny.spillAnimCounter(),
                beforeAny.spillAnimAccum(),
                beforeAny.spillAnimFrame(),
                beforeAny.lostRingFrameCounter(),
                beforeAny.lostRings(),
                beforeAny.attractedRings());
        mgr.restore(modified);

        RingSnapshot after = mgr.capture();
        assertEquals(1, after.sparkleTimers().length);
        assertEquals(1, after.sparkleTimers()[0].ringIndex());
        assertEquals(42, after.sparkleTimers()[0].startFrame(),
                "Sparkle timer at index 1 must survive restore");
    }

    @Test
    void captureOmitsInactiveSparkleTimers() {
        RingManager mgr = buildManager(4);

        RingSnapshot snapshot = mgr.capture();

        assertEquals(0, snapshot.sparkleTimers().length);
    }

    @Test
    void restoreSparseSparkleTimersClearsPreviousTimers() {
        RingManager mgr = buildManager(4);
        RingSnapshot base = mgr.capture();

        RingSnapshot withSparkle = new RingSnapshot(
                base.collected(),
                new RingSnapshot.SparkleEntry[] {
                        new RingSnapshot.SparkleEntry(1, 42)
                },
                base.placementCursorIndex(),
                base.placementLastCameraX(),
                base.lostRingActiveCount(),
                base.spillAnimCounter(),
                base.spillAnimAccum(),
                base.spillAnimFrame(),
                base.lostRingFrameCounter(),
                base.lostRings(),
                base.attractedRings());

        mgr.restore(withSparkle);

        RingSnapshot withoutSparkles = new RingSnapshot(
                base.collected(),
                new RingSnapshot.SparkleEntry[0],
                base.placementCursorIndex(),
                base.placementLastCameraX(),
                base.lostRingActiveCount(),
                base.spillAnimCounter(),
                base.spillAnimAccum(),
                base.spillAnimFrame(),
                base.lostRingFrameCounter(),
                base.lostRings(),
                base.attractedRings());

        mgr.restore(withoutSparkles);
        RingSnapshot after = mgr.capture();

        assertEquals(0, after.sparkleTimers().length);
    }

    @Test
    void roundTripLostRingPoolCounters() {
        RingManager mgr = buildManager(2);
        RingSnapshot base = mgr.capture();

        // Inject custom spill-anim state via a crafted snapshot
        RingSnapshot modified = new RingSnapshot(
                base.collected(),
                base.sparkleTimers(),
                base.placementCursorIndex(),
                base.placementLastCameraX(),
                3,      // lostRingActiveCount
                0xAB,   // spillAnimCounter
                0xCD,   // spillAnimAccum
                7,      // spillAnimFrame
                100,    // lostRingFrameCounter
                base.lostRings(),
                base.attractedRings());

        mgr.restore(modified);
        RingSnapshot after = mgr.capture();

        assertEquals(3,    after.lostRingActiveCount());
        assertEquals(0xAB, after.spillAnimCounter());
        assertEquals(0xCD, after.spillAnimAccum());
        assertEquals(7,    after.spillAnimFrame());
        assertEquals(100,  after.lostRingFrameCounter());
    }

    @Test
    void captureOmitsInactiveLostRingSlots() {
        RingManager mgr = buildManager(0);

        RingSnapshot snapshot = mgr.capture();

        assertEquals(0, snapshot.lostRings().length);
    }

    @Test
    void restoreSparseLostRingsClearsPreviousActiveSlots() {
        RingManager mgr = buildManager(0);
        RingSnapshot base = mgr.capture();

        RingSnapshot withActiveLostRing = new RingSnapshot(
                base.collected(),
                base.sparkleTimers(),
                base.placementCursorIndex(),
                base.placementLastCameraX(),
                1,
                base.spillAnimCounter(),
                base.spillAnimAccum(),
                base.spillAnimFrame(),
                base.lostRingFrameCounter(),
                new RingSnapshot.LostRingEntry[] {
                        new RingSnapshot.LostRingEntry(
                                true, 0x1234_00, 0x0800_00, 0x300, -0x200,
                                120, false, -1, 0, 5)
                },
                base.attractedRings());

        mgr.restore(withActiveLostRing);

        RingSnapshot withoutLostRings = new RingSnapshot(
                base.collected(),
                base.sparkleTimers(),
                base.placementCursorIndex(),
                base.placementLastCameraX(),
                0,
                base.spillAnimCounter(),
                base.spillAnimAccum(),
                base.spillAnimFrame(),
                base.lostRingFrameCounter(),
                new RingSnapshot.LostRingEntry[0],
                base.attractedRings());

        mgr.restore(withoutLostRings);
        RingSnapshot after = mgr.capture();

        assertEquals(0, after.lostRings().length);
        assertEquals(0, after.lostRingActiveCount());
    }

    @Test
    void roundTripLostRingSlot() {
        RingManager mgr = buildManager(0);
        RingSnapshot base = mgr.capture();

        // Inject an active LostRing entry at pool slot 3 with source slot 5.
        RingSnapshot.LostRingEntry[] lostRings = {
                new RingSnapshot.LostRingEntry(
                        true, 0x1234_00, 0x0800_00, 0x300, -0x200,
                        120, false, -1, 0, 5, 3)
        };

        RingSnapshot modified = new RingSnapshot(
                base.collected(),
                base.sparkleTimers(),
                base.placementCursorIndex(),
                base.placementLastCameraX(),
                4,
                base.spillAnimCounter(),
                base.spillAnimAccum(),
                base.spillAnimFrame(),
                base.lostRingFrameCounter(),
                lostRings,
                base.attractedRings());

        mgr.restore(modified);
        RingSnapshot after = mgr.capture();

        assertTrue(after.lostRings()[0].active());
        assertEquals(0x1234_00, after.lostRings()[0].xSubpixel());
        assertEquals(0x0800_00, after.lostRings()[0].ySubpixel());
        assertEquals(0x300,     after.lostRings()[0].xVel());
        assertEquals(-0x200,    after.lostRings()[0].yVel());
        assertEquals(120,       after.lostRings()[0].lifetime());
        assertEquals(5,         after.lostRings()[0].slotIndex());
        assertEquals(3,         after.lostRings()[0].poolIndex());
    }

    @Test
    void roundTripAttractedRingSlot() {
        RingManager mgr = buildManager(0);
        RingSnapshot base = mgr.capture();

        RingSnapshot.AttractedRingEntry[] atRings = {
                new RingSnapshot.AttractedRingEntry(
                        true, 3, 0x200, 0x180, 0x80, 0x40, 0x100, -0x50, 7)
        };

        RingSnapshot modified = new RingSnapshot(
                base.collected(),
                base.sparkleTimers(),
                base.placementCursorIndex(),
                base.placementLastCameraX(),
                base.lostRingActiveCount(),
                base.spillAnimCounter(),
                base.spillAnimAccum(),
                base.spillAnimFrame(),
                base.lostRingFrameCounter(),
                base.lostRings(),
                atRings);

        mgr.restore(modified);
        RingSnapshot after = mgr.capture();

        assertTrue(after.attractedRings()[0].active());
        assertEquals(3,      after.attractedRings()[0].sourceIndex());
        assertEquals(0x200,  after.attractedRings()[0].x());
        assertEquals(0x180,  after.attractedRings()[0].y());
        assertEquals(0x100,  after.attractedRings()[0].xVel());
        assertEquals(-0x50,  after.attractedRings()[0].yVel());
        assertEquals(7,      after.attractedRings()[0].slotIndex());
    }

    @Test
    void captureOmitsInactiveAttractedRingSlots() {
        RingManager mgr = buildManager(0);

        RingSnapshot snapshot = mgr.capture();

        assertEquals(0, snapshot.attractedRings().length);
    }

    @Test
    void restoreSparseAttractedRingsClearsPreviousActiveSlots() {
        RingManager mgr = buildManager(0);
        RingSnapshot base = mgr.capture();

        RingSnapshot withActiveAttractedRing = new RingSnapshot(
                base.collected(),
                base.sparkleTimers(),
                base.placementCursorIndex(),
                base.placementLastCameraX(),
                base.lostRingActiveCount(),
                base.spillAnimCounter(),
                base.spillAnimAccum(),
                base.spillAnimFrame(),
                base.lostRingFrameCounter(),
                base.lostRings(),
                new RingSnapshot.AttractedRingEntry[] {
                        new RingSnapshot.AttractedRingEntry(
                                true, 3, 0x200, 0x180, 0x80, 0x40, 0x100, -0x50)
                });

        mgr.restore(withActiveAttractedRing);

        RingSnapshot withoutAttractedRings = new RingSnapshot(
                base.collected(),
                base.sparkleTimers(),
                base.placementCursorIndex(),
                base.placementLastCameraX(),
                base.lostRingActiveCount(),
                base.spillAnimCounter(),
                base.spillAnimAccum(),
                base.spillAnimFrame(),
                base.lostRingFrameCounter(),
                base.lostRings(),
                new RingSnapshot.AttractedRingEntry[0]);

        mgr.restore(withoutAttractedRings);
        RingSnapshot after = mgr.capture();

        assertEquals(0, after.attractedRings().length);
    }
}
