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

    public PixelFontTextRenderer() {
        this(new PixelFont());
    }

    protected PixelFontTextRenderer(PixelFont font) {
        this.font = Objects.requireNonNull(font, "font");
    }

    public int lineHeight() {
        return PixelFont.glyphHeight() + 2;
    }

    public int measureWidth(String text) {
        return font.measureWidth(text);
    }

    public void drawShadowedText(String text, int x, int y, DebugColor color) {
        drawRawText(text, x + 1, y + 1, DebugColor.BLACK);
        drawRawText(text, x, y, color);
    }

    protected void drawRawText(String text, int x, int y, DebugColor color) {
        ensureInitialized();
        float alpha = color.getAlpha() / 255f;
        font.drawText(text, x, y,
                color.getRed() / 255f,
                color.getGreen() / 255f,
                color.getBlue() / 255f,
                alpha);
    }

    private void ensureInitialized() {
        if (renderer != null) {
            return;
        }
        try {
            renderer = new TexturedQuadRenderer();
            renderer.init();
            font.init(FONT_PATH, renderer);
        } catch (IOException e) {
            renderer = null;
            throw new IllegalStateException("Failed to initialize pixel font renderer", e);
        }
    }
}
