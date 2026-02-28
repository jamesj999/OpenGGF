package com.openggf.game.sonic2;

import com.openggf.game.AbstractLevelInitProfile;
import com.openggf.game.InitStep;

/**
 * Sonic 2 level initialization profile.
 * <p>
 * Maps the existing engine teardown operations from
 * {@link com.openggf.GameContext#forTesting()} (phases 2-8) and
 * {@link com.openggf.tests.TestEnvironment#resetPerTest()} to explicit
 * {@link InitStep} lists without changing any behavior.
 */
public class Sonic2LevelInitProfile extends AbstractLevelInitProfile {

    @Override
    protected InitStep levelEventTeardownStep() {
        return new InitStep("ResetS2LevelEvents", "Engine: clear S2 level event state",
            () -> Sonic2LevelEventManager.getInstance().resetState());
    }

    @Override
    protected InitStep perTestLeadStep() {
        return new InitStep("ResetS2LevelEvents", "Engine: clear S2 level event state",
            () -> Sonic2LevelEventManager.getInstance().resetState());
    }
}
