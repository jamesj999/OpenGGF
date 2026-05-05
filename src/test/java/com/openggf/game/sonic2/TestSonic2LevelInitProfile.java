package com.openggf.game.sonic2;

import com.openggf.game.session.EngineContext;
import com.openggf.game.InitStep;
import com.openggf.game.RuntimeManager;
import com.openggf.game.StaticFixup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class TestSonic2LevelInitProfile {

    private final Sonic2LevelInitProfile profile =
            new Sonic2LevelInitProfile(new Sonic2LevelEventManager());

    @BeforeEach
    public void setUp() {
        RuntimeManager.configureEngineServices(EngineContext.fromLegacySingletonsForBootstrap());
        RuntimeManager.createGameplay();
    }

    @AfterEach
    public void tearDown() {
        RuntimeManager.destroyCurrent();
    }

    @Test
    public void teardownStepsMatchExpected14Steps() {
        List<InitStep> steps = profile.levelTeardownSteps();

        // 13 original teardown steps + 1 DebugOverlayManager reset = 14 total
        assertEquals(14, steps.size());

        // Verify ordering matches ROM teardown phases
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

        // GroundSensor no longer needs wiring â€” it resolves the active runtime level directly.
        assertEquals(0, fixups.size());
    }

    @Test
    public void levelLoadStepsContains12WithoutPostLoad() {
        List<InitStep> steps = profile.levelLoadSteps(new com.openggf.game.LevelLoadContext());
        assertEquals(12, steps.size());
        assertEquals("InitGameModule", steps.get(0).name());
        assertEquals("InitBackgroundRenderer", steps.get(11).name());
    }

    @Test
    public void levelLoadStepsContains19Steps() {
        com.openggf.game.LevelLoadContext ctx = new com.openggf.game.LevelLoadContext();
        ctx.setIncludePostLoadAssembly(true);
        List<InitStep> steps = profile.levelLoadSteps(ctx);

        assertEquals(19, steps.size());

        // Original 12 ROM-aligned resource loading steps
        // (InitObjectManager + InitCameraBounds merged into InitObjectSystem)
        assertEquals("InitGameModule", steps.get(0).name());
        assertEquals("InitAudio", steps.get(1).name());
        assertEquals("LoadLevelData", steps.get(2).name());
        assertEquals("InitAnimatedContent", steps.get(3).name());
        assertEquals("InitObjectSystem", steps.get(4).name());
        assertEquals("InitGameplayState", steps.get(5).name());
        assertEquals("InitRings", steps.get(6).name());
        assertEquals("InitZoneFeatures", steps.get(7).name());
        assertEquals("InitArt", steps.get(8).name());
        assertEquals("InitPlayerAndCheckpoint", steps.get(9).name());
        assertEquals("InitWater", steps.get(10).name());
        assertEquals("InitBackgroundRenderer", steps.get(11).name());

        // 7 post-load assembly steps (12-18)
        assertEquals("RestoreCheckpoint", steps.get(12).name());
        assertEquals("SpawnPlayer", steps.get(13).name());
        assertEquals("ResetPlayerState", steps.get(14).name());
        assertEquals("InitCamera", steps.get(15).name());
        assertEquals("InitLevelEvents", steps.get(16).name());
        assertEquals("SpawnSidekick", steps.get(17).name());
        assertEquals("RequestTitleCard", steps.get(18).name());
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


