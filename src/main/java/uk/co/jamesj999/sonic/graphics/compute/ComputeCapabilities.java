package uk.co.jamesj999.sonic.graphics.compute;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.util.logging.Logger;

/**
 * Detects OpenGL 4.3+ compute shader capabilities.
 * <p>
 * Compute shaders require OpenGL 4.3 or higher. This class provides
 * detection and capability checking for GPU-accelerated audio synthesis.
 */
public class ComputeCapabilities {
    private static final Logger LOGGER = Logger.getLogger(ComputeCapabilities.class.getName());

    private static boolean detected = false;
    private static boolean computeShadersSupported = false;
    private static boolean ssboSupported = false;
    private static int maxComputeWorkGroupCount = 0;
    private static int maxComputeWorkGroupSize = 0;
    private static int maxComputeWorkGroupInvocations = 0;
    private static int maxShaderStorageBlockSize = 0;
    private static String glVersion = "";
    private static String glslVersion = "";

    /**
     * Detect compute shader capabilities from the current GL context.
     * This should be called once during initialization.
     *
     * @param gl the OpenGL context
     */
    public static void detect(GL gl) {
        if (detected) {
            return;
        }

        detected = true;
        glVersion = gl.glGetString(GL.GL_VERSION);
        glslVersion = gl.glGetString(GL4.GL_SHADING_LANGUAGE_VERSION);

        LOGGER.info("OpenGL Version: " + glVersion);
        LOGGER.info("GLSL Version: " + glslVersion);

        // Check if GL4 is available
        if (!gl.isGL4()) {
            LOGGER.info("OpenGL 4.0 not available - compute shaders disabled");
            computeShadersSupported = false;
            return;
        }

        GL4 gl4 = gl.getGL4();

        // Check for OpenGL 4.3+ which introduced compute shaders
        int[] majorVersion = new int[1];
        int[] minorVersion = new int[1];
        gl4.glGetIntegerv(GL4.GL_MAJOR_VERSION, majorVersion, 0);
        gl4.glGetIntegerv(GL4.GL_MINOR_VERSION, minorVersion, 0);

        if (majorVersion[0] < 4 || (majorVersion[0] == 4 && minorVersion[0] < 3)) {
            LOGGER.info("OpenGL " + majorVersion[0] + "." + minorVersion[0] +
                    " detected - compute shaders require 4.3+");
            computeShadersSupported = false;
            return;
        }

        // Query compute shader limits
        int[] value = new int[1];

        // GL_MAX_COMPUTE_WORK_GROUP_COUNT and SIZE are indexed (x, y, z)
        // Query X dimension (index 0) which is what we use for 1D compute
        gl4.glGetIntegeri_v(GL4.GL_MAX_COMPUTE_WORK_GROUP_COUNT, 0, value, 0);
        maxComputeWorkGroupCount = value[0];

        gl4.glGetIntegeri_v(GL4.GL_MAX_COMPUTE_WORK_GROUP_SIZE, 0, value, 0);
        maxComputeWorkGroupSize = value[0];

        gl4.glGetIntegerv(GL4.GL_MAX_COMPUTE_WORK_GROUP_INVOCATIONS, value, 0);
        maxComputeWorkGroupInvocations = value[0];

        gl4.glGetIntegerv(GL4.GL_MAX_SHADER_STORAGE_BLOCK_SIZE, value, 0);
        maxShaderStorageBlockSize = value[0];

        // Check SSBO support (required for compute shaders to be useful)
        ssboSupported = maxShaderStorageBlockSize > 0;

        computeShadersSupported = true;

        LOGGER.info("Compute shaders supported:");
        LOGGER.info("  Max work group count: " + maxComputeWorkGroupCount);
        LOGGER.info("  Max work group size: " + maxComputeWorkGroupSize);
        LOGGER.info("  Max work group invocations: " + maxComputeWorkGroupInvocations);
        LOGGER.info("  Max SSBO size: " + maxShaderStorageBlockSize + " bytes");
    }

    /**
     * Check if compute shaders are supported on this system.
     *
     * @return true if compute shaders can be used
     */
    public static boolean isComputeSupported() {
        return computeShadersSupported && ssboSupported;
    }

    /**
     * Check if detection has been performed.
     *
     * @return true if detect() has been called
     */
    public static boolean isDetected() {
        return detected;
    }

    /**
     * Get the maximum number of work groups that can be dispatched.
     *
     * @return max work group count per dimension
     */
    public static int getMaxComputeWorkGroupCount() {
        return maxComputeWorkGroupCount;
    }

    /**
     * Get the maximum size of a work group.
     *
     * @return max work group size per dimension
     */
    public static int getMaxComputeWorkGroupSize() {
        return maxComputeWorkGroupSize;
    }

    /**
     * Get the maximum total invocations in a work group.
     *
     * @return max invocations (product of local sizes)
     */
    public static int getMaxComputeWorkGroupInvocations() {
        return maxComputeWorkGroupInvocations;
    }

    /**
     * Get the maximum size of a shader storage buffer.
     *
     * @return max SSBO size in bytes
     */
    public static int getMaxShaderStorageBlockSize() {
        return maxShaderStorageBlockSize;
    }

    /**
     * Get the detected OpenGL version string.
     *
     * @return GL_VERSION string
     */
    public static String getGlVersion() {
        return glVersion;
    }

    /**
     * Get the detected GLSL version string.
     *
     * @return GL_SHADING_LANGUAGE_VERSION string
     */
    public static String getGlslVersion() {
        return glslVersion;
    }

    /**
     * Reset detection state. Used for testing.
     */
    public static void reset() {
        detected = false;
        computeShadersSupported = false;
        ssboSupported = false;
        maxComputeWorkGroupCount = 0;
        maxComputeWorkGroupSize = 0;
        maxComputeWorkGroupInvocations = 0;
        maxShaderStorageBlockSize = 0;
        glVersion = "";
        glslVersion = "";
    }
}
