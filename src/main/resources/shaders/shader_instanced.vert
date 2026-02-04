#version 120

attribute vec2 VertexPos;
attribute vec2 InstancePos;
attribute vec2 InstanceSize;
attribute vec2 InstanceUv0;
attribute vec2 InstanceUv1;
attribute float InstancePalette;
attribute float InstanceHighPriority;  // 0.0 = low priority, 1.0 = high priority

uniform mat4 ProjectionMatrix;
uniform vec2 CameraOffset;

varying vec2 v_texCoord;
varying float v_paletteLine;
varying float v_highPriority;

void main()
{
    vec2 pos = InstancePos + (VertexPos * InstanceSize) + CameraOffset;
    gl_Position = ProjectionMatrix * vec4(pos, 0.0, 1.0);

    vec2 uv = mix(InstanceUv0, InstanceUv1, VertexPos);
    v_texCoord = uv;
    v_paletteLine = InstancePalette;
    v_highPriority = InstanceHighPriority;  // Pass priority to fragment shader
}
