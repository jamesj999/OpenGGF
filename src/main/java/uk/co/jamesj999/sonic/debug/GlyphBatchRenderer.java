package uk.co.jamesj999.sonic.debug;

import org.lwjgl.system.MemoryUtil;
import uk.co.jamesj999.sonic.graphics.ShaderProgram;

import java.awt.*;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.ArrayDeque;
import java.util.logging.Logger;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL31.*;
import static org.lwjgl.opengl.GL33.*;

/**
 * GPU-accelerated batch renderer for debug text using instanced rendering.
 * Renders all text in a single draw call with shader-based outlining.
 * Supports multiple font sizes via the FontSize enum.
 */
public class GlyphBatchRenderer {
    private static final Logger LOGGER = Logger.getLogger(GlyphBatchRenderer.class.getName());

    private static final int MAX_GLYPHS_PER_BATCH = 4096;
    // x, y, w, h, u0, v0, u1, v1, r, g, b, a = 12 floats per glyph
    private static final int FLOATS_PER_GLYPH = 12;
    private static final int COMMAND_POOL_LIMIT = 4;

    private static final String VERTEX_SHADER_PATH = "shaders/shader_debug_text.vert";
    private static final String FRAGMENT_SHADER_PATH = "shaders/shader_debug_text.frag";

    private GlyphAtlas atlas;
    private ShaderProgram shader;

    private int quadVboId;
    private int instanceVboId;

    private final float[] instanceData = new float[MAX_GLYPHS_PER_BATCH * FLOATS_PER_GLYPH];
    private int glyphCount;
    private boolean batchActive;
    private boolean initialized;
    private boolean supported;

    // Attribute locations
    private int vertexPosLoc = -1;
    private int instancePosLoc = -1;
    private int instanceSizeLoc = -1;
    private int instanceUv0Loc = -1;
    private int instanceUv1Loc = -1;
    private int instanceColorLoc = -1;

    // Uniform locations
    private int glyphAtlasLoc = -1;
    private int texelSizeLoc = -1;
    private int outlineColorLoc = -1;

    // Viewport dimensions for coordinate conversion (default to common values to avoid 0)
    private int viewportWidth = 320;
    private int viewportHeight = 224;
    private float currentScale = 1.0f;

    // Reusable FloatBuffer for instance data - moved to outer class for proper reuse across frames
    private FloatBuffer instanceBuffer;

    // Command pool for batch execution
    private final ArrayDeque<GlyphBatchCommand> commandPool = new ArrayDeque<>();

    /**
     * Initializes the glyph batch renderer.
     * Must be called with a valid GL context before rendering.
     */
    public void init(Font font) {
        init(font, 1.0f);
    }

    /**
     * Initializes the glyph batch renderer with scale factor.
     * Must be called with a valid GL context before rendering.
     */
    public void init(Font font, float scaleFactor) {
        if (initialized) {
            return;
        }

        // Check for instancing support by parsing GL version
        // Instanced rendering requires GL 3.3+ (or ARB_instanced_arrays extension)
        supported = checkInstancingSupport();

        this.currentScale = scaleFactor;

        // Initialize glyph atlas with all font sizes (may be null after updateScale cleanup)
        if (atlas == null) {
            atlas = new GlyphAtlas();
        }
        atlas.init(font, scaleFactor);
        if (!atlas.isInitialized()) {
            LOGGER.warning("Failed to initialize glyph atlas");
            return;
        }

        // Only initialize shaders and VBOs if instancing is supported
        if (supported) {
            // Load shader program (reuse existing if available)
            if (shader == null) {
                try {
                    shader = new ShaderProgram(VERTEX_SHADER_PATH, FRAGMENT_SHADER_PATH);
                } catch (IOException e) {
                    LOGGER.severe("Failed to load debug text shader: " + e.getMessage());
                    supported = false;
                }

                if (shader != null) {
                    // Query attribute locations (only needed once per shader)
                    int programId = shader.getProgramId();
                    vertexPosLoc = glGetAttribLocation(programId, "VertexPos");
                    instancePosLoc = glGetAttribLocation(programId, "InstancePos");
                    instanceSizeLoc = glGetAttribLocation(programId, "InstanceSize");
                    instanceUv0Loc = glGetAttribLocation(programId, "InstanceUv0");
                    instanceUv1Loc = glGetAttribLocation(programId, "InstanceUv1");
                    instanceColorLoc = glGetAttribLocation(programId, "InstanceColor");

                    // Query uniform locations
                    glyphAtlasLoc = glGetUniformLocation(programId, "GlyphAtlas");
                    texelSizeLoc = glGetUniformLocation(programId, "TexelSize");
                    outlineColorLoc = glGetUniformLocation(programId, "OutlineColor");
                }
            }

            // Initialize VBOs (reuses existing if already created)
            if (supported) {
                initBuffers();
            }
        }

        initialized = true;
        if (supported) {
            LOGGER.info("Glyph batch renderer initialized with instanced rendering");
        } else {
            LOGGER.info("Glyph batch renderer initialized with fallback immediate mode");
        }
    }

    /**
     * Updates the viewport dimensions for coordinate conversion.
     */
    public void updateViewport(int width, int height) {
        this.viewportWidth = width;
        this.viewportHeight = height;
    }

    /**
     * Reinitializes the renderer if scale has changed significantly.
     * Call this when window is resized to keep text crisp.
     */
    public void updateScale(Font baseFont, float newScale) {
        if (Math.abs(newScale - currentScale) > 0.5f) {
            // Save viewport dimensions before cleanup
            int savedViewportWidth = viewportWidth;
            int savedViewportHeight = viewportHeight;

            // Clean up atlas (shader and VBOs can be reused)
            if (atlas != null) {
                atlas.cleanup();
                atlas = null;
            }

            // Reinitialize with new scale
            initialized = false;
            init(baseFont, newScale);

            // Restore viewport dimensions
            viewportWidth = savedViewportWidth;
            viewportHeight = savedViewportHeight;
        }
    }

    /**
     * Gets the current scale factor.
     */
    public float getCurrentScale() {
        return currentScale;
    }

    /**
     * Checks if the renderer is initialized and ready to use.
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Checks if the GL context supports instanced rendering (GL 3.3+).
     * Returns false on older contexts like macOS's OpenGL 2.1.
     */
    private boolean checkInstancingSupport() {
        try {
            String versionStr = glGetString(GL_VERSION);
            if (versionStr == null) {
                LOGGER.warning("Could not get GL version string");
                return false;
            }
            // Parse version like "2.1 INTEL-..." or "4.1 NVIDIA..."
            String[] parts = versionStr.split("[\\s.]");
            if (parts.length >= 2) {
                int major = Integer.parseInt(parts[0]);
                int minor = Integer.parseInt(parts[1]);
                // Need GL 3.3+ for glVertexAttribDivisor
                if (major > 3 || (major == 3 && minor >= 3)) {
                    return true;
                }
            }
            LOGGER.info("GL version " + versionStr + " does not support instanced rendering; debug text disabled");
            return false;
        } catch (Exception e) {
            LOGGER.warning("Error checking GL version: " + e.getMessage());
            return false;
        }
    }

    /**
     * Begins a new text rendering batch.
     * Must be called before any draw methods.
     */
    public void begin() {
        if (!initialized) {
            return;
        }
        glyphCount = 0;
        batchActive = true;
    }

    /**
     * Checks if a batch is currently active.
     */
    public boolean isBatchActive() {
        return batchActive;
    }

    /**
     * Draws text at the specified position with the given color and font size.
     * Position is in viewport coordinates (origin at bottom-left).
     */
    public void drawText(String text, int x, int y, Color color, FontSize fontSize) {
        if (!batchActive || text == null || text.isEmpty()) {
            return;
        }

        float r = color.getRed() / 255f;
        float g = color.getGreen() / 255f;
        float b = color.getBlue() / 255f;
        float a = color.getAlpha() / 255f;

        int cursorX = x;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            GlyphAtlas.GlyphInfo glyph = atlas.getGlyph(c, fontSize);
            if (glyph == null) {
                continue;
            }

            if (glyphCount >= MAX_GLYPHS_PER_BATCH) {
                LOGGER.warning("Glyph batch overflow, some text may not render");
                return;
            }

            int offset = glyphCount * FLOATS_PER_GLYPH;
            instanceData[offset] = cursorX + glyph.xOffset;
            instanceData[offset + 1] = y + glyph.yOffset;
            instanceData[offset + 2] = glyph.width;
            instanceData[offset + 3] = glyph.height;
            instanceData[offset + 4] = glyph.u0;
            instanceData[offset + 5] = glyph.v0;
            instanceData[offset + 6] = glyph.u1;
            instanceData[offset + 7] = glyph.v1;
            instanceData[offset + 8] = r;
            instanceData[offset + 9] = g;
            instanceData[offset + 10] = b;
            instanceData[offset + 11] = a;

            glyphCount++;
            cursorX += glyph.advance;
        }
    }

    /**
     * Draws text at the specified position with the given color using the default (MEDIUM) font size.
     * Position is in viewport coordinates (origin at bottom-left).
     */
    public void drawText(String text, int x, int y, Color color) {
        drawText(text, x, y, color, FontSize.MEDIUM);
    }

    /**
     * Draws outlined text at the specified position with the given font size.
     * This is the primary method for debug overlay text.
     * The outline is rendered in the fragment shader for efficiency.
     */
    public void drawTextOutlined(String text, int x, int y, Color fillColor, FontSize fontSize) {
        // The outline is handled in the shader, so just draw regular text
        drawText(text, x, y, fillColor, fontSize);
    }

    /**
     * Draws outlined text at the specified position using the default (MEDIUM) font size.
     * This is the primary method for debug overlay text.
     * The outline is rendered in the fragment shader for efficiency.
     */
    public void drawTextOutlined(String text, int x, int y, Color fillColor) {
        drawTextOutlined(text, x, y, fillColor, FontSize.MEDIUM);
    }

    /**
     * Measures the width of a string in pixels at a specific font size.
     */
    public int measureTextWidth(String text, FontSize fontSize) {
        if (atlas == null) {
            return 0;
        }
        return atlas.measureTextWidth(text, fontSize);
    }

    /**
     * Measures the width of a string in pixels using the default (MEDIUM) font size.
     */
    public int measureTextWidth(String text) {
        return measureTextWidth(text, FontSize.MEDIUM);
    }

    /**
     * Gets the line height for a specific font size.
     */
    public int getLineHeight(FontSize fontSize) {
        if (atlas == null) {
            return 12;
        }
        return atlas.getLineHeight(fontSize);
    }

    /**
     * Gets the line height for the default (MEDIUM) font size.
     */
    public int getLineHeight() {
        return getLineHeight(FontSize.MEDIUM);
    }

    /**
     * Ends the current batch and executes the draw command.
     */
    public void end() {
        if (!batchActive) {
            return;
        }
        batchActive = false;

        if (glyphCount == 0) {
            return;
        }

        if (supported) {
            // Use instanced rendering
            GlyphBatchCommand command = obtainCommand();
            command.load(instanceData, glyphCount);
            command.execute();
            recycleCommand(command);
        } else {
            // Use immediate mode fallback
            renderImmediateMode();
        }

        glyphCount = 0;
    }

    /**
     * Fallback immediate mode rendering for OpenGL 2.1 contexts.
     * Renders glyphs one at a time using glBegin/glEnd.
     */
    private void renderImmediateMode() {
        if (atlas == null || atlas.getTextureId() == 0) {
            return;
        }

        // Save GL state manually (glPushAttrib not reliable in LWJGL)
        boolean blendWasEnabled = glIsEnabled(GL_BLEND);
        boolean texture2dWasEnabled = glIsEnabled(GL_TEXTURE_2D);

        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        glBindTexture(GL_TEXTURE_2D, atlas.getTextureId());

        // Set up orthographic projection for 2D rendering
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, viewportWidth, 0, viewportHeight, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();

        // Render each glyph - batch all quads together for better performance
        // First pass: outlines (black)
        glColor4f(0, 0, 0, 1);
        glBegin(GL_QUADS);
        for (int i = 0; i < glyphCount; i++) {
            int offset = i * FLOATS_PER_GLYPH;
            float x = instanceData[offset];
            float y = instanceData[offset + 1];
            float w = instanceData[offset + 2];
            float h = instanceData[offset + 3];
            float u0 = instanceData[offset + 4];
            float v0 = instanceData[offset + 5];
            float u1 = instanceData[offset + 6];
            float v1 = instanceData[offset + 7];

            // Draw outline in 4 cardinal directions only (faster than 8)
            for (int d = 0; d < 4; d++) {
                int dx = (d == 0) ? -1 : (d == 1) ? 1 : 0;
                int dy = (d == 2) ? -1 : (d == 3) ? 1 : 0;
                glTexCoord2f(u0, v0); glVertex2f(x + dx, y + dy);
                glTexCoord2f(u1, v0); glVertex2f(x + w + dx, y + dy);
                glTexCoord2f(u1, v1); glVertex2f(x + w + dx, y + h + dy);
                glTexCoord2f(u0, v1); glVertex2f(x + dx, y + h + dy);
            }
        }
        glEnd();

        // Second pass: main glyphs with color
        glBegin(GL_QUADS);
        for (int i = 0; i < glyphCount; i++) {
            int offset = i * FLOATS_PER_GLYPH;
            float x = instanceData[offset];
            float y = instanceData[offset + 1];
            float w = instanceData[offset + 2];
            float h = instanceData[offset + 3];
            float u0 = instanceData[offset + 4];
            float v0 = instanceData[offset + 5];
            float u1 = instanceData[offset + 6];
            float v1 = instanceData[offset + 7];
            float r = instanceData[offset + 8];
            float g = instanceData[offset + 9];
            float b = instanceData[offset + 10];
            float a = instanceData[offset + 11];

            glColor4f(r, g, b, a);
            glTexCoord2f(u0, v0); glVertex2f(x, y);
            glTexCoord2f(u1, v0); glVertex2f(x + w, y);
            glTexCoord2f(u1, v1); glVertex2f(x + w, y + h);
            glTexCoord2f(u0, v1); glVertex2f(x, y + h);
        }
        glEnd();

        // Restore GL state
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();

        glBindTexture(GL_TEXTURE_2D, 0);

        // Restore previous state
        if (!blendWasEnabled) glDisable(GL_BLEND);
        if (!texture2dWasEnabled) glDisable(GL_TEXTURE_2D);
    }

    /**
     * Cleans up OpenGL resources.
     */
    public void cleanup() {
        if (quadVboId != 0) {
            glDeleteBuffers(quadVboId);
        }
        if (instanceVboId != 0) {
            glDeleteBuffers(instanceVboId);
        }
        quadVboId = 0;
        instanceVboId = 0;

        if (shader != null) {
            shader.cleanup();
        }
        shader = null;

        if (atlas != null) {
            atlas.cleanup();
        }
        atlas = null;

        if (instanceBuffer != null) {
            MemoryUtil.memFree(instanceBuffer);
            instanceBuffer = null;
        }

        initialized = false;
        commandPool.clear();
    }

    private void initBuffers() {
        if (quadVboId != 0) {
            return;
        }

        quadVboId = glGenBuffers();
        instanceVboId = glGenBuffers();

        // Create unit quad (0,0) to (1,1)
        FloatBuffer quadBuffer = MemoryUtil.memAllocFloat(8);
        quadBuffer.put(0f).put(0f);
        quadBuffer.put(1f).put(0f);
        quadBuffer.put(0f).put(1f);
        quadBuffer.put(1f).put(1f);
        quadBuffer.flip();

        glBindBuffer(GL_ARRAY_BUFFER, quadVboId);
        glBufferData(GL_ARRAY_BUFFER, quadBuffer, GL_STATIC_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, 0);

        MemoryUtil.memFree(quadBuffer);
    }

    private GlyphBatchCommand obtainCommand() {
        GlyphBatchCommand command = commandPool.pollFirst();
        if (command == null) {
            command = new GlyphBatchCommand();
        }
        return command;
    }

    private void recycleCommand(GlyphBatchCommand command) {
        if (commandPool.size() < COMMAND_POOL_LIMIT) {
            commandPool.addLast(command);
        }
    }

    /**
     * Inner class representing a batch of glyphs to render.
     */
    private class GlyphBatchCommand {
        private int glyphCount;
        private int floatCount;

        void load(float[] data, int count) {
            this.glyphCount = count;
            this.floatCount = count * FLOATS_PER_GLYPH;
            instanceBuffer = ensureBuffer(instanceBuffer, floatCount);
            instanceBuffer.clear();
            instanceBuffer.put(data, 0, floatCount);
            instanceBuffer.flip();
        }

        void execute() {
            if (glyphCount == 0 || shader == null || atlas == null) {
                return;
            }

            // Guard against invalid viewport dimensions
            if (viewportWidth <= 0 || viewportHeight <= 0) {
                LOGGER.warning("Invalid viewport dimensions: " + viewportWidth + "x" + viewportHeight);
                return;
            }

            // Set up viewport-space orthographic projection for screen-space text
            glMatrixMode(GL_PROJECTION);
            glPushMatrix();
            glLoadIdentity();
            glOrtho(0, viewportWidth, 0, viewportHeight, -1, 1);
            glMatrixMode(GL_MODELVIEW);
            glPushMatrix();
            glLoadIdentity();

            shader.use();

            // Set uniforms
            if (glyphAtlasLoc >= 0) {
                glUniform1i(glyphAtlasLoc, 0);
            }
            if (texelSizeLoc >= 0) {
                float texelSize = 1.0f / atlas.getAtlasSize();
                glUniform2f(texelSizeLoc, texelSize, texelSize);
            }
            if (outlineColorLoc >= 0) {
                glUniform4f(outlineColorLoc, 0f, 0f, 0f, 1f);
            }

            // Bind glyph atlas texture
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, atlas.getTextureId());

            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

            int stride = FLOATS_PER_GLYPH * Float.BYTES;

            // Bind quad VBO
            glBindBuffer(GL_ARRAY_BUFFER, quadVboId);
            enableAttrib(vertexPosLoc, 2, GL_FLOAT, 0, 0L);
            setDivisor(vertexPosLoc, 0);

            // Bind instance VBO
            glBindBuffer(GL_ARRAY_BUFFER, instanceVboId);
            instanceBuffer.rewind();
            instanceBuffer.limit(floatCount);
            glBufferData(GL_ARRAY_BUFFER, instanceBuffer, GL_DYNAMIC_DRAW);

            // Set up instance attributes
            enableAttrib(instancePosLoc, 2, GL_FLOAT, stride, 0L);
            enableAttrib(instanceSizeLoc, 2, GL_FLOAT, stride, 2L * Float.BYTES);
            enableAttrib(instanceUv0Loc, 2, GL_FLOAT, stride, 4L * Float.BYTES);
            enableAttrib(instanceUv1Loc, 2, GL_FLOAT, stride, 6L * Float.BYTES);
            enableAttrib(instanceColorLoc, 4, GL_FLOAT, stride, 8L * Float.BYTES);

            setDivisor(instancePosLoc, 1);
            setDivisor(instanceSizeLoc, 1);
            setDivisor(instanceUv0Loc, 1);
            setDivisor(instanceUv1Loc, 1);
            setDivisor(instanceColorLoc, 1);

            // Draw all glyphs in one call
            glDrawArraysInstanced(GL_TRIANGLE_STRIP, 0, 4, glyphCount);

            // Reset divisors
            setDivisor(instancePosLoc, 0);
            setDivisor(instanceSizeLoc, 0);
            setDivisor(instanceUv0Loc, 0);
            setDivisor(instanceUv1Loc, 0);
            setDivisor(instanceColorLoc, 0);

            // Disable attributes
            disableAttrib(instanceColorLoc);
            disableAttrib(instanceUv1Loc);
            disableAttrib(instanceUv0Loc);
            disableAttrib(instanceSizeLoc);
            disableAttrib(instancePosLoc);
            disableAttrib(vertexPosLoc);

            glBindBuffer(GL_ARRAY_BUFFER, 0);

            shader.stop();
            glDisable(GL_BLEND);

            // Restore previous matrices
            glMatrixMode(GL_MODELVIEW);
            glPopMatrix();
            glMatrixMode(GL_PROJECTION);
            glPopMatrix();
            glMatrixMode(GL_MODELVIEW);
        }

        private FloatBuffer ensureBuffer(FloatBuffer buffer, int required) {
            if (buffer == null) {
                return MemoryUtil.memAllocFloat(MAX_GLYPHS_PER_BATCH * FLOATS_PER_GLYPH);
            }
            if (buffer.capacity() < required) {
                MemoryUtil.memFree(buffer);
                return MemoryUtil.memAllocFloat(MAX_GLYPHS_PER_BATCH * FLOATS_PER_GLYPH);
            }
            return buffer;
        }

        private void enableAttrib(int location, int size, int type, int stride, long offset) {
            if (location < 0) {
                return;
            }
            glEnableVertexAttribArray(location);
            glVertexAttribPointer(location, size, type, false, stride, offset);
        }

        private void disableAttrib(int location) {
            if (location < 0) {
                return;
            }
            glDisableVertexAttribArray(location);
        }

        private void setDivisor(int location, int divisor) {
            if (location < 0) {
                return;
            }
            glVertexAttribDivisor(location, divisor);
        }
    }
}
