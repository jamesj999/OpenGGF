package com.openggf.game.animation;

import com.openggf.game.rewind.snapshot.AnimatedTileChannelSnapshot;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestAnimatedTileChannelRewindSnapshot {

    @Test
    void roundTripPreservesLastPhase() {
        AnimatedTileChannelGraph g = new AnimatedTileChannelGraph();
        g.recordPhase("ch-1", 5);
        g.recordPhase("ch-2", 12);
        AnimatedTileChannelSnapshot snap = g.capture();
        g.recordPhase("ch-1", 0);
        g.restore(snap);
        assertEquals(5, g.getLastPhase("ch-1"));
        assertEquals(12, g.getLastPhase("ch-2"));
    }

    @Test
    void keyIsAnimatedTileChannels() {
        assertEquals("animated-tile-channels", new AnimatedTileChannelGraph().key());
    }

    @Test
    void captureIsImmutable() {
        AnimatedTileChannelGraph g = new AnimatedTileChannelGraph();
        g.recordPhase("ch-1", 3);
        AnimatedTileChannelSnapshot snap = g.capture();
        // Map.copyOf in the record constructor should throw on mutation
        assertThrows(UnsupportedOperationException.class,
                () -> snap.lastPhaseByChannel().put("ch-1", 99));
    }

    @Test
    void emptyGraphRoundTrips() {
        AnimatedTileChannelGraph g = new AnimatedTileChannelGraph();
        AnimatedTileChannelSnapshot snap = g.capture();
        g.recordPhase("ch-x", 7);
        g.restore(snap);
        assertEquals(-1, g.getLastPhase("ch-x"));
    }
}
