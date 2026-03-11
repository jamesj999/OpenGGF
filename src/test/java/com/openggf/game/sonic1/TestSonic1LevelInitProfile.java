package com.openggf.game.sonic1;

import com.openggf.game.InitStep;
import com.openggf.game.StaticFixup;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class TestSonic1LevelInitProfile {

    private final Sonic1LevelInitProfile profile = new Sonic1LevelInitProfile();

    @Test
    public void teardownHas12Steps() {
        List<InitStep> steps = profile.levelTeardownSteps();

        assertEquals(12, steps.size());

        assertEquals("ResetAudio", steps.get(0).name());
        assertEquals("ResetS1LevelEvents", steps.get(1).name());
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
    public void perTestResetHas9Steps() {
        List<InitStep> steps = profile.perTestResetSteps();

        assertEquals(9, steps.size());

        assertEquals("ResetS1LevelEvents", steps.get(0).name());
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
    public void postTeardownFixupsContainGroundSensorOnly() {
        List<StaticFixup> fixups = profile.postTeardownFixups();

        // GroundSensor no longer needs wiring — always uses LevelManager.getInstance()
        assertEquals(0, fixups.size());
    }

    @Test
    public void levelLoadStepsContains13WithoutPostLoad() {
        List<InitStep> steps = profile.levelLoadSteps(new com.openggf.game.LevelLoadContext());
        assertEquals(13, steps.size());
    }

    @Test
    public void levelLoadStepsContains19Steps() {
        com.openggf.game.LevelLoadContext ctx = new com.openggf.game.LevelLoadContext();
        ctx.setIncludePostLoadAssembly(true);
        List<InitStep> steps = profile.levelLoadSteps(ctx);

        assertEquals(19, steps.size());

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

        // 6 post-load assembly steps (no SpawnSidekick — S1 has no Tails)
        assertEquals("RestoreCheckpoint", steps.get(13).name());
        assertEquals("SpawnPlayer", steps.get(14).name());
        assertEquals("ResetPlayerState", steps.get(15).name());
        assertEquals("InitCamera", steps.get(16).name());
        assertEquals("InitLevelEvents", steps.get(17).name());
        assertEquals("RequestTitleCard", steps.get(18).name());
    }
}
