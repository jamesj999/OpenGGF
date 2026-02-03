#version 330 core

uniform sampler2D GlyphAtlas;
uniform vec2 TexelSize;        // 1.0 / atlas_size
uniform vec4 OutlineColor;     // Typically black (0,0,0,1)

in vec2 v_texCoord;
in vec4 v_color;

out vec4 FragColor;

void main()
{
    // Sample center (the actual glyph) - now antialiased (0.0-1.0 coverage)
    float center = texture(GlyphAtlas, v_texCoord).r;

    // Sample 8 neighbors for outline detection
    float outline = 0.0;
    outline = max(outline, texture(GlyphAtlas, v_texCoord + vec2(-TexelSize.x, 0.0)).r);
    outline = max(outline, texture(GlyphAtlas, v_texCoord + vec2( TexelSize.x, 0.0)).r);
    outline = max(outline, texture(GlyphAtlas, v_texCoord + vec2(0.0, -TexelSize.y)).r);
    outline = max(outline, texture(GlyphAtlas, v_texCoord + vec2(0.0,  TexelSize.y)).r);
    outline = max(outline, texture(GlyphAtlas, v_texCoord + vec2(-TexelSize.x, -TexelSize.y)).r);
    outline = max(outline, texture(GlyphAtlas, v_texCoord + vec2( TexelSize.x, -TexelSize.y)).r);
    outline = max(outline, texture(GlyphAtlas, v_texCoord + vec2(-TexelSize.x,  TexelSize.y)).r);
    outline = max(outline, texture(GlyphAtlas, v_texCoord + vec2( TexelSize.x,  TexelSize.y)).r);

    // Smooth alpha compositing: blend fill over outline
    // fillColor.a = center coverage, outlineColor.a = outline coverage
    vec4 fillColor = v_color;
    fillColor.a *= center;

    vec4 outlineColorFinal = OutlineColor;
    outlineColorFinal.a *= outline;

    // Porter-Duff "over" compositing: fill over outline
    // result.rgb = fill.rgb * fill.a + outline.rgb * outline.a * (1 - fill.a)
    // result.a = fill.a + outline.a * (1 - fill.a)
    FragColor.rgb = fillColor.rgb * fillColor.a + outlineColorFinal.rgb * outlineColorFinal.a * (1.0 - fillColor.a);
    FragColor.a = fillColor.a + outlineColorFinal.a * (1.0 - fillColor.a);

    // Discard fully transparent fragments
    if (FragColor.a < 0.01) {
        discard;
    }
}
