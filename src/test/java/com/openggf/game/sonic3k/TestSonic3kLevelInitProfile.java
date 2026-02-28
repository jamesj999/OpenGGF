package com.openggf.game.sonic3k;

import com.openggf.game.InitStep;
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
        // S3K level event manager is NOT reset per-test because it would
        // destroy zone event handlers (e.g. AIZ events) initialized during
        // the @BeforeClass level load. The old TestEnvironment.resetPerTest()
        // only called Sonic2LevelEventManager.resetState() which was a no-op
        // for S3K tests.
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
    public void levelLoadStepsEmptyForNow() {
        assertTrue(profile.levelLoadSteps().isEmpty());
    }
}
