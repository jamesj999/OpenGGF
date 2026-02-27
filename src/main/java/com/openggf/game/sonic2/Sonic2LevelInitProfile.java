package com.openggf.game.sonic2;

import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.InitStep;
import com.openggf.game.LevelInitProfile;
import com.openggf.game.StaticFixup;
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
 * Sonic 2 level initialization profile.
 * <p>
 * Maps the existing engine teardown operations from
 * {@link com.openggf.GameContext#forTesting()} (phases 2-8) and
 * {@link com.openggf.tests.TestEnvironment#resetPerTest()} to explicit
 * {@link InitStep} lists without changing any behavior.
 */
public class Sonic2LevelInitProfile implements LevelInitProfile {

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
            new InitStep("ResetS2LevelEvents", "Engine: clear S2 level event state",
                () -> Sonic2LevelEventManager.getInstance().resetState()),
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
        return List.of(
            new InitStep("ResetS2LevelEvents", "Engine: clear S2 level event state",
                () -> Sonic2LevelEventManager.getInstance().resetState()),
            new InitStep("ResetParallax", "Engine: clear parallax state",
                () -> ParallaxManager.getInstance().resetState()),
            new InitStep("ClearSprites", "Engine: clear sprite list (lighter than full reset)",
                () -> SpriteManager.getInstance().clearAllSprites()),
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
                () -> GroundSensor.setLevelManager(LevelManager.getInstance()))
        );
    }
}
