package com.openggf.game;

import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.graphics.FadeManager;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.LevelManager;
import com.openggf.level.ParallaxManager;
import com.openggf.level.WaterSystem;
import com.openggf.physics.CollisionSystem;
import com.openggf.physics.GroundSensor;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.timer.TimerManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared teardown and per-test reset logic for all game profiles.
 * <p>
 * Subclasses provide only the game-specific steps via three hooks:
 * {@link #levelEventTeardownStep()}, {@link #perTestLeadStep()}, and
 * {@link #gameSpecificFixups()}.
 */
public abstract class AbstractLevelInitProfile implements LevelInitProfile {

    /** Game-specific level event manager reset (teardown index 1). */
    protected abstract InitStep levelEventTeardownStep();

    /** Game-specific first step for per-test reset. */
    protected abstract InitStep perTestLeadStep();

    /** Additional fixups beyond WireGroundSensor. Override to add game-specific ones. */
    protected List<StaticFixup> gameSpecificFixups() {
        return List.of();
    }

    // Not final: subclasses will provide game-specific level load steps
    // once production level loading is wired through the profile system.
    @Override
    public List<InitStep> levelLoadSteps() {
        return List.of();
    }

    @Override
    public final List<InitStep> levelTeardownSteps() {
        return List.of(
            new InitStep("ResetAudio", "Engine: clear SMPS loader cache",
                () -> AudioManager.getInstance().resetState()),

            levelEventTeardownStep(),

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
    public final List<InitStep> perTestResetSteps() {
        return List.of(
            perTestLeadStep(),

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
    public final List<StaticFixup> postTeardownFixups() {
        List<StaticFixup> extra = gameSpecificFixups();
        if (extra.isEmpty()) {
            return List.of(
                new StaticFixup("WireGroundSensor",
                    "GroundSensor uses static LevelManager ref that goes stale after resetState()",
                    () -> GroundSensor.setLevelManager(LevelManager.getInstance()))
            );
        }
        var fixups = new ArrayList<StaticFixup>(1 + extra.size());
        fixups.add(new StaticFixup("WireGroundSensor",
            "GroundSensor uses static LevelManager ref that goes stale after resetState()",
            () -> GroundSensor.setLevelManager(LevelManager.getInstance())));
        fixups.addAll(extra);
        return List.copyOf(fixups);
    }

    /** Empty profile used as safe default by {@link GameModule#getLevelInitProfile()}. */
    public static final LevelInitProfile EMPTY = new LevelInitProfile() {
        @Override public List<InitStep> levelLoadSteps() { return List.of(); }
        @Override public List<InitStep> levelTeardownSteps() { return List.of(); }
        @Override public List<InitStep> perTestResetSteps() { return List.of(); }
        @Override public List<StaticFixup> postTeardownFixups() { return List.of(); }
    };
}
