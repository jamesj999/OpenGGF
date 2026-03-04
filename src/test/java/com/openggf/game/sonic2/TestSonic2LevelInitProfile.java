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

        // GameContext.forTesting() phases 2-8 have 12 operations
        assertEquals(12, steps.size());

        // Verify ordering matches GameContext phases
        assertEquals("ResetAudio", steps.get(0).name());
        assertEquals("ResetS2LevelEvents", steps.get(1).name());
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
    public void perTestResetOmitsAudioLevelManagerAndGraphics() {
        List<InitStep> steps = profile.perTestResetSteps();

        // Per-test reset: 9 operations (no audio, no level manager, no graphics)
        assertEquals(9, steps.size());

        assertEquals("ResetS2LevelEvents", steps.get(0).name());
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
    public void postTeardownFixupsContainGroundSensorWiring() {
        List<StaticFixup> fixups = profile.postTeardownFixups();

        assertEquals(1, fixups.size());
        assertEquals("WireGroundSensor", fixups.get(0).name());
    }

    @Test
    public void levelLoadStepsContains13RomAlignedSteps() {
        List<InitStep> steps = profile.levelLoadSteps(new com.openggf.game.LevelLoadContext());

        assertEquals(13, steps.size());

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
}
