package com.openggf.trace;

import com.openggf.game.GameServices;
import com.openggf.game.LevelEventProvider;
import com.openggf.game.TitleCardProvider;
import com.openggf.game.GroundMode;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.objects.AizPlaneIntroInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.RomObjectSnapshot;
import com.openggf.physics.Direction;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SidekickCpuController;
import com.openggf.trace.replay.TraceReplayFixture;

import java.util.List;

/**
 * Shared pre-trace hydration for replay-style tests.
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
        applyPreTracePlayerHistory(trace.preTracePlayerHistorySnapshot(), fixture.sprite());

        ObjectManager objectManager = GameServices.level().getObjectManager();
        List<TraceEvent.ObjectStateSnapshot> snapshots = trace.preTraceObjectSnapshots();
        applyPreTraceSidekickCpuSnapshot(trace);
        if (objectManager == null || snapshots.isEmpty()) {
            return new TraceObjectSnapshotBinder.Result(0, 0, List.of());
        }

        applyPreTraceSidekickSnapshot(trace, snapshots);
        objectManager.preloadInitialSpawnsForHydration();
        return TraceObjectSnapshotBinder.apply(
                objectManager,
                snapshots.stream()
                        .filter(snapshot -> snapshot.slot() >= 2)
                        .toList());
    }

    public static void applyPreTracePlayerHistory(TraceEvent.PlayerHistorySnapshot snapshot,
                                                  AbstractPlayableSprite sprite) {
        if (snapshot == null || sprite == null) {
            return;
        }
        sprite.hydrateRecordedHistory(
                TraceHistoryHydration.centreHistoryToTopLeft(snapshot.xHistory(), sprite.getWidth()),
                TraceHistoryHydration.centreHistoryToTopLeft(snapshot.yHistory(), sprite.getHeight()),
                snapshot.inputHistory(),
                snapshot.statusHistory(),
                TraceHistoryHydration.romHistoryPosToEngineLatestSlot(snapshot.historyPos()));
    }

    public static void hydrateSidekickFromSnapshot(AbstractPlayableSprite sidekick,
                                                   RomObjectSnapshot snapshot,
                                                   TraceEvent.CpuStateSnapshot cpuSnapshot) {
        if (sidekick == null || snapshot == null || snapshot.isEmpty()) {
            return;
        }

        int status = snapshot.status();
        int objControl = snapshot.byteAt(0x2A);

        sidekick.setControlLocked(objControl != 0);
        sidekick.setObjectControlled(objControl != 0);
        sidekick.setMoveLockTimer(snapshot.wordAt(0x2E));
        sidekick.setHurt(snapshot.routine() == 0x04);
        sidekick.setDead(snapshot.routine() >= 0x06);
        sidekick.setDeathCountdown(0);
        sidekick.setSpindash(snapshot.byteAt(0x39) != 0);
        sidekick.setSpindashCounter((short) snapshot.wordAt(0x3A));
        sidekick.setXSpeed((short) snapshot.xVel());
        sidekick.setYSpeed((short) snapshot.yVel());
        sidekick.setGSpeed((short) snapshot.signedWordAt(0x14));
        sidekick.setAngle((byte) snapshot.angle());
        sidekick.setDirection((status & 0x01) != 0 ? Direction.LEFT : Direction.RIGHT);
        sidekick.setAir((status & 0x02) != 0);
        sidekick.setRolling((status & 0x04) != 0);
        sidekick.setOnObject((status & 0x08) != 0);
        sidekick.setRollingJump((status & 0x10) != 0);
        sidekick.setPushing((status & 0x20) != 0);
        sidekick.setInWater((status & 0x40) != 0);
        sidekick.setPreventTailsRespawn((status & 0x80) != 0);
        sidekick.setRenderFlagWidthPixels(snapshot.byteAt(0x19));
        sidekick.setRenderFlagOnScreen((snapshot.byteAt(0x01) & 0x80) != 0);
        sidekick.setAnimationId(snapshot.animId());
        sidekick.setCentreX((short) snapshot.xPos());
        sidekick.setCentreY((short) snapshot.yPos());
        sidekick.setSubpixelRaw(snapshot.xSub(), snapshot.ySub());
        sidekick.resetPositionHistory();

        SidekickCpuController controller = sidekick.getCpuController();
        if (controller != null) {
            if (cpuSnapshot != null) {
                controller.hydrateFromRomCpuState(
                        cpuSnapshot.cpuRoutine(),
                        cpuSnapshot.controlCounter(),
                        cpuSnapshot.respawnCounter(),
                        cpuSnapshot.interactId(),
                        cpuSnapshot.jumping());
            } else {
                controller.setInitialState(SidekickCpuController.State.NORMAL);
            }
        }
    }

    public static ReplayStartState applyReplayStartState(TraceData trace,
                                                         TraceReplayFixture fixture) {
        return applyReplayStartState(trace, fixture, true);
    }

    public static ReplayStartState applyReplayStartStateForTraceReplay(TraceData trace,
                                                                       TraceReplayFixture fixture) {
        if (shouldUseLegacyS3kAizIntroWarmup(trace)) {
            return warmupLegacyS3kAizTraceReplay(trace, fixture);
        }
        return applyReplayStartState(trace, fixture, false, replaySeedTraceIndexForTraceReplay(trace));
    }

    /**
     * Seeds legacy S3K AIZ end-to-end traces at the first live in-level frame
     * (trace frame 0) instead of running the full warmup up to strict start.
     * Callers that need the seed-at-0 semantics (intro object still at routine 0,
     * ObjectManager VBlank matches frame 0, sprite frozen at recorded frame 0
     * position) should use this entrypoint. For non-legacy traces this behaves
     * identically to {@link #applyReplayStartStateForTraceReplay}.
     *
     * <p>The warmup-compatible fixture initializes the ObjectManager VBla
     * counter to the strict-start frame's vblank. Seed-at-0 callers need the
     * counter aligned with the seed frame's vblank instead, so reset it here
     * before the seed path runs its level-event update and advance.
     */
    public static ReplayStartState applySeedReplayStartStateForTraceReplay(TraceData trace,
                                                                           TraceReplayFixture fixture) {
        if (shouldUseLegacyS3kAizIntroWarmup(trace)) {
            int seedTraceIndex = replaySeedTraceIndexForTraceReplay(trace);
            ObjectManager objectManager = GameServices.level() != null
                    ? GameServices.level().getObjectManager()
                    : null;
            if (objectManager != null && trace != null && trace.frameCount() > 0) {
                objectManager.initVblaCounter(trace.getFrame(seedTraceIndex).vblankCounter() - 1);
            }
        }
        return applyReplayStartState(trace, fixture, false, replaySeedTraceIndexForTraceReplay(trace));
    }

    public static int recordingStartFrameForTraceReplay(TraceData trace) {
        if (trace == null) {
            return 0;
        }
        if (shouldUseLegacyS3kAizIntroWarmup(trace)) {
            int strictStartTraceIndex = strictStartTraceIndexForTraceReplay(trace);
            // Trace frame N represents the end-of-frame state reached after consuming
            // the movie input for frame N-1. Seeded replay paths naturally preserve
            // that relationship because they restore trace frame N and resume from
            // trace frame N+1 while the BK2 cursor is still parked on N. The legacy
            // AIZ intro warmup path has no seed frame, so pre-roll the movie cursor
            // by one input frame to keep the first strict replay frame on the same
            // controller sample cadence as the rest of trace replay.
            return trace.metadata().bk2FrameOffset() + Math.max(0, strictStartTraceIndex - 1);
        }
        int seedTraceIndex = replaySeedTraceIndexForTraceReplay(trace);
        if (shouldSeedFrameZeroForTraceReplay(trace)) {
            // Frame 0 has already been restored as an end-of-frame snapshot.
            // Resume movie input at the next trace frame so a newly pressed
            // button (e.g. CNZ's first jump) affects the same frame the ROM
            // recorded, instead of being consumed one tick late.
            return trace.metadata().bk2FrameOffset() + seedTraceIndex + 1;
        }
        return trace.metadata().bk2FrameOffset() + seedTraceIndex;
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
        int traceIndex = shouldUseLegacyS3kAizIntroWarmup(trace)
                ? strictStartTraceIndexForTraceReplay(trace)
                : replaySeedTraceIndexForTraceReplay(trace);
        return trace.getFrame(traceIndex).vblankCounter();
    }

    /**
     * Returns true when the replay bootstrap should seed the engine from the
     * trace's recorded start state rather than driving gameplay from frame 0
     * without hydration. Currently always true; kept as a gate so future
     * trace schemas can opt out.
     */
    public static boolean shouldUseTraceStartBootstrapForTraceReplay(TraceData trace) {
        return true;
    }

    public static boolean shouldSeedFrameZeroForTraceReplay(TraceData trace) {
        if (trace == null || trace.frameCount() == 0) {
            return false;
        }
        if (isLegacyS3kAizIntroTrace(trace)) {
            return false;
        }
        TraceMetadata metadata = trace.metadata();
        return "s3k".equals(metadata.game())
                && replaySeedTraceIndexForTraceReplay(trace) == 0;
    }

    public static boolean shouldSeedReplayStartStateForTraceReplay(TraceData trace,
                                                                   int requestedSeedTraceIndex) {
        return isLegacyS3kAizIntroTrace(trace)
                || shouldSeedFrameZeroForTraceReplay(trace)
                || requestedSeedTraceIndex > 0;
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

    private static ReplayStartState applyReplayStartState(TraceData trace,
                                                          TraceReplayFixture fixture,
                                                          boolean seedLegacyS3kAizAtGameplayStart) {
        return applyReplayStartState(
                trace,
                fixture,
                seedLegacyS3kAizAtGameplayStart,
                seedLegacyS3kAizAtGameplayStart
                        ? resolveLegacyS3kAizSeedTraceIndex(trace)
                        : 0);
    }

    private static ReplayStartState applyReplayStartState(TraceData trace,
                                                          TraceReplayFixture fixture,
                                                          boolean seedLegacyS3kAizAtGameplayStart,
                                                          int requestedSeedTraceIndex) {
        if (!shouldSeedReplayStartState(trace, fixture, requestedSeedTraceIndex)) {
            return ReplayStartState.DEFAULT;
        }

        int seededTraceIndex = Math.max(0, Math.min(requestedSeedTraceIndex, trace.frameCount() - 1));
        TraceFrame seededFrame = trace.getFrame(seededTraceIndex);
        AbstractPlayableSprite sprite = fixture.sprite();
        applyRecordedFrameState(sprite, seededFrame);
        // v5 traces record the first sidekick's end-of-frame state on
        // every row. For frame-0 seeded replays that start mid-carry
        // (e.g. S3K CNZ, which opens with Tails already positioned for
        // the AIZ→CNZ fly-in), the sidekick must be restored to that
        // recorded state too — the applyPreTraceSidekickCpuSnapshot
        // step only hydrates the Tails CPU-routine byte, and CNZ's
        // aux_state.jsonl has no slot-1 object_state_snapshot. Without
        // this, the engine keeps Tails at repositionSidekicks's
        // (player_x - 32, player_y + 4) spawn offset and his first
        // frame runs off the wrong position.
        applySeededFirstSidekickState(seededFrame);

        if (GameServices.camera() != null) {
            GameServices.camera().setX((short) seededFrame.cameraX());
            GameServices.camera().setY((short) seededFrame.cameraY());
        }

        // For S3K non-zero level-entry seeds, the headless level fixture is created
        // before the first live gameplay frame executes. Re-run the first zone-event
        // pass once so intro-only bootstrap objects exist before replay continues.
        // Frame-0 intro traces already record the spawned-but-unadvanced intro object,
        // so only spawn level events there and do not replay the object's first update.
        LevelEventProvider levelEvents = GameServices.module().getLevelEventProvider();
        if (levelEvents instanceof Sonic3kLevelEventManager
                && seededTraceIndex == replaySeedTraceIndexForTraceReplay(trace)) {
            levelEvents.update();
            if (seededTraceIndex > 0) {
                completeSeededS3kLevelEntryFrame(trace, seededTraceIndex, sprite);
            } else {
                ObjectManager objectManager = GameServices.level() != null
                        ? GameServices.level().getObjectManager()
                        : null;
                if (objectManager != null) {
                    // Frame-0 intro seeds represent the recorded end-of-frame state.
                    // Align the object VBlank counter with that frame without
                    // advancing Obj_intPlane beyond its recorded routine-0 state.
                    objectManager.advanceVblaCounter();
                }
                // Re-apply the recorded frame-0 state because the intro object's
                // constructor forces controlLocked/objectControlled/hidden = true
                // and zeroes speeds. The recorded pre-lock state captured a Sonic
                // still in free-fall at frame 0 (y_speed=0x440), so restore that
                // end-of-frame snapshot after the intro spawn is complete.
                applyRecordedFrameState(sprite, seededFrame);
            }
        }

        // Legacy S3K AIZ end-to-end traces arm and emit frame 0 in the same
        // on_frame_end pass. The recorded bk2_frame_offset already points at
        // the next replayable input after that directly restored state, so
        // consuming one movie frame here shifts the splice one frame ahead.

        if (seedLegacyS3kAizAtGameplayStart && seededTraceIndex > 0) {
            replayLegacyFramesToSeedIndex(trace, fixture, seededTraceIndex);

            applyRecordedFrameState(sprite, seededFrame);
            resetSeededOverlayState();
            if (GameServices.camera() != null) {
                GameServices.camera().setX((short) seededFrame.cameraX());
                GameServices.camera().setY((short) seededFrame.cameraY());
                GameServices.camera().setLevelStarted(true);
            }
            return new ReplayStartState(seededTraceIndex + 1, seededTraceIndex);
        }

        return new ReplayStartState(seededTraceIndex + 1, seededTraceIndex);
    }

    private static ReplayStartState warmupLegacyS3kAizTraceReplay(TraceData trace,
                                                                  TraceReplayFixture fixture) {
        if (trace == null || fixture == null || fixture.sprite() == null || trace.frameCount() == 0) {
            return ReplayStartState.DEFAULT;
        }

        int strictStartTraceIndex = strictStartTraceIndexForTraceReplay(trace);
        if (strictStartTraceIndex <= 0) {
            return ReplayStartState.DEFAULT;
        }
        // This legacy trace begins at BK2 power-on and includes title/data-select
        // time before AIZ is actually live. The headless fixture starts directly in
        // AIZ, so replaying trace frames 0..strictStartTraceIndex would advance the
        // intro object during non-level time and push the cutscene hundreds of
        // frames ahead. Start the replay loop at the first real in-level frame
        // instead and align the BK2/VBlank cursors to that same trace index.
        //
        // Prime the sprite and camera to the recorded strict-start frame state so
        // the first strict replay frame continues from the exact recorded values
        // (player position, speeds, air/rolling flags, subpixel, camera scroll)
        // rather than from the ROM's default AIZ1 intro bootstrap position. The
        // intro object and level-started flag remain in their pre-gameplay_start
        // state because the trace recorded them that way on this frame.
        TraceFrame strictStart = trace.getFrame(strictStartTraceIndex);
        AbstractPlayableSprite sprite = fixture.sprite();
        applyRecordedFrameState(sprite, strictStart);
        if (GameServices.camera() != null) {
            GameServices.camera().setX((short) strictStart.cameraX());
            GameServices.camera().setY((short) strictStart.cameraY());
        }
        return new ReplayStartState(strictStartTraceIndex, -1);
    }

    /**
     * Trace-replay seeds restore the recorded end-of-frame player/camera state directly.
     * For AIZ1 frame 403, the ROM also spawned Obj_intPlane during the level-event pass
     * and then executed its first object update in the same frame, advancing routine 0
     * to routine 2. Reproduce that missing object-half of the seeded frame here so the
     * replay continues from the recorded end-of-frame state instead of starting the intro
     * object one frame late.
     */
    private static void completeSeededS3kLevelEntryFrame(TraceData trace,
                                                         int seededTraceIndex,
                                                         AbstractPlayableSprite sprite) {
        if (trace == null || sprite == null || !"s3k".equals(trace.metadata().game())) {
            return;
        }
        ObjectManager objectManager = GameServices.level() != null
                ? GameServices.level().getObjectManager()
                : null;
        AizPlaneIntroInstance intro = AizPlaneIntroInstance.getActiveIntroInstance();
        if (objectManager == null || intro == null) {
            return;
        }

        objectManager.advanceVblaCounter();
        intro.update(trace.getFrame(seededTraceIndex).vblankCounter(), sprite);
    }

    private static void replayLegacyFramesToSeedIndex(TraceData trace,
                                                      TraceReplayFixture fixture,
                                                      int seededTraceIndex) {
        TraceFrame previous = trace.getFrame(0);
        for (int traceIndex = 1; traceIndex <= seededTraceIndex; traceIndex++) {
            TraceFrame current = trace.getFrame(traceIndex);
            TraceExecutionPhase phase = phaseForReplay(trace, previous, current);
            if (phase == TraceExecutionPhase.VBLANK_ONLY) {
                fixture.skipFrameFromRecording();
            } else {
                fixture.stepFrameFromRecording();
            }
            previous = current;
        }
    }

    public static TraceExecutionPhase phaseForReplay(TraceData trace,
                                                     TraceFrame previous,
                                                     TraceFrame current) {
        if (shouldUseLegacyS3kAizIntroHeuristic(trace, current)) {
            return deriveLegacyPhase(previous, current);
        }
        return TraceExecutionModel.forGame(trace.metadata().game()).phaseFor(previous, current);
    }

    private static void applyPreTraceSidekickSnapshot(TraceData trace,
                                                      List<TraceEvent.ObjectStateSnapshot> snapshots) {
        TraceEvent.ObjectStateSnapshot sidekickSnapshot = snapshots.stream()
                .filter(snapshot -> snapshot.slot() == 1)
                .findFirst()
                .orElse(null);
        if (sidekickSnapshot == null) {
            return;
        }

        SpriteManager spriteManager = GameServices.sprites();
        if (spriteManager == null || spriteManager.getSidekicks().isEmpty()) {
            return;
        }

        String sidekickCharacter = trace.metadata().recordedSidekicks().isEmpty()
                ? "tails"
                : trace.metadata().recordedSidekicks().getFirst();
        TraceEvent.CpuStateSnapshot cpuSnapshot = trace.preTraceCpuStateSnapshot(sidekickCharacter);
        hydrateSidekickFromSnapshot(
                spriteManager.getSidekicks().getFirst(),
                sidekickSnapshot.fields(),
                cpuSnapshot);
    }

    private static void applyPreTraceSidekickCpuSnapshot(TraceData trace) {
        if (trace == null || trace.metadata().recordedSidekicks().isEmpty()) {
            return;
        }
        SpriteManager spriteManager = GameServices.sprites();
        if (spriteManager == null || spriteManager.getSidekicks().isEmpty()) {
            return;
        }

        String sidekickCharacter = trace.metadata().recordedSidekicks().getFirst();
        TraceEvent.CpuStateSnapshot cpuSnapshot = trace.preTraceCpuStateSnapshot(sidekickCharacter);
        SidekickCpuController controller = spriteManager.getSidekicks().getFirst().getCpuController();
        if (cpuSnapshot != null && controller != null) {
            controller.hydrateFromRomCpuState(
                    cpuSnapshot.cpuRoutine(),
                    cpuSnapshot.controlCounter(),
                    cpuSnapshot.respawnCounter(),
                    cpuSnapshot.interactId(),
                    cpuSnapshot.jumping());
        }
    }

    private static boolean shouldSeedFrameZero(TraceData trace,
                                               TraceReplayFixture fixture) {
        if (trace == null || fixture == null || fixture.sprite() == null || trace.frameCount() == 0) {
            return false;
        }

        return shouldSeedReplayStartStateForTraceReplay(trace, 0);
    }

    private static boolean shouldSeedReplayStartState(TraceData trace,
                                                      TraceReplayFixture fixture,
                                                      int requestedSeedTraceIndex) {
        return shouldSeedFrameZero(trace, fixture) || requestedSeedTraceIndex > 0;
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

    private static int resolveLegacyS3kAizSeedTraceIndex(TraceData trace) {
        int gameplayStartFrame = findCheckpointFrame(trace, "gameplay_start");
        return gameplayStartFrame >= 0 ? gameplayStartFrame : 0;
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

    /**
     * Restore the first sidekick's end-of-frame state from the recorded
     * CSV row. Mirrors the v5 sidekick column block that
     * {@code AbstractTraceReplayTest.applyRecordedFirstSidekickState}
     * uses in headless comparison; exposed here so the live test-mode
     * bootstrap path seeds Tails identically.
     *
     * <p>No-op when the trace has no sidekick data, the frame's sidekick
     * block reports {@code present=false}, or the engine has no
     * registered sidekick.
     */
    private static void applySeededFirstSidekickState(TraceFrame seededFrame) {
        if (seededFrame == null) {
            return;
        }
        TraceCharacterState state = seededFrame.sidekick();
        if (state == null) {
            return;
        }
        SpriteManager spriteManager = GameServices.sprites();
        if (spriteManager == null || spriteManager.getSidekicks().isEmpty()) {
            return;
        }
        AbstractPlayableSprite sidekick = spriteManager.getSidekicks().getFirst();

        if (!state.present()) {
            sidekick.setHidden(true);
            sidekick.setDead(true);
            sidekick.setCentreX((short) 0);
            sidekick.setCentreY((short) 0);
            sidekick.setXSpeed((short) 0);
            sidekick.setYSpeed((short) 0);
            sidekick.setGSpeed((short) 0);
            sidekick.setSubpixelRaw(0, 0);
            sidekick.resetPositionHistory();
            return;
        }

        sidekick.setHidden(false);
        sidekick.setDead(false);
        sidekick.setDeathCountdown(0);
        sidekick.setControlLocked(false);
        sidekick.setObjectControlled(false);
        sidekick.setMoveLockTimer(0);
        sidekick.setHurt(state.routine() == 0x04);
        sidekick.setCentreX(state.x());
        sidekick.setCentreY(state.y());
        sidekick.setXSpeed(state.xSpeed());
        sidekick.setYSpeed(state.ySpeed());
        sidekick.setGSpeed(state.gSpeed());
        sidekick.setAngle(state.angle());
        sidekick.setDirection((state.statusByte() & 0x01) != 0
                ? Direction.LEFT
                : Direction.RIGHT);
        sidekick.setAir(state.air());
        sidekick.setRolling(state.rolling());
        sidekick.setOnObject((state.statusByte() & 0x08) != 0);
        sidekick.setRollingJump((state.statusByte() & 0x10) != 0);
        sidekick.setPushing((state.statusByte() & 0x20) != 0);
        sidekick.setGroundMode(groundMode(state.groundMode()));
        sidekick.setSubpixelRaw(state.xSub(), state.ySub());
        sidekick.resetPositionHistory();
    }

    private static void applyRecordedFrameState(AbstractPlayableSprite sprite,
                                                TraceFrame frame) {
        if (sprite == null || frame == null) {
            return;
        }

        sprite.setCentreX(frame.x());
        sprite.setCentreY(frame.y());
        sprite.setXSpeed(frame.xSpeed());
        sprite.setYSpeed(frame.ySpeed());
        sprite.setGSpeed(frame.gSpeed());
        sprite.setAngle(frame.angle());
        sprite.setDirection((frame.statusByte() & 0x01) != 0 ? Direction.LEFT : Direction.RIGHT);
        sprite.setAir(frame.air());
        sprite.setRolling(frame.rolling());
        sprite.setRollingJump((frame.statusByte() & AbstractPlayableSprite.STATUS_ROLLING_JUMP) != 0);
        sprite.setOnObject((frame.statusByte() & 0x08) != 0);
        sprite.setPushing((frame.statusByte() & 0x20) != 0);
        sprite.setInWater((frame.statusByte() & AbstractPlayableSprite.STATUS_UNDERWATER) != 0);
        sprite.setGroundMode(groundMode(frame.groundMode()));
        sprite.setSubpixelRaw(frame.xSub(), frame.ySub());
        sprite.clearForcedInputMask();
        sprite.clearQueuedControlState();
        sprite.setControlLocked(false);
        sprite.setObjectControlled(false);
        sprite.setMoveLockTimer(0);
        sprite.setDirectionalInputPressed(false, false, false, false);
        sprite.setJumpInputPressed(false);
        sprite.clearLogicalInputState();
        sprite.setMovementInputActive(false);
        sprite.getMovementManager().resetTransientState();
        sprite.resetPositionHistory();
    }

    private static void resetSeededOverlayState() {
        TitleCardProvider titleCardProvider = GameServices.module().getTitleCardProvider();
        if (titleCardProvider != null) {
            titleCardProvider.reset();
        }
    }

    private static GroundMode groundMode(int ordinal) {
        GroundMode[] values = GroundMode.values();
        if (ordinal < 0 || ordinal >= values.length) {
            return GroundMode.GROUND;
        }
        return values[ordinal];
    }
}
