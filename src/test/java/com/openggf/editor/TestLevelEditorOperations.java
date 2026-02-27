package com.openggf.editor;

import com.openggf.level.Block;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class TestLevelEditorOperations {

    private LevelEditorManager editor;
    private com.openggf.level.Map map;
    private Block[] blocks;

    @Before
    public void setUp() {
        editor = new LevelEditorManager();
        // Create a small test level: 4x4 blocks, 8 chunks per block side
        map = new com.openggf.level.Map(2, 4, 4); // 2 layers, 4 wide, 4 tall
        map.setValue(0, 0, 0, (byte) 0); // Block 0 at (0,0)

        blocks = new Block[4];
        for (int i = 0; i < blocks.length; i++) {
            blocks[i] = new Block(8);
        }
        // Set a known chunk in block 1
        blocks[1].getChunkDesc(0, 0).set(0x0042);

        editor.initForLevel(4, 4, 64, blocks.length, 8);
    }

    @Test
    public void placeBlockChangesMapCell() {
        editor.setEditMode(LevelEditorManager.EditMode.BLOCK);
        editor.moveCursorRight(); // Move to (1, 0)
        editor.placeBlock(map, 2);
        assertEquals(2, map.getValue(0, 1, 0) & 0xFF);
    }

    @Test
    public void clearBlockSetsToZero() {
        map.setValue(0, 1, 1, (byte) 5);
        editor.setEditMode(LevelEditorManager.EditMode.BLOCK);
        editor.moveCursorRight();
        editor.moveCursorDown();
        editor.clearCell(map, blocks);
        assertEquals(0, map.getValue(0, 1, 1) & 0xFF);
    }

    @Test
    public void eyedropperInBlockModeSelectsBlockIndex() {
        map.setValue(0, 2, 0, (byte) 3);
        editor.setEditMode(LevelEditorManager.EditMode.BLOCK);
        editor.moveCursorRight();
        editor.moveCursorRight(); // At (2, 0)
        editor.eyedropper(map, blocks);
        assertEquals(3, editor.getPanelSelection());
    }

    @Test
    public void placeChunkUsesCopyOnWrite() {
        map.setValue(0, 0, 0, (byte) 0);
        editor.setEditMode(LevelEditorManager.EditMode.CHUNK);
        // Cursor at (0,0) = chunk (0,0) within block at map (0,0)

        int originalBlockCount = blocks.length;
        Block[] expandedBlocks = editor.placeChunk(map, blocks, 42);

        // Should have created a new block (copy-on-write)
        assertEquals(originalBlockCount + 1, expandedBlocks.length);

        // Map cell should now point to the new block
        int newBlockIndex = map.getValue(0, 0, 0) & 0xFF;
        assertEquals(originalBlockCount, newBlockIndex);

        // The new block's chunk at (0,0) should have the placed chunk index
        assertEquals(42, expandedBlocks[newBlockIndex].getChunkDesc(0, 0).getChunkIndex());
    }

    @Test
    public void eyedropperInChunkModeSelectsChunkIndex() {
        // Put a chunk value in block 0 at position (1,1)
        blocks[0].getChunkDesc(1, 1).set(0x0025); // chunk index = 0x25 = 37
        map.setValue(0, 0, 0, (byte) 0);
        editor.setEditMode(LevelEditorManager.EditMode.CHUNK);
        editor.moveCursorRight(); // chunk x=1
        editor.moveCursorDown();  // chunk y=1
        editor.eyedropper(map, blocks);
        assertEquals(37, editor.getPanelSelection());
    }

    @Test
    public void placeChunkPreservesOtherChunksInBlock() {
        // Set a known chunk in block 0 at position (3, 5)
        blocks[0].getChunkDesc(3, 5).set(0x00FF); // chunk index = 0xFF = 255
        map.setValue(0, 0, 0, (byte) 0);
        editor.setEditMode(LevelEditorManager.EditMode.CHUNK);
        // Cursor at (0,0) = chunk (0,0) within block 0

        Block[] expandedBlocks = editor.placeChunk(map, blocks, 10);

        int newBlockIndex = map.getValue(0, 0, 0) & 0xFF;
        // The other chunk in the cloned block should be preserved
        assertEquals(255, expandedBlocks[newBlockIndex].getChunkDesc(3, 5).getChunkIndex());
        // And the targeted chunk should have the new value
        assertEquals(10, expandedBlocks[newBlockIndex].getChunkDesc(0, 0).getChunkIndex());
    }

    @Test
    public void clearChunkSetsChunkDescToZero() {
        // Set block 0 at cursor position with a non-zero chunk
        blocks[0].getChunkDesc(0, 0).set(0x0042);
        map.setValue(0, 0, 0, (byte) 0);
        editor.setEditMode(LevelEditorManager.EditMode.CHUNK);
        // Cursor at (0,0) targets chunk (0,0) within block at map (0,0)

        editor.clearCell(map, blocks);

        // After clearing, the chunk desc at (0,0) of the block should be zero
        assertEquals(0, blocks[0].getChunkDesc(0, 0).getChunkIndex());
    }

    @Test
    public void placeBlockDoesNothingInChunkMode() {
        map.setValue(0, 0, 0, (byte) 0);
        editor.setEditMode(LevelEditorManager.EditMode.CHUNK);
        editor.placeBlock(map, 3);
        // Should not have changed anything since we're in chunk mode
        assertEquals(0, map.getValue(0, 0, 0) & 0xFF);
    }
}
