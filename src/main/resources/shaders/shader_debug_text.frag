#version 110

uniform sampler2D GlyphAtlas;
uniform vec2 TexelSize;        // 1.0 / atlas_size
uniform vec4 OutlineColor;     // Typically black (0,0,0,1)

varying vec2 v_texCoord;
varying vec4 v_color;

void main()
{
    // Sample center (the actual glyph)
    float center = texture2D(GlyphAtlas, v_texCoord).r;

    // Sample 8 neighbors for outline detection
    float outline = 0.0;
    outline = max(outline, texture2D(GlyphAtlas, v_texCoord + vec2(-TexelSize.x, 0.0)).r);
    outline = max(outline, texture2D(GlyphAtlas, v_texCoord + vec2( TexelSize.x, 0.0)).r);
    outline = max(outline, texture2D(GlyphAtlas, v_texCoord + vec2(0.0, -TexelSize.y)).r);
    outline = max(outline, texture2D(GlyphAtlas, v_texCoord + vec2(0.0,  TexelSize.y)).r);
    outline = max(outline, texture2D(GlyphAtlas, v_texCoord + vec2(-TexelSize.x, -TexelSize.y)).r);
    outline = max(outline, texture2D(GlyphAtlas, v_texCoord + vec2( TexelSize.x, -TexelSize.y)).r);
    outline = max(outline, texture2D(GlyphAtlas, v_texCoord + vec2(-TexelSize.x,  TexelSize.y)).r);
    outline = max(outline, texture2D(GlyphAtlas, v_texCoord + vec2( TexelSize.x,  TexelSize.y)).r);

    // Composite: outline underneath, fill on top
    // If center is opaque, use fill color; otherwise use outline color
    vec4 fillColor = v_color * center;
    vec4 outlineColorFinal = OutlineColor * outline;

    // Blend fill over outline
    gl_FragColor = mix(outlineColorFinal, fillColor, center);
    gl_FragColor.a = max(center, outline);

    // Discard fully transparent fragments
    if (gl_FragColor.a < 0.01) {
        discard;
    }
}
