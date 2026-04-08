package com.openggf.editor;

import com.openggf.editor.commands.PlaceBlockCommand;
import com.openggf.level.MutableLevel;

import java.util.Objects;

public final class LevelEditorController {
    private final EditorHistory history = new EditorHistory();
    private EditorHierarchyDepth depth = EditorHierarchyDepth.WORLD;
    private EditorSelectionState selection = EditorSelectionState.empty();
    private MutableLevel level;

    public void attachLevel(MutableLevel level) {
        this.level = Objects.requireNonNull(level, "level");
        history.clear();
        depth = EditorHierarchyDepth.WORLD;
        selection = EditorSelectionState.empty();
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
        }
    }

    public void selectChunk(int chunkIndex) {
        requireNonNegative(chunkIndex, "chunkIndex");
        if (selection.selectedBlock() == null) {
            throw new IllegalStateException("Cannot select a chunk without a selected block");
        }
        selection = new EditorSelectionState(selection.selectedBlock(), chunkIndex);
    }

    public void descend() {
        if (depth == EditorHierarchyDepth.WORLD && selection.selectedBlock() != null) {
            depth = EditorHierarchyDepth.BLOCK;
        } else if (depth == EditorHierarchyDepth.BLOCK && selection.selectedChunk() != null) {
            depth = EditorHierarchyDepth.CHUNK;
        }
    }

    public void ascend() {
        if (depth == EditorHierarchyDepth.CHUNK) {
            depth = EditorHierarchyDepth.BLOCK;
        } else if (depth == EditorHierarchyDepth.BLOCK) {
            depth = EditorHierarchyDepth.WORLD;
        }
    }

    public EditorHierarchyDepth depth() {
        return depth;
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

    private MutableLevel requireLevel() {
        if (level == null) {
            throw new IllegalStateException("No MutableLevel is attached to the editor controller");
        }
        return level;
    }
}
