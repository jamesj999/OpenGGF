package com.openggf.game.sonic3k;

import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.InitStep;
import com.openggf.game.LevelInitProfile;
import com.openggf.game.StaticFixup;
import com.openggf.game.sonic3k.objects.AizPlaneIntroInstance;
import com.openggf.graphics.FadeManager;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.LevelManager;
import com.openggf.level.ParallaxManager;
import com.openggf.level.WaterSystem;
import com.openggf.physics.CollisionSystem;
import com.openggf.physics.GroundSensor;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.timer.TimerManager;

import java.util.List;

/**
 * Sonic 3&K level initialization profile.
 * <p>
 * Same shared structure as the S1/S2 profiles but uses
 * {@link Sonic3kLevelEventManager} for level event reset, and adds
 * {@link AizPlaneIntroInstance#setSidekickSuppressed(boolean)} reset
 * to both per-test reset and post-teardown fixups.
 */
public class Sonic3kLevelInitProfile implements LevelInitProfile {

    @Override
    public List<InitStep> levelLoadSteps() {
        return List.of();
    }

    @Override
    public List<InitStep> levelTeardownSteps() {
        return List.of(
            // Phase 2: Audio (clears ROM-specific SMPS loader cache)
            new InitStep("ResetAudio", "Engine: clear SMPS loader cache",
                () -> AudioManager.getInstance().resetState()),

            // Phase 3: Level subsystems
            new InitStep("ResetS3kLevelEvents", "Engine: clear S3K level event state",
                () -> Sonic3kLevelEventManager.getInstance().resetState()),
            new InitStep("ResetParallax", "Engine: clear parallax state",
                () -> ParallaxManager.getInstance().resetState()),
            new InitStep("ResetLevelManager", "Engine: clear level manager state",
                () -> LevelManager.getInstance().resetState()),

            // Phase 4: Sprites
            new InitStep("ResetSprites", "Engine: full sprite manager reset",
                () -> SpriteManager.getInstance().resetState()),

            // Phase 5: Physics
            new InitStep("ResetCollision", "Engine: destroy and recreate collision system",
                CollisionSystem::resetInstance),

            // Phase 6: Camera and graphics
            new InitStep("ResetCamera", "Engine: clear camera state",
                () -> Camera.getInstance().resetState()),
            new InitStep("ResetGraphics", "Engine: clear graphics manager state",
                () -> GraphicsManager.getInstance().resetState()),
            new InitStep("ResetFade", "Engine: destroy and recreate fade manager",
                FadeManager::resetInstance),

            // Phase 7: Game state and timers
            new InitStep("ResetGameState", "Engine: clear score, lives, emeralds",
                () -> GameServices.gameState().resetSession()),
            new InitStep("ResetTimers", "Engine: clear timer manager state",
                () -> TimerManager.getInstance().resetState()),
            new InitStep("ResetWater", "Engine: clear water system state",
                () -> WaterSystem.getInstance().reset())
        );
    }

    @Override
    public List<InitStep> perTestResetSteps() {
        // Note: S3K level event manager is NOT reset here. The old
        // TestEnvironment.resetPerTest() only called
        // Sonic2LevelEventManager.resetState(), which was a no-op for S3K.
        // Resetting the S3K event manager would destroy zone event handlers
        // (e.g. AIZ events) initialized during the @BeforeClass level load.
        return List.of(
            new InitStep("ResetAizSidekickSuppression",
                "Engine: clear AIZ plane intro sidekick suppression flag",
                () -> AizPlaneIntroInstance.setSidekickSuppressed(false)),
            new InitStep("ResetParallax", "Engine: clear parallax state",
                () -> ParallaxManager.getInstance().resetState()),
            new InitStep("ResetSprites", "Engine: full sprite manager reset",
                () -> SpriteManager.getInstance().resetState()),
            new InitStep("ResetCollision", "Engine: destroy and recreate collision system",
                CollisionSystem::resetInstance),
            new InitStep("ResetCamera", "Engine: clear camera state",
                () -> Camera.getInstance().resetState()),
            new InitStep("ResetFade", "Engine: destroy and recreate fade manager",
                FadeManager::resetInstance),
            new InitStep("ResetGameState", "Engine: clear score, lives, emeralds",
                () -> GameServices.gameState().resetSession()),
            new InitStep("ResetTimers", "Engine: clear timer manager state",
                () -> TimerManager.getInstance().resetState()),
            new InitStep("ResetWater", "Engine: clear water system state",
                () -> WaterSystem.getInstance().reset())
        );
    }

    @Override
    public List<StaticFixup> postTeardownFixups() {
        return List.of(
            new StaticFixup("WireGroundSensor",
                "GroundSensor uses static LevelManager ref that goes stale after resetState()",
                () -> GroundSensor.setLevelManager(LevelManager.getInstance())),
            new StaticFixup("ResetAizSidekickSuppression",
                "AIZ intro sets sidekick suppression flag that persists across level loads",
                () -> AizPlaneIntroInstance.setSidekickSuppressed(false))
        );
    }
}
