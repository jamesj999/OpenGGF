package uk.co.jamesj999.sonic.game.sonic2.specialstage;

import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import uk.co.jamesj999.sonic.graphics.HScrollBuffer;
import uk.co.jamesj999.sonic.graphics.ParallaxShaderProgram;
import uk.co.jamesj999.sonic.graphics.QuadRenderer;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.logging.Logger;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL14.GL_DEPTH_COMPONENT16;
import static org.lwjgl.opengl.GL20.glUniformMatrix4fv;
import static org.lwjgl.opengl.GL30.*;

/**
 * GPU-based background renderer for Special Stage.
 *
 * Implements the two-pass rendering approach:
 * 1. Render background tiles to an FBO (256x256 texture)
 * 2. Draw fullscreen quad with shader applying per-scanline H-scroll and H32
 * clipping
 *
 * This emulates the Mega Drive VDP behavior where:
 * - H-scroll table provides per-scanline horizontal scroll offsets
 * - H32 mode displays only 256 pixels centered on the 320-pixel screen
 * - V-scroll provides vertical parallax during rise/drop animations
 */
public class SpecialStageBackgroundRenderer {

    private static final Logger LOGGER = Logger.getLogger(SpecialStageBackgroundRenderer.class.getName());

    // FBO dimensions - background is 32x32 tiles = 256x256 pixels
    private static final int FBO_WIDTH = 256;
    private static final int FBO_HEIGHT = 256;

    // Screen dimensions
    public static final int SCREEN_WIDTH = 320;
    public static final int SCREEN_HEIGHT = 224;
    public static final int H32_WIDTH = 256;
    public static final int H32_OFFSET = (SCREEN_WIDTH - H32_WIDTH) / 2; // 32 pixels

    // OpenGL resources
    private int fboId = -1;
    private int fboTextureId = -1;
    private int fboDepthId = -1;

    // Shader and scroll buffer
    private ParallaxShaderProgram shader;
    private HScrollBuffer hScrollBuffer;
    private final QuadRenderer quadRenderer = new QuadRenderer();

    // Per-scanline scroll data (224 entries)
    private final int[] hScrollData = new int[SCREEN_HEIGHT];

    // State
    private boolean initialized = false;
    private final int[] savedViewport = new int[4];

    /**
     * Initialize the renderer with FBO and shader.
     *
     * @throws IOException if shader loading fails
     */
    public void init() throws IOException {
        if (initialized) {
            return;
        }

        // Create FBO for background tile rendering
        createFBO();

        // Initialize H-scroll buffer
        hScrollBuffer = new HScrollBuffer();
        hScrollBuffer.init();

        // Load special stage background shader
        shader = new ParallaxShaderProgram("shaders/shader_ss_background.glsl");
        shader.cacheUniformLocations();
        quadRenderer.init();

        // Initialize H-scroll data to zero
        for (int i = 0; i < SCREEN_HEIGHT; i++) {
            hScrollData[i] = 0;
        }

        initialized = true;
        LOGGER.info("SpecialStageBackgroundRenderer initialized");
    }

    /**
     * Create the framebuffer object for tile rendering.
     */
    private void createFBO() {
        // Generate FBO
        fboId = glGenFramebuffers();

        // Generate texture for color attachment
        fboTextureId = glGenTextures();

        glBindTexture(GL_TEXTURE_2D, fboTextureId);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, FBO_WIDTH, FBO_HEIGHT, 0,
                GL_RGBA, GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        // Use REPEAT for seamless horizontal wrapping
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        glBindTexture(GL_TEXTURE_2D, 0);

        // Generate depth buffer (optional, but good practice)
        fboDepthId = glGenRenderbuffers();

        glBindRenderbuffer(GL_RENDERBUFFER, fboDepthId);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT16, FBO_WIDTH, FBO_HEIGHT);
        glBindRenderbuffer(GL_RENDERBUFFER, 0);

        // Attach to FBO
        glBindFramebuffer(GL_FRAMEBUFFER, fboId);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                GL_TEXTURE_2D, fboTextureId, 0);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT,
                GL_RENDERBUFFER, fboDepthId);

        // Check FBO completeness
        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            LOGGER.severe("Special stage background FBO creation failed with status: " + status);
        }

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        LOGGER.fine("Created FBO " + FBO_WIDTH + "x" + FBO_HEIGHT + " for special stage background");
    }

    /**
     * Begin the tile rendering pass - bind FBO and set up projection.
     * After calling this, render background tiles using the normal tile renderer.
     *
     * @param displayHeight The display height used by pattern renderer for Y-flip
     */
    public void beginTilePass(int displayHeight) {
        if (!initialized)
            return;

        // Save current viewport to restore later (must query actual GL state)
        glGetIntegerv(GL_VIEWPORT, savedViewport);

        // Bind FBO
        glBindFramebuffer(GL_FRAMEBUFFER, fboId);
        glViewport(0, 0, FBO_WIDTH, FBO_HEIGHT);

        // Set up projection matrix
        // The pattern renderer places tiles at OpenGL Y = screenHeight - genesisY - 8.
        // For a 32-tile (256 pixel) tall background with screenHeight=224:
        // - Genesis Y=0 (top row) -> OpenGL Y = 224 - 0 - 8 = 216 (tile spans 216..224)
        // - Genesis Y=248 (bottom row) -> OpenGL Y = 224 - 248 - 8 = -32 (tile spans -32..-24)
        //
        // The full tilemap spans exactly -32..224 (256 pixels). Match that 1:1.
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();

        // Capture exactly 256 world units (matching Genesis VDP plane height)
        // to fit into the 256x256 FBO texture with 1:1 pixel mapping.
        int top = displayHeight; // 224: top boundary includes row 0's top edge
        int bottom = top - FBO_HEIGHT; // -32: bottom boundary includes row 31's bottom edge
        glOrtho(0, FBO_WIDTH, bottom, top, -1, 1);

        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();

        // Clear FBO with transparent black
        glClearColor(0, 0, 0, 0);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    }

    /**
     * End the tile rendering pass - unbind FBO and restore state.
     */
    public void endTilePass() {
        if (!initialized)
            return;

        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();

        glBindFramebuffer(GL_FRAMEBUFFER, 0);

        // Restore viewport
        glViewport(savedViewport[0], savedViewport[1], savedViewport[2], savedViewport[3]);
    }

    /**
     * Render the background with per-scanline scrolling using the shader.
     *
     * @param vScrollBG Vertical scroll offset for parallax
     */
    public void renderWithShader(float vScrollBG) {
        if (!initialized)
            return;

        // Upload H-scroll data to GPU
        hScrollBuffer.upload(hScrollData);

        // Bind shader
        shader.use();
        shader.cacheUniformLocations();

        // Set texture units
        shader.setBackgroundTexture(0); // FBO texture
        shader.setHScrollTexture(1); // H-scroll table

        // Get actual viewport dimensions for resolution independence
        int[] viewport = new int[4];
        glGetIntegerv(GL_VIEWPORT, viewport);
        float realWidth = (float) viewport[2];
        float realHeight = (float) viewport[3];

        // Set shader uniforms
        shader.setScreenDimensions(realWidth, realHeight);
        shader.setBGTextureDimensions(FBO_WIDTH, FBO_HEIGHT);
        shader.setVScrollBG(vScrollBG);
        shader.setViewportOffset((float) viewport[0], (float) viewport[1]);

        // Bind textures
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, fboTextureId);

        hScrollBuffer.bind(1);

        // Draw fullscreen quad
        drawFullscreenQuad();

        // Cleanup
        shader.stop();
        hScrollBuffer.unbind(1);
        glActiveTexture(GL_TEXTURE0);
    }

    /**
     * Draw a fullscreen quad covering the entire screen.
     * The shader handles H32 clipping internally.
     */
    private void drawFullscreenQuad() {
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        // Use Genesis screen coordinates (320x224)
        glOrtho(0, SCREEN_WIDTH, SCREEN_HEIGHT, 0, -1, 1);

        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();

        // Draw quad covering full screen - shader will clip to H32 viewport
        quadRenderer.draw(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);

        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
    }

    /**
     * Set the horizontal scroll value for all scanlines uniformly.
     *
     * @param scroll The scroll value in pixels
     */
    public void setUniformHScroll(int scroll) {
        for (int i = 0; i < SCREEN_HEIGHT; i++) {
            hScrollData[i] = scroll;
        }
    }

    /**
     * Set the horizontal scroll value for a specific scanline.
     *
     * @param scanline The scanline index (0-223)
     * @param scroll   The scroll value in pixels
     */
    public void setHScroll(int scanline, int scroll) {
        if (scanline >= 0 && scanline < SCREEN_HEIGHT) {
            hScrollData[scanline] = scroll;
        }
    }

    /**
     * Get the H-scroll data array for direct manipulation.
     * Useful for bulk updates based on segment animation.
     */
    public int[] getHScrollData() {
        return hScrollData;
    }

    /**
     * Apply a delta to all scanlines' H-scroll values.
     * Used for the per-frame parallax scroll update.
     *
     * @param delta The value to add to each scanline's scroll
     */
    public void addHScrollDelta(int delta) {
        for (int i = 0; i < SCREEN_HEIGHT; i++) {
            hScrollData[i] += delta;
        }
    }

    /**
     * Check if renderer is initialized.
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Get the FBO texture ID (for debugging).
     */
    public int getFBOTextureId() {
        return fboTextureId;
    }

    /**
     * Clean up OpenGL resources.
     */
    public void cleanup() {
        if (hScrollBuffer != null) {
            hScrollBuffer.cleanup();
            hScrollBuffer = null;
        }
        if (shader != null) {
            shader.cleanup();
            shader = null;
        }
        quadRenderer.cleanup();
        if (fboId > 0) {
            glDeleteFramebuffers(fboId);
            fboId = -1;
        }
        if (fboTextureId > 0) {
            glDeleteTextures(fboTextureId);
            fboTextureId = -1;
        }
        if (fboDepthId > 0) {
            glDeleteRenderbuffers(fboDepthId);
            fboDepthId = -1;
        }
        initialized = false;
        LOGGER.info("SpecialStageBackgroundRenderer cleaned up");
    }
}
