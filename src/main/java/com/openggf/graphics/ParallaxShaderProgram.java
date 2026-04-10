package com.openggf.graphics;

import java.io.IOException;

import static org.lwjgl.opengl.GL20.*;

/**
 * Shader program specialized for parallax background rendering.
 * Extends the base shader functionality with uniforms for per-scanline
 * scrolling.
 */
public class ParallaxShaderProgram extends ShaderProgram {

    // Texture sampler locations
    private int backgroundTextureLocation = -1;
    private int hScrollTextureLocation = -1;
    private int vScrollTextureLocation = -1;
    private int vScrollColumnTextureLocation = -1;
    private int parallaxPaletteLocation = -1;

    // Scroll and dimension uniforms
    private int screenHeightLocation = -1;
    private int screenWidthLocation = -1;
    private int vScrollBGLocation = -1;
    private int bgTextureWidthLocation = -1;
    private int bgTextureHeightLocation = -1;
    private int scrollMidpointLocation = -1;
    private int extraBufferLocation = -1;
    private int vScrollLocation = -1;
    private int viewportOffsetXLocation = -1;
    private int viewportOffsetYLocation = -1;
    private int backdropColorLocation = -1;
    private int fillTransparentWithBackdropLocation = -1;
    private int activeDisplayWidthLocation = -1;
    private int fboAllocationWidthLocation = -1;
    private int noHScrollLocation = -1;
    private int usePerLineVScrollLocation = -1;
    private int usePerColumnVScrollLocation = -1;
    private int frameCounterLocation = -1;
    private int shimmerStyleLocation = -1;
    private int waterlineScreenYLocation = -1;

    /**
     * Creates and links the parallax shader program.
     *
     * @param fragmentShaderPath Path to the fragment shader file
     * @throws IOException if shader loading fails
     */
    public ParallaxShaderProgram(String fragmentShaderPath) throws IOException {
        super(FULLSCREEN_VERTEX_SHADER, fragmentShaderPath);
    }

    /**
     * Cache all uniform locations for efficient access.
     * Calls the parent to cache base uniforms, then caches parallax-specific ones.
     */
    @Override
    public void cacheUniformLocations() {
        if (uniformsCached) {
            return;
        }
        super.cacheUniformLocations();

        int programId = getProgramId();

        // Texture samplers
        backgroundTextureLocation = glGetUniformLocation(programId, "BackgroundTexture");
        hScrollTextureLocation = glGetUniformLocation(programId, "HScrollTexture");
        vScrollTextureLocation = glGetUniformLocation(programId, "VScrollTexture");
        vScrollColumnTextureLocation = glGetUniformLocation(programId, "VScrollColumnTexture");
        parallaxPaletteLocation = glGetUniformLocation(programId, "Palette");

        // Scroll and dimensions
        screenHeightLocation = glGetUniformLocation(programId, "ScreenHeight");
        screenWidthLocation = glGetUniformLocation(programId, "ScreenWidth");
        vScrollBGLocation = glGetUniformLocation(programId, "VScrollBG");
        bgTextureWidthLocation = glGetUniformLocation(programId, "BGTextureWidth");
        bgTextureHeightLocation = glGetUniformLocation(programId, "BGTextureHeight");
        scrollMidpointLocation = glGetUniformLocation(programId, "ScrollMidpoint");
        extraBufferLocation = glGetUniformLocation(programId, "ExtraBuffer");
        vScrollLocation = glGetUniformLocation(programId, "VScroll");
        viewportOffsetXLocation = glGetUniformLocation(programId, "ViewportOffsetX");
        viewportOffsetYLocation = glGetUniformLocation(programId, "ViewportOffsetY");
        backdropColorLocation = glGetUniformLocation(programId, "BackdropColor");
        fillTransparentWithBackdropLocation = glGetUniformLocation(programId, "FillTransparentWithBackdrop");
        activeDisplayWidthLocation = glGetUniformLocation(programId, "ActiveDisplayWidth");
        fboAllocationWidthLocation = glGetUniformLocation(programId, "FBOAllocationWidth");
        noHScrollLocation = glGetUniformLocation(programId, "NoHScroll");
        usePerLineVScrollLocation = glGetUniformLocation(programId, "UsePerLineVScroll");
        usePerColumnVScrollLocation = glGetUniformLocation(programId, "UsePerColumnVScroll");
        frameCounterLocation = glGetUniformLocation(programId, "FrameCounter");
        shimmerStyleLocation = glGetUniformLocation(programId, "ShimmerStyle");
        waterlineScreenYLocation = glGetUniformLocation(programId, "WaterlineScreenY");
    }

    // Texture unit setters
    public void setBackgroundTexture(int textureUnit) {
        if (backgroundTextureLocation >= 0) {
            glUniform1i(backgroundTextureLocation, textureUnit);
        }
    }

    public void setHScrollTexture(int textureUnit) {
        if (hScrollTextureLocation >= 0) {
            glUniform1i(hScrollTextureLocation, textureUnit);
        }
    }

    public void setVScrollTexture(int textureUnit) {
        if (vScrollTextureLocation >= 0) {
            glUniform1i(vScrollTextureLocation, textureUnit);
        }
    }

    public void setVScrollColumnTexture(int textureUnit) {
        if (vScrollColumnTextureLocation >= 0) {
            glUniform1i(vScrollColumnTextureLocation, textureUnit);
        }
    }

    public void setPalette(int textureUnit) {
        if (parallaxPaletteLocation >= 0) {
            glUniform1i(parallaxPaletteLocation, textureUnit);
        }
    }

    // Dimension and scroll setters
    public void setScreenDimensions(float width, float height) {
        if (screenWidthLocation >= 0) {
            glUniform1f(screenWidthLocation, width);
        }
        if (screenHeightLocation >= 0) {
            glUniform1f(screenHeightLocation, height);
        }
    }

    public void setVScrollBG(float vScroll) {
        if (vScrollBGLocation >= 0) {
            glUniform1f(vScrollBGLocation, vScroll);
        }
    }

    public void setBGTextureDimensions(float width, float height) {
        if (bgTextureWidthLocation >= 0) {
            glUniform1f(bgTextureWidthLocation, width);
        }
        if (bgTextureHeightLocation >= 0) {
            glUniform1f(bgTextureHeightLocation, height);
        }
    }

    public void setScrollMidpoint(int midpoint) {
        if (scrollMidpointLocation >= 0) {
            glUniform1f(scrollMidpointLocation, (float) midpoint);
        }
    }

    public void setExtraBuffer(int buffer) {
        if (extraBufferLocation >= 0) {
            glUniform1f(extraBufferLocation, (float) buffer);
        }
    }

    public void setVScroll(float vScroll) {
        if (vScrollLocation >= 0) {
            glUniform1f(vScrollLocation, vScroll);
        }
    }

    public void setViewportOffset(float offsetX, float offsetY) {
        if (viewportOffsetXLocation >= 0) {
            glUniform1f(viewportOffsetXLocation, offsetX);
        }
        if (viewportOffsetYLocation >= 0) {
            glUniform1f(viewportOffsetYLocation, offsetY);
        }
    }

    public void setBackdropColor(float r, float g, float b) {
        if (backdropColorLocation >= 0) {
            glUniform3f(backdropColorLocation, r, g, b);
        }
    }

    public void setShimmerParams(int frameCounter, int shimmerStyle, float waterlineScreenY) {
        if (frameCounterLocation >= 0) {
            glUniform1i(frameCounterLocation, frameCounter);
        }
        if (shimmerStyleLocation >= 0) {
            glUniform1i(shimmerStyleLocation, shimmerStyle);
        }
        if (waterlineScreenYLocation >= 0) {
            glUniform1f(waterlineScreenYLocation, waterlineScreenY);
        }
    }

    public void setFillTransparentWithBackdrop(boolean fill) {
        if (fillTransparentWithBackdropLocation >= 0) {
            glUniform1f(fillTransparentWithBackdropLocation, fill ? 1.0f : 0.0f);
        }
    }

    public void setActiveDisplayWidth(float width) {
        if (activeDisplayWidthLocation >= 0) {
            glUniform1f(activeDisplayWidthLocation, width);
        }
    }

    public void setFBOAllocationWidth(float width) {
        if (fboAllocationWidthLocation >= 0) {
            glUniform1f(fboAllocationWidthLocation, width);
        }
    }

    public void setNoHScroll(boolean noHScroll) {
        if (noHScrollLocation >= 0) {
            glUniform1i(noHScrollLocation, noHScroll ? 1 : 0);
        }
    }

    public void setUsePerLineVScroll(boolean enabled) {
        if (usePerLineVScrollLocation >= 0) {
            glUniform1i(usePerLineVScrollLocation, enabled ? 1 : 0);
        }
    }

    public void setUsePerColumnVScroll(boolean enabled) {
        if (usePerColumnVScrollLocation >= 0) {
            glUniform1i(usePerColumnVScrollLocation, enabled ? 1 : 0);
        }
    }

}
