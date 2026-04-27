package com.openggf.trace;

import com.openggf.game.GameServices;
import com.openggf.level.objects.ObjectManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.trace.replay.TraceReplayFixture;

import java.util.List;

/**
 * Shared trace replay bootstrap helpers.
 *
 * <p>Trace rows are a read-only comparison ledger. This class may align replay
 * cursors and classify execution phases from trace metadata/events, but it must
 * not copy recorded player, sidekick, object, camera, RNG, or CPU state back
 * into the engine.
 */
public final class TraceReplayBootstrap {

    public record ReplayStartState(int startingTraceIndex, int seededTraceIndex) {
        public static final ReplayStartState DEFAULT = new ReplayStartState(0, -1);

        public boolean hasSeededTraceState() {
            return seededTraceIndex >= 0;
        }
    }

    public record ReplayPrimaryState(
            short x,
            short y,
            short xSpeed,
            short ySpeed,
            short gSpeed,
            byte angle,
            boolean air,
            boolean rolling,
            int groundMode,
            int xSub,
            int ySub,
            String source) {

        public static ReplayPrimaryState fromSprite(AbstractPlayableSprite sprite) {
            return new ReplayPrimaryState(
                    sprite.getCentreX(),
                    sprite.getCentreY(),
                    sprite.getXSpeed(),
                    sprite.getYSpeed(),
                    sprite.getGSpeed(),
                    sprite.getAngle(),
                    sprite.getAir(),
                    sprite.getRolling(),
                    sprite.getGroundMode().ordinal(),
                    sprite.getXSubpixelRaw(),
                    sprite.getYSubpixelRaw(),
                    "player");
        }
    }

    private TraceReplayBootstrap() {
    }

    public static TraceObjectSnapshotBinder.Result applyPreTraceState(TraceData trace,
                                                                     TraceReplayFixture fixture) {
        return TraceObjectSnapshotBinder.apply(
                GameServices.level() != null ? GameServices.level().getObjectManager() : null,
                List.of());
    }

    public static void applyPreTracePlayerHistory(TraceEvent.PlayerHistorySnapshot snapshot,
                                                  AbstractPlayableSprite sprite) {
        // Deliberately no-op: trace history snapshots are diagnostic context,
        // not engine input.
    }

    public static ReplayStartState applyReplayStartState(TraceData trace,
                                                         TraceReplayFixture fixture) {
        return applyReplayStartStateForTraceReplay(trace, fixture);
    }

    public static ReplayStartState applyReplayStartStateForTraceReplay(TraceData trace,
                                                                       TraceReplayFixture fixture) {
        if (shouldUseLegacyS3kAizIntroWarmup(trace)) {
            return warmupLegacyS3kAizTraceReplay(trace, fixture);
        }
        return new ReplayStartState(replaySeedTraceIndexForTraceReplay(trace), -1);
    }

    /**
     * Compatibility entrypoint for callers that used to request a seeded replay
     * start. It now only returns the unseeded comparison cursor.
     */
    public static ReplayStartState applySeedReplayStartStateForTraceReplay(TraceData trace,
                                                                           TraceReplayFixture fixture) {
        return applyReplayStartStateForTraceReplay(trace, fixture);
    }

    public static int recordingStartFrameForTraceReplay(TraceData trace) {
        if (trace == null) {
            return 0;
        }
        if (shouldUseLegacyS3kAizIntroWarmup(trace)) {
            return trace.metadata().bk2FrameOffset();
        }
        int seedTraceIndex = replaySeedTraceIndexForTraceReplay(trace);
        return trace.metadata().bk2FrameOffset() + Math.max(0, seedTraceIndex - 1);
    }

    /**
     * Returns the first trace frame where a ZoneActState or Checkpoint event
     * reports {@code game_mode=0x0C} (LEVEL). Headless replay fixtures start
     * the engine directly in gamemode 0x0C, but the recorder usually runs
     * for some number of frames in SEGA/title/level-load gamemodes before
     * the ROM reaches LevelLoop for the first time. Many ROM systems
     * (OscillateNumDo, sprite placement cursor, random lookups) only tick
     * inside LevelLoop, so replay drivers need to know how many leading
     * frames to neutralise from the engine-side so the ROM and engine
     * stay phase-aligned over long traces.
     */
    public static int preLevelFrameCountForTraceReplay(TraceData trace) {
        if (trace == null || trace.frameCount() == 0) {
            return 0;
        }
        return findFirstLevelGameplayFrame(trace);
    }

    public static int replaySeedTraceIndexForTraceReplay(TraceData trace) {
        if (trace == null || trace.frameCount() == 0) {
            return 0;
        }
        if (isLegacyS3kAizIntroTrace(trace)) {
            // The AIZ full-run fixture records the intro/cutscene timeline from its
            // own frame 0. Replaying from the first in-level frame skips hundreds of
            // recorded intro frames and loses global state that the seed frame alone
            // cannot reconstruct (timers, title-card state, zone-event evolution).
            return 0;
        }
        int firstLevelFrame = findFirstLevelGameplayFrame(trace);
        return Math.max(firstLevelFrame, 0);
    }

    public static int initialVblankCounterForTraceReplay(TraceData trace) {
        if (trace == null || trace.frameCount() == 0) {
            return 0;
        }
        return recordingStartFrameForTraceReplay(trace);
    }

    public static int preTraceOscillationFramesForTraceReplay(TraceData trace,
                                                              int override) {
        if (override >= 0) {
            return override;
        }
        if (trace == null || trace.frameCount() == 0
                || shouldUseLegacyS3kAizIntroWarmup(trace)) {
            return 0;
        }
        int seedTraceIndex = replaySeedTraceIndexForTraceReplay(trace);
        if (seedTraceIndex < 0 || seedTraceIndex >= trace.frameCount()) {
            return 0;
        }
        int firstComparedGameplayFrame =
                trace.getFrame(seedTraceIndex).gameplayFrameCounter();
        // The replay loop steps the seed trace row before comparing it. A row
        // with gameplay_frame_counter=1 has already observed the ROM's first
        // LevelLoop tick, but the headless fixture will produce that same tick
        // natively when it steps the row. Only pre-advance ticks that completed
        // before the first compared row.
        return Math.max(0, firstComparedGameplayFrame - 1);
    }

    /**
     * Number of native sidekick-only object ticks that occur after level load
     * but before the first gameplay comparison frame. Sonic 2's title-card
     * path runs Obj02/Tails CPU for ten frames while Sonic's own level-frame
     * physics is still held; the first recorded gameplay row then observes
     * Sonic's first input-driven movement frame and Tails' eleventh follower
     * tick. This is derived from execution timing, not from recorded Tails
     * fields.
     */
    public static int sidekickTitleCardPreludeFramesForTraceReplay(TraceData trace) {
        if (trace == null || trace.frameCount() == 0
                || trace.metadata().recordedSidekicks().isEmpty()
                || shouldUseLegacyS3kAizIntroWarmup(trace)) {
            return 0;
        }
        if (!"s2".equals(trace.metadata().game())) {
            return 0;
        }
        TraceFrame firstFrame = trace.getFrame(replaySeedTraceIndexForTraceReplay(trace));
        return firstFrame.gameplayFrameCounter() == 1 ? 10 : 0;
    }

    /**
     * Returns false because trace start state is comparison data only. Kept as
     * a named policy gate for callers that need to avoid legacy hydration paths.
     */
    public static boolean shouldUseTraceStartBootstrapForTraceReplay(TraceData trace) {
        return false;
    }

    public static boolean shouldSeedFrameZeroForTraceReplay(TraceData trace) {
        return false;
    }

    public static boolean shouldSeedReplayStartStateForTraceReplay(TraceData trace,
                                                                   int requestedSeedTraceIndex) {
        return false;
    }

    public static boolean requiresFreshLevelLoadForTraceReplay(TraceData trace) {
        return isLegacyS3kAizIntroTrace(trace)
                && replaySeedTraceIndexForTraceReplay(trace) == 0;
    }

    public static boolean shouldUseLegacyS3kAizIntroWarmup(TraceData trace) {
        return isLegacyS3kAizIntroTrace(trace);
    }

    public static boolean shouldApplyMetadataStartPositionForTraceReplay(TraceData trace) {
        return replaySeedTraceIndexForTraceReplay(trace) == 0
                && !shouldUseLegacyS3kAizIntroWarmup(trace);
    }

    public static int strictStartTraceIndexForTraceReplay(TraceData trace) {
        if (trace == null || trace.frameCount() == 0) {
            return 0;
        }
        if (!shouldUseLegacyS3kAizIntroWarmup(trace)) {
            return replaySeedTraceIndexForTraceReplay(trace);
        }

        int firstGameplayModeFrame = findFirstLevelGameplayFrame(trace);
        return Math.min(firstGameplayModeFrame + 1, trace.frameCount() - 1);
    }

    public static ReplayPrimaryState capturePrimaryReplayStateForComparison(TraceData trace,
                                                                            TraceFrame current,
                                                                            AbstractPlayableSprite sprite) {
        if (sprite == null) {
            throw new IllegalArgumentException("sprite must not be null");
        }
        return ReplayPrimaryState.fromSprite(sprite);
    }

    private static ReplayStartState warmupLegacyS3kAizTraceReplay(TraceData trace,
                                                                  TraceReplayFixture fixture) {
        if (trace == null || trace.frameCount() == 0) {
            return ReplayStartState.DEFAULT;
        }

        int strictStartTraceIndex = strictStartTraceIndexForTraceReplay(trace);
        if (strictStartTraceIndex <= 0) {
            return ReplayStartState.DEFAULT;
        }
        TraceFrame previous = null;
        for (int traceIndex = 0; traceIndex < strictStartTraceIndex; traceIndex++) {
            TraceFrame current = trace.getFrame(traceIndex);
            TraceExecutionPhase phase = phaseForReplay(trace, previous, current);
            if (phase == TraceExecutionPhase.VBLANK_ONLY) {
                fixture.skipFrameFromRecording();
            } else {
                fixture.stepFrameFromRecording();
            }
            previous = current;
        }
        return new ReplayStartState(strictStartTraceIndex, -1);
    }


    public static TraceExecutionPhase phaseForReplay(TraceData trace,
                                                     TraceFrame previous,
                                                     TraceFrame current) {
        if (shouldUseLegacyS3kAizIntroHeuristic(trace, current)) {
            return deriveLegacyPhase(previous, current);
        }
        return TraceExecutionModel.forGame(trace.metadata().game()).phaseFor(previous, current);
    }


    private static boolean shouldUseLegacyS3kAizIntroHeuristic(TraceData trace,
                                                                TraceFrame current) {
        if (trace == null || current == null || !isLegacyS3kAizIntroTrace(trace)) {
            return false;
        }
        int gameplayStartFrame = findCheckpointFrame(trace, "gameplay_start");
        return gameplayStartFrame >= 0 && current.frame() <= gameplayStartFrame;
    }

    /**
     * Legacy S3K AIZ intro traces keep gameplay_frame_counter pinned during the
     * opening cutscene, so the normal execution model misclassifies many real
     * gameplay frames as VBlank-only. Use the old state-change heuristic for
     * the intro window instead of forcing every frame to full execution.
     */
    private static TraceExecutionPhase deriveLegacyPhase(TraceFrame previous,
                                                         TraceFrame current) {
        if (previous == null || current == null) {
            return TraceExecutionPhase.FULL_LEVEL_FRAME;
        }
        if (!current.stateEquals(previous)) {
            return TraceExecutionPhase.FULL_LEVEL_FRAME;
        }
        // Pre-level frames (SEGA/title/level-load) leave stale non-zero speed
        // fields in the Player_1 RAM block that the recorder samples. When the
        // gameplay_frame_counter stays pinned across two consecutive frames,
        // treat the state as "game is still initializing / intro cutscene
        // running" rather than trusting the stale speed fields, which would
        // otherwise misclassify frozen-state frames as VBLANK_ONLY.
        if (previous.gameplayFrameCounter() >= 0
                && current.gameplayFrameCounter() >= 0
                && previous.gameplayFrameCounter() == current.gameplayFrameCounter()) {
            return TraceExecutionPhase.FULL_LEVEL_FRAME;
        }
        return current.xSpeed() != 0 || current.ySpeed() != 0
                || current.gSpeed() != 0 || current.air()
                ? TraceExecutionPhase.VBLANK_ONLY
                : TraceExecutionPhase.FULL_LEVEL_FRAME;
    }

    private static boolean isLegacyS3kAizIntroTrace(TraceData trace) {
        if (trace == null) {
            return false;
        }
        TraceMetadata metadata = trace.metadata();
        if (!"s3k".equals(metadata.game())) {
            return false;
        }
        if (metadata.zoneId() == null || metadata.zoneId() != 0 || metadata.act() != 1) {
            return false;
        }
        return trace.getEventsForFrame(0).stream()
                .filter(TraceEvent.Checkpoint.class::isInstance)
                .map(TraceEvent.Checkpoint.class::cast)
                .anyMatch(checkpoint -> "intro_begin".equals(checkpoint.name()));
    }


    private static int findCheckpointFrame(TraceData trace, String checkpointName) {
        for (int frame = 0; frame < trace.frameCount(); frame++) {
            for (TraceEvent event : trace.getEventsForFrame(frame)) {
                if (event instanceof TraceEvent.Checkpoint checkpoint
                        && checkpointName.equals(checkpoint.name())) {
                    return frame;
                }
            }
        }
        return -1;
    }

    private static int findFirstLevelGameplayFrame(TraceData trace) {
        for (int frame = 0; frame < trace.frameCount(); frame++) {
            for (TraceEvent event : trace.getEventsForFrame(frame)) {
                if (event instanceof TraceEvent.ZoneActState state
                        && state.gameMode() != null
                        && state.gameMode() == 12) {
                    return frame;
                }
                if (event instanceof TraceEvent.Checkpoint checkpoint
                        && checkpoint.gameMode() != null
                        && checkpoint.gameMode() == 12) {
                    return frame;
                }
            }
        }
        return 0;
    }

}
