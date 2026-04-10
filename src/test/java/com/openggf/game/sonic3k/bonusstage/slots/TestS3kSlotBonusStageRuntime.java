package com.openggf.game.sonic3k.bonusstage.slots;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.ObjectArtProvider;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.GameRuntime;
import com.openggf.game.GameServices;
import com.openggf.game.RuntimeManager;
import com.openggf.game.sonic3k.objects.S3kSlotRingRewardObjectInstance;
import com.openggf.game.sonic3k.objects.S3kSlotSpikeRewardObjectInstance;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.LevelManager;
import com.openggf.level.Pattern;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.Tails;
import com.openggf.sprites.render.PlayerSpriteRenderer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS3kSlotBonusStageRuntime {
    private Field levelManagerField;
    private LevelManager originalLevelManager;

    @AfterEach
    void tearDown() throws Exception {
        if (levelManagerField != null) {
            levelManagerField.set(null, originalLevelManager);
        }
        RuntimeManager.destroyCurrent();
        SonicConfigurationService.getInstance().resetToDefaults();
    }

    @Test
    void bootstrapReplacesTailsMainCharacterAtRawPositionTransfersRendererStateAndRemovesSidekicks() {
        RuntimeManager.createGameplay();
        SonicConfigurationService.getInstance().setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "tails");

        AbstractPlayableSprite originalPlayer = new Tails("tails", (short) 0x460, (short) 0x430);
        PlayerSpriteRenderer renderer = new PlayerSpriteRenderer(SpriteArtSet.EMPTY);
        originalPlayer.setSpriteRenderer(renderer);
        originalPlayer.setMappingFrame(3);
        originalPlayer.setAnimationFrameCount(5);
        originalPlayer.setAnimationId(7);
        originalPlayer.setAnimationFrameIndex(2);
        originalPlayer.setAnimationTick(11);
        GameServices.sprites().addSprite(originalPlayer);
        GameServices.camera().setFocusedSprite(originalPlayer);

        AbstractPlayableSprite sidekick = new Sonic("sonic_p2", (short) 0x420, (short) 0x430);
        sidekick.setCpuControlled(true);
        GameServices.sprites().addSprite(sidekick, "sonic");

        S3kSlotBonusStageRuntime runtime = new S3kSlotBonusStageRuntime();
        runtime.bootstrap();

        assertTrue(runtime.isInitialized());
        assertNotNull(runtime.activeSlotCageForTest());
        assertTrue(runtime.activeSlotRingRewardsForTest().isEmpty());
        assertTrue(runtime.activeSlotSpikeRewardsForTest().isEmpty());
        assertTrue(GameServices.sprites().getSprite("tails") instanceof S3kSlotBonusPlayer);

        AbstractPlayableSprite slotPlayer = assertInstanceOf(Tails.class, GameServices.sprites().getSprite("tails"));
        assertTrue(slotPlayer instanceof S3kSlotBonusPlayer);
        assertFalse(slotPlayer instanceof Sonic);
        assertEquals("tails", slotPlayer.getCode());
        assertEquals(S3kSlotRomData.SLOT_BONUS_PLAYER_START_X, slotPlayer.getCentreX());
        assertEquals(S3kSlotRomData.SLOT_BONUS_PLAYER_START_Y, slotPlayer.getCentreY());
        assertSame(renderer, slotPlayer.getSpriteRenderer());
        assertEquals(3, slotPlayer.getMappingFrame());
        assertEquals(5, slotPlayer.getAnimationFrameCount());
        assertEquals(7, slotPlayer.getAnimationId());
        assertEquals(2, slotPlayer.getAnimationFrameIndex());
        assertEquals(11, slotPlayer.getAnimationTick());
        assertTrue(GameServices.sprites().getSidekicks().isEmpty());
        assertNull(GameServices.sprites().getSprite("sonic_p2"));
        assertNotSame(originalPlayer, slotPlayer);
        assertSame(slotPlayer, GameServices.camera().getFocusedSprite());
        assertEquals(slotPlayer.getCentreX() - 0xA0, GameServices.camera().getX());
        assertEquals(slotPlayer.getCentreY() - 0x70, GameServices.camera().getY());
        assertNotNull(runtime.activeLayoutForTest());
        assertEquals(32 * 32, runtime.activeLayoutForTest().length);

        runtime.shutdown();

        assertSame(sidekick, GameServices.sprites().getSprite("sonic_p2"));
        assertEquals(1, GameServices.sprites().getSidekicks().size());
    }

    @Test
    void queuedRingRewardActivatesInsideRuntimeAndExpires() {
        RuntimeManager.createGameplay();
        SonicConfigurationService.getInstance().setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "tails");

        Tails originalPlayer = new Tails("tails", (short) 0x460, (short) 0x430);
        GameServices.sprites().addSprite(originalPlayer);
        GameServices.camera().setFocusedSprite(originalPlayer);

        S3kSlotBonusStageRuntime runtime = new S3kSlotBonusStageRuntime();
        runtime.bootstrap();

        AbstractPlayableSprite slotPlayer = assertInstanceOf(
                AbstractPlayableSprite.class, GameServices.sprites().getSprite("tails"));
        // Move player away from cage to prevent capture interference
        slotPlayer.setX((short) 0x200);
        slotPlayer.setY((short) 0x200);
        assertTrue(runtime.activeSlotRingRewardsForTest().isEmpty());

        runtime.queueRingReward();
        runtime.update(0);
        assertFalse(runtime.activeSlotRingRewardsForTest().isEmpty());
        // ROM Obj_SlotRing: 0x1A frames interpolation + 8 frames sparkle before deletion
        for (int frame = 1; frame <= 0x1A + 8; frame++) {
            runtime.update(frame);
        }

        assertTrue(runtime.activeSlotRingRewardsForTest().isEmpty());
        assertSame(slotPlayer, GameServices.sprites().getSprite("tails"));
    }

    @Test
    void queuedRingRewardsSpawnIndependentTransientChildren() {
        RuntimeManager.createGameplay();
        SonicConfigurationService.getInstance().setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "tails");

        Tails originalPlayer = new Tails("tails", (short) 0x460, (short) 0x430);
        GameServices.sprites().addSprite(originalPlayer);
        GameServices.camera().setFocusedSprite(originalPlayer);

        S3kSlotBonusStageRuntime runtime = new S3kSlotBonusStageRuntime();
        runtime.bootstrap();

        runtime.queueRingReward();
        runtime.queueRingReward();
        runtime.queueRingReward();
        runtime.update(0);

        assertEquals(3, runtime.activeSlotRingRewardsForTest().size());
        assertTrue(runtime.activeSlotRingRewardsForTest().stream().allMatch(S3kSlotRingRewardObjectInstance::isActive));
    }

    @Test
    void queuedSpikeRewardsSpawnIndependentTransientChildren() {
        RuntimeManager.createGameplay();
        SonicConfigurationService.getInstance().setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "tails");

        Tails originalPlayer = new Tails("tails", (short) 0x460, (short) 0x430);
        GameServices.sprites().addSprite(originalPlayer);
        GameServices.camera().setFocusedSprite(originalPlayer);

        S3kSlotBonusStageRuntime runtime = new S3kSlotBonusStageRuntime();
        runtime.bootstrap();

        runtime.queueSpikeReward();
        runtime.queueSpikeReward();
        runtime.update(0);

        assertEquals(2, runtime.activeSlotSpikeRewardsForTest().size());
        assertTrue(runtime.activeSlotSpikeRewardsForTest().stream().allMatch(S3kSlotSpikeRewardObjectInstance::isActive));
    }

    @Test
    void bootstrapPreservesLiveCollisionBitsOnSwappedSlotPlayer() {
        RuntimeManager.createGameplay();
        SonicConfigurationService.getInstance().setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "tails");

        AbstractPlayableSprite originalPlayer = new Tails("tails", (short) 0x460, (short) 0x430);
        originalPlayer.setTopSolidBit((byte) 0x02);
        originalPlayer.setLrbSolidBit((byte) 0x03);
        GameServices.sprites().addSprite(originalPlayer);
        GameServices.camera().setFocusedSprite(originalPlayer);

        S3kSlotBonusStageRuntime runtime = new S3kSlotBonusStageRuntime();
        runtime.bootstrap();

        AbstractPlayableSprite slotPlayer = assertInstanceOf(
                AbstractPlayableSprite.class, GameServices.sprites().getSprite("tails"));
        assertTrue(slotPlayer instanceof S3kSlotBonusPlayer);
        assertEquals((byte) 0x02, slotPlayer.getTopSolidBit());
        assertEquals((byte) 0x03, slotPlayer.getLrbSolidBit());
    }

    @Test
    void runtimeUpdateDoesNotImmediatelyCaptureAndFreezeBootstrapPlayer() {
        RuntimeManager.createGameplay();
        SonicConfigurationService.getInstance().setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "tails");

        AbstractPlayableSprite originalPlayer = new Tails("tails", (short) 0x460, (short) 0x430);
        GameServices.sprites().addSprite(originalPlayer);
        GameServices.camera().setFocusedSprite(originalPlayer);

        S3kSlotBonusStageRuntime runtime = new S3kSlotBonusStageRuntime();
        runtime.bootstrap();

        AbstractPlayableSprite slotPlayer = assertInstanceOf(
                AbstractPlayableSprite.class, GameServices.sprites().getSprite("tails"));
        runtime.update(0);

        // Cage should NOT capture on first frame (suppressInitialCaptureOnce)
        assertFalse(slotPlayer.isControlLocked());
        assertFalse(slotPlayer.isObjectControlled());
        // Player starts airborne per ROM (bset #Status_InAir)
        assertTrue(slotPlayer.getAir());
    }

    @Test
    void runtimeUpdateKeepsCameraBoundToSlotRuntimeOrigin() {
        RuntimeManager.createGameplay();
        SonicConfigurationService.getInstance().setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "tails");

        AbstractPlayableSprite originalPlayer = new Tails("tails", (short) 0x460, (short) 0x430);
        GameServices.sprites().addSprite(originalPlayer);
        GameServices.camera().setFocusedSprite(originalPlayer);

        S3kSlotBonusStageRuntime runtime = new S3kSlotBonusStageRuntime();
        runtime.bootstrap();

        AbstractPlayableSprite slotPlayer = assertInstanceOf(
                AbstractPlayableSprite.class, GameServices.sprites().getSprite("tails"));
        runtime.update(0);

        assertEquals(slotPlayer.getCentreX() - 0xA0, GameServices.camera().getX());
        assertEquals(slotPlayer.getCentreY() - 0x70, GameServices.camera().getY());
    }

    @Test
    void runtimeUpdateBuildsVisibleSemanticCellsForSlotLayout() {
        RuntimeManager.createGameplay();
        SonicConfigurationService.getInstance().setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "tails");

        AbstractPlayableSprite originalPlayer = new Tails("tails", (short) 0x460, (short) 0x430);
        GameServices.sprites().addSprite(originalPlayer);
        GameServices.camera().setFocusedSprite(originalPlayer);

        S3kSlotBonusStageRuntime runtime = new S3kSlotBonusStageRuntime();
        runtime.bootstrap();
        runtime.update(0);

        assertNotNull(runtime.activeVisibleCellsForTest());
        assertFalse(runtime.activeVisibleCellsForTest().isEmpty());
        assertTrue(runtime.activeVisibleCellsForTest().size() >= 8);
        assertTrue(runtime.activeVisibleCellsForTest().stream().allMatch(cell -> cell.cellId() > 0));
        assertTrue(runtime.activeVisibleCellsForTest().stream().noneMatch(cell -> cell.cellId() == 0x09));
        int cameraX = GameServices.camera().getX();
        int cameraY = GameServices.camera().getY();
        assertTrue(runtime.activeVisibleCellsForTest().stream().allMatch(cell -> cell.worldX() >= cameraX - 0x10));
        assertTrue(runtime.activeVisibleCellsForTest().stream().allMatch(cell -> cell.worldX() < cameraX + 0x150));
        assertTrue(runtime.activeVisibleCellsForTest().stream().allMatch(cell -> cell.worldY() >= cameraY - 0x10));
        assertTrue(runtime.activeVisibleCellsForTest().stream().allMatch(cell -> cell.worldY() < cameraY + 0xF0));
    }

    @Test
    void runtimeUsesSharedMachineAnchorForCageAndDisplay() {
        RuntimeManager.createGameplay();
        SonicConfigurationService.getInstance().setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "tails");

        AbstractPlayableSprite originalPlayer = new Tails("tails", (short) 0x460, (short) 0x430);
        GameServices.sprites().addSprite(originalPlayer);
        GameServices.camera().setFocusedSprite(originalPlayer);

        S3kSlotBonusStageRuntime runtime = new S3kSlotBonusStageRuntime();
        runtime.bootstrap();
        runtime.update(0);

        S3kSlotMachineDisplayState displayState = runtime.slotMachineDisplayStateForTest();
        assertNotNull(displayState);
        assertTrue(displayState.worldX() < runtime.stageStateForTest().eventsBgX());
        assertTrue(displayState.worldY() < runtime.stageStateForTest().eventsBgY());
        assertFalse(displayState.worldX() == runtime.stageStateForTest().eventsBgX()
                && displayState.worldY() == runtime.stageStateForTest().eventsBgY());
        assertEquals(3, displayState.faces().length);
        assertEquals(3, displayState.nextFaces().length);
        assertEquals(3, displayState.offsets().length);
    }

    @Test
    void machineDisplayAnchorDoesNotRotateWithStageAngle() {
        RuntimeManager.createGameplay();
        SonicConfigurationService.getInstance().setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "tails");

        AbstractPlayableSprite originalPlayer = new Tails("tails", (short) 0x460, (short) 0x430);
        GameServices.sprites().addSprite(originalPlayer);
        GameServices.camera().setFocusedSprite(originalPlayer);

        S3kSlotBonusStageRuntime runtime = new S3kSlotBonusStageRuntime();
        runtime.bootstrap();
        runtime.update(0);

        S3kSlotMachineDisplayState baseline = runtime.slotMachineDisplayStateForTest();
        runtime.stageStateForTest().setStatTable(0x4000);
        S3kSlotMachineDisplayState rotated = runtime.slotMachineDisplayStateForTest();

        assertEquals(S3kSlotRomData.SLOT_BONUS_CAGE_CENTER_X + S3kSlotRomData.SLOT_MACHINE_PANEL_CENTER_OFFSET_X,
                baseline.worldX());
        assertEquals(S3kSlotRomData.SLOT_BONUS_CAGE_CENTER_Y + S3kSlotRomData.SLOT_MACHINE_PANEL_CENTER_OFFSET_Y,
                baseline.worldY());
        assertEquals(baseline.worldX(), rotated.worldX());
        assertEquals(baseline.worldY(), rotated.worldY());
    }

    @Test
    void goalExitReportsCompletedProviderFadeAfterRomExitFadeCompletes() {
        RuntimeManager.createGameplay();
        SonicConfigurationService.getInstance().setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "tails");

        AbstractPlayableSprite originalPlayer = new Tails("tails", (short) 0x460, (short) 0x430);
        GameServices.sprites().addSprite(originalPlayer);
        GameServices.camera().setFocusedSprite(originalPlayer);

        S3kSlotBonusStageRuntime runtime = new S3kSlotBonusStageRuntime();
        runtime.bootstrap();
        runtime.startGoalExitForTest();

        assertFalse(runtime.hasCompletedExitFadeToBlack());

        for (int frame = 0; frame < 155; frame++) {
            runtime.update(frame);
        }

        assertTrue(runtime.hasCompletedExitFadeToBlack());
        assertTrue(runtime.isExitTriggered());
    }

    @Test
    void lateRuntimeRenderPassDoesNotDrawMachineFacePanel() throws Exception {
        RuntimeManager.createGameplay();
        SonicConfigurationService.getInstance().setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "tails");

        AbstractPlayableSprite originalPlayer = new Tails("tails", (short) 0x460, (short) 0x430);
        GameServices.sprites().addSprite(originalPlayer);
        GameServices.camera().setFocusedSprite(originalPlayer);

        RecordingRenderer renderer = new RecordingRenderer();
        installRenderer(renderer, Sonic3kObjectArtKeys.SLOT_MACHINE_FACE);

        S3kSlotBonusStageRuntime runtime = new S3kSlotBonusStageRuntime();
        runtime.bootstrap();
        runtime.update(0);
        runtime.renderSlotLayout(GameServices.camera());

        assertEquals(0, renderer.drawCount);
    }

    @Test
    void shutdownRestoresOriginalPlayerAndCameraFocus() {
        RuntimeManager.createGameplay();
        SonicConfigurationService.getInstance().setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "tails");

        Tails originalPlayer = new Tails("tails", (short) 0x460, (short) 0x430);
        GameServices.sprites().addSprite(originalPlayer);
        GameServices.camera().setFocusedSprite(originalPlayer);

        S3kSlotBonusStageRuntime runtime = new S3kSlotBonusStageRuntime();
        runtime.bootstrap();

        assertTrue(GameServices.sprites().getSprite("tails") instanceof S3kSlotBonusPlayer);
        assertNotSame(originalPlayer, GameServices.sprites().getSprite("tails"));
        assertSame(GameServices.sprites().getSprite("tails"), GameServices.camera().getFocusedSprite());

        runtime.shutdown();

        assertSame(originalPlayer, GameServices.sprites().getSprite("tails"));
        assertSame(originalPlayer, GameServices.camera().getFocusedSprite());
        assertNull(runtime.activeSlotCageForTest());
        assertTrue(runtime.activeSlotRingRewardsForTest().isEmpty());
        assertTrue(runtime.activeSlotSpikeRewardsForTest().isEmpty());
    }

    @Test
    void shutdownRestoresOriginalPlayerOnBootstrapRuntimeAfterCurrentRuntimeRecreation() {
        GameRuntime bootstrapRuntime = RuntimeManager.createGameplay();
        SonicConfigurationService.getInstance().setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "tails");

        Tails originalPlayer = new Tails("tails", (short) 0x460, (short) 0x430);
        GameServices.sprites().addSprite(originalPlayer);
        GameServices.camera().setFocusedSprite(originalPlayer);

        S3kSlotBonusStageRuntime runtime = new S3kSlotBonusStageRuntime();
        runtime.bootstrap();

        assertTrue(bootstrapRuntime.getSpriteManager().getSprite("tails") instanceof S3kSlotBonusPlayer);
        assertNotSame(originalPlayer, bootstrapRuntime.getSpriteManager().getSprite("tails"));
        assertSame(bootstrapRuntime.getSpriteManager().getSprite("tails"), bootstrapRuntime.getCamera().getFocusedSprite());

        GameRuntime recreatedRuntime = RuntimeManager.createGameplay();

        runtime.shutdown();

        assertSame(originalPlayer, bootstrapRuntime.getSpriteManager().getSprite("tails"));
        assertSame(originalPlayer, bootstrapRuntime.getCamera().getFocusedSprite());
        assertFalse(runtime.isInitialized());
        assertTrue(recreatedRuntime.getSpriteManager().getSprite("tails") == null);

        bootstrapRuntime.destroy();
    }

    @Test
    void bootstrapInitializesAllSubsystems() {
        RuntimeManager.createGameplay();
        SonicConfigurationService.getInstance().setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "tails");

        AbstractPlayableSprite originalPlayer = new Tails("tails", (short) 0x460, (short) 0x430);
        GameServices.sprites().addSprite(originalPlayer);
        GameServices.camera().setFocusedSprite(originalPlayer);

        S3kSlotBonusStageRuntime runtime = new S3kSlotBonusStageRuntime();
        runtime.bootstrap();

        assertTrue(runtime.isInitialized());
        assertNotNull(runtime.activeSlotCageForTest());
        assertTrue(runtime.activeSlotRingRewardsForTest().isEmpty());
        assertTrue(runtime.activeSlotSpikeRewardsForTest().isEmpty());
        assertNotNull(runtime.activeLayoutForTest());
        assertNotNull(runtime.optionCycleSystemForTest());
        assertNotNull(runtime.activeLayoutAnimatorForTest());
        assertFalse(runtime.isExitTriggered());

        // Run a few frames to verify no crashes
        for (int i = 0; i < 50; i++) {
            runtime.update(i);
        }

        assertTrue(runtime.isInitialized());

        runtime.shutdown();
        assertFalse(runtime.isInitialized());
    }

    @Test
    void bootstrapWithoutGameplayRuntimeDoesNotMarkInitialized() {
        SonicConfigurationService.getInstance().setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "tails");

        S3kSlotBonusStageRuntime runtime = new S3kSlotBonusStageRuntime();
        runtime.bootstrap();

        assertFalse(runtime.isInitialized());
    }

    private void installRenderer(RecordingRenderer renderer, String artKey) throws Exception {
        levelManagerField = LevelManager.class.getDeclaredField("levelManager");
        levelManagerField.setAccessible(true);
        originalLevelManager = (LevelManager) levelManagerField.get(null);
        ObjectRenderManager renderManager = new ObjectRenderManager(new StubObjectArtProvider(renderer, artKey));
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
        }

        private static ObjectSpriteSheet dummySheet() {
            Pattern[] patterns = {new Pattern()};
            SpriteMappingPiece piece = new SpriteMappingPiece(0, 0, 1, 1, 0, false, false, 0, false);
            return new ObjectSpriteSheet(patterns, List.of(new SpriteMappingFrame(List.of(piece))), 0, 1);
        }
    }
}
