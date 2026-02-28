package com.openggf.game.sonic3k;

import com.openggf.game.AbstractLevelInitProfile;
import com.openggf.game.InitStep;
import com.openggf.game.StaticFixup;
import com.openggf.game.sonic3k.objects.AizPlaneIntroInstance;

import java.util.List;

/**
 * Sonic 3&K level initialization profile.
 * <p>
 * Same shared structure as the S1/S2 profiles but uses
 * {@link Sonic3kLevelEventManager} for level event reset, and adds
 * {@link AizPlaneIntroInstance#setSidekickSuppressed(boolean)} reset
 * to both per-test reset and post-teardown fixups.
 */
public class Sonic3kLevelInitProfile extends AbstractLevelInitProfile {

    @Override
    protected InitStep levelEventTeardownStep() {
        return new InitStep("ResetS3kLevelEvents", "Engine: clear S3K level event state",
            () -> Sonic3kLevelEventManager.getInstance().resetState());
    }

    @Override
    protected InitStep perTestLeadStep() {
        // Note: S3K level event manager is NOT reset here. The old
        // TestEnvironment.resetPerTest() only called
        // Sonic2LevelEventManager.resetState(), which was a no-op for S3K.
        // Resetting the S3K event manager would destroy zone event handlers
        // (e.g. AIZ events) initialized during the @BeforeClass level load.
        return new InitStep("ResetAizSidekickSuppression",
            "Engine: clear AIZ plane intro sidekick suppression flag",
            () -> AizPlaneIntroInstance.setSidekickSuppressed(false));
    }

    @Override
    protected List<StaticFixup> gameSpecificFixups() {
        return List.of(
            new StaticFixup("ResetAizSidekickSuppression",
                "AIZ intro sets sidekick suppression flag that persists across level loads",
                () -> AizPlaneIntroInstance.setSidekickSuppressed(false))
        );
    }
}
