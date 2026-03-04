#version 410 core

// Standard inputs (from ShaderProgram/default shader)
uniform sampler2D Palette;              // Texture Unit 0
uniform sampler2D IndexedColorTexture;  // Texture Unit 1
uniform float PaletteLine;
uniform float TotalPaletteLines;

// Water-specific inputs
uniform sampler2D UnderwaterPalette;    // Texture Unit 2
uniform float WaterlineScreenY;         // Screen Y where water starts (0 = top)
uniform float ScreenHeight;             // Screen height in pixels (e.g. 224)
uniform float ScreenWidth;              // Screen width in pixels (e.g. 320)
uniform float IndexedTextureWidth;      // Width of indexed texture in pixels (atlas width)
uniform int FrameCounter;               // For animation
uniform float DistortionAmplitude;      // Amplitude of ripple in pixels
uniform int ShimmerStyle;               // 0 = S2/S3K (smooth sine), 1 = S1 (integer-snapped shimmer)
uniform float WindowHeight;             // Physical window height in pixels

// World-space water uniforms for FBO rendering
uniform float WaterLevelWorldY;         // Water level in world coordinates
uniform float RenderWorldYOffset;       // World Y offset for current render context
uniform int UseWorldSpaceWater;         // 0 = screen space, 1 = world space

in vec2 v_texCoord;
in float v_paletteLine;

out vec4 FragColor;

void main()
{
    // Determine if we're underwater based on mode
    float pixelYFromTop;
    float waterlineY;

    if (UseWorldSpaceWater == 1) {
        // World-space mode (for FBO/background rendering)
        // gl_FragCoord.y in FBO space (0 at bottom of FBO)
        // Convert to world Y: worldY = RenderWorldYOffset + (FBOHeight - gl_FragCoord.y)
        // Since FBOHeight = WindowHeight in FBO mode:
        float worldY = RenderWorldYOffset + (WindowHeight - gl_FragCoord.y);
        pixelYFromTop = worldY;
        waterlineY = WaterLevelWorldY;
    } else {
        // Screen-space mode (for foreground rendering)
        // Normalize to 0..1 (0 at top, 1 at bottom)
        float normalizedY = 1.0 - (gl_FragCoord.y / WindowHeight);
        pixelYFromTop = normalizedY * ScreenHeight;
        waterlineY = WaterlineScreenY;
    }

    float distortion = 0.0;

    // Check if below waterline
    if (pixelYFromTop >= waterlineY) {
        float scanlinesBelow = pixelYFromTop - waterlineY;

        if (ShimmerStyle == 1) {
            // S1-style shimmer enhancement. The original S1 does NOT use per-scanline
            // horizontal distortion (underwater effect is purely palette swap + water
            // surface sprite animation). This is an enhancement: broad waves (~125px
            // wavelength) with integer pixel snapping, creating 1-2 visible ±1px
            // shift bands that scroll upward with large undistorted gaps between them.
            float angle = (scanlinesBelow * 0.05) + (float(FrameCounter) * 0.04);
            float rawDistortion = sin(angle) * 2.0;
            distortion = floor(rawDistortion + 0.5);
        } else {
            // S2/S3K: Smooth procedural sine wave (existing behavior)
            float angle = (scanlinesBelow * 0.15) + (float(FrameCounter) * 0.2);
            distortion = sin(angle) * DistortionAmplitude;
        }
    }

    // Apply distortion to U coordinate
    // UV.s is 0..1 representing 0..ScreenWidth
    vec2 uv = v_texCoord;
    float uDistortion = distortion / IndexedTextureWidth;
    uv.s += uDistortion;

    // Sample texture index
    float index = texture(IndexedColorTexture, uv).r * 255.0;

    bool isTransparent = index < 0.1;

    // Output Color Lookup
    vec4 color;

    if (isTransparent) {
        discard;
    } else {
        // Resolve palette line (uniform or per-vertex attribute)
        float paletteLine = PaletteLine;
        if (paletteLine < 0.0) {
            paletteLine = v_paletteLine;
        }

        // Standard palette lookup
        float paletteX = (index + 0.5) / 16.0;
        float paletteY = (paletteLine + 0.5) / TotalPaletteLines;

        if (pixelYFromTop >= waterlineY) {
             // Use underwater palette
             color = texture(UnderwaterPalette, vec2(paletteX, paletteY));
        } else {
             // Use normal palette
             color = texture(Palette, vec2(paletteX, paletteY));
        }
    }

    FragColor = color;
}
