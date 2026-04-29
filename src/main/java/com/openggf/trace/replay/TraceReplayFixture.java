package com.openggf.trace.replay;

import com.openggf.game.GameRuntime;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Narrow view of a fixture capable of driving trace replay. Implemented
 * by {@code HeadlessTestFixture} in tests and by the live launcher's
 * internal adapter at runtime.
 */
public interface TraceReplayFixture {
    AbstractPlayableSprite sprite();

    GameRuntime runtime();

    /** Run one gameplay tick using the next BK2 input. Returns the mask. */
    int stepFrameFromRecording();

    /** Advance BK2 without stepping gameplay (lag frame). Returns the mask. */
    int skipFrameFromRecording();

    /** Consume one BK2 frame without stepping gameplay or timing counters. Returns the mask. */
    int consumeRecordingFrameInputOnly();

    /** Advance the BK2 cursor by N frames, no gameplay ticks. */
    void advanceRecordingCursor(int frameCount);
}
