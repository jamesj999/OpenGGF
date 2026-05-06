package com.openggf.game.rewind;

import com.openggf.game.rewind.snapshot.CameraSnapshot;
import com.openggf.game.rewind.snapshot.LevelSnapshot;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;
import com.openggf.game.rewind.snapshot.SpriteManagerSnapshot;
import com.openggf.level.Block;
import com.openggf.level.Chunk;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.IdentityHashMap;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TestRewindBenchmarkSizeEstimator {

    @Test
    void estimatesSnapshotsWithLiveSpawnReferences() {
        ObjectSpawn spawn = new ObjectSpawn(0x120, 0x280, 0x22, 0, 0, true, 0x280, 7);
        PerObjectRewindSnapshot state = new PerObjectRewindSnapshot(
                false, false,
                false, 0, 0,
                0, 0, false, 0,
                false, false,
                32, 3,
                null, null, null);
        ObjectManagerSnapshot snapshot = new ObjectManagerSnapshot(
                new long[] {1L << 12},
                List.of(new ObjectManagerSnapshot.PerSlotEntry(32, spawn, state)),
                123,
                456,
                0,
                33,
                true,
                List.of(new ObjectManagerSnapshot.ChildSpawnEntry(spawn, new int[] {45, 46})),
                List.of(),
                new ObjectManagerSnapshot.PlacementSnapshot(
                        new int[] {7},
                        new long[] {2L},
                        new long[] {4L},
                        new long[] {8L},
                        new long[] {16L},
                        10,
                        0x100,
                        1,
                        true,
                        false,
                        true,
                        80,
                        false,
                        9,
                        2,
                        3,
                        new byte[] {0, 1, 2},
                        List.of(new ObjectManagerSnapshot.SpawnCounterEntry(7, 3))));

        long bytes = RewindBenchmark.estimateStructuralSize(snapshot);

        assertTrue(bytes > 0, "structural estimate should be positive");
    }

    @Test
    void estimatesCompositeSnapshotsBySubsystemContent() {
        CompositeSnapshot snapshot = new CompositeSnapshot(Map.of(
                "camera", new CameraSnapshot(
                        (short) 1, (short) 2,
                        (short) 3, (short) 4,
                        (short) 5, (short) 6,
                        (short) 7, (short) 8,
                        (short) 9, (short) 10,
                        (short) 11, (short) 12,
                        true, 13,
                        false, true, false,
                        14, 15, false,
                        (short) 16, (short) 17, (short) 18)));

        long bytes = RewindBenchmark.estimateStructuralSize(snapshot);

        assertTrue(bytes > 0, "composite estimate should include subsystem entries");
    }

    @Test
    void levelSnapshotChargesSharedCowDataAsReferencesOnly() {
        LevelSnapshot snapshot = new LevelSnapshot(
                1L,
                new Block[] {new Block(), new Block()},
                new Chunk[] {new Chunk(), new Chunk(), new Chunk()},
                new byte[64 * 1024],
                42);

        long bytes = RewindBenchmark.estimateStructuralSize(snapshot);

        assertTrue(bytes < 1024,
                "per-keyframe level estimate should not include the shared map payload");
    }

    @Test
    void retainedWindowEstimateAccountsForSharedReferencesAcrossKeyframes() {
        byte[] sharedPayload = new byte[64 * 1024];
        Map<String, Object> first = Map.of("payload", sharedPayload);
        Map<String, Object> second = Map.of("payload", sharedPayload);
        IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();

        long firstBytes = RewindBenchmark.estimateStructuralSizeShared(first, seen);
        long secondBytes = RewindBenchmark.estimateStructuralSizeShared(second, seen);

        assertTrue(firstBytes > secondBytes,
                "second retained keyframe should charge repeated shared payloads as references");
    }

    @Test
    void retainedWindowEstimateAccountsForSharedLevelArraysAcrossKeyframes() {
        Block[] sharedBlocks = new Block[] {new Block(), new Block()};
        Chunk[] sharedChunks = new Chunk[] {new Chunk(), new Chunk(), new Chunk()};
        byte[] sharedMap = new byte[64 * 1024];
        LevelSnapshot first = new LevelSnapshot(1L, sharedBlocks, sharedChunks, sharedMap, 42);
        LevelSnapshot second = new LevelSnapshot(1L, sharedBlocks, sharedChunks, sharedMap, 43);
        IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();

        long firstBytes = RewindBenchmark.estimateStructuralSizeShared(first, seen);
        long secondBytes = RewindBenchmark.estimateStructuralSizeShared(second, seen);

        assertTrue(firstBytes > secondBytes,
                "second level keyframe should charge shared block/chunk/map references only once");
    }

    @Test
    void placementSnapshotStoresObjStateAsBytes() {
        var component = java.util.Arrays.stream(ObjectManagerSnapshot.PlacementSnapshot.class.getRecordComponents())
                .filter(c -> c.getName().equals("objState"))
                .findFirst()
                .orElseThrow();

        assertEquals(byte[].class, component.getType());
    }

    @Test
    void spriteManagerSnapshotUsesCompactEntriesInsteadOfMapComponent() {
        boolean hasMapComponent = java.util.Arrays.stream(SpriteManagerSnapshot.class.getRecordComponents())
                .anyMatch(component -> java.util.Map.class.isAssignableFrom(component.getType()));

        assertFalse(hasMapComponent, "sprite manager snapshot should not retain map storage");
        assertEquals(SpriteManagerSnapshot.SpriteEntry[].class,
                SpriteManagerSnapshot.class.getRecordComponents()[1].getType());
    }

    @Test
    void benchmarkKeyframeIntervalComesFromProperty() {
        String oldValue = System.getProperty("openggf.rewind.benchmark.keyframeInterval");
        try {
            System.setProperty("openggf.rewind.benchmark.keyframeInterval", "30");
            assertEquals(30, RewindBenchmark.keyframeInterval());
        } finally {
            if (oldValue == null) {
                System.clearProperty("openggf.rewind.benchmark.keyframeInterval");
            } else {
                System.setProperty("openggf.rewind.benchmark.keyframeInterval", oldValue);
            }
        }
    }
}
