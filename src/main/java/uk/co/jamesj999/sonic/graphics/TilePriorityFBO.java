package uk.co.jamesj999.sonic.graphics;

import java.util.logging.Logger;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Framebuffer object for rendering tile priority information.
 *
 * This FBO captures high-priority foreground tiles to a texture that can be
 * sampled by the sprite priority shader. The shader uses this texture to
 * determine if low-priority sprites should be hidden behind high-priority tiles.
 *
 * The FBO stores priority in the red channel:
 * - R = 0.0: No high-priority tile at this pixel
 * - R = 1.0: High-priority tile present at this pixel
 */
public class TilePriorityFBO {

    private static final Logger LOGGER = Logger.getLogger(TilePriorityFBO.class.getName());

    private int fboId = -1;
    private int textureId = -1;
    private int width;
    private int height;
    private boolean initialized = false;

    private final int[] savedViewport = new int[4];

    /**
     * Initialize the tile priority FBO.
     *
     * @param width  FBO width in pixels (should match screen width)
     * @param height FBO height in pixels (should match screen height)
     */
    public void init(int width, int height) {
        if (initialized) {
            cleanup();
        }

        this.width = width;
        this.height = height;

        // Generate FBO
        fboId = glGenFramebuffers();

        // Generate texture for color attachment
        textureId = glGenTextures();

        // Configure texture
        glBindTexture(GL_TEXTURE_2D, textureId);
        // Use single-channel RED format for priority (0 = no tile, 1 = high-priority tile)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_R8, width, height, 0,
                GL_RED, GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glBindTexture(GL_TEXTURE_2D, 0);

        // Attach texture to FBO
        glBindFramebuffer(GL_FRAMEBUFFER, fboId);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                GL_TEXTURE_2D, textureId, 0);

        // Check FBO completeness
        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            LOGGER.severe("Tile priority FBO creation failed with status: " + status);
        }

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        initialized = true;

        LOGGER.info("TilePriorityFBO initialized: " + width + "x" + height);
    }

    /**
     * Begin rendering to the tile priority FBO.
     * Call this before rendering high-priority foreground tiles.
     */
    public void begin() {
        if (!initialized) {
            return;
        }

        // Save current viewport
        glGetIntegerv(GL_VIEWPORT, savedViewport);

        // Bind FBO and set viewport
        glBindFramebuffer(GL_FRAMEBUFFER, fboId);
        glViewport(0, 0, width, height);

        // Clear to 0 (no high-priority tiles)
        glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        glClear(GL_COLOR_BUFFER_BIT);
    }

    /**
     * End rendering to the tile priority FBO.
     * Call this after rendering high-priority foreground tiles.
     */
    public void end() {
        if (!initialized) {
            return;
        }

        // Restore framebuffer and viewport
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(savedViewport[0], savedViewport[1], savedViewport[2], savedViewport[3]);
    }

    /**
     * Get the texture ID for sampling the priority buffer.
     */
    public int getTextureId() {
        return textureId;
    }

    /**
     * Check if the FBO is initialized and ready for use.
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Get the FBO width in pixels.
     */
    public int getWidth() {
        return width;
    }

    /**
     * Get the FBO height in pixels.
     */
    public int getHeight() {
        return height;
    }

    /**
     * Resize the FBO if screen dimensions change.
     */
    public void resize(int width, int height) {
        if (this.width == width && this.height == height) {
            return;
        }

        // Re-initialize with new dimensions
        init(width, height);
    }

    /**
     * Clean up OpenGL resources.
     */
    public void cleanup() {
        if (fboId > 0) {
            glDeleteFramebuffers(fboId);
            fboId = -1;
        }
        if (textureId > 0) {
            glDeleteTextures(textureId);
            textureId = -1;
        }
        initialized = false;
    }
}
