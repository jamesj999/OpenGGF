package com.openggf.editor;

import com.openggf.editor.commands.PlaceBlockCommand;
import com.openggf.level.Block;
import com.openggf.level.Chunk;
import com.openggf.level.MutableLevel;
import com.openggf.game.session.EditorCursorState;

import java.util.Objects;

public final class LevelEditorController {
    private final EditorHistory history = new EditorHistory();
    private EditorHierarchyDepth depth = EditorHierarchyDepth.WORLD;
    private EditorFocusRegion focusRegion = EditorFocusRegion.WORLD_CANVAS;
    private EditorSelectionState selection = EditorSelectionState.empty();
    private EditorCursorState worldCursor = new EditorCursorState(0, 0);
    private int blockGridSide = 8;
    private int selectedBlockCellX;
    private int selectedBlockCellY;
    private int selectedChunkCellX;
    private int selectedChunkCellY;
    private MutableLevel level;

    public void attachLevel(MutableLevel level) {
        this.level = Objects.requireNonNull(level, "level");
        history.clear();
        depth = EditorHierarchyDepth.WORLD;
        focusRegion = EditorFocusRegion.WORLD_CANVAS;
        selection = EditorSelectionState.empty();
        worldCursor = new EditorCursorState(0, 0);
        blockGridSide = level.getChunksPerBlockSide();
        selectedBlockCellX = 0;
        selectedBlockCellY = 0;
        selectedChunkCellX = 0;
        selectedChunkCellY = 0;
    }

    public void placeBlock(int layer, int x, int y, int blockIndex) {
        MutableLevel attachedLevel = requireLevel();
        int before = Byte.toUnsignedInt(attachedLevel.getMap().getValue(layer, x, y));
        history.execute(new PlaceBlockCommand(attachedLevel, layer, x, y, before, blockIndex));
    }

    public void undo() {
        history.undo();
    }

    public void redo() {
        history.redo();
    }

    public void selectBlock(int blockIndex) {
        requireNonNegative(blockIndex, "blockIndex");
        selection = new EditorSelectionState(blockIndex, null);
        if (depth == EditorHierarchyDepth.CHUNK) {
            depth = EditorHierarchyDepth.BLOCK;
            focusRegion = EditorFocusRegion.FOCUSED_PANE;
        }
    }

    public void selectChunk(int chunkIndex) {
        requireNonNegative(chunkIndex, "chunkIndex");
        if (selection.selectedBlock() == null) {
            throw new IllegalStateException("Cannot select a chunk without a selected block");
        }
        selection = new EditorSelectionState(selection.selectedBlock(), chunkIndex);
    }

    public Block selectedBlockPreview() {
        Integer selectedBlock = selection.selectedBlock();
        if (selectedBlock == null) {
            return null;
        }
        MutableLevel attachedLevel = requireLevel();
        if (selectedBlock < 0 || selectedBlock >= attachedLevel.getBlockCount()) {
            return null;
        }
        return attachedLevel.getBlock(selectedBlock);
    }

    public Chunk selectedBlockCellPreview() {
        Block block = selectedBlockPreview();
        if (block == null) {
            return null;
        }
        if (selectedBlockCellX < 0 || selectedBlockCellX >= block.getGridSide()
                || selectedBlockCellY < 0 || selectedBlockCellY >= block.getGridSide()) {
            return null;
        }
        int chunkIndex = block.getChunkDesc(selectedBlockCellX, selectedBlockCellY).getChunkIndex();
        MutableLevel attachedLevel = requireLevel();
        if (chunkIndex < 0 || chunkIndex >= attachedLevel.getChunkCount()) {
            return null;
        }
        return attachedLevel.getChunk(chunkIndex);
    }

    public Chunk selectedBlockChunkPreview(int blockCellX, int blockCellY) {
        Block block = selectedBlockPreview();
        if (block == null) {
            return null;
        }
        if (blockCellX < 0 || blockCellY < 0
                || blockCellX >= block.getGridSide()
                || blockCellY >= block.getGridSide()) {
            return null;
        }
        int chunkIndex = block.getChunkDesc(blockCellX, blockCellY).getChunkIndex();
        MutableLevel attachedLevel = requireLevel();
        if (chunkIndex < 0 || chunkIndex >= attachedLevel.getChunkCount()) {
            return null;
        }
        return attachedLevel.getChunk(chunkIndex);
    }

    public Chunk selectedChunkPreview() {
        Integer selectedChunk = selection.selectedChunk();
        if (selectedChunk == null) {
            return null;
        }
        MutableLevel attachedLevel = requireLevel();
        if (selectedChunk < 0 || selectedChunk >= attachedLevel.getChunkCount()) {
            return null;
        }
        return attachedLevel.getChunk(selectedChunk);
    }

    public void descend() {
        if (depth == EditorHierarchyDepth.WORLD && selection.selectedBlock() != null) {
            depth = EditorHierarchyDepth.BLOCK;
            focusRegion = EditorFocusRegion.FOCUSED_PANE;
        } else if (depth == EditorHierarchyDepth.BLOCK && selection.selectedChunk() != null) {
            depth = EditorHierarchyDepth.CHUNK;
            focusRegion = EditorFocusRegion.FOCUSED_PANE;
        }
    }

    public void ascend() {
        if (depth == EditorHierarchyDepth.CHUNK) {
            depth = EditorHierarchyDepth.BLOCK;
            focusRegion = EditorFocusRegion.FOCUSED_PANE;
        } else if (depth == EditorHierarchyDepth.BLOCK) {
            depth = EditorHierarchyDepth.WORLD;
            focusRegion = EditorFocusRegion.WORLD_CANVAS;
        }
    }

    public EditorHierarchyDepth depth() {
        return depth;
    }

    public EditorFocusRegion focusRegion() {
        return focusRegion;
    }

    public void cycleFocusRegion() {
        if (depth == EditorHierarchyDepth.WORLD) {
            focusRegion = focusRegion == EditorFocusRegion.WORLD_CANVAS
                    ? EditorFocusRegion.LIBRARY
                    : EditorFocusRegion.WORLD_CANVAS;
            return;
        }
        focusRegion = focusRegion == EditorFocusRegion.FOCUSED_PANE
                ? EditorFocusRegion.LIBRARY
                : EditorFocusRegion.FOCUSED_PANE;
    }

    public void applyPrimaryAction() {
        if (focusRegion != EditorFocusRegion.WORLD_CANVAS) {
            return;
        }
        Integer selectedBlock = selection.selectedBlock();
        if (selectedBlock == null) {
            return;
        }
        MutableLevel attachedLevel = level;
        if (attachedLevel == null) {
            return;
        }
        WorldMapPosition mapPosition = resolveWorldMapPosition(attachedLevel);
        if (mapPosition == null) {
            return;
        }
        placeBlock(0, mapPosition.mapX(), mapPosition.mapY(), selectedBlock);
    }

    public void performEyedrop() {
        if (focusRegion != EditorFocusRegion.WORLD_CANVAS) {
            return;
        }
        MutableLevel attachedLevel = level;
        if (attachedLevel == null) {
            return;
        }
        WorldMapPosition mapPosition = resolveWorldMapPosition(attachedLevel);
        if (mapPosition == null) {
            return;
        }
        int blockIndex = Byte.toUnsignedInt(attachedLevel.getMap().getValue(0, mapPosition.mapX(), mapPosition.mapY()));
        selectBlock(blockIndex);
    }

    public void setWorldCursor(EditorCursorState cursor) {
        Objects.requireNonNull(cursor, "cursor");
        this.worldCursor = clampWorldCursor(cursor.x(), cursor.y());
    }

    public EditorCursorState worldCursor() {
        return worldCursor;
    }

    public int blockGridSide() {
        return blockGridSide;
    }

    public int chunkGridSide() {
        return 2;
    }

    public void moveWorldCursor(int dx, int dy) {
        worldCursor = clampWorldCursor(worldCursor.x() + dx, worldCursor.y() + dy);
    }

    public void moveActiveSelection(int dx, int dy) {
        int gridSide = activeGridSide();
        if (depth == EditorHierarchyDepth.WORLD) {
            moveWorldCursor(dx, dy);
            return;
        }
        if (depth == EditorHierarchyDepth.BLOCK) {
            selectedBlockCellX = clamp(selectedBlockCellX + dx, 0, gridSide - 1);
            selectedBlockCellY = clamp(selectedBlockCellY + dy, 0, gridSide - 1);
            return;
        }
        selectedChunkCellX = clamp(selectedChunkCellX + dx, 0, gridSide - 1);
        selectedChunkCellY = clamp(selectedChunkCellY + dy, 0, gridSide - 1);
    }

    public int selectedBlockCellX() {
        return selectedBlockCellX;
    }

    public int selectedBlockCellY() {
        return selectedBlockCellY;
    }

    public int selectedChunkCellX() {
        return selectedChunkCellX;
    }

    public int selectedChunkCellY() {
        return selectedChunkCellY;
    }

    public EditorSelectionState selection() {
        return selection;
    }

    public String breadcrumb() {
        if (depth == EditorHierarchyDepth.WORLD) {
            return "World";
        }
        if (depth == EditorHierarchyDepth.BLOCK) {
            return "World > Block " + selection.selectedBlock();
        }
        return "World > Block " + selection.selectedBlock() + " > Chunk " + selection.selectedChunk();
    }

    private static void requireNonNegative(int index, String name) {
        if (index < 0) {
            throw new IllegalArgumentException(name + " must be >= 0");
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private EditorCursorState clampWorldCursor(int x, int y) {
        if (level == null) {
            return new EditorCursorState(x, y);
        }
        int minX = level.getMinX();
        int maxX = level.getMaxX();
        int minY = level.getMinY();
        int maxY = level.getMaxY();
        return new EditorCursorState(clamp(x, minX, maxX), clamp(y, minY, maxY));
    }

    private WorldMapPosition resolveWorldMapPosition(MutableLevel attachedLevel) {
        int blockPixelSize = attachedLevel.getBlockPixelSize();
        if (blockPixelSize <= 0) {
            return null;
        }
        int mapX = worldCursor.x();
        int mapY = worldCursor.y();
        int mapWidth = attachedLevel.getMap().getWidth();
        int mapHeight = attachedLevel.getMap().getHeight();
        if (mapWidth <= 0 || mapHeight <= 0) {
            return null;
        }
        mapX = clamp(mapX / blockPixelSize, 0, mapWidth - 1);
        mapY = clamp(mapY / blockPixelSize, 0, mapHeight - 1);
        return new WorldMapPosition(mapX, mapY);
    }

    private int activeGridSide() {
        if (depth == EditorHierarchyDepth.BLOCK) {
            return blockGridSide;
        }
        if (depth == EditorHierarchyDepth.CHUNK) {
            return chunkGridSide();
        }
        return 1;
    }

    private MutableLevel requireLevel() {
        if (level == null) {
            throw new IllegalStateException("No MutableLevel is attached to the editor controller");
        }
        return level;
    }

    private record WorldMapPosition(int mapX, int mapY) {
    }
}
