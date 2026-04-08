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
import com.openggf.level.rings.RingManager;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class S3kSlotBonusStageRuntime {
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
    private boolean continueAwarded;
    private final List<S3kSlotRingRewardObjectInstance> slotRingRewards = new ArrayList<>();
    private final List<S3kSlotSpikeRewardObjectInstance> slotSpikeRewards = new ArrayList<>();
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
        continueAwarded = false;
        slotRingRewards.clear();
        slotSpikeRewards.clear();
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
        slotStageController.setActiveLayout(layout);
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
            // ROM loc_4B9CE: starts airborne and rolling
            slotPlayer.setAir(true);       // bset #Status_InAir,status(a0)
            slotPlayer.setRolling(true);   // bset #Status_Roll,status(a0)
            slotPlayer.setGSpeed((short) 0);
            slotPlayer.setXSpeed((short) 0);
            slotPlayer.setYSpeed((short) 0);
            bootstrapRuntime.getSpriteManager().addSprite(slotPlayer);
            bootstrapRuntime.getCamera().setFocusedSprite(slotPlayer);
            slotCage = new S3kSlotBonusCageObjectInstance(
                    new ObjectSpawn(centerX, centerY, 0, 0, 0, false, 0),
                    slotStageController);
            slotCage.setServices(new DefaultObjectServices(bootstrapRuntime));
            slotCage.suppressInitialCaptureOnce();
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

        // ROM line 98745: move.b #0,$30(a0) — clear collision tile at start of frame
        slotStageController.clearLastCollision();

        // Per-frame rotation integration
        if (slotPlayer != null && slotPlayer.isObjectControlled()) {
            // ROM loc_4BA80: accelerated rotation during cage capture
            slotStageController.tickObjectControlled();
        } else {
            slotStageController.tick();
        }

        // Reel state machine
        if (!slotStageController.isReelsFrozen()) {
            reelStateMachine.tick(frameCounter);
            if (reelStateMachine.isResolved()) {
                slotStageController.latchResolvedPrize(
                        reelStateMachine.lastPrizeResult(),
                        reelStateMachine.completedCycles());
            }
        }

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

        // Tile interaction dispatch — collision was already handled inline by player physics.
        // Read stored tile ID from controller (set during physics collision check).
        if (slotPlayer != null && slotStageController.lastCollisionTileId() > 0) {
            dispatchTileInteraction();
        }

        // ROM sub_4BBF4: custom camera tracking centered on player
        updateCamera();

        // ROM sub_4B4C4: pre-render animation updates for goal/peppermint sprites
        slotLayoutRenderer.updateAnimations();

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
        continueAwarded = false;
        slotRingRewards.clear();
        slotSpikeRewards.clear();
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
        return slotRingRewards.isEmpty() ? null : slotRingRewards.get(0);
    }

    public S3kSlotSpikeRewardObjectInstance activeSlotSpikeRewardForTest() {
        return slotSpikeRewards.isEmpty() ? null : slotSpikeRewards.get(0);
    }

    public List<S3kSlotRingRewardObjectInstance> activeSlotRingRewardsForTest() {
        return List.copyOf(slotRingRewards);
    }

    public List<S3kSlotSpikeRewardObjectInstance> activeSlotSpikeRewardsForTest() {
        return List.copyOf(slotSpikeRewards);
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

    public S3kSlotStageController stageController() {
        return slotStageController;
    }

    public void render(com.openggf.camera.Camera camera) {
        LevelManager levelManager = GameServices.level();
        ObjectRenderManager renderManager = levelManager != null ? levelManager.getObjectRenderManager() : null;
        RingManager ringManager = levelManager != null ? levelManager.getRingManager() : null;
        if (camera == null || renderManager == null || visibleCells.isEmpty()) {
            return;
        }
        slotLayoutRenderer.renderVisibleCells(visibleCells, camera, renderManager);
        renderCage(renderManager);
        renderRewardRings(ringManager);
        renderRewardSpikes(renderManager);
    }

    private void renderCage(ObjectRenderManager renderManager) {
        if (slotCage == null) {
            return;
        }
        PatternSpriteRenderer renderer = renderManager.getRenderer(
                com.openggf.game.sonic3k.Sonic3kObjectArtKeys.SLOT_BONUS_CAGE);
        if (renderer == null) {
            return;
        }
        renderer.drawFrameIndex(slotCage.getMappingFrame(), slotCage.getCurrentX(), slotCage.getCurrentY(),
                false, false);
    }

    private void renderRewardRings(RingManager ringManager) {
        if (ringManager == null) {
            return;
        }
        int frameCounter = Math.max(0, lastFrameCounter);
        for (S3kSlotRingRewardObjectInstance reward : slotRingRewards) {
            if (!reward.isActive() || reward.isDestroyed()) {
                continue;
            }
            ringManager.drawRingAt(reward.getInterpolatedX(), reward.getInterpolatedY(), frameCounter);
        }
    }

    private void renderRewardSpikes(ObjectRenderManager renderManager) {
        PatternSpriteRenderer renderer = renderManager.getRenderer(
                com.openggf.game.sonic3k.Sonic3kObjectArtKeys.SLOT_SPIKE_REWARD);
        if (renderer == null) {
            return;
        }
        for (S3kSlotSpikeRewardObjectInstance reward : slotSpikeRewards) {
            if (!reward.isActive() || reward.isDestroyed()) {
                continue;
            }
            renderer.drawFrameIndex(0, reward.getInterpolatedX(), reward.getInterpolatedY(), false, false);
        }
    }

    private void checkRingPickup() {
        S3kSlotGridCollision.RingCheck ring = S3kSlotGridCollision.checkRingPickup(
                layout, slotPlayer.getX(), slotPlayer.getY());
        if (ring.foundRing()) {
            layout[ring.layoutIndex()] = 0; // consume ring tile
            layoutAnimator.queueRingSparkle(layout, ring.layoutIndex());
            slotPlayer.addRings(1);
            // Track on coordinator so rings persist after exit (ROM: GiveRing)
            if (GameServices.bonusStage() != null) {
                GameServices.bonusStage().addRings(1);
            }
            if (GameServices.audio() != null) {
                GameServices.audio().playSfx(Sonic3kSfx.RING_RIGHT.id);
            }
            // ROM lines 99168-99174: 50-ring continue bonus (once per stage)
            if (slotPlayer.getRingCount() >= 50 && !continueAwarded) {
                continueAwarded = true;
                if (GameServices.bonusStage() != null) {
                    GameServices.bonusStage().addLife();
                }
                if (GameServices.audio() != null) {
                    GameServices.audio().playSfx(Sonic3kSfx.CONTINUE.id);
                }
            }
        }
    }

    /**
     * Dispatch tile interaction based on collision detected during player physics.
     * ROM sub_4BE3A reads $30(a0) which was set by sub_4BDA2 during collision.
     */
    private void dispatchTileInteraction() {
        int tileId = slotStageController.lastCollisionTileId();
        int layoutIndex = slotStageController.lastCollisionIndex();
        if (tileId <= 0 || tileId > 6 || layoutIndex < 0) return;

        int tileRow = layoutIndex / S3kSlotGridCollision.LAYOUT_STRIDE;
        int tileCol = layoutIndex % S3kSlotGridCollision.LAYOUT_STRIDE;
        short tileCenterX = (short) (tileCol * S3kSlotGridCollision.CELL_SIZE
                - S3kSlotGridCollision.COLLISION_X_OFFSET + 0x0C);
        short tileCenterY = (short) (tileRow * S3kSlotGridCollision.CELL_SIZE
                - S3kSlotGridCollision.COLLISION_Y_OFFSET + 0x0C);

        S3kSlotTileInteraction.Response response = S3kSlotTileInteraction.process(
                tileId, slotPlayer.getX(), slotPlayer.getY(),
                tileCenterX, tileCenterY,
                slotStageController, tileInteractionState);

        handleTileResponse(response, layoutIndex);
    }

    private void handleTileResponse(S3kSlotTileInteraction.Response response, int layoutIndex) {
        switch (response.effect()) {
            case BUMPER_LAUNCH -> {
                slotPlayer.setXSpeed(response.launchXVel());
                slotPlayer.setYSpeed(response.launchYVel());
                slotPlayer.setAir(true);
                if (GameServices.audio() != null) GameServices.audio().playSfx(Sonic3kSfx.BUMPER.id);
                // ROM loc_4BEC8: animation spawns at layout address - 1
                layoutAnimator.queueBumperBounce(layout, Math.max(0, layoutIndex - 1));
            }
            case GOAL_EXIT -> {
                if (GameServices.audio() != null) GameServices.audio().playSfx(Sonic3kSfx.GOAL.id);
                exitSequence = new S3kSlotExitSequence(slotStageController);
            }
            case SPIKE_REVERSAL -> {
                if (GameServices.audio() != null) GameServices.audio().playSfx(Sonic3kSfx.LAUNCH_GO.id);
                // ROM loc_4BEC8: animation spawns at layout address - 1
                layoutAnimator.queueSpikeAnimation(layout, Math.max(0, layoutIndex - 1));
            }
            case SLOT_REEL_INCREMENT -> {
                if (GameServices.audio() != null) GameServices.audio().playSfx(Sonic3kSfx.FLIPPER.id);
            }
            default -> {}
        }
    }

    /**
     * ROM sub_4BBF4 (lines 98937-98954): Custom camera tracking.
     * Centers the player at screen offset (0xA0, 0x70). Only adjusts when
     * player moves beyond those offsets.
     */
    private void updateCamera() {
        if (slotPlayer == null || bootstrapRuntime == null) {
            return;
        }
        int playerX = slotPlayer.getX();
        int playerY = slotPlayer.getY();
        int camX = bootstrapRuntime.getCamera().getX();
        int camY = bootstrapRuntime.getCamera().getY();
        int targetX = playerX - 0xA0;
        if (targetX >= 0) {
            camX = targetX;
        }
        int targetY = playerY - 0x70;
        if (targetY >= 0) {
            camY = targetY;
        }
        bootstrapRuntime.getCamera().setX((short) camX);
        bootstrapRuntime.getCamera().setY((short) camY);
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
        if (bootstrapRuntime == null || slotPlayer == null) {
            return;
        }
        drainPendingRingRewards();
        drainPendingSpikeRewards();
        updateActiveRewards(slotRingRewards, frameCounter);
        updateActiveRewards(slotSpikeRewards, frameCounter);
    }

    private void drainPendingRingRewards() {
        int[] ringPos;
        while ((ringPos = slotStageController.consumePendingRingReward()) != null) {
            S3kSlotRingRewardObjectInstance reward = new S3kSlotRingRewardObjectInstance(
                    new ObjectSpawn(S3kSlotRomData.SLOT_BONUS_START_X, S3kSlotRomData.SLOT_BONUS_START_Y,
                            0, 0, 0, false, 0),
                    slotStageController);
            reward.setServices(new DefaultObjectServices(bootstrapRuntime));
            if (ringPos.length == 4) {
                reward.activate(ringPos[0], ringPos[1], ringPos[2], ringPos[3]);
            } else {
                reward.activate();
            }
            slotStageController.onRewardSpawned();
            slotRingRewards.add(reward);
        }
    }

    private void drainPendingSpikeRewards() {
        int[] spikePos;
        while ((spikePos = slotStageController.consumePendingSpikeReward()) != null) {
            S3kSlotSpikeRewardObjectInstance reward = new S3kSlotSpikeRewardObjectInstance(
                    new ObjectSpawn(S3kSlotRomData.SLOT_BONUS_START_X, S3kSlotRomData.SLOT_BONUS_START_Y,
                            0, 0, 0, false, 0),
                    slotStageController);
            reward.setServices(new DefaultObjectServices(bootstrapRuntime));
            if (spikePos.length == 4) {
                reward.activate(spikePos[0], spikePos[1], spikePos[2], spikePos[3]);
            } else {
                reward.activate();
            }
            slotStageController.onRewardSpawned();
            slotSpikeRewards.add(reward);
        }
    }

    private <T extends com.openggf.level.objects.AbstractObjectInstance> void updateActiveRewards(
            List<T> rewards, int frameCounter) {
        Iterator<T> iterator = rewards.iterator();
        while (iterator.hasNext()) {
            T reward = iterator.next();
            reward.update(frameCounter, slotPlayer);
            boolean inactive = reward instanceof S3kSlotRingRewardObjectInstance ring && !ring.isActive()
                    || reward instanceof S3kSlotSpikeRewardObjectInstance spike && !spike.isActive();
            if (reward.isDestroyed() || inactive) {
                slotStageController.onRewardExpired();
                iterator.remove();
            }
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
