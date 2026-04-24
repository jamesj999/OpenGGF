package com.openggf.trace.live;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.PlaybackDebugManager.PlaybackFrameObserver;
import com.openggf.game.GameServices;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.trace.FieldComparison;
import com.openggf.trace.FrameComparison;
import com.openggf.trace.Severity;
import com.openggf.trace.ToleranceConfig;
import com.openggf.trace.TraceBinder;
import com.openggf.trace.TraceCharacterState;
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
    private boolean firstErrorLogged;
    private boolean firstWarningLogged;
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
        // Seeded S3K replays resume at cursor > 0, but the
        // gameplay_start checkpoint is typically emitted on trace
        // frame 0. Without this sweep, shouldSuppressComparison never
        // observes the checkpoint and silently discards every frame
        // comparison. Scan the skipped prefix up front so replays that
        // splice past frame 0 still unlock the comparator.
        for (int f = 0; f < initialCursor && f < trace.frameCount(); f++) {
            boolean seen = trace.getEventsForFrame(f).stream()
                    .anyMatch(e -> e instanceof TraceEvent.Checkpoint cp
                            && "gameplay_start".equals(cp.name()));
            if (seen) {
                gameplayStartSeen = true;
                break;
            }
        }
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
        // Pass the first sidekick's state too so the binder's
        // appendCharacterComparisons finds an actual value instead of
        // flagging every recorded sidekick field as divergent (EHZ1
        // etc. record Sonic+Tails).
        TraceCharacterState actualSidekick = captureFirstSidekickState();
        FrameComparison result = binder.compareFrame(expected,
                sprite.getCentreX(), sprite.getCentreY(),
                sprite.getXSpeed(), sprite.getYSpeed(), sprite.getGSpeed(),
                sprite.getAngle(), sprite.getAir(), sprite.getRolling(),
                sprite.getGroundMode().ordinal(),
                null, null,
                "sidekick", actualSidekick);
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
            if (sev == Severity.ERROR && !firstErrorLogged) {
                firstErrorLogged = true;
                System.err.printf(
                        "[LiveTraceComparator] FIRST ERROR at trace frame %d:%n"
                                + "  field=%s expected=%s actual=%s delta=%d%n"
                                + "  full frame comparison: %s%n"
                                + "  active objects near player:%n%s",
                        frameNumber,
                        fc.fieldName(),
                        fc.expected(),
                        fc.actual(),
                        fc.delta(),
                        result,
                        summariseNearbyObjects());
            } else if (sev == Severity.WARNING && !firstWarningLogged) {
                firstWarningLogged = true;
                System.err.printf(
                        "[LiveTraceComparator] FIRST WARNING at trace frame %d:%n"
                                + "  field=%s expected=%s actual=%s delta=%d%n"
                                + "  full frame comparison: %s%n"
                                + "  active objects near player:%n%s",
                        frameNumber,
                        fc.fieldName(),
                        fc.expected(),
                        fc.actual(),
                        fc.delta(),
                        result,
                        summariseNearbyObjects());
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

    private String summariseNearbyObjects() {
        ObjectManager om = GameServices.level() != null
                ? GameServices.level().getObjectManager() : null;
        if (om == null) {
            return "    (no ObjectManager)";
        }
        AbstractPlayableSprite sprite = spriteProvider.get();
        int px = sprite != null ? sprite.getCentreX() & 0xFFFF : 0;
        int py = sprite != null ? sprite.getCentreY() & 0xFFFF : 0;
        StringBuilder sb = new StringBuilder();
        for (ObjectInstance inst : om.getActiveObjects()) {
            if (!(inst instanceof AbstractObjectInstance aoi)) {
                continue;
            }
            // Dynamic effects/projectiles can legitimately have a null
            // spawn (see AbstractObjectInstance.snapshotPreUpdatePosition);
            // the interface getX()/getY() default would NPE on them. They
            // also have no meaningful placement coordinate to show in a
            // "nearby objects" summary, so skip them.
            if (aoi.getSpawn() == null) {
                continue;
            }
            int ox = aoi.getX() & 0xFFFF;
            int oy = aoi.getY() & 0xFFFF;
            int dx = Math.abs(ox - px);
            int dy = Math.abs(oy - py);
            // Include anything within a ~screen-width horizontal box —
            // the divergence is typically tied to badniks a few blocks
            // ahead of the player, not off-screen spawns.
            if (dx > 0x180 || dy > 0x100) {
                continue;
            }
            int id = aoi.getSpawn().objectId();
            sb.append(String.format(
                    "    slot=%3d id=0x%02X %s @%04X,%04X (dx=%d dy=%d)%n",
                    aoi.getSlotIndex(),
                    id & 0xFF,
                    aoi.getClass().getSimpleName(),
                    ox, oy,
                    ox - px, oy - py));
        }
        if (sb.length() == 0) {
            return "    (no active objects within a screen-width of the player)";
        }
        return sb.toString();
    }

    private static TraceCharacterState captureFirstSidekickState() {
        SpriteManager sprites = GameServices.sprites();
        if (sprites == null || sprites.getSidekicks().isEmpty()) {
            return null;
        }
        return TraceCharacterState.fromSprite(sprites.getSidekicks().getFirst());
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
