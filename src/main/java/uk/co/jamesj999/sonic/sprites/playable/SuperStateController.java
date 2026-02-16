package uk.co.jamesj999.sonic.sprites.playable;

import uk.co.jamesj999.sonic.data.RomByteReader;
import uk.co.jamesj999.sonic.game.GameStateManager;
import uk.co.jamesj999.sonic.game.LevelState;
import uk.co.jamesj999.sonic.game.PhysicsProfile;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.sprites.animation.ScriptedVelocityAnimationProfile;
import uk.co.jamesj999.sonic.sprites.animation.SpriteAnimationProfile;

import java.util.logging.Logger;

public abstract class SuperStateController {
    private static final Logger LOGGER = Logger.getLogger(SuperStateController.class.getName());

    protected final AbstractPlayableSprite player;
    private SuperState state = SuperState.NORMAL;
    private int ringDrainCounter;
    private SpriteAnimationProfile normalAnimProfile;

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
            default -> {} // REVERTING not used (revert is instant in ROM)
        }
        // Post-revert effects (e.g. palette fade-out) run every frame regardless of state
        updatePostRevertEffects();
    }

    public SuperState getState() { return state; }

    public boolean isSuper() {
        return state == SuperState.SUPER || state == SuperState.TRANSFORMING;
    }

    public void debugActivate() {
        if (state != SuperState.NORMAL) return;
        player.addRings(50);
        state = SuperState.SUPER;
        player.setSuperSonic(true);
        player.applyExternalPhysicsProfile(getSuperProfile());
        swapToSuperAnimProfile();
        ringDrainCounter = getRingDrainInterval();
        onSuperActivated();
        LOGGER.info("Debug: Super Sonic activated");
    }

    public void debugDeactivate() {
        if (state == SuperState.NORMAL) return;
        revertToNormal();
        LOGGER.info("Debug: Super Sonic deactivated");
    }

    /**
     * Loads game-specific ROM data (palette cycling, etc.).
     * Called once during level initialization. Default is no-op.
     *
     * @param reader ROM byte reader for data access
     */
    public void loadRomData(RomByteReader reader) {
        // Default: no ROM data needed
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

    /**
     * Called every frame regardless of state. Override to run post-revert effects
     * (e.g. palette fade-out animation that continues after state returns to NORMAL).
     */
    protected void updatePostRevertEffects() {
        // Default: no-op
    }

    /**
     * Returns the run speed threshold to use in the Super animation profile.
     * Default is 0x800 (ROM: cmpi.w #$800,d2 in SAnim_Super). Subclasses can
     * override for Hyper or other characters if needed.
     */
    protected int getSuperRunSpeedThreshold() {
        return 0x800;
    }

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
            swapToSuperAnimProfile();
            ringDrainCounter = getRingDrainInterval();
            onSuperActivated();
        }
    }

    private void updateSuper() {
        // ROM: Sonic_Super checks Update_HUD_timer == 0 every frame.
        // When the signpost/egg prison clears the timer, Super Sonic reverts.
        // Do NOT use player.isObjectControlled() - many objects (CPZ pipes, grabbers)
        // set that flag temporarily, causing false detransformation.
        LevelState levelState = LevelManager.getInstance().getLevelGamestate();
        if (levelState != null && levelState.isTimerPaused()) {
            revertToNormal();
            return;
        }
        updateSuperPalette();
        ringDrainCounter--;
        if (ringDrainCounter <= 0) {
            ringDrainCounter = getRingDrainInterval();
            player.addRings(-1);
            if (player.getRingCount() <= 0) {
                revertToNormal();
                return;
            }
        }
    }

    private void revertToNormal() {
        player.setSuperSonic(false);
        player.applyExternalPhysicsProfile(getNormalProfile());
        restoreNormalAnimProfile();
        onRevertStarted();
        state = SuperState.NORMAL;
    }

    private void swapToSuperAnimProfile() {
        SpriteAnimationProfile current = player.getAnimationProfile();
        if (current instanceof ScriptedVelocityAnimationProfile velocityProfile) {
            normalAnimProfile = current;
            player.setAnimationProfile(velocityProfile.withRunSpeedThreshold(getSuperRunSpeedThreshold()));
        }
    }

    private void restoreNormalAnimProfile() {
        if (normalAnimProfile != null) {
            player.setAnimationProfile(normalAnimProfile);
            normalAnimProfile = null;
        }
    }
}
