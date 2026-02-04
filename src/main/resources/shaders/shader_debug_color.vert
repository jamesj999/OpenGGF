#version 120

// Debug color vertex shader for rendering debug primitives (lines, quads, etc.)

attribute vec2 VertexPos;
attribute vec4 VertexColor;

uniform mat4 ProjectionMatrix;
uniform vec2 CameraOffset;

varying vec4 v_color;

void main()
{
    vec2 pos = VertexPos + CameraOffset;
    gl_Position = ProjectionMatrix * vec4(pos, 0.0, 1.0);
    v_color = VertexColor;
}
