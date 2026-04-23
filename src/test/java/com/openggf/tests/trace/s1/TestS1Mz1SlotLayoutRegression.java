package com.openggf.tests.trace.s1;

import com.openggf.game.GameServices;
import com.openggf.game.sonic1.objects.Sonic1RingInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.rings.LostRing;
import com.openggf.level.rings.RingManager;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceEvent;
import com.openggf.trace.TraceExecutionModel;
import com.openggf.trace.TraceExecutionPhase;
import com.openggf.trace.TraceFrame;
import com.openggf.trace.TraceMetadata;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@RequiresRom(SonicGame.SONIC_1)
class TestS1Mz1SlotLayoutRegression {

    private static final Path TRACE_DIR = Path.of("src/test/resources/traces/s1/mz1_fullrun");
    private static final int TARGET_FRAME = Integer.getInteger("slot.frame", 3101);
    private static final int HURT_FRAME = Integer.getInteger("slot.hurtFrame", 517);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void lowSlotLayoutStillMatchesRecordedRomAtHurtFrame() throws Exception {
        Assumptions.assumeTrue(Files.isDirectory(TRACE_DIR), "Trace directory not found: " + TRACE_DIR);

        Path bk2Path;
        try (var files = Files.list(TRACE_DIR)) {
            bk2Path = files
                    .filter(path -> path.toString().endsWith(".bk2"))
                    .findFirst()
                    .orElse(null);
        }
        Assumptions.assumeTrue(bk2Path != null, "No .bk2 file found in " + TRACE_DIR);

        TraceData trace = TraceData.load(TRACE_DIR);
        TraceMetadata meta = trace.metadata();
        Map<Integer, String> expected = findExpectedSlotDump(trace, HURT_FRAME);

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

            for (int i = 0; i <= HURT_FRAME; i++) {
                TraceFrame expectedFrame = trace.getFrame(i);
                TraceFrame previous = i > 0 ? trace.getFrame(i - 1) : null;
                TraceExecutionPhase phase =
                        TraceExecutionModel.forGame(meta.game()).phaseFor(previous, expectedFrame);
                if (phase == TraceExecutionPhase.VBLANK_ONLY) {
                    fixture.skipFrameFromRecording();
                } else {
                    fixture.stepFrameFromRecording();
                }
            }

            Map<Integer, String> actual = collectLiveSlotMap(GameServices.level().getObjectManager());
            Map<Integer, String> expectedLowSlots = expected.entrySet().stream()
                    .filter(entry -> entry.getKey() >= 32 && entry.getKey() <= 58)
                    .collect(LinkedHashMap::new,
                            (map, entry) -> map.put(entry.getKey(), entry.getValue()),
                            LinkedHashMap::putAll);
            Map<Integer, String> actualLowSlots = actual.entrySet().stream()
                    .filter(entry -> entry.getKey() >= 32 && entry.getKey() <= 58)
                    .collect(LinkedHashMap::new,
                            (map, entry) -> map.put(entry.getKey(), entry.getValue()),
                            LinkedHashMap::putAll);
            Map<Integer, String> actualDetails = collectLiveSlotDetails(GameServices.level().getObjectManager());
            String duplicateSlots = describeDuplicateSlots(GameServices.level().getObjectManager());
            String ringCounts = describeRingCounts(expectedFrameFor(trace, HURT_FRAME), fixture);
            String placementRegion = describePlacementRegion(GameServices.level().getObjectManager(), 0x0280, 0x0500);
            String ringSpawnStates = describeSpawnStates(GameServices.level().getObjectManager(), 0x0280, 0x0500, 0x25);
            String ringMappingSizes = describeRingMappingSizes(GameServices.level().getObjectManager(), 0x0280, 0x0500);
            String liveRingInstanceStates = describeLiveRingInstanceStates(GameServices.level().getObjectManager(), 0x0280, 0x0500);
            String reservedChildSlots = describeReservedChildSlots(GameServices.level().getObjectManager(), 0x0280, 0x0500, 0x25);
            String childCollectedStates = describeChildCollectedStates(GameServices.level().getObjectManager(), 0x0280, 0x0500);
            String ringSparkleState = describeRingSparkleState(GameServices.level().getObjectManager(), 0x0200, 0x0500);
            String animalStates = describeAnimalStates(GameServices.level().getObjectManager());

            assertEquals(expectedLowSlots, actualLowSlots,
                    () -> "Live low-slot layout diverged at hurt frame " + HURT_FRAME
                            + " expected=" + expectedLowSlots + " actual=" + actualLowSlots
                            + " allActual=" + actual
                            + " actualDetails=" + actualDetails
                            + " duplicateSlots=" + duplicateSlots
                            + " ringCounts=" + ringCounts
                            + " placementRegion=" + placementRegion
                            + " ringSpawnStates=" + ringSpawnStates
                            + " ringMappingSizes=" + ringMappingSizes
                            + " liveRingInstanceStates=" + liveRingInstanceStates
                            + " reservedChildSlots=" + reservedChildSlots
                            + " childCollectedStates=" + childCollectedStates
                            + " ringSparkleState=" + ringSparkleState
                            + " animalStates=" + animalStates);
        } finally {
            sharedLevel.dispose();
        }
    }

    @Test
    void slotSuffixStillMatchesRecordedRomLayoutBeforeBatbrainRegion() throws Exception {
        Assumptions.assumeTrue(Files.isDirectory(TRACE_DIR), "Trace directory not found: " + TRACE_DIR);

        Path bk2Path;
        try (var files = Files.list(TRACE_DIR)) {
            bk2Path = files
                    .filter(path -> path.toString().endsWith(".bk2"))
                    .findFirst()
                    .orElse(null);
        }
        Assumptions.assumeTrue(bk2Path != null, "No .bk2 file found in " + TRACE_DIR);

        TraceData trace = TraceData.load(TRACE_DIR);
        TraceMetadata meta = trace.metadata();
        Map<Integer, String> expected = findExpectedSlotDump(trace, TARGET_FRAME);

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

            for (int i = 0; i <= TARGET_FRAME; i++) {
                TraceFrame expectedFrame = trace.getFrame(i);
                TraceFrame previous = i > 0 ? trace.getFrame(i - 1) : null;
                TraceExecutionPhase phase =
                        TraceExecutionModel.forGame(meta.game()).phaseFor(previous, expectedFrame);
                if (phase == TraceExecutionPhase.VBLANK_ONLY) {
                    fixture.skipFrameFromRecording();
                } else {
                    fixture.stepFrameFromRecording();
                }
            }

            Map<Integer, String> actual = collectLiveSlotMap(GameServices.level().getObjectManager());
            Map<Integer, String> expectedSuffix = expected.entrySet().stream()
                    .filter(entry -> entry.getKey() >= 65)
                    .collect(LinkedHashMap::new,
                            (map, entry) -> map.put(entry.getKey(), entry.getValue()),
                            LinkedHashMap::putAll);
            Map<Integer, String> actualSuffix = actual.entrySet().stream()
                    .filter(entry -> entry.getKey() >= 65)
                    .collect(LinkedHashMap::new,
                            (map, entry) -> map.put(entry.getKey(), entry.getValue()),
                            LinkedHashMap::putAll);
            Map<Integer, String> actualDetails = collectLiveSlotDetails(GameServices.level().getObjectManager());
            ObjectManager liveManager = GameServices.level().getObjectManager();
            String placementRegion = describePlacementRegion(liveManager);
            String buttonState = describeSpawnState(liveManager, 0x0AD0, 0x32);
            String lavaState = describeSpawnState(liveManager, 0x0B00, 0x54);
            String buttonInstance = describeLiveInstance(liveManager, 0x0AD0, 0x32);
            String lavaInstance = describeLiveInstance(liveManager, 0x0B00, 0x54);
            String buttonMapState = describePrivateMapState(liveManager, 0x0AD0, 0x32);
            String lavaMapState = describePrivateMapState(liveManager, 0x0B00, 0x54);
            int allocatedSlots = liveManager != null ? liveManager.getAllocatedSlotCount() : -1;

            assertEquals(expectedSuffix, actualSuffix,
                    () -> "Live slot suffix diverged at frame " + TARGET_FRAME
                            + " expected=" + expectedSuffix + " actual=" + actualSuffix
                            + " allActual=" + actual
                            + " actualDetails=" + actualDetails
                            + " placementRegion=" + placementRegion
                            + " buttonState=" + buttonState
                            + " lavaState=" + lavaState
                            + " buttonInstance=" + buttonInstance
                            + " lavaInstance=" + lavaInstance
                            + " buttonMapState=" + buttonMapState
                            + " lavaMapState=" + lavaMapState
                            + " allocatedSlots=" + allocatedSlots);
        } finally {
            sharedLevel.dispose();
        }
    }

    private static Map<Integer, String> findExpectedSlotDump(TraceData trace, int frame) {
        return trace.getEventsForFrame(frame).stream()
                .filter(TraceEvent.StateSnapshot.class::isInstance)
                .map(TraceEvent.StateSnapshot.class::cast)
                .filter(snapshot -> "slot_dump".equals(snapshot.fields().get("event")))
                .findFirst()
                .map(TestS1Mz1SlotLayoutRegression::parseSlotMap)
                .orElseThrow(() -> new AssertionError("Expected ROM slot dump missing at frame " + frame));
    }

    private static TraceFrame expectedFrameFor(TraceData trace, int frame) {
        return trace.getFrame(frame);
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, String> parseSlotMap(TraceEvent.StateSnapshot dump) {
        Map<Integer, String> slots = new LinkedHashMap<>();
        Object rawSlots = dump.fields().get("slots");
        if (rawSlots == null) {
            return slots;
        }
        try {
            List<List<Object>> parsed = MAPPER.readValue(rawSlots.toString(), List.class);
            for (List<Object> entry : parsed) {
                if (entry.size() >= 2) {
                    slots.put(((Number) entry.get(0)).intValue(), entry.get(1).toString());
                }
            }
        } catch (Exception e) {
            throw new AssertionError("Failed to parse slot dump payload: " + rawSlots, e);
        }
        return slots;
    }

    private static Map<Integer, String> collectLiveSlotMap(ObjectManager objectManager) {
        Map<Integer, String> slots = new TreeMap<>();
        if (objectManager == null) {
            return slots;
        }
        for (ObjectInstance instance : objectManager.getActiveObjects()) {
            if (instance.isDestroyed() || !(instance instanceof AbstractObjectInstance aoi)) {
                continue;
            }
            int slot = aoi.getSlotIndex();
            if (slot < 0) {
                continue;
            }
            int objectId = instance.getSpawn() != null ? instance.getSpawn().objectId() & 0xFF : 0;
            slots.put(slot, String.format("0x%02X", objectId));
        }
        appendLostRingSlots(slots);
        return slots;
    }

    @Test
    void badnikAnimalStillTransitionsToRecordedRoutineAtLandingFrame() throws Exception {
        Assumptions.assumeTrue(Files.isDirectory(TRACE_DIR), "Trace directory not found: " + TRACE_DIR);

        Path bk2Path;
        try (var files = Files.list(TRACE_DIR)) {
            bk2Path = files
                    .filter(path -> path.toString().endsWith(".bk2"))
                    .findFirst()
                    .orElse(null);
        }
        Assumptions.assumeTrue(bk2Path != null, "No .bk2 file found in " + TRACE_DIR);

        TraceData trace = TraceData.load(TRACE_DIR);
        TraceMetadata meta = trace.metadata();
        int landingFrame = 481;

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

            for (int i = 0; i <= landingFrame; i++) {
                TraceFrame expectedFrame = trace.getFrame(i);
                TraceFrame previous = i > 0 ? trace.getFrame(i - 1) : null;
                TraceExecutionPhase phase =
                        TraceExecutionModel.forGame(meta.game()).phaseFor(previous, expectedFrame);
                if (phase == TraceExecutionPhase.VBLANK_ONLY) {
                    fixture.skipFrameFromRecording();
                } else {
                    fixture.stepFrameFromRecording();
                }
            }

            ObjectInstance animal = findLiveObjectAtSlot(GameServices.level().getObjectManager(), 51);
            assertNotNull(animal,
                    () -> "Expected S1 animal in slot 51 at frame " + landingFrame
                            + " details=" + collectLiveSlotDetails(GameServices.level().getObjectManager()));
            assertEquals("Sonic1AnimalsObjectInstance", animal.getClass().getSimpleName());
            assertEquals(0x0360, animal.getX() & 0xFFFF);
            assertEquals(0x0293, animal.getY() & 0xFFFF);
            assertEquals(0x0A, reflectAnimalRoutine(animal),
                    () -> "Animal state diverged at frame " + landingFrame + ": "
                            + reflectAnimalState(animal));
        } finally {
            sharedLevel.dispose();
        }
    }

    @Test
    void lavaBallMakerSpawnsSlot33BallAtFrame1204() throws Exception {
        Assumptions.assumeTrue(Files.isDirectory(TRACE_DIR), "Trace directory not found: " + TRACE_DIR);

        Path bk2Path;
        try (var files = Files.list(TRACE_DIR)) {
            bk2Path = files
                    .filter(path -> path.toString().endsWith(".bk2"))
                    .findFirst()
                    .orElse(null);
        }
        Assumptions.assumeTrue(bk2Path != null, "No .bk2 file found in " + TRACE_DIR);

        TraceData trace = TraceData.load(TRACE_DIR);
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

            for (int i = 0; i <= 1204; i++) {
                TraceFrame expectedFrame = trace.getFrame(i);
                TraceFrame previous = i > 0 ? trace.getFrame(i - 1) : null;
                TraceExecutionPhase phase =
                        TraceExecutionModel.forGame(meta.game()).phaseFor(previous, expectedFrame);
                if (phase == TraceExecutionPhase.VBLANK_ONLY) {
                    fixture.skipFrameFromRecording();
                } else {
                    fixture.stepFrameFromRecording();
                }
            }

            ObjectManager liveManager = GameServices.level().getObjectManager();
            ObjectInstance liveSlot33 = findLiveObjectAtSlot(liveManager, 33);
            assertNotNull(liveSlot33,
                    () -> "Expected frame-1204 lava ball in slot 33"
                            + " actualSlots=" + collectLiveSlotMap(liveManager)
                            + " actualDetails=" + collectLiveSlotDetails(liveManager)
                            + " makers=" + describeLavaBallMakerStates(liveManager));
            assertEquals(0x14, liveSlot33.getSpawn().objectId() & 0xFF,
                    () -> "Expected lava ball id at slot 33 on frame 1204"
                            + " actualSlots=" + collectLiveSlotMap(liveManager)
                            + " actualDetails=" + collectLiveSlotDetails(liveManager)
                            + " makers=" + describeLavaBallMakerStates(liveManager));
        } finally {
            sharedLevel.dispose();
        }
    }

    @Test
    void ringPairNear0798AppearsAtRecordedFrames() throws Exception {
        Assumptions.assumeTrue(Files.isDirectory(TRACE_DIR), "Trace directory not found: " + TRACE_DIR);

        Path bk2Path;
        try (var files = Files.list(TRACE_DIR)) {
            bk2Path = files
                    .filter(path -> path.toString().endsWith(".bk2"))
                    .findFirst()
                    .orElse(null);
        }
        Assumptions.assumeTrue(bk2Path != null, "No .bk2 file found in " + TRACE_DIR);

        TraceData trace = TraceData.load(TRACE_DIR);
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

            int first0798 = -1;
            int first07b0 = -1;
            int first07b0Slot = -1;
            String liveSlotsAtFirst07b0 = null;
            for (int i = 0; i <= 1210; i++) {
                TraceFrame expectedFrame = trace.getFrame(i);
                TraceFrame previous = i > 0 ? trace.getFrame(i - 1) : null;
                TraceExecutionPhase phase =
                        TraceExecutionModel.forGame(meta.game()).phaseFor(previous, expectedFrame);
                if (phase == TraceExecutionPhase.VBLANK_ONLY) {
                    fixture.skipFrameFromRecording();
                } else {
                    fixture.stepFrameFromRecording();
                }

                if (first0798 < 0 && hasLiveObjectAt(GameServices.level().getObjectManager(), 0x25, 0x0798, 0x0150)) {
                    first0798 = i;
                }
                ObjectInstance ring07b0 = findLiveObjectAt(GameServices.level().getObjectManager(), 0x25, 0x07B0, 0x0150);
                if (first07b0 < 0 && ring07b0 != null) {
                    first07b0 = i;
                    first07b0Slot = ((AbstractObjectInstance) ring07b0).getSlotIndex();
                    liveSlotsAtFirst07b0 = String.valueOf(collectLiveSlotDetails(GameServices.level().getObjectManager()));
                }
            }

            ObjectManager liveManager = GameServices.level().getObjectManager();
            String liveSlots = String.valueOf(collectLiveSlotDetails(liveManager));
            String reservedRingSlots = describeReservedChildSlots(liveManager, 0x0700, 0x0900, 0x25);
            String ringMappings = describeRingMappingSizes(liveManager, 0x0700, 0x0900);
            assertEquals(1149, first0798,
                    "Ring at x=0x0798 first appeared on frame " + first0798
                            + " liveSlots=" + liveSlots
                            + " reservedRingSlots=" + reservedRingSlots
                            + " ringMappings=" + ringMappings);
            assertEquals(1150, first07b0,
                    "Ring at x=0x07B0 first appeared on frame " + first07b0
                            + " slot=" + first07b0Slot
                            + " liveSlots=" + liveSlots
                            + " liveSlotsAtFirst07b0=" + liveSlotsAtFirst07b0
                            + " reservedRingSlots=" + reservedRingSlots
                            + " ringMappings=" + ringMappings);
            assertEquals(35, first07b0Slot,
                    "Ring at x=0x07B0 first used slot " + first07b0Slot
                            + " liveSlots=" + liveSlots
                            + " liveSlotsAtFirst07b0=" + liveSlotsAtFirst07b0
                            + " reservedRingSlots=" + reservedRingSlots
                            + " ringMappings=" + ringMappings);
        } finally {
            sharedLevel.dispose();
        }
    }

    @Test
    void usedSlotBitmapMatchesLiveOccupancyWhen07b0RingAppears() throws Exception {
        Assumptions.assumeTrue(Files.isDirectory(TRACE_DIR), "Trace directory not found: " + TRACE_DIR);

        Path bk2Path;
        try (var files = Files.list(TRACE_DIR)) {
            bk2Path = files
                    .filter(path -> path.toString().endsWith(".bk2"))
                    .findFirst()
                    .orElse(null);
        }
        Assumptions.assumeTrue(bk2Path != null, "No .bk2 file found in " + TRACE_DIR);

        TraceData trace = TraceData.load(TRACE_DIR);
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

            int frameWith07b0Ring = -1;
            for (int i = 0; i <= 1210; i++) {
                TraceFrame expectedFrame = trace.getFrame(i);
                TraceFrame previous = i > 0 ? trace.getFrame(i - 1) : null;
                TraceExecutionPhase phase =
                        TraceExecutionModel.forGame(meta.game()).phaseFor(previous, expectedFrame);
                if (phase == TraceExecutionPhase.VBLANK_ONLY) {
                    fixture.skipFrameFromRecording();
                } else {
                    fixture.stepFrameFromRecording();
                }

                if (hasLiveObjectAt(GameServices.level().getObjectManager(), 0x25, 0x07B0, 0x0150)) {
                    frameWith07b0Ring = i;
                    break;
                }
            }

            ObjectManager liveManager = GameServices.level().getObjectManager();
            assertEquals(1150, frameWith07b0Ring,
                    "Expected x=0x07B0 ring to appear at frame 1150"
                            + " liveSlots=" + collectLiveSlotDetails(liveManager));

            String occupancy = describeUsedSlotOccupancyMismatch(liveManager);
            assertEquals("<none>", occupancy,
                    "usedSlots diverged from live slot occupancy at frame " + frameWith07b0Ring
                            + " mismatch=" + occupancy
                            + " liveSlots=" + collectLiveSlotDetails(liveManager)
                            + " allStates=" + describeAllObjectStates(liveManager));
        } finally {
            sharedLevel.dispose();
        }
    }

    @Test
    void buttonAt0ad0FirstAppearsAtRecordedFrame() throws Exception {
        Assumptions.assumeTrue(Files.isDirectory(TRACE_DIR), "Trace directory not found: " + TRACE_DIR);

        Path bk2Path;
        try (var files = Files.list(TRACE_DIR)) {
            bk2Path = files
                    .filter(path -> path.toString().endsWith(".bk2"))
                    .findFirst()
                    .orElse(null);
        }
        Assumptions.assumeTrue(bk2Path != null, "No .bk2 file found in " + TRACE_DIR);

        TraceData trace = TraceData.load(TRACE_DIR);
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

            int firstButtonFrame = -1;
            int firstButtonSlot = -1;
            for (int i = 0; i <= TARGET_FRAME; i++) {
                TraceFrame expectedFrame = trace.getFrame(i);
                TraceFrame previous = i > 0 ? trace.getFrame(i - 1) : null;
                TraceExecutionPhase phase =
                        TraceExecutionModel.forGame(meta.game()).phaseFor(previous, expectedFrame);
                if (phase == TraceExecutionPhase.VBLANK_ONLY) {
                    fixture.skipFrameFromRecording();
                } else {
                    fixture.stepFrameFromRecording();
                }

                ObjectInstance button = findLiveObjectAt(GameServices.level().getObjectManager(), 0x32, 0x0AD0, 0x03FB);
                if (firstButtonFrame < 0 && button != null) {
                    firstButtonFrame = i;
                    firstButtonSlot = ((AbstractObjectInstance) button).getSlotIndex();
                    break;
                }
            }

            ObjectManager liveManager = GameServices.level().getObjectManager();
            int observedFirstButtonFrame = firstButtonFrame;
            int observedFirstButtonSlot = firstButtonSlot;
            assertEquals(1464, firstButtonFrame,
                    () -> "Button at x=0x0AD0 first appeared on frame " + observedFirstButtonFrame
                            + " slot=" + observedFirstButtonSlot
                            + " liveSlots=" + collectLiveSlotDetails(liveManager)
                            + " placementRegion=" + describePlacementRegion(liveManager));
            assertEquals(43, firstButtonSlot,
                    () -> "Button at x=0x0AD0 first used slot " + observedFirstButtonSlot
                            + " firstFrame=" + observedFirstButtonFrame
                            + " liveSlots=" + collectLiveSlotDetails(liveManager)
                            + " placementRegion=" + describePlacementRegion(liveManager));
        } finally {
            sharedLevel.dispose();
        }
    }

    @Test
    void buttonAt0ad0UnloadsAtRecordedRemovalFrame() throws Exception {
        Assumptions.assumeTrue(Files.isDirectory(TRACE_DIR), "Trace directory not found: " + TRACE_DIR);

        Path bk2Path;
        try (var files = Files.list(TRACE_DIR)) {
            bk2Path = files
                    .filter(path -> path.toString().endsWith(".bk2"))
                    .findFirst()
                    .orElse(null);
        }
        Assumptions.assumeTrue(bk2Path != null, "No .bk2 file found in " + TRACE_DIR);

        TraceData trace = TraceData.load(TRACE_DIR);
        TraceMetadata meta = trace.metadata();
        int removalFrame = 1774;

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

            for (int i = 0; i <= removalFrame; i++) {
                TraceFrame expectedFrame = trace.getFrame(i);
                TraceFrame previous = i > 0 ? trace.getFrame(i - 1) : null;
                TraceExecutionPhase phase =
                        TraceExecutionModel.forGame(meta.game()).phaseFor(previous, expectedFrame);
                if (phase == TraceExecutionPhase.VBLANK_ONLY) {
                    fixture.skipFrameFromRecording();
                } else {
                    fixture.stepFrameFromRecording();
                }
            }

            ObjectManager liveManager = GameServices.level().getObjectManager();
            ObjectInstance button = findLiveObjectAt(liveManager, 0x32, 0x0AD0, 0x03FB);
            assertEquals(null, button,
                    () -> "Button at x=0x0AD0 should be unloaded by frame " + removalFrame
                            + " liveSlots=" + collectLiveSlotDetails(liveManager)
                            + " placementRegion=" + describePlacementRegion(liveManager)
                            + " buttonState=" + describeSpawnState(liveManager, 0x0AD0, 0x32)
                            + " buttonInstance=" + describeLiveInstance(liveManager, 0x0AD0, 0x32)
                            + " buttonMapState=" + describePrivateMapState(liveManager, 0x0AD0, 0x32));
        } finally {
            sharedLevel.dispose();
        }
    }

    @Test
    void buttonAt0ad0ReappearsAtRecordedFrameAndSlot() throws Exception {
        Assumptions.assumeTrue(Files.isDirectory(TRACE_DIR), "Trace directory not found: " + TRACE_DIR);

        Path bk2Path;
        try (var files = Files.list(TRACE_DIR)) {
            bk2Path = files
                    .filter(path -> path.toString().endsWith(".bk2"))
                    .findFirst()
                    .orElse(null);
        }
        Assumptions.assumeTrue(bk2Path != null, "No .bk2 file found in " + TRACE_DIR);

        TraceData trace = TraceData.load(TRACE_DIR);
        TraceMetadata meta = trace.metadata();
        int respawnFrame = 3101;

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

            int firstRespawnFrame = -1;
            int firstRespawnSlot = -1;
            String liveSlotsAtRespawn = null;
            String buttonStateAt3100 = null;
            String buttonInstanceAt3100 = null;
            String liveSlotsAt3100 = null;
            String cursorStateAt3100 = null;
            String cursorStateAt3101 = null;
            for (int i = 0; i <= respawnFrame; i++) {
                TraceFrame expectedFrame = trace.getFrame(i);
                TraceFrame previous = i > 0 ? trace.getFrame(i - 1) : null;
                TraceExecutionPhase phase =
                        TraceExecutionModel.forGame(meta.game()).phaseFor(previous, expectedFrame);
                if (phase == TraceExecutionPhase.VBLANK_ONLY) {
                    fixture.skipFrameFromRecording();
                } else {
                    fixture.stepFrameFromRecording();
                }

                if (i <= 1774) {
                    continue;
                }

                if (i == 3100) {
                    ObjectManager manager3100 = GameServices.level().getObjectManager();
                    buttonStateAt3100 = describeSpawnState(manager3100, 0x0AD0, 0x32);
                    buttonInstanceAt3100 = describeLiveInstance(manager3100, 0x0AD0, 0x32);
                    liveSlotsAt3100 = String.valueOf(collectLiveSlotDetails(manager3100));
                    cursorStateAt3100 = describeCursorState(manager3100);
                }
                if (i == 3101) {
                    ObjectManager manager3101 = GameServices.level().getObjectManager();
                    cursorStateAt3101 = describeCursorState(manager3101);
                }

                ObjectInstance liveButton = findLiveObjectAt(GameServices.level().getObjectManager(),
                        0x32, 0x0AD0, 0x03FB);
                if (firstRespawnFrame < 0 && liveButton != null) {
                    firstRespawnFrame = i;
                    firstRespawnSlot = ((AbstractObjectInstance) liveButton).getSlotIndex();
                    liveSlotsAtRespawn = String.valueOf(
                            collectLiveSlotDetails(GameServices.level().getObjectManager()));
                }
            }

            ObjectManager liveManager = GameServices.level().getObjectManager();
            ObjectInstance button = findLiveObjectAt(liveManager, 0x32, 0x0AD0, 0x03FB);
            assertNotNull(button,
                    () -> "Expected button at x=0x0AD0 to be alive at frame " + respawnFrame
                            + " liveSlots=" + collectLiveSlotDetails(liveManager)
                            + " placementRegion=" + describePlacementRegion(liveManager)
                            + " buttonState=" + describeSpawnState(liveManager, 0x0AD0, 0x32)
                            + " buttonInstance=" + describeLiveInstance(liveManager, 0x0AD0, 0x32)
                            + " buttonMapState=" + describePrivateMapState(liveManager, 0x0AD0, 0x32));
            int observedFirstRespawnFrame = firstRespawnFrame;
            int observedFirstRespawnSlot = firstRespawnSlot;
            String observedLiveSlotsAtRespawn = liveSlotsAtRespawn;
            String observedButtonStateAt3100 = buttonStateAt3100;
            String observedButtonInstanceAt3100 = buttonInstanceAt3100;
            String observedLiveSlotsAt3100 = liveSlotsAt3100;
            String observedCursorStateAt3100 = cursorStateAt3100;
            String observedCursorStateAt3101 = cursorStateAt3101;
            assertEquals(respawnFrame, firstRespawnFrame,
                    () -> "Button at x=0x0AD0 first reappeared on frame " + observedFirstRespawnFrame
                            + " slot=" + observedFirstRespawnSlot
                            + " liveSlotsAtRespawn=" + observedLiveSlotsAtRespawn
                            + " liveSlotsAt3100=" + observedLiveSlotsAt3100
                            + " buttonStateAt3100=" + observedButtonStateAt3100
                            + " buttonInstanceAt3100=" + observedButtonInstanceAt3100
                            + " cursorStateAt3100=" + observedCursorStateAt3100
                            + " cursorStateAt3101=" + observedCursorStateAt3101
                            + " placementRegion=" + describePlacementRegion(liveManager)
                            + " buttonState=" + describeSpawnState(liveManager, 0x0AD0, 0x32)
                            + " buttonInstance=" + describeLiveInstance(liveManager, 0x0AD0, 0x32)
                            + " buttonMapState=" + describePrivateMapState(liveManager, 0x0AD0, 0x32));
            assertEquals(65, ((AbstractObjectInstance) button).getSlotIndex(),
                    () -> "Button at x=0x0AD0 used wrong slot at frame " + respawnFrame
                            + " liveSlots=" + collectLiveSlotDetails(liveManager)
                            + " liveSlotsAt3100=" + observedLiveSlotsAt3100
                            + " buttonStateAt3100=" + observedButtonStateAt3100
                            + " buttonInstanceAt3100=" + observedButtonInstanceAt3100
                            + " cursorStateAt3100=" + observedCursorStateAt3100
                            + " cursorStateAt3101=" + observedCursorStateAt3101
                            + " placementRegion=" + describePlacementRegion(liveManager)
                            + " buttonState=" + describeSpawnState(liveManager, 0x0AD0, 0x32)
                            + " buttonInstance=" + describeLiveInstance(liveManager, 0x0AD0, 0x32)
                            + " buttonMapState=" + describePrivateMapState(liveManager, 0x0AD0, 0x32));
        } finally {
            sharedLevel.dispose();
        }
    }

    @Test
    void chainedStomperAt10c0UnloadsByRecordedFrame() throws Exception {
        Assumptions.assumeTrue(Files.isDirectory(TRACE_DIR), "Trace directory not found: " + TRACE_DIR);

        Path bk2Path;
        try (var files = Files.list(TRACE_DIR)) {
            bk2Path = files
                    .filter(path -> path.toString().endsWith(".bk2"))
                    .findFirst()
                    .orElse(null);
        }
        Assumptions.assumeTrue(bk2Path != null, "No .bk2 file found in " + TRACE_DIR);

        TraceData trace = TraceData.load(TRACE_DIR);
        TraceMetadata meta = trace.metadata();
        int removalFrame = 2493;

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

            for (int i = 0; i <= removalFrame; i++) {
                TraceFrame expectedFrame = trace.getFrame(i);
                TraceFrame previous = i > 0 ? trace.getFrame(i - 1) : null;
                TraceExecutionPhase phase =
                        TraceExecutionModel.forGame(meta.game()).phaseFor(previous, expectedFrame);
                if (phase == TraceExecutionPhase.VBLANK_ONLY) {
                    fixture.skipFrameFromRecording();
                } else {
                    fixture.stepFrameFromRecording();
                }
            }

            ObjectManager liveManager = GameServices.level().getObjectManager();
            ObjectInstance stomper = findLiveObjectAt(liveManager, 0x31, 0x10C0, 0x033C);
            assertEquals(null, stomper,
                    () -> "Chained stomper at x=0x10C0 should be unloaded by frame " + removalFrame
                            + " liveSlots=" + collectLiveSlotDetails(liveManager)
                            + " placementRegion=" + describePlacementRegion(liveManager, 0x0D00, 0x1140)
                            + " stomperState=" + describeSpawnState(liveManager, 0x10C0, 0x31)
                            + " stomperInstance=" + describeLiveInstance(liveManager, 0x10C0, 0x31)
                            + " stomperMapState=" + describePrivateMapState(liveManager, 0x10C0, 0x31));
        } finally {
            sharedLevel.dispose();
        }
    }

    @Test
    void lavaTagAt0d80AppearsAtRecordedFrameAndSlot() throws Exception {
        Assumptions.assumeTrue(Files.isDirectory(TRACE_DIR), "Trace directory not found: " + TRACE_DIR);

        Path bk2Path;
        try (var files = Files.list(TRACE_DIR)) {
            bk2Path = files
                    .filter(path -> path.toString().endsWith(".bk2"))
                    .findFirst()
                    .orElse(null);
        }
        Assumptions.assumeTrue(bk2Path != null, "No .bk2 file found in " + TRACE_DIR);

        TraceData trace = TraceData.load(TRACE_DIR);
        TraceMetadata meta = trace.metadata();
        int respawnFrame = 2538;

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

            for (int i = 0; i <= respawnFrame; i++) {
                TraceFrame expectedFrame = trace.getFrame(i);
                TraceFrame previous = i > 0 ? trace.getFrame(i - 1) : null;
                TraceExecutionPhase phase =
                        TraceExecutionModel.forGame(meta.game()).phaseFor(previous, expectedFrame);
                if (phase == TraceExecutionPhase.VBLANK_ONLY) {
                    fixture.skipFrameFromRecording();
                } else {
                    fixture.stepFrameFromRecording();
                }
            }

            ObjectManager liveManager = GameServices.level().getObjectManager();
            ObjectInstance lavaTag = findLiveObjectAt(liveManager, 0x54, 0x0D80, 0x05E8);
            assertNotNull(lavaTag,
                    () -> "Expected lava tag at x=0x0D80 to be alive at frame " + respawnFrame
                            + " liveSlots=" + collectLiveSlotDetails(liveManager)
                            + " placementRegion=" + describePlacementRegion(liveManager, 0x0C80, 0x0E20)
                            + " cursorState=" + describeCursorState(liveManager)
                            + " lavaState=" + describeSpawnState(liveManager, 0x0D80, 0x54)
                            + " lavaInstance=" + describeLiveInstance(liveManager, 0x0D80, 0x54)
                            + " slot44=" + describeSlotOccupant(liveManager, 44)
                            + " slot49=" + describeSlotOccupant(liveManager, 49));
            assertEquals(44, ((AbstractObjectInstance) lavaTag).getSlotIndex(),
                    () -> "Lava tag at x=0x0D80 used wrong slot at frame " + respawnFrame
                            + " liveSlots=" + collectLiveSlotDetails(liveManager)
                            + " placementRegion=" + describePlacementRegion(liveManager, 0x0C80, 0x0E20)
                            + " cursorState=" + describeCursorState(liveManager)
                            + " lavaState=" + describeSpawnState(liveManager, 0x0D80, 0x54)
                            + " lavaInstance=" + describeLiveInstance(liveManager, 0x0D80, 0x54)
                            + " slot44=" + describeSlotOccupant(liveManager, 44)
                            + " slot49=" + describeSlotOccupant(liveManager, 49));
        } finally {
            sharedLevel.dispose();
        }
    }

    @Test
    void burningGrassWalkerAt0bc1AppearsAtRecordedFrameAndSlot() throws Exception {
        Assumptions.assumeTrue(Files.isDirectory(TRACE_DIR), "Trace directory not found: " + TRACE_DIR);

        Path bk2Path;
        try (var files = Files.list(TRACE_DIR)) {
            bk2Path = files
                    .filter(path -> path.toString().endsWith(".bk2"))
                    .findFirst()
                    .orElse(null);
        }
        Assumptions.assumeTrue(bk2Path != null, "No .bk2 file found in " + TRACE_DIR);

        TraceData trace = TraceData.load(TRACE_DIR);
        TraceMetadata meta = trace.metadata();
        int appearFrame = 1772;

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

            int firstFrame = -1;
            int firstSlot = -1;
            for (int i = 0; i <= appearFrame; i++) {
                TraceFrame expectedFrame = trace.getFrame(i);
                TraceFrame previous = i > 0 ? trace.getFrame(i - 1) : null;
                TraceExecutionPhase phase =
                        TraceExecutionModel.forGame(meta.game()).phaseFor(previous, expectedFrame);
                if (phase == TraceExecutionPhase.VBLANK_ONLY) {
                    fixture.skipFrameFromRecording();
                } else {
                    fixture.stepFrameFromRecording();
                }

                ObjectInstance grassFire = findLiveObjectAt(GameServices.level().getObjectManager(),
                        0x35, 0x0BC1, 0x02D7);
                if (firstFrame < 0 && grassFire != null) {
                    firstFrame = i;
                    firstSlot = ((AbstractObjectInstance) grassFire).getSlotIndex();
                }
            }

            ObjectManager liveManager = GameServices.level().getObjectManager();
            ObjectInstance grassFire = findLiveObjectAt(liveManager, 0x35, 0x0BC1, 0x02D7);
            int observedFirstFrame = firstFrame;
            int observedFirstSlot = firstSlot;
            assertNotNull(grassFire,
                    () -> "Expected burning grass walker at x=0x0BC1 to be alive at frame " + appearFrame
                            + " liveSlots=" + collectLiveSlotDetails(liveManager)
                            + " placementRegion=" + describePlacementRegion(liveManager, 0x0B00, 0x0D00)
                            + " platformState=" + describeSpawnState(liveManager, 0x0C00, 0x2F)
                            + " grassInstance=" + describeLiveInstance(liveManager, 0x0BC1, 0x35)
                            + " slot44=" + describeSlotOccupant(liveManager, 44)
                            + " slot51=" + describeSlotOccupant(liveManager, 51));
            assertEquals(appearFrame, firstFrame,
                    () -> "Burning grass walker at x=0x0BC1 first appeared on frame " + observedFirstFrame
                            + " slot=" + observedFirstSlot
                            + " liveSlots=" + collectLiveSlotDetails(liveManager)
                            + " placementRegion=" + describePlacementRegion(liveManager, 0x0B00, 0x0D00)
                            + " platformState=" + describeSpawnState(liveManager, 0x0C00, 0x2F)
                            + " grassInstance=" + describeLiveInstance(liveManager, 0x0BC1, 0x35)
                            + " slot44=" + describeSlotOccupant(liveManager, 44)
                            + " slot51=" + describeSlotOccupant(liveManager, 51));
            assertEquals(44, ((AbstractObjectInstance) grassFire).getSlotIndex(),
                    () -> "Burning grass walker at x=0x0BC1 used wrong slot at frame " + appearFrame
                            + " liveSlots=" + collectLiveSlotDetails(liveManager)
                            + " placementRegion=" + describePlacementRegion(liveManager, 0x0B00, 0x0D00)
                            + " platformState=" + describeSpawnState(liveManager, 0x0C00, 0x2F)
                            + " grassInstance=" + describeLiveInstance(liveManager, 0x0BC1, 0x35)
                            + " slot44=" + describeSlotOccupant(liveManager, 44)
                            + " slot51=" + describeSlotOccupant(liveManager, 51));
        } finally {
            sharedLevel.dispose();
        }
    }

    @Test
    void mzBrickAt0e30FirstAppearsAtRecordedFrameAndSlot() throws Exception {
        Assumptions.assumeTrue(Files.isDirectory(TRACE_DIR), "Trace directory not found: " + TRACE_DIR);

        Path bk2Path;
        try (var files = Files.list(TRACE_DIR)) {
            bk2Path = files
                    .filter(path -> path.toString().endsWith(".bk2"))
                    .findFirst()
                    .orElse(null);
        }
        Assumptions.assumeTrue(bk2Path != null, "No .bk2 file found in " + TRACE_DIR);

        TraceData trace = TraceData.load(TRACE_DIR);
        TraceMetadata meta = trace.metadata();
        int appearFrame = 1815;

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

            int firstFrame = -1;
            int firstSlot = -1;
            for (int i = 0; i <= appearFrame; i++) {
                TraceFrame expectedFrame = trace.getFrame(i);
                TraceFrame previous = i > 0 ? trace.getFrame(i - 1) : null;
                TraceExecutionPhase phase =
                        TraceExecutionModel.forGame(meta.game()).phaseFor(previous, expectedFrame);
                if (phase == TraceExecutionPhase.VBLANK_ONLY) {
                    fixture.skipFrameFromRecording();
                } else {
                    fixture.stepFrameFromRecording();
                }

                ObjectInstance brick = findLiveObjectAt(GameServices.level().getObjectManager(),
                        0x46, 0x0E30, 0x0530);
                if (firstFrame < 0 && brick != null) {
                    firstFrame = i;
                    firstSlot = ((AbstractObjectInstance) brick).getSlotIndex();
                }
            }

            ObjectManager liveManager = GameServices.level().getObjectManager();
            ObjectInstance brick = findLiveObjectAt(liveManager, 0x46, 0x0E30, 0x0530);
            int observedFirstFrame = firstFrame;
            int observedFirstSlot = firstSlot;
            assertNotNull(brick,
                    () -> "Expected MZ brick at x=0x0E30 to be alive at frame " + appearFrame
                            + " liveSlots=" + collectLiveSlotDetails(liveManager)
                            + " placementRegion=" + describePlacementRegion(liveManager, 0x0D80, 0x0F20)
                            + " brickState=" + describeSpawnState(liveManager, 0x0E30, 0x46)
                            + " brickInstance=" + describeLiveInstance(liveManager, 0x0E30, 0x46));
            assertEquals(appearFrame, firstFrame,
                    () -> "MZ brick at x=0x0E30 first appeared on frame " + observedFirstFrame
                            + " slot=" + observedFirstSlot
                            + " liveSlots=" + collectLiveSlotDetails(liveManager)
                            + " placementRegion=" + describePlacementRegion(liveManager, 0x0D80, 0x0F20)
                            + " brickState=" + describeSpawnState(liveManager, 0x0E30, 0x46)
                            + " brickInstance=" + describeLiveInstance(liveManager, 0x0E30, 0x46));
            assertEquals(85, ((AbstractObjectInstance) brick).getSlotIndex(),
                    () -> "MZ brick at x=0x0E30 used wrong slot at frame " + appearFrame
                            + " liveSlots=" + collectLiveSlotDetails(liveManager)
                            + " placementRegion=" + describePlacementRegion(liveManager, 0x0D80, 0x0F20)
                            + " brickState=" + describeSpawnState(liveManager, 0x0E30, 0x46)
                            + " brickInstance=" + describeLiveInstance(liveManager, 0x0E30, 0x46));
        } finally {
            sharedLevel.dispose();
        }
    }

    @Test
    void caterkillerBodySegmentsStillOccupySlotsDuringDeleteRoutineFrame() throws Exception {
        Assumptions.assumeTrue(Files.isDirectory(TRACE_DIR), "Trace directory not found: " + TRACE_DIR);

        Path bk2Path;
        try (var files = Files.list(TRACE_DIR)) {
            bk2Path = files
                    .filter(path -> path.toString().endsWith(".bk2"))
                    .findFirst()
                    .orElse(null);
        }
        Assumptions.assumeTrue(bk2Path != null, "No .bk2 file found in " + TRACE_DIR);

        TraceData trace = TraceData.load(TRACE_DIR);
        TraceMetadata meta = trace.metadata();
        int deleteRoutineFrame = 1040;

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

            for (int i = 0; i <= deleteRoutineFrame; i++) {
                TraceFrame expectedFrame = trace.getFrame(i);
                TraceFrame previous = i > 0 ? trace.getFrame(i - 1) : null;
                TraceExecutionPhase phase =
                        TraceExecutionModel.forGame(meta.game()).phaseFor(previous, expectedFrame);
                if (phase == TraceExecutionPhase.VBLANK_ONLY) {
                    fixture.skipFrameFromRecording();
                } else {
                    fixture.stepFrameFromRecording();
                }
            }

            ObjectManager liveManager = GameServices.level().getObjectManager();
            assertLiveObjectIdAtSlot(liveManager, 36, 0x78, deleteRoutineFrame);
            assertLiveObjectIdAtSlot(liveManager, 39, 0x78, deleteRoutineFrame);
            assertLiveObjectIdAtSlot(liveManager, 41, 0x78, deleteRoutineFrame);
        } finally {
            sharedLevel.dispose();
        }
    }

    @Test
    void slotSuffixStillMatchesMissileAnimalPointsSequenceDuringDeleteRoutineFrame() throws Exception {
        Assumptions.assumeTrue(Files.isDirectory(TRACE_DIR), "Trace directory not found: " + TRACE_DIR);

        Path bk2Path;
        try (var files = Files.list(TRACE_DIR)) {
            bk2Path = files
                    .filter(path -> path.toString().endsWith(".bk2"))
                    .findFirst()
                    .orElse(null);
        }
        Assumptions.assumeTrue(bk2Path != null, "No .bk2 file found in " + TRACE_DIR);

        TraceData trace = TraceData.load(TRACE_DIR);
        TraceMetadata meta = trace.metadata();
        int deleteRoutineFrame = 1040;

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

            for (int i = 0; i <= deleteRoutineFrame; i++) {
                TraceFrame expectedFrame = trace.getFrame(i);
                TraceFrame previous = i > 0 ? trace.getFrame(i - 1) : null;
                TraceExecutionPhase phase =
                        TraceExecutionModel.forGame(meta.game()).phaseFor(previous, expectedFrame);
                if (phase == TraceExecutionPhase.VBLANK_ONLY) {
                    fixture.skipFrameFromRecording();
                } else {
                    fixture.stepFrameFromRecording();
                }
            }

            ObjectManager liveManager = GameServices.level().getObjectManager();
            assertLiveObjectIdAtSlot(liveManager, 45, 0x23, deleteRoutineFrame);
            assertLiveObjectIdAtSlot(liveManager, 46, 0x28, deleteRoutineFrame);
            assertLiveObjectIdAtSlot(liveManager, 47, 0x29, deleteRoutineFrame);
        } finally {
            sharedLevel.dispose();
        }
    }

    private static Map<Integer, String> collectLiveSlotDetails(ObjectManager objectManager) {
        Map<Integer, String> slots = new TreeMap<>();
        if (objectManager == null) {
            return slots;
        }
        for (ObjectInstance instance : objectManager.getActiveObjects()) {
            if (instance.isDestroyed() || !(instance instanceof AbstractObjectInstance aoi)) {
                continue;
            }
            int slot = aoi.getSlotIndex();
            if (slot < 0) {
                continue;
            }
            ObjectSpawn spawn = instance.getSpawn();
            String objectId = spawn != null
                    ? String.format("0x%02X", spawn.objectId() & 0xFF)
                    : "null";
            String x = safeHexCoord(instance, true);
            String y = safeHexCoord(instance, false);
            slots.put(slot, String.format("%s id=%s x=0x%04X y=0x%04X",
                    instance.getClass().getSimpleName(),
                    objectId,
                    Integer.parseInt(x, 16),
                    Integer.parseInt(y, 16)));
        }
        appendLostRingDetails(slots);
        return slots;
    }

    private static void appendLostRingSlots(Map<Integer, String> slots) {
        for (LostRing ring : getActiveLostRings()) {
            if (ring.getSlotIndex() >= 0) {
                slots.put(ring.getSlotIndex(), "0x37");
            }
        }
    }

    private static void appendLostRingDetails(Map<Integer, String> slots) {
        for (LostRing ring : getActiveLostRings()) {
            if (ring.getSlotIndex() < 0) {
                continue;
            }
            slots.put(ring.getSlotIndex(), String.format("LostRing id=0x37 x=0x%04X y=0x%04X",
                    ring.getX() & 0xFFFF,
                    ring.getY() & 0xFFFF));
        }
    }

    private static List<LostRing> getActiveLostRings() {
        RingManager ringManager = GameServices.level() != null ? GameServices.level().getRingManager() : null;
        return ringManager != null ? ringManager.getActiveLostRings() : List.of();
    }

    private static String safeHexCoord(ObjectInstance instance, boolean xAxis) {
        try {
            int value = xAxis ? instance.getX() : instance.getY();
            return String.format("%04X", value & 0xFFFF);
        } catch (NullPointerException e) {
            return "FFFF";
        }
    }

    private static String describePlacementRegion(ObjectManager objectManager) {
        return describePlacementRegion(objectManager, 0x0A80, 0x0D00);
    }

    private static String describePlacementRegion(ObjectManager objectManager, int minX, int maxX) {
        if (objectManager == null) {
            return "<no object manager>";
        }
        return objectManager.getActiveSpawns().stream()
                .filter(spawn -> spawn.x() >= minX && spawn.x() <= maxX)
                .map(spawn -> String.format("0x%04X:0x%02X",
                        spawn.x() & 0xFFFF,
                        spawn.objectId() & 0xFF))
                .reduce((left, right) -> left + " " + right)
                .orElse("<none>");
    }

    private static String describeCursorState(ObjectManager objectManager) {
        if (objectManager == null) {
            return "<no object manager>";
        }
        int[] state = objectManager.getPlacementCursorState();
        if (state == null) {
            return "<not counter-based>";
        }
        return String.format("cursor=%d left=%d fwd=%d bwd=%d opl=0x%04X",
                state[0], state[1], state[2], state[3], state[4] & 0xFFFF);
    }

    private static String describeAnimalStates(ObjectManager objectManager) {
        if (objectManager == null) {
            return "<no object manager>";
        }
        return objectManager.getActiveObjects().stream()
                .filter(instance -> !instance.isDestroyed())
                .filter(instance -> instance instanceof com.openggf.game.sonic1.objects.Sonic1AnimalsObjectInstance)
                .map(instance -> {
                    int slot = instance instanceof AbstractObjectInstance aoi ? aoi.getSlotIndex() : -1;
                    return String.format("slot=%d x=0x%04X y=0x%04X %s",
                            slot,
                            instance.getX() & 0xFFFF,
                            instance.getY() & 0xFFFF,
                            reflectAnimalState(instance));
                })
                .reduce((left, right) -> left + " | " + right)
                .orElse("<none>");
    }

    private static ObjectInstance findLiveObjectAtSlot(ObjectManager objectManager, int slotIndex) {
        if (objectManager == null) {
            return null;
        }
        return objectManager.getActiveObjects().stream()
                .filter(instance -> !instance.isDestroyed())
                .filter(AbstractObjectInstance.class::isInstance)
                .filter(instance -> ((AbstractObjectInstance) instance).getSlotIndex() == slotIndex)
                .findFirst()
                .orElse(null);
    }

    private static String describeSlotOccupant(ObjectManager objectManager, int slotIndex) {
        ObjectInstance instance = findLiveObjectAtSlot(objectManager, slotIndex);
        if (instance == null) {
            return "<empty>";
        }
        int objectId = instance.getSpawn() != null ? instance.getSpawn().objectId() & 0xFF : -1;
        return String.format("slot=%d id=0x%02X x=0x%04X y=0x%04X class=%s",
                slotIndex,
                objectId,
                instance.getX() & 0xFFFF,
                instance.getY() & 0xFFFF,
                instance.getClass().getSimpleName());
    }

    private static ObjectInstance findLiveObjectAt(ObjectManager objectManager,
            int expectedObjectId, int expectedX, int expectedY) {
        if (objectManager == null) {
            return null;
        }
        return objectManager.getActiveObjects().stream()
                .filter(instance -> !instance.isDestroyed())
                .filter(AbstractObjectInstance.class::isInstance)
                .filter(instance -> instance.getSpawn() != null)
                .filter(instance -> (instance.getSpawn().objectId() & 0xFF) == expectedObjectId)
                .filter(instance -> safeHexCoord(instance, true).equals(String.format("%04X", expectedX)))
                .filter(instance -> safeHexCoord(instance, false).equals(String.format("%04X", expectedY)))
                .findFirst()
                .orElse(null);
    }

    private static boolean hasLiveObjectAt(ObjectManager objectManager,
            int expectedObjectId, int expectedX, int expectedY) {
        return findLiveObjectAt(objectManager, expectedObjectId, expectedX, expectedY) != null;
    }

    private static void assertLiveObjectIdAtSlot(ObjectManager objectManager,
            int slotIndex, int expectedObjectId, int frame) {
        ObjectInstance instance = findLiveObjectAtSlot(objectManager, slotIndex);
        assertNotNull(instance,
                () -> "Expected live object at slot " + slotIndex + " during frame " + frame
                        + " liveSlots=" + collectLiveSlotDetails(objectManager)
                        + " allStates=" + describeAllObjectStates(objectManager));
        int actualObjectId = instance.getSpawn() != null ? instance.getSpawn().objectId() & 0xFF : -1;
        assertEquals(expectedObjectId, actualObjectId,
                () -> "Unexpected object id at slot " + slotIndex + " during frame " + frame
                        + " liveSlots=" + collectLiveSlotDetails(objectManager)
                        + " allStates=" + describeAllObjectStates(objectManager));
    }

    private static String describeAllObjectStates(ObjectManager objectManager) {
        if (objectManager == null) {
            return "<no object manager>";
        }
        return objectManager.getActiveObjects().stream()
                .map(instance -> {
                    int slot = instance instanceof AbstractObjectInstance aoi ? aoi.getSlotIndex() : -1;
                    int objectId = instance.getSpawn() != null ? instance.getSpawn().objectId() & 0xFF : -1;
                    return String.format("slot=%d id=0x%02X type=%s destroyed=%s x=0x%04X y=0x%04X",
                            slot,
                            objectId,
                            instance.getClass().getSimpleName(),
                            instance.isDestroyed(),
                            instance.getX() & 0xFFFF,
                            instance.getY() & 0xFFFF);
                })
                .reduce((left, right) -> left + " | " + right)
                .orElse("<none>");
    }

    private static String describeUsedSlotOccupancyMismatch(ObjectManager objectManager) {
        if (objectManager == null) {
            return "<no object manager>";
        }
        BitSet used = reflectUsedSlots(objectManager);
        Map<Integer, String> liveSlots = collectLiveSlotDetails(objectManager);
        List<Integer> stale = new ArrayList<>();
        List<Integer> missing = new ArrayList<>();
        for (int slot = 32; slot <= 127; slot++) {
            boolean usedBit = used.get(slot - 32);
            boolean live = liveSlots.containsKey(slot);
            if (usedBit && !live) {
                stale.add(slot);
            } else if (!usedBit && live) {
                missing.add(slot);
            }
        }
        if (stale.isEmpty() && missing.isEmpty()) {
            return "<none>";
        }
        return "stale=" + stale + " missing=" + missing;
    }

    private static BitSet reflectUsedSlots(ObjectManager objectManager) {
        try {
            Field field = ObjectManager.class.getDeclaredField("usedSlots");
            field.setAccessible(true);
            return (BitSet) ((BitSet) field.get(objectManager)).clone();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to inspect usedSlots", e);
        }
    }

    private static int reflectAnimalRoutine(ObjectInstance instance) {
        try {
            Field routine = instance.getClass().getDeclaredField("routine");
            routine.setAccessible(true);
            return routine.getInt(instance) & 0xFF;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to inspect animal routine", e);
        }
    }

    private static String reflectAnimalState(ObjectInstance instance) {
        try {
            Class<?> type = instance.getClass();
            Field routine = type.getDeclaredField("routine");
            Field variant = type.getDeclaredField("fromEnemyVariantIndex");
            Field xVelocity = type.getDeclaredField("xVelocity");
            Field yVelocity = type.getDeclaredField("yVelocity");
            Field subtype = type.getDeclaredField("subtype");
            routine.setAccessible(true);
            variant.setAccessible(true);
            xVelocity.setAccessible(true);
            yVelocity.setAccessible(true);
            subtype.setAccessible(true);
            return String.format("routine=0x%02X variant=%d subtype=0x%02X xVel=0x%04X yVel=0x%04X",
                    routine.getInt(instance) & 0xFF,
                    variant.getInt(instance),
                    subtype.getInt(instance) & 0xFF,
                    xVelocity.getInt(instance) & 0xFFFF,
                    yVelocity.getInt(instance) & 0xFFFF);
        } catch (ReflectiveOperationException e) {
            return "<reflection failed: " + e.getClass().getSimpleName() + ">";
        }
    }

    private static String describeSpawnState(ObjectManager objectManager, int x, int objectId) {
        if (objectManager == null) {
            return "<no object manager>";
        }
        return findSpawn(objectManager, x, objectId)
                .map(spawn -> String.format("active=%s remembered=%s dormant=%s counter=%d",
                        objectManager.getActiveSpawns().contains(spawn),
                        objectManager.isRemembered(spawn),
                        objectManager.isDormant(spawn),
                        objectManager.getSpawnCounter(spawn)))
                .orElse("<missing spawn>");
    }

    private static String describeLiveInstance(ObjectManager objectManager, int x, int objectId) {
        if (objectManager == null) {
            return "<no object manager>";
        }
        return findSpawn(objectManager, x, objectId)
                .map(spawn -> objectManager.getActiveObjects().stream()
                        .filter(instance -> instance.getSpawn() == spawn)
                        .findFirst()
                        .map(instance -> {
                            int slot = instance instanceof AbstractObjectInstance aoi ? aoi.getSlotIndex() : -1;
                            return String.format("slot=%d type=%s destroyed=%s",
                                    slot,
                                    instance.getClass().getSimpleName(),
                                    instance.isDestroyed());
                        })
                        .orElse("<not instantiated>"))
                .orElse("<missing spawn>");
    }

    @SuppressWarnings("unchecked")
    private static String describePrivateMapState(ObjectManager objectManager, int x, int objectId) {
        if (objectManager == null) {
            return "<no object manager>";
        }
        Optional<ObjectSpawn> maybeSpawn = findSpawn(objectManager, x, objectId);
        if (maybeSpawn.isEmpty()) {
            return "<missing spawn>";
        }
        ObjectSpawn spawn = maybeSpawn.get();
        try {
            Field activeObjectsField = ObjectManager.class.getDeclaredField("activeObjects");
            activeObjectsField.setAccessible(true);
            Map<ObjectSpawn, ObjectInstance> activeObjects =
                    (Map<ObjectSpawn, ObjectInstance>) activeObjectsField.get(objectManager);

            ObjectInstance byIdentity = activeObjects.get(spawn);
            ObjectInstance byEquals = null;
            for (Map.Entry<ObjectSpawn, ObjectInstance> entry : activeObjects.entrySet()) {
                if (entry.getKey().equals(spawn)) {
                    byEquals = entry.getValue();
                    break;
                }
            }

            return "identityKey=" + describeInstance(byIdentity)
                    + " equalsKey=" + describeInstance(byEquals)
                    + " activeContainsIdentity=" + activeObjects.containsKey(spawn);
        } catch (ReflectiveOperationException e) {
            return "<reflection failed: " + e.getClass().getSimpleName() + ">";
        }
    }

    private static String describeInstance(ObjectInstance instance) {
        if (instance == null) {
            return "null";
        }
        int slot = instance instanceof AbstractObjectInstance aoi ? aoi.getSlotIndex() : -1;
        ObjectSpawn spawn = instance.getSpawn();
        return instance.getClass().getSimpleName()
                + "@slot=" + slot
                + " spawnId=" + (spawn != null ? String.format("0x%02X", spawn.objectId() & 0xFF) : "null")
                + " destroyed=" + instance.isDestroyed();
    }

    private static Optional<ObjectSpawn> findSpawn(ObjectManager objectManager, int x, int objectId) {
        return objectManager.getAllSpawns().stream()
                .filter(spawn -> spawn.x() == x && (spawn.objectId() & 0xFF) == objectId)
                .findFirst();
    }

    private static String describeSpawnStates(ObjectManager objectManager, int minX, int maxX, int objectId) {
        if (objectManager == null) {
            return "<no object manager>";
        }
        return objectManager.getAllSpawns().stream()
                .filter(spawn -> spawn.x() >= minX && spawn.x() <= maxX)
                .filter(spawn -> (spawn.objectId() & 0xFF) == objectId)
                .map(spawn -> String.format("0x%04X:active=%s remembered=%s dormant=%s counter=%d subtype=0x%02X",
                        spawn.x() & 0xFFFF,
                        objectManager.getActiveSpawns().contains(spawn),
                        objectManager.isRemembered(spawn),
                        objectManager.isDormant(spawn),
                        objectManager.getSpawnCounter(spawn),
                        spawn.subtype() & 0xFF))
                .reduce((left, right) -> left + " " + right)
                .orElse("<none>");
    }

    private static String describeLavaBallMakerStates(ObjectManager objectManager) {
        if (objectManager == null) {
            return "<no object manager>";
        }
        return objectManager.getActiveObjects().stream()
                .filter(instance -> instance.getClass().getSimpleName().equals("Sonic1LavaBallMakerObjectInstance"))
                .map(instance -> {
                    try {
                        Field timerField = instance.getClass().getDeclaredField("timer");
                        timerField.setAccessible(true);
                        Field delayField = instance.getClass().getDeclaredField("spawnDelay");
                        delayField.setAccessible(true);
                        return String.format("slot=%d x=0x%04X y=0x%04X timer=%d delay=%d",
                                ((AbstractObjectInstance) instance).getSlotIndex(),
                                ((AbstractObjectInstance) instance).getX() & 0xFFFF,
                                ((AbstractObjectInstance) instance).getY() & 0xFFFF,
                                timerField.getInt(instance),
                                delayField.getInt(instance));
                    } catch (ReflectiveOperationException e) {
                        return "reflect-failed:" + e.getClass().getSimpleName();
                    }
                })
                .reduce((left, right) -> left + " " + right)
                .orElse("<none>");
    }

    private static String describeRingMappingSizes(ObjectManager objectManager, int minX, int maxX) {
        if (objectManager == null || !(GameServices.level().getCurrentLevel() instanceof com.openggf.game.sonic1.Sonic1Level s1Level)) {
            return "<no s1 level>";
        }
        Map<ObjectSpawn, List<com.openggf.level.rings.RingSpawn>> mapping = s1Level.getRingSpawnMapping();
        return objectManager.getAllSpawns().stream()
                .filter(spawn -> spawn.x() >= minX && spawn.x() <= maxX)
                .filter(spawn -> (spawn.objectId() & 0xFF) == 0x25)
                .map(spawn -> {
                    List<com.openggf.level.rings.RingSpawn> direct = mapping.get(spawn);
                    List<com.openggf.level.rings.RingSpawn> byEquals = mapping.entrySet().stream()
                            .filter(entry -> entry.getKey().equals(spawn))
                            .map(Map.Entry::getValue)
                            .findFirst()
                            .orElse(null);
                    return String.format("0x%04X:direct=%d equals=%d",
                            spawn.x() & 0xFFFF,
                            direct != null ? direct.size() : -1,
                            byEquals != null ? byEquals.size() : -1);
                })
                .reduce((left, right) -> left + " " + right)
                .orElse("<none>");
    }

    private static String describeLiveRingInstanceStates(ObjectManager objectManager, int minX, int maxX) {
        if (objectManager == null) {
            return "<no object manager>";
        }
        return objectManager.getActiveObjects().stream()
                .filter(com.openggf.game.sonic1.objects.Sonic1RingInstance.class::isInstance)
                .map(com.openggf.game.sonic1.objects.Sonic1RingInstance.class::cast)
                .filter(instance -> instance.getX() >= minX && instance.getX() <= maxX)
                .map(instance -> {
                    try {
                        Field childField = com.openggf.game.sonic1.objects.Sonic1RingInstance.class
                                .getDeclaredField("childRingSpawns");
                        childField.setAccessible(true);
                        List<?> children = (List<?>) childField.get(instance);
                        Field stateField = com.openggf.game.sonic1.objects.Sonic1RingInstance.class
                                .getDeclaredField("state");
                        stateField.setAccessible(true);
                        Object state = stateField.get(instance);
                        return String.format("0x%04X:children=%d state=%s slot=%d",
                                instance.getX() & 0xFFFF,
                                children.size(),
                                state,
                                instance.getSlotIndex());
                    } catch (ReflectiveOperationException e) {
                        return "reflect-failed:" + e.getClass().getSimpleName();
                    }
                })
                .reduce((left, right) -> left + " " + right)
                .orElse("<none>");
    }

    @SuppressWarnings("unchecked")
    private static String describeReservedChildSlots(ObjectManager objectManager, int minX, int maxX, int objectId) {
        if (objectManager == null) {
            return "<no object manager>";
        }
        try {
            Field field = ObjectManager.class.getDeclaredField("reservedChildSlots");
            field.setAccessible(true);
            Map<ObjectSpawn, int[]> reserved = (Map<ObjectSpawn, int[]>) field.get(objectManager);
            return reserved.entrySet().stream()
                    .filter(entry -> entry.getKey() != null)
                    .filter(entry -> entry.getKey().x() >= minX && entry.getKey().x() <= maxX)
                    .filter(entry -> (entry.getKey().objectId() & 0xFF) == objectId)
                    .map(entry -> String.format("0x%04X:%s",
                            entry.getKey().x() & 0xFFFF,
                            java.util.Arrays.toString(entry.getValue())))
                    .reduce((left, right) -> left + " " + right)
                    .orElse("<none>");
        } catch (ReflectiveOperationException e) {
            return "<error " + e.getClass().getSimpleName() + ">";
        }
    }

    private static String describeDuplicateSlots(ObjectManager objectManager) {
        if (objectManager == null) {
            return "<no object manager>";
        }
        Map<Integer, java.util.List<String>> occupants = new TreeMap<>();
        for (ObjectInstance instance : objectManager.getActiveObjects()) {
            if (instance.isDestroyed() || !(instance instanceof AbstractObjectInstance aoi)) {
                continue;
            }
            int slot = aoi.getSlotIndex();
            if (slot < 0) {
                continue;
            }
            String objectId = instance.getSpawn() != null
                    ? String.format("0x%02X", instance.getSpawn().objectId() & 0xFF)
                    : "null";
            occupants.computeIfAbsent(slot, ignored -> new java.util.ArrayList<>())
                    .add(instance.getClass().getSimpleName() + ":" + objectId);
        }
        return occupants.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((left, right) -> left + " " + right)
                .orElse("<none>");
    }

    private static String describeRingCounts(TraceFrame expectedFrame, HeadlessTestFixture fixture) {
        if (expectedFrame == null || fixture == null) {
            return "<unknown>";
        }
        return String.format("expected=%d actual=%d",
                expectedFrame.rings(),
                fixture.sprite().getRingCount());
    }

    private static String describeChildCollectedStates(ObjectManager objectManager, int minX, int maxX) {
        if (objectManager == null) {
            return "<no object manager>";
        }
        RingManager ringManager = GameServices.level() != null ? GameServices.level().getRingManager() : null;
        if (ringManager == null) {
            return "<no ring manager>";
        }
        return objectManager.getActiveObjects().stream()
                .filter(Sonic1RingInstance.class::isInstance)
                .map(Sonic1RingInstance.class::cast)
                .filter(instance -> instance.getSpawn() != null)
                .filter(instance -> instance.getSpawn().x() >= minX && instance.getSpawn().x() <= maxX)
                .map(instance -> describeChildCollectedState(instance, ringManager))
                .reduce((left, right) -> left + " " + right)
                .orElse("<none>");
    }

    private static String describeChildCollectedState(Sonic1RingInstance instance, RingManager ringManager) {
        try {
            Field field = Sonic1RingInstance.class.getDeclaredField("childRingSpawns");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<com.openggf.level.rings.RingSpawn> childRings =
                    (List<com.openggf.level.rings.RingSpawn>) field.get(instance);
            return String.format("0x%04X:%s",
                    instance.getSpawn().x() & 0xFFFF,
                    childRings.stream()
                            .map(ring -> String.format("0x%04X=%s",
                                    ring.x() & 0xFFFF,
                                    ringManager.isCollected(ring)))
                            .collect(java.util.stream.Collectors.joining(",")));
        } catch (ReflectiveOperationException e) {
            return "0x" + Integer.toHexString(instance.getSpawn().x() & 0xFFFF) + ":<error>";
        }
    }

    private static String describeRingSparkleState(ObjectManager objectManager, int minX, int maxX) {
        if (objectManager == null) {
            return "<no object manager>";
        }
        RingManager ringManager = GameServices.level() != null ? GameServices.level().getRingManager() : null;
        if (ringManager == null || !(GameServices.level().getCurrentLevel() instanceof com.openggf.game.sonic1.Sonic1Level s1Level)) {
            return "<no ring manager>";
        }
        Map<ObjectSpawn, List<com.openggf.level.rings.RingSpawn>> mapping = s1Level.getRingSpawnMapping();
        return objectManager.getAllSpawns().stream()
                .filter(spawn -> spawn.x() >= minX && spawn.x() <= maxX)
                .filter(spawn -> (spawn.objectId() & 0xFF) == 0x25)
                .map(spawn -> {
                    List<com.openggf.level.rings.RingSpawn> rings = mapping.get(spawn);
                    if (rings == null) {
                        return String.format("0x%04X:<no-mapping>", spawn.x() & 0xFFFF);
                    }
                    String state = rings.stream()
                            .map(ring -> String.format("0x%04X[c=%s,s=%d]",
                                    ring.x() & 0xFFFF,
                                    ringManager.isCollected(ring),
                                    ringManager.getSparkleStartFrame(ring)))
                            .collect(java.util.stream.Collectors.joining(","));
                    return String.format("0x%04X:%s", spawn.x() & 0xFFFF, state);
                })
                .reduce((left, right) -> left + " " + right)
                .orElse("<none>");
    }
}
