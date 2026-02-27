package com.openggf.editor;

import com.openggf.graphics.PixelFont;
import com.openggf.graphics.TexturedQuadRenderer;
import com.openggf.level.Chunk;
import com.openggf.level.Level;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.glUseProgram;

/**
 * Renders the chunk/block selection panel on the right side of the screen.
 * Operates in screen-space (game projection: 320x224, origin top-left, Y down).
 * <p>
 * PixelFont assumes a 224px tall game projection and converts to OpenGL coords
 * internally via {@code glY = 224f - y - GLYPH_H}. This renderer uses the same
 * coordinate convention for its immediate-mode quad drawing.
 */
public class ChunkPanelRenderer {

    public static final int PANEL_WIDTH = 200;
    private static final int HEADER_HEIGHT = 24;
    private static final int FOOTER_HEIGHT = 20;
    private static final int ITEM_PADDING = 2;
    private static final int CHUNK_PREVIEW_SIZE = 16;
    private static final int GRID_COLS = 10;

    private final LevelEditorManager editor;
    private PixelFont font;
    private Level level;

    public ChunkPanelRenderer(LevelEditorManager editor) {
        this.editor = editor;
    }

    public void init(PixelFont font, TexturedQuadRenderer quadRenderer) {
        this.font = font;
    }

    public void setLevel(Level level) {
        this.level = level;
    }

    public static int getPanelWidth() {
        return PANEL_WIDTH;
    }

    /**
     * Draw the panel. Called with a screen-space projection active.
     * PixelFont assumes 320x224 game projection, so this must be called
     * while that projection is active (not raw window pixel coords).
     *
     * @param screenWidth  projection width (e.g. 320 for game space)
     * @param screenHeight projection height (e.g. 224 for game space)
     * @param zoneName     display name of the current zone
     */
    public void draw(int screenWidth, int screenHeight, String zoneName) {
        if (level == null) return;

        int panelX = screenWidth - PANEL_WIDTH;
        boolean isChunkMode = editor.getEditMode() == LevelEditorManager.EditMode.CHUNK;
        int itemCount = isChunkMode ? level.getChunkCount() : level.getBlockCount();

        glUseProgram(0);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // Panel background (dark semi-transparent)
        drawScreenQuad(panelX, 0, PANEL_WIDTH, screenHeight, 0.1f, 0.1f, 0.15f, 0.9f);

        // Header text
        String modeLabel = isChunkMode ? "Chunks" : "Blocks";
        String header = modeLabel + " - " + zoneName;
        font.drawText(header, panelX + 4, 4, 1.0f, 1.0f, 1.0f, 1.0f);

        String countText = itemCount + " items";
        font.drawText(countText, panelX + 4, 14, 0.6f, 0.6f, 0.6f, 1.0f);

        // Grid area with item indices
        int gridY = HEADER_HEIGHT;
        int cellSize = CHUNK_PREVIEW_SIZE + ITEM_PADDING;
        int visibleRows = (screenHeight - HEADER_HEIGHT - FOOTER_HEIGHT) / cellSize;
        // Center scroll on selected item
        int selectedRow = editor.getPanelSelection() / GRID_COLS;
        int scrollOffset = Math.max(0, selectedRow - visibleRows / 2);

        for (int row = 0; row < visibleRows; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                int itemIndex = (scrollOffset + row) * GRID_COLS + col;
                if (itemIndex >= itemCount) break;

                int ix = panelX + 4 + col * cellSize;
                int iy = gridY + row * cellSize;

                // Highlight selected item
                if (itemIndex == editor.getPanelSelection()) {
                    boolean panelFocused = editor.getFocus() == LevelEditorManager.Focus.PANEL;
                    if (panelFocused) {
                        drawScreenQuad(ix, iy, CHUNK_PREVIEW_SIZE, CHUNK_PREVIEW_SIZE,
                                0.3f, 0.7f, 1.0f, 0.5f);
                    } else {
                        drawScreenQuad(ix, iy, CHUNK_PREVIEW_SIZE, CHUNK_PREVIEW_SIZE,
                                0.5f, 0.5f, 0.5f, 0.3f);
                    }
                }

                // Draw a small colored square as placeholder for actual tile preview
                // (Actual PatternAtlas-based chunk rendering is a future refinement)
                float brightness = (itemIndex % 16) / 16.0f;
                drawScreenQuad(ix + 1, iy + 1, CHUNK_PREVIEW_SIZE - 2, CHUNK_PREVIEW_SIZE - 2,
                        0.2f + brightness * 0.3f, 0.2f, 0.3f + brightness * 0.2f, 0.6f);
            }
        }

        // Footer -- selected item info
        int sel = editor.getPanelSelection();
        String footer = "Sel: " + sel;
        if (isChunkMode && sel < level.getChunkCount()) {
            Chunk chunk = level.getChunk(sel);
            if (chunk != null) {
                footer += "  Sol:" + chunk.getSolidTileIndex();
            }
        }
        font.drawText(footer, panelX + 4, screenHeight - FOOTER_HEIGHT + 4,
                0.7f, 0.7f, 0.7f, 1.0f);
    }

    /**
     * Draw a colored quad in screen-space (game coordinates: origin top-left, Y down).
     * Converts to OpenGL coords (origin bottom-left, Y up) internally,
     * matching the convention used by {@link PixelFont}.
     */
    private void drawScreenQuad(float x, float y, float w, float h,
                                float r, float g, float b, float a) {
        // Convert game-space top-left Y to OpenGL bottom-left Y
        // In a 224-high projection: glY = 224 - y - h
        float glY = 224f - y - h;
        glColor4f(r, g, b, a);
        glBegin(GL_QUADS);
        glVertex2f(x, glY);
        glVertex2f(x + w, glY);
        glVertex2f(x + w, glY + h);
        glVertex2f(x, glY + h);
        glEnd();
    }
}
