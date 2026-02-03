package uk.co.jamesj999.sonic.graphics;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import com.jogamp.opengl.GL2;

public class ShaderLoader {
    public static int loadShader(GL2 gl, String filePath, int shaderType) throws IOException {
        // Load the shader source code from classpath resource
        String shaderSource;
        try (InputStream is = ShaderLoader.class.getClassLoader().getResourceAsStream(filePath)) {
            if (is == null) {
                throw new IOException("Shader resource not found: " + filePath);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                shaderSource = reader.lines().collect(Collectors.joining("\n"));
            }
        }

        // Create a new shader object
        int shaderId = gl.glCreateShader(shaderType);

        // Pass the shader source to OpenGL
        gl.glShaderSource(shaderId, 1, new String[] { shaderSource }, null);

        // Compile the shader
        gl.glCompileShader(shaderId);

        // Check for compile errors
        int[] compiled = new int[1];
        gl.glGetShaderiv(shaderId, GL2.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            // Compilation failed, retrieve and print the log
            int[] logLength = new int[1];
            gl.glGetShaderiv(shaderId, GL2.GL_INFO_LOG_LENGTH, logLength, 0);
            byte[] log = new byte[logLength[0]];
            gl.glGetShaderInfoLog(shaderId, log.length, null, 0, log, 0);
            System.err.println("Shader compilation failed:\n" + new String(log));
        }

        return shaderId;
    }
}