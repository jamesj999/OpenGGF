package com.openggf.game.sonic3k.bonusstage.slots;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.GameRuntime;
import com.openggf.game.RuntimeManager;
import com.openggf.game.sonic3k.objects.S3kSlotBonusCageObjectInstance;
import com.openggf.game.sonic3k.objects.S3kSlotRingRewardObjectInstance;
import com.openggf.game.sonic3k.objects.S3kSlotSpikeRewardObjectInstance;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.DefaultObjectServices;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.ArrayList;
import java.util.List;

public final class S3kSlotBonusStageRuntime {
    private static final int CAGE_BOOTSTRAP_OFFSET_Y = -0x80;

    private boolean initialized;
    private GameRuntime bootstrapRuntime;
    private AbstractPlayableSprite originalPlayer;
    private final S3kSlotStageController slotStageController = new S3kSlotStageController();
    private final S3kSlotLayoutRenderer slotLayoutRenderer = new S3kSlotLayoutRenderer();
    private AbstractPlayableSprite slotPlayer;
    private S3kSlotBonusCageObjectInstance slotCage;
    private S3kSlotRingRewardObjectInstance slotRingReward;
    private S3kSlotSpikeRewardObjectInstance slotSpikeReward;
    private byte[] layout;
    private short[] pointGrid;
    private List<S3kSlotLayoutRenderer.VisibleCell> visibleCells = List.of();
    private int lastFrameCounter = -1;
    private final List<SuppressedSidekick> suppressedSidekicks = new ArrayList<>();

    public void bootstrap() {
        initialized = false;
        originalPlayer = null;
        slotPlayer = null;
        slotCage = null;
        slotRingReward = null;
        slotSpikeReward = null;
        layout = null;
        pointGrid = null;
        visibleCells = List.of();
        lastFrameCounter = -1;
        suppressedSidekicks.clear();
        slotStageController.bootstrap();
        layout = S3kSlotRomData.SLOT_BONUS_LAYOUT.clone();
        bootstrapRuntime = RuntimeManager.getCurrent();
        if (bootstrapRuntime == null) {
            return;
        }

        suppressCpuSidekicks();

        String mainCode = SonicConfigurationService.getInstance().getString(SonicConfiguration.MAIN_CHARACTER_CODE);
        if (bootstrapRuntime.getSpriteManager().getSprite(mainCode) instanceof AbstractPlayableSprite mainPlayer) {
            originalPlayer = mainPlayer;
            short slotStartX = S3kSlotRomData.SLOT_BONUS_START_X;
            short slotStartY = S3kSlotRomData.SLOT_BONUS_START_Y;
            short centerX = S3kSlotRomData.SLOT_BONUS_START_X;
            short centerY = 0x430;
            slotPlayer = S3kSlotBonusPlayer.create(mainCode, slotStartX, slotStartY, slotStageController);
            copyLivePlayerState(mainPlayer, slotPlayer);
            slotPlayer.setX(slotStartX);
            slotPlayer.setY(slotStartY);
            slotPlayer.setAir(false);
            slotPlayer.setGSpeed((short) 0);
            slotPlayer.setXSpeed((short) 0);
            slotPlayer.setYSpeed((short) 0);
            bootstrapRuntime.getSpriteManager().addSprite(slotPlayer);
            bootstrapRuntime.getCamera().setFocusedSprite(slotPlayer);
            slotCage = new S3kSlotBonusCageObjectInstance(
                    new ObjectSpawn(centerX, centerY + CAGE_BOOTSTRAP_OFFSET_Y, 0, 0, 0, false, 0),
                    slotStageController);
            slotCage.setServices(new DefaultObjectServices(bootstrapRuntime));
            slotRingReward = new S3kSlotRingRewardObjectInstance(
                    new ObjectSpawn(centerX, centerY, 0, 0, 0, false, 0),
                    slotStageController);
            slotRingReward.setServices(new DefaultObjectServices(bootstrapRuntime));
            slotSpikeReward = new S3kSlotSpikeRewardObjectInstance(
                    new ObjectSpawn(centerX, centerY, 0, 0, 0, false, 0),
                    slotStageController);
            slotSpikeReward.setServices(new DefaultObjectServices(bootstrapRuntime));
            initialized = true;
        }
    }

    public void update(int frameCounter) {
        lastFrameCounter = frameCounter;
        if (slotCage != null && slotPlayer != null) {
            slotCage.update(frameCounter, slotPlayer);
        }
        if (slotRingReward != null && !slotRingReward.isActive() && slotStageController.consumePendingRingReward()) {
            slotRingReward.activate();
        }
        if (slotRingReward != null && slotPlayer != null) {
            slotRingReward.update(frameCounter, slotPlayer);
        }
        if (slotSpikeReward != null && !slotSpikeReward.isActive() && slotStageController.consumePendingSpikeReward()) {
            slotSpikeReward.activate();
        }
        if (slotSpikeReward != null && slotPlayer != null) {
            slotSpikeReward.update(frameCounter, slotPlayer);
        }
        if (bootstrapRuntime != null) {
            int stageCameraX = bootstrapRuntime.getCamera().getX() - S3kSlotRomData.SLOT_BONUS_START_X;
            int stageCameraY = bootstrapRuntime.getCamera().getY() - S3kSlotRomData.SLOT_BONUS_START_Y;
            pointGrid = slotLayoutRenderer.buildPointGrid(slotStageController.angle(),
                    stageCameraX, stageCameraY);
            visibleCells = slotLayoutRenderer.buildVisibleCells(
                    layout,
                    slotStageController.angle(),
                    stageCameraX,
                    stageCameraY);
        }
    }

    public void queueRingReward() {
        slotStageController.queueRingReward();
    }

    public void queueSpikeReward() {
        slotStageController.queueSpikeReward();
    }

    public void shutdown() {
        if (slotPlayer != null && bootstrapRuntime != null) {
            bootstrapRuntime.getSpriteManager().removeSprite(slotPlayer.getCode());
            if (originalPlayer != null) {
                bootstrapRuntime.getSpriteManager().addSprite(originalPlayer);
                bootstrapRuntime.getCamera().setFocusedSprite(originalPlayer);
            }
        }
        restoreSuppressedSidekicks();
        slotPlayer = null;
        slotCage = null;
        slotRingReward = null;
        slotSpikeReward = null;
        layout = null;
        pointGrid = null;
        visibleCells = List.of();
        lastFrameCounter = -1;
        originalPlayer = null;
        bootstrapRuntime = null;
        initialized = false;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public S3kSlotBonusCageObjectInstance activeSlotCageForTest() {
        return slotCage;
    }

    public S3kSlotRingRewardObjectInstance activeSlotRingRewardForTest() {
        return slotRingReward;
    }

    public S3kSlotSpikeRewardObjectInstance activeSlotSpikeRewardForTest() {
        return slotSpikeReward;
    }

    public short[] activePointGridForTest() {
        return pointGrid;
    }

    public byte[] activeLayoutForTest() {
        return layout;
    }

    public List<S3kSlotLayoutRenderer.VisibleCell> activeVisibleCellsForTest() {
        return visibleCells;
    }

    public int lastFrameCounterForTest() {
        return lastFrameCounter;
    }

    public void render(com.openggf.camera.Camera camera) {
        LevelManager levelManager = GameServices.level();
        ObjectRenderManager renderManager = levelManager != null ? levelManager.getObjectRenderManager() : null;
        if (camera == null || renderManager == null || visibleCells.isEmpty()) {
            return;
        }
        slotLayoutRenderer.renderVisibleCells(visibleCells, camera, renderManager);
    }

    private void copyLivePlayerState(AbstractPlayableSprite source, AbstractPlayableSprite target) {
        target.setTopSolidBit(source.getTopSolidBit());
        target.setLrbSolidBit(source.getLrbSolidBit());
        target.setSpriteRenderer(source.getSpriteRenderer());
        target.setMappingFrame(source.getMappingFrame());
        target.setAnimationFrameCount(source.getAnimationFrameCount());
        target.setAnimationProfile(source.getAnimationProfile());
        target.setAnimationSet(source.getAnimationSet());
        target.setAnimationId(source.getAnimationId());
        target.setAnimationFrameIndex(source.getAnimationFrameIndex());
        target.setAnimationTick(source.getAnimationTick());
        target.setSpindashDustController(source.getSpindashDustController());
        target.setTailsTailsController(source.getTailsTailsController());
        target.setSuperStateController(source.getSuperStateController());
        target.setPowerUpSpawner(source.getPowerUpSpawner());
        target.setDirection(source.getDirection());
        target.setHighPriority(source.isHighPriority());
        target.setAir(source.getAir());
        target.setRolling(source.getRolling());
        target.setControlLocked(false);
        target.setObjectControlled(false);
        target.setOnObject(source.isOnObject());
    }

    private void suppressCpuSidekicks() {
        if (bootstrapRuntime == null) {
            return;
        }
        SpriteManager spriteManager = bootstrapRuntime.getSpriteManager();
        List<AbstractPlayableSprite> liveSidekicks = List.copyOf(spriteManager.getSidekicks());
        for (AbstractPlayableSprite sidekick : liveSidekicks) {
            String characterName = spriteManager.getSidekickCharacterName(sidekick);
            suppressedSidekicks.add(new SuppressedSidekick(sidekick, characterName));
            spriteManager.removeSprite(sidekick.getCode());
        }
    }

    private void restoreSuppressedSidekicks() {
        if (bootstrapRuntime == null || suppressedSidekicks.isEmpty()) {
            suppressedSidekicks.clear();
            return;
        }
        SpriteManager spriteManager = bootstrapRuntime.getSpriteManager();
        for (SuppressedSidekick entry : suppressedSidekicks) {
            if (entry.characterName() != null) {
                spriteManager.addSprite(entry.sidekick(), entry.characterName());
            } else {
                spriteManager.addSprite(entry.sidekick());
            }
        }
        suppressedSidekicks.clear();
    }

    private record SuppressedSidekick(AbstractPlayableSprite sidekick, String characterName) {
    }
}
