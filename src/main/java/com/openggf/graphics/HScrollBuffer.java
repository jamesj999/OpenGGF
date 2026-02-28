package com.openggf.graphics;

import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL30.GL_R32F;

/**
 * GPU-side horizontal scroll buffer for per-scanline parallax scrolling.
 * Emulates Mega Drive VDP HScroll RAM by storing per-line scroll values
 * in a 1D texture that the parallax shader samples.
 *
 * The texture stores 224 entries (one per visible scanline), with each
 * entry containing the background X scroll offset normalized to -1..1.
 *
 * IMPORTANT: Uses R32F format (32-bit float) instead of R16F because:
 * - 16-bit half-float only has 11 significant bits of mantissa
 * - At high scroll values (e.g., cameraX=25000), precision loss causes
 * visible "jitter" as fractional positions are rounded
 * - 32-bit float provides 23 bits of mantissa, sufficient for sub-pixel
 * precision across the entire level range
 */
public class HScrollBuffer {

    public static final int VISIBLE_LINES = 224;

    private int textureId = -1;
    private final float[] scrollData = new float[VISIBLE_LINES];
    private final boolean foregroundWord;
    private boolean initialized = false;

    public HScrollBuffer() {
        this(false);
    }

    public HScrollBuffer(boolean foregroundWord) {
        this.foregroundWord = foregroundWord;
    }

    /**
     * Initialize the OpenGL texture for scroll data.
     * Must be called on the GL thread after context is created.
     */
    public void init() {
        if (initialized) {
            return;
        }

        textureId = glGenTextures();

        glBindTexture(GL_TEXTURE_1D, textureId);

        // Use nearest filtering - we want exact per-line values
        glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

        // Clamp to edge - shouldn't sample outside valid range
        glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);

        // Allocate texture with R32F format for full precision
        // R16F (half-float) only has 11 significant bits, causing jitter at high X
        glTexImage1D(
                GL_TEXTURE_1D,
                0,
                GL_R32F,
                VISIBLE_LINES,
                0,
                GL_RED,
                GL_FLOAT,
                (FloatBuffer) null);

        glBindTexture(GL_TEXTURE_1D, 0);
        initialized = true;
    }

    /**
     * Upload new scroll data to the GPU texture.
     *
     * @param hScroll Packed scroll array from ParallaxManager.
     *                Lower 16 bits contain BG scroll value.
     */
    public void upload(int[] hScroll) {
        if (!initialized || hScroll == null) {
            return;
        }

        // Extract per-line scroll values from packed words and normalize to -1..1.
        // foregroundWord=false -> BG word (low 16 bits), true -> FG word (high 16 bits).
        for (int i = 0; i < VISIBLE_LINES && i < hScroll.length; i++) {
            int raw = foregroundWord
                    ? (short) ((hScroll[i] >>> 16) & 0xFFFF)
                    : (short) (hScroll[i] & 0xFFFF);
            float normalized = raw / 32767.0f;
            if (normalized > 1.0f) {
                normalized = 1.0f;
            } else if (normalized < -1.0f) {
                normalized = -1.0f;
            }
            scrollData[i] = normalized;
        }

        FloatBuffer buffer = MemoryUtil.memAllocFloat(VISIBLE_LINES);
        try {
            buffer.put(scrollData);
            buffer.flip();

            glBindTexture(GL_TEXTURE_1D, textureId);
            glTexSubImage1D(
                    GL_TEXTURE_1D,
                    0,
                    0,
                    VISIBLE_LINES,
                    GL_RED,
                    GL_FLOAT,
                    buffer);
            glBindTexture(GL_TEXTURE_1D, 0);
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    /**
     * Bind the scroll texture to a texture unit for shader sampling.
     *
     * @param textureUnit Texture unit index (0-15)
     */
    public void bind(int textureUnit) {
        if (!initialized) {
            return;
        }
        glActiveTexture(GL_TEXTURE0 + textureUnit);
        glBindTexture(GL_TEXTURE_1D, textureId);
    }

    /**
     * Unbind the scroll texture.
     */
    public void unbind(int textureUnit) {
        glActiveTexture(GL_TEXTURE0 + textureUnit);
        glBindTexture(GL_TEXTURE_1D, 0);
    }

    /**
     * Get the OpenGL texture ID.
     */
    public int getTextureId() {
        return textureId;
    }

    /**
     * Check if the buffer has been initialized.
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Clean up OpenGL resources.
     */
    public void cleanup() {
        if (textureId > 0) {
            glDeleteTextures(textureId);
            textureId = -1;
        }
        initialized = false;
    }
}
