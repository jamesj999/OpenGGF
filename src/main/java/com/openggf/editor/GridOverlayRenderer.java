package com.openggf.editor;

import com.openggf.graphics.GLCommandable;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.glUseProgram;

/**
 * Renders grid overlay lines and cursor highlight in game-space.
 * Registered as a GLCommandable so it participates in the camera-aware flush.
 *
 * The engine projection is ortho2D(0, screenWidth, 0, screenHeight) with
 * OpenGL conventions (Y=0 at bottom).  Genesis game coordinates use Y-down
 * (Y=0 at top).  This renderer sets up its own fixed-function projection
 * and model-view matrices to draw in Genesis game-space, then restores GL
 * state so subsequent shader-based commands are unaffected.
 */
public class GridOverlayRenderer implements GLCommandable {

    private final LevelEditorManager editor;

    public GridOverlayRenderer(LevelEditorManager editor) {
        this.editor = editor;
    }

    @Override
    public void execute(int cameraX, int cameraY, int cameraWidth, int cameraHeight) {
        if (cameraWidth <= 0 || cameraHeight <= 0) {
            return;
        }

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glUseProgram(0); // Fixed-function pipeline for simple lines/quads

        // Save current projection and set up a screen-space ortho that matches
        // Genesis coordinates: X right, Y down, origin at top-left.
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        // left=0, right=cameraWidth, bottom=cameraHeight, top=0  -> Y-down
        glOrtho(0, cameraWidth, cameraHeight, 0, -1, 1);

        // Model-view: translate so that game-space coordinate (cameraX, cameraY)
        // maps to screen origin (0, 0).
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        glTranslatef(-cameraX, -cameraY, 0);

        drawCursorHighlight();
        drawChunkGrid(cameraX, cameraY, cameraWidth, cameraHeight);
        drawBlockGrid(cameraX, cameraY, cameraWidth, cameraHeight);

        // Restore matrices
        glPopMatrix(); // model-view
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);

        glDisable(GL_BLEND);
    }

    private void drawCursorHighlight() {
        int px = editor.getCursorPixelX();
        int py = editor.getCursorPixelY();
        int size = editor.getCursorCellSize();

        boolean gridFocused = editor.getFocus() == LevelEditorManager.Focus.GRID;

        // Semi-transparent fill
        if (gridFocused) {
            glColor4f(0.2f, 0.6f, 1.0f, 0.25f); // Blue tint when grid focused
        } else {
            glColor4f(1.0f, 0.6f, 0.2f, 0.15f); // Orange tint when panel focused
        }
        glBegin(GL_QUADS);
        glVertex2f(px, py);
        glVertex2f(px + size, py);
        glVertex2f(px + size, py + size);
        glVertex2f(px, py + size);
        glEnd();

        // Bright outline
        if (gridFocused) {
            glColor4f(0.3f, 0.7f, 1.0f, 0.9f);
        } else {
            glColor4f(1.0f, 0.7f, 0.3f, 0.6f);
        }
        glLineWidth(2.0f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(px, py);
        glVertex2f(px + size, py);
        glVertex2f(px + size, py + size);
        glVertex2f(px, py + size);
        glEnd();
    }

    private void drawChunkGrid(int camX, int camY, int camW, int camH) {
        glColor4f(1.0f, 1.0f, 1.0f, 0.15f); // Subtle white, 15% opacity
        glLineWidth(1.0f);
        glBegin(GL_LINES);

        // Vertical lines at 16px (chunk) intervals
        int startX = (camX / 16) * 16;
        int endX = camX + camW + 16;
        for (int x = startX; x <= endX; x += 16) {
            glVertex2f(x, camY);
            glVertex2f(x, camY + camH);
        }

        // Horizontal lines at 16px intervals
        int startY = (camY / 16) * 16;
        int endY = camY + camH + 16;
        for (int y = startY; y <= endY; y += 16) {
            glVertex2f(camX, y);
            glVertex2f(camX + camW, y);
        }

        glEnd();
    }

    private void drawBlockGrid(int camX, int camY, int camW, int camH) {
        glColor4f(1.0f, 1.0f, 1.0f, 0.35f); // Brighter white, 35% opacity
        glLineWidth(2.0f);
        glBegin(GL_LINES);

        // Vertical lines at 128px (block) intervals
        int startX = (camX / 128) * 128;
        int endX = camX + camW + 128;
        for (int x = startX; x <= endX; x += 128) {
            glVertex2f(x, camY);
            glVertex2f(x, camY + camH);
        }

        // Horizontal lines at 128px intervals
        int startY = (camY / 128) * 128;
        int endY = camY + camH + 128;
        for (int y = startY; y <= endY; y += 128) {
            glVertex2f(camX, y);
            glVertex2f(camX + camW, y);
        }

        glEnd();
    }
}
