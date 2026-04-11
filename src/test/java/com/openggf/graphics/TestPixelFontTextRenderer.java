package com.openggf.graphics;

import com.openggf.debug.DebugColor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestPixelFontTextRenderer {

    private static final class RecordingPixelFontTextRenderer extends PixelFontTextRenderer {
        private final List<DrawCall> calls = new ArrayList<>();

        @Override
        protected void drawRawText(String text, int x, int y, DebugColor color) {
            calls.add(new DrawCall(text, x, y, color));
        }
    }

    private record DrawCall(String text, int x, int y, DebugColor color) {}

    @Test
    void lineHeight_matchesPixelFontMetricsPlusShadowPadding() {
        PixelFontTextRenderer renderer = new PixelFontTextRenderer();

        assertEquals(PixelFont.glyphHeight() + 2, renderer.lineHeight());
    }

    @Test
    void drawShadowedText_emitsShadowThenForeground() {
        RecordingPixelFontTextRenderer renderer = new RecordingPixelFontTextRenderer();

        renderer.drawShadowedText("Hello", 12, 34, DebugColor.RED);

        assertEquals(List.of(
                new DrawCall("Hello", 13, 35, DebugColor.BLACK),
                new DrawCall("Hello", 12, 34, DebugColor.RED)
        ), renderer.calls);
    }
}
