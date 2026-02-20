#version 410 core

layout(location = 0) in vec2 VertexPos;
layout(location = 1) in vec2 VertexUv;

uniform mat4 ProjectionMatrix;

out vec2 v_texCoord;

void main()
{
    gl_Position = ProjectionMatrix * vec4(VertexPos, 0.0, 1.0);
    v_texCoord = VertexUv;
}
