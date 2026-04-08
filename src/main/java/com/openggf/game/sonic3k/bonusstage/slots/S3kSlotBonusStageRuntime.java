package com.openggf.game.sonic3k.bonusstage.slots;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameRuntime;
import com.openggf.game.RuntimeManager;
import com.openggf.game.sonic3k.objects.S3kSlotBonusCageObjectInstance;
import com.openggf.game.sonic3k.objects.S3kSlotRingRewardObjectInstance;
import com.openggf.game.sonic3k.objects.S3kSlotSpikeRewardObjectInstance;
import com.openggf.level.objects.DefaultObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;

public final class S3kSlotBonusStageRuntime {
    private boolean initialized;
    private GameRuntime bootstrapRuntime;
    private AbstractPlayableSprite originalPlayer;
    private final S3kSlotStageController slotStageController = new S3kSlotStageController();
    private AbstractPlayableSprite slotPlayer;
    private S3kSlotBonusCageObjectInstance slotCage;
    private S3kSlotRingRewardObjectInstance slotRingReward;
    private S3kSlotSpikeRewardObjectInstance slotSpikeReward;

    public void bootstrap() {
        initialized = false;
        originalPlayer = null;
        slotPlayer = null;
        slotCage = null;
        slotRingReward = null;
        slotSpikeReward = null;
        slotStageController.bootstrap();
        bootstrapRuntime = RuntimeManager.getCurrent();
        if (bootstrapRuntime == null) {
            return;
        }

        String mainCode = SonicConfigurationService.getInstance().getString(SonicConfiguration.MAIN_CHARACTER_CODE);
        if (bootstrapRuntime.getSpriteManager().getSprite(mainCode) instanceof AbstractPlayableSprite mainPlayer) {
            originalPlayer = mainPlayer;
            short centerX = mainPlayer.getCentreX();
            short centerY = mainPlayer.getCentreY();
            slotPlayer = S3kSlotBonusPlayer.create(mainCode, centerX, centerY, slotStageController);
            slotPlayer.setTopSolidBit(mainPlayer.getTopSolidBit());
            slotPlayer.setLrbSolidBit(mainPlayer.getLrbSolidBit());
            bootstrapRuntime.getSpriteManager().addSprite(slotPlayer);
            bootstrapRuntime.getCamera().setFocusedSprite(slotPlayer);
            slotCage = new S3kSlotBonusCageObjectInstance(
                    new ObjectSpawn(centerX, centerY, 0, 0, 0, false, 0),
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
        if (slotCage != null && slotPlayer != null) {
            slotCage.update(frameCounter, slotPlayer);
        }
        if (slotRingReward != null && slotPlayer != null) {
            slotRingReward.update(frameCounter, slotPlayer);
        }
        if (slotSpikeReward != null && slotPlayer != null) {
            slotSpikeReward.update(frameCounter, slotPlayer);
        }
    }

    public void shutdown() {
        if (slotPlayer != null && bootstrapRuntime != null) {
            bootstrapRuntime.getSpriteManager().removeSprite(slotPlayer.getCode());
            if (originalPlayer != null) {
                bootstrapRuntime.getSpriteManager().addSprite(originalPlayer);
                bootstrapRuntime.getCamera().setFocusedSprite(originalPlayer);
            }
        }
        slotPlayer = null;
        slotCage = null;
        slotRingReward = null;
        slotSpikeReward = null;
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
}
