package com.openggf.game.sonic3k.bonusstage.slots;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameRuntime;
import com.openggf.game.RuntimeManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;

public final class S3kSlotBonusStageRuntime {
    private boolean initialized;
    private GameRuntime bootstrapRuntime;
    private AbstractPlayableSprite originalPlayer;
    private final S3kSlotStageController slotStageController = new S3kSlotStageController();
    private AbstractPlayableSprite slotPlayer;

    public void bootstrap() {
        initialized = false;
        originalPlayer = null;
        slotPlayer = null;
        slotStageController.bootstrap();
        bootstrapRuntime = RuntimeManager.getCurrent();
        if (bootstrapRuntime == null) {
            return;
        }

        String mainCode = SonicConfigurationService.getInstance().getString(SonicConfiguration.MAIN_CHARACTER_CODE);
        if (bootstrapRuntime.getSpriteManager().getSprite(mainCode) instanceof AbstractPlayableSprite mainPlayer) {
            originalPlayer = mainPlayer;
            slotPlayer = S3kSlotBonusPlayer.create(mainCode, mainPlayer.getX(), mainPlayer.getY(), slotStageController);
            slotPlayer.setTopSolidBit(mainPlayer.getTopSolidBit());
            slotPlayer.setLrbSolidBit(mainPlayer.getLrbSolidBit());
            bootstrapRuntime.getSpriteManager().addSprite(slotPlayer);
            bootstrapRuntime.getCamera().setFocusedSprite(slotPlayer);
            initialized = true;
        }
    }

    public void update(int frameCounter) {
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
        originalPlayer = null;
        bootstrapRuntime = null;
        initialized = false;
    }

    public boolean isInitialized() {
        return initialized;
    }
}
