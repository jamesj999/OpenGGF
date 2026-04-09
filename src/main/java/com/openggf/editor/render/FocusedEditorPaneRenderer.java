package com.openggf.editor.render;

import com.openggf.editor.LevelEditorController;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GLCommandGroup;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Block;
import com.openggf.level.Chunk;
import com.openggf.level.PatternDesc;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.GL_LINES;

public class FocusedEditorPaneRenderer {
    private static final float BLOCK_R = 0.96f;
    private static final float BLOCK_G = 0.64f;
    private static final float BLOCK_B = 0.28f;
    private static final float CHUNK_R = 0.54f;
    private static final float CHUNK_G = 0.82f;
    private static final float CHUNK_B = 0.34f;
    private static final float PREVIEW_R = 0.76f;
    private static final float PREVIEW_G = 0.90f;
    private static final float PREVIEW_B = 0.58f;
    private static final float ACTIVE_R = 1.0f;
    private static final float ACTIVE_G = 0.96f;
    private static final float ACTIVE_B = 0.24f;

    private final LevelEditorController controller;

    public FocusedEditorPaneRenderer() {
        this(null);
    }

    public FocusedEditorPaneRenderer(LevelEditorController controller) {
        this.controller = controller;
    }

    public void renderBlockEditorPane() {
        List<GLCommand> commands = new ArrayList<>();
        appendBlockPaneCommands(commands);
        if (!commands.isEmpty()) {
            GraphicsManager.getInstance().registerCommand(new GLCommandGroup(GL_LINES, commands));
        }
    }

    public void renderChunkEditorPane() {
        List<GLCommand> commands = new ArrayList<>();
        appendChunkPaneCommands(commands);
        if (!commands.isEmpty()) {
            GraphicsManager.getInstance().registerCommand(new GLCommandGroup(GL_LINES, commands));
        }
    }

    protected void appendBlockPaneCommands(List<GLCommand> commands) {
        int left = 196;
        int top = 34;
        int right = 316;
        int bottom = 194;
        appendPaneCommands(commands, left, top, right, bottom, BLOCK_R, BLOCK_G, BLOCK_B);
        appendBlockPreviewCommands(commands, left, top, right, bottom);
    }

    protected void appendChunkPaneCommands(List<GLCommand> commands) {
        appendPaneCommands(commands, 176, 34, 316, 194, CHUNK_R, CHUNK_G, CHUNK_B);
    }

    protected void appendBlockPreviewCommands(List<GLCommand> commands,
                                              int left,
                                              int top,
                                              int right,
                                              int bottom) {
        if (controller == null) {
            return;
        }

        Block block = controller.selectedBlockPreview();
        if (block == null) {
            return;
        }

        int gridSide = Math.max(1, block.getGridSide());
        int availableWidth = Math.max(1, right - left - 16);
        int availableHeight = Math.max(1, bottom - top - 42);
        int cellSize = Math.max(4, Math.min(12,
                Math.min(availableWidth / gridSide, availableHeight / gridSide)));
        int previewWidth = cellSize * gridSide;
        int previewHeight = cellSize * gridSide;
        int originX = left + 8 + Math.max(0, (availableWidth - previewWidth) / 2);
        int originY = top + 34 + Math.max(0, (availableHeight - previewHeight) / 2);

        for (int cellY = 0; cellY < gridSide; cellY++) {
            for (int cellX = 0; cellX < gridSide; cellX++) {
                int cellLeft = originX + cellX * cellSize;
                int cellTop = originY + cellY * cellSize;
                int cellRight = cellLeft + cellSize - 1;
                int cellBottom = cellTop + cellSize - 1;
                int chunkIndex = block.getChunkDesc(cellX, cellY).getChunkIndex();
                float shade = 0.18f + (Math.floorMod(chunkIndex, 6) * 0.12f);
                EditorToolbarRenderer.appendRectOutline(commands, cellLeft, cellTop, cellRight, cellBottom,
                        PREVIEW_R * shade, PREVIEW_G * shade, PREVIEW_B * shade);
                int markerSpan = Math.max(1, cellSize - 2);
                int markerX = cellLeft + 1 + Math.floorMod(chunkIndex, markerSpan);
                int markerY = cellTop + 1 + Math.floorMod(chunkIndex / Math.max(1, markerSpan), markerSpan);
                EditorToolbarRenderer.appendLine(commands, markerX, markerY, markerX + 1, markerY + 1,
                        PREVIEW_R, PREVIEW_G, PREVIEW_B);
            }
        }

        appendBlockActiveCellHighlight(commands, block, originX, originY, cellSize);
    }

    protected void appendBlockActiveCellHighlight(List<GLCommand> commands,
                                                  Block block,
                                                  int originX,
                                                  int originY,
                                                  int cellSize) {
        int activeX = controller.selectedBlockCellX();
        int activeY = controller.selectedBlockCellY();
        if (activeX < 0 || activeY < 0 || activeX >= block.getGridSide() || activeY >= block.getGridSide()) {
            return;
        }

        int cellLeft = originX + activeX * cellSize;
        int cellTop = originY + activeY * cellSize;
        int cellRight = cellLeft + cellSize - 1;
        int cellBottom = cellTop + cellSize - 1;
        EditorToolbarRenderer.appendRectOutline(commands, cellLeft - 1, cellTop - 1, cellRight + 1, cellBottom + 1,
                ACTIVE_R, ACTIVE_G, ACTIVE_B);

        Chunk activeChunk = controller.selectedBlockCellPreview();
        if (activeChunk == null) {
            return;
        }

        PatternDesc descriptor = activeChunk.getPatternDesc(0, 0);
        int markerSpan = Math.max(1, cellSize - 4);
        int markerOffset = Math.floorMod(descriptor.getPatternIndex(), markerSpan);
        int markerX = cellLeft + 2 + markerOffset;
        int markerY = cellTop + 2 + Math.floorMod(descriptor.getPatternIndex() / Math.max(1, markerSpan), markerSpan);
        EditorToolbarRenderer.appendLine(commands, markerX, markerY, markerX + 1, markerY + 1,
                ACTIVE_R, ACTIVE_G, ACTIVE_B);
    }

    private void appendPaneCommands(List<GLCommand> commands,
                                    int left,
                                    int top,
                                    int right,
                                    int bottom,
                                    float r,
                                    float g,
                                    float b) {
        EditorToolbarRenderer.appendRectOutline(commands, left, top, right, bottom, r, g, b);
        EditorToolbarRenderer.appendLine(commands, left, top + 26, right, top + 26, r, g, b);
        EditorToolbarRenderer.appendLine(commands, left, top + 98, right, top + 98, r, g, b);
        EditorToolbarRenderer.appendLine(commands, left + 28, top + 26, left + 28, bottom, r, g, b);
    }
}
