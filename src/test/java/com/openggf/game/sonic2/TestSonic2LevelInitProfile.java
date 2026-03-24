package com.openggf.game.sonic2;

import com.openggf.game.InitStep;
import com.openggf.game.StaticFixup;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class TestSonic2LevelInitProfile {

    private final Sonic2LevelInitProfile profile = new Sonic2LevelInitProfile();

    @Test
    public void teardownStepsMatchGameContextPhases2Through8() {
        List<InitStep> steps = profile.levelTeardownSteps();

        // GameContext.forTesting() phases 2-8 (13 original + 1 DebugOverlayManager reset)
        assertEquals(14, steps.size());

        // Verify ordering matches GameContext phases
        assertEquals("ResetAudio", steps.get(0).name());
        assertEquals("ResetCrossGameFeatures", steps.get(1).name());
        assertEquals("ResetS2LevelEvents", steps.get(2).name());
        assertEquals("ResetParallax", steps.get(3).name());
        assertEquals("ResetLevelManager", steps.get(4).name());
        assertEquals("ResetSprites", steps.get(5).name());
        assertEquals("ResetCollision", steps.get(6).name());
        assertEquals("ResetCamera", steps.get(7).name());
        assertEquals("ResetGraphics", steps.get(8).name());
        assertEquals("ResetFade", steps.get(9).name());
        assertEquals("ResetGameState", steps.get(10).name());
        assertEquals("ResetTimers", steps.get(11).name());
        assertEquals("ResetWater", steps.get(12).name());
        assertEquals("ResetDebugOverlay", steps.get(13).name());
    }

    @Test
    public void perTestResetOmitsAudioLevelManagerAndGraphics() {
        List<InitStep> steps = profile.perTestResetSteps();

        // Per-test reset: 11 operations (no audio, no level manager, no graphics)
        assertEquals(11, steps.size());

        assertEquals("ResetS2LevelEvents", steps.get(0).name());
        assertEquals("ResetCrossGameFeatures", steps.get(1).name());
        assertEquals("ResetParallax", steps.get(2).name());
        assertEquals("ResetSprites", steps.get(3).name());
        assertEquals("ResetCollision", steps.get(4).name());
        assertEquals("ResetCamera", steps.get(5).name());
        assertEquals("ResetFade", steps.get(6).name());
        assertEquals("ResetGameState", steps.get(7).name());
        assertEquals("ResetTimers", steps.get(8).name());
        assertEquals("ResetWater", steps.get(9).name());
        assertEquals("ResetDebugOverlay", steps.get(10).name());
    }

    @Test
    public void postTeardownFixupsContainGroundSensorWiring() {
        List<StaticFixup> fixups = profile.postTeardownFixups();

        // GroundSensor no longer needs wiring — always uses LevelManager.getInstance()
        assertEquals(0, fixups.size());
    }

    @Test
    public void levelLoadStepsContains13WithoutPostLoad() {
        List<InitStep> steps = profile.levelLoadSteps(new com.openggf.game.LevelLoadContext());
        assertEquals(13, steps.size());
        assertEquals("InitGameModule", steps.get(0).name());
        assertEquals("InitBackgroundRenderer", steps.get(12).name());
    }

    @Test
    public void levelLoadStepsContains20Steps() {
        com.openggf.game.LevelLoadContext ctx = new com.openggf.game.LevelLoadContext();
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

        // 7 post-load assembly steps (14-20)
        assertEquals("RestoreCheckpoint", steps.get(13).name());
        assertEquals("SpawnPlayer", steps.get(14).name());
        assertEquals("ResetPlayerState", steps.get(15).name());
        assertEquals("InitCamera", steps.get(16).name());
        assertEquals("InitLevelEvents", steps.get(17).name());
        assertEquals("SpawnSidekick", steps.get(18).name());
        assertEquals("RequestTitleCard", steps.get(19).name());
    }

    @Test
    public void teardownStepsAreImmutable() {
        try {
            profile.levelTeardownSteps().add(null);
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    @Test
    public void levelLoadStepsAreImmutable() {
        try {
            com.openggf.game.LevelLoadContext ctx = new com.openggf.game.LevelLoadContext();
            ctx.setIncludePostLoadAssembly(true);
            profile.levelLoadSteps(ctx).add(null);
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }
}
