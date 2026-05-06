package com.openggf.testmode;

import com.openggf.graphics.PixelFontTextRenderer;
import com.openggf.trace.live.LiveTraceComparator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class TestTraceHudOverlay {

    @Test
    void renderBatchesAllTraceHudText() {
        LiveTraceComparator comparator = mock(LiveTraceComparator.class);
        when(comparator.recentMismatches()).thenReturn(List.of());

        PixelFontTextRenderer textRenderer = mock(PixelFontTextRenderer.class);
        TraceHudOverlay overlay = new TraceHudOverlay(comparator, () -> "ENTER", () -> false, () -> null);

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

    @Test
    void renderShowsPauseMessageAboveErrorsWhenPausedAfterDesync() {
        LiveTraceComparator comparator = mock(LiveTraceComparator.class);
        when(comparator.hasRecordingDesync()).thenReturn(true);
        when(comparator.recentMismatches()).thenReturn(List.of());

        PixelFontTextRenderer textRenderer = mock(PixelFontTextRenderer.class);
        TraceHudOverlay overlay = new TraceHudOverlay(comparator, () -> "SPACE", () -> true, () -> null);

        overlay.render(textRenderer);

        verify(textRenderer).drawShadowedText(
                org.mockito.ArgumentMatchers.eq(
                        "Game Paused due to recording desync. Press SPACE to resume"),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.eq(com.openggf.debug.DebugColor.RED),
                org.mockito.ArgumentMatchers.anyFloat());
        var order = inOrder(textRenderer);
        order.verify(textRenderer).drawShadowedText(
                org.mockito.ArgumentMatchers.eq(
                        "Game Paused due to recording desync. Press SPACE to resume"),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyFloat());
        order.verify(textRenderer).drawShadowedText(
                org.mockito.ArgumentMatchers.eq("ERRORS    0"),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.eq(120),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyFloat());
    }

    @Test
    void renderClearsPauseMessageAfterResumeEvenIfDesynced() {
        LiveTraceComparator comparator = mock(LiveTraceComparator.class);
        when(comparator.hasRecordingDesync()).thenReturn(true);
        when(comparator.recentMismatches()).thenReturn(List.of());

        PixelFontTextRenderer textRenderer = mock(PixelFontTextRenderer.class);
        TraceHudOverlay overlay = new TraceHudOverlay(comparator, () -> "SPACE", () -> false, () -> null);

        overlay.render(textRenderer);

        verify(textRenderer, never()).drawShadowedText(
                org.mockito.ArgumentMatchers.eq(
                        "Game Paused due to recording desync. Press SPACE to resume"),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyFloat());
    }

    @Test
    void renderDoesNotShowPauseMessageWhenUserRepausesAfterResumingDesyncPause() {
        LiveTraceComparator comparator = mock(LiveTraceComparator.class);
        when(comparator.hasRecordingDesync()).thenReturn(true);
        when(comparator.recentMismatches()).thenReturn(List.of());
        AtomicBoolean paused = new AtomicBoolean(true);

        PixelFontTextRenderer textRenderer = mock(PixelFontTextRenderer.class);
        TraceHudOverlay overlay = new TraceHudOverlay(comparator, () -> "SPACE", paused::get, () -> null);

        overlay.render(textRenderer);
        verify(textRenderer).drawShadowedText(
                org.mockito.ArgumentMatchers.eq(
                        "Game Paused due to recording desync. Press SPACE to resume"),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyFloat());

        paused.set(false);
        overlay.render(textRenderer);
        clearInvocations(textRenderer);

        paused.set(true);
        overlay.render(textRenderer);

        verify(textRenderer, never()).drawShadowedText(
                org.mockito.ArgumentMatchers.eq(
                        "Game Paused due to recording desync. Press SPACE to resume"),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyFloat());
    }

    @Test
    void renderShowsCameraFocusBlockWhenPausedAndLabelSupplied() {
        LiveTraceComparator comparator = mock(LiveTraceComparator.class);
        when(comparator.recentMismatches()).thenReturn(List.of());
        PixelFontTextRenderer textRenderer = mock(PixelFontTextRenderer.class);
        when(textRenderer.measureWidth(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyFloat())).thenReturn(40);

        TraceHudOverlay overlay = new TraceHudOverlay(
                comparator, () -> "ENTER", () -> true, () -> "Sidekick (Eng)");

        overlay.render(textRenderer);

        verify(textRenderer).drawShadowedText(
                org.mockito.ArgumentMatchers.eq("Camera: Sidekick (Eng)"),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyFloat());
        verify(textRenderer).drawShadowedText(
                org.mockito.ArgumentMatchers.eq("<- -> Cycle Cameras"),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyFloat());
    }

    @Test
    void renderShowsRewindStatusWhenSupplierReturnsText() {
        LiveTraceComparator comparator = mock(LiveTraceComparator.class);
        when(comparator.recentMismatches()).thenReturn(List.of());
        PixelFontTextRenderer textRenderer = mock(PixelFontTextRenderer.class);

        TraceHudOverlay overlay = new TraceHudOverlay(
                comparator, () -> "ENTER", () -> false, () -> null, () -> "Hold R Rewind");

        overlay.render(textRenderer);

        verify(textRenderer).drawShadowedText(
                org.mockito.ArgumentMatchers.eq("Hold R Rewind"),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.eq(com.openggf.debug.DebugColor.CYAN),
                org.mockito.ArgumentMatchers.anyFloat());
    }

    @Test
    void renderHidesCameraFocusBlockWhenNotPaused() {
        LiveTraceComparator comparator = mock(LiveTraceComparator.class);
        when(comparator.recentMismatches()).thenReturn(List.of());
        PixelFontTextRenderer textRenderer = mock(PixelFontTextRenderer.class);
        when(textRenderer.measureWidth(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyFloat())).thenReturn(40);

        TraceHudOverlay overlay = new TraceHudOverlay(
                comparator, () -> "ENTER", () -> false, () -> "Default");

        overlay.render(textRenderer);

        verify(textRenderer, never()).drawShadowedText(
                org.mockito.ArgumentMatchers.startsWith("Camera:"),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyFloat());
        verify(textRenderer, never()).drawShadowedText(
                org.mockito.ArgumentMatchers.eq("<- -> Cycle Cameras"),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyFloat());
    }

    @Test
    void renderHidesCameraFocusBlockWhenLabelSupplierReturnsNull() {
        LiveTraceComparator comparator = mock(LiveTraceComparator.class);
        when(comparator.recentMismatches()).thenReturn(List.of());
        PixelFontTextRenderer textRenderer = mock(PixelFontTextRenderer.class);

        TraceHudOverlay overlay = new TraceHudOverlay(
                comparator, () -> "ENTER", () -> true, () -> null);

        overlay.render(textRenderer);

        verify(textRenderer, never()).drawShadowedText(
                org.mockito.ArgumentMatchers.startsWith("Camera:"),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyFloat());
    }
}
