package com.openggf.game.sonic3k.bonusstage.slots;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.GameRuntime;
import com.openggf.game.RuntimeManager;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
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
    private final S3kSlotLayoutAnimator layoutAnimator = new S3kSlotLayoutAnimator();
    private final S3kSlotReelStateMachine reelStateMachine = new S3kSlotReelStateMachine();
    private final S3kSlotTileInteraction.State tileInteractionState = new S3kSlotTileInteraction.State();
    private S3kSlotExitSequence exitSequence;
    private boolean exitTriggered;
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
        reelStateMachine.reset();
        tileInteractionState.reset();
        exitSequence = null;
        exitTriggered = false;
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

        // Exit sequence takes priority
        if (exitSequence != null) {
            exitSequence.tick();
            if (exitSequence.isComplete()) {
                exitTriggered = true;
            }
            // During exit, still update visuals
            updateVisuals();
            return;
        }

        // Per-frame rotation integration
        slotStageController.tick();

        // Reel state machine
        reelStateMachine.tick(frameCounter);

        // Tile interaction timers
        tileInteractionState.tickTimers();

        // Layout tile animations
        if (layout != null) {
            layoutAnimator.tick(layout);
        }

        // Cage object
        if (slotCage != null && slotPlayer != null) {
            slotCage.update(frameCounter, slotPlayer);
        }

        // Reward objects
        updateRewards(frameCounter);

        // Ring pickup from grid
        if (layout != null && slotPlayer != null) {
            checkRingPickup();
        }

        // Grid collision for player movement
        if (layout != null && slotPlayer != null) {
            checkGridCollision();
        }

        // Visuals
        updateVisuals();
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
        exitSequence = null;
        exitTriggered = false;
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

    public boolean isExitTriggered() {
        return exitTriggered;
    }

    public S3kSlotReelStateMachine activeReelStateMachineForTest() {
        return reelStateMachine;
    }

    public S3kSlotLayoutAnimator activeLayoutAnimatorForTest() {
        return layoutAnimator;
    }

    public S3kSlotExitSequence activeExitSequenceForTest() {
        return exitSequence;
    }

    public void render(com.openggf.camera.Camera camera) {
        LevelManager levelManager = GameServices.level();
        ObjectRenderManager renderManager = levelManager != null ? levelManager.getObjectRenderManager() : null;
        if (camera == null || renderManager == null || visibleCells.isEmpty()) {
            return;
        }
        slotLayoutRenderer.renderVisibleCells(visibleCells, camera, renderManager);
    }

    private void checkRingPickup() {
        S3kSlotGridCollision.RingCheck ring = S3kSlotGridCollision.checkRingPickup(
                layout, slotPlayer.getX(), slotPlayer.getY());
        if (ring.foundRing()) {
            layout[ring.layoutIndex()] = 0; // consume ring tile
            layoutAnimator.queueRingSparkle(layout, ring.layoutIndex());
            slotPlayer.addRings(1);
            if (GameServices.audio() != null) {
                GameServices.audio().playSfx(Sonic3kSfx.RING_RIGHT.id);
            }
        }
    }

    private void checkGridCollision() {
        S3kSlotGridCollision.Result collision = S3kSlotGridCollision.check(
                layout, slotPlayer.getX(), slotPlayer.getY());

        if (!collision.solid()) return;

        // If special tile (1-6), process interaction
        if (collision.special()) {
            int tileRow = collision.layoutIndex() / S3kSlotGridCollision.LAYOUT_STRIDE;
            int tileCol = collision.layoutIndex() % S3kSlotGridCollision.LAYOUT_STRIDE;
            short tileCenterX = (short) (tileCol * S3kSlotGridCollision.CELL_SIZE
                    - S3kSlotGridCollision.COLLISION_X_OFFSET + 0x0C);
            short tileCenterY = (short) (tileRow * S3kSlotGridCollision.CELL_SIZE
                    - S3kSlotGridCollision.COLLISION_Y_OFFSET + 0x0C);

            S3kSlotTileInteraction.Response response = S3kSlotTileInteraction.process(
                    collision.tileId(), slotPlayer.getX(), slotPlayer.getY(),
                    tileCenterX, tileCenterY,
                    slotStageController, tileInteractionState);

            handleTileResponse(response, collision.layoutIndex());
        }
    }

    private void handleTileResponse(S3kSlotTileInteraction.Response response, int layoutIndex) {
        switch (response.effect()) {
            case BUMPER_LAUNCH -> {
                slotPlayer.setXSpeed(response.launchXVel());
                slotPlayer.setYSpeed(response.launchYVel());
                slotPlayer.setAir(true);
                if (GameServices.audio() != null) GameServices.audio().playSfx(Sonic3kSfx.BUMPER.id);
                layoutAnimator.queueBumperBounce(layout, layoutIndex);
            }
            case GOAL_EXIT -> {
                if (GameServices.audio() != null) GameServices.audio().playSfx(Sonic3kSfx.GOAL.id);
                exitSequence = new S3kSlotExitSequence(slotStageController);
            }
            case SPIKE_REVERSAL -> {
                if (GameServices.audio() != null) GameServices.audio().playSfx(Sonic3kSfx.LAUNCH_GO.id);
                layoutAnimator.queueSpikeAnimation(layout, layoutIndex);
            }
            case SLOT_REEL_INCREMENT -> {
                if (GameServices.audio() != null) GameServices.audio().playSfx(Sonic3kSfx.FLIPPER.id);
            }
            default -> {}
        }
    }

    private void updateVisuals() {
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

    private void updateRewards(int frameCounter) {
        if (slotRingReward != null && !slotRingReward.isActive()) {
            int[] ringPos = slotStageController.consumePendingRingReward();
            if (ringPos != null) {
                if (ringPos.length == 4) {
                    slotRingReward.activate(ringPos[0], ringPos[1], ringPos[2], ringPos[3]);
                } else {
                    slotRingReward.activate();
                }
            }
        }
        if (slotRingReward != null && slotPlayer != null) {
            slotRingReward.update(frameCounter, slotPlayer);
        }
        if (slotSpikeReward != null && !slotSpikeReward.isActive()) {
            int[] spikePos = slotStageController.consumePendingSpikeReward();
            if (spikePos != null) {
                if (spikePos.length == 4) {
                    slotSpikeReward.activate(spikePos[0], spikePos[1], spikePos[2], spikePos[3]);
                } else {
                    slotSpikeReward.activate();
                }
            }
        }
        if (slotSpikeReward != null && slotPlayer != null) {
            slotSpikeReward.update(frameCounter, slotPlayer);
        }
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
