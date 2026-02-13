package uk.co.jamesj999.sonic.sprites.playable;

import uk.co.jamesj999.sonic.game.GameStateManager;
import uk.co.jamesj999.sonic.game.PhysicsProfile;

import java.util.logging.Logger;

public abstract class SuperStateController {
    private static final Logger LOGGER = Logger.getLogger(SuperStateController.class.getName());

    protected final AbstractPlayableSprite player;
    private SuperState state = SuperState.NORMAL;
    private int ringDrainCounter;

    protected SuperStateController(AbstractPlayableSprite player) {
        this.player = player;
        reset();
    }

    public void reset() {
        state = SuperState.NORMAL;
        ringDrainCounter = 0;
    }

    public void update() {
        switch (state) {
            case NORMAL -> checkTransformationTrigger();
            case TRANSFORMING -> updateTransformation();
            case SUPER -> updateSuper();
            case REVERTING -> updateRevert();
        }
    }

    public SuperState getState() { return state; }

    public boolean isSuper() {
        return state == SuperState.SUPER || state == SuperState.TRANSFORMING;
    }

    public void debugActivate() {
        if (state != SuperState.NORMAL) return;
        state = SuperState.SUPER;
        player.setSuperSonic(true);
        player.applyExternalPhysicsProfile(getSuperProfile());
        ringDrainCounter = getRingDrainInterval();
        onSuperActivated();
        LOGGER.info("Debug: Super Sonic activated");
    }

    public void debugDeactivate() {
        if (state == SuperState.NORMAL) return;
        revertToNormal();
        LOGGER.info("Debug: Super Sonic deactivated");
    }

    // --- Template methods for subclasses ---
    protected abstract int getRingDrainInterval();
    protected abstract int getMinRingsToTransform();
    protected abstract PhysicsProfile getSuperProfile();
    protected abstract PhysicsProfile getNormalProfile();
    protected abstract void onTransformationStarted();
    protected abstract boolean updateTransformationAnimation();
    protected abstract void onSuperActivated();
    protected abstract void updateSuperPalette();
    protected abstract void onRevertStarted();

    // --- Core logic ---
    private void checkTransformationTrigger() {
        if (!canTransform()) return;
        if (player.getAir() && player.isJumping() && player.getYSpeed() >= -0x100 && player.getYSpeed() <= 0) {
            startTransformation();
        }
    }

    private boolean canTransform() {
        if (player.isSuperSonic()) return false;
        if (!GameStateManager.getInstance().hasAllEmeralds()) return false;
        if (player.getRingCount() < getMinRingsToTransform()) return false;
        if (player.getDead() || player.isHurt() || player.isDebugMode()) return false;
        if (player.isObjectControlled()) return false;
        return true;
    }

    private void startTransformation() {
        state = SuperState.TRANSFORMING;
        player.setSuperSonic(true);
        onTransformationStarted();
    }

    private void updateTransformation() {
        if (updateTransformationAnimation()) {
            state = SuperState.SUPER;
            player.applyExternalPhysicsProfile(getSuperProfile());
            ringDrainCounter = getRingDrainInterval();
            onSuperActivated();
        }
    }

    private void updateSuper() {
        updateSuperPalette();
        ringDrainCounter--;
        if (ringDrainCounter <= 0) {
            ringDrainCounter = getRingDrainInterval();
            int rings = player.getRingCount();
            if (rings <= 0) {
                revertToNormal();
                return;
            }
            player.addRings(-1);
        }
    }

    private void updateRevert() {
        state = SuperState.NORMAL;
    }

    private void revertToNormal() {
        state = SuperState.REVERTING;
        player.setSuperSonic(false);
        player.applyExternalPhysicsProfile(getNormalProfile());
        onRevertStarted();
        state = SuperState.NORMAL;
    }
}
