package com.openggf.game.sonic3k.bonusstage.slots;

import com.openggf.audio.GameSound;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.game.GameRuntime;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.objects.S3kSlotBonusCageObjectInstance;
import com.openggf.game.sonic3k.objects.S3kSlotRingRewardObjectInstance;
import com.openggf.game.sonic3k.objects.S3kSlotSpikeRewardObjectInstance;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.DefaultObjectServices;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class S3kSlotBonusStageRuntime {
    private boolean initialized;
    private GameRuntime bootstrapRuntime;
    private AbstractPlayableSprite originalPlayer;
    private S3kSlotStageState slotStageState;
    private S3kSlotRenderBuffers slotRenderBuffers;
    private final S3kSlotOptionCycleSystem optionCycleSystem = new S3kSlotOptionCycleSystem();
    private S3kSlotPlayerRuntime slotPlayerRuntime;
    private S3kSlotCollisionSystem slotCollisionSystem;
    private S3kSlotStageController slotStageController;
    private final S3kSlotLayoutRenderer slotLayoutRenderer = new S3kSlotLayoutRenderer();
    private final S3kSlotLayoutAnimator layoutAnimator = new S3kSlotLayoutAnimator();
    private boolean exitTriggered;
    private AbstractPlayableSprite slotPlayer;
    private S3kSlotBonusCageObjectInstance slotCage;
    private boolean continueAwarded;
    private boolean exitFadeStarted;
    private final List<S3kSlotRingRewardObjectInstance> slotRingRewards = new ArrayList<>();
    private final List<S3kSlotSpikeRewardObjectInstance> slotSpikeRewards = new ArrayList<>();
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
        exitFadeStarted = false;
        slotRingRewards.clear();
        slotSpikeRewards.clear();
        pointGrid = null;
        visibleCells = List.of();
        lastFrameCounter = -1;
        suppressedSidekicks.clear();
        slotStageState = S3kSlotStageState.bootstrap();
        slotRenderBuffers = S3kSlotRenderBuffers.fromRomData();
        optionCycleSystem.bootstrap(slotStageState);
        slotCollisionSystem = new S3kSlotCollisionSystem(slotRenderBuffers, slotStageState);
        slotPlayerRuntime = new S3kSlotPlayerRuntime(slotStageState, slotCollisionSystem);
        exitTriggered = false;
        slotStageController = new S3kSlotStageController(slotStageState);
        slotStageController.bootstrap();
        slotStageController.setActiveLayout(slotRenderBuffers.layout());
        bootstrapRuntime = GameServices.runtimeOrNull();
        if (bootstrapRuntime == null) {
            return;
        }

        suppressCpuSidekicks();

        String mainCode = GameServices.configuration().getString(SonicConfiguration.MAIN_CHARACTER_CODE);
        if (bootstrapRuntime.getSpriteManager().getSprite(mainCode) instanceof AbstractPlayableSprite mainPlayer) {
            originalPlayer = mainPlayer;
            short slotStartX = S3kSlotRomData.SLOT_BONUS_PLAYER_START_X;
            short slotStartY = S3kSlotRomData.SLOT_BONUS_PLAYER_START_Y;
            short centerX = S3kSlotRomData.SLOT_BONUS_CAGE_CENTER_X;
            short centerY = S3kSlotRomData.SLOT_BONUS_CAGE_CENTER_Y;
            slotPlayer = S3kSlotBonusPlayer.create(mainCode, slotStartX, slotStartY, slotPlayerRuntime);
            copyLivePlayerState(mainPlayer, slotPlayer);
            slotPlayer.setCentreX(slotStartX);
            slotPlayer.setCentreY(slotStartY);
            slotPlayerRuntime.initialize(slotPlayer);
            slotPlayerRuntime.resetSlotOrigin(slotPlayer);
            bootstrapRuntime.getSpriteManager().addSprite(slotPlayer);
            bootstrapRuntime.getCamera().setFocusedSprite(slotPlayer);
            bootstrapRuntime.getCamera().setX((short) (slotPlayer.getCentreX() - 0xA0));
            bootstrapRuntime.getCamera().setY((short) (slotPlayer.getCentreY() - 0x70));
            slotCage = new S3kSlotBonusCageObjectInstance(
                    new ObjectSpawn(centerX, centerY, 0, 0, 0, false, 0),
                    slotStageController);
            slotCage.setServices(new DefaultObjectServices(bootstrapRuntime));
            slotCage.suppressObjectManagerUpdate();
            registerDynamicSlotObject(slotCage);
            slotCage.suppressInitialCaptureOnce();
            initialized = true;
        }
    }

    public void update(int frameCounter) {
        lastFrameCounter = frameCounter;

        if (slotPlayerRuntime != null && slotPlayerRuntime.isExiting()) {
            slotPlayerRuntime.tickExitFrame(slotPlayer);
            var fade = GameServices.fadeOrNull();
            if (slotPlayerRuntime.isExitFading() && !exitFadeStarted && fade != null) {
                fade.startFadeToBlack(null, 0, S3kSlotExitSequence.FADE_FRAMES);
                exitFadeStarted = true;
            }
            if (slotPlayerRuntime.isExitComplete()) {
                exitTriggered = true;
            }
            updateVisuals();
            return;
        }

        // ROM line 98745: move.b #0,$30(a0) — clear collision tile at start of frame

        if (slotCollisionSystem != null) {
            slotCollisionSystem.tickFrameState();
        }

        // Option cycle system
        if (!slotStageController.isReelsFrozen()) {
            optionCycleSystem.tick(slotStageState, frameCounter);
            if (optionCycleSystem.isResolved(slotStageState)) {
                slotStageController.latchResolvedPrize(
                        optionCycleSystem.lastPrizeResult(slotStageState),
                        optionCycleSystem.completedCycles(slotStageState));
            }
        }

        // Cage object
        if (slotCage != null && slotPlayer != null) {
            slotCage.tickSlotRuntime(frameCounter, slotPlayer,
                    currentPlayerOriginX(), currentPlayerOriginY());
            slotStageState.setEventsBg(slotCage.getCurrentX(), slotCage.getCurrentY());
        }

        // Reward objects
        updateRewards(frameCounter);

        // Ring pickup from grid
        if (slotRenderBuffers != null && slotPlayer != null && !slotPlayer.isDebugMode()) {
            checkRingPickup();
        }

        // Tile interaction dispatch — collision was already handled inline by player physics.
        // Read stored tile ID from controller (set during physics collision check).
        if (slotPlayer != null && slotStageState != null && !slotPlayer.isDebugMode()
                && slotStageState.lastCollisionTileId() > 0) {
            dispatchTileInteraction();
            int collisionTileId = slotStageState.lastCollisionTileId();
            if (collisionTileId < 1 || collisionTileId > 3) {
                slotStageState.clearSlotWallContact();
            }
        } else if (slotStageState != null) {
            slotStageState.clearSlotWallContact();
        }

        // ROM sub_4BBF4: custom camera tracking centered on player
        updateCamera();

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
        for (S3kSlotRingRewardObjectInstance reward : slotRingRewards) {
            unregisterDynamicSlotObject(reward);
        }
        for (S3kSlotSpikeRewardObjectInstance reward : slotSpikeRewards) {
            unregisterDynamicSlotObject(reward);
        }
        unregisterDynamicSlotObject(slotCage);
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
        exitFadeStarted = false;
        slotRingRewards.clear();
        slotSpikeRewards.clear();
        pointGrid = null;
        visibleCells = List.of();
        lastFrameCounter = -1;
        slotStageState = null;
        slotRenderBuffers = null;
        slotPlayerRuntime = null;
        slotCollisionSystem = null;
        slotStageController = null;
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

    public S3kSlotStageState stageStateForTest() {
        return slotStageState;
    }

    public SlotVisualState slotVisualState() {
        if (slotStageState == null) {
            return new SlotVisualState(S3kSlotRomData.SLOT_BONUS_CAGE_CENTER_X,
                    S3kSlotRomData.SLOT_BONUS_CAGE_CENTER_Y, 0x40, false);
        }
        return new SlotVisualState(slotStageState.eventsBgX(), slotStageState.eventsBgY(),
                slotStageState.scalarIndex1(), slotStageState.paletteCycleEnabled());
    }

    public int paletteCycleMode() {
        return slotStageState != null && slotStageState.paletteCycleEnabled() ? 1 : 0;
    }

    public void setPaletteCycleEnabledForTest(boolean enabled) {
        if (slotStageController != null) {
            slotStageController.setPaletteCycleEnabled(enabled);
        }
    }

    public S3kSlotRenderBuffers renderBuffersForTest() {
        return slotRenderBuffers;
    }

    public S3kSlotOptionCycleSystem optionCycleSystemForTest() {
        return optionCycleSystem;
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
        return slotRenderBuffers != null ? slotRenderBuffers.layout() : null;
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

    public boolean hasCompletedExitFadeToBlack() {
        return exitFadeStarted && slotPlayerRuntime != null && slotPlayerRuntime.isExitComplete();
    }

    public S3kSlotLayoutAnimator activeLayoutAnimatorForTest() {
        return layoutAnimator;
    }

    public S3kSlotExitSequence activeExitSequenceForTest() {
        return slotPlayerRuntime != null ? slotPlayerRuntime.activeExitSequence() : null;
    }

    public S3kSlotStageController stageController() {
        return slotStageController;
    }

    public S3kSlotMachineDisplayState slotMachineDisplayStateForTest() {
        return slotMachineDisplayState();
    }

    public S3kSlotMachineDisplayState slotMachineDisplayState() {
        S3kSlotLayoutRenderer.TransformedStagePoint anchor = currentMachineAnchor();
        return S3kSlotMachineDisplayState.fromState(slotStageState, anchor.worldX(), anchor.worldY());
    }

    public void startGoalExitForTest() {
        if (slotPlayerRuntime != null && slotPlayer != null) {
            slotPlayerRuntime.startGoalExit(slotPlayer);
        }
    }

    public void render(com.openggf.camera.Camera camera) {
        renderSlotLayout(camera);
    }

    public void renderSlotLayout(com.openggf.camera.Camera camera) {
        LevelManager levelManager = GameServices.level();
        if (camera == null || levelManager == null) {
            return;
        }
        // Only the slot layout pass is rendered here. Cage/reward objects stay on
        // the normal object pipeline so this hook matches the ROM's post-sprite pass.
        slotLayoutRenderer.renderVisibleCells(visibleCells, camera, levelManager.getObjectRenderManager());
    }

    public void renderSlotMachineFaceForeground() {
        // The visible machine panel is part of the slot-machine foreground tiles.
        // Only the reel window is overlaid in renderAfterForeground().
    }

    private S3kSlotLayoutRenderer.TransformedStagePoint currentMachineAnchor() {
        int panelX = S3kSlotRomData.SLOT_BONUS_CAGE_CENTER_X + S3kSlotRomData.SLOT_MACHINE_PANEL_CENTER_OFFSET_X;
        int panelY = S3kSlotRomData.SLOT_BONUS_CAGE_CENTER_Y + S3kSlotRomData.SLOT_MACHINE_PANEL_CENTER_OFFSET_Y;
        if (bootstrapRuntime != null) {
            int cameraX = bootstrapRuntime.getCamera().getX();
            int cameraY = bootstrapRuntime.getCamera().getY();
            return new S3kSlotLayoutRenderer.TransformedStagePoint(
                    panelX,
                    panelY,
                    S3kSlotMachineRenderer.computeDisplayScreenX(panelX, cameraX),
                    S3kSlotMachineRenderer.computeDisplayScreenY(panelY, cameraY));
        }
        return new S3kSlotLayoutRenderer.TransformedStagePoint(
                panelX,
                panelY,
                S3kSlotMachineRenderer.computeDisplayScreenX(panelX, 0),
                S3kSlotMachineRenderer.computeDisplayScreenY(panelY, 0));
    }

    private void checkRingPickup() {
        S3kSlotCollisionSystem.RingCheck ring = slotCollisionSystem.checkRingPickup(
                currentPlayerOriginX(), currentPlayerOriginY());
        if (ring.foundRing()) {
            slotCollisionSystem.consumeRing(ring);
            slotRenderBuffers.startRingAnimationAt(ring.layoutIndex());
            slotPlayer.addRings(1);
            // Track on coordinator so rings persist after exit (ROM: GiveRing)
            var bonusStage = GameServices.bonusStageOrNull();
            if (bonusStage != null) {
                bonusStage.addRings(1);
            }
            if (GameServices.audio() != null) {
                GameServices.audio().playSfx(GameSound.RING);
            }
            // ROM lines 99168-99174: 50-ring continue bonus (once per stage)
            if (slotPlayer.getRingCount() >= 50 && !continueAwarded) {
                continueAwarded = true;
                bonusStage = GameServices.bonusStageOrNull();
                if (bonusStage != null) {
                    bonusStage.addLife();
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
        int tileId = slotStageState.lastCollisionTileId();
        int layoutIndex = slotStageState.lastCollisionIndex();
        if (tileId <= 0 || tileId > 6 || layoutIndex < 0) return;

        int expandedIndex = slotRenderBuffers != null ? slotRenderBuffers.compactToExpandedIndex(layoutIndex) : -1;
        if (expandedIndex < 0) {
            return;
        }
        short tileAnchorX = S3kSlotCollisionSystem.tileResponseAnchorX(expandedIndex);
        short tileAnchorY = S3kSlotCollisionSystem.tileResponseAnchorY(expandedIndex);

        S3kSlotCollisionSystem.TileResponse response = slotCollisionSystem.resolveTileResponse(
                tileId, (short) currentPlayerOriginX(), (short) currentPlayerOriginY(), tileAnchorX, tileAnchorY);

        handleTileResponse(response, layoutIndex, tileId);
        slotStageController.setScalarIndex(slotStageState.scalarIndex1());
    }

    private void handleTileResponse(S3kSlotCollisionSystem.TileResponse response, int layoutIndex, int tileId) {
        switch (response.effect()) {
            case BUMPER_LAUNCH -> {
                slotPlayer.setXSpeed(response.launchXVel());
                slotPlayer.setYSpeed(response.launchYVel());
                slotPlayer.setAir(true);
                if (GameServices.audio() != null) GameServices.audio().playSfx(Sonic3kSfx.BUMPER.id);
                slotRenderBuffers.startBumperAnimationAt(layoutIndex);
            }
            case GOAL_EXIT -> {
                if (GameServices.audio() != null) GameServices.audio().playSfx(Sonic3kSfx.GOAL.id);
                slotPlayerRuntime.startGoalExit(slotPlayer);
                exitFadeStarted = false;
            }
            case SPIKE_REVERSAL -> {
                if (GameServices.audio() != null) GameServices.audio().playSfx(Sonic3kSfx.LAUNCH_GO.id);
                slotRenderBuffers.startSpikeAnimationAt(layoutIndex);
            }
            case SLOT_REEL_INCREMENT -> {
                if (slotStageState.shouldTriggerSlotWall(layoutIndex)) {
                    slotStageState.incrementSlotValue();
                    if (tileId >= 1 && tileId <= 3) {
                        slotRenderBuffers.startSlotWallAnimationAt(layoutIndex, tileId + 1);
                    }
                    if (GameServices.audio() != null) GameServices.audio().playSfx(Sonic3kSfx.FLIPPER.id);
                }
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
        int playerX = currentPlayerOriginX();
        int playerY = currentPlayerOriginY();
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

    private int currentPlayerOriginX() {
        if (slotPlayerRuntime != null) {
            return slotPlayerRuntime.slotOriginX() >> 16;
        }
        return slotPlayer != null ? slotPlayer.getCentreX() : 0;
    }

    private int currentPlayerOriginY() {
        if (slotPlayerRuntime != null) {
            return slotPlayerRuntime.slotOriginY() >> 16;
        }
        return slotPlayer != null ? slotPlayer.getCentreY() : 0;
    }

    private void updateVisuals() {
        if (bootstrapRuntime != null) {
            int stageCameraX = bootstrapRuntime.getCamera().getX();
            int stageCameraY = bootstrapRuntime.getCamera().getY();
            if (slotRenderBuffers != null) {
                if (pointGrid == null || pointGrid.length < 16 * 16 * 2) {
                    pointGrid = new short[16 * 16 * 2];
                }
                slotLayoutRenderer.updateAnimations(slotStageState.angle());
                slotLayoutRenderer.tickTransientAnimations(slotRenderBuffers);
                slotLayoutRenderer.buildPointGridInto(pointGrid, slotStageState.angle(),
                        stageCameraX, stageCameraY);
                slotRenderBuffers.stageViewport(stageCameraX, stageCameraY);
                slotRenderBuffers.stagePointGrid(pointGrid);
                visibleCells = slotLayoutRenderer.buildVisibleCells(slotRenderBuffers);
            } else {
                visibleCells = List.of();
            }
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
                    new ObjectSpawn(S3kSlotRomData.SLOT_BONUS_CAGE_CENTER_X, S3kSlotRomData.SLOT_BONUS_CAGE_CENTER_Y,
                            0, 0, 0, false, 0),
                    slotStageController);
            reward.setServices(new DefaultObjectServices(bootstrapRuntime));
            reward.suppressObjectManagerUpdate();
            if (ringPos.length == 4) {
                reward.activate(ringPos[0], ringPos[1], ringPos[2], ringPos[3]);
            } else {
                reward.activate();
            }
            registerDynamicSlotObject(reward);
            slotStageController.onRewardSpawned();
            slotRingRewards.add(reward);
        }
    }

    private void drainPendingSpikeRewards() {
        int[] spikePos;
        while ((spikePos = slotStageController.consumePendingSpikeReward()) != null) {
            S3kSlotSpikeRewardObjectInstance reward = new S3kSlotSpikeRewardObjectInstance(
                    new ObjectSpawn(S3kSlotRomData.SLOT_BONUS_CAGE_CENTER_X, S3kSlotRomData.SLOT_BONUS_CAGE_CENTER_Y,
                            0, 0, 0, false, 0),
                    slotStageController);
            reward.setServices(new DefaultObjectServices(bootstrapRuntime));
            reward.suppressObjectManagerUpdate();
            if (spikePos.length == 4) {
                reward.activate(spikePos[0], spikePos[1], spikePos[2], spikePos[3]);
            } else {
                reward.activate();
            }
            registerDynamicSlotObject(reward);
            slotStageController.onRewardSpawned();
            slotSpikeRewards.add(reward);
        }
    }

    private <T extends com.openggf.level.objects.AbstractObjectInstance> void updateActiveRewards(
            List<T> rewards, int frameCounter) {
        Iterator<T> iterator = rewards.iterator();
        while (iterator.hasNext()) {
            T reward = iterator.next();
            if (reward instanceof S3kSlotRingRewardObjectInstance ringReward) {
                ringReward.tickSlotRuntime(frameCounter, slotPlayer);
            } else if (reward instanceof S3kSlotSpikeRewardObjectInstance spikeReward) {
                spikeReward.tickSlotRuntime(frameCounter, slotPlayer);
            }
            boolean inactive = reward instanceof S3kSlotRingRewardObjectInstance ring && !ring.isActive()
                    || reward instanceof S3kSlotSpikeRewardObjectInstance spike && !spike.isActive();
            if (reward.isDestroyed() || inactive) {
                slotStageController.onRewardExpired();
                unregisterDynamicSlotObject(reward);
                iterator.remove();
            }
        }
    }

    private void registerDynamicSlotObject(com.openggf.level.objects.ObjectInstance object) {
        if (bootstrapRuntime == null || object == null) {
            return;
        }
        ObjectManager objectManager = bootstrapRuntime.getObjectManager();
        if (objectManager != null) {
            objectManager.addDynamicObject(object);
        }
    }

    private void unregisterDynamicSlotObject(com.openggf.level.objects.ObjectInstance object) {
        if (bootstrapRuntime == null || object == null) {
            return;
        }
        ObjectManager objectManager = bootstrapRuntime.getObjectManager();
        if (objectManager != null) {
            objectManager.removeDynamicObject(object);
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
        // ROM Obj_Sonic_RotatingSlotBonus uses make_art_tile(...,0,0);
        // the slot-machine glass is supplied by high-priority FG tiles.
        target.setHighPriority(false);
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

    public record SlotVisualState(int eventsBgX, int eventsBgY, int scalarIndex1, boolean paletteCycleEnabled) {
    }
}
