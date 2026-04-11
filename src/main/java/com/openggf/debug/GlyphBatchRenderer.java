package com.openggf.debug;

import com.openggf.game.GameServices;
import com.openggf.graphics.PixelFont;
import com.openggf.graphics.PixelFontTextRenderer;

/**
 * Temporary compatibility wrapper that preserves the old debug text API while
 * delegating to the pixel-font renderer.
 */
public class GlyphBatchRenderer {

    private static final int BASE_WIDTH = 320;
    private static final int BASE_HEIGHT = 224;

    private final PixelFontTextRenderer textRenderer = new PixelFontTextRenderer();
    private boolean initialized;
    private boolean batchActive;
    private int viewportWidth = BASE_WIDTH;
    private int viewportHeight = BASE_HEIGHT;
    private float currentScale = 1.0f;

    public void init(Object ignoredFont) {
        init(ignoredFont, 1.0f);
    }

    public void init(Object ignoredFont, float scaleFactor) {
        currentScale = scaleFactor;
        initialized = true;
        syncProjectionMatrix();
    }

    public void updateViewport(int width, int height) {
        if (width > 0) {
            viewportWidth = width;
        }
        if (height > 0) {
            viewportHeight = height;
        }
    }

    public void updateScale(Object ignoredFont, float newScale) {
        currentScale = newScale;
    }

    public float getCurrentScale() {
        return currentScale;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void begin() {
        if (!initialized) {
            return;
        }
        batchActive = true;
        syncProjectionMatrix();
    }

    public boolean isBatchActive() {
        return batchActive;
    }

    public void drawText(String text, int x, int y, DebugColor color, FontSize fontSize) {
        if (!batchActive || text == null || text.isEmpty()) {
            return;
        }
        int scaledX = scaleX(x);
        int scaledBottomY = scaleY(y);
        int topLeftY = BASE_HEIGHT - scaledBottomY - PixelFont.glyphHeight();
        textRenderer.drawShadowedText(text, scaledX, topLeftY, color);
    }

    public void drawTextOutlined(String text, int x, int y, DebugColor fillColor, FontSize fontSize) {
        drawText(text, x, y, fillColor, fontSize);
    }

    public void drawTextOutlined(String text, int x, int y, DebugColor fillColor) {
        drawTextOutlined(text, x, y, fillColor, FontSize.MEDIUM);
    }

    public int measureTextWidth(String text, FontSize fontSize) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.round(textRenderer.measureWidth(text) * viewportWidth / (float) BASE_WIDTH);
    }

    public int measureTextWidth(String text) {
        return measureTextWidth(text, FontSize.MEDIUM);
    }

    public int getLineHeight(FontSize fontSize) {
        return Math.max(1, Math.round(baseLineHeight(fontSize) * viewportHeight / (float) BASE_HEIGHT));
    }

    public int getLineHeight() {
        return getLineHeight(FontSize.MEDIUM);
    }

    public void end() {
        batchActive = false;
    }

    public void cleanup() {
        textRenderer.cleanup();
        batchActive = false;
        initialized = false;
    }

    private void syncProjectionMatrix() {
        float[] projectionMatrix = GameServices.graphics().getProjectionMatrixBuffer();
        if (projectionMatrix != null) {
            textRenderer.setProjectionMatrix(projectionMatrix);
        }
    }

    private int scaleX(int x) {
        return Math.round(x * BASE_WIDTH / (float) viewportWidth);
    }

    private int scaleY(int y) {
        return Math.round(y * BASE_HEIGHT / (float) viewportHeight);
    }

    private static int baseLineHeight(FontSize fontSize) {
        return switch (fontSize) {
            case SMALL -> 10;
            case MEDIUM -> 12;
            case LARGE -> 14;
        };
    }
}
