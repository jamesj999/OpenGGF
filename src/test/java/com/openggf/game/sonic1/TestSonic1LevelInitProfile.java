package com.openggf.game.sonic1;

import com.openggf.game.InitStep;
import com.openggf.game.RuntimeManager;
import com.openggf.game.StaticFixup;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class TestSonic1LevelInitProfile {

    private final Sonic1LevelInitProfile profile = new Sonic1LevelInitProfile();

    @Before
    public void setUp() {
        RuntimeManager.createGameplay();
    }

    @After
    public void tearDown() {
        RuntimeManager.destroyCurrent();
    }

    @Test
    public void teardownHasExpectedSteps() {
        List<InitStep> steps = profile.levelTeardownSteps();

        assertTrue("Should have at least 10 teardown steps", steps.size() >= 10);

        assertTrue("Should include audio reset",
                steps.stream().anyMatch(s -> s.name().contains("Audio")));
        assertTrue("Should include S1 level event reset",
                steps.stream().anyMatch(s -> s.name().contains("S1LevelEvents")));
        assertTrue("Should include parallax reset",
                steps.stream().anyMatch(s -> s.name().contains("Parallax")));
        assertTrue("Should include level manager reset",
                steps.stream().anyMatch(s -> s.name().contains("LevelManager")));
        assertTrue("Should include sprite reset",
                steps.stream().anyMatch(s -> s.name().contains("Sprites")));
        assertTrue("Should include collision reset",
                steps.stream().anyMatch(s -> s.name().contains("Collision")));
        assertTrue("Should include camera reset",
                steps.stream().anyMatch(s -> s.name().contains("Camera")));
        assertTrue("Should include graphics reset",
                steps.stream().anyMatch(s -> s.name().contains("Graphics")));
        assertTrue("Should include fade reset",
                steps.stream().anyMatch(s -> s.name().contains("Fade")));
        assertTrue("Should include game state reset",
                steps.stream().anyMatch(s -> s.name().contains("GameState")));
        assertTrue("Should include timer reset",
                steps.stream().anyMatch(s -> s.name().contains("Timers")));
        assertTrue("Should include water reset",
                steps.stream().anyMatch(s -> s.name().contains("Water")));
    }

    @Test
    public void perTestResetHasExpectedSteps() {
        List<InitStep> steps = profile.perTestResetSteps();

        assertTrue("Should have at least 7 per-test reset steps", steps.size() >= 7);

        assertTrue("Should include S1 level event reset",
                steps.stream().anyMatch(s -> s.name().contains("S1LevelEvents")));
        assertTrue("Should include parallax reset",
                steps.stream().anyMatch(s -> s.name().contains("Parallax")));
        assertTrue("Should include sprite reset",
                steps.stream().anyMatch(s -> s.name().contains("Sprites")));
        assertTrue("Should include collision reset",
                steps.stream().anyMatch(s -> s.name().contains("Collision")));
        assertTrue("Should include camera reset",
                steps.stream().anyMatch(s -> s.name().contains("Camera")));
        assertTrue("Should include fade reset",
                steps.stream().anyMatch(s -> s.name().contains("Fade")));
        assertTrue("Should include game state reset",
                steps.stream().anyMatch(s -> s.name().contains("GameState")));
        assertTrue("Should include timer reset",
                steps.stream().anyMatch(s -> s.name().contains("Timers")));
        assertTrue("Should include water reset",
                steps.stream().anyMatch(s -> s.name().contains("Water")));
    }

    @Test
    public void postTeardownFixupsAreEmpty() {
        List<StaticFixup> fixups = profile.postTeardownFixups();

        // S1 has no game-specific post-teardown fixups
        assertEquals(0, fixups.size());
    }

    @Test
    public void levelLoadStepsWithoutPostLoadHasMinimumSteps() {
        List<InitStep> steps = profile.levelLoadSteps(new com.openggf.game.LevelLoadContext());
        assertTrue("Should have at least 11 level load steps without post-load",
                steps.size() >= 11);
    }

    @Test
    public void levelLoadStepsWithPostLoadHasExpectedSteps() {
        com.openggf.game.LevelLoadContext ctx = new com.openggf.game.LevelLoadContext();
        ctx.setIncludePostLoadAssembly(true);
        List<InitStep> steps = profile.levelLoadSteps(ctx);

        assertTrue("Should have at least 16 level load steps with post-load",
                steps.size() >= 16);

        // Verify key ROM-aligned resource loading steps are present
        assertTrue("Should include InitGameModule",
                steps.stream().anyMatch(s -> s.name().equals("InitGameModule")));
        assertTrue("Should include InitAudio",
                steps.stream().anyMatch(s -> s.name().equals("InitAudio")));
        assertTrue("Should include LoadLevelData",
                steps.stream().anyMatch(s -> s.name().equals("LoadLevelData")));
        assertTrue("Should include InitAnimatedContent",
                steps.stream().anyMatch(s -> s.name().equals("InitAnimatedContent")));
        assertTrue("Should include InitObjectManager",
                steps.stream().anyMatch(s -> s.name().equals("InitObjectManager")));
        assertTrue("Should include InitCameraBounds",
                steps.stream().anyMatch(s -> s.name().equals("InitCameraBounds")));
        assertTrue("Should include InitGameplayState",
                steps.stream().anyMatch(s -> s.name().equals("InitGameplayState")));
        assertTrue("Should include InitRings",
                steps.stream().anyMatch(s -> s.name().equals("InitRings")));
        assertTrue("Should include InitZoneFeatures",
                steps.stream().anyMatch(s -> s.name().equals("InitZoneFeatures")));
        assertTrue("Should include InitArt",
                steps.stream().anyMatch(s -> s.name().equals("InitArt")));
        assertTrue("Should include InitPlayerAndCheckpoint",
                steps.stream().anyMatch(s -> s.name().equals("InitPlayerAndCheckpoint")));
        assertTrue("Should include InitWater",
                steps.stream().anyMatch(s -> s.name().equals("InitWater")));
        assertTrue("Should include InitBackgroundRenderer",
                steps.stream().anyMatch(s -> s.name().equals("InitBackgroundRenderer")));

        // Verify key post-load assembly steps are present (S1 has no SpawnSidekick)
        assertTrue("Should include RestoreCheckpoint",
                steps.stream().anyMatch(s -> s.name().equals("RestoreCheckpoint")));
        assertTrue("Should include SpawnPlayer",
                steps.stream().anyMatch(s -> s.name().equals("SpawnPlayer")));
        assertTrue("Should include ResetPlayerState",
                steps.stream().anyMatch(s -> s.name().equals("ResetPlayerState")));
        assertTrue("Should include InitCamera",
                steps.stream().anyMatch(s -> s.name().equals("InitCamera")));
        assertTrue("Should include InitLevelEvents",
                steps.stream().anyMatch(s -> s.name().equals("InitLevelEvents")));
        assertTrue("Should include RequestTitleCard",
                steps.stream().anyMatch(s -> s.name().equals("RequestTitleCard")));
    }
}
