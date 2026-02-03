#version 410 core

// Debug color vertex shader for rendering debug primitives (lines, quads, etc.)

layout(location = 0) in vec2 VertexPos;
layout(location = 1) in vec4 VertexColor;

uniform mat4 ProjectionMatrix;
uniform vec2 CameraOffset;

out vec4 v_color;

void main()
{
    vec2 pos = VertexPos + CameraOffset;
    gl_Position = ProjectionMatrix * vec4(pos, 0.0, 1.0);
    v_color = VertexColor;
}
