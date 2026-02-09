package uk.co.jamesj999.sonic.game.sonic2;

import uk.co.jamesj999.sonic.game.PhysicsFeatureSet;
import uk.co.jamesj999.sonic.game.PhysicsModifiers;
import uk.co.jamesj999.sonic.game.PhysicsProfile;
import uk.co.jamesj999.sonic.game.PhysicsProvider;

/**
 * Physics provider for Sonic 2.
 * Returns Sonic or Tails profile based on character type.
 * Spindash is enabled with standard speed table.
 */
public class Sonic2PhysicsProvider implements PhysicsProvider {

    @Override
    public PhysicsProfile getProfile(String characterType) {
        if ("tails".equalsIgnoreCase(characterType)) {
            return PhysicsProfile.SONIC_2_TAILS;
        }
        return PhysicsProfile.SONIC_2_SONIC;
    }

    @Override
    public PhysicsModifiers getModifiers() {
        return PhysicsModifiers.STANDARD;
    }

    @Override
    public PhysicsFeatureSet getFeatureSet() {
        return PhysicsFeatureSet.SONIC_2;
    }
}
