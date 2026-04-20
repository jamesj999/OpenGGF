package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.BreakableWallObjectInstance;
import com.openggf.game.sonic3k.objects.MGZTopPlatformObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.Tails;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void wallClingAlone_doesNotOptIntoObjectControlledSolidContacts() {
        Sonic isolated = new Sonic("sonic", (short) 0, (short) 0);
        MGZTopPlatformObjectInstance candidate = new MGZTopPlatformObjectInstance(
                new ObjectSpawn(0, 0, 0x5B, 0, 0, false, 0));
        isolated.setObjectControlled(true);
        isolated.setWallCling(true);

        assertFalse(isolated.allowsSolidContactsWhileObjectControlled(candidate),
                "Generic wall-cling state should not re-enable solid contacts while object-controlled");
    }

    @Test
    void mgzCarryController_onlyAllowsMgzPlatformAndWallCandidates() {
        Sonic isolated = new Sonic("sonic", (short) 0, (short) 0);
        MGZTopPlatformObjectInstance controllingPlatform = new MGZTopPlatformObjectInstance(
                new ObjectSpawn(0, 0, 0x5B, 0, 0, false, 0));
        MGZTopPlatformObjectInstance otherPlatform = new MGZTopPlatformObjectInstance(
                new ObjectSpawn(32, 0, 0x5B, 0, 0, false, 1));
        BreakableWallObjectInstance mgzWall = new BreakableWallObjectInstance(
                new ObjectSpawn(0, 0, 0x0D, 0, 0, false, 0));
        isolated.setObjectControlled(true);
        isolated.setMgzTopPlatformCarrySolidContactObject(controllingPlatform);

        assertTrue(isolated.allowsSolidContactsWhileObjectControlled(controllingPlatform),
                "MGZ carry should allow solid contacts against the controlling platform instance");
        assertTrue(isolated.allowsSolidContactsWhileObjectControlled(mgzWall),
                "MGZ carry should still allow the MGZ wall checkpoint contact the controller depends on");
        assertFalse(isolated.allowsSolidContactsWhileObjectControlled(otherPlatform),
                "MGZ carry should not opt the player back into every other solid while object-controlled");
    }

    @Test
    void releasedFlight_clearsOccupiedSecondaryRiderStandingState() throws Exception {
        MGZTopPlatformObjectInstance platform = new MGZTopPlatformObjectInstance(
                new ObjectSpawn(0, 0, 0x5B, 0, 0, false, 0));
        Sonic mainPlayer = new Sonic("sonic", (short) 0, (short) 0);
        Tails sidekick = new Tails("tails", (short) 0, (short) 0);
        mainPlayer.setObjectControlled(true);
        mainPlayer.setMgzTopPlatformCarrySolidContactObject(platform);
        mainPlayer.setWallCling(true);
        sidekick.setOnObject(true);

        Object mainState = newPlayerGrabState();
        setIntField(mainState, "routine", 4);
        setBooleanField(mainState, "grabbed", true);
        setIntField(mainState, "entrySideBias", 0x0F);

        Object sidekickState = newPlayerGrabState();
        setIntField(sidekickState, "routine", 2);
        setBooleanField(sidekickState, "standingNow", true);
        setBooleanField(sidekickState, "grabbed", false);
        setIntField(sidekickState, "entrySideBias", 0x0F);

        Map<Object, Object> playerStates = playerStates(platform);
        playerStates.put(mainPlayer, mainState);
        playerStates.put(sidekick, sidekickState);

        Method enterReleasedFlight = MGZTopPlatformObjectInstance.class.getDeclaredMethod("enterReleasedFlight");
        enterReleasedFlight.setAccessible(true);
        enterReleasedFlight.invoke(platform);

        assertFalse(mainPlayer.isObjectControlled(),
                "Released-flight handoff should drop main-player object control");
        assertFalse(mainPlayer.isWallCling(),
                "Released-flight handoff should clear the MGZ wall-cling bits");
        assertFalse(mainPlayer.allowsSolidContactsWhileObjectControlled(platform),
                "Released-flight handoff should clear the explicit MGZ carry solid-contact seam");
        assertFalse(sidekick.isOnObject(),
                "Occupied secondary riders should be detached from ordinary standing state");
        assertEquals(6, getIntField(mainState, "routine"),
                "Main player should advance to the released slot state");
        assertEquals(6, getIntField(sidekickState, "routine"),
                "Occupied secondary rider should advance to the released slot state");
        assertFalse(getBooleanField(mainState, "grabbed"),
                "Released-flight handoff should clear main-player grabbed state");
        assertFalse(getBooleanField(sidekickState, "grabbed"),
                "Released-flight handoff should clear occupied secondary-rider grabbed state");
        assertEquals(0, getIntField(mainState, "entrySideBias"),
                "Released-flight handoff should clear main-player entry bias");
        assertEquals(0, getIntField(sidekickState, "entrySideBias"),
                "Released-flight handoff should clear secondary-rider entry bias");
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

    private static Object newPlayerGrabState() throws Exception {
        Constructor<?> ctor = Class
                .forName("com.openggf.game.sonic3k.objects.MGZTopPlatformObjectInstance$PlayerGrabState")
                .getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> playerStates(MGZTopPlatformObjectInstance platform) throws Exception {
        Field field = MGZTopPlatformObjectInstance.class.getDeclaredField("playerStates");
        field.setAccessible(true);
        return (Map<Object, Object>) field.get(platform);
    }

    private static int getIntField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(target);
    }

    private static boolean getBooleanField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getBoolean(target);
    }

    private static void setIntField(Object target, String fieldName, int value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setInt(target, value);
    }

    private static void setBooleanField(Object target, String fieldName, boolean value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setBoolean(target, value);
    }
}
