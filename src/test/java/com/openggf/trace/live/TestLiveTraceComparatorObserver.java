package com.openggf.trace.live;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.GroundMode;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.trace.FrameComparison;
import com.openggf.trace.ToleranceConfig;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceFixtures;
import com.openggf.trace.TraceFrame;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestLiveTraceComparatorObserver {

    private static AbstractPlayableSprite stubSprite() {
        AbstractPlayableSprite sprite = mock(AbstractPlayableSprite.class);
        when(sprite.getCentreX()).thenReturn((short) 10);
        when(sprite.getCentreY()).thenReturn((short) 0);
        when(sprite.getXSpeed()).thenReturn((short) 0);
        when(sprite.getYSpeed()).thenReturn((short) 0);
        when(sprite.getGSpeed()).thenReturn((short) 0);
        when(sprite.getAngle()).thenReturn((byte) 0);
        when(sprite.getAir()).thenReturn(false);
        when(sprite.getRolling()).thenReturn(false);
        when(sprite.getGroundMode()).thenReturn(GroundMode.GROUND);
        return sprite;
    }

    private static TraceData twoFrameTrace() {
        // Two s2 FULL_LEVEL_FRAME frames (previous=null for frame 0 → always FULL_LEVEL_FRAME;
        // frame 1 has previous with same gameplayFrameCounter=0x100 → legacy path → since
        // vblankCounter advances it would be VBLANK_ONLY in legacy. Use of(…) with distinct
        // gameplayFrameCounters avoids that: frame 1 has counter 0x101 → counter advanced,
        // so NOT a same-counter pin, and speeds=0/air=false → FULL_LEVEL_FRAME).
        return TraceFixtures.trace(
                TraceFixtures.metadata("s2", 0, 0),
                List.of(
                        TraceFrame.of(0, 0,
                                (short) 10, (short) 0,
                                (short) 0, (short) 0, (short) 0,
                                (byte) 0, false, false, 0),
                        TraceFrame.of(1, 0,
                                (short) 10, (short) 0,
                                (short) 0, (short) 0, (short) 0,
                                (byte) 0, false, false, 0)));
    }

    @Test
    void perFrameObserverReceivesEveryComparison() {
        AbstractPlayableSprite sprite = stubSprite();
        TraceData trace = twoFrameTrace();

        List<FrameComparison> observed = new ArrayList<>();
        Consumer<FrameComparison> observer = observed::add;

        LiveTraceComparator c = new LiveTraceComparator(
                trace,
                ToleranceConfig.DEFAULT,
                0,
                () -> sprite,
                null,
                observer);

        Bk2FrameInput empty = new Bk2FrameInput(0, 0, 0, false, "0");
        c.afterFrameAdvanced(empty, false);
        c.afterFrameAdvanced(empty, false);

        assertEquals(2, observed.size(),
                "Observer should fire exactly once per gameplay-compared frame");
        assertNotNull(observed.get(0));
        assertNotNull(observed.get(1));
    }

    @Test
    void nullObserverIsHonoured() {
        // Uses the existing 4-arg constructor — must not NPE.
        AbstractPlayableSprite sprite = stubSprite();
        TraceData trace = twoFrameTrace();

        LiveTraceComparator c = new LiveTraceComparator(
                trace,
                ToleranceConfig.DEFAULT,
                0,
                () -> sprite);

        Bk2FrameInput empty = new Bk2FrameInput(0, 0, 0, false, "0");
        c.afterFrameAdvanced(empty, false);
        c.afterFrameAdvanced(empty, false);

        // No assertion needed beyond surviving without NPE.
        assertEquals(0, c.errorCount());
    }

    @Test
    void existingFiveArgConstructorDelegatesWithNullObserver() {
        // Uses the existing 5-arg constructor — must not NPE.
        AbstractPlayableSprite sprite = stubSprite();
        TraceData trace = twoFrameTrace();

        LiveTraceComparator c = new LiveTraceComparator(
                trace,
                ToleranceConfig.DEFAULT,
                0,
                () -> sprite,
                null);

        Bk2FrameInput empty = new Bk2FrameInput(0, 0, 0, false, "0");
        c.afterFrameAdvanced(empty, false);
        c.afterFrameAdvanced(empty, false);

        assertEquals(0, c.errorCount());
    }
}
