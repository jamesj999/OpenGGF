package com.openggf.tests.trace;

import com.openggf.game.GameServices;
import com.openggf.game.LevelEventProvider;
import com.openggf.game.TitleCardProvider;
import com.openggf.game.GroundMode;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.RomObjectSnapshot;
import com.openggf.physics.Direction;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SidekickCpuController;
import com.openggf.tests.HeadlessTestFixture;

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

    private TraceReplayBootstrap() {
    }

    public static TraceObjectSnapshotBinder.Result applyPreTraceState(TraceData trace,
                                                                     HeadlessTestFixture fixture) {
        applyPreTracePlayerHistory(trace.preTracePlayerHistorySnapshot(), fixture.sprite());

        ObjectManager objectManager = GameServices.level().getObjectManager();
        List<TraceEvent.ObjectStateSnapshot> snapshots = trace.preTraceObjectSnapshots();
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
                                                         HeadlessTestFixture fixture) {
        return applyReplayStartState(trace, fixture, true);
    }

    public static ReplayStartState applyReplayStartStateForTraceReplay(TraceData trace,
                                                                       HeadlessTestFixture fixture) {
        return applyReplayStartState(trace, fixture, false);
    }

    private static ReplayStartState applyReplayStartState(TraceData trace,
                                                          HeadlessTestFixture fixture,
                                                          boolean seedLegacyS3kAizAtGameplayStart) {
        if (!shouldSeedFrameZero(trace, fixture)) {
            return ReplayStartState.DEFAULT;
        }

        int seededTraceIndex = seedLegacyS3kAizAtGameplayStart
                ? resolveLegacyS3kAizSeedTraceIndex(trace)
                : 0;
        TraceFrame frame0 = trace.getFrame(0);
        AbstractPlayableSprite sprite = fixture.sprite();
        applyRecordedFrameState(sprite, frame0);

        if (GameServices.camera() != null) {
            GameServices.camera().setX((short) frame0.cameraX());
            GameServices.camera().setY((short) frame0.cameraY());
        }

        // Schema v3 S3K end-to-end traces arm at frame-end, so metadata start_x/start_y
        // and trace frame 0 describe the world AFTER the first armed frame. Recreate
        // that state directly and run the frame-0 level-event pass once so AIZ intro
        // objects exist before replay continues with frame 1.
        LevelEventProvider levelEvents = GameServices.module().getLevelEventProvider();
        if (levelEvents instanceof Sonic3kLevelEventManager) {
            levelEvents.update();
        }

        // Legacy S3K AIZ end-to-end traces arm and emit frame 0 in the same
        // on_frame_end pass. The recorded bk2_frame_offset already points at
        // the next replayable input after that directly restored state, so
        // consuming one movie frame here shifts the splice one frame ahead.

        if (seededTraceIndex > 0) {
            replayLegacyFramesToSeedIndex(trace, fixture, seededTraceIndex);

            TraceFrame seededFrame = trace.getFrame(seededTraceIndex);
            applyRecordedFrameState(sprite, seededFrame);
            resetSeededOverlayState();
            if (GameServices.camera() != null) {
                GameServices.camera().setX((short) seededFrame.cameraX());
                GameServices.camera().setY((short) seededFrame.cameraY());
                GameServices.camera().setLevelStarted(true);
            }
            return new ReplayStartState(seededTraceIndex + 1, seededTraceIndex);
        }

        return new ReplayStartState(1, 0);
    }

    private static void replayLegacyFramesToSeedIndex(TraceData trace,
                                                      HeadlessTestFixture fixture,
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
        if (shouldTreatLegacyS3kAizIntroFrameAsFull(trace, current)) {
            return TraceExecutionPhase.FULL_LEVEL_FRAME;
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

    private static boolean shouldSeedFrameZero(TraceData trace,
                                               HeadlessTestFixture fixture) {
        if (trace == null || fixture == null || fixture.sprite() == null || trace.frameCount() == 0) {
            return false;
        }
        if (!trace.preTraceObjectSnapshots().isEmpty()) {
            return false;
        }

        return isLegacyS3kAizIntroTrace(trace);
    }

    private static boolean shouldTreatLegacyS3kAizIntroFrameAsFull(TraceData trace,
                                                                   TraceFrame current) {
        if (trace == null || current == null || !isLegacyS3kAizIntroTrace(trace)) {
            return false;
        }
        int gameplayStartFrame = findCheckpointFrame(trace, "gameplay_start");
        return gameplayStartFrame >= 0 && current.frame() <= gameplayStartFrame;
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
        sprite.setOnObject((frame.statusByte() & 0x08) != 0);
        sprite.setPushing((frame.statusByte() & 0x20) != 0);
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
