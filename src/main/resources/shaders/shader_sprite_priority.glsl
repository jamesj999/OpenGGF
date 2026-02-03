#version 410 core

// Sprite priority shader - composites sprites with tile priority awareness
//
// This shader extends the standard hedgehog pattern shader to support
// ROM-accurate sprite-to-tile layering. Low-priority sprites (isHighPriority=false)
// are hidden behind high-priority foreground tiles, while high-priority sprites
// always render on top of all tiles.
//
// This allows sprite-to-sprite ordering (via buckets 0-7) to work independently
// of sprite-to-tile layering (via isHighPriority flag).

uniform sampler2D Palette;
uniform sampler2D IndexedColorTexture;
uniform sampler2D TilePriorityTexture;  // FBO texture with high-priority tile info
uniform float PaletteLine;
uniform int SpriteHighPriority;         // 1 = sprite appears above all tiles, 0 = behind high-priority tiles
uniform vec2 ScreenSize;                // Viewport dimensions for screen coord lookup
uniform vec2 ViewportOffset;            // Viewport offset in window coords (for letterboxing)

// Underwater palette uniforms
uniform sampler2D UnderwaterPalette;    // Texture Unit 2
uniform float WaterlineScreenY;         // Screen Y where water starts (negative = above screen)
uniform float WindowHeight;             // Physical window height in pixels
uniform float ScreenHeight;             // Logical screen height (e.g., 224)
uniform int WaterEnabled;               // 1 = zone has water, 0 = no water

in vec2 v_texCoord;
in float v_paletteLine;

out vec4 FragColor;

void main()
{
    // Get the color index from the indexed texture
    float index = texture(IndexedColorTexture, v_texCoord).r * 255.0;

    // Mega Drive VDP Rule: Index 0 is transparent.
    // We discard the fragment so it doesn't write to the frame buffer (or depth buffer),
    // allowing the backdrop or previous layers to show through.
    if (index < 0.1) {
        discard;
    }

    // Check tile priority at this screen position
    // gl_FragCoord is in WINDOW coordinates (0,0 at bottom-left of window),
    // but the viewport may have a non-zero offset when letterboxed/centered.
    // Subtract ViewportOffset to get viewport-local coordinates, then normalize.
    // No Y-flip needed: OpenGL texture V coordinates already match the FBO orientation
    vec2 screenCoord = (gl_FragCoord.xy - ViewportOffset) / ScreenSize;
    float tilePriority = texture(TilePriorityTexture, screenCoord).r;

    // Low-priority sprite behind high-priority tile: discard
    // tilePriority > 0.5 means there's a high-priority tile pixel at this location
    if (SpriteHighPriority == 0 && tilePriority > 0.5) {
        discard;
    }

    // Resolve palette line (uniform or per-vertex attribute)
    float paletteLine = PaletteLine;
    if (paletteLine < 0.0) {
        paletteLine = v_paletteLine;
    }

    // Map the index to palette coordinates (16 colors, 4 lines)
    float paletteX = (index + 0.5) / 16.0;
    float paletteY = (paletteLine + 0.5) / 4.0;

    // Check if underwater (screen-space mode) and sample appropriate palette
    vec4 indexedColor;
    if (WaterEnabled == 1) {
        if (WaterlineScreenY < 0.0) {
            // Waterline above screen - entire screen is underwater
            indexedColor = texture(UnderwaterPalette, vec2(paletteX, paletteY));
        } else {
            // Waterline on screen - check per-pixel
            float normalizedY = 1.0 - (gl_FragCoord.y / WindowHeight);
            float pixelYFromTop = normalizedY * ScreenHeight;
            if (pixelYFromTop >= WaterlineScreenY) {
                indexedColor = texture(UnderwaterPalette, vec2(paletteX, paletteY));
            } else {
                indexedColor = texture(Palette, vec2(paletteX, paletteY));
            }
        }
    } else {
        // No water in this zone
        indexedColor = texture(Palette, vec2(paletteX, paletteY));
    }

    FragColor = indexedColor; // Output the final color
}
