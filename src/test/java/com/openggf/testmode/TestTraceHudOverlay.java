package com.openggf.testmode;

import com.openggf.graphics.PixelFontTextRenderer;
import com.openggf.trace.live.LiveTraceComparator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestTraceHudOverlay {

    @Test
    void renderBatchesAllTraceHudText() {
        LiveTraceComparator comparator = mock(LiveTraceComparator.class);
        when(comparator.recentMismatches()).thenReturn(List.of());

        PixelFontTextRenderer textRenderer = mock(PixelFontTextRenderer.class);
        TraceHudOverlay overlay = new TraceHudOverlay(comparator);

        overlay.render(textRenderer);

        var order = inOrder(textRenderer);
        order.verify(textRenderer).beginBatch();
        order.verify(textRenderer).drawShadowedText(
                org.mockito.ArgumentMatchers.eq("ERRORS    0"),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyFloat());
        order.verify(textRenderer).endBatch();
    }
}
