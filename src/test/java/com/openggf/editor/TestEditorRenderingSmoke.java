package com.openggf.editor;

import com.openggf.game.session.EditorCursorState;
import com.openggf.graphics.GLCommand;
import com.openggf.editor.render.EditorOverlayRenderer;
import com.openggf.editor.render.EditorCommandStripRenderer;
import com.openggf.editor.render.EditorToolbarRenderer;
import com.openggf.editor.render.EditorWorldOverlayRenderer;
import com.openggf.editor.render.FocusedEditorPaneRenderer;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TestEditorRenderingSmoke {

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

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

    @Test
    void worldOverlayRenderer_usesCurrentSessionCursorWhenRendering() {
        SessionManager.openGameplaySession(new Sonic2GameModule());
        SessionManager.enterEditorMode(new EditorCursorState(320, 448));
        CapturingWorldOverlayRenderer renderer = new CapturingWorldOverlayRenderer();

        renderer.render();

        assertEquals(SessionManager.getCurrentEditorMode().getCursor(), renderer.capturedCursor);
    }

    @Test
    void worldOverlayRenderer_buildsDifferentCursorCommandsForDifferentPositions() {
        InspectableWorldOverlayRenderer renderer = new InspectableWorldOverlayRenderer();

        List<GLCommand> first = renderer.buildCursorCommands(new EditorCursorState(320, 448));
        List<GLCommand> second = renderer.buildCursorCommands(new EditorCursorState(384, 480));

        assertFalse(first.isEmpty());
        assertFalse(second.isEmpty());
        assertNotEquals(commandSignature(first), commandSignature(second));
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

    private static final class InspectableWorldOverlayRenderer extends EditorWorldOverlayRenderer {
        private List<GLCommand> buildCursorCommands(EditorCursorState cursor) {
            List<GLCommand> commands = new ArrayList<>();
            appendCursorCommands(commands, cursor);
            return commands;
        }
    }

    private static final class CapturingWorldOverlayRenderer extends EditorWorldOverlayRenderer {
        private EditorCursorState capturedCursor;

        @Override
        protected void appendCursorCommands(List<GLCommand> commands, EditorCursorState cursor) {
            capturedCursor = cursor;
        }
    }

    private static String commandSignature(List<GLCommand> commands) {
        StringBuilder signature = new StringBuilder();
        for (GLCommand command : commands) {
            signature.append(command.getCommandType())
                    .append(':')
                    .append((int) command.getX1())
                    .append(',')
                    .append((int) command.getY1())
                    .append(';');
        }
        return signature.toString();
    }
}
