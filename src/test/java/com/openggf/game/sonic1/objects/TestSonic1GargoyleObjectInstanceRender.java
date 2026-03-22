package com.openggf.game.sonic1.objects;

import com.openggf.game.ObjectArtProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import com.openggf.game.sonic1.constants.Sonic1ObjectIds;
import com.openggf.level.objects.DefaultObjectServices;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.LevelManager;
import com.openggf.level.Pattern;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.sprites.animation.SpriteAnimationSet;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestSonic1GargoyleObjectInstanceRender {

    private Field levelManagerField;
    private LevelManager originalLevelManager;

    @Before
    public void setUp() throws Exception {
        levelManagerField = LevelManager.class.getDeclaredField("levelManager");
        levelManagerField.setAccessible(true);
        originalLevelManager = (LevelManager) levelManagerField.get(null);
    }

    @After
    public void tearDown() throws Exception {
        levelManagerField.set(null, originalLevelManager);
    }

    @Test
    public void headRenderFlipMatchesStatusBit0() throws Exception {
        RecordingRenderer renderer = new RecordingRenderer();
        installRenderer(renderer);

        Sonic1GargoyleObjectInstance leftFacing = new Sonic1GargoyleObjectInstance(
                new ObjectSpawn(100, 100, Sonic1ObjectIds.GARGOYLE, 0, 0, false, 0));
        leftFacing.appendRenderCommands(new ArrayList<>());

        assertEquals(1, renderer.drawCount);
        assertFalse("Status bit 0 clear should render without H-flip (facing left)", renderer.lastHFlip);

        Sonic1GargoyleObjectInstance rightFacing = new Sonic1GargoyleObjectInstance(
                new ObjectSpawn(120, 100, Sonic1ObjectIds.GARGOYLE, 0, 1, false, 0));
        rightFacing.appendRenderCommands(new ArrayList<>());

        assertEquals(2, renderer.drawCount);
        assertTrue("Status bit 0 set should render with H-flip (facing right)", renderer.lastHFlip);
    }

    @Test
    public void fireballRendersWithPaletteZero() throws Exception {
        RecordingRenderer renderer = new RecordingRenderer();
        installRenderer(renderer);

        Sonic1GargoyleObjectInstance.Fireball fireball =
                new Sonic1GargoyleObjectInstance.Fireball(100, 100, true);
        fireball.setServices(new DefaultObjectServices());
        fireball.appendRenderCommands(new ArrayList<>());

        assertEquals(1, renderer.drawCount);
        assertTrue("Fireball render path should call palette override variant", renderer.usedPaletteOverride);
        assertEquals("Gar_FireBall uses make_art_tile(...,0,0), so palette line must be 0",
                0, renderer.lastPaletteOverride);
    }

    private void installRenderer(RecordingRenderer renderer) throws Exception {
        ObjectRenderManager renderManager = new ObjectRenderManager(new StubObjectArtProvider(renderer));
        levelManagerField.set(null, new TestLevelManager(renderManager));
    }

    private static final class TestLevelManager extends LevelManager {
        private final ObjectRenderManager renderManager;

        private TestLevelManager(ObjectRenderManager renderManager) {
            this.renderManager = renderManager;
        }

        @Override
        public ObjectRenderManager getObjectRenderManager() {
            return renderManager;
        }
    }

    private static final class StubObjectArtProvider implements ObjectArtProvider {
        private final PatternSpriteRenderer renderer;

        private StubObjectArtProvider(PatternSpriteRenderer renderer) {
            this.renderer = renderer;
        }

        @Override
        public void loadArtForZone(int zoneIndex) {
        }

        @Override
        public PatternSpriteRenderer getRenderer(String key) {
            return ObjectArtKeys.LZ_GARGOYLE.equals(key) ? renderer : null;
        }

        @Override
        public ObjectSpriteSheet getSheet(String key) {
            return null;
        }

        @Override
        public SpriteAnimationSet getAnimations(String key) {
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
            return List.of(ObjectArtKeys.LZ_GARGOYLE);
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
        private boolean lastHFlip;
        private boolean usedPaletteOverride;
        private int lastPaletteOverride = -1;

        private RecordingRenderer() {
            super(dummySheet());
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void drawFrameIndex(int frameIndex, int originX, int originY, boolean hFlip, boolean vFlip) {
            drawCount++;
            lastHFlip = hFlip;
            usedPaletteOverride = false;
            lastPaletteOverride = -1;
        }

        @Override
        public void drawFrameIndex(int frameIndex, int originX, int originY, boolean hFlip, boolean vFlip,
                                   int paletteOverride) {
            drawCount++;
            lastHFlip = hFlip;
            usedPaletteOverride = true;
            lastPaletteOverride = paletteOverride;
        }

        private static ObjectSpriteSheet dummySheet() {
            Pattern[] patterns = {new Pattern()};
            SpriteMappingPiece piece = new SpriteMappingPiece(0, 0, 1, 1, 0, false, false, 0, false);
            return new ObjectSpriteSheet(patterns, List.of(new SpriteMappingFrame(List.of(piece))), 0, 1);
        }
    }
}
