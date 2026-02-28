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

        assertEquals(1, fixups.size());
        assertEquals("WireGroundSensor", fixups.get(0).name());
    }

    @Test
    public void levelLoadStepsEmptyForNow() {
        assertTrue(profile.levelLoadSteps().isEmpty());
    }
}
