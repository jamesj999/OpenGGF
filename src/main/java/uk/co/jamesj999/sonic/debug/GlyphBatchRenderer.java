package uk.co.jamesj999.sonic.debug;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GL2GL3;
import com.jogamp.opengl.util.GLBuffers;
import uk.co.jamesj999.sonic.graphics.ShaderProgram;

import java.awt.*;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.ArrayDeque;
import java.util.logging.Logger;

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

    // Command pool for batch execution
    private final ArrayDeque<GlyphBatchCommand> commandPool = new ArrayDeque<>();

    /**
     * Initializes the glyph batch renderer.
     * Must be called with a valid GL context before rendering.
     */
    public void init(GL2 gl, Font font) {
        init(gl, font, 1.0f);
    }

    /**
     * Initializes the glyph batch renderer with scale factor.
     * Must be called with a valid GL context before rendering.
     */
    public void init(GL2 gl, Font font, float scaleFactor) {
        if (initialized || gl == null) {
            return;
        }

        // Check for instancing support
        supported = isInstancingSupported(gl);
        if (!supported) {
            LOGGER.warning("Instanced rendering not supported, glyph batch renderer disabled");
            return;
        }

        this.currentScale = scaleFactor;

        // Initialize glyph atlas with all font sizes (may be null after updateScale cleanup)
        if (atlas == null) {
            atlas = new GlyphAtlas();
        }
        atlas.init(gl, font, scaleFactor);
        if (!atlas.isInitialized()) {
            LOGGER.warning("Failed to initialize glyph atlas");
            return;
        }

        // Load shader program (reuse existing if available)
        if (shader == null) {
            try {
                shader = new ShaderProgram(gl, VERTEX_SHADER_PATH, FRAGMENT_SHADER_PATH);
            } catch (IOException e) {
                LOGGER.severe("Failed to load debug text shader: " + e.getMessage());
                return;
            }

            // Query attribute locations (only needed once per shader)
            int programId = shader.getProgramId();
            vertexPosLoc = gl.glGetAttribLocation(programId, "VertexPos");
            instancePosLoc = gl.glGetAttribLocation(programId, "InstancePos");
            instanceSizeLoc = gl.glGetAttribLocation(programId, "InstanceSize");
            instanceUv0Loc = gl.glGetAttribLocation(programId, "InstanceUv0");
            instanceUv1Loc = gl.glGetAttribLocation(programId, "InstanceUv1");
            instanceColorLoc = gl.glGetAttribLocation(programId, "InstanceColor");

            // Query uniform locations
            glyphAtlasLoc = gl.glGetUniformLocation(programId, "GlyphAtlas");
            texelSizeLoc = gl.glGetUniformLocation(programId, "TexelSize");
            outlineColorLoc = gl.glGetUniformLocation(programId, "OutlineColor");
        }

        // Initialize VBOs (reuses existing if already created)
        initBuffers(gl);

        initialized = true;
        LOGGER.info("Glyph batch renderer initialized");
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
    public void updateScale(GL2 gl, Font baseFont, float newScale) {
        if (Math.abs(newScale - currentScale) > 0.5f) {
            // Save viewport dimensions before cleanup
            int savedViewportWidth = viewportWidth;
            int savedViewportHeight = viewportHeight;

            // Clean up atlas (shader and VBOs can be reused)
            if (atlas != null) {
                atlas.cleanup(gl);
                atlas = null;
            }

            // Reinitialize with new scale
            initialized = false;
            init(gl, baseFont, newScale);

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
        return initialized && supported;
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
    public void end(GL2 gl) {
        if (!batchActive) {
            return;
        }
        batchActive = false;

        if (glyphCount == 0 || gl == null) {
            return;
        }

        GlyphBatchCommand command = obtainCommand();
        command.load(instanceData, glyphCount);
        command.execute(gl);
        recycleCommand(command);

        glyphCount = 0;
    }

    /**
     * Cleans up OpenGL resources.
     */
    public void cleanup(GL2 gl) {
        if (gl != null) {
            if (quadVboId != 0) {
                gl.glDeleteBuffers(1, new int[]{quadVboId}, 0);
            }
            if (instanceVboId != 0) {
                gl.glDeleteBuffers(1, new int[]{instanceVboId}, 0);
            }
        }
        quadVboId = 0;
        instanceVboId = 0;

        if (shader != null && gl != null) {
            shader.cleanup(gl);
        }
        shader = null;

        if (atlas != null && gl != null) {
            atlas.cleanup(gl);
        }
        atlas = null;

        initialized = false;
        commandPool.clear();
    }

    private void initBuffers(GL2 gl) {
        if (quadVboId != 0) {
            return;
        }

        int[] buffers = new int[2];
        gl.glGenBuffers(2, buffers, 0);
        quadVboId = buffers[0];
        instanceVboId = buffers[1];

        // Create unit quad (0,0) to (1,1)
        FloatBuffer quadBuffer = GLBuffers.newDirectFloatBuffer(8);
        quadBuffer.put(0f).put(0f);
        quadBuffer.put(1f).put(0f);
        quadBuffer.put(0f).put(1f);
        quadBuffer.put(1f).put(1f);
        quadBuffer.flip();

        gl.glBindBuffer(GL2.GL_ARRAY_BUFFER, quadVboId);
        gl.glBufferData(GL2.GL_ARRAY_BUFFER, (long) quadBuffer.capacity() * Float.BYTES,
                quadBuffer, GL2.GL_STATIC_DRAW);
        gl.glBindBuffer(GL2.GL_ARRAY_BUFFER, 0);
    }

    private boolean isInstancingSupported(GL2 gl) {
        if (!gl.isFunctionAvailable("glDrawArraysInstanced")) {
            return false;
        }
        if (!gl.isFunctionAvailable("glVertexAttribDivisor") &&
                !gl.isFunctionAvailable("glVertexAttribDivisorARB")) {
            return false;
        }
        return gl.isExtensionAvailable("GL_ARB_instanced_arrays")
                || gl.isExtensionAvailable("GL_EXT_instanced_arrays")
                || gl.isGL2GL3();
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
        private FloatBuffer instanceBuffer;
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

        void execute(GL2 gl) {
            if (glyphCount == 0 || shader == null || atlas == null) {
                return;
            }

            // Guard against invalid viewport dimensions
            if (viewportWidth <= 0 || viewportHeight <= 0) {
                LOGGER.warning("Invalid viewport dimensions: " + viewportWidth + "x" + viewportHeight);
                return;
            }

            // Set up viewport-space orthographic projection for screen-space text
            gl.glMatrixMode(GL2.GL_PROJECTION);
            gl.glPushMatrix();
            gl.glLoadIdentity();
            gl.glOrtho(0, viewportWidth, 0, viewportHeight, -1, 1);
            gl.glMatrixMode(GL2.GL_MODELVIEW);
            gl.glPushMatrix();
            gl.glLoadIdentity();

            shader.use(gl);

            // Set uniforms
            if (glyphAtlasLoc >= 0) {
                gl.glUniform1i(glyphAtlasLoc, 0);
            }
            if (texelSizeLoc >= 0) {
                float texelSize = 1.0f / atlas.getAtlasSize();
                gl.glUniform2f(texelSizeLoc, texelSize, texelSize);
            }
            if (outlineColorLoc >= 0) {
                gl.glUniform4f(outlineColorLoc, 0f, 0f, 0f, 1f);
            }

            // Bind glyph atlas texture
            gl.glActiveTexture(GL2.GL_TEXTURE0);
            gl.glBindTexture(GL2.GL_TEXTURE_2D, atlas.getTextureId());

            gl.glEnable(GL2.GL_BLEND);
            gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);

            GL2GL3 gl23 = gl.getGL2GL3();
            int stride = FLOATS_PER_GLYPH * Float.BYTES;

            // Bind quad VBO
            gl.glBindBuffer(GL2.GL_ARRAY_BUFFER, quadVboId);
            enableAttrib(gl, vertexPosLoc, 2, GL2.GL_FLOAT, 0, 0L);
            setDivisor(gl23, vertexPosLoc, 0);

            // Bind instance VBO
            gl.glBindBuffer(GL2.GL_ARRAY_BUFFER, instanceVboId);
            instanceBuffer.rewind();
            instanceBuffer.limit(floatCount);
            gl.glBufferData(GL2.GL_ARRAY_BUFFER, (long) floatCount * Float.BYTES,
                    instanceBuffer, GL2.GL_DYNAMIC_DRAW);

            // Set up instance attributes
            enableAttrib(gl, instancePosLoc, 2, GL2.GL_FLOAT, stride, 0L);
            enableAttrib(gl, instanceSizeLoc, 2, GL2.GL_FLOAT, stride, 2L * Float.BYTES);
            enableAttrib(gl, instanceUv0Loc, 2, GL2.GL_FLOAT, stride, 4L * Float.BYTES);
            enableAttrib(gl, instanceUv1Loc, 2, GL2.GL_FLOAT, stride, 6L * Float.BYTES);
            enableAttrib(gl, instanceColorLoc, 4, GL2.GL_FLOAT, stride, 8L * Float.BYTES);

            setDivisor(gl23, instancePosLoc, 1);
            setDivisor(gl23, instanceSizeLoc, 1);
            setDivisor(gl23, instanceUv0Loc, 1);
            setDivisor(gl23, instanceUv1Loc, 1);
            setDivisor(gl23, instanceColorLoc, 1);

            // Draw all glyphs in one call
            gl23.glDrawArraysInstanced(GL2.GL_TRIANGLE_STRIP, 0, 4, glyphCount);

            // Reset divisors
            setDivisor(gl23, instancePosLoc, 0);
            setDivisor(gl23, instanceSizeLoc, 0);
            setDivisor(gl23, instanceUv0Loc, 0);
            setDivisor(gl23, instanceUv1Loc, 0);
            setDivisor(gl23, instanceColorLoc, 0);

            // Disable attributes
            disableAttrib(gl, instanceColorLoc);
            disableAttrib(gl, instanceUv1Loc);
            disableAttrib(gl, instanceUv0Loc);
            disableAttrib(gl, instanceSizeLoc);
            disableAttrib(gl, instancePosLoc);
            disableAttrib(gl, vertexPosLoc);

            gl.glBindBuffer(GL2.GL_ARRAY_BUFFER, 0);

            shader.stop(gl);
            gl.glDisable(GL2.GL_BLEND);

            // Restore previous matrices
            gl.glMatrixMode(GL2.GL_MODELVIEW);
            gl.glPopMatrix();
            gl.glMatrixMode(GL2.GL_PROJECTION);
            gl.glPopMatrix();
            gl.glMatrixMode(GL2.GL_MODELVIEW);
        }

        private FloatBuffer ensureBuffer(FloatBuffer buffer, int required) {
            if (buffer == null) {
                return GLBuffers.newDirectFloatBuffer(MAX_GLYPHS_PER_BATCH * FLOATS_PER_GLYPH);
            }
            if (buffer.capacity() < required) {
                return GLBuffers.newDirectFloatBuffer(MAX_GLYPHS_PER_BATCH * FLOATS_PER_GLYPH);
            }
            return buffer;
        }

        private void enableAttrib(GL2 gl, int location, int size, int type, int stride, long offset) {
            if (location < 0) {
                return;
            }
            gl.glEnableVertexAttribArray(location);
            gl.glVertexAttribPointer(location, size, type, false, stride, offset);
        }

        private void disableAttrib(GL2 gl, int location) {
            if (location < 0) {
                return;
            }
            gl.glDisableVertexAttribArray(location);
        }

        private void setDivisor(GL2GL3 gl, int location, int divisor) {
            if (location < 0) {
                return;
            }
            gl.glVertexAttribDivisor(location, divisor);
        }
    }
}
