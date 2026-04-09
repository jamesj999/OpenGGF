package com.openggf.graphics;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

import static org.lwjgl.opengl.GL20.*;

public class ShaderLoader {
    private static final Logger LOGGER = Logger.getLogger(ShaderLoader.class.getName());
    private static final Path REPO_RESOURCES_DIR = Paths.get("src", "main", "resources");

    public static int loadShader(String filePath, int shaderType) throws IOException {
        // Load the shader source code from the classpath
        String shaderSource = loadShaderSource(filePath);

        // Create a new shader object
        int shaderId = glCreateShader(shaderType);

        // Pass the shader source to OpenGL
        glShaderSource(shaderId, shaderSource);

        // Compile the shader
        glCompileShader(shaderId);

        // Check for compile errors
        int compiled = glGetShaderi(shaderId, GL_COMPILE_STATUS);
        if (compiled == 0) {
            // Compilation failed, retrieve log, clean up the shader object, then throw.
            String log = glGetShaderInfoLog(shaderId);
            glDeleteShader(shaderId);
            LOGGER.severe("Shader compilation failed:\n" + log);
            throw new RuntimeException("Shader compilation failed: " + log);
        }

        return shaderId;
    }

    static String loadShaderSource(String filePath) throws IOException {
        try (InputStream is = ShaderLoader.class.getClassLoader().getResourceAsStream(filePath)) {
            if (is == null) {
                Path filesystemPath = REPO_RESOURCES_DIR.resolve(filePath).normalize();
                if (Files.isRegularFile(filesystemPath)) {
                    LOGGER.warning("Shader '" + filePath + "' was missing from the runtime classpath; "
                            + "falling back to filesystem path '" + filesystemPath + "'.");
                    return Files.readString(filesystemPath, StandardCharsets.UTF_8);
                }
                throw new IOException("Shader file not found: " + filePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
