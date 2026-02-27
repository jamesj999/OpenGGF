package com.openggf.editor;

import org.junit.Test;
import static org.junit.Assert.*;

public class TestLevelEditorManager {

    private LevelEditorManager createEditor() {
        LevelEditorManager editor = new LevelEditorManager();
        // Initialize with a level grid of 128 blocks wide, 16 blocks tall
        // and 256 chunks, 128 blocks (typical S2 level)
        editor.initForLevel(128, 16, 256, 128, 8);
        return editor;
    }

    @Test
    public void defaultFocusIsGrid() {
        LevelEditorManager editor = createEditor();
        assertEquals(LevelEditorManager.Focus.GRID, editor.getFocus());
    }

    @Test
    public void tabTogglesFocus() {
        LevelEditorManager editor = createEditor();
        editor.toggleFocus();
        assertEquals(LevelEditorManager.Focus.PANEL, editor.getFocus());
        editor.toggleFocus();
        assertEquals(LevelEditorManager.Focus.GRID, editor.getFocus());
    }

    @Test
    public void defaultEditModeIsChunk() {
        LevelEditorManager editor = createEditor();
        assertEquals(LevelEditorManager.EditMode.CHUNK, editor.getEditMode());
    }

    @Test
    public void switchToBlockMode() {
        LevelEditorManager editor = createEditor();
        editor.setEditMode(LevelEditorManager.EditMode.BLOCK);
        assertEquals(LevelEditorManager.EditMode.BLOCK, editor.getEditMode());
    }

    @Test
    public void gridCursorMovesInChunkMode() {
        LevelEditorManager editor = createEditor();
        assertEquals(0, editor.getCursorX());
        assertEquals(0, editor.getCursorY());
        editor.moveCursorRight();
        assertEquals(1, editor.getCursorX());
        editor.moveCursorDown();
        assertEquals(1, editor.getCursorY());
    }

    @Test
    public void gridCursorClampsToLevelBounds() {
        LevelEditorManager editor = createEditor();
        editor.moveCursorLeft();
        assertEquals(0, editor.getCursorX());
        editor.moveCursorUp();
        assertEquals(0, editor.getCursorY());
    }

    @Test
    public void gridCursorInBlockModeMovesInBlockSteps() {
        LevelEditorManager editor = createEditor();
        editor.setEditMode(LevelEditorManager.EditMode.BLOCK);
        editor.moveCursorRight();
        assertEquals(1, editor.getCursorX());
    }

    @Test
    public void panelCursorMoves() {
        LevelEditorManager editor = createEditor();
        editor.toggleFocus();
        assertEquals(0, editor.getPanelSelection());
        editor.movePanelSelectionDown();
        assertEquals(1, editor.getPanelSelection());
    }

    @Test
    public void panelSelectionClampsToItemCount() {
        LevelEditorManager editor = createEditor();
        editor.toggleFocus();
        editor.movePanelSelectionUp();
        assertEquals(0, editor.getPanelSelection());
    }

    @Test
    public void cursorPixelPositionInChunkMode() {
        LevelEditorManager editor = createEditor();
        editor.moveCursorRight();
        editor.moveCursorRight();
        editor.moveCursorDown();
        assertEquals(32, editor.getCursorPixelX()); // 2 * 16
        assertEquals(16, editor.getCursorPixelY()); // 1 * 16
    }

    @Test
    public void cursorPixelPositionInBlockMode() {
        LevelEditorManager editor = createEditor();
        editor.setEditMode(LevelEditorManager.EditMode.BLOCK);
        editor.moveCursorRight();
        assertEquals(128, editor.getCursorPixelX()); // 1 * 128
    }

    @Test
    public void switchingModeConvertsCursorPosition() {
        LevelEditorManager editor = createEditor();
        // Move to chunk position (16, 8) = block position (2, 1)
        for (int i = 0; i < 16; i++) editor.moveCursorRight();
        for (int i = 0; i < 8; i++) editor.moveCursorDown();
        assertEquals(16, editor.getCursorX());
        assertEquals(8, editor.getCursorY());

        editor.setEditMode(LevelEditorManager.EditMode.BLOCK);
        assertEquals(2, editor.getCursorX()); // 16 / 8
        assertEquals(1, editor.getCursorY()); // 8 / 8
    }
}
