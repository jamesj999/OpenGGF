package com.openggf.graphics;

import java.io.IOException;

import static org.lwjgl.opengl.GL20.*;

public class ShaderProgram {
    private int programId;

    // Cached uniform locations for pattern rendering
    private int paletteLocation = -1;
    private int indexedColorTextureLocation = -1;
    private int paletteLineLocation = -1;
    private int totalPaletteLinesLocation = -1;
    private boolean uniformsCached = false;

    public int getProgramId() {
        return programId;
    }

    public void setProgramId(int programId) {
        this.programId = programId;
        // Invalidate cached locations when program changes
        uniformsCached = false;
    }

    /**
     * Cache uniform locations for efficient repeated access.
     * Call this once after shader is linked and before rendering.
     */
    public void cacheUniformLocations() {
        if (uniformsCached) {
            return;
        }
        paletteLocation = glGetUniformLocation(programId, "Palette");
        indexedColorTextureLocation = glGetUniformLocation(programId, "IndexedColorTexture");
        paletteLineLocation = glGetUniformLocation(programId, "PaletteLine");
        totalPaletteLinesLocation = glGetUniformLocation(programId, "TotalPaletteLines");
        uniformsCached = true;
    }

    public int getPaletteLocation() {
        return paletteLocation;
    }

    public int getIndexedColorTextureLocation() {
        return indexedColorTextureLocation;
    }

    public int getPaletteLineLocation() {
        return paletteLineLocation;
    }

    /**
     * Set the palette line uniform (fast path using cached location).
     */
    public void setPaletteLine(float line) {
        if (paletteLineLocation >= 0) {
            glUniform1f(paletteLineLocation, line);
        }
    }

    /**
     * Set the total palette lines uniform for dynamic palette texture height.
     */
    public void setTotalPaletteLines(float n) {
        if (totalPaletteLinesLocation >= 0) {
            glUniform1f(totalPaletteLinesLocation, n);
        }
    }

    /**
     * Initializes a shader program with a vertex and fragment shader.
     *
     * @param vertexShaderPath   the path to the vertex shader file
     * @param fragmentShaderPath the path to the fragment shader file
     * @throws IOException if a shader file cannot be loaded
     */
    public ShaderProgram(String vertexShaderPath, String fragmentShaderPath) throws IOException {
        int vertexShaderId = ShaderLoader.loadShader(vertexShaderPath, GL_VERTEX_SHADER);
        int fragmentShaderId = ShaderLoader.loadShader(fragmentShaderPath, GL_FRAGMENT_SHADER);

        programId = glCreateProgram();
        glAttachShader(programId, vertexShaderId);
        glAttachShader(programId, fragmentShaderId);
        glLinkProgram(programId);

        int linked = glGetProgrami(programId, GL_LINK_STATUS);
        if (linked == 0) {
            String log = glGetProgramInfoLog(programId);
            System.err.println("Shader linking failed:\n" + log);
        }

        // Detach and delete shader objects - they're no longer needed after linking.
        // The program retains the compiled code, so keeping the shader objects
        // around wastes GPU memory, especially on level restarts.
        glDetachShader(programId, vertexShaderId);
        glDetachShader(programId, fragmentShaderId);
        glDeleteShader(vertexShaderId);
        glDeleteShader(fragmentShaderId);
    }

    /**
     * Binds the shader program for use.
     */
    public void use() {
        glUseProgram(programId);
    }

    /**
     * Unbinds the shader program.
     */
    public void stop() {
        glUseProgram(0);
    }

    /**
     * Cleans up and deletes the shader program.
     */
    public void cleanup() {
        if (programId != 0) {
            glDeleteProgram(programId);
            programId = 0;
        }
    }
}
