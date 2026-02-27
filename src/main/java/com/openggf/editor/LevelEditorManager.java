package com.openggf.editor;

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
     * Returns the number of items in the side panel, depending on the current edit mode.
     */
    private int getPanelItemCount() {
        return switch (editMode) {
            case CHUNK -> chunkCount;
            case BLOCK -> blockCount;
        };
    }

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
