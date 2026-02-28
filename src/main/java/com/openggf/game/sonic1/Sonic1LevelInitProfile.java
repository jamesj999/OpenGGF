package com.openggf.game.sonic1;

import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.InitStep;
import com.openggf.game.LevelInitProfile;
import com.openggf.game.StaticFixup;
import com.openggf.game.sonic1.events.Sonic1LevelEventManager;
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
 * Sonic 1 level initialization profile.
 * <p>
 * Same structure as the S2 profile but uses
 * {@link Sonic1LevelEventManager} for level event reset.
 */
public class Sonic1LevelInitProfile implements LevelInitProfile {

    @Override
    public List<InitStep> levelLoadSteps() {
        return List.of();
    }

    @Override
    public List<InitStep> levelTeardownSteps() {
        return List.of(
            new InitStep("ResetAudio", "Engine: clear SMPS loader cache",
                () -> AudioManager.getInstance().resetState()),

            new InitStep("ResetS1LevelEvents", "Engine: clear S1 level event state",
                () -> Sonic1LevelEventManager.getInstance().resetState()),
            new InitStep("ResetParallax", "Engine: clear parallax state",
                () -> ParallaxManager.getInstance().resetState()),
            new InitStep("ResetLevelManager", "Engine: clear level manager state",
                () -> LevelManager.getInstance().resetState()),

            new InitStep("ResetSprites", "Engine: full sprite manager reset",
                () -> SpriteManager.getInstance().resetState()),

            new InitStep("ResetCollision", "Engine: destroy and recreate collision system",
                CollisionSystem::resetInstance),

            new InitStep("ResetCamera", "Engine: clear camera state",
                () -> Camera.getInstance().resetState()),
            new InitStep("ResetGraphics", "Engine: clear graphics manager state",
                () -> GraphicsManager.getInstance().resetState()),
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
    public List<InitStep> perTestResetSteps() {
        return List.of(
            new InitStep("ResetS1LevelEvents", "Engine: clear S1 level event state",
                () -> Sonic1LevelEventManager.getInstance().resetState()),
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
                () -> GroundSensor.setLevelManager(LevelManager.getInstance()))
        );
    }
}
