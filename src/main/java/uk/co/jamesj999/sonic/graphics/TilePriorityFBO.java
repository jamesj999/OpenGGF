package uk.co.jamesj999.sonic.graphics;

import com.jogamp.opengl.GL2;

import java.util.logging.Logger;

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
     * @param gl     OpenGL context
     * @param width  FBO width in pixels (should match screen width)
     * @param height FBO height in pixels (should match screen height)
     */
    public void init(GL2 gl, int width, int height) {
        if (initialized) {
            cleanup(gl);
        }

        this.width = width;
        this.height = height;

        // Generate FBO
        int[] fbos = new int[1];
        gl.glGenFramebuffers(1, fbos, 0);
        fboId = fbos[0];

        // Generate texture for color attachment
        int[] textures = new int[1];
        gl.glGenTextures(1, textures, 0);
        textureId = textures[0];

        // Configure texture
        gl.glBindTexture(GL2.GL_TEXTURE_2D, textureId);
        // Use single-channel RED format for priority (0 = no tile, 1 = high-priority tile)
        gl.glTexImage2D(GL2.GL_TEXTURE_2D, 0, GL2.GL_R8, width, height, 0,
                GL2.GL_RED, GL2.GL_UNSIGNED_BYTE, null);
        gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_MIN_FILTER, GL2.GL_NEAREST);
        gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_MAG_FILTER, GL2.GL_NEAREST);
        gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_WRAP_S, GL2.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_WRAP_T, GL2.GL_CLAMP_TO_EDGE);
        gl.glBindTexture(GL2.GL_TEXTURE_2D, 0);

        // Attach texture to FBO
        gl.glBindFramebuffer(GL2.GL_FRAMEBUFFER, fboId);
        gl.glFramebufferTexture2D(GL2.GL_FRAMEBUFFER, GL2.GL_COLOR_ATTACHMENT0,
                GL2.GL_TEXTURE_2D, textureId, 0);

        // Check FBO completeness
        int status = gl.glCheckFramebufferStatus(GL2.GL_FRAMEBUFFER);
        if (status != GL2.GL_FRAMEBUFFER_COMPLETE) {
            LOGGER.severe("Tile priority FBO creation failed with status: " + status);
        }

        gl.glBindFramebuffer(GL2.GL_FRAMEBUFFER, 0);
        initialized = true;

        LOGGER.info("TilePriorityFBO initialized: " + width + "x" + height);
    }

    /**
     * Begin rendering to the tile priority FBO.
     * Call this before rendering high-priority foreground tiles.
     */
    public void begin(GL2 gl) {
        if (!initialized) {
            return;
        }

        // Save current viewport
        gl.glGetIntegerv(GL2.GL_VIEWPORT, savedViewport, 0);

        // Bind FBO and set viewport
        gl.glBindFramebuffer(GL2.GL_FRAMEBUFFER, fboId);
        gl.glViewport(0, 0, width, height);

        // Clear to 0 (no high-priority tiles)
        gl.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        gl.glClear(GL2.GL_COLOR_BUFFER_BIT);
    }

    /**
     * End rendering to the tile priority FBO.
     * Call this after rendering high-priority foreground tiles.
     */
    public void end(GL2 gl) {
        if (!initialized) {
            return;
        }

        // Restore framebuffer and viewport
        gl.glBindFramebuffer(GL2.GL_FRAMEBUFFER, 0);
        gl.glViewport(savedViewport[0], savedViewport[1], savedViewport[2], savedViewport[3]);
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
    public void resize(GL2 gl, int width, int height) {
        if (this.width == width && this.height == height) {
            return;
        }

        // Re-initialize with new dimensions
        init(gl, width, height);
    }

    /**
     * Clean up OpenGL resources.
     */
    public void cleanup(GL2 gl) {
        if (fboId > 0) {
            gl.glDeleteFramebuffers(1, new int[]{fboId}, 0);
            fboId = -1;
        }
        if (textureId > 0) {
            gl.glDeleteTextures(1, new int[]{textureId}, 0);
            textureId = -1;
        }
        initialized = false;
    }
}
