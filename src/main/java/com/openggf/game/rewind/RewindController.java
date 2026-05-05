package com.openggf.game.rewind;

import com.openggf.debug.playback.Bk2FrameInput;

import java.util.Objects;

public final class RewindController {

    private final RewindRegistry registry;
    private final KeyframeStore keyframes;
    private final InputSource inputs;
    private final EngineStepper engineStepper;
    private final SegmentCache segmentCache;
    private final int keyframeInterval;

    private int currentFrame;

    public RewindController(
            RewindRegistry registry,
            KeyframeStore keyframes,
            InputSource inputs,
            EngineStepper engineStepper,
            int keyframeInterval) {
        this.registry = Objects.requireNonNull(registry);
        this.keyframes = Objects.requireNonNull(keyframes);
        this.inputs = Objects.requireNonNull(inputs);
        this.engineStepper = Objects.requireNonNull(engineStepper);
        if (keyframeInterval <= 0) {
            throw new IllegalArgumentException(
                    "keyframeInterval must be > 0, got " + keyframeInterval);
        }
        this.keyframeInterval = keyframeInterval;
        this.segmentCache = new SegmentCache(keyframeInterval);
        this.currentFrame = 0;
        // Capture frame 0 so seekTo(0) always has a base.
        keyframes.put(0, registry.capture());
    }

    public int currentFrame() { return currentFrame; }

    public int earliestAvailableFrame() {
        // v1: trace mode — earliest accessible frame is whatever the
        // earliest stored keyframe is (typically 0).
        int e = keyframes.earliestFrame();
        return e < 0 ? 0 : e;
    }

    /** Steps forward one frame, capturing a keyframe at the boundary. */
    public void step() {
        if (currentFrame + 1 >= inputs.frameCount()) {
            return;   // end of trace
        }
        Bk2FrameInput in = inputs.read(currentFrame + 1);
        engineStepper.step(in);
        currentFrame++;
        if (currentFrame % keyframeInterval == 0) {
            keyframes.put(currentFrame, registry.capture());
        }
    }

    /**
     * Seeks to {@code targetFrame} by restoring the latest keyframe at or
     * before it, then stepping forward. Held-rewind callers should use
     * {@link #stepBackward()} for steady-state O(1) cost.
     */
    public void seekTo(int targetFrame) {
        if (targetFrame == currentFrame) return;
        if (targetFrame < earliestAvailableFrame()) {
            targetFrame = earliestAvailableFrame();
        }
        final int clampedTarget = targetFrame;
        var floor = keyframes.latestAtOrBefore(clampedTarget).orElseThrow(
                () -> new IllegalStateException(
                        "no keyframe at or before " + clampedTarget));
        segmentCache.invalidate();
        registry.restore(floor.snapshot());
        currentFrame = floor.frame();
        while (currentFrame < clampedTarget) {
            Bk2FrameInput in = inputs.read(currentFrame + 1);
            engineStepper.step(in);
            currentFrame++;
        }
    }

    /**
     * Rewinds one frame using the segment cache for amortised O(1) cost.
     * Returns false if already at {@code earliestAvailableFrame}.
     */
    public boolean stepBackward() {
        if (currentFrame <= earliestAvailableFrame()) return false;
        int target = currentFrame - 1;
        int keyframeFrame = (target / keyframeInterval) * keyframeInterval;
        final var floor = keyframes.latestAtOrBefore(keyframeFrame).orElseThrow();
        final int keyframeSnapshot = floor.frame();
        final var restoreSnapshot = floor.snapshot();
        // Use int[] wrapper to allow mutation within lambdas
        final int[] pos = { currentFrame };
        CompositeSnapshot snap = segmentCache.snapshotAt(
                target,
                restoreSnapshot,
                keyframeSnapshot,
                () -> {
                    registry.restore(restoreSnapshot);
                    pos[0] = keyframeSnapshot;
                },
                () -> {
                    Bk2FrameInput in = inputs.read(pos[0] + 1);
                    engineStepper.step(in);
                    pos[0]++;
                    return registry.capture();
                });
        registry.restore(snap);
        currentFrame = target;
        return true;
    }
}
