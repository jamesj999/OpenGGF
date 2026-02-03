#version 410 core

layout(location = 0) in vec2 VertexPos;
layout(location = 1) in vec2 VertexUv;
layout(location = 2) in float VertexPalette;

uniform mat4 ProjectionMatrix;
uniform vec2 CameraOffset;

out vec2 v_texCoord;
out float v_paletteLine;

void main()
{
    vec2 pos = VertexPos + CameraOffset;
    gl_Position = ProjectionMatrix * vec4(pos, 0.0, 1.0);
    v_texCoord = VertexUv;
    v_paletteLine = VertexPalette;
}
