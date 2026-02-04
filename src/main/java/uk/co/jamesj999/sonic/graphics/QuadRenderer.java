package uk.co.jamesj999.sonic.graphics;

import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;

/**
 * OpenGL 2.1 compatible fullscreen quad renderer.
 * Provides vertex position data via a VBO since gl_VertexID is not
 * available in GLSL 1.20.
 */
public class QuadRenderer {
    private int vboId;
    private boolean initialized = false;

    // Fullscreen quad vertex positions in clip space (triangle strip order)
    // bottom-left, bottom-right, top-left, top-right
    private static final float[] QUAD_VERTICES = {
        -1.0f, -1.0f,  // 0: bottom-left
         1.0f, -1.0f,  // 1: bottom-right
        -1.0f,  1.0f,  // 2: top-left
         1.0f,  1.0f   // 3: top-right
    };

    public void init() {
        if (initialized) {
            return;
        }

        // Create VBO with vertex positions
        vboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboId);

        FloatBuffer buffer = MemoryUtil.memAllocFloat(QUAD_VERTICES.length);
        try {
            buffer.put(QUAD_VERTICES).flip();
            glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW);
        } finally {
            MemoryUtil.memFree(buffer);
        }

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        initialized = true;
    }

    /**
     * Draws a fullscreen quad. The x0,y0,x1,y1 parameters are ignored since
     * the tilemap shader uses gl_FragCoord for pixel positioning and receives
     * viewport dimensions via uniforms.
     */
    public void draw(float x0, float y0, float x1, float y1) {
        if (!initialized) {
            init();
        }

        // Bind VBO and set up vertex attribute
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glEnableVertexAttribArray(0);  // VertexPos is at location 0
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 0, 0);

        // Draw a fullscreen triangle strip (4 vertices)
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

        glDisableVertexAttribArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    public void cleanup() {
        if (vboId != 0) {
            glDeleteBuffers(vboId);
            vboId = 0;
        }
        initialized = false;
    }
}
