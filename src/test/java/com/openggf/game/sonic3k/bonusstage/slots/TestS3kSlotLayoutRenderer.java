package com.openggf.game.sonic3k.bonusstage.slots;

import com.openggf.game.ObjectArtProvider;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Pattern;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS3kSlotLayoutRenderer {
    private static final int BOOTSTRAP_CAMERA_X = S3kSlotRomData.SLOT_BONUS_PLAYER_START_X - 0xA0;
    private static final int BOOTSTRAP_CAMERA_Y = S3kSlotRomData.SLOT_BONUS_PLAYER_START_Y - 0x70;

    @Test
    void renderBuildsVisiblePiecesFromStagedExpandedBuffers() {
        S3kSlotLayoutRenderer renderer = new S3kSlotLayoutRenderer();
        S3kSlotRenderBuffers buffers = S3kSlotRenderBuffers.fromRomData();

        buffers.stageViewport(BOOTSTRAP_CAMERA_X, BOOTSTRAP_CAMERA_Y);
        buffers.stagePointGrid(renderer.buildPointGrid(0, BOOTSTRAP_CAMERA_X, BOOTSTRAP_CAMERA_Y));

        List<S3kSlotLayoutRenderer.VisibleCell> cells = renderer.buildVisibleCells(buffers);

        assertFalse(cells.isEmpty());
        assertTrue(cells.stream().anyMatch(cell -> cell.cellId() == 5));
        assertTrue(cells.stream().anyMatch(cell -> cell.cellId() == 7));
        assertTrue(cells.stream().allMatch(cell -> cell.worldX() >= BOOTSTRAP_CAMERA_X - 0x10
                && cell.worldX() < BOOTSTRAP_CAMERA_X + 0x150));
        assertTrue(cells.stream().allMatch(cell -> cell.worldY() >= BOOTSTRAP_CAMERA_Y - 0x10
                && cell.worldY() < BOOTSTRAP_CAMERA_Y + 0xF0));
    }

    @Test
    void transientRingAnimationUsesRuntimeAnimationSlots() {
        S3kSlotLayoutRenderer renderer = new S3kSlotLayoutRenderer();
        S3kSlotRenderBuffers buffers = S3kSlotRenderBuffers.fromRomData();

        int expandedIndex = buffers.compactToExpandedIndex(0x21);
        int expandedRow = expandedIndex / buffers.layoutStrideBytes();
        int expandedCol = expandedIndex % buffers.layoutStrideBytes();
        buffers.stageViewport(BOOTSTRAP_CAMERA_X, BOOTSTRAP_CAMERA_Y);
        buffers.stagePointGrid(renderer.buildPointGrid(0, BOOTSTRAP_CAMERA_X, BOOTSTRAP_CAMERA_Y));
        buffers.startRingAnimationAt(0x21);

        renderer.tickTransientAnimations(buffers);

        assertTrue(buffers.hasActiveTransientAnimationAt(0x21));
        assertEquals(0x10, buffers.renderCellIdAt(expandedRow, expandedCol));
    }

    @Test
    void transientRingAnimationAdvancesAndFallsBackToExpandedLayoutTile() {
        S3kSlotLayoutRenderer renderer = new S3kSlotLayoutRenderer();
        S3kSlotRenderBuffers buffers = S3kSlotRenderBuffers.fromRomData();
        int expandedIndex = buffers.compactToExpandedIndex(0x21);
        int expandedRow = expandedIndex / buffers.layoutStrideBytes();
        int expandedCol = expandedIndex % buffers.layoutStrideBytes();
        buffers.expandedLayout()[expandedIndex] = 8;

        buffers.startRingAnimationAt(0x21);

        for (int i = 0; i < S3kSlotRomData.RING_SPARKLE_DELAY; i++) {
            renderer.tickTransientAnimations(buffers);
        }
        assertEquals(0x11, buffers.renderCellIdAt(expandedRow, expandedCol));

        for (int i = 0; i < S3kSlotRomData.RING_SPARKLE_DELAY; i++) {
            renderer.tickTransientAnimations(buffers);
        }
        assertEquals(0x12, buffers.renderCellIdAt(expandedRow, expandedCol));

        for (int i = 0; i < S3kSlotRomData.RING_SPARKLE_DELAY; i++) {
            renderer.tickTransientAnimations(buffers);
        }
        assertEquals(0x13, buffers.renderCellIdAt(expandedRow, expandedCol));

        for (int i = 0; i < S3kSlotRomData.RING_SPARKLE_DELAY; i++) {
            renderer.tickTransientAnimations(buffers);
        }
        assertFalse(buffers.hasActiveTransientAnimationAt(0x21));
        assertEquals(8, buffers.renderCellIdAt(expandedRow, expandedCol));
    }

    @Test
    void transientBumperAnimationUsesExactCompactLayoutIndex() {
        S3kSlotLayoutRenderer renderer = new S3kSlotLayoutRenderer();
        S3kSlotRenderBuffers buffers = S3kSlotRenderBuffers.fromRomData();
        int compactIndex = 0x22;
        int expandedIndex = buffers.compactToExpandedIndex(compactIndex);
        int expandedRow = expandedIndex / buffers.layoutStrideBytes();
        int expandedCol = expandedIndex % buffers.layoutStrideBytes();

        buffers.startBumperAnimationAt(compactIndex);
        renderer.tickTransientAnimations(buffers);

        assertEquals(0x0A, buffers.renderCellIdAt(expandedRow, expandedCol));
    }

    @Test
    void zeroAngleBuildsStable16x16PointGrid() {
        S3kSlotLayoutRenderer renderer = new S3kSlotLayoutRenderer();

        short[] points = renderer.buildPointGrid(0, 0, 0);

        assertEquals(16 * 16 * 2, points.length);
        assertArrayEquals(new short[] {(short) -0xB4, (short) -0xB4}, new short[] {points[0], points[1]});
        assertArrayEquals(new short[] {(short) -0x9C, (short) -0xB4}, new short[] {points[2], points[3]});
        int secondRowIndex = 16 * 2;
        assertArrayEquals(new short[] {(short) -0xB4, (short) -0x9C},
                new short[] {points[secondRowIndex], points[secondRowIndex + 1]});
    }

    @Test
    void quarterTurnRotatesGridBasisClockwise() {
        S3kSlotLayoutRenderer renderer = new S3kSlotLayoutRenderer();

        short[] points = renderer.buildPointGrid(0x40, 0, 0);

        assertArrayEquals(new short[] {(short) 0xB4, (short) -0xB4}, new short[] {points[0], points[1]});
        assertArrayEquals(new short[] {(short) 0xB4, (short) -0x9C}, new short[] {points[2], points[3]});
        int secondRowIndex = 16 * 2;
        assertArrayEquals(new short[] {(short) 0x9C, (short) -0xB4},
                new short[] {points[secondRowIndex], points[secondRowIndex + 1]});
    }

    @Test
    void transformStagePointUsesSameLayoutTransformAsVisibleGrid() {
        S3kSlotLayoutRenderer renderer = new S3kSlotLayoutRenderer();

        S3kSlotLayoutRenderer.TransformedStagePoint point = renderer.transformStagePoint(
                0,
                BOOTSTRAP_CAMERA_X,
                BOOTSTRAP_CAMERA_Y,
                S3kSlotRomData.SLOT_BONUS_CAGE_CENTER_X,
                S3kSlotRomData.SLOT_BONUS_CAGE_CENTER_Y);

        assertEquals(0x44C, point.worldX());
        assertEquals(0x3EC, point.worldY());
        assertEquals(0x10C, point.screenX());
        assertEquals(0x17C, point.screenY());
    }

    @Test
    void visibleCellsIncludeSemanticSlotStagePieces() {
        S3kSlotLayoutRenderer renderer = new S3kSlotLayoutRenderer();
        S3kSlotRenderBuffers buffers = S3kSlotRenderBuffers.fromRomData();

        buffers.stageViewport(BOOTSTRAP_CAMERA_X, BOOTSTRAP_CAMERA_Y);
        buffers.stagePointGrid(renderer.buildPointGrid(0, BOOTSTRAP_CAMERA_X, BOOTSTRAP_CAMERA_Y));

        List<S3kSlotLayoutRenderer.VisibleCell> cells = renderer.buildVisibleCells(buffers);

        assertFalse(cells.isEmpty());
        assertTrue(cells.stream().anyMatch(cell -> cell.cellId() == 5));
        assertTrue(cells.stream().anyMatch(cell -> cell.cellId() == 7));
        assertTrue(cells.stream().anyMatch(cell -> cell.cellId() == 8));
        assertTrue(cells.stream().allMatch(cell -> cell.worldX() >= BOOTSTRAP_CAMERA_X - 0x10
                && cell.worldX() < BOOTSTRAP_CAMERA_X + 0x150));
        assertTrue(cells.stream().allMatch(cell -> cell.worldY() >= BOOTSTRAP_CAMERA_Y - 0x10
                && cell.worldY() < BOOTSTRAP_CAMERA_Y + 0xF0));
    }

    @Test
    void renderVisibleCellsUsesWorldCoordinates() {
        S3kSlotLayoutRenderer renderer = new S3kSlotLayoutRenderer();
        RecordingRenderer recordingRenderer = new RecordingRenderer();
        ObjectRenderManager renderManager = new ObjectRenderManager(
                new StubObjectArtProvider(recordingRenderer, com.openggf.game.sonic3k.Sonic3kObjectArtKeys.SLOT_COLORED_WALL));

        renderer.renderVisibleCells(
                List.of(new S3kSlotLayoutRenderer.VisibleCell((byte) 0x01, 0x450, 0x390)),
                new StubCamera(0x460, 0x430),
                renderManager);

        assertEquals(1, recordingRenderer.drawCount);
        assertEquals(0x450, recordingRenderer.lastX);
        assertEquals(0x390, recordingRenderer.lastY);
    }

    @Test
    void coloredWallsUseAngleDrivenFrameOverride() {
        S3kSlotLayoutRenderer renderer = new S3kSlotLayoutRenderer();
        RecordingRenderer recordingRenderer = new RecordingRenderer();
        ObjectRenderManager renderManager = new ObjectRenderManager(
                new StubObjectArtProvider(recordingRenderer, com.openggf.game.sonic3k.Sonic3kObjectArtKeys.SLOT_COLORED_WALL));

        renderer.updateAnimations(0x1C);
        renderer.renderVisibleCells(
                List.of(new S3kSlotLayoutRenderer.VisibleCell((byte) 0x01, 0x450, 0x390)),
                new StubCamera(0x460, 0x430),
                renderManager);

        assertEquals(1, recordingRenderer.drawCount);
        assertEquals(7, recordingRenderer.lastFrameIndex);
    }

    @Test
    void coloredWallsDoNotForceSinglePaletteOverride() {
        S3kSlotLayoutRenderer renderer = new S3kSlotLayoutRenderer();
        RecordingRenderer recordingRenderer = new RecordingRenderer();
        ObjectRenderManager renderManager = new ObjectRenderManager(
                new StubObjectArtProvider(recordingRenderer, com.openggf.game.sonic3k.Sonic3kObjectArtKeys.SLOT_COLORED_WALL));

        renderer.renderVisibleCells(
                List.of(new S3kSlotLayoutRenderer.VisibleCell((byte) 0x01, 0x450, 0x390)),
                new StubCamera(0x460, 0x430),
                renderManager);

        assertEquals(1, recordingRenderer.drawCount);
        assertEquals(Integer.MIN_VALUE, recordingRenderer.lastPaletteOverride);
        assertEquals(3, recordingRenderer.lastPaletteBase);
    }

    @Test
    void slotStageRingsUseLiveRingRotationFrame() {
        S3kSlotLayoutRenderer renderer = new S3kSlotLayoutRenderer();
        RecordingRenderer recordingRenderer = new RecordingRenderer();
        ObjectRenderManager renderManager = new ObjectRenderManager(
                new StubObjectArtProvider(recordingRenderer, com.openggf.game.sonic3k.Sonic3kObjectArtKeys.SLOT_RING_STAGE));

        for (int i = 0; i < 8; i++) {
            renderer.updateAnimations(0);
        }
        renderer.renderVisibleCells(
                List.of(new S3kSlotLayoutRenderer.VisibleCell((byte) 0x08, 0x450, 0x390)),
                new StubCamera(0x460, 0x430),
                renderManager);

        assertEquals(1, recordingRenderer.drawCount);
        assertEquals(1, recordingRenderer.lastFrameIndex);
    }

    private static final class StubCamera extends com.openggf.camera.Camera {
        private StubCamera(int x, int y) {
            setX((short) x);
            setY((short) y);
        }
    }

    private static final class StubObjectArtProvider implements ObjectArtProvider {
        private final PatternSpriteRenderer renderer;
        private final String artKey;

        private StubObjectArtProvider(PatternSpriteRenderer renderer, String artKey) {
            this.renderer = renderer;
            this.artKey = artKey;
        }

        @Override
        public void loadArtForZone(int zoneIndex) {
        }

        @Override
        public PatternSpriteRenderer getRenderer(String key) {
            return artKey.equals(key) ? renderer : null;
        }

        @Override
        public ObjectSpriteSheet getSheet(String key) {
            return null;
        }

        @Override
        public com.openggf.sprites.animation.SpriteAnimationSet getAnimations(String key) {
            return null;
        }

        @Override
        public int getZoneData(String key, int zoneIndex) {
            return -1;
        }

        @Override
        public Pattern[] getHudDigitPatterns() {
            return new Pattern[0];
        }

        @Override
        public Pattern[] getHudTextPatterns() {
            return new Pattern[0];
        }

        @Override
        public Pattern[] getHudLivesPatterns() {
            return new Pattern[0];
        }

        @Override
        public Pattern[] getHudLivesNumbers() {
            return new Pattern[0];
        }

        @Override
        public List<String> getRendererKeys() {
            return List.of(artKey);
        }

        @Override
        public int ensurePatternsCached(GraphicsManager graphicsManager, int baseIndex) {
            return baseIndex;
        }

        @Override
        public boolean isReady() {
            return true;
        }
    }

    private static final class RecordingRenderer extends PatternSpriteRenderer {
        private int drawCount;
        private int lastFrameIndex;
        private int lastX;
        private int lastY;
        private int lastPaletteOverride = Integer.MIN_VALUE;
        private int lastPaletteBase = Integer.MIN_VALUE;

        private RecordingRenderer() {
            super(dummySheet());
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void drawFrameIndex(int frameIndex, int originX, int originY, boolean hFlip, boolean vFlip, int paletteOverride) {
            drawCount++;
            lastFrameIndex = frameIndex;
            lastX = originX;
            lastY = originY;
            lastPaletteOverride = paletteOverride;
        }

        @Override
        public void drawFrameIndexWithPaletteBase(int frameIndex, int originX, int originY,
                boolean hFlip, boolean vFlip, int paletteBase) {
            drawCount++;
            lastFrameIndex = frameIndex;
            lastX = originX;
            lastY = originY;
            lastPaletteBase = paletteBase;
        }

        private static ObjectSpriteSheet dummySheet() {
            Pattern[] patterns = {new Pattern()};
            SpriteMappingPiece piece = new SpriteMappingPiece(0, 0, 1, 1, 0, false, false, 0, false);
            return new ObjectSpriteSheet(patterns, List.of(new SpriteMappingFrame(List.of(piece))), 0, 1);
        }
    }
}
