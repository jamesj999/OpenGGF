package com.openggf.graphics;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;

/**
 * Modern OpenGL fullscreen quad renderer for core profile.
 * Uses a dummy VAO and draws a fullscreen triangle strip.
 * The vertex shader generates positions from gl_VertexID.
 */
public class QuadRenderer {
    private int vaoId;

    public void init() {
        if (vaoId != 0) {
            return;
        }
        // Create a VAO - required for core profile even if we don't use vertex attributes
        vaoId = glGenVertexArrays();
    }

    /**
     * Draws a fullscreen quad. The x0,y0,x1,y1 parameters are ignored since
     * the tilemap shader uses gl_FragCoord for pixel positioning and receives
     * viewport dimensions via uniforms.
     */
    public void draw(float x0, float y0, float x1, float y1) {
        if (vaoId == 0) {
            init();
        }

        // Bind the VAO (required even for attribute-less draws in core profile)
        glBindVertexArray(vaoId);

        // Draw a fullscreen triangle strip (4 vertices)
        // The vertex shader (shader_fullscreen.vert) generates positions from gl_VertexID
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

        glBindVertexArray(0);
    }

    public void cleanup() {
        if (vaoId != 0) {
            glDeleteVertexArrays(vaoId);
            vaoId = 0;
        }
    }
}
