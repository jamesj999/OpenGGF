package com.openggf.game.sonic1;

import com.openggf.game.AbstractLevelInitProfile;
import com.openggf.game.InitStep;
import com.openggf.game.sonic1.events.Sonic1LevelEventManager;

/**
 * Sonic 1 level initialization profile.
 * <p>
 * Same structure as the S2 profile but uses
 * {@link Sonic1LevelEventManager} for level event reset.
 */
public class Sonic1LevelInitProfile extends AbstractLevelInitProfile {

    @Override
    protected InitStep levelEventTeardownStep() {
        return new InitStep("ResetS1LevelEvents", "Engine: clear S1 level event state",
            () -> Sonic1LevelEventManager.getInstance().resetState());
    }

    @Override
    protected InitStep perTestLeadStep() {
        return new InitStep("ResetS1LevelEvents", "Engine: clear S1 level event state",
            () -> Sonic1LevelEventManager.getInstance().resetState());
    }
}
