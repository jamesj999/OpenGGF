package com.openggf.tests.trace.s1;

import com.openggf.game.GameServices;
import com.openggf.game.sonic1.objects.Sonic1RingInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.rings.RingManager;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tests.trace.TraceData;
import com.openggf.tests.trace.TraceEvent;
import com.openggf.tests.trace.TraceExecutionModel;
import com.openggf.tests.trace.TraceExecutionPhase;
import com.openggf.tests.trace.TraceFrame;
import com.openggf.tests.trace.TraceMetadata;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Field;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
                            + " ringSparkleState=" + ringSparkleState);
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
        return slots;
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
        return slots;
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
