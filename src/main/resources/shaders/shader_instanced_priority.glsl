#version 110

// Instanced sprite priority shader - composites sprites with tile priority awareness
//
// This shader extends the standard instanced pattern shader to support
// ROM-accurate sprite-to-tile layering. Low-priority sprites (InstanceHighPriority=0)
// are hidden behind high-priority foreground tiles, while high-priority sprites
// always render on top of all tiles.
//
// The priority is passed per-instance from the vertex shader via gl_TexCoord[2].

uniform sampler2D Palette;
uniform sampler2D IndexedColorTexture;
uniform sampler2D TilePriorityTexture;  // FBO texture with high-priority tile info
uniform float PaletteLine;
uniform vec2 ScreenSize;                // Viewport dimensions for screen coord lookup
uniform vec2 ViewportOffset;            // Viewport offset in window coords (for letterboxing)

void main()
{
    // Get the color index from the indexed texture
    float index = texture2D(IndexedColorTexture, gl_TexCoord[0].st).r * 255.0;

    // Mega Drive VDP Rule: Index 0 is transparent.
    // We discard the fragment so it doesn't write to the frame buffer (or depth buffer),
    // allowing the backdrop or previous layers to show through.
    if (index < 0.1) {
        discard;
    }

    // Get per-instance priority from vertex shader
    float spriteHighPriority = gl_TexCoord[2].s;

    // Check tile priority at this screen position
    // gl_FragCoord is in WINDOW coordinates (0,0 at bottom-left of window),
    // but the viewport may have a non-zero offset when letterboxed/centered.
    // Subtract ViewportOffset to get viewport-local coordinates, then normalize.
    // No Y-flip needed: OpenGL texture V coordinates already match the FBO orientation
    vec2 screenCoord = (gl_FragCoord.xy - ViewportOffset) / ScreenSize;
    float tilePriority = texture2D(TilePriorityTexture, screenCoord).r;

    // Low-priority sprite behind high-priority tile: discard
    // tilePriority > 0.5 means there's a high-priority tile pixel at this location
    if (spriteHighPriority < 0.5 && tilePriority > 0.5) {
        discard;
    }

    // Resolve palette line (uniform or per-vertex attribute via texcoord1.s)
    float paletteLine = PaletteLine;
    if (paletteLine < 0.0) {
        paletteLine = gl_TexCoord[1].s;
    }

    // Map the index to palette coordinates (16 colors, 4 lines)
    float paletteX = (index + 0.5) / 16.0;
    float paletteY = (paletteLine + 0.5) / 4.0;

    // Sample the palette texture to get the actual color
    vec4 indexedColor = texture2D(Palette, vec2(paletteX, paletteY));

    gl_FragColor = indexedColor; // Output the final color
}
