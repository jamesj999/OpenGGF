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

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kMgzTopPlatformParityHeadless {

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
        sprite.setX((short) 10612);
        sprite.setY((short) 2036);
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
                "MGZ top platform should still set the ROM wall-cling/status-tertiary state");
        assertFalse(sprite.isOnObject(),
                "Grabbed player should not remain in ordinary on-object standing state");
    }

    private MGZTopPlatformObjectInstance runUntilGrabbedHoldingLeft() {
        MGZTopPlatformObjectInstance platform = null;
        for (int frame = 0; frame < 120; frame++) {
            fixture.stepFrame(false, false, true, false, false);
            platform = findGrabbedPlatform();
            if (platform != null) {
                return platform;
            }
        }
        return null;
    }

    private MGZTopPlatformObjectInstance findGrabbedPlatform() {
        for (ObjectInstance obj : GameServices.level().getObjectManager().getActiveObjects()) {
            if (obj instanceof MGZTopPlatformObjectInstance platform && isGrabbed(platform, sprite)) {
                return platform;
            }
        }
        return null;
    }

    private void teleportToReproArea() {
        fixture.camera().updatePosition(true);
        GameServices.level().getObjectManager().reset(fixture.camera().getX());
    }

    private static boolean isGrabbed(MGZTopPlatformObjectInstance platform, Sonic sprite) {
        try {
            Field field = MGZTopPlatformObjectInstance.class.getDeclaredField("playerStates");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            var map = (java.util.Map<Object, Object>) field.get(platform);
            Object state = map.get(sprite);
            if (state == null) {
                return false;
            }
            Field routineField = state.getClass().getDeclaredField("routine");
            routineField.setAccessible(true);
            return routineField.getInt(state) == 4;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to inspect MGZ top platform grab state", e);
        }
    }
}
