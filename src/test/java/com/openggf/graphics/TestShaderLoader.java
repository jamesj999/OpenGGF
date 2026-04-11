package com.openggf.graphics;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestShaderLoader {

    @Test
    public void loadShaderSourceFindsBasicVertexShader() throws IOException {
        String source = ShaderLoader.loadShaderSource("shaders/shader_basic.vert");

        assertFalse(source.isBlank());
        assertTrue(source.contains("gl_Position"));
    }
}


