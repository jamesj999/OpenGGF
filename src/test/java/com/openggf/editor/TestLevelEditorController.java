package com.openggf.editor;

import com.openggf.GameLoop;
import com.openggf.control.InputHandler;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameMode;
import com.openggf.game.RuntimeManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_TAB;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;

class TestLevelEditorController {

    @Test
    void inputHandler_mapsDescendAndAscendActionsOntoController() {
        LevelEditorController controller = new LevelEditorController();
        EditorInputHandler handler = new EditorInputHandler(controller);

        controller.selectBlock(7);
        handler.handleAction(EditorInputHandler.Action.DESCEND);

        assertEquals(EditorHierarchyDepth.BLOCK, controller.depth());

        handler.handleAction(EditorInputHandler.Action.ASCEND);

        assertEquals(EditorHierarchyDepth.WORLD, controller.depth());
    }

    @Test
    void inputHandler_updateMapsEscapeToAscendOnController() {
        LevelEditorController controller = new LevelEditorController();
        EditorInputHandler handler = new EditorInputHandler(controller);
        InputHandler inputHandler = new InputHandler();

        controller.selectBlock(7);
        controller.descend();

        assertEquals(EditorHierarchyDepth.BLOCK, controller.depth());

        inputHandler.handleKeyEvent(GLFW_KEY_ESCAPE, GLFW_PRESS);
        handler.update(inputHandler);

        assertEquals(EditorHierarchyDepth.WORLD, controller.depth());
    }

    @Test
    void inputHandler_rejectsUnsupportedPublicActions() {
        LevelEditorController controller = new LevelEditorController();
        EditorInputHandler handler = new EditorInputHandler(controller);

        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class,
                () -> handler.handleAction(EditorInputHandler.Action.APPLY));

        assertEquals("Action APPLY is not implemented yet", exception.getMessage());
    }

    @Test
    void gameLoop_editorModeUpdatesEditorInputHandlerBeforeAdvancingInputState() {
        RuntimeManager.createGameplay();
        try {
            InputHandler inputHandler = new InputHandler();
            GameLoop gameLoop = new GameLoop(inputHandler);
            LevelEditorController controller = new LevelEditorController();
            EditorInputHandler editorInputHandler = new EditorInputHandler(controller);

            gameLoop.setEditorInputHandler(editorInputHandler);
            gameLoop.setGameMode(GameMode.EDITOR);

            controller.selectBlock(7);
            inputHandler.handleKeyEvent(GLFW_KEY_ENTER, GLFW_PRESS);

            gameLoop.step();

            assertEquals(EditorHierarchyDepth.BLOCK, controller.depth());
            assertFalse(inputHandler.isKeyPressed(GLFW_KEY_ENTER));
        } finally {
            RuntimeManager.destroyCurrent();
        }
    }

    @Test
    void gameLoop_shiftTabDoesNotInvokePlaytestToggleHandlerOutsideEditorMode() {
        RuntimeManager.createGameplay();
        try {
            InputHandler inputHandler = new InputHandler();
            GameLoop gameLoop = new GameLoop(inputHandler);
            int[] toggleCount = {0};

            gameLoop.setEditorPlaytestToggleHandler(() -> toggleCount[0]++);
            gameLoop.pause();

            inputHandler.handleKeyEvent(GLFW_KEY_LEFT_SHIFT, GLFW_PRESS);
            inputHandler.handleKeyEvent(GLFW_KEY_TAB, GLFW_PRESS);

            gameLoop.step();

            assertEquals(0, toggleCount[0]);
            assertFalse(inputHandler.isKeyPressed(GLFW_KEY_TAB));
        } finally {
            RuntimeManager.destroyCurrent();
        }
    }

    @Test
    void gameLoop_shiftTabInEditorModeInvokesPlaytestToggleHandler() {
        RuntimeManager.createGameplay();
        try {
            InputHandler inputHandler = new InputHandler();
            GameLoop gameLoop = new GameLoop(inputHandler);
            int[] toggleCount = {0};

            gameLoop.setEditorPlaytestToggleHandler(() -> toggleCount[0]++);
            gameLoop.setGameMode(GameMode.EDITOR);

            inputHandler.handleKeyEvent(GLFW_KEY_LEFT_SHIFT, GLFW_PRESS);
            inputHandler.handleKeyEvent(GLFW_KEY_TAB, GLFW_PRESS);

            gameLoop.step();

            assertEquals(1, toggleCount[0]);
            assertFalse(inputHandler.isKeyPressed(GLFW_KEY_TAB));
        } finally {
            RuntimeManager.destroyCurrent();
        }
    }

    @Test
    void gameLoop_plainTabInEditorModeDoesNotInvokePlaytestToggleHandler() {
        RuntimeManager.createGameplay();
        try {
            InputHandler inputHandler = new InputHandler();
            GameLoop gameLoop = new GameLoop(inputHandler);
            int[] toggleCount = {0};

            gameLoop.setEditorPlaytestToggleHandler(() -> toggleCount[0]++);
            gameLoop.setGameMode(GameMode.EDITOR);

            inputHandler.handleKeyEvent(GLFW_KEY_TAB, GLFW_PRESS);

            gameLoop.step();

            assertEquals(0, toggleCount[0]);
            assertFalse(inputHandler.isKeyPressed(GLFW_KEY_TAB));
        } finally {
            RuntimeManager.destroyCurrent();
        }
    }

    @Test
    void gameLoop_rightShiftTabInEditorModeInvokesPlaytestToggleHandler() {
        RuntimeManager.createGameplay();
        try {
            InputHandler inputHandler = new InputHandler();
            GameLoop gameLoop = new GameLoop(inputHandler);
            int[] toggleCount = {0};

            gameLoop.setEditorPlaytestToggleHandler(() -> toggleCount[0]++);
            gameLoop.setGameMode(GameMode.EDITOR);

            inputHandler.handleKeyEvent(GLFW_KEY_RIGHT_SHIFT, GLFW_PRESS);
            inputHandler.handleKeyEvent(GLFW_KEY_TAB, GLFW_PRESS);

            gameLoop.step();

            assertEquals(1, toggleCount[0]);
            assertFalse(inputHandler.isKeyPressed(GLFW_KEY_TAB));
        } finally {
            RuntimeManager.destroyCurrent();
        }
    }

    @Test
    void gameLoop_editorModeBypassesPauseToggleHandling() {
        RuntimeManager.createGameplay();
        try {
            InputHandler inputHandler = new InputHandler();
            GameLoop gameLoop = new GameLoop(inputHandler);
            int pauseKey = SonicConfigurationService.getInstance().getInt(SonicConfiguration.PAUSE_KEY);

            gameLoop.setGameMode(GameMode.EDITOR);
            inputHandler.handleKeyEvent(pauseKey, GLFW_PRESS);

            gameLoop.step();

            assertFalse(gameLoop.isUserPaused());
            assertFalse(inputHandler.isKeyPressed(pauseKey));
        } finally {
            RuntimeManager.destroyCurrent();
        }
    }

    @Test
    void controller_descendsAndAscendsHierarchyWithBreadcrumbs() {
        LevelEditorController controller = new LevelEditorController();

        assertEquals(EditorHierarchyDepth.WORLD, controller.depth());
        assertEquals("World", controller.breadcrumb());

        controller.selectBlock(12);
        controller.descend();

        assertEquals(EditorHierarchyDepth.BLOCK, controller.depth());
        assertEquals("World > Block 12", controller.breadcrumb());

        controller.selectChunk(3);
        controller.descend();

        assertEquals(EditorHierarchyDepth.CHUNK, controller.depth());
        assertEquals("World > Block 12 > Chunk 3", controller.breadcrumb());

        controller.ascend();

        assertEquals(EditorHierarchyDepth.BLOCK, controller.depth());
        assertEquals("World > Block 12", controller.breadcrumb());

        controller.ascend();

        assertEquals(EditorHierarchyDepth.WORLD, controller.depth());
        assertEquals("World", controller.breadcrumb());
    }

    @Test
    void controller_descendRequiresSelectionAtCurrentDepth() {
        LevelEditorController controller = new LevelEditorController();

        controller.descend();

        assertEquals(EditorHierarchyDepth.WORLD, controller.depth());
        assertEquals("World", controller.breadcrumb());

        assertThrows(IllegalStateException.class, () -> controller.selectChunk(3));

        assertEquals(EditorHierarchyDepth.WORLD, controller.depth());
        assertEquals("World", controller.breadcrumb());

        controller.selectBlock(12);
        controller.descend();

        assertEquals(EditorHierarchyDepth.BLOCK, controller.depth());
        assertEquals("World > Block 12", controller.breadcrumb());

        controller.descend();

        assertEquals(EditorHierarchyDepth.BLOCK, controller.depth());
        assertEquals("World > Block 12", controller.breadcrumb());
    }

    @Test
    void controller_selectBlockAtChunkDepthNormalizesToBlockDepth() {
        LevelEditorController controller = new LevelEditorController();

        controller.selectBlock(12);
        controller.descend();
        controller.selectChunk(3);
        controller.descend();

        assertEquals(EditorHierarchyDepth.CHUNK, controller.depth());
        assertEquals("World > Block 12 > Chunk 3", controller.breadcrumb());

        controller.selectBlock(8);

        assertEquals(EditorHierarchyDepth.BLOCK, controller.depth());
        assertEquals("World > Block 8", controller.breadcrumb());
    }

    @Test
    void controller_rejectsChunkSelectionWithoutSelectedBlock() {
        LevelEditorController controller = new LevelEditorController();

        assertThrows(IllegalStateException.class, () -> controller.selectChunk(3));
    }

    @Test
    void controller_rejectsNegativeSelectionIndices() {
        LevelEditorController controller = new LevelEditorController();

        assertThrows(IllegalArgumentException.class, () -> controller.selectBlock(-1));

        controller.selectBlock(12);

        assertThrows(IllegalArgumentException.class, () -> controller.selectChunk(-1));
    }
}
