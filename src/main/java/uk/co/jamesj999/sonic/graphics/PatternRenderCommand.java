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
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;

/**
 * Optimized pattern render command that minimizes redundant GL state changes.
 * Uses modern OpenGL (VAOs, vertex attributes) for core profile compatibility.
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

    // Pre-allocated vertex buffers for transformed coordinates
    private static final FloatBuffer VERTEX_BUFFER = MemoryUtil.memAllocFloat(8);
    private static final FloatBuffer TEX_COORD_BUFFER = MemoryUtil.memAllocFloat(8);
    private static final FloatBuffer PALETTE_BUFFER = MemoryUtil.memAllocFloat(4);

    // VAO and VBOs for modern OpenGL (shared across all instances)
    private static int vaoId = 0;
    private static int vertexVboId = 0;
    private static int texCoordVboId = 0;
    private static int paletteVboId = 0;

    // Vertex attribute locations (standard layout matching shader_basic.vert)
    private static final int ATTRIB_POSITION = 0;
    private static final int ATTRIB_TEXCOORD = 1;
    private static final int ATTRIB_PALETTE = 2;

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

    private static void ensureVbos() {
        if (vaoId != 0) {
            return;
        }
        vaoId = glGenVertexArrays();
        vertexVboId = glGenBuffers();
        texCoordVboId = glGenBuffers();
        paletteVboId = glGenBuffers();
    }

    @Override
    public void execute(int cameraX, int cameraY, int cameraWidth, int cameraHeight) {
        ShaderProgram shaderProgram = getGraphicsManager().getShaderProgram();

        // Initialize persistent state once per batch of patterns
        if (!stateInitialized) {
            ensureVbos();
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            shaderProgram.use();
            shaderProgram.cacheUniformLocations();
            glUniform1i(shaderProgram.getPaletteLocation(), 0);
            glUniform1i(shaderProgram.getIndexedColorTextureLocation(), 1);

            // Set projection matrix uniform - REQUIRED for correct rendering
            int projectionLoc = glGetUniformLocation(shaderProgram.getProgramId(), "ProjectionMatrix");
            if (projectionLoc != -1) {
                uk.co.jamesj999.sonic.Engine engine = uk.co.jamesj999.sonic.Engine.getInstance();
                if (engine != null) {
                    float[] projMatrix = engine.getProjectionMatrixBuffer();
                    if (projMatrix != null) {
                        glUniformMatrix4fv(projectionLoc, false, projMatrix);
                    }
                }
            }

            // Set camera offset uniform
            // X is negated to scroll objects left when camera moves right
            // Y is NOT negated because vertex Y is already in screen space (flipped from Genesis coords)
            int cameraOffsetLoc = glGetUniformLocation(shaderProgram.getProgramId(), "CameraOffset");
            if (cameraOffsetLoc != -1) {
                glUniform2f(cameraOffsetLoc, -cameraX, cameraY);
            }

            // Bind VAO
            glBindVertexArray(vaoId);

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
        // Note: camera offset is now handled via uniform, so vertices are in world space
        float screenX = x;
        float screenY = y;

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

        // Fill vertex buffer (quad: bottom-left, bottom-right, top-right, top-left)
        VERTEX_BUFFER.clear();
        VERTEX_BUFFER.put(x0).put(y0); // Bottom-left
        VERTEX_BUFFER.put(x1).put(y0); // Bottom-right
        VERTEX_BUFFER.put(x1).put(y1); // Top-right
        VERTEX_BUFFER.put(x0).put(y1); // Top-left
        VERTEX_BUFFER.flip();

        // Fill texture coordinate buffer
        TEX_COORD_BUFFER.clear();
        TEX_COORD_BUFFER.put(u0).put(v0);
        TEX_COORD_BUFFER.put(u1).put(v0);
        TEX_COORD_BUFFER.put(u1).put(v1);
        TEX_COORD_BUFFER.put(u0).put(v1);
        TEX_COORD_BUFFER.flip();

        // Fill palette coordinate buffer (same palette index for all 4 vertices)
        PALETTE_BUFFER.clear();
        PALETTE_BUFFER.put(paletteIndex).put(paletteIndex).put(paletteIndex).put(paletteIndex);
        PALETTE_BUFFER.flip();

        // Upload and bind vertex data
        glBindBuffer(GL_ARRAY_BUFFER, vertexVboId);
        glBufferData(GL_ARRAY_BUFFER, VERTEX_BUFFER, GL_DYNAMIC_DRAW);
        glVertexAttribPointer(ATTRIB_POSITION, 2, GL_FLOAT, false, 0, 0L);
        glEnableVertexAttribArray(ATTRIB_POSITION);

        glBindBuffer(GL_ARRAY_BUFFER, texCoordVboId);
        glBufferData(GL_ARRAY_BUFFER, TEX_COORD_BUFFER, GL_DYNAMIC_DRAW);
        glVertexAttribPointer(ATTRIB_TEXCOORD, 2, GL_FLOAT, false, 0, 0L);
        glEnableVertexAttribArray(ATTRIB_TEXCOORD);

        glBindBuffer(GL_ARRAY_BUFFER, paletteVboId);
        glBufferData(GL_ARRAY_BUFFER, PALETTE_BUFFER, GL_DYNAMIC_DRAW);
        glVertexAttribPointer(ATTRIB_PALETTE, 1, GL_FLOAT, false, 0, 0L);
        glEnableVertexAttribArray(ATTRIB_PALETTE);

        glDrawArrays(GL_TRIANGLE_FAN, 0, 4);

        // Return to pool for reuse
        recycle();
    }

    /**
     * Clean up GL state after all patterns are rendered.
     * Call this after the last pattern command in a frame.
     */
    public static void cleanupFrameState() {
        if (stateInitialized) {
            glDisableVertexAttribArray(ATTRIB_POSITION);
            glDisableVertexAttribArray(ATTRIB_TEXCOORD);
            glDisableVertexAttribArray(ATTRIB_PALETTE);
            glBindBuffer(GL_ARRAY_BUFFER, 0);
            glBindVertexArray(0);
            ShaderProgram shaderProgram = getGraphicsManager().getShaderProgram();
            if (shaderProgram != null) {
                shaderProgram.stop();
            }
            glDisable(GL_BLEND);
            stateInitialized = false;
        }
    }

    /**
     * Cleanup VBOs and VAO.
     */
    public static void cleanup() {
        if (vaoId != 0) {
            glDeleteVertexArrays(vaoId);
            vaoId = 0;
        }
        if (vertexVboId != 0) {
            glDeleteBuffers(vertexVboId);
            vertexVboId = 0;
        }
        if (texCoordVboId != 0) {
            glDeleteBuffers(texCoordVboId);
            texCoordVboId = 0;
        }
        if (paletteVboId != 0) {
            glDeleteBuffers(paletteVboId);
            paletteVboId = 0;
        }
    }
}
