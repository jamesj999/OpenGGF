#version 410 core

layout(location = 0) in vec2 VertexPos;
layout(location = 1) in vec2 InstancePos;
layout(location = 2) in vec2 InstanceSize;
layout(location = 3) in vec2 InstanceUv0;
layout(location = 4) in vec2 InstanceUv1;
layout(location = 5) in float InstancePalette;
layout(location = 6) in float InstanceHighPriority;  // 0.0 = low priority, 1.0 = high priority

uniform mat4 ProjectionMatrix;
uniform vec2 CameraOffset;

out vec2 v_texCoord;
out float v_paletteLine;
out float v_highPriority;

void main()
{
    vec2 pos = InstancePos + (VertexPos * InstanceSize) + CameraOffset;
    gl_Position = ProjectionMatrix * vec4(pos, 0.0, 1.0);

    vec2 uv = mix(InstanceUv0, InstanceUv1, VertexPos);
    v_texCoord = uv;
    v_paletteLine = InstancePalette;
    v_highPriority = InstanceHighPriority;  // Pass priority to fragment shader
}
