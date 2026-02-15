package uk.co.jamesj999.sonic.level.render;

import uk.co.jamesj999.sonic.graphics.GraphicsManager;
import uk.co.jamesj999.sonic.graphics.HScrollBuffer;
import uk.co.jamesj999.sonic.graphics.ParallaxShaderProgram;
import uk.co.jamesj999.sonic.graphics.QuadRenderer;

import java.io.IOException;
import java.util.logging.Logger;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Two-pass background renderer implementing GPU-based per-scanline scrolling.
 *
 * Emulates Mega Drive VDP horizontal interrupt behavior by:
 * 1. Rendering the background tilemap to an offscreen framebuffer (FBO)
 * 2. Drawing a fullscreen quad with a shader that samples per-line scroll
 * values
 *
 * This allows true per-scanline horizontal scrolling rather than the per-tile
 * approximation used in the CPU-based approach.
 */
public class BackgroundRenderer {

    private static final Logger LOGGER = Logger.getLogger(BackgroundRenderer.class.getName());

    // Default FBO size - can be adjusted per zone
    private static final int DEFAULT_FBO_WIDTH = 1024;
    private static final int DEFAULT_FBO_HEIGHT = 256;

    // Visible screen dimensions (Mega Drive resolution)
    public static final int SCREEN_WIDTH = 320;
    public static final int SCREEN_HEIGHT = 224;

    private int fboId = -1;
    private int fboTextureId = -1;
    private int fboDepthId = -1;

    private int fboWidth = DEFAULT_FBO_WIDTH;
    private int fboHeight = DEFAULT_FBO_HEIGHT;

    private HScrollBuffer hScrollBuffer;
    private ParallaxShaderProgram parallaxShader;
    private final QuadRenderer quadRenderer = new QuadRenderer();

    private boolean initialized = false;
    private final int[] savedViewport = new int[4];

    // Shimmer state for parallax compositing pass
    private int shimmerFrameCounter = 0;
    private int shimmerStyle = 0;
    private float shimmerWaterlineScreenY = 9999.0f;

    /**
     * Initialize the background renderer with FBO and shader.
     *
     * @param shaderPath Path to the parallax fragment shader
     */
    public void init(String shaderPath) throws IOException {
        if (initialized) {
            return;
        }

        // Initialize HScroll buffer
        hScrollBuffer = new HScrollBuffer();
        hScrollBuffer.init();

        // Load parallax shader
        parallaxShader = new ParallaxShaderProgram(shaderPath);
        parallaxShader.cacheUniformLocations();
        quadRenderer.init();

        // Create FBO for background tile rendering
        createFBO(fboWidth, fboHeight);

        initialized = true;
        LOGGER.info("BackgroundRenderer initialized with FBO " + fboWidth + "x" + fboHeight);
    }

    /**
     * Set shimmer state for the parallax compositing pass.
     * Called once per frame before renderWithScroll/renderWithScrollWide.
     */
    public void setShimmerState(int frameCounter, int shimmerStyle, float waterlineScreenY) {
        this.shimmerFrameCounter = frameCounter;
        this.shimmerStyle = shimmerStyle;
        this.shimmerWaterlineScreenY = waterlineScreenY;
    }

    public ParallaxShaderProgram getParallaxShader() {
        return parallaxShader;
    }

    /**
     * Create the framebuffer object and its attachments.
     */
    private void createFBO(int width, int height) {
        // Generate FBO
        fboId = glGenFramebuffers();

        // Generate texture for color attachment
        fboTextureId = glGenTextures();

        glBindTexture(GL_TEXTURE_2D, fboTextureId);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0,
                GL_RGBA, GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        glBindTexture(GL_TEXTURE_2D, 0);

        // Generate depth buffer
        fboDepthId = glGenRenderbuffers();

        glBindRenderbuffer(GL_RENDERBUFFER, fboDepthId);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT16, width, height);
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
            LOGGER.severe("FBO creation failed with status: " + status);
        }

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    /**
     * Begin the tile rendering pass - binds FBO and clears it.
     * After calling this, render background tiles using GPU tilemap.
     *
     * @param displayHeight The display pixel height (unused, kept for API compatibility)
     * @param gpuTilemap    True (always, GPU tilemap is the only supported path)
     */
    public void beginTilePass(int displayHeight, boolean gpuTilemap) {
        if (!initialized)
            return;

        // Save current viewport to restore later
        GraphicsManager gm = GraphicsManager.getInstance();
        savedViewport[0] = gm.getViewportX();
        savedViewport[1] = gm.getViewportY();
        savedViewport[2] = gm.getViewportWidth();
        savedViewport[3] = gm.getViewportHeight();

        glBindFramebuffer(GL_FRAMEBUFFER, fboId);
        glViewport(0, 0, fboWidth, fboHeight);

        // Note: The tilemap shader uses gl_FragCoord for positioning,
        // so no projection matrix uniform is needed. The viewport setup above
        // is sufficient for gl_FragCoord to work correctly.

        glClearColor(0, 0, 0, 0);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

    }

    /**
     * End the tile rendering pass - unbinds FBO.
     */
    public void endTilePass() {
        if (!initialized)
            return;

        glBindFramebuffer(GL_FRAMEBUFFER, 0);

        // Restore viewport
        glViewport(savedViewport[0], savedViewport[1], savedViewport[2], savedViewport[3]);
    }

    /**
     * Execute the scroll pass - renders the FBO with per-scanline scrolling.
     *
     * @param hScroll          Packed horizontal scroll array from ParallaxManager
     * @param vScrollBG        Vertical scroll offset for background
     * @param paletteTextureId ID of the combined palette texture
     * @param screenWidth      UNUSED (legacy)
     * @param screenHeight     UNUSED (legacy)
     */
    public void renderWithScroll(int[] hScroll, float vScrollBG,
            int paletteTextureId, int screenWidth, int screenHeight) {
        if (!initialized)
            return;

        // Upload scroll data to GPU
        hScrollBuffer.upload(hScroll);

        // Bind shader and set uniforms
        parallaxShader.use();
        parallaxShader.cacheUniformLocations();

        // Set texture units
        parallaxShader.setBackgroundTexture(0);
        parallaxShader.setHScrollTexture(1);
        parallaxShader.setPalette(2);

        // Get viewport for resolution independence
        GraphicsManager gm = GraphicsManager.getInstance();
        float viewportX = gm.getViewportX();
        float viewportY = gm.getViewportY();
        float realWidth = gm.getViewportWidth();
        float realHeight = gm.getViewportHeight();

        // Set dimensions and scroll
        parallaxShader.setScreenDimensions(realWidth, realHeight);
        parallaxShader.setBGTextureDimensions(fboWidth, fboHeight);
        parallaxShader.setVScrollBG(vScrollBG);
        parallaxShader.setViewportOffset(viewportX, viewportY);
        parallaxShader.setShimmerParams(shimmerFrameCounter, shimmerStyle, shimmerWaterlineScreenY);

        // Bind textures
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, fboTextureId);

        hScrollBuffer.bind(1);

        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_2D, paletteTextureId);

        // Draw fullscreen quad
        drawFullscreenQuad();

        // Cleanup
        parallaxShader.stop();
        hScrollBuffer.unbind(1);
        glActiveTexture(GL_TEXTURE0);
    }

    /**
     * Execute the scroll pass with wider FBO for per-scanline scrolling.
     *
     * @param hScroll          Packed horizontal scroll array from ParallaxManager
     * @param scrollMidpoint   The midpoint of the scroll range (hScroll values are
     *                         relative to this)
     * @param extraBuffer      Extra pixels on each side of the FBO
     * @param paletteTextureId ID of the combined palette texture
     * @param screenWidth      Display width in pixels
     * @param screenHeight     Display height in pixels
     */
    public void renderWithScrollWide(int[] hScroll, int scrollMidpoint, int extraBuffer,
            int fboVScroll, int paletteTextureId, int screenWidth, int screenHeight) {
        if (!initialized)
            return;

        // Upload scroll data to GPU
        hScrollBuffer.upload(hScroll);

        // Bind shader and set uniforms
        parallaxShader.use();
        parallaxShader.cacheUniformLocations();

        // Set texture units
        parallaxShader.setBackgroundTexture(0);
        parallaxShader.setHScrollTexture(1);
        parallaxShader.setPalette(2);

        // Get viewport for resolution independence
        GraphicsManager gm = GraphicsManager.getInstance();
        float viewportX = gm.getViewportX();
        float viewportY = gm.getViewportY();
        float realWidth = gm.getViewportWidth();
        float realHeight = gm.getViewportHeight();

        // Set dimensions and scroll
        parallaxShader.setScreenDimensions(realWidth, realHeight);
        parallaxShader.setBGTextureDimensions(fboWidth, fboHeight);

        // Pass scroll midpoint and extra buffer to shader
        parallaxShader.setScrollMidpoint(scrollMidpoint);
        parallaxShader.setExtraBuffer(extraBuffer);
        parallaxShader.setVScroll((float) fboVScroll);
        parallaxShader.setViewportOffset(viewportX, viewportY);
        parallaxShader.setShimmerParams(shimmerFrameCounter, shimmerStyle, shimmerWaterlineScreenY);

        // Bind textures
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, fboTextureId);

        hScrollBuffer.bind(1);

        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_2D, paletteTextureId);

        // Draw fullscreen quad
        drawFullscreenQuad();

        // Cleanup
        parallaxShader.stop();
        hScrollBuffer.unbind(1);
        glActiveTexture(GL_TEXTURE0);
    }

    /**
     * Draw a fullscreen quad for the parallax pass.
     * Note: The parallax shader uses gl_FragCoord for positioning,
     * so no projection matrix is needed. The quad coordinates are
     * arbitrary since the shader only cares about fragment screen position.
     */
    private void drawFullscreenQuad() {
        quadRenderer.draw(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);
    }

    /**
     * Resize the FBO for zones with larger backgrounds.
     */
    public void resizeFBO(int width, int height) {
        if (width == fboWidth && height == fboHeight) {
            return;
        }

        // Delete old FBO resources
        if (fboId > 0) {
            glDeleteFramebuffers(fboId);
        }
        if (fboTextureId > 0) {
            glDeleteTextures(fboTextureId);
        }
        if (fboDepthId > 0) {
            glDeleteRenderbuffers(fboDepthId);
        }

        fboWidth = width;
        fboHeight = height;
        createFBO(width, height);

        LOGGER.info("BackgroundRenderer FBO resized to " + width + "x" + height);
    }

    /**
     * Get the FBO texture ID (for debugging or external use).
     */
    public int getFBOTextureId() {
        return fboTextureId;
    }

    /**
     * Check if renderer is initialized.
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Clean up all OpenGL resources.
     */
    public void cleanup() {
        if (hScrollBuffer != null) {
            hScrollBuffer.cleanup();
        }
        if (parallaxShader != null) {
            parallaxShader.cleanup();
        }
        quadRenderer.cleanup();
        if (fboId > 0) {
            glDeleteFramebuffers(fboId);
        }
        if (fboTextureId > 0) {
            glDeleteTextures(fboTextureId);
        }
        if (fboDepthId > 0) {
            glDeleteRenderbuffers(fboDepthId);
        }
        initialized = false;
    }
}
