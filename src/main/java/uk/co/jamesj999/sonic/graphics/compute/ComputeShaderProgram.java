package uk.co.jamesj999.sonic.graphics.compute;

import com.jogamp.opengl.GL4;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Manages OpenGL compute shader programs.
 * <p>
 * Compute shaders run on the GPU and can perform general-purpose
 * parallel computations. This class handles loading, compiling,
 * linking, and dispatching compute shaders.
 */
public class ComputeShaderProgram {
    private static final Logger LOGGER = Logger.getLogger(ComputeShaderProgram.class.getName());

    private int programId;
    private final Map<String, Integer> uniformLocations = new HashMap<>();

    /**
     * Create a compute shader program from a GLSL source file.
     *
     * @param gl           the OpenGL 4 context
     * @param shaderPath   path to the compute shader file (in resources)
     * @throws IOException if the shader file cannot be loaded
     */
    public ComputeShaderProgram(GL4 gl, String shaderPath) throws IOException {
        String shaderSource = loadShaderSource(shaderPath);
        int shaderId = compileComputeShader(gl, shaderSource, shaderPath);
        programId = linkProgram(gl, shaderId);

        // Shader object can be deleted after linking
        gl.glDetachShader(programId, shaderId);
        gl.glDeleteShader(shaderId);
    }

    /**
     * Load shader source code from a resource file.
     */
    private String loadShaderSource(String path) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new IOException("Could not find shader resource: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Compile a compute shader from source.
     */
    private int compileComputeShader(GL4 gl, String source, String path) throws IOException {
        int shaderId = gl.glCreateShader(GL4.GL_COMPUTE_SHADER);

        gl.glShaderSource(shaderId, 1, new String[]{source}, null);
        gl.glCompileShader(shaderId);

        int[] compiled = new int[1];
        gl.glGetShaderiv(shaderId, GL4.GL_COMPILE_STATUS, compiled, 0);

        if (compiled[0] == 0) {
            int[] logLength = new int[1];
            gl.glGetShaderiv(shaderId, GL4.GL_INFO_LOG_LENGTH, logLength, 0);
            byte[] log = new byte[logLength[0]];
            gl.glGetShaderInfoLog(shaderId, log.length, null, 0, log, 0);
            String errorLog = new String(log, StandardCharsets.UTF_8);
            gl.glDeleteShader(shaderId);
            throw new IOException("Compute shader compilation failed (" + path + "):\n" + errorLog);
        }

        LOGGER.fine("Compiled compute shader: " + path);
        return shaderId;
    }

    /**
     * Link a compute shader into a program.
     */
    private int linkProgram(GL4 gl, int shaderId) throws IOException {
        int program = gl.glCreateProgram();
        gl.glAttachShader(program, shaderId);
        gl.glLinkProgram(program);

        int[] linked = new int[1];
        gl.glGetProgramiv(program, GL4.GL_LINK_STATUS, linked, 0);

        if (linked[0] == 0) {
            int[] logLength = new int[1];
            gl.glGetProgramiv(program, GL4.GL_INFO_LOG_LENGTH, logLength, 0);
            byte[] log = new byte[logLength[0]];
            gl.glGetProgramInfoLog(program, log.length, null, 0, log, 0);
            String errorLog = new String(log, StandardCharsets.UTF_8);
            gl.glDeleteProgram(program);
            throw new IOException("Compute shader linking failed:\n" + errorLog);
        }

        return program;
    }

    /**
     * Get the program ID.
     *
     * @return OpenGL program ID
     */
    public int getProgramId() {
        return programId;
    }

    /**
     * Bind this program for use.
     *
     * @param gl the OpenGL context
     */
    public void use(GL4 gl) {
        gl.glUseProgram(programId);
    }

    /**
     * Unbind this program.
     *
     * @param gl the OpenGL context
     */
    public void stop(GL4 gl) {
        gl.glUseProgram(0);
    }

    /**
     * Dispatch compute shader work groups.
     * <p>
     * The total number of invocations is numGroupsX * numGroupsY * numGroupsZ
     * multiplied by the local work group size defined in the shader.
     *
     * @param gl         the OpenGL context
     * @param numGroupsX number of work groups in X dimension
     * @param numGroupsY number of work groups in Y dimension
     * @param numGroupsZ number of work groups in Z dimension
     */
    public void dispatch(GL4 gl, int numGroupsX, int numGroupsY, int numGroupsZ) {
        gl.glDispatchCompute(numGroupsX, numGroupsY, numGroupsZ);
    }

    /**
     * Insert a memory barrier to ensure compute shader writes are visible.
     * <p>
     * Call this after dispatch() and before reading results from SSBOs.
     *
     * @param gl the OpenGL context
     */
    public void memoryBarrier(GL4 gl) {
        gl.glMemoryBarrier(GL4.GL_SHADER_STORAGE_BARRIER_BIT);
    }

    /**
     * Insert a full memory barrier for all buffer types.
     *
     * @param gl the OpenGL context
     */
    public void memoryBarrierAll(GL4 gl) {
        gl.glMemoryBarrier((int) GL4.GL_ALL_BARRIER_BITS);
    }

    /**
     * Get (and cache) a uniform location by name.
     *
     * @param gl   the OpenGL context
     * @param name uniform variable name
     * @return uniform location, or -1 if not found
     */
    public int getUniformLocation(GL4 gl, String name) {
        return uniformLocations.computeIfAbsent(name,
                n -> gl.glGetUniformLocation(programId, n));
    }

    /**
     * Set an integer uniform.
     *
     * @param gl    the OpenGL context
     * @param name  uniform name
     * @param value integer value
     */
    public void setUniform(GL4 gl, String name, int value) {
        int loc = getUniformLocation(gl, name);
        if (loc >= 0) {
            gl.glUniform1i(loc, value);
        }
    }

    /**
     * Set a float uniform.
     *
     * @param gl    the OpenGL context
     * @param name  uniform name
     * @param value float value
     */
    public void setUniform(GL4 gl, String name, float value) {
        int loc = getUniformLocation(gl, name);
        if (loc >= 0) {
            gl.glUniform1f(loc, value);
        }
    }

    /**
     * Set a 2-component float uniform.
     *
     * @param gl the OpenGL context
     * @param name uniform name
     * @param x first component
     * @param y second component
     */
    public void setUniform(GL4 gl, String name, float x, float y) {
        int loc = getUniformLocation(gl, name);
        if (loc >= 0) {
            gl.glUniform2f(loc, x, y);
        }
    }

    /**
     * Set an unsigned integer uniform.
     *
     * @param gl    the OpenGL context
     * @param name  uniform name
     * @param value unsigned integer value
     */
    public void setUniformUint(GL4 gl, String name, int value) {
        int loc = getUniformLocation(gl, name);
        if (loc >= 0) {
            gl.glUniform1ui(loc, value);
        }
    }

    /**
     * Clean up and delete the program.
     *
     * @param gl the OpenGL context
     */
    public void cleanup(GL4 gl) {
        if (programId != 0) {
            gl.glDeleteProgram(programId);
            programId = 0;
        }
        uniformLocations.clear();
    }
}
