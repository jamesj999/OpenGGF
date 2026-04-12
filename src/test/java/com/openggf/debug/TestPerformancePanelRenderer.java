package com.openggf.debug;

import com.openggf.graphics.PixelFont;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestPerformancePanelRenderer {

    @Test
    void performanceTextScale_isHalfSize() {
        assertEquals(0.5f, PerformancePanelRenderer.PERFORMANCE_TEXT_SCALE);
    }

    @Test
    void lineHeight_usesHalfScale() throws Exception {
        Method lineHeight = PerformancePanelRenderer.class.getDeclaredMethod("lineHeight", FontSize.class);
        lineHeight.setAccessible(true);

        assertEquals(5, lineHeight.invoke(null, FontSize.SMALL));
        assertEquals(6, lineHeight.invoke(null, FontSize.MEDIUM));
        assertEquals(7, lineHeight.invoke(null, FontSize.LARGE));
    }

    @Test
    void halfScaleUsesScaledPixelFontHeight() {
        assertEquals(5, PixelFont.scaledGlyphHeight(PerformancePanelRenderer.PERFORMANCE_TEXT_SCALE));
    }

    @Test
    void heapLine_isCompactedToFitRightColumn() throws Exception {
        Method formatHeapLine = PerformancePanelRenderer.class.getDeclaredMethod(
                "formatHeapLine", StringBuilder.class, double.class, double.class, int.class);
        formatHeapLine.setAccessible(true);

        String line = (String) formatHeapLine.invoke(null, new StringBuilder(), 123.0, 456.0, 78);

        assertEquals("Heap 123/456 78%", line);
        assertTrue(new PixelFont().measureWidth(line, PerformancePanelRenderer.PERFORMANCE_TEXT_SCALE) <= 85);
    }

    @Test
    void gcLine_isCompactedToFitRightColumn() throws Exception {
        Method formatGcLine = PerformancePanelRenderer.class.getDeclaredMethod(
                "formatGcLine", StringBuilder.class, long.class, long.class, double.class);
        formatGcLine.setAccessible(true);

        String line = (String) formatGcLine.invoke(null, new StringBuilder(), 12L, 345L, 6.7);

        assertEquals("GC 12 345ms 6.7M/s", line);
        assertTrue(new PixelFont().measureWidth(line, PerformancePanelRenderer.PERFORMANCE_TEXT_SCALE) <= 85);
    }
}
