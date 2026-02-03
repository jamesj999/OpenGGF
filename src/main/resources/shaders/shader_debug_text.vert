#version 330 core

in vec2 VertexPos;
in vec2 InstancePos;
in vec2 InstanceSize;
in vec2 InstanceUv0;
in vec2 InstanceUv1;
in vec4 InstanceColor;

out vec2 v_texCoord;
out vec4 v_color;

uniform mat4 ProjectionMatrix;

void main()
{
    vec2 pos = InstancePos + (VertexPos * InstanceSize);
    gl_Position = ProjectionMatrix * vec4(pos, 0.0, 1.0);

    // Interpolate UV coordinates based on vertex position (0 or 1)
    v_texCoord = mix(InstanceUv0, InstanceUv1, VertexPos);
    v_color = InstanceColor;
}
