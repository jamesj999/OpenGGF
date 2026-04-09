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
import com.openggf.level.AbstractLevel;
import com.openggf.level.Block;
import com.openggf.level.Chunk;
import com.openggf.level.ChunkDesc;
import com.openggf.level.MutableLevel;
import com.openggf.level.PatternDesc;
import com.openggf.level.Pattern;
import com.openggf.level.rings.RingSpriteSheet;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.rings.RingSpawn;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

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
    void toolbarRenderer_buildsVisibleChromeCommands() {
        InspectableToolbarRenderer renderer = new InspectableToolbarRenderer();

        assertFalse(renderer.buildCommands().isEmpty());
    }

    @Test
    void commandStripRenderer_buildsVisibleChromeCommands() {
        InspectableCommandStripRenderer renderer = new InspectableCommandStripRenderer();

        assertFalse(renderer.buildCommands().isEmpty());
    }

    @Test
    void focusedPaneRenderer_buildsVisiblePaneChromeCommands() {
        InspectableFocusedEditorPaneRenderer renderer = new InspectableFocusedEditorPaneRenderer();

        assertFalse(renderer.buildBlockCommands().isEmpty());
        assertFalse(renderer.buildChunkCommands().isEmpty());
    }

    @Test
    void focusedPaneRenderer_blockPreviewPlacementsUseRealSelectedBlockComposition() {
        LevelEditorController controller = createPreviewController();
        InspectableFocusedEditorPaneRenderer renderer = new InspectableFocusedEditorPaneRenderer(controller);

        controller.selectBlock(1);
        List<FocusedEditorPaneRenderer.PreviewPlacement> first = renderer.buildBlockPreviewPlacements();

        assertEquals(16, first.size());
        assertEquals(controller.selectedBlockChunkPreview(0, 0).getPatternDesc(0, 0).get(),
                first.get(0).descriptor().get());
        assertEquals(controller.selectedBlockChunkPreview(0, 0).getPatternDesc(1, 0).get(),
                first.get(1).descriptor().get());
        assertEquals(controller.selectedBlockChunkPreview(0, 0).getPatternDesc(0, 1).get(),
                first.get(2).descriptor().get());
        assertEquals(controller.selectedBlockChunkPreview(0, 0).getPatternDesc(1, 1).get(),
                first.get(3).descriptor().get());
        assertEquals(first.get(0).x() + 8, first.get(1).x());
        assertEquals(first.get(0).y() + 8, first.get(2).y());

        controller.selectBlock(2);
        List<FocusedEditorPaneRenderer.PreviewPlacement> second = renderer.buildBlockPreviewPlacements();

        assertFalse(second.isEmpty());
        assertNotEquals(placementSignature(first), placementSignature(second));
    }

    @Test
    void focusedPaneRenderer_blockPreviewCommandsChangeWithActiveCell() {
        LevelEditorController controller = createPreviewController();
        InspectableFocusedEditorPaneRenderer renderer = new InspectableFocusedEditorPaneRenderer(controller);

        controller.selectBlock(1);
        controller.descend();
        List<GLCommand> first = renderer.buildBlockCommands();

        controller.moveActiveSelection(1, 1);
        List<GLCommand> second = renderer.buildBlockCommands();

        assertNotEquals(commandSignature(first), commandSignature(second));
    }

    @Test
    void focusedPaneRenderer_chunkPreviewPlacementsUseRealSelectedChunkComposition() {
        LevelEditorController controller = createPreviewController();
        InspectableFocusedEditorPaneRenderer renderer = new InspectableFocusedEditorPaneRenderer(controller);

        controller.selectBlock(1);
        controller.selectChunk(3);
        controller.descend();
        controller.descend();
        List<FocusedEditorPaneRenderer.PreviewPlacement> first = renderer.buildChunkPreviewPlacements();

        assertEquals(4, first.size());
        assertEquals(controller.selectedChunkPreview().getPatternDesc(0, 0).get(), first.get(0).descriptor().get());
        assertEquals(controller.selectedChunkPreview().getPatternDesc(1, 0).get(), first.get(1).descriptor().get());
        assertEquals(controller.selectedChunkPreview().getPatternDesc(0, 1).get(), first.get(2).descriptor().get());
        assertEquals(controller.selectedChunkPreview().getPatternDesc(1, 1).get(), first.get(3).descriptor().get());
        assertEquals(first.get(0).x() + 8, first.get(1).x());
        assertEquals(first.get(0).y() + 8, first.get(2).y());

        controller.selectChunk(4);
        List<FocusedEditorPaneRenderer.PreviewPlacement> second = renderer.buildChunkPreviewPlacements();

        assertFalse(second.isEmpty());
        assertNotEquals(placementSignature(first), placementSignature(second));
    }

    @Test
    void focusedPaneRenderer_chunkPreviewCommandsChangeWithActiveChunkCell() {
        LevelEditorController controller = createPreviewController();
        InspectableFocusedEditorPaneRenderer renderer = new InspectableFocusedEditorPaneRenderer(controller);

        controller.selectBlock(1);
        controller.selectChunk(3);
        controller.descend();
        controller.descend();
        List<GLCommand> first = renderer.buildChunkCommands();

        controller.moveActiveSelection(1, 1);
        List<GLCommand> second = renderer.buildChunkCommands();

        assertNotEquals(commandSignature(first), commandSignature(second));
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

    private static final class InspectableToolbarRenderer extends EditorToolbarRenderer {
        private List<GLCommand> buildCommands() {
            List<GLCommand> commands = new ArrayList<>();
            appendCommands(commands);
            return commands;
        }
    }

    private static final class InspectableCommandStripRenderer extends EditorCommandStripRenderer {
        private List<GLCommand> buildCommands() {
            List<GLCommand> commands = new ArrayList<>();
            appendCommands(commands);
            return commands;
        }
    }

    private static final class InspectableFocusedEditorPaneRenderer extends FocusedEditorPaneRenderer {
        private InspectableFocusedEditorPaneRenderer() {
            super();
        }

        private InspectableFocusedEditorPaneRenderer(LevelEditorController controller) {
            super(controller);
        }

        private List<GLCommand> buildBlockCommands() {
            List<GLCommand> commands = new ArrayList<>();
            appendBlockPaneCommands(commands);
            return commands;
        }

        private List<GLCommand> buildChunkCommands() {
            List<GLCommand> commands = new ArrayList<>();
            appendChunkPaneCommands(commands);
            return commands;
        }

        @Override
        protected void renderPreviewPlacements(List<PreviewPlacement> placements) {
            // Test-only renderer: the placement model is verified directly in dedicated assertions.
        }

        public List<PreviewPlacement> buildBlockPreviewPlacements() {
            return super.buildBlockPreviewPlacements();
        }

        public List<PreviewPlacement> buildChunkPreviewPlacements() {
            return super.buildChunkPreviewPlacements();
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

    private static String placementSignature(List<FocusedEditorPaneRenderer.PreviewPlacement> placements) {
        StringBuilder signature = new StringBuilder();
        for (FocusedEditorPaneRenderer.PreviewPlacement placement : placements) {
            signature.append(placement.descriptor().get())
                    .append('@')
                    .append(placement.x())
                    .append(',')
                    .append(placement.y())
                    .append(';');
        }
        return signature.toString();
    }

    private static LevelEditorController createPreviewController() {
        LevelEditorController controller = new LevelEditorController();
        controller.attachLevel(createPreviewLevel());
        return controller;
    }

    private static MutableLevel createPreviewLevel() {
        return MutableLevel.snapshot(new PreviewLevel());
    }

    private static final class PreviewLevel extends AbstractLevel {
        PreviewLevel() {
            super(0);
            patternCount = 1;
            patterns = new Pattern[] { new Pattern() };

            chunkCount = 9;
            chunks = new Chunk[chunkCount];
            for (int i = 0; i < chunkCount; i++) {
                chunks[i] = new Chunk();
                chunks[i].setPatternDesc(0, 0, new PatternDesc(i * 4 + 1));
                chunks[i].setPatternDesc(1, 0, new PatternDesc(i * 4 + 2));
                chunks[i].setPatternDesc(0, 1, new PatternDesc(i * 4 + 3));
                chunks[i].setPatternDesc(1, 1, new PatternDesc(i * 4 + 4));
            }

            blockCount = 3;
            blocks = new Block[blockCount];
            for (int i = 0; i < blockCount; i++) {
                blocks[i] = new Block(2);
                blocks[i].setChunkDesc(0, 0, new ChunkDesc(i));
                blocks[i].setChunkDesc(1, 0, new ChunkDesc((i + 1) % chunkCount));
                blocks[i].setChunkDesc(0, 1, new ChunkDesc((i + 2) % chunkCount));
                blocks[i].setChunkDesc(1, 1, new ChunkDesc((i + 3) % chunkCount));
            }

            solidTileCount = 0;
            solidTiles = new com.openggf.level.SolidTile[0];
            map = new com.openggf.level.Map(1, 2, 2);
            map.setValue(0, 0, 0, (byte) 0);
            map.setValue(0, 1, 0, (byte) 1);
            map.setValue(0, 0, 1, (byte) 2);
            map.setValue(0, 1, 1, (byte) 0);
            palettes = new com.openggf.level.Palette[] {
                    new com.openggf.level.Palette(),
                    new com.openggf.level.Palette(),
                    new com.openggf.level.Palette(),
                    new com.openggf.level.Palette()
            };
            objects = List.of();
            rings = List.of();
            minX = 0;
            maxX = 255;
            minY = 0;
            maxY = 191;
        }

        @Override
        public int getChunksPerBlockSide() {
            return 2;
        }

        @Override
        public int getBlockPixelSize() {
            return 128;
        }

        @Override
        public int getPaletteCount() {
            return palettes.length;
        }

        @Override
        public com.openggf.level.Palette getPalette(int index) {
            return palettes[index];
        }

        @Override
        public int getPatternCount() {
            return patternCount;
        }

        @Override
        public Pattern getPattern(int index) {
            return patterns[index];
        }

        @Override
        public int getChunkCount() {
            return chunkCount;
        }

        @Override
        public Chunk getChunk(int index) {
            return chunks[index];
        }

        @Override
        public int getBlockCount() {
            return blockCount;
        }

        @Override
        public Block getBlock(int index) {
            return blocks[index];
        }

        @Override
        public com.openggf.level.SolidTile getSolidTile(int index) {
            return solidTiles[index];
        }

        @Override
        public com.openggf.level.Map getMap() {
            return map;
        }

        @Override
        public List<ObjectSpawn> getObjects() {
            return objects;
        }

        @Override
        public List<RingSpawn> getRings() {
            return rings;
        }

        @Override
        public RingSpriteSheet getRingSpriteSheet() {
            return null;
        }

        @Override
        public int getMinX() {
            return minX;
        }

        @Override
        public int getMaxX() {
            return maxX;
        }

        @Override
        public int getMinY() {
            return minY;
        }

        @Override
        public int getMaxY() {
            return maxY;
        }

        @Override
        public int getZoneIndex() {
            return 0;
        }
    }
}
