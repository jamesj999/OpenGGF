package com.openggf.editor;

import com.openggf.graphics.PixelFont;
import com.openggf.graphics.TexturedQuadRenderer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;

/**
 * Renders a contextual tooltip bar at the bottom of the screen in screen-space.
 * Uses the master title screen PixelFont for text rendering.
 *
 * Rendered in screen-space (1:1 DPI-scaled pixels), NOT game-space.
 * Requires its own orthographic projection set before draw().
 */
public class TooltipBarRenderer {

    private static final int BAR_HEIGHT = 20;
    private static final int TEXT_Y_OFFSET = 5;
    private static final int TEXT_X_MARGIN = 8;

    private static final float DESC_R = 0.8f, DESC_G = 0.8f, DESC_B = 0.8f, DESC_A = 1.0f;

    private final LevelEditorManager editor;
    private PixelFont font;
    private TexturedQuadRenderer quadRenderer;

    public TooltipBarRenderer(LevelEditorManager editor) {
        this.editor = editor;
    }

    public void init(PixelFont font, TexturedQuadRenderer quadRenderer) {
        this.font = font;
        this.quadRenderer = quadRenderer;
    }

    /**
     * Draw the tooltip bar. Must be called with a screen-space orthographic
     * projection active (origin bottom-left in OpenGL).
     *
     * @param screenWidth  screen width in projection units
     * @param screenHeight screen height in projection units
     */
    public void draw(int screenWidth, int screenHeight) {
        // Draw semi-transparent dark background strip at bottom
        glUseProgram(0);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        glColor4f(0.0f, 0.0f, 0.0f, 0.7f);
        glBegin(GL_QUADS);
        glVertex2f(0, 0);
        glVertex2f(screenWidth, 0);
        glVertex2f(screenWidth, BAR_HEIGHT);
        glVertex2f(0, BAR_HEIGHT);
        glEnd();

        // Draw tooltip text using the GL-coordinate variant (no 224px Y-flip)
        // Position text inside the bar: TEXT_Y_OFFSET from the bottom of the bar
        String tooltip = getTooltipText();
        font.drawTextGL(tooltip, TEXT_X_MARGIN, TEXT_Y_OFFSET,
                DESC_R, DESC_G, DESC_B, DESC_A);
    }

    private String getTooltipText() {
        String mode = editor.getEditMode() == LevelEditorManager.EditMode.CHUNK
                ? "Chunk" : "Block";

        if (editor.getFocus() == LevelEditorManager.Focus.GRID) {
            return "Arrows:Move  Space:Place  Del:Clear  E:Eyedrop  B/C:" + mode + "  Tab:Panel";
        } else {
            return "Arrows:Browse  PgUp/Dn:Scroll  Enter:Select  B/C:" + mode + "  Tab:Grid";
        }
    }
}
