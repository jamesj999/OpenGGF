package uk.co.jamesj999.sonic.game.sonic3k;

import uk.co.jamesj999.sonic.game.PhysicsProfile;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;
import uk.co.jamesj999.sonic.sprites.playable.SuperStateController;

import java.util.logging.Logger;

/**
 * S3K-specific Super Sonic controller.
 * Uses S3K palette data, animation IDs, and physics profile.
 * TODO: Implement palette cycling from S3K ROM data.
 * TODO: Future extension point for Hyper Sonic, Super Tails, Super Knuckles.
 */
public class Sonic3kSuperStateController extends SuperStateController {
    private static final Logger LOGGER = Logger.getLogger(Sonic3kSuperStateController.class.getName());

    public Sonic3kSuperStateController(AbstractPlayableSprite player) {
        super(player);
    }

    @Override protected int getRingDrainInterval() { return 60; }
    @Override protected int getMinRingsToTransform() { return 50; }
    @Override protected PhysicsProfile getSuperProfile() { return PhysicsProfile.SONIC_3K_SUPER_SONIC; }
    @Override protected PhysicsProfile getNormalProfile() { return PhysicsProfile.SONIC_2_SONIC; }

    @Override protected void onTransformationStarted() {
        LOGGER.info("S3K Super transformation started (stub)");
    }

    @Override protected boolean updateTransformationAnimation() {
        return true; // Instant for now
    }

    @Override protected void onSuperActivated() {
        LOGGER.info("S3K Super Sonic activated (stub)");
    }

    @Override protected void updateSuperPalette() {
        // TODO: S3K palette cycling
    }

    @Override protected void onRevertStarted() {
        LOGGER.info("S3K Super Sonic deactivated (stub)");
    }
}
