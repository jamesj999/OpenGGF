#version 120

uniform sampler2D TilemapTexture;    // RGBA8 tile descriptors
uniform sampler2D PatternLookup;     // RGBA8: R=tileX, G=tileY (1D data in 2D texture)
uniform sampler2D AtlasTexture;      // Indexed color atlas (GL_RED)
uniform sampler2D Palette;           // Combined palette texture
uniform sampler2D UnderwaterPalette; // Underwater palette

uniform float TilemapWidth;          // In tiles
uniform float TilemapHeight;         // In tiles
uniform float AtlasWidth;            // In pixels
uniform float AtlasHeight;           // In pixels
uniform float LookupSize;            // Pattern lookup width
uniform float WindowWidth;           // Target width in pixels (FBO)
uniform float WindowHeight;          // Target height in pixels (FBO)
uniform float ViewportWidth;         // Actual GL viewport width
uniform float ViewportHeight;        // Actual GL viewport height
uniform float ViewportOffsetX;       // GL viewport X offset
uniform float ViewportOffsetY;       // GL viewport Y offset
uniform float WorldOffsetX;          // World X at left edge
uniform float WorldOffsetY;          // World Y at top edge
uniform int WrapY;                   // 1 to wrap vertically, 0 to clamp
uniform int PriorityPass;            // -1 = all, 0 = low, 1 = high
uniform int MaskOutput;              // 1 = output white mask, 0 = output actual color
uniform int UseUnderwaterPalette;
uniform float WaterlineScreenY;

void main()
{
    // Pixel position in viewport space (0..ViewportWidth/Height), origin at bottom-left
    float viewportX = gl_FragCoord.x - ViewportOffsetX - 0.5;
    float viewportY = gl_FragCoord.y - ViewportOffsetY - 0.5;

    if (viewportX < 0.0 || viewportY < 0.0 || viewportX >= ViewportWidth || viewportY >= ViewportHeight) {
        discard;
    }

    float scaleX = ViewportWidth / WindowWidth;
    float scaleY = ViewportHeight / WindowHeight;

    // Logical pixel position in screen space (0..WindowWidth/Height), origin at top-left
    float pixelX = viewportX / scaleX;
    float pixelYFromTop = (ViewportHeight - 1.0 - viewportY) / scaleY;

    float worldX = WorldOffsetX + pixelX;
    float worldY = WorldOffsetY + pixelYFromTop;

    float tileXf = floor(worldX / 8.0);
    float tileYf = floor(worldY / 8.0);

    tileXf = mod(tileXf, TilemapWidth);
    if (tileXf < 0.0) tileXf += TilemapWidth;

    if (WrapY == 1) {
        tileYf = mod(tileYf, TilemapHeight);
        if (tileYf < 0.0) tileYf += TilemapHeight;
    } else {
        if (tileYf < 0.0 || tileYf >= TilemapHeight) {
            discard;
        }
    }

    vec2 tileUv = vec2((tileXf + 0.5) / TilemapWidth, (tileYf + 0.5) / TilemapHeight);
    vec4 desc = texture2D(TilemapTexture, tileUv);

    if (desc.a < 0.5) {
        discard;
    }

    float r = desc.r * 255.0;
    float g = desc.g * 255.0;

    float patternHigh = mod(g, 8.0);
    float patternIndex = r + patternHigh * 256.0;
    float paletteIndex = mod(floor(g / 8.0), 4.0);
    float hFlip = mod(floor(g / 32.0), 2.0);
    float vFlipBit = mod(floor(g / 64.0), 2.0);
    float priority = mod(floor(g / 128.0), 2.0);

    if (PriorityPass >= 0 && priority != float(PriorityPass)) {
        discard;
    }

    float localX = mod(worldX, 8.0);
    if (localX < 0.0) localX += 8.0;
    float localY = mod(worldY, 8.0);
    if (localY < 0.0) localY += 8.0;
    localX = floor(localX);
    localY = floor(localY);

    if (hFlip > 0.5) {
        localX = 7.0 - localX;
    }
    // Note: VFlip=false means flip (matching PatternDesc behavior)
    if (vFlipBit > 0.5) {
        localY = 7.0 - localY;
    }

    // Pattern lookup using 2D texture with Y=0.5 (single row)
    float lookupU = (patternIndex + 0.5) / LookupSize;
    vec4 lookup = texture2D(PatternLookup, vec2(lookupU, 0.5));
    float atlasTileX = lookup.r * 255.0;
    float atlasTileY = lookup.g * 255.0;

    float atlasPixelX = atlasTileX * 8.0 + localX + 0.5;
    float atlasPixelY = atlasTileY * 8.0 + localY + 0.5;

    vec2 atlasUv = vec2(atlasPixelX / AtlasWidth, atlasPixelY / AtlasHeight);
    float index = texture2D(AtlasTexture, atlasUv).r * 255.0;

    if (index < 0.1) {
        discard;
    }

    float paletteX = (index + 0.5) / 16.0;
    float paletteY = (paletteIndex + 0.5) / 4.0;
    vec4 color;
    if (UseUnderwaterPalette == 1 && pixelYFromTop >= WaterlineScreenY) {
        color = texture2D(UnderwaterPalette, vec2(paletteX, paletteY));
    } else {
        color = texture2D(Palette, vec2(paletteX, paletteY));
    }

    // When MaskOutput is set, output white as a binary priority mask
    // Otherwise output the actual tile color
    if (MaskOutput == 1) {
        gl_FragColor = vec4(1.0, 1.0, 1.0, 1.0);
    } else {
        gl_FragColor = color;
    }
}
