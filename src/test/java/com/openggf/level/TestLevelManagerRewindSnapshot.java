package com.openggf.level;

import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.rewind.snapshot.LevelSnapshot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for LevelManager's rewind snapshot adapter.
 *
 * Note: These tests use a minimal stub level without full ROM loading.
 * Full integration tests would require a HeadlessTestRunner or similar fixture.
 */
class TestLevelManagerRewindSnapshot {

    /**
     * Minimal concrete stub for testing level snapshots without full load.
     */
    static class StubLevel extends AbstractLevel {
        StubLevel() {
            super(0);
            // Initialize required fields to non-null values
            this.palettes = new Palette[PALETTE_COUNT];
            this.patterns = new Pattern[256];
            this.chunks = new Chunk[256];
            this.blocks = new Block[256];
            for (int i = 0; i < 256; i++) {
                this.chunks[i] = new Chunk();
                this.blocks[i] = new Block();
            }
            this.solidTiles = new SolidTile[256];
            this.map = new Map(2, 256, 256);
            this.objects = new ArrayList<>();
            this.rings = new ArrayList<>();
            this.patternCount = 256;
            this.chunkCount = 256;
            this.blockCount = 256;
            this.solidTileCount = 256;
            this.minX = 0;
            this.maxX = 1024;
            this.minY = 0;
            this.maxY = 1024;
        }
    }

    /**
     * Minimal stub LevelManager that wraps a level for testing.
     */
    static class StubLevelManager {
        private final Level level;

        StubLevelManager(Level level) {
            this.level = level;
        }

        public Level getCurrentLevel() {
            return level;
        }

        /**
         * Returns a rewind snapshottable adapter (copied from LevelManager).
         */
        public RewindSnapshottable<LevelSnapshot> levelRewindSnapshottable() {
            return new RewindSnapshottable<LevelSnapshot>() {
                @Override
                public String key() {
                    return "level";
                }

                @Override
                public LevelSnapshot capture() {
                    Level currentLevel = getCurrentLevel();
                    if (!(currentLevel instanceof AbstractLevel)) {
                        throw new IllegalStateException("Current level is not an AbstractLevel: " + currentLevel.getClass().getName());
                    }
                    AbstractLevel level = (AbstractLevel) currentLevel;

                    return new LevelSnapshot(
                            level.currentEpoch(),
                            level.blocksReference().clone(),
                            level.chunksReference().clone(),
                            level.getMap().getData()
                    );
                }

                @Override
                public void restore(LevelSnapshot s) {
                    Level currentLevel = getCurrentLevel();
                    if (!(currentLevel instanceof AbstractLevel)) {
                        throw new IllegalStateException("Current level is not an AbstractLevel: " + currentLevel.getClass().getName());
                    }
                    AbstractLevel level = (AbstractLevel) currentLevel;

                    level.replaceBlocks(s.blocks());
                    level.replaceChunks(s.chunks());
                    level.getMap().restoreData(s.mapData());
                    level.bumpEpoch();
                    level.markAllDirty();
                }
            };
        }
    }

    @Test
    void adapterKeyIsStable() {
        StubLevel level = new StubLevel();
        StubLevelManager manager = new StubLevelManager(level);
        RewindSnapshottable<LevelSnapshot> adapter = manager.levelRewindSnapshottable();

        assertEquals("level", adapter.key());
    }

    @Test
    void captureRestoreRoundTripBlockArrayReference() {
        StubLevel level = new StubLevel();
        StubLevelManager manager = new StubLevelManager(level);
        RewindSnapshottable<LevelSnapshot> adapter = manager.levelRewindSnapshottable();

        // Capture snapshot
        LevelSnapshot snap1 = adapter.capture();

        // Mutate block array by replacing the reference
        Block[] newBlocks = new Block[512];
        for (int i = 0; i < 512; i++) {
            newBlocks[i] = new Block();
        }
        level.replaceBlocks(newBlocks);

        // Snapshot should still have the original
        assertEquals(256, snap1.blocks().length);

        // Restore
        adapter.restore(snap1);

        // Level should be back to 256 blocks
        assertEquals(256, level.getBlockCount());
        assertEquals(snap1.blocks(), level.blocksReference());
    }

    @Test
    void captureRestoreRoundTripChunkArrayReference() {
        StubLevel level = new StubLevel();
        StubLevelManager manager = new StubLevelManager(level);
        RewindSnapshottable<LevelSnapshot> adapter = manager.levelRewindSnapshottable();

        // Capture snapshot
        LevelSnapshot snap1 = adapter.capture();

        // Mutate chunk array by replacing the reference
        Chunk[] newChunks = new Chunk[512];
        for (int i = 0; i < 512; i++) {
            newChunks[i] = new Chunk();
        }
        level.replaceChunks(newChunks);

        // Snapshot should still have the original
        assertEquals(256, snap1.chunks().length);

        // Restore
        adapter.restore(snap1);

        // Level should be back to 256 chunks
        assertEquals(256, level.getChunkCount());
        assertEquals(snap1.chunks(), level.chunksReference());
    }

    @Test
    void mutationAfterCaptureCowClonesUnderlyingMapData() {
        StubLevel level = new StubLevel();
        StubLevelManager manager = new StubLevelManager(level);
        RewindSnapshottable<LevelSnapshot> adapter = manager.levelRewindSnapshottable();

        // Initialize map with some data
        level.getMap().setValue(0, 0, 0, (byte) 0x11);

        // Capture snapshot
        LevelSnapshot snap1 = adapter.capture();
        byte[] snapshotMapData = snap1.mapData();

        // Verify initial value
        assert snapshotMapData[0] == (byte) 0x11;

        // Mutate the map with CoW
        level.bumpEpoch();
        level.getMap().cowEnsureWritable(level.currentEpoch());
        level.getMap().setValue(0, 0, 0, (byte) 0x99);

        // The snapshot's data should still have the original value
        assert snapshotMapData[0] == (byte) 0x11 : "Snapshot data should be unchanged after CoW mutation";

        // The live map should have the new value
        assert level.getMap().getValue(0, 0, 0) == (byte) 0x99;
    }

    @Test
    void restoreIncrementsEpoch() {
        StubLevel level = new StubLevel();
        StubLevelManager manager = new StubLevelManager(level);
        RewindSnapshottable<LevelSnapshot> adapter = manager.levelRewindSnapshottable();

        long epoch1 = level.currentEpoch();

        // Capture snapshot
        LevelSnapshot snap1 = adapter.capture();

        // Restore
        adapter.restore(snap1);

        long epoch2 = level.currentEpoch();

        // Epoch should have been bumped
        assertEquals(epoch1 + 1, epoch2);
    }

    @Test
    void captureRecordsCurrentEpoch() {
        StubLevel level = new StubLevel();
        level.bumpEpoch();
        level.bumpEpoch();

        StubLevelManager manager = new StubLevelManager(level);
        RewindSnapshottable<LevelSnapshot> adapter = manager.levelRewindSnapshottable();

        LevelSnapshot snap = adapter.capture();

        // Snapshot should record the epoch at capture time
        assertEquals(2L, snap.epochAtCapture());
    }

    @Test
    void multipleCaptureCyclesIndependent() {
        StubLevel level = new StubLevel();
        StubLevelManager manager = new StubLevelManager(level);
        RewindSnapshottable<LevelSnapshot> adapter = manager.levelRewindSnapshottable();

        // First capture
        LevelSnapshot snap1 = adapter.capture();
        Block[] blocks1 = snap1.blocks();

        // Mutate
        level.bumpEpoch();
        Block[] newBlocks = new Block[512];
        for (int i = 0; i < 512; i++) {
            newBlocks[i] = new Block();
        }
        level.replaceBlocks(newBlocks);

        // Second capture (should have new blocks)
        LevelSnapshot snap2 = adapter.capture();
        Block[] blocks2 = snap2.blocks();

        // Snapshots should be independent
        assertEquals(256, blocks1.length);
        assertEquals(512, blocks2.length);
        assertNotSame(blocks1, blocks2);
    }
}
