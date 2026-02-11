package uk.co.jamesj999.sonic.game.sonic1.objects;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import uk.co.jamesj999.sonic.game.ObjectArtProvider;
import uk.co.jamesj999.sonic.game.sonic1.constants.Sonic1ObjectIds;
import uk.co.jamesj999.sonic.graphics.GraphicsManager;
import uk.co.jamesj999.sonic.level.Pattern;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.ObjectArtKeys;
import uk.co.jamesj999.sonic.level.objects.ObjectSpriteSheet;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.level.render.SpriteMappingFrame;
import uk.co.jamesj999.sonic.level.render.SpriteMappingPiece;
import uk.co.jamesj999.sonic.sprites.animation.SpriteAnimationSet;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestSonic1ChainedStomperObjectInstanceRender {
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
    public void chainedStomperSpikesUseVerticalFlip() throws Exception {
        RecordingRenderer stomperRenderer = new RecordingRenderer();
        RecordingRenderer spikeRenderer = new RecordingRenderer();
        ObjectRenderManager renderManager = new ObjectRenderManager(
                new StubObjectArtProvider(stomperRenderer, spikeRenderer));
        levelManagerField.set(null, new TestLevelManager(renderManager));

        Sonic1ChainedStomperObjectInstance stomper = new Sonic1ChainedStomperObjectInstance(
                new ObjectSpawn(100, 100, Sonic1ObjectIds.CHAINED_STOMPER, 0x00, 0, false, 0));
        stomper.appendRenderCommands(new ArrayList<>());

        assertEquals(1, spikeRenderer.drawCount);
        assertEquals(4, spikeRenderer.lastFrameIndex);
        assertFalse(spikeRenderer.lastHFlip);
        assertTrue(spikeRenderer.lastVFlip);
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
        private final PatternSpriteRenderer stomperRenderer;
        private final PatternSpriteRenderer spikeRenderer;

        private StubObjectArtProvider(PatternSpriteRenderer stomperRenderer, PatternSpriteRenderer spikeRenderer) {
            this.stomperRenderer = stomperRenderer;
            this.spikeRenderer = spikeRenderer;
        }

        @Override
        public void loadArtForZone(int zoneIndex) {
        }

        @Override
        public PatternSpriteRenderer getRenderer(String key) {
            if (ObjectArtKeys.MZ_CHAINED_STOMPER.equals(key)) {
                return stomperRenderer;
            }
            if (ObjectArtKeys.SPIKE.equals(key)) {
                return spikeRenderer;
            }
            return null;
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
            return List.of(ObjectArtKeys.MZ_CHAINED_STOMPER, ObjectArtKeys.SPIKE);
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
        private int lastFrameIndex = -1;
        private boolean lastHFlip;
        private boolean lastVFlip;

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
            lastFrameIndex = frameIndex;
            lastHFlip = hFlip;
            lastVFlip = vFlip;
        }

        private static ObjectSpriteSheet dummySheet() {
            Pattern[] patterns = {new Pattern()};
            SpriteMappingPiece piece = new SpriteMappingPiece(0, 0, 1, 1, 0, false, false, 0, false);
            return new ObjectSpriteSheet(patterns, List.of(new SpriteMappingFrame(List.of(piece))), 0, 1);
        }
    }
}
