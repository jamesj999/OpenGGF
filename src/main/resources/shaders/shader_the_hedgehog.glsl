#version 120

uniform sampler2D Palette;
uniform sampler2D IndexedColorTexture;
uniform float PaletteLine;

varying vec2 v_texCoord;
varying float v_paletteLine;

void main()
{
    // Get the color index from the indexed texture
    float index = texture2D(IndexedColorTexture, v_texCoord).r * 255.0;

    // Mega Drive VDP Rule: Index 0 is transparent.
    // We discard the fragment so it doesn't write to the frame buffer (or depth buffer),
    // allowing the backdrop or previous layers to show through.
    if (index < 0.1) {
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

    // Sample the palette texture to get the actual color
    vec4 indexedColor = texture2D(Palette, vec2(paletteX, paletteY));

    gl_FragColor = indexedColor; // Output the final color
}
