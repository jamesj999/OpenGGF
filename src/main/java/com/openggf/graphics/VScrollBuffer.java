package com.openggf.graphics;

import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL30.GL_R32F;

/**
 * GPU-side per-scanline vertical scroll buffer.
 *
 * Stores signed pixel offsets as normalized float values in a 1D texture
 * sampled by the parallax background shader.
 */
public class VScrollBuffer {

    public static final int VISIBLE_LINES = 224;

    private int textureId = -1;
    private final int entryCount;
    private final float[] scrollData;
    private boolean initialized = false;

    public VScrollBuffer() {
        this(VISIBLE_LINES);
    }

    public VScrollBuffer(int entryCount) {
        this.entryCount = Math.max(1, entryCount);
        this.scrollData = new float[this.entryCount];
    }

    public void init() {
        if (initialized) {
            return;
        }

        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_1D, textureId);
        glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexImage1D(
                GL_TEXTURE_1D,
                0,
                GL_R32F,
                entryCount,
                0,
                GL_RED,
                GL_FLOAT,
                (FloatBuffer) null);
        glBindTexture(GL_TEXTURE_1D, 0);
        initialized = true;
    }

    public void upload(short[] vScroll) {
        if (!initialized || vScroll == null) {
            return;
        }

        for (int i = 0; i < entryCount; i++) {
            int raw = i < vScroll.length ? vScroll[i] : 0;
            float normalized = raw / 32767.0f;
            if (normalized > 1.0f) {
                normalized = 1.0f;
            } else if (normalized < -1.0f) {
                normalized = -1.0f;
            }
            scrollData[i] = normalized;
        }

        FloatBuffer buffer = MemoryUtil.memAllocFloat(entryCount);
        try {
            buffer.put(scrollData);
            buffer.flip();
            glBindTexture(GL_TEXTURE_1D, textureId);
            glTexSubImage1D(
                    GL_TEXTURE_1D,
                    0,
                    0,
                    entryCount,
                    GL_RED,
                    GL_FLOAT,
                    buffer);
            glBindTexture(GL_TEXTURE_1D, 0);
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    public void bind(int textureUnit) {
        if (!initialized) {
            return;
        }
        glActiveTexture(GL_TEXTURE0 + textureUnit);
        glBindTexture(GL_TEXTURE_1D, textureId);
    }

    public void unbind(int textureUnit) {
        glActiveTexture(GL_TEXTURE0 + textureUnit);
        glBindTexture(GL_TEXTURE_1D, 0);
    }

    public int getTextureId() {
        return textureId;
    }

    public void cleanup() {
        if (textureId > 0) {
            glDeleteTextures(textureId);
            textureId = -1;
        }
        initialized = false;
    }
}
