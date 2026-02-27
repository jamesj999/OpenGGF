package com.openggf.editor;

import com.openggf.Control.InputHandler;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Translates keyboard input into level editor actions.
 * Called each frame from the editor's update loop.
 */
public class EditorInputHandler {

    private static final int KEY_PLACE = GLFW_KEY_SPACE;
    private static final int KEY_CLEAR = GLFW_KEY_DELETE;
    private static final int KEY_EYEDROP = GLFW_KEY_E;
    private static final int KEY_TOGGLE_FOCUS = GLFW_KEY_TAB;
    private static final int KEY_BLOCK_MODE = GLFW_KEY_B;
    private static final int KEY_CHUNK_MODE = GLFW_KEY_C;
    private static final int KEY_CONFIRM = GLFW_KEY_ENTER;
    private static final int KEY_PAGE_UP = GLFW_KEY_PAGE_UP;
    private static final int KEY_PAGE_DOWN = GLFW_KEY_PAGE_DOWN;

    private final LevelEditorManager editor;

    public EditorInputHandler(LevelEditorManager editor) {
        this.editor = editor;
    }

    /**
     * Process one frame of input. Returns an action if a data-mutating
     * operation was requested, or null for navigation-only input.
     */
    public EditorAction update(InputHandler input) {
        // Mode switches (available in both focus states)
        if (input.isKeyPressed(KEY_BLOCK_MODE)) {
            editor.setEditMode(LevelEditorManager.EditMode.BLOCK);
        }
        if (input.isKeyPressed(KEY_CHUNK_MODE)) {
            editor.setEditMode(LevelEditorManager.EditMode.CHUNK);
        }
        if (input.isKeyPressed(KEY_TOGGLE_FOCUS)) {
            editor.toggleFocus();
        }

        if (editor.getFocus() == LevelEditorManager.Focus.GRID) {
            return updateGridInput(input);
        } else {
            updatePanelInput(input);
            return null;
        }
    }

    private EditorAction updateGridInput(InputHandler input) {
        // Navigation
        if (input.isKeyPressed(GLFW_KEY_LEFT))  editor.moveCursorLeft();
        if (input.isKeyPressed(GLFW_KEY_RIGHT)) editor.moveCursorRight();
        if (input.isKeyPressed(GLFW_KEY_UP))    editor.moveCursorUp();
        if (input.isKeyPressed(GLFW_KEY_DOWN))  editor.moveCursorDown();

        // Actions
        if (input.isKeyPressed(KEY_PLACE) || input.isKeyPressed(KEY_CONFIRM)) {
            return EditorAction.PLACE;
        }
        if (input.isKeyPressed(KEY_CLEAR)) {
            return EditorAction.CLEAR;
        }
        if (input.isKeyPressed(KEY_EYEDROP)) {
            return EditorAction.EYEDROP;
        }

        return null;
    }

    private void updatePanelInput(InputHandler input) {
        if (input.isKeyPressed(GLFW_KEY_UP))    editor.movePanelSelectionUp();
        if (input.isKeyPressed(GLFW_KEY_DOWN))  editor.movePanelSelectionDown();
        if (input.isKeyPressed(KEY_PAGE_UP))    editor.movePanelSelectionPageUp(8);
        if (input.isKeyPressed(KEY_PAGE_DOWN))  editor.movePanelSelectionPageDown(8);

        // Confirm selection and return to grid
        if (input.isKeyPressed(KEY_CONFIRM)) {
            editor.toggleFocus();
        }
    }

    public enum EditorAction {
        PLACE, CLEAR, EYEDROP
    }
}
