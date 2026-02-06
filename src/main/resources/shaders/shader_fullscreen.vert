#version 410 core

// Simple fullscreen quad vertex shader for tilemap and post-processing passes.
// Generates a quad covering the entire screen in clip space (-1 to 1).
// No vertex attributes needed - positions are generated from gl_VertexID.

void main()
{
    // Generate fullscreen quad vertices from vertex ID (0,1,2,3)
    // Using triangle strip order: bottom-left, bottom-right, top-left, top-right
    vec2 positions[4] = vec2[](
        vec2(-1.0, -1.0),  // 0: bottom-left
        vec2( 1.0, -1.0),  // 1: bottom-right
        vec2(-1.0,  1.0),  // 2: top-left
        vec2( 1.0,  1.0)   // 3: top-right
    );

    gl_Position = vec4(positions[gl_VertexID], 0.0, 1.0);
}
