package com.openggf.graphics;

import com.openggf.debug.DebugColor;

import java.io.IOException;
import java.util.Objects;

/**
 * Shared overlay text helper backed by the pixel font texture.
 */
public class PixelFontTextRenderer {

    private static final String FONT_PATH = "pixel-font.png";

    private final PixelFont font;
    private TexturedQuadRenderer renderer;
    private float[] projectionMatrix;

    public PixelFontTextRenderer() {
        this(new PixelFont());
    }

    protected PixelFontTextRenderer(PixelFont font) {
        this.font = Objects.requireNonNull(font, "font");
    }

    public int lineHeight() {
        return lineHeight(1.0f);
    }

    public int lineHeight(float scale) {
        return PixelFont.scaledGlyphHeight(scale) + shadowOffset(scale) + 1;
    }

    public int measureWidth(String text) {
        return measureWidth(text, 1.0f);
    }

    public int measureWidth(String text, float scale) {
        return font.measureWidth(text, scale);
    }

    public void setProjectionMatrix(float[] projectionMatrix) {
        this.projectionMatrix = Objects.requireNonNull(projectionMatrix, "projectionMatrix");
        if (renderer != null) {
            renderer.setProjectionMatrix(this.projectionMatrix);
        }
    }

    public void drawShadowedText(String text, int x, int y, DebugColor color) {
        drawShadowedText(text, x, y, color, 1.0f);
    }

    public void drawShadowedText(String text, int x, int y, DebugColor color, float scale) {
        int shadowOffset = shadowOffset(scale);
        drawRawText(text, x + shadowOffset, y + shadowOffset, DebugColor.BLACK, scale);
        drawRawText(text, x, y, color, scale);
    }

    public void cleanup() {
        if (renderer != null) {
            renderer.cleanup();
            renderer = null;
        }
        font.cleanup();
    }

    protected void drawRawText(String text, int x, int y, DebugColor color) {
        drawRawText(text, x, y, color, 1.0f);
    }

    protected void drawRawText(String text, int x, int y, DebugColor color, float scale) {
        ensureInitialized();
        float alpha = color.getAlpha() / 255f;
        font.drawText(text, x, y, scale,
                color.getRed() / 255f,
                color.getGreen() / 255f,
                color.getBlue() / 255f,
                alpha);
    }

    private static int shadowOffset(float scale) {
        return Math.max(1, Math.round(scale));
    }

    private void ensureInitialized() {
        if (renderer != null) {
            return;
        }
        try {
            renderer = createRenderer();
            renderer.init();
            font.init(FONT_PATH, renderer);
            if (projectionMatrix != null) {
                renderer.setProjectionMatrix(projectionMatrix);
            }
        } catch (IOException e) {
            if (renderer != null) {
                renderer.cleanup();
                renderer = null;
            }
            throw new IllegalStateException("Failed to initialize pixel font renderer", e);
        }
    }

    protected TexturedQuadRenderer createRenderer() {
        return new TexturedQuadRenderer();
    }
}
