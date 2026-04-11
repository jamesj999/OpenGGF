package com.openggf.tests;

import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.graphics.GraphicsManager;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.level.LevelManager;
import com.openggf.physics.GroundSensor;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.Sprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that HCZ Act 1 starts with the correct falling intro state
 * using the full production level-load path (no test fixture interference).
 * ROM: sonic3k.asm SpawnLevelMainSprites loc_6834.
 */
@RequiresRom(SonicGame.SONIC_3K)
public class TestS3kHcz1IntroState {
    private static AbstractPlayableSprite player;
    private static HeadlessTestRunner runner;

    @BeforeAll
    public static void loadHcz1ViaProductionPath() throws Exception {
        GraphicsManager.getInstance().initHeadless();

        SonicConfigurationService cs = SonicConfigurationService.getInstance();
        String charCode = cs.getString(SonicConfiguration.MAIN_CHARACTER_CODE);

        // Create and register player sprite BEFORE level load, just like the real game.
        Sonic sonic = new Sonic(charCode, (short) 0, (short) 0);
        GameServices.sprites().addSprite(sonic);
        GameServices.camera().setFocusedSprite(sonic);
        GameServices.camera().setFrozen(false);

        // Full production level load path â€” includes ALL profile steps
        LevelManager lm = GameServices.level();
        lm.loadZoneAndAct(Sonic3kZoneIds.ZONE_HCZ, 0);
        GroundSensor.setLevelManager(lm);

        Sprite s = GameServices.sprites().getSprite(charCode);
        assertTrue(s instanceof AbstractPlayableSprite, "Player sprite should be AbstractPlayableSprite");
        player = (AbstractPlayableSprite) s;
        runner = new HeadlessTestRunner(player);
    }

    @AfterAll
    public static void cleanup() {
        TestEnvironment.resetAll();
    }

    @Test
    public void playerStartsAirborne() {
        assertTrue(player.getAir(), "HCZ1 player should start airborne (Status_InAir set)");
    }

    @Test
    public void playerHasFallingAnimation() {
        assertEquals(Sonic3kAnimationIds.HURT_FALL.id(), player.getForcedAnimationId(), "HCZ1 player should have HURT_FALL forced animation (0x1B)");
    }

    @Test
    public void cameraFastScrollCapIs24ForS3k() {
        assertEquals(24, GameServices.camera().getFastScrollCap(), "S3K camera fast scroll cap should be 24 (0x18)");
    }

    @Test
    public void fallingStatePersistsAfterFirstFrames() {
        // Run 5 idle frames and verify the falling state persists
        for (int frame = 0; frame < 5; frame++) {
            runner.stepFrame(false, false, false, false, false);

            assertTrue(player.getAir(), "Player should still be airborne at frame " + frame
                            + " (y=" + player.getCentreY() + ")");
            assertEquals(Sonic3kAnimationIds.HURT_FALL.id(), player.getForcedAnimationId(), "Forced animation should still be HURT_FALL at frame " + frame);
        }
    }

    @Test
    public void animationIdMatchesHurtFallAfterFrames() {
        // Run a frame to trigger animation update
        runner.stepFrame(false, false, false, false, false);
        assertEquals(Sonic3kAnimationIds.HURT_FALL.id(), player.getAnimationId(), "Animation ID should be HURT_FALL (0x1B) after first frame");
    }

    @Test
    public void hurtFallProducesTumbleMappingFrames() {
        // ROM: AniSonic1B: dc.b $09, $8C, $8D, $FF â€” frames 0x8C and 0x8D
        runner.stepFrame(false, false, false, false, false);
        int mappingFrame = player.getMappingFrame();
        assertTrue(mappingFrame == 0x8C || mappingFrame == 0x8D, "HURT_FALL mapping frame should be 0x8C or 0x8D (tumble frames), got 0x"
                        + Integer.toHexString(mappingFrame));
    }

    @Test
    public void animationSetContainsHurtFallScript() {
        var animSet = player.getAnimationSet();
        assertNotNull(animSet, "Player should have an animation set");
        var script = animSet.getScript(Sonic3kAnimationIds.HURT_FALL.id());
        assertNotNull(script, "Animation set should have script for HURT_FALL (0x1B)");
        assertFalse(script.frames().isEmpty(), "HURT_FALL script should have frames");
        assertEquals(0x8C, (int) script.frames().get(0), "HURT_FALL first frame should be 0x8C");
    }
}


