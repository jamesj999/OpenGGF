package uk.co.jamesj999.sonic.graphics;

import java.io.IOException;

import static org.lwjgl.opengl.GL20.*;

/**
 * Shader program for underwater distortion effects.
 * Extends ShaderProgram to be compatible with PatternRenderCommand.
 */
public class WaterShaderProgram extends ShaderProgram {

    // Uniform locations for water effect
    private int underwaterPaletteLocation = -1;
    private int waterlineScreenYLocation = -1;
    private int frameCounterLocation = -1;
    private int distortionAmplitudeLocation = -1;
    private int screenHeightLocation = -1;
    private int screenWidthLocation = -1;
    private int indexedTextureWidthLocation = -1;
    private int windowHeightLocation = -1;

    // World-space water level for FBO rendering
    private int waterLevelWorldYLocation = -1;
    private int renderWorldYOffsetLocation = -1;
    private int useWorldSpaceWaterLocation = -1;

    // S1 shimmer style
    private int shimmerStyleLocation = -1;

    public WaterShaderProgram(String vertexShaderPath, String fragmentShaderPath) throws IOException {
        super(vertexShaderPath, fragmentShaderPath);
    }

    @Override
    public void cacheUniformLocations() {
        // Cache base uniforms (Palette, IndexedColorTexture, PaletteLine)
        super.cacheUniformLocations();

        int programId = getProgramId();

        // Cache water-specific uniforms
        underwaterPaletteLocation = glGetUniformLocation(programId, "UnderwaterPalette");
        waterlineScreenYLocation = glGetUniformLocation(programId, "WaterlineScreenY");
        frameCounterLocation = glGetUniformLocation(programId, "FrameCounter");
        distortionAmplitudeLocation = glGetUniformLocation(programId, "DistortionAmplitude");
        screenHeightLocation = glGetUniformLocation(programId, "ScreenHeight");
        screenWidthLocation = glGetUniformLocation(programId, "ScreenWidth");
        indexedTextureWidthLocation = glGetUniformLocation(programId, "IndexedTextureWidth");
        windowHeightLocation = glGetUniformLocation(programId, "WindowHeight");

        // World-space uniforms for FBO rendering
        waterLevelWorldYLocation = glGetUniformLocation(programId, "WaterLevelWorldY");
        renderWorldYOffsetLocation = glGetUniformLocation(programId, "RenderWorldYOffset");
        useWorldSpaceWaterLocation = glGetUniformLocation(programId, "UseWorldSpaceWater");

        // Shimmer style
        shimmerStyleLocation = glGetUniformLocation(programId, "ShimmerStyle");
    }

    public int getUnderwaterPaletteLocation() {
        return underwaterPaletteLocation;
    }

    public void setWaterlineScreenY(float y) {
        if (waterlineScreenYLocation != -1) {
            glUniform1f(waterlineScreenYLocation, y);
        }
    }

    public void setFrameCounter(int frame) {
        if (frameCounterLocation != -1) {
            glUniform1i(frameCounterLocation, frame);
        }
    }

    public void setDistortionAmplitude(float amp) {
        if (distortionAmplitudeLocation != -1) {
            glUniform1f(distortionAmplitudeLocation, amp);
        }
    }

    public void setScreenDimensions(float width, float height) {
        if (screenWidthLocation != -1) {
            glUniform1f(screenWidthLocation, width);
        }
        if (screenHeightLocation != -1) {
            glUniform1f(screenHeightLocation, height);
        }
    }

    public void setIndexedTextureWidth(float width) {
        if (indexedTextureWidthLocation != -1) {
            glUniform1f(indexedTextureWidthLocation, width);
        }
    }

    public void setWindowHeight(float height) {
        if (windowHeightLocation != -1) {
            glUniform1f(windowHeightLocation, height);
        }
    }

    /**
     * Set the shimmer style for the water distortion effect.
     *
     * @param style 0 = S2/S3K smooth sine wave, 1 = S1 integer-snapped shimmer
     */
    public void setShimmerStyle(int style) {
        if (shimmerStyleLocation != -1) {
            glUniform1i(shimmerStyleLocation, style);
        }
    }

    /**
     * Set world-space water parameters for FBO rendering.
     *
     * @param waterLevelWorldY   The water level in world Y coordinates
     * @param renderWorldYOffset The world Y offset for the current render
     *                           (typically camera Y + FBO offset)
     * @param useWorldSpace      If true, use world-space calculation instead of
     *                           screen-space
     */
    public void setWorldSpaceWater(float waterLevelWorldY, float renderWorldYOffset, boolean useWorldSpace) {
        if (waterLevelWorldYLocation != -1) {
            glUniform1f(waterLevelWorldYLocation, waterLevelWorldY);
        }
        if (renderWorldYOffsetLocation != -1) {
            glUniform1f(renderWorldYOffsetLocation, renderWorldYOffset);
        }
        if (useWorldSpaceWaterLocation != -1) {
            glUniform1i(useWorldSpaceWaterLocation, useWorldSpace ? 1 : 0);
        }
    }
}
