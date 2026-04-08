package com.openggf.editor;

import com.openggf.editor.render.EditorOverlayRenderer;
import com.openggf.editor.render.EditorCommandStripRenderer;
import com.openggf.editor.render.EditorToolbarRenderer;
import com.openggf.editor.render.EditorWorldOverlayRenderer;
import com.openggf.editor.render.FocusedEditorPaneRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TestEditorRenderingSmoke {

    @Test
    void focusedPaneRenderer_buildsWithoutPermanentSidebarAssumptions() {
        FocusedEditorPaneRenderer renderer = new FocusedEditorPaneRenderer();

        assertDoesNotThrow(renderer::renderBlockEditorPane);
        assertDoesNotThrow(renderer::renderChunkEditorPane);
    }

    @Test
    void overlayRenderer_splitsWorldAndScreenSpacePassesByHierarchyDepth() {
        TrackingToolbarRenderer toolbar = new TrackingToolbarRenderer();
        TrackingCommandStripRenderer commandStrip = new TrackingCommandStripRenderer();
        TrackingWorldOverlayRenderer worldOverlay = new TrackingWorldOverlayRenderer();
        TrackingFocusedEditorPaneRenderer focusedPane = new TrackingFocusedEditorPaneRenderer();
        EditorOverlayRenderer renderer = new EditorOverlayRenderer(toolbar, commandStrip, worldOverlay, focusedPane);

        assertDoesNotThrow(renderer::renderWorldSpaceOverlay);
        assertEquals(1, worldOverlay.renderCalls);
        assertEquals(0, toolbar.renderCalls);
        assertEquals(0, commandStrip.renderCalls);
        assertEquals(0, focusedPane.blockPaneCalls);
        assertEquals(0, focusedPane.chunkPaneCalls);

        assertDoesNotThrow(renderer::renderScreenSpaceOverlay);
        assertEquals(1, worldOverlay.renderCalls);
        assertEquals(1, toolbar.renderCalls);
        assertEquals(1, commandStrip.renderCalls);
        assertEquals(0, focusedPane.blockPaneCalls);
        assertEquals(0, focusedPane.chunkPaneCalls);

        renderer.setHierarchyDepth(EditorHierarchyDepth.BLOCK);
        renderer.renderWorldSpaceOverlay();
        renderer.renderScreenSpaceOverlay();

        assertEquals(1, worldOverlay.renderCalls);
        assertEquals(2, toolbar.renderCalls);
        assertEquals(2, commandStrip.renderCalls);
        assertEquals(1, focusedPane.blockPaneCalls);
        assertEquals(0, focusedPane.chunkPaneCalls);

        renderer.setHierarchyDepth(EditorHierarchyDepth.CHUNK);
        renderer.renderWorldSpaceOverlay();
        renderer.renderScreenSpaceOverlay();

        assertEquals(1, worldOverlay.renderCalls);
        assertEquals(3, toolbar.renderCalls);
        assertEquals(3, commandStrip.renderCalls);
        assertEquals(1, focusedPane.blockPaneCalls);
        assertEquals(1, focusedPane.chunkPaneCalls);
    }

    private static final class TrackingToolbarRenderer extends EditorToolbarRenderer {
        private int renderCalls;

        @Override
        public void render() {
            renderCalls++;
        }
    }

    private static final class TrackingCommandStripRenderer extends EditorCommandStripRenderer {
        private int renderCalls;

        @Override
        public void render() {
            renderCalls++;
        }
    }

    private static final class TrackingWorldOverlayRenderer extends EditorWorldOverlayRenderer {
        private int renderCalls;

        @Override
        public void render() {
            renderCalls++;
        }
    }

    private static final class TrackingFocusedEditorPaneRenderer extends FocusedEditorPaneRenderer {
        private int blockPaneCalls;
        private int chunkPaneCalls;

        @Override
        public void renderBlockEditorPane() {
            blockPaneCalls++;
        }

        @Override
        public void renderChunkEditorPane() {
            chunkPaneCalls++;
        }
    }
}
