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

    @Test
    public void instancedPriorityShaderSupportsGhostEffect() throws IOException {
        String source = ShaderLoader.loadShaderSource("shaders/shader_instanced_priority.glsl");

        assertTrue(source.contains("uniform int GhostMode;"));
        assertTrue(source.contains("uniform float GhostAlpha;"));
        assertTrue(source.contains("clamp(GhostAlpha"));
    }
}


