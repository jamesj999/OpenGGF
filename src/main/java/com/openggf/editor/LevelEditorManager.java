package com.openggf.editor;

import com.openggf.Control.InputHandler;
import com.openggf.graphics.PixelFont;
import com.openggf.graphics.TexturedQuadRenderer;
import com.openggf.level.Block;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;

import java.util.Arrays;

/**
 * Coordinator singleton for the in-engine level editor.
 * Holds all editor state: cursor position, focus mode, edit mode, and panel selection.
 * Does NOT handle rendering or input directly -- those are separate classes.
 */
public class LevelEditorManager {

    /** Which UI region currently has input focus. */
    public enum Focus {
        /** The main level grid (cursor moves over chunks/blocks). */
        GRID,
        /** The side panel (chunk/block palette selection). */
        PANEL
    }

    /** Granularity of editing operations. */
    public enum EditMode {
        /** Edit individual 16x16 chunks. */
        CHUNK,
        /** Edit 128x128 blocks (groups of chunks). */
        BLOCK
    }

    // -- Singleton ----------------------------------------------------------

    private static LevelEditorManager instance;

    public static synchronized LevelEditorManager getInstance() {
        if (instance == null) {
            instance = new LevelEditorManager();
        }
        return instance;
    }

    /**
     * Resets the singleton instance. Used for testing to ensure clean state.
     */
    public static synchronized void resetInstance() {
        instance = null;
    }

    // -- Level dimensions (set by initForLevel) -----------------------------

    /** Width of the level map in blocks (128x128 areas). */
    private int mapWidthBlocks;

    /** Height of the level map in blocks. */
    private int mapHeightBlocks;

    /** Total number of unique chunk definitions available. */
    private int chunkCount;

    /** Total number of unique block definitions available. */
    private int blockCount;

    /** Number of chunks per block edge (typically 8 for 128/16). */
    private int chunksPerBlock;

    // -- Editor state -------------------------------------------------------

    private Focus focus = Focus.GRID;
    private EditMode editMode = EditMode.CHUNK;

    /** Grid cursor X position (in current edit-mode units). */
    private int cursorX;

    /** Grid cursor Y position (in current edit-mode units). */
    private int cursorY;

    /** Currently selected index in the side panel. */
    private int panelSelection;

    // -- Rendering and input subsystems -------------------------------------

    private EditorInputHandler inputHandler;
    private GridOverlayRenderer gridOverlayRenderer;
    private TooltipBarRenderer tooltipBarRenderer;
    private ChunkPanelRenderer chunkPanelRenderer;
    private boolean initialized;

    // -- Live level reference -----------------------------------------------

    private Level currentLevel;

    // -- Editor camera position (pixels) ------------------------------------

    private int cameraX;
    private int cameraY;

    // -- Construction -------------------------------------------------------

    public LevelEditorManager() {
        // Defaults are fine for pre-init state; call initForLevel() before use.
    }

    /**
     * Initialise the editor for a loaded level.
     *
     * @param mapWidthBlocks  width of the level in blocks
     * @param mapHeightBlocks height of the level in blocks
     * @param chunkCount      number of unique chunk definitions
     * @param blockCount      number of unique block definitions
     * @param chunksPerBlock  chunks per block edge (e.g. 8 for 128px/16px)
     */
    public void initForLevel(int mapWidthBlocks, int mapHeightBlocks,
                             int chunkCount, int blockCount, int chunksPerBlock) {
        this.mapWidthBlocks = mapWidthBlocks;
        this.mapHeightBlocks = mapHeightBlocks;
        this.chunkCount = chunkCount;
        this.blockCount = blockCount;
        this.chunksPerBlock = chunksPerBlock;

        // Reset editor state
        this.focus = Focus.GRID;
        this.editMode = EditMode.CHUNK;
        this.cursorX = 0;
        this.cursorY = 0;
        this.panelSelection = 0;
    }

    // -- Focus --------------------------------------------------------------

    public Focus getFocus() {
        return focus;
    }

    public void toggleFocus() {
        focus = (focus == Focus.GRID) ? Focus.PANEL : Focus.GRID;
    }

    // -- Edit mode ----------------------------------------------------------

    public EditMode getEditMode() {
        return editMode;
    }

    /**
     * Switch edit mode. When switching between CHUNK and BLOCK mode the cursor
     * position is converted so it stays over the same area of the level.
     */
    public void setEditMode(EditMode newMode) {
        if (newMode == editMode) {
            return;
        }
        if (editMode == EditMode.CHUNK && newMode == EditMode.BLOCK) {
            // Chunk -> Block: divide by chunksPerBlock (integer division floors)
            cursorX /= chunksPerBlock;
            cursorY /= chunksPerBlock;
        } else if (editMode == EditMode.BLOCK && newMode == EditMode.CHUNK) {
            // Block -> Chunk: multiply by chunksPerBlock
            cursorX *= chunksPerBlock;
            cursorY *= chunksPerBlock;
        }
        editMode = newMode;
        // Clamp panel selection to new mode's item count
        int maxSel = getPanelItemCount() - 1;
        if (maxSel >= 0 && panelSelection > maxSel) {
            panelSelection = maxSel;
        }
    }

    // -- Grid cursor --------------------------------------------------------

    public int getCursorX() {
        return cursorX;
    }

    public int getCursorY() {
        return cursorY;
    }

    public void moveCursorRight() {
        int maxX = getMaxCursorX();
        if (cursorX < maxX) {
            cursorX++;
        }
    }

    public void moveCursorLeft() {
        if (cursorX > 0) {
            cursorX--;
        }
    }

    public void moveCursorDown() {
        int maxY = getMaxCursorY();
        if (cursorY < maxY) {
            cursorY++;
        }
    }

    public void moveCursorUp() {
        if (cursorY > 0) {
            cursorY--;
        }
    }

    private int getMaxCursorX() {
        return switch (editMode) {
            case CHUNK -> mapWidthBlocks * chunksPerBlock - 1;
            case BLOCK -> mapWidthBlocks - 1;
        };
    }

    private int getMaxCursorY() {
        return switch (editMode) {
            case CHUNK -> mapHeightBlocks * chunksPerBlock - 1;
            case BLOCK -> mapHeightBlocks - 1;
        };
    }

    // -- Pixel position helpers ---------------------------------------------

    /**
     * Returns the pixel X position of the cursor's top-left corner in level space.
     */
    public int getCursorPixelX() {
        return cursorX * getCursorCellSize();
    }

    /**
     * Returns the pixel Y position of the cursor's top-left corner in level space.
     */
    public int getCursorPixelY() {
        return cursorY * getCursorCellSize();
    }

    /**
     * Returns the size of one cursor cell in pixels.
     * 16 for chunk mode, chunksPerBlock * 16 for block mode.
     */
    public int getCursorCellSize() {
        return switch (editMode) {
            case CHUNK -> 16;
            case BLOCK -> chunksPerBlock * 16;
        };
    }

    // -- Panel selection ----------------------------------------------------

    public int getPanelSelection() {
        return panelSelection;
    }

    public void movePanelSelectionDown() {
        int maxSelection = getPanelItemCount() - 1;
        if (panelSelection < maxSelection) {
            panelSelection++;
        }
    }

    public void movePanelSelectionUp() {
        if (panelSelection > 0) {
            panelSelection--;
        }
    }

    /**
     * Move the panel selection up by {@code count} items, clamping at 0.
     */
    public void movePanelSelectionPageUp(int count) {
        panelSelection = Math.max(0, panelSelection - count);
    }

    /**
     * Move the panel selection down by {@code count} items, clamping at max.
     */
    public void movePanelSelectionPageDown(int count) {
        int maxSelection = getPanelItemCount() - 1;
        panelSelection = Math.min(maxSelection, panelSelection + count);
    }

    /**
     * Returns the number of items in the side panel, depending on the current edit mode.
     */
    private int getPanelItemCount() {
        return switch (editMode) {
            case CHUNK -> chunkCount;
            case BLOCK -> blockCount;
        };
    }

    // -- Edit operations ----------------------------------------------------

    /**
     * In BLOCK mode, sets the map cell at the current cursor position to the
     * given block index. Does nothing if the current edit mode is not BLOCK.
     *
     * @param map        the level map (layer 0 = foreground)
     * @param blockIndex the block index to place
     */
    public void placeBlock(com.openggf.level.Map map, int blockIndex) {
        if (editMode != EditMode.BLOCK) {
            return;
        }
        map.setValue(0, cursorX, cursorY, (byte) blockIndex);
    }

    /**
     * In CHUNK mode, places a chunk using copy-on-write semantics:
     * <ol>
     *   <li>Determines which block and chunk-within-block from cursor position</li>
     *   <li>Clones the block via {@link Block#copy()}</li>
     *   <li>Modifies the chunk in the clone</li>
     *   <li>Appends the clone to the blocks array</li>
     *   <li>Updates the map cell to point to the new block</li>
     * </ol>
     *
     * @param map        the level map
     * @param blocks     the current block definitions array
     * @param chunkIndex the chunk index to place
     * @return the expanded blocks array (caller must replace their reference)
     */
    public Block[] placeChunk(com.openggf.level.Map map, Block[] blocks, int chunkIndex) {
        // Determine which block the cursor is inside
        int blockX = cursorX / chunksPerBlock;
        int blockY = cursorY / chunksPerBlock;

        // Determine which chunk within that block
        int chunkX = cursorX % chunksPerBlock;
        int chunkY = cursorY % chunksPerBlock;

        // Look up the current block index from the map
        int currentBlockIndex = map.getValue(0, blockX, blockY) & 0xFF;

        // Clone the block (copy-on-write)
        Block clone = blocks[currentBlockIndex].copy();

        // Modify the targeted chunk in the clone
        clone.getChunkDesc(chunkX, chunkY).set(chunkIndex);

        // Expand the blocks array and append the clone
        int newIndex = blocks.length;
        Block[] expanded = Arrays.copyOf(blocks, blocks.length + 1);
        expanded[newIndex] = clone;

        // Update the map cell to point to the new block
        map.setValue(0, blockX, blockY, (byte) newIndex);

        // Update the block count to include the new block
        this.blockCount = expanded.length;

        return expanded;
    }

    /**
     * Clears the cell at the current cursor position.
     * <ul>
     *   <li>BLOCK mode: sets the map cell to 0</li>
     *   <li>CHUNK mode: sets the chunk descriptor to 0 in place</li>
     * </ul>
     *
     * @param map    the level map
     * @param blocks the current block definitions array
     */
    public void clearCell(com.openggf.level.Map map, Block[] blocks) {
        switch (editMode) {
            case BLOCK -> map.setValue(0, cursorX, cursorY, (byte) 0);
            case CHUNK -> {
                int blockX = cursorX / chunksPerBlock;
                int blockY = cursorY / chunksPerBlock;
                int chunkX = cursorX % chunksPerBlock;
                int chunkY = cursorY % chunksPerBlock;
                int blockIndex = map.getValue(0, blockX, blockY) & 0xFF;
                blocks[blockIndex].getChunkDesc(chunkX, chunkY).set(0);
            }
        }
    }

    /**
     * Picks the value under the cursor and sets it as the current panel selection.
     * <ul>
     *   <li>BLOCK mode: reads the block index from the map cell</li>
     *   <li>CHUNK mode: reads the chunk index from the block at cursor position</li>
     * </ul>
     *
     * @param map    the level map
     * @param blocks the current block definitions array
     */
    public void eyedropper(com.openggf.level.Map map, Block[] blocks) {
        switch (editMode) {
            case BLOCK -> panelSelection = map.getValue(0, cursorX, cursorY) & 0xFF;
            case CHUNK -> {
                int blockX = cursorX / chunksPerBlock;
                int blockY = cursorY / chunksPerBlock;
                int chunkX = cursorX % chunksPerBlock;
                int chunkY = cursorY % chunksPerBlock;
                int blockIndex = map.getValue(0, blockX, blockY) & 0xFF;
                panelSelection = blocks[blockIndex].getChunkDesc(chunkX, chunkY).getChunkIndex();
            }
        }
    }

    // -- Lifecycle (init / update / draw) ------------------------------------

    /**
     * Initialize rendering and input subsystems.
     * Must be called after GL context is available.
     */
    public void initialize(PixelFont font, TexturedQuadRenderer quadRenderer) {
        this.inputHandler = new EditorInputHandler(this);
        this.gridOverlayRenderer = new GridOverlayRenderer(this);
        this.tooltipBarRenderer = new TooltipBarRenderer(this);
        this.chunkPanelRenderer = new ChunkPanelRenderer(this);
        this.tooltipBarRenderer.init(font, quadRenderer);
        this.chunkPanelRenderer.init(font, quadRenderer);
        this.initialized = true;
    }

    public boolean isInitialized() { return initialized; }

    /**
     * Set the level for the editor, initializing dimensions from the Level object.
     */
    public void setLevel(Level level) {
        this.currentLevel = level;
        this.chunkPanelRenderer.setLevel(level);
        int mapW = level.getLayerWidthBlocks(0);
        int mapH = level.getLayerHeightBlocks(0);
        // chunksPerBlock is typically 8 (128px block / 16px chunk)
        initForLevel(mapW, mapH, level.getChunkCount(), level.getBlockCount(),
                level.getChunksPerBlockSide());
    }

    /**
     * Process one frame of editor logic: input, edit actions, and camera follow.
     */
    public void update(InputHandler input) {
        EditorInputHandler.EditorAction action = this.inputHandler.update(input);

        if (action != null && currentLevel != null) {
            com.openggf.level.Map map = currentLevel.getMap();
            if (map != null) {
                switch (action) {
                    case PLACE -> {
                        if (editMode == EditMode.BLOCK) {
                            placeBlock(map, panelSelection);
                        } else {
                            // In-place chunk edit: modify the existing block directly.
                            // This is simpler than copy-on-write for MVP.
                            placeChunkInPlace(map, panelSelection);
                        }
                        LevelManager.getInstance().invalidateForegroundTilemap();
                    }
                    case CLEAR -> {
                        clearCell(map, currentLevel);
                        LevelManager.getInstance().invalidateForegroundTilemap();
                    }
                    case EYEDROP -> {
                        eyedropperFromLevel(map, currentLevel);
                    }
                }
            }
        }

        updateCameraFollow(320, 224);
    }

    /**
     * In-place chunk edit: directly modifies the chunk descriptor in the existing block.
     * Simpler than copy-on-write for an in-memory MVP editor.
     */
    private void placeChunkInPlace(com.openggf.level.Map map, int chunkIndex) {
        int blockX = cursorX / chunksPerBlock;
        int blockY = cursorY / chunksPerBlock;
        int chunkX = cursorX % chunksPerBlock;
        int chunkY = cursorY % chunksPerBlock;
        int blockIndex = map.getValue(0, blockX, blockY) & 0xFF;
        if (blockIndex >= currentLevel.getBlockCount()) return;
        Block block = currentLevel.getBlock(blockIndex);
        if (block != null) {
            block.getChunkDesc(chunkX, chunkY).set(chunkIndex);
        }
    }

    /**
     * Clears the cell at the current cursor position using the live level.
     */
    private void clearCell(com.openggf.level.Map map, Level level) {
        switch (editMode) {
            case BLOCK -> map.setValue(0, cursorX, cursorY, (byte) 0);
            case CHUNK -> {
                int blockX = cursorX / chunksPerBlock;
                int blockY = cursorY / chunksPerBlock;
                int chunkX = cursorX % chunksPerBlock;
                int chunkY = cursorY % chunksPerBlock;
                int blockIndex = map.getValue(0, blockX, blockY) & 0xFF;
                if (blockIndex >= level.getBlockCount()) return;
                Block block = level.getBlock(blockIndex);
                if (block != null) {
                    block.getChunkDesc(chunkX, chunkY).set(0);
                }
            }
        }
    }

    /**
     * Picks the value under the cursor and sets it as the current panel selection.
     */
    private void eyedropperFromLevel(com.openggf.level.Map map, Level level) {
        switch (editMode) {
            case BLOCK -> panelSelection = map.getValue(0, cursorX, cursorY) & 0xFF;
            case CHUNK -> {
                int blockX = cursorX / chunksPerBlock;
                int blockY = cursorY / chunksPerBlock;
                int chunkX = cursorX % chunksPerBlock;
                int chunkY = cursorY % chunksPerBlock;
                int blockIndex = map.getValue(0, blockX, blockY) & 0xFF;
                if (blockIndex >= level.getBlockCount()) return;
                Block block = level.getBlock(blockIndex);
                if (block != null) {
                    panelSelection = block.getChunkDesc(chunkX, chunkY).getChunkIndex();
                }
            }
        }
    }

    /**
     * Keep cursor visible by scrolling camera when cursor moves out of view.
     * @param viewW viewport width in game pixels
     * @param viewH viewport height in game pixels
     */
    private void updateCameraFollow(int viewW, int viewH) {
        int cursorPx = getCursorPixelX();
        int cursorPy = getCursorPixelY();
        int cellSize = getCursorCellSize();

        // Scroll right if cursor goes past right edge
        if (cursorPx + cellSize > cameraX + viewW) {
            cameraX = cursorPx + cellSize - viewW;
        }
        // Scroll left if cursor goes past left edge
        if (cursorPx < cameraX) {
            cameraX = cursorPx;
        }
        // Scroll down if cursor goes past bottom edge
        if (cursorPy + cellSize > cameraY + viewH) {
            cameraY = cursorPy + cellSize - viewH;
        }
        // Scroll up if cursor goes past top edge
        if (cursorPy < cameraY) {
            cameraY = cursorPy;
        }

        // Clamp camera to level bounds
        int levelW = mapWidthBlocks * chunksPerBlock * 16;
        int levelH = mapHeightBlocks * chunksPerBlock * 16;
        cameraX = Math.max(0, Math.min(cameraX, Math.max(0, levelW - viewW)));
        cameraY = Math.max(0, Math.min(cameraY, Math.max(0, levelH - viewH)));
    }

    // -- Renderer accessors -------------------------------------------------

    public GridOverlayRenderer getGridOverlayRenderer() { return gridOverlayRenderer; }
    public TooltipBarRenderer getTooltipBarRenderer() { return tooltipBarRenderer; }
    public ChunkPanelRenderer getChunkPanelRenderer() { return chunkPanelRenderer; }
    public int getCameraX() { return cameraX; }
    public int getCameraY() { return cameraY; }

    // -- Accessors for level dimensions -------------------------------------

    public int getMapWidthBlocks() {
        return mapWidthBlocks;
    }

    public int getMapHeightBlocks() {
        return mapHeightBlocks;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public int getBlockCount() {
        return blockCount;
    }

    public int getChunksPerBlock() {
        return chunksPerBlock;
    }
}
