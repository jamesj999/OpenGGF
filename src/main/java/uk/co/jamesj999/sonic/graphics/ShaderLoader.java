package uk.co.jamesj999.sonic.graphics;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.lwjgl.opengl.GL20.*;

public class ShaderLoader {
    public static int loadShader(String filePath, int shaderType) throws IOException {
        // Load the shader source code from the classpath
        String shaderSource;
        try (InputStream is = ShaderLoader.class.getClassLoader().getResourceAsStream(filePath)) {
            if (is == null) {
                throw new IOException("Shader file not found: " + filePath);
            }
            shaderSource = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        // Create a new shader object
        int shaderId = glCreateShader(shaderType);

        // Pass the shader source to OpenGL
        glShaderSource(shaderId, shaderSource);

        // Compile the shader
        glCompileShader(shaderId);

        // Check for compile errors
        int compiled = glGetShaderi(shaderId, GL_COMPILE_STATUS);
        if (compiled == 0) {
            // Compilation failed, retrieve and print the log
            String log = glGetShaderInfoLog(shaderId);
            System.err.println("Shader compilation failed:\n" + log);
        }

        return shaderId;
    }
}