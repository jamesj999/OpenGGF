package com.openggf.trace.live;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.GroundMode;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.trace.ToleranceConfig;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceFixtures;
import com.openggf.trace.TraceFrame;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveTraceComparatorTest {

    @Test
    void skipIncrementsLagCounter() {
        LiveTraceComparator c = new LiveTraceComparator(
                stubTrace(List.of(
                        TraceFrame.executionTestFrame(0, 10, 0x100, 0),
                        TraceFrame.executionTestFrame(1, 10, 0x100, 1))),
                ToleranceConfig.DEFAULT,
                0,
                () -> null);
        Bk2FrameInput empty = new Bk2FrameInput(0, 0, 0, false, "0");
        c.afterFrameAdvanced(empty, true);
        assertEquals(1, c.laggedFrames());
        assertEquals(0, c.errorCount());
    }

    @Test
    void shouldSkipGameplayTickDelegatesToPhase() {
        // First two frames share the same gameplay_frame_counter → second is VBLANK_ONLY
        LiveTraceComparator c = new LiveTraceComparator(
                stubTrace(List.of(
                        TraceFrame.executionTestFrame(0, 10, 0x100, 0),
                        TraceFrame.executionTestFrame(1, 11, 0x100, 1))),
                ToleranceConfig.DEFAULT,
                0,
                () -> null);
        Bk2FrameInput empty = new Bk2FrameInput(1, 0, 0, false, "0");
        // Advance our internal cursor past index 0 first:
        c.afterFrameAdvanced(new Bk2FrameInput(0, 0, 0, false, "0"), false);
        assertTrue(c.shouldSkipGameplayTick(empty));
    }

    @Test
    void s3kTraceWithoutGameplayStartCheckpointStillComparesFullLevelFrames() {
        AbstractPlayableSprite sprite = mock(AbstractPlayableSprite.class);
        when(sprite.getCentreX()).thenReturn((short) 11);
        when(sprite.getCentreY()).thenReturn((short) 0);
        when(sprite.getXSpeed()).thenReturn((short) 0);
        when(sprite.getYSpeed()).thenReturn((short) 0);
        when(sprite.getGSpeed()).thenReturn((short) 0);
        when(sprite.getAngle()).thenReturn((byte) 0);
        when(sprite.getAir()).thenReturn(false);
        when(sprite.getRolling()).thenReturn(false);
        when(sprite.getGroundMode()).thenReturn(GroundMode.GROUND);

        LiveTraceComparator c = new LiveTraceComparator(
                TraceFixtures.trace(
                        TraceFixtures.metadata("s3k", 2, 1),
                        List.of(TraceFrame.of(0, 0,
                                (short) 10, (short) 0,
                                (short) 0, (short) 0, (short) 0,
                                (byte) 0, false, false, 0))),
                ToleranceConfig.DEFAULT,
                0,
                () -> sprite);

        c.afterFrameAdvanced(new Bk2FrameInput(0, 0, 0, false, "0"), false);

        assertEquals(1, c.errorCount());
    }

    @Test
    void invokesFirstErrorCallbackOnceOnFirstDesync() {
        AbstractPlayableSprite sprite = mock(AbstractPlayableSprite.class);
        when(sprite.getCentreX()).thenReturn((short) 11);
        when(sprite.getCentreY()).thenReturn((short) 0);
        when(sprite.getXSpeed()).thenReturn((short) 0);
        when(sprite.getYSpeed()).thenReturn((short) 0);
        when(sprite.getGSpeed()).thenReturn((short) 0);
        when(sprite.getAngle()).thenReturn((byte) 0);
        when(sprite.getAir()).thenReturn(false);
        when(sprite.getRolling()).thenReturn(false);
        when(sprite.getGroundMode()).thenReturn(GroundMode.GROUND);
        Runnable onFirstError = mock(Runnable.class);

        LiveTraceComparator c = new LiveTraceComparator(
                stubTrace(List.of(
                        TraceFrame.executionTestFrame(0, 10, 0x100, 0),
                        TraceFrame.executionTestFrame(1, 11, 0x100, 1))),
                ToleranceConfig.DEFAULT,
                0,
                () -> sprite,
                onFirstError);

        c.afterFrameAdvanced(new Bk2FrameInput(0, 0, 0, false, "0"), false);
        c.afterFrameAdvanced(new Bk2FrameInput(1, 0, 0, false, "0"), false);

        assertTrue(c.hasRecordingDesync());
        verify(onFirstError, times(1)).run();
    }

    @Test
    void rewindSeekShowsLastAppliedFrameButKeepsNextComparisonCursor() {
        AbstractPlayableSprite sprite = mock(AbstractPlayableSprite.class);
        when(sprite.getCentreX()).thenReturn((short) 300);
        when(sprite.getCentreY()).thenReturn((short) 0);
        when(sprite.getXSpeed()).thenReturn((short) 0);
        when(sprite.getYSpeed()).thenReturn((short) 0);
        when(sprite.getGSpeed()).thenReturn((short) 0);
        when(sprite.getAngle()).thenReturn((byte) 0);
        when(sprite.getAir()).thenReturn(false);
        when(sprite.getRolling()).thenReturn(false);
        when(sprite.getGroundMode()).thenReturn(GroundMode.GROUND);

        LiveTraceComparator c = new LiveTraceComparator(
                stubTrace(List.of(
                        TraceFrame.of(0, 0, (short) 100, (short) 0,
                                (short) 0, (short) 0, (short) 0,
                                (byte) 0, false, false, 0),
                        TraceFrame.of(1, 0, (short) 200, (short) 0,
                                (short) 0, (short) 0, (short) 0,
                                (byte) 0, false, false, 0),
                        TraceFrame.of(2, 0, (short) 300, (short) 0,
                                (short) 0, (short) 0, (short) 0,
                                (byte) 0, false, false, 0))),
                ToleranceConfig.DEFAULT,
                0,
                () -> sprite);

        c.seekForRewind(2);

        assertEquals(1, c.currentVisualFrame().frame(),
                "restored frame boundary 2 should draw the last applied trace frame");

        c.afterFrameAdvanced(new Bk2FrameInput(2, 0, 0, false, "0"), false);

        assertEquals(0, c.errorCount(),
                "the next live comparison after releasing rewind should still use cursor 2");
        assertEquals(2, c.currentVisualFrame().frame());
    }

    private static TraceData stubTrace(List<TraceFrame> frames) {
        return TraceFixtures.trace(TraceFixtures.metadata("s2", 0, 0), frames);
    }
}
