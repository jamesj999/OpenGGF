package com.openggf.game.sonic3k;

import com.openggf.game.InitStep;
import com.openggf.game.LevelLoadContext;
import com.openggf.game.LevelLoadMode;
import com.openggf.game.StaticFixup;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class TestSonic3kLevelInitProfile {

    private final Sonic3kLevelInitProfile profile = new Sonic3kLevelInitProfile();

    @Test
    public void teardownHas12Steps() {
        List<InitStep> steps = profile.levelTeardownSteps();

        assertEquals(12, steps.size());

        assertEquals("ResetAudio", steps.get(0).name());
        assertEquals("ResetS3kLevelEvents", steps.get(1).name());
        assertEquals("ResetParallax", steps.get(2).name());
        assertEquals("ResetLevelManager", steps.get(3).name());
        assertEquals("ResetSprites", steps.get(4).name());
        assertEquals("ResetCollision", steps.get(5).name());
        assertEquals("ResetCamera", steps.get(6).name());
        assertEquals("ResetGraphics", steps.get(7).name());
        assertEquals("ResetFade", steps.get(8).name());
        assertEquals("ResetGameState", steps.get(9).name());
        assertEquals("ResetTimers", steps.get(10).name());
        assertEquals("ResetWater", steps.get(11).name());
    }

    @Test
    public void perTestResetHas9StepsWithoutLevelEventReset() {
        List<InitStep> steps = profile.perTestResetSteps();

        assertEquals(9, steps.size());

        assertEquals("ResetAizSidekickSuppression", steps.get(0).name());
        assertEquals("ResetParallax", steps.get(1).name());
        assertEquals("ResetSprites", steps.get(2).name());
        assertEquals("ResetCollision", steps.get(3).name());
        assertEquals("ResetCamera", steps.get(4).name());
        assertEquals("ResetFade", steps.get(5).name());
        assertEquals("ResetGameState", steps.get(6).name());
        assertEquals("ResetTimers", steps.get(7).name());
        assertEquals("ResetWater", steps.get(8).name());
    }

    @Test
    public void postTeardownFixupsContainGroundSensorAndAiz() {
        List<StaticFixup> fixups = profile.postTeardownFixups();

        assertEquals(2, fixups.size());
        assertEquals("WireGroundSensor", fixups.get(0).name());
        assertEquals("ResetAizSidekickSuppression", fixups.get(1).name());
    }

    @Test
    public void levelLoadStepsContains13WithoutPostLoad() {
        List<InitStep> steps = profile.levelLoadSteps(new LevelLoadContext());
        assertEquals(13, steps.size());
    }

    @Test
    public void levelLoadStepsContains20Steps() {
        LevelLoadContext ctx = new LevelLoadContext();
        ctx.setIncludePostLoadAssembly(true);
        List<InitStep> steps = profile.levelLoadSteps(ctx);

        assertEquals(20, steps.size());

        // Original 13 ROM-aligned resource loading steps
        assertEquals("InitGameModule", steps.get(0).name());
        assertEquals("InitAudio", steps.get(1).name());
        assertEquals("LoadLevelData", steps.get(2).name());
        assertEquals("InitAnimatedContent", steps.get(3).name());
        assertEquals("InitObjectManager", steps.get(4).name());
        assertEquals("InitCameraBounds", steps.get(5).name());
        assertEquals("InitGameplayState", steps.get(6).name());
        assertEquals("InitRings", steps.get(7).name());
        assertEquals("InitZoneFeatures", steps.get(8).name());
        assertEquals("InitArt", steps.get(9).name());
        assertEquals("InitPlayerAndCheckpoint", steps.get(10).name());
        assertEquals("InitWater", steps.get(11).name());
        assertEquals("InitBackgroundRenderer", steps.get(12).name());

        // 7 post-load assembly steps
        assertEquals("RestoreCheckpoint", steps.get(13).name());
        assertEquals("SpawnPlayer", steps.get(14).name());
        assertEquals("ResetPlayerState", steps.get(15).name());
        assertEquals("InitCamera", steps.get(16).name());
        assertEquals("InitLevelEvents", steps.get(17).name());
        assertEquals("SpawnSidekick", steps.get(18).name());
        assertEquals("RequestTitleCard", steps.get(19).name());
    }

    @Test
    public void seamlessReloadSkipsPlayerAndSidekickSteps() {
        LevelLoadContext ctx = new LevelLoadContext();
        ctx.setLoadMode(LevelLoadMode.SEAMLESS_RELOAD);
        List<InitStep> steps = profile.levelLoadSteps(ctx);

        // 13 resource steps minus InitPlayerAndCheckpoint = 12, plus no post-load = 12
        assertEquals(12, steps.size());
        assertFalse(steps.stream().anyMatch(step -> "InitPlayerAndCheckpoint".equals(step.name())));
        assertFalse(steps.stream().anyMatch(step -> "SpawnPlayer".equals(step.name())));
        assertFalse(steps.stream().anyMatch(step -> "SpawnSidekick".equals(step.name())));
        assertFalse(steps.stream().anyMatch(step -> "RestoreCheckpoint".equals(step.name())));
        assertFalse(steps.stream().anyMatch(step -> "RequestTitleCard".equals(step.name())));
    }
}
