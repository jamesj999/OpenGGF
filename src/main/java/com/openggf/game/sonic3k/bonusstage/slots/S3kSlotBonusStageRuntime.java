package com.openggf.game.sonic3k.bonusstage.slots;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.RuntimeManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;

public final class S3kSlotBonusStageRuntime {
    private boolean initialized;
    private AbstractPlayableSprite originalPlayer;
    private final S3kSlotStageController slotStageController = new S3kSlotStageController();
    private S3kSlotBonusPlayer slotPlayer;

    public void bootstrap() {
        if (RuntimeManager.getCurrent() != null) {
            String mainCode = SonicConfigurationService.getInstance().getString(SonicConfiguration.MAIN_CHARACTER_CODE);
            if (GameServices.sprites().getSprite(mainCode) instanceof AbstractPlayableSprite mainPlayer) {
                originalPlayer = mainPlayer;
                slotPlayer = new S3kSlotBonusPlayer(mainCode, mainPlayer.getCentreX(), mainPlayer.getCentreY(), slotStageController);
                GameServices.sprites().addSprite(slotPlayer);
            }
        }
        initialized = true;
    }

    public void update(int frameCounter) {
    }

    public void shutdown() {
        if (slotPlayer != null && RuntimeManager.getCurrent() != null) {
            GameServices.sprites().removeSprite(slotPlayer.getCode());
            if (originalPlayer != null) {
                GameServices.sprites().addSprite(originalPlayer);
            }
        }
        slotPlayer = null;
        originalPlayer = null;
        initialized = false;
    }

    public boolean isInitialized() {
        return initialized;
    }
}
