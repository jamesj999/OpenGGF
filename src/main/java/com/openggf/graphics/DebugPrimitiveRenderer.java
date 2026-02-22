package com.openggf.graphics;

import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Minimal VBO/VAO-backed renderer for debug primitives.
 * Uses attribute location 0 for position.
 */
public class DebugPrimitiveRenderer {
    private int vaoId;
    private int vboId;
    private FloatBuffer buffer;

    public void draw(int drawMode, FloatBuffer vertexBuffer, int vertexCount) {
        if (vertexCount <= 0) {
            return;
        }
        ensureBuffers(vertexBuffer.remaining());

        glBindVertexArray(vaoId);
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, vertexBuffer, GL_DYNAMIC_DRAW);

        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 0, 0L);
        glDrawArrays(drawMode, 0, vertexCount);
        glDisableVertexAttribArray(0);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    public FloatBuffer ensureFloatBuffer(int requiredFloats) {
        if (buffer == null || buffer.capacity() < requiredFloats) {
            if (buffer != null) {
                MemoryUtil.memFree(buffer);
            }
            buffer = MemoryUtil.memAllocFloat(Math.max(requiredFloats, 64));
        }
        return buffer;
    }

    private void ensureBuffers(int requiredFloats) {
        if (vaoId == 0) {
            vaoId = glGenVertexArrays();
            vboId = glGenBuffers();
        }
        ensureFloatBuffer(requiredFloats);
    }

    public void cleanup() {
        if (vboId != 0) {
            glDeleteBuffers(vboId);
        }
        if (vaoId != 0) {
            glDeleteVertexArrays(vaoId);
        }
        vboId = 0;
        vaoId = 0;
        if (buffer != null) {
            MemoryUtil.memFree(buffer);
            buffer = null;
        }
    }
}
