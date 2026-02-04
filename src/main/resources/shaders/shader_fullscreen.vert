#version 120

// Simple fullscreen quad vertex shader for tilemap and post-processing passes.
// Generates a quad covering the entire screen in clip space (-1 to 1).
// Requires a vertex attribute for position since gl_VertexID is not available in GLSL 1.20.

attribute vec2 VertexPos;

void main()
{
    gl_Position = vec4(VertexPos, 0.0, 1.0);
}
