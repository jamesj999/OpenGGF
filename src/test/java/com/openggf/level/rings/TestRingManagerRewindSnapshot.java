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
                base.sparkleStartFrames(),
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
    void roundTripSparkleTimers() {
        RingManager mgr = buildManager(4);
        // Manually drive placement to set sparkle frame for index 1
        RingSnapshot beforeAny = mgr.capture();
        int[] sparkles = beforeAny.sparkleStartFrames().clone();
        sparkles[1] = 42;

        // Build a snapshot with the modified sparkle array
        RingSnapshot modified = new RingSnapshot(
                beforeAny.collected(),
                sparkles,
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
        assertEquals(42, after.sparkleStartFrames()[1],
                "Sparkle timer at index 1 must survive restore");
    }

    @Test
    void roundTripLostRingPoolCounters() {
        RingManager mgr = buildManager(2);
        RingSnapshot base = mgr.capture();

        // Inject custom spill-anim state via a crafted snapshot
        RingSnapshot modified = new RingSnapshot(
                base.collected(),
                base.sparkleStartFrames(),
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
    void roundTripLostRingSlot() {
        RingManager mgr = buildManager(0);
        RingSnapshot base = mgr.capture();

        // Inject an active LostRing entry at slot 0
        RingSnapshot.LostRingEntry[] lostRings = base.lostRings().clone();
        lostRings[0] = new RingSnapshot.LostRingEntry(
                true, 0x1234_00, 0x0800_00, 0x300, -0x200,
                120, false, -1, 0, 5);

        RingSnapshot modified = new RingSnapshot(
                base.collected(),
                base.sparkleStartFrames(),
                base.placementCursorIndex(),
                base.placementLastCameraX(),
                1,
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
    }

    @Test
    void roundTripAttractedRingSlot() {
        RingManager mgr = buildManager(0);
        RingSnapshot base = mgr.capture();

        RingSnapshot.AttractedRingEntry[] atRings = base.attractedRings().clone();
        atRings[0] = new RingSnapshot.AttractedRingEntry(
                true, 3, 0x200, 0x180, 0x80, 0x40, 0x100, -0x50);

        RingSnapshot modified = new RingSnapshot(
                base.collected(),
                base.sparkleStartFrames(),
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
    }
}
