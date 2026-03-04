package com.openggf.graphics;

import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;

/**
 * 1D texture that maps pattern index to atlas tile coordinates.
 * Stores RGBA8 where R=tileX, G=tileY.
 */
public class PatternLookupBuffer {
    private int textureId = 0;
    private int size = 0;

    public void init(int size) {
        if (size <= 0) {
            return;
        }
        if (textureId == 0) {
            textureId = glGenTextures();
        }
        this.size = size;

        glBindTexture(GL_TEXTURE_1D, textureId);
        glTexImage1D(GL_TEXTURE_1D, 0, GL_RGBA8, size, 0, GL_RGBA,
                GL_UNSIGNED_BYTE, (ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glBindTexture(GL_TEXTURE_1D, 0);
    }

    public void upload(byte[] data, int size) {
        if (data == null || size <= 0) {
            return;
        }
        if (textureId == 0 || this.size != size) {
            init(size);
        }
        ByteBuffer buffer = MemoryUtil.memAlloc(data.length);
        try {
            buffer.put(data);
            buffer.flip();
            glBindTexture(GL_TEXTURE_1D, textureId);
            glTexSubImage1D(GL_TEXTURE_1D, 0, 0, size, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
            glBindTexture(GL_TEXTURE_1D, 0);
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    public int getTextureId() {
        return textureId;
    }

    public int getSize() {
        return size;
    }

    public void cleanup() {
        if (textureId != 0) {
            glDeleteTextures(textureId);
        }
        textureId = 0;
        size = 0;
    }
}
