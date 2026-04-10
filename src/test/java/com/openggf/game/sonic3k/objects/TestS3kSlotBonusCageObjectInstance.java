package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameRuntime;
import com.openggf.game.ObjectArtProvider;
import com.openggf.game.RuntimeManager;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.bonusstage.slots.S3kSlotStageController;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.LevelManager;
import com.openggf.level.Pattern;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.sprites.playable.Sonic;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS3kSlotBonusCageObjectInstance {

    private Field levelManagerField;
    private LevelManager originalLevelManager;
    private GameRuntime originalRuntime;

    @AfterEach
    void tearDown() throws Exception {
        if (levelManagerField != null) {
            levelManagerField.set(null, originalLevelManager);
        }
        RuntimeManager.setCurrent(originalRuntime);
    }

    @Test
    void captureCentersNearbyPlayableAndLocksControl() {
        ObjectSpawn spawn = new ObjectSpawn(0x460, 0x430, 0x00, 0x00, 0x00, false, 0);
        S3kSlotStageController controller = new S3kSlotStageController();
        controller.bootstrap();

        S3kSlotBonusCageObjectInstance cage = new S3kSlotBonusCageObjectInstance(spawn, controller);
        cage.setServices(new TestObjectServices());

        Sonic player = new Sonic("sonic", (short) 0x44C, (short) 0x41C);
        player.setXSpeed((short) 0x0123);
        player.setYSpeed((short) -0x0456);
        player.setGSpeed((short) 0x0789);
        player.setAir(false);
        player.setOnObject(true);

        cage.update(0, player);

        assertEquals(spawn.x(), player.getCentreX());
        assertEquals(spawn.y(), player.getCentreY());
        assertEquals(0, player.getXSpeed());
        assertEquals(0, player.getYSpeed());
        assertEquals(0, player.getGSpeed());
        assertTrue(player.isObjectControlled());
        assertTrue(player.isControlLocked());
        assertTrue(player.getAir());
        assertFalse(player.isOnObject());
    }

    @Test
    void debugPlayableUpdatesAnimatedAnchorButDoesNotCapture() {
        ObjectSpawn spawn = new ObjectSpawn(0x460, 0x430, 0x00, 0x00, 0x00, false, 0);
        S3kSlotStageController controller = new S3kSlotStageController();
        controller.bootstrap();
        controller.setScalarIndex(0x4000);
        controller.tick();

        S3kSlotBonusCageObjectInstance cage = new S3kSlotBonusCageObjectInstance(spawn, controller);
        cage.setServices(new TestObjectServices());

        Sonic player = new Sonic("sonic", (short) 0x430, (short) 0x440);
        player.setDebugMode(true);
        short originalX = player.getX();
        short originalY = player.getY();

        cage.update(0, player);

        int angle = controller.angle() & 0xFC;
        int sin = com.openggf.physics.TrigLookupTable.sinHex(angle);
        int cos = com.openggf.physics.TrigLookupTable.cosHex(angle);
        int dx = spawn.x() - player.getCentreX();
        int dy = spawn.y() - player.getCentreY();
        int expectedX = (((dx * cos) - (dy * sin)) >> 8) + player.getCentreX();
        int expectedY = (((dx * sin) + (dy * cos)) >> 8) + player.getCentreY();
        assertEquals(expectedX, cage.getCurrentX());
        assertEquals(expectedY, cage.getCurrentY());
        assertEquals(originalX, player.getX());
        assertEquals(originalY, player.getY());
        assertFalse(player.isObjectControlled());
        assertFalse(player.isControlLocked());
    }

    @Test
    void captureLatchesPositiveResolvedPrizeIntoRingPayout() throws Exception {
        ObjectSpawn spawn = new ObjectSpawn(0x460, 0x430, 0x00, 0x00, 0x00, false, 0);
        S3kSlotStageController controller = new S3kSlotStageController();
        controller.bootstrap();
        controller.latchResolvedPrizeForCapture(6);
        forceOptionCycleState(controller, 0x0C);

        S3kSlotBonusCageObjectInstance cage = new S3kSlotBonusCageObjectInstance(spawn, controller);
        cage.setServices(new TestObjectServices());

        Sonic player = new Sonic("sonic", (short) 0x460, (short) 0x430);
        cage.update(0, player);
        forceOptionCycleState(controller, 0x18);
        cage.update(2, player);

        assertTrue(cage.spawnsRingsForTest());
        assertEquals(6, cage.pendingRewardsForTest());
    }

    @Test
    void captureLatchesNegativeResolvedPrizeIntoFixedSpikePenaltyBudget() throws Exception {
        ObjectSpawn spawn = new ObjectSpawn(0x460, 0x430, 0x00, 0x00, 0x00, false, 0);
        S3kSlotStageController controller = new S3kSlotStageController();
        controller.bootstrap();
        controller.latchResolvedPrizeForCapture(-4);
        forceOptionCycleState(controller, 0x0C);

        S3kSlotBonusCageObjectInstance cage = new S3kSlotBonusCageObjectInstance(spawn, controller);
        cage.setServices(new TestObjectServices());

        Sonic player = new Sonic("sonic", (short) 0x460, (short) 0x430);
        cage.update(0, player);
        forceOptionCycleState(controller, 0x18);
        cage.update(2, player);

        assertFalse(cage.spawnsRingsForTest());
        assertEquals(0x64, cage.pendingRewardsForTest());
    }

    @Test
    void captureDoesNotStartRewardSpawnWhileOptionCycleIsStillUnresolved() throws Exception {
        ObjectSpawn spawn = new ObjectSpawn(0x460, 0x430, 0x00, 0x00, 0x00, false, 0);
        S3kSlotStageController controller = new S3kSlotStageController();
        controller.bootstrap();
        controller.latchResolvedPrizeForCapture(6);
        forceOptionCycleState(controller, 0x0C);

        S3kSlotBonusCageObjectInstance cage = new S3kSlotBonusCageObjectInstance(spawn, controller);
        cage.setServices(new TestObjectServices());

        Sonic player = new Sonic("sonic", (short) 0x460, (short) 0x430);
        cage.update(0, player);

        assertEquals(1, cage.cageStateForTest());
        assertEquals(0, cage.pendingRewardsForTest());
    }

    @Test
    void appendRenderCommandsSuppressesUnusedCageVisual() throws Exception {
        RecordingRenderer renderer = new RecordingRenderer();
        installRenderer(renderer, Sonic3kObjectArtKeys.SLOT_BONUS_CAGE);

        ObjectSpawn spawn = new ObjectSpawn(0x460, 0x430, 0x00, 0x00, 0x00, false, 0);
        S3kSlotStageController controller = new S3kSlotStageController();
        controller.bootstrap();

        S3kSlotBonusCageObjectInstance cage = new S3kSlotBonusCageObjectInstance(spawn, controller);
        cage.setServices(new TestObjectServices());

        Sonic player = new Sonic("sonic", (short) 0x420, (short) 0x400);
        cage.suppressInitialCaptureOnce();
        cage.update(0, player);
        cage.update(1, player);

        cage.appendRenderCommands(new ArrayList<>());

        assertEquals(0, renderer.drawCount);
    }

    private void installRenderer(RecordingRenderer renderer, String artKey) throws Exception {
        levelManagerField = LevelManager.class.getDeclaredField("levelManager");
        levelManagerField.setAccessible(true);
        originalLevelManager = (LevelManager) levelManagerField.get(null);
        originalRuntime = RuntimeManager.getCurrent();
        RuntimeManager.setCurrent(null);
        ObjectRenderManager renderManager = new ObjectRenderManager(new StubObjectArtProvider(renderer, artKey));
        levelManagerField.set(null, new TestLevelManager(renderManager));
    }

    private void forceOptionCycleState(S3kSlotStageController controller, int stateValue) throws Exception {
        Field stageStateField = S3kSlotStageController.class.getDeclaredField("stageState");
        stageStateField.setAccessible(true);
        Object stageState = stageStateField.get(controller);
        Field optionCycleStateField = stageState.getClass().getDeclaredField("optionCycleState");
        optionCycleStateField.setAccessible(true);
        optionCycleStateField.setInt(stageState, stateValue);
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
        private int lastFrameIndex = -1;
        private int lastX;
        private int lastY;

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
            lastX = originX;
            lastY = originY;
        }

        private static ObjectSpriteSheet dummySheet() {
            Pattern[] patterns = {new Pattern()};
            SpriteMappingPiece piece = new SpriteMappingPiece(0, 0, 1, 1, 0, false, false, 0, false);
            return new ObjectSpriteSheet(patterns, List.of(new SpriteMappingFrame(List.of(piece))), 0, 1);
        }
    }
}
