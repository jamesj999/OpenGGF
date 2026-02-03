package uk.co.jamesj999.sonic.graphics;

import org.lwjgl.system.MemoryUtil;
import uk.co.jamesj999.sonic.configuration.SonicConfiguration;
import uk.co.jamesj999.sonic.configuration.SonicConfigurationService;
import uk.co.jamesj999.sonic.level.PatternDesc;

import java.nio.FloatBuffer;
import java.util.ArrayDeque;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;

/**
 * Optimized pattern render command that minimizes redundant GL state changes.
 *
 * Optimizations applied:
 * 1. Uses cached uniform locations (eliminates string hash lookups)
 * 2. Uses static singleton for shader program reference
 * 3. Tracks last-used textures to avoid redundant binds
 * 4. Pre-computes transformed vertices instead of using matrix operations
 * 5. Object pooling to avoid per-pattern allocation
 */
public class PatternRenderCommand implements GLCommandable {

    // Object pool for command reuse
    private static final ArrayDeque<PatternRenderCommand> pool = new ArrayDeque<>(256);

    private int paletteTextureId;
    private float u0;
    private float v0;
    private float u1;
    private float v1;
    private int atlasIndex;
    private int paletteIndex;
    private boolean hFlip;
    private boolean vFlip;
    private int x;
    private int y;

    // Static state tracking for batch optimization
    private static int lastAtlasTextureId = -1;
    private static int lastPaletteTextureId = -1;
    private static int lastPaletteIndex = -1;
    private static boolean stateInitialized = false;

    // Pre-allocated vertex buffer for transformed coordinates
    private static final FloatBuffer VERTEX_BUFFER = MemoryUtil.memAllocFloat(8);
    private static final FloatBuffer TEX_COORD_BUFFER = MemoryUtil.memAllocFloat(8);

    // Screen height for Y coordinate transformation
    private static final int SCREEN_HEIGHT = SonicConfigurationService.getInstance()
            .getInt(SonicConfiguration.SCREEN_HEIGHT_PIXELS);

    // Cached GraphicsManager reference to avoid synchronized getInstance() calls
    private static GraphicsManager graphicsManager;

    private static GraphicsManager getGraphicsManager() {
        if (graphicsManager == null) {
            graphicsManager = GraphicsManager.getInstance();
        }
        return graphicsManager;
    }

    /**
     * Obtain a PatternRenderCommand from the pool or create a new one.
     */
    public static PatternRenderCommand obtain(PatternAtlas.Entry entry, int paletteTextureId, PatternDesc desc, int x, int y) {
        PatternRenderCommand cmd = pool.pollFirst();
        if (cmd == null) {
            cmd = new PatternRenderCommand();
        }
        cmd.init(entry, paletteTextureId, desc, x, y);
        return cmd;
    }

    private PatternRenderCommand() {
        // Private constructor for pooling
    }

    /**
     * @deprecated Use {@link #obtain(PatternAtlas.Entry, int, PatternDesc, int, int)} instead for pooled allocation.
     */
    @Deprecated
    public PatternRenderCommand(PatternAtlas.Entry entry, int paletteTextureId, PatternDesc desc, int x, int y) {
        init(entry, paletteTextureId, desc, x, y);
    }

    private void init(PatternAtlas.Entry entry, int paletteTextureId, PatternDesc desc, int x, int y) {
        this.paletteTextureId = paletteTextureId;
        this.u0 = entry.u0();
        this.v0 = entry.v0();
        this.u1 = entry.u1();
        this.v1 = entry.v1();
        this.atlasIndex = entry.atlasIndex();
        this.paletteIndex = desc.getPaletteIndex();
        this.hFlip = desc.getHFlip();
        this.vFlip = desc.getVFlip();
        this.x = x;
        // Genesis Y refers to the TOP of the pattern, so we subtract the pattern height
        // (8)
        // to get the OpenGL Y coordinate for the bottom of the quad
        this.y = SCREEN_HEIGHT - y - 8;
    }

    /**
     * Return this command to the pool for reuse.
     */
    public void recycle() {
        if (pool.size() < 512) { // Cap pool size to prevent unbounded growth
            pool.offerFirst(this);
        }
    }

    /**
     * Reset static state at the start of each frame.
     * Call this before beginning a new frame of rendering.
     */
    public static void resetFrameState() {
        lastAtlasTextureId = -1;
        lastPaletteTextureId = -1;
        lastPaletteIndex = -1;
        stateInitialized = false;
    }

    @Override
    public void execute(int cameraX, int cameraY, int cameraWidth, int cameraHeight) {
        ShaderProgram shaderProgram = getGraphicsManager().getShaderProgram();

        // Initialize persistent state once per batch of patterns
        if (!stateInitialized) {
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            shaderProgram.use();
            shaderProgram.cacheUniformLocations();
            glUniform1i(shaderProgram.getPaletteLocation(), 0);
            glUniform1i(shaderProgram.getIndexedColorTextureLocation(), 1);
            glEnableClientState(GL_VERTEX_ARRAY);
            glClientActiveTexture(GL_TEXTURE0);
            glEnableClientState(GL_TEXTURE_COORD_ARRAY);

            // If using water shader, bind underwater palette to texture unit 2
            if (shaderProgram instanceof WaterShaderProgram) {
                WaterShaderProgram waterShader = (WaterShaderProgram) shaderProgram;
                Integer underwaterPaletteId = getGraphicsManager().getUnderwaterPaletteTextureId();
                if (underwaterPaletteId != null) {
                    glActiveTexture(GL_TEXTURE2);
                    glBindTexture(GL_TEXTURE_2D, underwaterPaletteId);
                    int loc = waterShader.getUnderwaterPaletteLocation();
                    if (loc != -1) {
                        glUniform1i(loc, 2);
                    }
                    glActiveTexture(GL_TEXTURE0);
                }
            }

            stateInitialized = true;
        }

        // Only bind palette texture if it changed
        if (paletteTextureId != lastPaletteTextureId) {
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, paletteTextureId);
            lastPaletteTextureId = paletteTextureId;
        }

        // Only bind atlas texture if it changed
        Integer atlasTextureId = getGraphicsManager().getPatternAtlasTextureId(atlasIndex);
        if (atlasTextureId != null && atlasTextureId != lastAtlasTextureId) {
            glActiveTexture(GL_TEXTURE1);
            glBindTexture(GL_TEXTURE_2D, atlasTextureId);
            lastAtlasTextureId = atlasTextureId;
        }

        // Only update palette line uniform if it changed
        if (paletteIndex != lastPaletteIndex) {
            shaderProgram.setPaletteLine(paletteIndex);
            lastPaletteIndex = paletteIndex;
        }

        // Compute transformed vertices directly (avoids push/pop/translate/scale)
        float screenX = x - cameraX;
        float screenY = y + cameraY;

        // Bottom-left, bottom-right, top-right, top-left
        float x0 = screenX;
        float x1 = screenX + 8;
        float y0 = screenY;
        float y1 = screenY + 8;

        // Apply horizontal flip by swapping left/right
        if (hFlip) {
            float temp = x0;
            x0 = x1;
            x1 = temp;
        }

        // Apply vertical flip by swapping top/bottom
        // Note: VFlip=false means apply flip (original VDP behavior)
        if (!vFlip) {
            float temp = y0;
            y0 = y1;
            y1 = temp;
        }

        // Compute texture coordinates based on flips
        // Fill vertex buffer
        VERTEX_BUFFER.clear();
        VERTEX_BUFFER.put(x0).put(y0); // Bottom-left
        VERTEX_BUFFER.put(x1).put(y0); // Bottom-right
        VERTEX_BUFFER.put(x1).put(y1); // Top-right
        VERTEX_BUFFER.put(x0).put(y1); // Top-left
        VERTEX_BUFFER.flip();

        // Fill texture coordinate buffer (always the same, no flip needed here since we
        // flipped vertices)
        TEX_COORD_BUFFER.clear();
        TEX_COORD_BUFFER.put(u0).put(v0);
        TEX_COORD_BUFFER.put(u1).put(v0);
        TEX_COORD_BUFFER.put(u1).put(v1);
        TEX_COORD_BUFFER.put(u0).put(v1);
        TEX_COORD_BUFFER.flip();

        glVertexPointer(2, GL_FLOAT, 0, VERTEX_BUFFER);
        glClientActiveTexture(GL_TEXTURE0);
        glTexCoordPointer(2, GL_FLOAT, 0, TEX_COORD_BUFFER);
        glDrawArrays(GL_QUADS, 0, 4);

        // Return to pool for reuse
        recycle();
    }

    /**
     * Clean up GL state after all patterns are rendered.
     * Call this after the last pattern command in a frame.
     */
    public static void cleanupFrameState() {
        if (stateInitialized) {
            glDisableClientState(GL_VERTEX_ARRAY);
            glClientActiveTexture(GL_TEXTURE0);
            glDisableClientState(GL_TEXTURE_COORD_ARRAY);
            ShaderProgram shaderProgram = getGraphicsManager().getShaderProgram();
            if (shaderProgram != null) {
                shaderProgram.stop();
            }
            glDisable(GL_BLEND);
            stateInitialized = false;
        }
    }
}
