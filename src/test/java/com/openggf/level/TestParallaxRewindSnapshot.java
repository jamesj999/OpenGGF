package com.openggf.level;

import com.openggf.game.rewind.snapshot.ParallaxSnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestParallaxRewindSnapshot {

    @Test
    void roundTripPreservesShakeOffsetsAndCache() {
        ParallaxManager pm = new ParallaxManager();
        ParallaxSnapshot initial = pm.capture();
        // Build a modified snapshot via record constructor
        ParallaxSnapshot modified = new ParallaxSnapshot(
                7, -3,
                0x1234, 256,
                initial.hScroll(),
                initial.vScrollPerLineBG(),
                initial.vScrollPerColumnBG(),
                initial.vScrollPerColumnFG(),
                false, false, false,
                0, 0,
                (short) 0, (short) 0
        );
        pm.restore(modified);
        ParallaxSnapshot snap = pm.capture();
        assertEquals(7, snap.currentShakeOffsetX());
        assertEquals(-3, snap.currentShakeOffsetY());
        assertEquals(0x1234, snap.cachedBgCameraX());
        assertEquals(256, snap.cachedBgPeriodWidth());
    }

    @Test
    void keyIsParallax() {
        assertEquals("parallax", new ParallaxManager().key());
    }

    @Test
    void captureIsDeepCopy() {
        ParallaxManager pm = new ParallaxManager();
        ParallaxSnapshot snap = pm.capture();
        // The snapshot array copy should be independent of the manager's internal array
        int originalFirst = snap.hScroll()[0];
        // Restore a state with different hScroll
        int[] newScroll = new int[224];
        java.util.Arrays.fill(newScroll, 999);
        ParallaxSnapshot other = new ParallaxSnapshot(
                0, 0, Integer.MIN_VALUE, 512,
                newScroll, snap.vScrollPerLineBG(), snap.vScrollPerColumnBG(),
                snap.vScrollPerColumnFG(), false, false, false, 0, 0, (short) 0, (short) 0
        );
        pm.restore(other);
        // Original snapshot's hScroll should be unchanged
        assertEquals(originalFirst, snap.hScroll()[0]);
    }
}
