#version 410 core

/*
 * Parallax Background Shader
 *
 * Applies per-scanline horizontal scrolling to the already-rendered background.
 *
 * The FBO contains the background rendered wider than the screen (e.g., 512 pixels)
 * centered on the expected scroll midpoint. This shader samples the FBO with
 * per-scanline offsets based on hScroll values.
 */

// Background rendered to FBO (RGBA, wider than screen)
uniform sampler2D BackgroundTexture;
uniform sampler2D Palette;

// 1D texture containing per-scanline scroll values (224 entries)
uniform sampler1D HScrollTexture;

// Screen dimensions (actual viewport pixels)
uniform float ScreenHeight;
uniform float ScreenWidth;

// Background texture dimensions (e.g., 512x224)
uniform float BGTextureWidth;
uniform float BGTextureHeight;

// Scroll midpoint - the hScroll value that corresponds to FBO center
uniform float ScrollMidpoint;

// Extra buffer pixels on each side of the FBO
// Extra buffer pixels on each side of the FBO
uniform float ExtraBuffer;

// Vertical scroll offset (sub-chunk alignment)
uniform float VScroll;

// Viewport offset (for letterboxing/pillarboxing support)
uniform float ViewportOffsetX;
uniform float ViewportOffsetY;
uniform vec3 BackdropColor;
uniform float FillTransparentWithBackdrop;

// FBO allocation width (actual GPU texture size, may be larger than BGTextureWidth)
uniform float FBOAllocationWidth;

out vec4 FragColor;

void main()
{
    // Get viewport-relative position by subtracting viewport offset from window coordinates
    // gl_FragCoord gives window coordinates, but we need viewport-relative coordinates
    vec2 screenPos = gl_FragCoord.xy - vec2(ViewportOffsetX, ViewportOffsetY);
    float normX = screenPos.x / ScreenWidth;
    float normY = screenPos.y / ScreenHeight;

    // Map to game coordinates (0..320, 0..224)
    float gameX = normX * 320.0;
    float gameY = (1.0 - normY) * 224.0;  // Flip Y for Genesis coords (Y=0 at top)

    // Get the scroll value for this scanline
    float scanline = clamp(gameY, 0.0, 223.0);  // Clamp to valid scanline range
    float scanlineTexCoord = (scanline + 0.5) / 224.0;
    float hScrollThis = texture(HScrollTexture, scanlineTexCoord).r * 32767.0;

    // hScroll contains signed scroll values from the zone handler.
    // Convert back to world-space sample coordinate.
    float worldX = gameX - hScrollThis;

    // Apply vertical scroll offset (sub-chunk alignment)
    float fboY = gameY + VScroll;

    // Background tile pass may render from a shifted world origin.
    // ScrollMidpoint/ExtraBuffer define that origin for intro paths.
    float fboWorldOffsetX = -ScrollMidpoint - ExtraBuffer;

    // Wrap X within the background period rendered into the FBO.
    float fboX = mod(worldX - fboWorldOffsetX, BGTextureWidth);
    if (fboX < 0.0) fboX += BGTextureWidth;

    // Clamp Y to valid range
    fboY = clamp(fboY, 0.0, BGTextureHeight - 1.0);

    // Sample FBO with half-pixel offset to avoid edge artifacts
    // Use FBOAllocationWidth for UV mapping (actual texture size) while BGTextureWidth
    // was used above for wrapping (the rendered region may be smaller than the allocation)
    float uvWidth = FBOAllocationWidth > 0.0 ? FBOAllocationWidth : BGTextureWidth;
    float fboU = fboX / uvWidth;
    float fboV = 1.0 - ((fboY + 0.5) / BGTextureHeight);  // Add 0.5 for pixel center
    fboV = clamp(fboV, 0.5 / BGTextureHeight, 1.0 - 0.5 / BGTextureHeight);  // Stay within texture

    vec4 color = texture(BackgroundTexture, vec2(fboU, fboV));

    // Alpha test
    if (color.a < 0.1) {
        if (FillTransparentWithBackdrop > 0.5) {
            FragColor = vec4(BackdropColor, 1.0);
            return;
        }
        discard;
    }

    FragColor = color;
}
