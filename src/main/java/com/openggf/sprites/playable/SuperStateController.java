package com.openggf.sprites.playable;

import com.openggf.data.RomByteReader;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.EngineServices;
import com.openggf.game.GameStateManager;
import com.openggf.game.LevelState;
import com.openggf.game.PhysicsProfile;
import com.openggf.graphics.RenderContext;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import com.openggf.level.Palette;
import com.openggf.sprites.animation.ScriptedVelocityAnimationProfile;
import com.openggf.sprites.animation.SpriteAnimationProfile;

import java.util.logging.Logger;

public abstract class SuperStateController {
    private static final Logger LOGGER = Logger.getLogger(SuperStateController.class.getName());

    protected final AbstractPlayableSprite player;
    private SuperState state = SuperState.NORMAL;
    private int ringDrainCounter;
    private SpriteAnimationProfile normalAnimProfile;
    private boolean romDataPreLoaded;

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
        startTransformation();
        LOGGER.info("Debug: Super Sonic transformation started");
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

    public void setRomDataPreLoaded(boolean preLoaded) {
        this.romDataPreLoaded = preLoaded;
    }

    public boolean isRomDataPreLoaded() {
        return romDataPreLoaded;
    }

    // --- Palette target resolution for cross-game support ---

    protected record PaletteTarget(Palette palette, int gpuLine) {}

    /**
     * Resolves the correct palette and GPU line for Super Sonic palette cycling.
     * In cross-game mode, uses the donor render context's palette so cycling
     * affects the palette the sprite actually renders from (GPU line 4+).
     * In normal mode, uses the level's palette at the given logical line.
     *
     * @param logicalLine logical palette line (e.g., 0 for Sonic's palette)
     * @return the palette and GPU line to write to, or null if unavailable
     */
    protected PaletteTarget resolvePaletteTarget(int logicalLine) {
        if (CrossGameFeatureProvider.isActive()) {
            EngineServices engineServices = com.openggf.game.RuntimeManager.getEngineServices();
            CrossGameFeatureProvider crossGame = engineServices.crossGameFeatures();
            RenderContext donor = crossGame.getDonorRenderContext();
            if (donor != null) {
                Palette p = donor.getPalette(logicalLine);
                if (p != null) {
                    return new PaletteTarget(p, donor.getEffectivePaletteLine(logicalLine));
                }
            }
        }
        Level level = player.currentLevelManager().getCurrentLevel();
        if (level == null) return null;
        Palette p = level.getPalette(logicalLine);
        return p != null ? new PaletteTarget(p, logicalLine) : null;
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

    /**
     * Returns the animation ID to play during the transformation.
     * Default is 0x1F (AniIDSupSonAni_Transform), used by both S2 and S3K.
     * ROM: move.b #$1F,anim(a0) in Sonic_Transform_Super.
     */
    protected int getTransformationAnimationId() {
        return 0x1F;
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
        if (!player.currentGameState().hasAllEmeralds()) return false;
        if (player.getRingCount() < getMinRingsToTransform()) return false;
        if (player.getDead() || player.isHurt() || player.isDebugMode()) return false;
        if (player.isObjectControlled()) return false;
        return true;
    }

    private void startTransformation() {
        state = SuperState.TRANSFORMING;
        player.setSuperSonic(true);
        // ROM: move.b #$81,obj_control(a0) - freeze physics during transformation
        player.setObjectControlled(true);
        // ROM: move.b #$1F,anim(a0) - play transformation sparkle animation
        player.setForcedAnimationId(getTransformationAnimationId());
        onTransformationStarted();
    }

    private void updateTransformation() {
        if (updateTransformationAnimation()) {
            state = SuperState.SUPER;
            player.applyExternalPhysicsProfile(getSuperProfile());
            swapToSuperAnimProfile();
            ringDrainCounter = getRingDrainInterval();
            onSuperActivated();
            // ROM: clr.b obj_control(a0) - unfreeze after transformation complete
            player.setObjectControlled(false);
            player.setForcedAnimationId(-1);
        }
    }

    private void updateSuper() {
        // ROM: Sonic_Super checks Update_HUD_timer == 0 every frame.
        // When the signpost/egg prison clears the timer, Super Sonic reverts.
        // Do NOT use player.isObjectControlled() - many objects (CPZ pipes, grabbers)
        // set that flag temporarily, causing false detransformation.
        LevelState levelState = player.currentLevelState();
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
        // Clear transformation freeze in case revert happens during transformation
        player.setObjectControlled(false);
        player.setForcedAnimationId(-1);
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
