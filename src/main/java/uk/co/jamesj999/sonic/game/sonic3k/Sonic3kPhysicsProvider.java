package uk.co.jamesj999.sonic.game.sonic3k;

import uk.co.jamesj999.sonic.game.PhysicsFeatureSet;
import uk.co.jamesj999.sonic.game.PhysicsModifiers;
import uk.co.jamesj999.sonic.game.PhysicsProfile;
import uk.co.jamesj999.sonic.game.PhysicsProvider;

/**
 * Physics provider for Sonic 3 &amp; Knuckles.
 * Returns character-specific profiles; spindash is enabled.
 * Knuckles uses the same base constants as Sonic (sonic3k.asm:30361-30363).
 */
public class Sonic3kPhysicsProvider implements PhysicsProvider {

    @Override
    public PhysicsProfile getProfile(String characterType) {
        if ("tails".equalsIgnoreCase(characterType)) {
            return PhysicsProfile.SONIC_2_TAILS;
        }
        // Sonic and Knuckles share the same base constants
        return PhysicsProfile.SONIC_2_SONIC;
    }

    @Override
    public PhysicsModifiers getModifiers() {
        return PhysicsModifiers.STANDARD;
    }

    @Override
    public PhysicsFeatureSet getFeatureSet() {
        return PhysicsFeatureSet.SONIC_3K;
    }
}
