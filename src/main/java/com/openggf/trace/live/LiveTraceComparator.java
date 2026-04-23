package com.openggf.trace.live;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.PlaybackDebugManager.PlaybackFrameObserver;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.trace.FieldComparison;
import com.openggf.trace.FrameComparison;
import com.openggf.trace.Severity;
import com.openggf.trace.ToleranceConfig;
import com.openggf.trace.TraceBinder;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceEvent;
import com.openggf.trace.TraceExecutionPhase;
import com.openggf.trace.TraceFrame;
import com.openggf.trace.TraceReplayBootstrap;

import java.util.List;
import java.util.function.Supplier;

/**
 * Engine-side per-frame trace comparator. Attached to
 * {@link com.openggf.debug.playback.PlaybackDebugManager} as a
 * {@link PlaybackFrameObserver}; gates ROM lag frames and accumulates
 * divergences into a ring buffer plus counters.
 */
public final class LiveTraceComparator implements PlaybackFrameObserver {
    private static final int RING_CAPACITY = 5;

    private final TraceData trace;
    private final TraceBinder binder;
    private final MismatchRingBuffer mismatches = new MismatchRingBuffer(RING_CAPACITY);
    private final Supplier<AbstractPlayableSprite> spriteProvider;

    private int cursor;
    private int errorCount;
    private int warningCount;
    private int laggedFrames;
    private int lastActionMask;
    private int lastInputMask;
    private boolean lastStartPressed;
    private boolean complete;
    private boolean gameplayStartSeen;

    public LiveTraceComparator(TraceData trace,
                               ToleranceConfig tolerances,
                               int initialCursor,
                               Supplier<AbstractPlayableSprite> spriteProvider) {
        this.trace = trace;
        this.binder = new TraceBinder(tolerances);
        this.cursor = initialCursor;
        this.spriteProvider = spriteProvider;
    }

    @Override
    public boolean shouldSkipGameplayTick(Bk2FrameInput frame) {
        if (cursor >= trace.frameCount()) {
            return false;
        }
        TraceFrame current = trace.getFrame(cursor);
        TraceFrame previous = cursor > 0 ? trace.getFrame(cursor - 1) : null;
        TraceExecutionPhase phase =
                TraceReplayBootstrap.phaseForReplay(trace, previous, current);
        return phase == TraceExecutionPhase.VBLANK_ONLY;
    }

    @Override
    public void afterFrameAdvanced(Bk2FrameInput frame, boolean wasSkipped) {
        lastActionMask = frame.p1ActionMask();
        lastInputMask = frame.p1InputMask();
        lastStartPressed = frame.p1StartPressed();
        if (wasSkipped) {
            laggedFrames++;
            cursor++;
            checkComplete();
            return;
        }
        if (cursor >= trace.frameCount()) {
            checkComplete();
            return;
        }
        TraceFrame expected = trace.getFrame(cursor);
        if (shouldSuppressComparison(expected)) {
            cursor++;
            checkComplete();
            return;
        }
        AbstractPlayableSprite sprite = spriteProvider.get();
        if (sprite == null) {
            cursor++;
            checkComplete();
            return;
        }
        FrameComparison result = binder.compareFrame(expected,
                sprite.getCentreX(), sprite.getCentreY(),
                sprite.getXSpeed(), sprite.getYSpeed(), sprite.getGSpeed(),
                sprite.getAngle(), sprite.getAir(), sprite.getRolling(),
                sprite.getGroundMode().ordinal());
        absorbDivergentFields(result, expected.frame());
        cursor++;
        checkComplete();
    }

    private boolean shouldSuppressComparison(TraceFrame expected) {
        if (!"s3k".equals(trace.metadata().game())) {
            return false;
        }
        if (gameplayStartSeen) {
            return false;
        }
        boolean isGameplayStart = trace.getEventsForFrame(expected.frame()).stream()
                .anyMatch(e -> e instanceof TraceEvent.Checkpoint cp
                        && "gameplay_start".equals(cp.name()));
        if (isGameplayStart) {
            gameplayStartSeen = true;
        }
        return !gameplayStartSeen;
    }

    private void absorbDivergentFields(FrameComparison result, int frameNumber) {
        List<FieldComparison> divergent = result.divergentFields();
        for (FieldComparison fc : divergent) {
            Severity sev = fc.severity();
            if (sev == Severity.ERROR) {
                errorCount++;
            } else if (sev == Severity.WARNING) {
                warningCount++;
            } else {
                continue;
            }
            mismatches.push(new MismatchEntry(
                    frameNumber,
                    fc.fieldName(),
                    fc.expected(),
                    fc.actual(),
                    Integer.toString(fc.delta()),
                    sev,
                    1));
        }
    }

    private void checkComplete() {
        if (cursor >= trace.frameCount()) {
            complete = true;
        }
    }

    public int errorCount() { return errorCount; }
    public int warningCount() { return warningCount; }
    public int laggedFrames() { return laggedFrames; }
    public boolean isComplete() { return complete; }
    public List<MismatchEntry> recentMismatches() { return mismatches.recent(); }
    public int recentActionMask() { return lastActionMask; }
    public int recentInputMask() { return lastInputMask; }
    public boolean recentStartPressed() { return lastStartPressed; }
}
