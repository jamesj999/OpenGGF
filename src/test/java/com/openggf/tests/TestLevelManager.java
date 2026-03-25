package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.level.*;
import com.openggf.level.rings.RingSpawn;
import org.junit.Before;
import org.junit.Test;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.rings.RingSpriteSheet;

import java.lang.reflect.Field;

import static org.junit.Assert.*;

public class TestLevelManager {

    @Before
    public void setUp() {
        TestEnvironment.resetAll();
    }

    @Test
    public void testGetBlockAtPositionWithLargeIndex() throws Exception {
        // Setup a mock level
        MockLevel level = new MockLevel();

        // Inject into LevelManager
        LevelManager levelManager = GameServices.level();
        Field levelField = LevelManager.class.getDeclaredField("level");
        levelField.setAccessible(true);
        levelField.set(levelManager, level);

        // Try to access the block at (0,0) which is mapped to index 128 (0x80)
        // LevelManager.getChunkDescAt calls getBlockAtPosition
        ChunkDesc chunkDesc = levelManager.getChunkDescAt((byte) 0, 0, 0);

        assertNotNull("ChunkDesc should not be null for block index 128", chunkDesc);
    }

    @Test
    public void testProcessDirtyRegionsConsumesSolidTileDirtySet() throws Exception {
        LevelManager levelManager = GameServices.level();
        MutableLevel mutableLevel = MutableLevel.snapshot(new MutableMockLevel());

        byte[] heights = new byte[SolidTile.TILE_SIZE_IN_ROM];
        byte[] widths = new byte[SolidTile.TILE_SIZE_IN_ROM];
        heights[0] = 7;
        widths[0] = 4;
        mutableLevel.setSolidTile(0, new SolidTile(0, heights, widths, (byte) 12));

        Field levelField = LevelManager.class.getDeclaredField("level");
        levelField.setAccessible(true);
        levelField.set(levelManager, mutableLevel);

        levelManager.processDirtyRegions();

        assertTrue("Solid-tile dirty set should be consumed by the frame pipeline",
                mutableLevel.consumeDirtySolidTiles().isEmpty());
    }

    private static class MockLevel implements Level {
        private final Map map;
        private final Block validBlock;

        public MockLevel() {
            // Map 1x1, 1 layer
            map = new Map(1, 1, 1);
            // Set value at (0,0) to -128 (which is index 128)
            map.setValue(0, 0, 0, (byte) -128);

            validBlock = new Block();
        }

        @Override public int getPaletteCount() { return 0; }
        @Override public Palette getPalette(int index) { return null; }
        @Override public int getPatternCount() { return 0; }
        @Override public Pattern getPattern(int index) { return null; }
        @Override public int getChunkCount() { return 0; }
        @Override public Chunk getChunk(int index) { return null; }
        @Override public int getBlockCount() { return 256; }

        @Override
        public Block getBlock(int index) {
            if (index == 128) {
                return validBlock;
            }
            return null;
        }

        @Override public SolidTile getSolidTile(int index) { return null; }
        @Override public Map getMap() { return map; }
        @Override public java.util.List<ObjectSpawn> getObjects() { return java.util.Collections.emptyList(); }
        @Override public java.util.List<RingSpawn> getRings() { return java.util.Collections.emptyList(); }
        @Override public RingSpriteSheet getRingSpriteSheet() { return null; }
        @Override public int getMinX() { return 0; }
        @Override public int getMaxX() { return 0; }
        @Override public int getMinY() { return 0; }
        @Override public int getMaxY() { return 0; }
        @Override public int getZoneIndex() { return 0; }
        public short getStartX() { return 0; }
        public short getStartY() { return 0; }
    }

    private static class MutableMockLevel extends MockLevel {
        private final SolidTile solidTile;

        MutableMockLevel() {
            byte[] heights = new byte[SolidTile.TILE_SIZE_IN_ROM];
            byte[] widths = new byte[SolidTile.TILE_SIZE_IN_ROM];
            heights[0] = 5;
            widths[0] = 3;
            solidTile = new SolidTile(0, heights, widths, (byte) 42);
        }

        @Override
        public int getSolidTileCount() {
            return 1;
        }

        @Override
        public SolidTile getSolidTile(int index) {
            if (index != 0) {
                return null;
            }
            return solidTile;
        }

        @Override
        public int getPatternCount() {
            return 1;
        }

        @Override
        public Pattern getPattern(int index) {
            return new Pattern();
        }

        @Override
        public int getChunkCount() {
            return 1;
        }

        @Override
        public Chunk getChunk(int index) {
            Chunk chunk = new Chunk();
            int[] state = new int[Chunk.PATTERNS_PER_CHUNK + 2];
            state[0] = 1;
            chunk.restoreState(state);
            return chunk;
        }

        @Override
        public Block getBlock(int index) {
            Block block = new Block();
            block.setChunkDesc(0, 0, new ChunkDesc(0));
            return block;
        }

        @Override
        public int getPaletteCount() {
            return 4;
        }

        @Override
        public Palette getPalette(int index) {
            return new Palette();
        }
    }
}
