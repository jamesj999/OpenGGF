package com.openggf.game.sonic3k;

import com.openggf.game.PhysicsFeatureSet;
import com.openggf.game.PhysicsModifiers;
import com.openggf.game.PhysicsProfile;
import com.openggf.game.PhysicsProvider;

/**
 * Physics provider for Sonic 3 &amp; Knuckles.
 * Returns character-specific profiles; spindash is enabled.
 *
 * <p>Normal single-player mode uses the same base constants as S2 ($600/$C/$80).
 * The {@code Character_Speeds} table (sonic3k.asm:202288) is only used in
 * Competition mode ({@code Sonic2P_Index}, line 21457); it is NOT loaded
 * during normal single-player init.
 */
public class Sonic3kPhysicsProvider implements PhysicsProvider {

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
        return PhysicsFeatureSet.SONIC_3K;
    }
}
