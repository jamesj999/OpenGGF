package uk.co.jamesj999.sonic.graphics;

import java.io.IOException;

import static org.lwjgl.opengl.GL20.*;

/**
 * Shader program for solid-color debug rendering.
 * Provides a cached uniform for setting the debug color.
 */
public class DebugColorShaderProgram extends ShaderProgram {
    private int debugColorLocation = -1;

    public DebugColorShaderProgram(String vertexShaderPath, String fragmentShaderPath) throws IOException {
        super(vertexShaderPath, fragmentShaderPath);
    }

    @Override
    public void cacheUniformLocations() {
        super.cacheUniformLocations();
        debugColorLocation = glGetUniformLocation(getProgramId(), "DebugColor");
    }

    public void setDebugColor(float r, float g, float b, float a) {
        if (debugColorLocation >= 0) {
            glUniform4f(debugColorLocation, r, g, b, a);
        }
    }
}
