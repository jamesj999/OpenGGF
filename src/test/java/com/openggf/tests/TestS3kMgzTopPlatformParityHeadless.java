package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.MGZTopPlatformObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kMgzTopPlatformParityHeadless {
    // Captured from the MGZ Act 1 debug-overlay repro used by the launcher regression.
    private static final int START_PIXEL_X = 10612;
    private static final int START_PIXEL_Y = 2036;

    private static SharedLevel sharedLevel;
    private static Object oldSkipIntros;
    private static Object oldMainCharacter;
    private static Object oldSidekickCharacter;

    private HeadlessTestFixture fixture;
    private Sonic sprite;

    @BeforeAll
    static void loadLevel() throws Exception {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        oldSkipIntros = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        oldMainCharacter = config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE);
        oldSidekickCharacter = config.getConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE);
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, Sonic3kZoneIds.ZONE_MGZ, 0);
    }

    @AfterAll
    static void cleanup() {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS,
                oldSkipIntros != null ? oldSkipIntros : false);
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE,
                oldMainCharacter != null ? oldMainCharacter : "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE,
                oldSidekickCharacter != null ? oldSidekickCharacter : "tails");
        if (sharedLevel != null) {
            sharedLevel.dispose();
            sharedLevel = null;
        }
    }

    @BeforeEach
    void setUp() {
        fixture = HeadlessTestFixture.builder().withSharedLevel(sharedLevel).build();
        sprite = (Sonic) fixture.sprite();
        sprite.setCentreX((short) (START_PIXEL_X + (sprite.getWidth() / 2)));
        sprite.setCentreY((short) (START_PIXEL_Y + (sprite.getHeight() / 2)));
        sprite.setPushing(false);
        sprite.setRolling(false);
        sprite.setJumping(false);
        sprite.setDirection(com.openggf.physics.Direction.LEFT);
        sprite.clearWallClingState();
        teleportToReproArea();
        fixture.stepIdleFrames(1);
    }

    @Test
    void grabbedPlatform_usesTrueObjectControlledOwnership() {
        MGZTopPlatformObjectInstance platform = runUntilGrabbedHoldingLeft();

        assertNotNull(platform, "Expected Sonic to grab the MGZ top platform");
        assertTrue(sprite.isObjectControlled(),
                "MGZ top platform should own the player via objectControlled while grabbed");
        assertTrue(sprite.isWallCling(),
                "MGZ top platform should keep the ROM wall-cling/status-tertiary state while grabbed");
        assertFalse(sprite.isOnObject(),
                "Grabbed player should not remain in ordinary on-object standing state");
    }

    @Test
    void jumpRelease_clearsObjectControlledAndWallCling() {
        MGZTopPlatformObjectInstance platform = runUntilGrabbedHoldingLeft();
        assertNotNull(platform, "Expected Sonic to grab the MGZ top platform before jump release");
        assertTrue(sprite.isObjectControlled(),
                "Release regression requires MGZ top platform to start from true object-controlled ownership");
        assertTrue(sprite.isWallCling(),
                "Release regression requires MGZ wall-cling status to be armed before jump release");

        boolean released = false;
        for (int frame = 0; frame < 60; frame++) {
            fixture.stepFrame(false, false, false, false, true);
            if (!sprite.isObjectControlled()) {
                released = true;
                break;
            }
        }

        assertTrue(released, "Expected jump input to release Sonic from the MGZ top platform");
        assertFalse(sprite.isObjectControlled(), "Jump release should clear object-controlled ownership");
        assertFalse(sprite.isWallCling(), "Jump release should clear MGZ wall-cling bits");
        assertTrue(sprite.getAir(), "Released player should return to airborne movement");
    }

    private MGZTopPlatformObjectInstance runUntilGrabbedHoldingLeft() {
        for (int frame = 0; frame < 120; frame++) {
            fixture.stepFrame(false, false, true, false, false);
            MGZTopPlatformObjectInstance platform = findGrabbedPlatform();
            if (platform != null) {
                return platform;
            }
        }
        return null;
    }

    private MGZTopPlatformObjectInstance findGrabbedPlatform() {
        for (ObjectInstance obj : GameServices.level().getObjectManager().getActiveObjects()) {
            if (obj instanceof MGZTopPlatformObjectInstance platform && platform.isPlayerGrabbed(sprite)) {
                return platform;
            }
        }
        return null;
    }

    private void teleportToReproArea() {
        fixture.camera().updatePosition(true);
        GameServices.level().postCameraObjectPlacementSync();
        GameServices.level().getObjectManager().reset(fixture.camera().getX());
    }
}
