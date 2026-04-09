package com.openggf.graphics;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestShaderLoader {

    @Test
    public void loadShaderSourceFindsBasicVertexShader() throws IOException {
        String source = ShaderLoader.loadShaderSource("shaders/shader_basic.vert");

        assertFalse(source.isBlank());
        assertTrue(source.contains("gl_Position"));
    }
}
