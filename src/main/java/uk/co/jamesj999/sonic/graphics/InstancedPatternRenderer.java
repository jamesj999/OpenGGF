package uk.co.jamesj999.sonic.graphics;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GL2GL3;
import com.jogamp.opengl.util.GLBuffers;
import uk.co.jamesj999.sonic.configuration.SonicConfiguration;
import uk.co.jamesj999.sonic.configuration.SonicConfigurationService;
import uk.co.jamesj999.sonic.level.PatternDesc;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.ArrayDeque;
import java.util.logging.Logger;

/**
 * Instanced renderer for batched 8x8 pattern draws.
 * Uses a single quad VBO with per-instance attributes.
 */
public class InstancedPatternRenderer {
    private static final Logger LOGGER = Logger.getLogger(InstancedPatternRenderer.class.getName());

    private static final int MAX_PATTERNS_PER_BATCH = 4096;
    private static final int FLOATS_PER_INSTANCE = 10; // x,y,w,h,u0,v0,u1,v1,palette,highPriority
    private static final int COMMAND_POOL_LIMIT = 8;
    private static final String PRIORITY_FRAGMENT_SHADER_PATH = "shaders/shader_instanced_priority.glsl";

    private final int screenHeight;
    private final float[] instanceData;

    private int instanceCount;
    private boolean batchActive;
    private boolean supported;
    private boolean initialized;

    private ShaderProgram instancedShader;
    private WaterShaderProgram instancedWaterShader;
    private ShaderProgram instancedPriorityShader;  // Priority-aware instanced shader

    private int quadVboId;
    private int instanceVboId;

    private AttribLocations defaultAttribs;
    private AttribLocations waterAttribs;
    private AttribLocations priorityAttribs;

    // Cached uniform locations for priority shader to avoid glGetUniformLocation calls
    private int cachedTilePriorityTexLoc = -1;
    private int cachedScreenSizeLoc = -1;
    private int cachedViewportOffsetLoc = -1;

    // Cached uniform locations for underwater palette support in priority shader
    private int cachedUnderwaterPaletteLoc = -1;
    private int cachedWaterlineScreenYLoc = -1;
    private int cachedWindowHeightLoc = -1;
    private int cachedScreenHeightLoc = -1;
    private int cachedWaterEnabledLoc = -1;

    private final ArrayDeque<InstancedBatchCommand> commandPool = new ArrayDeque<>();

    public InstancedPatternRenderer() {
        this.screenHeight = SonicConfigurationService.getInstance().getInt(SonicConfiguration.SCREEN_HEIGHT_PIXELS);
        this.instanceData = new float[MAX_PATTERNS_PER_BATCH * FLOATS_PER_INSTANCE];
    }

    public void init(GL2 gl, String vertexShaderPath, String fragmentShaderPath, String waterFragmentPath)
            throws IOException {
        if (initialized || gl == null) {
            return;
        }
        supported = isInstancingSupported(gl);
        if (!supported) {
            LOGGER.info("Instanced rendering not supported; falling back to non-instanced batching.");
            return;
        }
        instancedShader = new ShaderProgram(gl, vertexShaderPath, fragmentShaderPath);
        instancedShader.cacheUniformLocations(gl);
        instancedWaterShader = new WaterShaderProgram(gl, vertexShaderPath, waterFragmentPath);
        instancedWaterShader.cacheUniformLocations(gl);
        instancedPriorityShader = new ShaderProgram(gl, vertexShaderPath, PRIORITY_FRAGMENT_SHADER_PATH);
        instancedPriorityShader.cacheUniformLocations(gl);

        defaultAttribs = queryAttribLocations(gl, instancedShader);
        waterAttribs = queryAttribLocations(gl, instancedWaterShader);
        priorityAttribs = queryAttribLocations(gl, instancedPriorityShader);

        // Cache uniform locations for priority shader
        int priorityProgramId = instancedPriorityShader.getProgramId();
        cachedTilePriorityTexLoc = gl.glGetUniformLocation(priorityProgramId, "TilePriorityTexture");
        cachedScreenSizeLoc = gl.glGetUniformLocation(priorityProgramId, "ScreenSize");
        cachedViewportOffsetLoc = gl.glGetUniformLocation(priorityProgramId, "ViewportOffset");

        // Cache underwater palette uniform locations for priority shader
        cachedUnderwaterPaletteLoc = gl.glGetUniformLocation(priorityProgramId, "UnderwaterPalette");
        cachedWaterlineScreenYLoc = gl.glGetUniformLocation(priorityProgramId, "WaterlineScreenY");
        cachedWindowHeightLoc = gl.glGetUniformLocation(priorityProgramId, "WindowHeight");
        cachedScreenHeightLoc = gl.glGetUniformLocation(priorityProgramId, "ScreenHeight");
        cachedWaterEnabledLoc = gl.glGetUniformLocation(priorityProgramId, "WaterEnabled");

        initBuffers(gl);
        initialized = true;
        LOGGER.info("Instanced pattern renderer initialized.");
    }

    public boolean isSupported() {
        return supported;
    }

    public ShaderProgram getInstancedShaderProgram() {
        return instancedShader;
    }

    public WaterShaderProgram getInstancedWaterShaderProgram() {
        return instancedWaterShader;
    }

    public void beginBatch() {
        instanceCount = 0;
        batchActive = true;
    }

    public boolean isBatchActive() {
        return batchActive;
    }

    public boolean addPattern(PatternAtlas.Entry entry, int paletteIndex, PatternDesc desc, int x, int y) {
        if (!batchActive || instanceCount >= MAX_PATTERNS_PER_BATCH) {
            return false;
        }
        int screenY = screenHeight - y - 8;
        float u0 = entry.u0();
        float u1 = entry.u1();
        float v0 = entry.v0();
        float v1 = entry.v1();
        if (desc.getHFlip()) {
            float tmp = u0;
            u0 = u1;
            u1 = tmp;
        }
        if (!desc.getVFlip()) {
            float tmp = v0;
            v0 = v1;
            v1 = tmp;
        }

        // Get current sprite priority from GraphicsManager
        GraphicsManager gm = GraphicsManager.getInstance();
        float highPriority = gm.getCurrentSpriteHighPriority() ? 1.0f : 0.0f;

        int offset = instanceCount * FLOATS_PER_INSTANCE;
        instanceData[offset] = x;
        instanceData[offset + 1] = screenY;
        instanceData[offset + 2] = 8f;
        instanceData[offset + 3] = 8f;
        instanceData[offset + 4] = u0;
        instanceData[offset + 5] = v0;
        instanceData[offset + 6] = u1;
        instanceData[offset + 7] = v1;
        instanceData[offset + 8] = paletteIndex;
        instanceData[offset + 9] = highPriority;
        instanceCount++;
        return true;
    }

    public boolean addStripPattern(PatternAtlas.Entry entry, int paletteIndex, PatternDesc desc,
            int x, int y, int stripIndex) {
        if (!batchActive || instanceCount >= MAX_PATTERNS_PER_BATCH) {
            return false;
        }
        int screenY = screenHeight - y - 2;

        int rowTop = stripIndex * 2;
        int rowBottom = stripIndex * 2 + 1;
        float rowStep = (entry.v1() - entry.v0()) / 8.0f;
        float stripTop = entry.v0() + rowStep * ((7 - rowTop) + 0.5f);
        float stripBottom = entry.v0() + rowStep * ((7 - rowBottom) + 0.5f);

        float u0;
        float u1;
        if (desc.getHFlip()) {
            u0 = entry.u1();
            u1 = entry.u0();
        } else {
            u0 = entry.u0();
            u1 = entry.u1();
        }

        float v0;
        float v1;
        if (desc.getVFlip()) {
            v0 = stripTop;
            v1 = stripBottom;
        } else {
            v0 = stripBottom;
            v1 = stripTop;
        }

        // Get current sprite priority from GraphicsManager
        GraphicsManager gm = GraphicsManager.getInstance();
        float highPriority = gm.getCurrentSpriteHighPriority() ? 1.0f : 0.0f;

        int offset = instanceCount * FLOATS_PER_INSTANCE;
        instanceData[offset] = x;
        instanceData[offset + 1] = screenY;
        instanceData[offset + 2] = 8f;
        instanceData[offset + 3] = 2f;
        instanceData[offset + 4] = u0;
        instanceData[offset + 5] = v0;
        instanceData[offset + 6] = u1;
        instanceData[offset + 7] = v1;
        instanceData[offset + 8] = paletteIndex;
        instanceData[offset + 9] = highPriority;
        instanceCount++;
        return true;
    }

    public GLCommandable endBatch() {
        if (instanceCount == 0) {
            batchActive = false;
            return null;
        }
        GraphicsManager gm = GraphicsManager.getInstance();
        boolean usePriority = gm.isUseSpritePriorityShader() && instancedPriorityShader != null;

        InstancedBatchCommand command = obtainCommand();
        command.load(instanceData, instanceCount, usePriority);
        instanceCount = 0;
        batchActive = false;
        return command;
    }

    public void cleanup(GL2 gl) {
        if (gl != null) {
            if (quadVboId != 0) {
                gl.glDeleteBuffers(1, new int[] { quadVboId }, 0);
            }
            if (instanceVboId != 0) {
                gl.glDeleteBuffers(1, new int[] { instanceVboId }, 0);
            }
        }
        quadVboId = 0;
        instanceVboId = 0;
        if (instancedShader != null && gl != null) {
            instancedShader.cleanup(gl);
        }
        if (instancedWaterShader != null && gl != null) {
            instancedWaterShader.cleanup(gl);
        }
        if (instancedPriorityShader != null && gl != null) {
            instancedPriorityShader.cleanup(gl);
        }
        instancedShader = null;
        instancedWaterShader = null;
        instancedPriorityShader = null;
        defaultAttribs = null;
        waterAttribs = null;
        priorityAttribs = null;
        initialized = false;
        supported = false;
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

        FloatBuffer quadBuffer = GLBuffers.newDirectFloatBuffer(8);
        quadBuffer.put(0f).put(0f);
        quadBuffer.put(1f).put(0f);
        quadBuffer.put(0f).put(1f);
        quadBuffer.put(1f).put(1f);
        quadBuffer.flip();

        gl.glBindBuffer(GL2.GL_ARRAY_BUFFER, quadVboId);
        gl.glBufferData(GL2.GL_ARRAY_BUFFER, (long) quadBuffer.capacity() * Float.BYTES, quadBuffer,
                GL2.GL_STATIC_DRAW);
        gl.glBindBuffer(GL2.GL_ARRAY_BUFFER, 0);
    }

    private boolean isInstancingSupported(GL2 gl) {
        if (!gl.isFunctionAvailable("glDrawArraysInstanced")) {
            return false;
        }
        if (!gl.isFunctionAvailable("glVertexAttribDivisor") && !gl.isFunctionAvailable("glVertexAttribDivisorARB")) {
            return false;
        }
        return gl.isExtensionAvailable("GL_ARB_instanced_arrays")
                || gl.isExtensionAvailable("GL_EXT_instanced_arrays")
                || gl.isGL2GL3();
    }

    private AttribLocations queryAttribLocations(GL2 gl, ShaderProgram program) {
        int programId = program.getProgramId();
        return new AttribLocations(
                gl.glGetAttribLocation(programId, "VertexPos"),
                gl.glGetAttribLocation(programId, "InstancePos"),
                gl.glGetAttribLocation(programId, "InstanceSize"),
                gl.glGetAttribLocation(programId, "InstanceUv0"),
                gl.glGetAttribLocation(programId, "InstanceUv1"),
                gl.glGetAttribLocation(programId, "InstancePalette"),
                gl.glGetAttribLocation(programId, "InstanceHighPriority"));
    }

    private InstancedBatchCommand obtainCommand() {
        InstancedBatchCommand command = commandPool.pollFirst();
        if (command == null) {
            command = new InstancedBatchCommand();
        }
        return command;
    }

    private void recycleCommand(InstancedBatchCommand command) {
        if (commandPool.size() < COMMAND_POOL_LIMIT) {
            commandPool.addLast(command);
        }
    }

    private static class AttribLocations {
        private final int vertexPos;
        private final int instancePos;
        private final int instanceSize;
        private final int instanceUv0;
        private final int instanceUv1;
        private final int instancePalette;
        private final int instanceHighPriority;

        private AttribLocations(int vertexPos, int instancePos, int instanceSize,
                int instanceUv0, int instanceUv1, int instancePalette, int instanceHighPriority) {
            this.vertexPos = vertexPos;
            this.instancePos = instancePos;
            this.instanceSize = instanceSize;
            this.instanceUv0 = instanceUv0;
            this.instanceUv1 = instanceUv1;
            this.instancePalette = instancePalette;
            this.instanceHighPriority = instanceHighPriority;
        }
    }

    private class InstancedBatchCommand implements GLCommandable {
        private FloatBuffer instanceBuffer;
        private int instanceCount;
        private int floatCount;
        private boolean usePriorityShader;

        private void load(float[] data, int instanceCount, boolean usePriorityShader) {
            this.instanceCount = instanceCount;
            this.floatCount = instanceCount * FLOATS_PER_INSTANCE;
            this.usePriorityShader = usePriorityShader;
            instanceBuffer = ensureBuffer(instanceBuffer, floatCount);
            instanceBuffer.clear();
            instanceBuffer.put(data, 0, floatCount);
            instanceBuffer.flip();
        }

        @Override
        public void execute(GL2 gl, int cameraX, int cameraY, int cameraWidth, int cameraHeight) {
            if (instanceCount == 0 || instancedShader == null) {
                return;
            }
            GraphicsManager gm = GraphicsManager.getInstance();
            boolean useWaterShader = gm.getShaderProgram() instanceof WaterShaderProgram;
            // Use captured priority shader state from batch creation time
            boolean usePriorityShader = this.usePriorityShader;

            // Select the appropriate shader based on mode
            ShaderProgram shader;
            if (usePriorityShader) {
                shader = instancedPriorityShader;
            } else if (useWaterShader) {
                shader = instancedWaterShader;
            } else {
                shader = instancedShader;
            }
            if (shader == null) {
                return;
            }

            shader.use(gl);
            shader.cacheUniformLocations(gl);
            gl.glUniform1i(shader.getPaletteLocation(), 0);
            gl.glUniform1i(shader.getIndexedColorTextureLocation(), 1);
            shader.setPaletteLine(gl, -1.0f);

            // Set priority uniforms if using the priority shader
            // Priority is now per-instance via InstanceHighPriority attribute,
            // but we still need to bind the tile priority FBO texture and screen size
            if (usePriorityShader) {
                TilePriorityFBO fbo = gm.getTilePriorityFBO();
                if (fbo != null && fbo.isInitialized()) {
                    // Use cached uniform locations instead of glGetUniformLocation every batch
                    if (cachedTilePriorityTexLoc != -1) {
                        gl.glActiveTexture(GL2.GL_TEXTURE3);
                        gl.glBindTexture(GL2.GL_TEXTURE_2D, fbo.getTextureId());
                        gl.glUniform1i(cachedTilePriorityTexLoc, 3);

                        // Use cached viewport dimensions from GraphicsManager
                        if (cachedScreenSizeLoc != -1) {
                            gl.glUniform2f(cachedScreenSizeLoc, gm.getViewportWidth(), gm.getViewportHeight());
                        }

                        if (cachedViewportOffsetLoc != -1) {
                            gl.glUniform2f(cachedViewportOffsetLoc, gm.getViewportX(), gm.getViewportY());
                        }
                        gl.glActiveTexture(GL2.GL_TEXTURE0);
                    }
                }

                // Bind underwater palette for per-scanline palette switching
                Integer underwaterPaletteId = gm.getUnderwaterPaletteTextureId();
                if (underwaterPaletteId != null && cachedUnderwaterPaletteLoc != -1) {
                    gl.glActiveTexture(GL2.GL_TEXTURE2);
                    gl.glBindTexture(GL2.GL_TEXTURE_2D, underwaterPaletteId);
                    gl.glUniform1i(cachedUnderwaterPaletteLoc, 2);
                    gl.glActiveTexture(GL2.GL_TEXTURE0);
                }

                // Set water uniforms from cached values in GraphicsManager
                if (cachedWaterEnabledLoc != -1) {
                    gl.glUniform1i(cachedWaterEnabledLoc, gm.isWaterEnabled() ? 1 : 0);
                }
                if (cachedWaterlineScreenYLoc != -1) {
                    gl.glUniform1f(cachedWaterlineScreenYLoc, gm.getWaterlineScreenY());
                }
                if (cachedWindowHeightLoc != -1) {
                    gl.glUniform1f(cachedWindowHeightLoc, gm.getWindowHeight());
                }
                if (cachedScreenHeightLoc != -1) {
                    gl.glUniform1f(cachedScreenHeightLoc, gm.getScreenHeight());
                }
            }

            Integer paletteTextureId;
            if (gm.isUseUnderwaterPaletteForBackground()) {
                paletteTextureId = gm.getUnderwaterPaletteTextureId();
                if (paletteTextureId == null) {
                    paletteTextureId = gm.getCombinedPaletteTextureId();
                }
            } else {
                paletteTextureId = gm.getCombinedPaletteTextureId();
            }
            if (paletteTextureId != null) {
                gl.glActiveTexture(GL2.GL_TEXTURE0);
                gl.glBindTexture(GL2.GL_TEXTURE_2D, paletteTextureId);
            }

            Integer atlasTextureId = gm.getPatternAtlasTextureId();
            if (atlasTextureId != null) {
                gl.glActiveTexture(GL2.GL_TEXTURE1);
                gl.glBindTexture(GL2.GL_TEXTURE_2D, atlasTextureId);
            }

            if (shader instanceof WaterShaderProgram) {
                WaterShaderProgram waterShader = (WaterShaderProgram) shader;
                Integer underwaterPaletteId = gm.getUnderwaterPaletteTextureId();
                if (underwaterPaletteId != null) {
                    gl.glActiveTexture(GL2.GL_TEXTURE2);
                    gl.glBindTexture(GL2.GL_TEXTURE_2D, underwaterPaletteId);
                    int loc = waterShader.getUnderwaterPaletteLocation();
                    if (loc != -1) {
                        gl.glUniform1i(loc, 2);
                    }
                    gl.glActiveTexture(GL2.GL_TEXTURE0);
                }
            }

            // Select attribute locations based on which shader we're using
            AttribLocations attribs;
            if (usePriorityShader) {
                attribs = priorityAttribs;
            } else if (useWaterShader) {
                attribs = waterAttribs;
            } else {
                attribs = defaultAttribs;
            }
            if (attribs == null || quadVboId == 0 || instanceVboId == 0) {
                shader.stop(gl);
                return;
            }

            gl.glEnable(GL2.GL_BLEND);
            gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);

            gl.glPushMatrix();
            gl.glTranslatef(-cameraX, cameraY, 0);

            GL2GL3 gl23 = gl.getGL2GL3();
            int stride = FLOATS_PER_INSTANCE * Float.BYTES;

            gl.glBindBuffer(GL2.GL_ARRAY_BUFFER, quadVboId);
            enableAttrib(gl, attribs.vertexPos, 2, GL2.GL_FLOAT, 0, 0L);
            setDivisor(gl23, attribs.vertexPos, 0);

            gl.glBindBuffer(GL2.GL_ARRAY_BUFFER, instanceVboId);
            instanceBuffer.rewind();
            instanceBuffer.limit(floatCount);
            gl.glBufferData(GL2.GL_ARRAY_BUFFER, (long) floatCount * Float.BYTES, instanceBuffer, GL2.GL_DYNAMIC_DRAW);

            enableAttrib(gl, attribs.instancePos, 2, GL2.GL_FLOAT, stride, 0L);
            enableAttrib(gl, attribs.instanceSize, 2, GL2.GL_FLOAT, stride, 2L * Float.BYTES);
            enableAttrib(gl, attribs.instanceUv0, 2, GL2.GL_FLOAT, stride, 4L * Float.BYTES);
            enableAttrib(gl, attribs.instanceUv1, 2, GL2.GL_FLOAT, stride, 6L * Float.BYTES);
            enableAttrib(gl, attribs.instancePalette, 1, GL2.GL_FLOAT, stride, 8L * Float.BYTES);
            enableAttrib(gl, attribs.instanceHighPriority, 1, GL2.GL_FLOAT, stride, 9L * Float.BYTES);

            setDivisor(gl23, attribs.instancePos, 1);
            setDivisor(gl23, attribs.instanceSize, 1);
            setDivisor(gl23, attribs.instanceUv0, 1);
            setDivisor(gl23, attribs.instanceUv1, 1);
            setDivisor(gl23, attribs.instancePalette, 1);
            setDivisor(gl23, attribs.instanceHighPriority, 1);

            gl23.glDrawArraysInstanced(GL2.GL_TRIANGLE_STRIP, 0, 4, instanceCount);

            setDivisor(gl23, attribs.instancePos, 0);
            setDivisor(gl23, attribs.instanceSize, 0);
            setDivisor(gl23, attribs.instanceUv0, 0);
            setDivisor(gl23, attribs.instanceUv1, 0);
            setDivisor(gl23, attribs.instancePalette, 0);
            setDivisor(gl23, attribs.instanceHighPriority, 0);

            disableAttrib(gl, attribs.instanceHighPriority);
            disableAttrib(gl, attribs.instancePalette);
            disableAttrib(gl, attribs.instanceUv1);
            disableAttrib(gl, attribs.instanceUv0);
            disableAttrib(gl, attribs.instanceSize);
            disableAttrib(gl, attribs.instancePos);
            disableAttrib(gl, attribs.vertexPos);

            gl.glBindBuffer(GL2.GL_ARRAY_BUFFER, 0);
            gl.glPopMatrix();

            shader.stop(gl);
            gl.glDisable(GL2.GL_BLEND);

            PatternRenderCommand.resetFrameState();
            recycleCommand(this);
        }

        /**
         * Ensures buffer has required capacity, pre-allocating at max capacity
         * to avoid mid-frame native memory allocations.
         */
        private FloatBuffer ensureBuffer(FloatBuffer buffer, int required) {
            if (buffer == null) {
                // Pre-allocate at max batch capacity
                return GLBuffers.newDirectFloatBuffer(MAX_PATTERNS_PER_BATCH * FLOATS_PER_INSTANCE);
            }
            if (buffer.capacity() < required) {
                return GLBuffers.newDirectFloatBuffer(MAX_PATTERNS_PER_BATCH * FLOATS_PER_INSTANCE);
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
