#version 120

attribute vec2 VertexPos;
attribute vec2 VertexUv;
attribute float VertexPalette;

uniform mat4 ProjectionMatrix;
uniform vec2 CameraOffset;

varying vec2 v_texCoord;
varying float v_paletteLine;

void main()
{
    vec2 pos = VertexPos + CameraOffset;
    gl_Position = ProjectionMatrix * vec4(pos, 0.0, 1.0);
    v_texCoord = VertexUv;
    v_paletteLine = VertexPalette;
}
