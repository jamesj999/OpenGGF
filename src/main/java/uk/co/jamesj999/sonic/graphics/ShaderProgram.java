package uk.co.jamesj999.sonic.graphics;

import java.io.IOException;

import static org.lwjgl.opengl.GL20.*;

public class ShaderProgram {
    private int programId;

    // Cached uniform locations for pattern rendering
    private int paletteLocation = -1;
    private int indexedColorTextureLocation = -1;
    private int paletteLineLocation = -1;
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
     * Initializes a shader program with only a fragment shader.
     *
     * @param fragmentShaderPath the path to the fragment shader file
     * @throws IOException if the shader file cannot be loaded
     */
    public ShaderProgram(String fragmentShaderPath) throws IOException {
        // Load and compile the fragment shader
        int fragmentShaderId = ShaderLoader.loadShader(fragmentShaderPath, GL_FRAGMENT_SHADER);

        // Create a new shader program
        programId = glCreateProgram();

        // Attach the fragment shader to the program
        glAttachShader(programId, fragmentShaderId);

        // Link the program
        glLinkProgram(programId);

        // Check for linking errors
        int linked = glGetProgrami(programId, GL_LINK_STATUS);
        if (linked == 0) {
            // Linking failed, retrieve and print the log
            String log = glGetProgramInfoLog(programId);
            System.err.println("Shader linking failed:\n" + log);
        }

        // Detach and delete shader object - it's no longer needed after linking.
        // The program retains the compiled code, so keeping the shader object
        // around wastes GPU memory, especially on level restarts.
        glDetachShader(programId, fragmentShaderId);
        glDeleteShader(fragmentShaderId);
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
