package com.openggf.tests.trace.s1;

import com.openggf.game.GameServices;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.TouchResponseDebugHit;
import com.openggf.level.objects.TouchResponseDebugState;
import com.openggf.level.LevelManager;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tests.trace.TraceData;
import com.openggf.tests.trace.TraceExecutionModel;
import com.openggf.tests.trace.TraceExecutionPhase;
import com.openggf.tests.trace.TraceFrame;
import com.openggf.tests.trace.TraceMetadata;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

@RequiresRom(SonicGame.SONIC_1)
class TestS1Mz1LostRingCollectionOrderRegression {

    @Test
    void spilledRingDoesNotCollectBeforeTouchPhaseSeesItsPreviousPosition() throws Exception {
        Path traceDir = Path.of("src/test/resources/traces/s1/mz1_fullrun");
        Assumptions.assumeTrue(Files.isDirectory(traceDir), "Trace directory not found: " + traceDir);

        Path bk2Path;
        try (var files = Files.list(traceDir)) {
            bk2Path = files
                    .filter(path -> path.toString().endsWith(".bk2"))
                    .findFirst()
                    .orElse(null);
        }
        Assumptions.assumeTrue(bk2Path != null, "No .bk2 file found in " + traceDir);

        TraceData trace = TraceData.load(traceDir);
        TraceMetadata meta = trace.metadata();
        SharedLevel sharedLevel = SharedLevel.load(SonicGame.SONIC_1, 1, 0);
        try {
            HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                    .withSharedLevel(sharedLevel)
                    .startPosition(meta.startX(), meta.startY())
                    .startPositionIsCentre()
                    .withRecording(bk2Path)
                    .withRecordingStartFrame(meta.bk2FrameOffset())
                    .build();

            ObjectManager objectManager = GameServices.level().getObjectManager();
            if (objectManager != null) {
                objectManager.initVblaCounter(trace.initialVblankCounter() - 1);
            }

            int preTraceOsc = meta.preTraceOscillationFrames();
            for (int i = 0; i < preTraceOsc; i++) {
                com.openggf.game.OscillationManager.update(-(preTraceOsc - i));
            }

            for (int i = 0; i < trace.frameCount(); i++) {
                TraceFrame expected = trace.getFrame(i);
                TraceFrame previous = i > 0 ? trace.getFrame(i - 1) : null;
                TraceExecutionPhase phase =
                        TraceExecutionModel.forGame(meta.game()).phaseFor(previous, expected);
                if (phase == TraceExecutionPhase.VBLANK_ONLY) {
                    fixture.skipFrameFromRecording();
                } else {
                    fixture.stepFrameFromRecording();
                }

                if (expected.frame() == 758) {
                    assertEquals(6, expected.rings(), "Trace fixture assumption changed");
                    assertEquals(6, fixture.sprite().getRingCount(),
                            "Lost-ring collection should run before spill physics advances into overlap");
                    return;
                }
            }
        } finally {
            sharedLevel.dispose();
        }
    }

    @Test
    void spilledRingDoesNotCollectWhenFrame759TouchOrderHasNotReachedItsSlot() throws Exception {
        Path traceDir = Path.of("src/test/resources/traces/s1/mz1_fullrun");
        Assumptions.assumeTrue(Files.isDirectory(traceDir), "Trace directory not found: " + traceDir);

        Path bk2Path;
        try (var files = Files.list(traceDir)) {
            bk2Path = files
                    .filter(path -> path.toString().endsWith(".bk2"))
                    .findFirst()
                    .orElse(null);
        }
        Assumptions.assumeTrue(bk2Path != null, "No .bk2 file found in " + traceDir);

        TraceData trace = TraceData.load(traceDir);
        TraceMetadata meta = trace.metadata();
        SharedLevel sharedLevel = SharedLevel.load(SonicGame.SONIC_1, 1, 0);
        try {
            HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                    .withSharedLevel(sharedLevel)
                    .startPosition(meta.startX(), meta.startY())
                    .startPositionIsCentre()
                    .withRecording(bk2Path)
                    .withRecordingStartFrame(meta.bk2FrameOffset())
                    .build();

            ObjectManager objectManager = GameServices.level().getObjectManager();
            if (objectManager != null) {
                objectManager.initVblaCounter(trace.initialVblankCounter() - 1);
            }

            int preTraceOsc = meta.preTraceOscillationFrames();
            for (int i = 0; i < preTraceOsc; i++) {
                com.openggf.game.OscillationManager.update(-(preTraceOsc - i));
            }

            GameServices.debugOverlay().setEnabled(com.openggf.debug.DebugOverlayToggle.TOUCH_RESPONSE, true);

            for (int i = 0; i < trace.frameCount(); i++) {
                TraceFrame expected = trace.getFrame(i);
                TraceFrame previous = i > 0 ? trace.getFrame(i - 1) : null;
                TraceExecutionPhase phase =
                        TraceExecutionModel.forGame(meta.game()).phaseFor(previous, expected);
                if (phase == TraceExecutionPhase.VBLANK_ONLY) {
                    fixture.skipFrameFromRecording();
                } else {
                    fixture.stepFrameFromRecording();
                }

                if (expected.frame() == 759) {
                    assertEquals(6, expected.rings(), "Trace fixture assumption changed");
                    assertEquals(6, fixture.sprite().getRingCount(),
                            () -> "Lost-ring recollection should still be blocked at frame 759"
                                    + " player=" + formatPlayerState(fixture)
                                    + " touchHits=" + describeTouchHits(GameServices.level().getObjectManager()));
                    return;
                }
            }
        } finally {
            GameServices.debugOverlay().setEnabled(com.openggf.debug.DebugOverlayToggle.TOUCH_RESPONSE, false);
            sharedLevel.dispose();
        }
    }

    @Test
    void lostRingDoesNotCollectDuringTouchPhaseBeforePlatformSlopeCarryRuns() throws Exception {
        Path traceDir = Path.of("src/test/resources/traces/s1/mz1_fullrun");
        Assumptions.assumeTrue(Files.isDirectory(traceDir), "Trace directory not found: " + traceDir);

        Path bk2Path;
        try (var files = Files.list(traceDir)) {
            bk2Path = files
                    .filter(path -> path.toString().endsWith(".bk2"))
                    .findFirst()
                    .orElse(null);
        }
        Assumptions.assumeTrue(bk2Path != null, "No .bk2 file found in " + traceDir);

        TraceData trace = TraceData.load(traceDir);
        TraceMetadata meta = trace.metadata();
        SharedLevel sharedLevel = SharedLevel.load(SonicGame.SONIC_1, 1, 0);
        try {
            HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                    .withSharedLevel(sharedLevel)
                    .startPosition(meta.startX(), meta.startY())
                    .startPositionIsCentre()
                    .withRecording(bk2Path)
                    .withRecordingStartFrame(meta.bk2FrameOffset())
                    .build();

            ObjectManager objectManager = GameServices.level().getObjectManager();
            if (objectManager != null) {
                objectManager.initVblaCounter(trace.initialVblankCounter() - 1);
            }

            int preTraceOsc = meta.preTraceOscillationFrames();
            for (int i = 0; i < preTraceOsc; i++) {
                com.openggf.game.OscillationManager.update(-(preTraceOsc - i));
            }

            for (int i = 0; i < trace.frameCount(); i++) {
                TraceFrame expected = trace.getFrame(i);
                TraceFrame previous = i > 0 ? trace.getFrame(i - 1) : null;
                TraceExecutionPhase phase =
                        TraceExecutionModel.forGame(meta.game()).phaseFor(previous, expected);

                if (expected.frame() < 762) {
                    if (phase == TraceExecutionPhase.VBLANK_ONLY) {
                        fixture.skipFrameFromRecording();
                    } else {
                        fixture.stepFrameFromRecording();
                    }
                    continue;
                }

                assertEquals(TraceExecutionPhase.FULL_LEVEL_FRAME, phase,
                        "Frame 762 regression assumes a normal gameplay frame");
                assertEquals(6, fixture.sprite().getRingCount(),
                        "Fixture assumption changed before frame 762");

                int beforeX = fixture.sprite().getCentreX();
                int beforeY = fixture.sprite().getCentreY();
                boolean beforeOnObject = fixture.sprite().isOnObject();
                stepTouchPhaseOnly(fixture, expected.input());
                int afterX = fixture.sprite().getCentreX();
                int afterY = fixture.sprite().getCentreY();
                boolean afterOnObject = fixture.sprite().isOnObject();
                var ridingObject = GameServices.level().getObjectManager() != null
                        ? GameServices.level().getObjectManager().getRidingObject(fixture.sprite())
                        : null;
                String ridingSummary = ridingObject == null
                        ? "<none>"
                        : ridingObject.getClass().getSimpleName()
                                + String.format("@0x%04X,0x%04X", ridingObject.getX(), ridingObject.getY());

                assertEquals(6, fixture.sprite().getRingCount(),
                        "Touch phase should not recollect the spilled ring before the platform's current-frame slope carry"
                                + String.format(" before=(0x%04X,0x%04X onObj=%s) after=(0x%04X,0x%04X onObj=%s) riding=%s",
                                beforeX, beforeY, beforeOnObject, afterX, afterY, afterOnObject, ridingSummary));
                return;
            }
        } finally {
            sharedLevel.dispose();
        }
    }

    @Test
    void stageRingDoesNotCollectUntilReactToItemHeightMinusThreeActuallyOverlaps() throws Exception {
        Path traceDir = Path.of("src/test/resources/traces/s1/mz1_fullrun");
        Assumptions.assumeTrue(Files.isDirectory(traceDir), "Trace directory not found: " + traceDir);

        Path bk2Path;
        try (var files = Files.list(traceDir)) {
            bk2Path = files
                    .filter(path -> path.toString().endsWith(".bk2"))
                    .findFirst()
                    .orElse(null);
        }
        Assumptions.assumeTrue(bk2Path != null, "No .bk2 file found in " + traceDir);

        TraceData trace = TraceData.load(traceDir);
        TraceMetadata meta = trace.metadata();
        SharedLevel sharedLevel = SharedLevel.load(SonicGame.SONIC_1, 1, 0);
        try {
            HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                    .withSharedLevel(sharedLevel)
                    .startPosition(meta.startX(), meta.startY())
                    .startPositionIsCentre()
                    .withRecording(bk2Path)
                    .withRecordingStartFrame(meta.bk2FrameOffset())
                    .build();

            ObjectManager objectManager = GameServices.level().getObjectManager();
            if (objectManager != null) {
                objectManager.initVblaCounter(trace.initialVblankCounter() - 1);
            }

            int preTraceOsc = meta.preTraceOscillationFrames();
            for (int i = 0; i < preTraceOsc; i++) {
                com.openggf.game.OscillationManager.update(-(preTraceOsc - i));
            }

            for (int i = 0; i < trace.frameCount(); i++) {
                TraceFrame expected = trace.getFrame(i);
                TraceFrame previous = i > 0 ? trace.getFrame(i - 1) : null;
                TraceExecutionPhase phase =
                        TraceExecutionModel.forGame(meta.game()).phaseFor(previous, expected);
                if (phase == TraceExecutionPhase.VBLANK_ONLY) {
                    fixture.skipFrameFromRecording();
                } else {
                    fixture.stepFrameFromRecording();
                }

                if (expected.frame() == 2098) {
                    assertEquals(0, expected.rings(), "Trace fixture assumption changed");
                    assertEquals(0, fixture.sprite().getRingCount(),
                            () -> "Placed ring should not collect until the next frame's"
                                    + " ReactToItem height-minus-three overlap"
                                    + " player=" + formatPlayerState(fixture));
                    return;
                }
            }
        } finally {
            sharedLevel.dispose();
        }
    }

    @Test
    void stageTouchPassCollectsOnlyOneOverlappingRingPerFrameInSlotOrder() throws Exception {
        Path traceDir = Path.of("src/test/resources/traces/s1/mz1_fullrun");
        Assumptions.assumeTrue(Files.isDirectory(traceDir), "Trace directory not found: " + traceDir);

        Path bk2Path;
        try (var files = Files.list(traceDir)) {
            bk2Path = files
                    .filter(path -> path.toString().endsWith(".bk2"))
                    .findFirst()
                    .orElse(null);
        }
        Assumptions.assumeTrue(bk2Path != null, "No .bk2 file found in " + traceDir);

        TraceData trace = TraceData.load(traceDir);
        TraceMetadata meta = trace.metadata();
        SharedLevel sharedLevel = SharedLevel.load(SonicGame.SONIC_1, 1, 0);
        try {
            HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                    .withSharedLevel(sharedLevel)
                    .startPosition(meta.startX(), meta.startY())
                    .startPositionIsCentre()
                    .withRecording(bk2Path)
                    .withRecordingStartFrame(meta.bk2FrameOffset())
                    .build();

            ObjectManager objectManager = GameServices.level().getObjectManager();
            if (objectManager != null) {
                objectManager.initVblaCounter(trace.initialVblankCounter() - 1);
            }

            int preTraceOsc = meta.preTraceOscillationFrames();
            for (int i = 0; i < preTraceOsc; i++) {
                com.openggf.game.OscillationManager.update(-(preTraceOsc - i));
            }

            for (int i = 0; i < trace.frameCount(); i++) {
                TraceFrame expected = trace.getFrame(i);
                TraceFrame previous = i > 0 ? trace.getFrame(i - 1) : null;
                TraceExecutionPhase phase =
                        TraceExecutionModel.forGame(meta.game()).phaseFor(previous, expected);
                if (phase == TraceExecutionPhase.VBLANK_ONLY) {
                    fixture.skipFrameFromRecording();
                } else {
                    fixture.stepFrameFromRecording();
                }

                if (expected.frame() == 2808) {
                    assertEquals(8, expected.rings(), "Trace fixture assumption changed");
                    assertEquals(8, fixture.sprite().getRingCount(),
                            () -> "S1 ReactToItem should stop after the first overlapping ring"
                                    + " player=" + formatPlayerState(fixture));
                    return;
                }
            }
        } finally {
            sharedLevel.dispose();
        }
    }

    private static void stepTouchPhaseOnly(HeadlessTestFixture fixture, int inputMask) {
        boolean up = (inputMask & com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_UP) != 0;
        boolean down = (inputMask & com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_DOWN) != 0;
        boolean left = (inputMask & com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_LEFT) != 0;
        boolean right = (inputMask & com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_RIGHT) != 0;
        boolean jump = (inputMask & com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_JUMP) != 0;

        var sprite = fixture.sprite();
        LevelManager levelManager = GameServices.level();
        int nextFrame = fixture.frameCount() + 1;

        sprite.setJumpInputPressed(jump);
        sprite.setDirectionalInputPressed(up, down, left, right);

        levelManager.updateZoneFeaturesPrePhysics();
        levelManager.prepareTouchResponseSnapshots();

        com.openggf.sprites.managers.SpriteManager.tickPlayablePhysics(sprite,
                up, down, left, right, jump,
                false, false, false, levelManager, nextFrame);
    }

    private static String formatPlayerState(HeadlessTestFixture fixture) {
        var sprite = fixture.sprite();
        return String.format("@0x%04X,0x%04X rings=%d onObj=%s frame=%d",
                sprite.getCentreX(),
                sprite.getCentreY(),
                sprite.getRingCount(),
                sprite.isOnObject(),
                fixture.frameCount());
    }

    private static String describeTouchHits(ObjectManager objectManager) {
        if (objectManager == null) {
            return "<no object manager>";
        }
        TouchResponseDebugState state = objectManager.getTouchResponseDebugState();
        if (state == null || state.getHits().isEmpty()) {
            return "<none>";
        }
        return state.getHits().stream()
                .filter(TouchResponseDebugHit::overlapping)
                .map(hit -> String.format("slot=%d flags=0x%02X obj=0x%04X,0x%04X",
                        hit.slotIndex(),
                        hit.flags() & 0xFF,
                        hit.objectX() & 0xFFFF,
                        hit.objectY() & 0xFFFF))
                .collect(Collectors.joining(" | "));
    }
}
