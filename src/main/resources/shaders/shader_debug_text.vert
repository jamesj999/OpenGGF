#version 120

attribute vec2 VertexPos;
attribute vec2 InstancePos;
attribute vec2 InstanceSize;
attribute vec2 InstanceUv0;
attribute vec2 InstanceUv1;
attribute vec4 InstanceColor;

varying vec2 v_texCoord;
varying vec4 v_color;

uniform mat4 ProjectionMatrix;

void main()
{
    vec2 pos = InstancePos + (VertexPos * InstanceSize);
    gl_Position = ProjectionMatrix * vec4(pos, 0.0, 1.0);

    // Interpolate UV coordinates based on vertex position (0 or 1)
    v_texCoord = mix(InstanceUv0, InstanceUv1, VertexPos);
    v_color = InstanceColor;
}
