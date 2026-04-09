package com.openggf.editor;

import com.openggf.GameLoop;
import com.openggf.control.InputHandler;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameMode;
import com.openggf.game.RuntimeManager;
import com.openggf.game.session.EditorCursorState;
import com.openggf.level.AbstractLevel;
import com.openggf.level.Block;
import com.openggf.level.Chunk;
import com.openggf.level.Map;
import com.openggf.level.MutableLevel;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.SolidTile;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.rings.RingSpawn;
import com.openggf.level.rings.RingSpriteSheet;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT;
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
    void inputHandler_updateMovesWorldCursorWithHeldArrowKeys() {
        LevelEditorController controller = new LevelEditorController();
        EditorInputHandler handler = new EditorInputHandler(controller);
        InputHandler input = new InputHandler();

        controller.setWorldCursor(new EditorCursorState(100, 200));
        input.handleKeyEvent(GLFW_KEY_RIGHT, GLFW_PRESS);
        input.handleKeyEvent(GLFW_KEY_DOWN, GLFW_PRESS);

        handler.update(input);

        assertEquals(103, controller.worldCursor().x());
        assertEquals(203, controller.worldCursor().y());
    }

    @Test
    void inputHandler_updateMovesBlockGridSelectionInsteadOfWorldCursorOutsideWorldDepth() {
        LevelEditorController controller = new LevelEditorController();
        EditorInputHandler handler = new EditorInputHandler(controller);
        InputHandler input = new InputHandler();

        controller.setWorldCursor(new EditorCursorState(100, 200));
        controller.selectBlock(12);
        controller.descend();
        input.handleKeyEvent(GLFW_KEY_RIGHT, GLFW_PRESS);

        handler.update(input);

        assertEquals(new EditorCursorState(100, 200), controller.worldCursor());
        assertEquals(1, controller.selectedBlockCellX());
        assertEquals(0, controller.selectedBlockCellY());
    }

    @Test
    void inputHandler_publicActionsMatchImplementedMvpSurface() {
        assertArrayEquals(
                new EditorInputHandler.Action[] {
                        EditorInputHandler.Action.DESCEND,
                        EditorInputHandler.Action.ASCEND
                },
                EditorInputHandler.Action.values());
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
    void gameLoop_editorModeUpdatesCursorMovementBeforeAdvancingInputState() {
        RuntimeManager.createGameplay();
        try {
            InputHandler inputHandler = new InputHandler();
            GameLoop gameLoop = new GameLoop(inputHandler);
            LevelEditorController controller = new LevelEditorController();
            EditorInputHandler editorInputHandler = new EditorInputHandler(controller);

            gameLoop.setEditorInputHandler(editorInputHandler);
            gameLoop.setGameMode(GameMode.EDITOR);
            controller.setWorldCursor(new EditorCursorState(100, 200));
            inputHandler.handleKeyEvent(GLFW_KEY_RIGHT, GLFW_PRESS);

            gameLoop.step();

            assertEquals(103, controller.worldCursor().x());
            assertFalse(inputHandler.isKeyPressed(GLFW_KEY_RIGHT));
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

    @Test
    void controller_movesWorldCursorInWorldDepth() {
        LevelEditorController controller = new LevelEditorController();

        controller.setWorldCursor(new EditorCursorState(320, 448));
        controller.moveWorldCursor(-3, 6);

        assertEquals(317, controller.worldCursor().x());
        assertEquals(454, controller.worldCursor().y());
    }

    @Test
    void moveWorldCursor_clampsToAttachedLevelBounds() {
        LevelEditorController controller = new LevelEditorController();
        controller.attachLevel(createMutableLevel(4, 3, 2, 2));
        controller.setWorldCursor(new EditorCursorState(0, 0));

        controller.moveWorldCursor(-64, -32);
        assertEquals(0, controller.worldCursor().x());
        assertEquals(0, controller.worldCursor().y());

        controller.moveWorldCursor(9999, 9999);
        assertEquals(255, controller.worldCursor().x());
        assertEquals(191, controller.worldCursor().y());
    }

    @Test
    void moveActiveSelection_inWorldDepthUsesClampedCursorMovement() {
        LevelEditorController controller = new LevelEditorController();
        controller.attachLevel(createMutableLevel(4, 3, 2, 2));
        controller.setWorldCursor(new EditorCursorState(254, 190));

        controller.moveActiveSelection(3, 3);

        assertEquals(255, controller.worldCursor().x());
        assertEquals(191, controller.worldCursor().y());
    }

    @Test
    void controller_arrowNavigationInBlockDepthMovesChunkSelectionInsteadOfWorldCursor() {
        LevelEditorController controller = new LevelEditorController();

        controller.setWorldCursor(new EditorCursorState(320, 448));
        controller.selectBlock(12);
        controller.descend();
        controller.moveActiveSelection(1, 0);

        assertEquals(new EditorCursorState(320, 448), controller.worldCursor());
        assertEquals(1, controller.selectedBlockCellX());
        assertEquals(0, controller.selectedBlockCellY());
    }

    @Test
    void controller_arrowNavigationInChunkDepthMovesPatternSelectionInsteadOfWorldCursor() {
        LevelEditorController controller = new LevelEditorController();

        controller.setWorldCursor(new EditorCursorState(320, 448));
        controller.selectBlock(12);
        controller.descend();
        controller.selectChunk(3);
        controller.descend();
        controller.moveActiveSelection(1, 1);

        assertEquals(new EditorCursorState(320, 448), controller.worldCursor());
        assertEquals(1, controller.selectedChunkCellX());
        assertEquals(1, controller.selectedChunkCellY());
    }

    @Test
    void controller_attachLevelResetsNavigationState() {
        LevelEditorController controller = new LevelEditorController();
        controller.setWorldCursor(new EditorCursorState(320, 448));
        controller.selectBlock(12);
        controller.descend();
        controller.selectChunk(3);
        controller.descend();
        controller.moveActiveSelection(1, 1);

        MutableLevel level = MutableLevel.snapshot(new NavigationResetSourceLevel());

        controller.attachLevel(level);

        assertEquals(new EditorCursorState(0, 0), controller.worldCursor());
        assertEquals(EditorHierarchyDepth.WORLD, controller.depth());
        assertEquals(2, controller.blockGridSide());
        assertEquals(0, controller.selectedBlockCellX());
        assertEquals(0, controller.selectedBlockCellY());
        assertEquals(0, controller.selectedChunkCellX());
        assertEquals(0, controller.selectedChunkCellY());
        assertEquals("World", controller.breadcrumb());
    }

    @Test
    void controller_clampsBlockSelectionToControllerOwnedGridBounds() {
        LevelEditorController controller = new LevelEditorController();
        controller.attachLevel(MutableLevel.snapshot(new NavigationResetSourceLevel()));
        controller.selectBlock(12);
        controller.descend();

        controller.moveActiveSelection(-1, -1);
        assertEquals(0, controller.selectedBlockCellX());
        assertEquals(0, controller.selectedBlockCellY());

        controller.moveActiveSelection(10, 10);
        assertEquals(1, controller.selectedBlockCellX());
        assertEquals(1, controller.selectedBlockCellY());
    }

    @Test
    void controller_clampsChunkSelectionToControllerOwnedGridBounds() {
        LevelEditorController controller = new LevelEditorController();
        controller.attachLevel(MutableLevel.snapshot(new NavigationResetSourceLevel()));
        controller.selectBlock(12);
        controller.descend();
        controller.selectChunk(3);
        controller.descend();

        controller.moveActiveSelection(-1, -1);
        assertEquals(0, controller.selectedChunkCellX());
        assertEquals(0, controller.selectedChunkCellY());

        controller.moveActiveSelection(10, 10);
        assertEquals(1, controller.selectedChunkCellX());
        assertEquals(1, controller.selectedChunkCellY());
    }

    private static MutableLevel createMutableLevel(int mapWidth, int mapHeight, int blockGridSide, int blockCount) {
        TestLevel level = new TestLevel(mapWidth, mapHeight, blockGridSide, blockCount);
        return MutableLevel.snapshot(level);
    }

    private static final class TestLevel extends AbstractLevel {
        private TestLevel(int mapWidth, int mapHeight, int blockGridSide, int blockCount) {
            super(0);
            patternCount = 1;
            patterns = new Pattern[] { new Pattern() };
            chunkCount = 1;
            chunks = new Chunk[] { new Chunk() };
            this.blockCount = blockCount;
            blocks = new Block[blockCount];
            for (int i = 0; i < blockCount; i++) {
                blocks[i] = new Block(blockGridSide);
            }
            solidTileCount = 0;
            solidTiles = new SolidTile[0];
            map = new Map(1, mapWidth, mapHeight);
            palettes = new Palette[] {
                    new Palette(), new Palette(), new Palette(), new Palette()
            };
            objects = List.of();
            rings = List.of();
            minX = 0;
            maxX = 0;
            minY = 0;
            maxY = 0;
        }

        @Override
        public int getChunksPerBlockSide() {
            return blocks.length > 0 ? blocks[0].getGridSide() : 0;
        }

        @Override
        public int getBlockPixelSize() {
            return getChunksPerBlockSide() * 32;
        }

        @Override
        public int getPaletteCount() {
            return palettes.length;
        }

        @Override
        public Palette getPalette(int index) {
            return palettes[index];
        }

        @Override
        public int getPatternCount() {
            return patternCount;
        }

        @Override
        public Pattern getPattern(int index) {
            return patterns[index];
        }

        @Override
        public int getChunkCount() {
            return chunkCount;
        }

        @Override
        public Chunk getChunk(int index) {
            return chunks[index];
        }

        @Override
        public int getBlockCount() {
            return blockCount;
        }

        @Override
        public Block getBlock(int index) {
            return blocks[index];
        }

        @Override
        public SolidTile getSolidTile(int index) {
            return solidTiles[index];
        }

        @Override
        public Map getMap() {
            return map;
        }

        @Override
        public List<ObjectSpawn> getObjects() {
            return List.of();
        }

        @Override
        public List<RingSpawn> getRings() {
            return List.of();
        }

        @Override
        public RingSpriteSheet getRingSpriteSheet() {
            return null;
        }

        @Override
        public int getMinX() {
            return 0;
        }

        @Override
        public int getMaxX() {
            return 0;
        }

        @Override
        public int getMinY() {
            return 0;
        }

        @Override
        public int getMaxY() {
            return 0;
        }

        @Override
        public int getZoneIndex() {
            return 0;
        }
    }

    private static final class NavigationResetSourceLevel extends AbstractLevel {
        private NavigationResetSourceLevel() {
            super(0);
            patternCount = 0;
            patterns = new Pattern[0];
            chunkCount = 0;
            chunks = new Chunk[0];
            blockCount = 0;
            blocks = new Block[0];
            solidTileCount = 0;
            solidTiles = new SolidTile[0];
            map = new Map(1, 1, 1);
            palettes = new Palette[0];
            objects = List.of();
            rings = List.of();
            minX = 0;
            maxX = 64;
            minY = 0;
            maxY = 64;
        }

        @Override
        public int getChunksPerBlockSide() {
            return 2;
        }

        @Override
        public int getBlockPixelSize() {
            return 32;
        }

        @Override
        public int getPaletteCount() {
            return 0;
        }

        @Override
        public Palette getPalette(int index) {
            return null;
        }

        @Override
        public int getPatternCount() {
            return 0;
        }

        @Override
        public Pattern getPattern(int index) {
            return null;
        }

        @Override
        public int getChunkCount() {
            return 0;
        }

        @Override
        public Chunk getChunk(int index) {
            return null;
        }

        @Override
        public int getBlockCount() {
            return 0;
        }

        @Override
        public Block getBlock(int index) {
            return null;
        }

        @Override
        public SolidTile getSolidTile(int index) {
            return null;
        }

        @Override
        public Map getMap() {
            return map;
        }

        @Override
        public List<ObjectSpawn> getObjects() {
            return List.of();
        }

        @Override
        public List<RingSpawn> getRings() {
            return List.of();
        }

        @Override
        public RingSpriteSheet getRingSpriteSheet() {
            return null;
        }

        @Override
        public int getMinX() {
            return 0;
        }

        @Override
        public int getMaxX() {
            return 64;
        }

        @Override
        public int getMinY() {
            return 0;
        }

        @Override
        public int getMaxY() {
            return 64;
        }

        @Override
        public int getZoneIndex() {
            return 0;
        }
    }
}
