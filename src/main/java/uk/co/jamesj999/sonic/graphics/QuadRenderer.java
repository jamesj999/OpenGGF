package uk.co.jamesj999.sonic.graphics;

import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;

/**
 * VBO-backed quad renderer to avoid immediate mode draws.
 */
public class QuadRenderer {
    private static final int FLOATS_PER_QUAD = 8;

    private int vboId;
    // Pre-allocate buffer at construction time instead of lazy initialization
    private FloatBuffer vertexBuffer = MemoryUtil.memAllocFloat(FLOATS_PER_QUAD);

    public void init() {
        if (vboId != 0) {
            return;
        }
        vboId = glGenBuffers();
    }

    public void draw(float x0, float y0, float x1, float y1) {
        if (vboId == 0) {
            init();
        }

        vertexBuffer.clear();
        vertexBuffer.put(x0).put(y0);
        vertexBuffer.put(x1).put(y0);
        vertexBuffer.put(x1).put(y1);
        vertexBuffer.put(x0).put(y1);
        vertexBuffer.flip();

        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, vertexBuffer, GL_DYNAMIC_DRAW);

        glEnableClientState(GL_VERTEX_ARRAY);
        glVertexPointer(2, GL_FLOAT, 0, 0L);
        glDrawArrays(GL_QUADS, 0, 4);
        glDisableClientState(GL_VERTEX_ARRAY);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    public void cleanup() {
        if (vboId != 0) {
            glDeleteBuffers(vboId);
        }
        vboId = 0;
        if (vertexBuffer != null) {
            MemoryUtil.memFree(vertexBuffer);
            vertexBuffer = null;
        }
    }
}
