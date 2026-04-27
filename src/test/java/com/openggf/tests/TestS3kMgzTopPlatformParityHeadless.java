package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.BreakableWallObjectInstance;
import com.openggf.game.sonic3k.objects.CollapsingBridgeObjectInstance;
import com.openggf.game.sonic3k.objects.MGZTopPlatformObjectInstance;
import com.openggf.game.sonic3k.objects.Sonic3kSpringObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.TouchCategory;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
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
    private static final short TEST_CROSSING_SPEED = (short) 0x600;

    private static SharedLevel sharedLevel;
    private static Object oldSkipIntros;
    private static Object oldMainCharacter;
    private static Object oldSidekickCharacter;

    private HeadlessTestFixture fixture;
    private Sonic sprite;

    private record TerrainApproachCandidate(int centreX, int centreY, int clearance) {
    }

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
    void grabbedPlayer_hurtPathDoesNotUseGenericLostRingSpawnOrdering() throws Exception {
        MGZTopPlatformObjectInstance platform = runUntilGrabbedHoldingLeft();
        assertNotNull(platform, "Expected Sonic to grab the MGZ top platform before hurt");
        sprite.setRingCount(10);

        invokeSharedTouchHurt(platform);
        fixture.stepIdleFrames(1);

        assertTrue(sprite.isHurt(), "Attached player should still enter hurt state");
        assertFalse(sprite.isObjectControlled(), "Hurt should release MGZ platform ownership");
        assertFalse(sprite.isWallCling(), "Hurt should clear the MGZ wall-cling status");
        assertEquals(10, sprite.getRingCount(),
                "MGZ attached hurt should keep the player's rings while wall-cling is active");
        assertEquals(0, activeLostRingCount(),
                "MGZ attached hurt path should not use the generic pre-hurt lost-ring spawn ordering");
    }

    @Test
    void ordinaryTouchHurtStillUsesGenericLostRingSpawnOrdering() throws Exception {
        sprite.setRingCount(10);

        invokeSharedTouchHurt(null);

        assertTrue(sprite.isHurt(), "Ordinary touch hurt should still put the player into hurt");
        assertEquals(0, sprite.getRingCount(),
                "Ordinary touch hurt should still spend the player's rings through the generic spill path");
        assertEquals(10, activeLostRingCount(),
                "Ordinary touch hurt should still spawn lost rings in the shared hurt path");
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
    void mgzCarryController_allowsSpringCandidates() {
        Sonic isolated = new Sonic("sonic", (short) 0, (short) 0);
        MGZTopPlatformObjectInstance controllingPlatform = new MGZTopPlatformObjectInstance(
                new ObjectSpawn(0, 0, 0x5B, 0, 0, false, 0));
        Sonic3kSpringObjectInstance spring = new Sonic3kSpringObjectInstance(
                new ObjectSpawn(0, 0, 0x07, 0x00, 0x00, false, 0));
        isolated.setObjectControlled(true);
        isolated.setMgzTopPlatformCarrySolidContactObject(controllingPlatform);

        assertTrue(isolated.allowsSolidContactsWhileObjectControlled(spring),
                "MGZ carry should still allow spring solid contacts so the carried spring handoff can run");
    }

    @Test
    void mgzCarryController_allowsMgzStompBridgeCandidates() throws Exception {
        Sonic isolated = new Sonic("sonic", (short) 0, (short) 0);
        MGZTopPlatformObjectInstance controllingPlatform = new MGZTopPlatformObjectInstance(
                new ObjectSpawn(0, 0, 0x5B, 0, 0, false, 0));
        CollapsingBridgeObjectInstance stompBridge = newMgzStompBridge();
        isolated.setObjectControlled(true);
        isolated.setMgzTopPlatformCarrySolidContactObject(controllingPlatform);

        assertTrue(isolated.allowsSolidContactsWhileObjectControlled(stompBridge),
                "MGZ carry should still allow MGZ stomp bridge contacts so collapse-on-impact can run");
    }

    @Test
    void mgzStompBridge_contactWhileCarriedTriggersCollapse() throws Exception {
        CollapsingBridgeObjectInstance stompBridge = newMgzStompBridge();
        sprite.setWallCling(true);
        sprite.setAir(true);
        sprite.setOnObject(false);

        stompBridge.onSolidContact(sprite, new SolidContact(true, false, false, true, false), 0);

        assertTrue(getBooleanField(stompBridge, "fragmented"),
                "MGZ stomp bridge should shatter when carried Sonic touches it with wall-cling armed");
        assertEquals(3, getIntField(stompBridge, "state"),
                "MGZ stomp bridge should enter its falling fragment state immediately on stomp contact");
    }

    @Test
    void grabbedCarryMotion_resolvesFloorInsteadOfTunnellingThroughTerrain() throws Exception {
        MGZTopPlatformObjectInstance platform = new MGZTopPlatformObjectInstance(
                new ObjectSpawn(START_PIXEL_X, START_PIXEL_Y, 0x5B, 0, 0, false, 0));
        Object grabState = newPlayerGrabState();
        setIntField(grabState, "routine", 4);
        setBooleanField(grabState, "grabbed", true);
        playerStates(platform).put(sprite, grabState);

        sprite.setObjectControlled(true);
        sprite.setMgzTopPlatformCarrySolidContactObject(platform);
        sprite.setWallCling(true);
        sprite.setAir(true);
        sprite.setOnObject(false);

        TerrainApproachCandidate candidate = findNearbyFloorApproachCandidate();
        assertNotNull(candidate, "Expected MGZ repro area to provide a nearby floor-approach terrain sample");

        sprite.setCentreX((short) candidate.centreX());
        sprite.setCentreY((short) candidate.centreY());
        sprite.setXSpeed((short) 0);
        sprite.setYSpeed(TEST_CROSSING_SPEED);

        invokeMoveGrabbedPlayer(platform, sprite, grabState);

        int maxResolvedCentreY = candidate.centreY() + candidate.clearance();
        assertTrue(sprite.getCentreY() <= maxResolvedCentreY,
                "Grabbed carry motion should resolve nearby floor contact instead of moving through terrain");
    }

    @Test
    void grabbedCarryMotion_keepsFlatAnimationAngleAfterSlopeResolution() throws Exception {
        MGZTopPlatformObjectInstance platform = new MGZTopPlatformObjectInstance(
                new ObjectSpawn(START_PIXEL_X, START_PIXEL_Y, 0x5B, 0, 0, false, 0));
        Object grabState = newPlayerGrabState();
        setIntField(grabState, "routine", 4);
        setBooleanField(grabState, "grabbed", true);
        playerStates(platform).put(sprite, grabState);

        sprite.setObjectControlled(true);
        sprite.setMgzTopPlatformCarrySolidContactObject(platform);
        sprite.setWallCling(true);
        sprite.setAir(true);
        sprite.setOnObject(false);

        TerrainApproachCandidate candidate = findNearbySlopedFloorApproachCandidate();
        assertNotNull(candidate, "Expected MGZ repro area to provide a nearby sloped floor sample");

        sprite.setCentreX((short) candidate.centreX());
        sprite.setCentreY((short) candidate.centreY());
        sprite.setXSpeed((short) 0);
        sprite.setYSpeed(TEST_CROSSING_SPEED);
        sprite.setAngle((byte) 0x20);

        invokeMoveGrabbedPlayer(platform, sprite, grabState);

        assertEquals(0, sprite.getAngle() & 0xFF,
                "MGZ carry should keep Sonic on flat animation-driving angle semantics after terrain resolution");
    }

    @Test
    void grabbedCarryMotion_resolvesWallInsteadOfTunnellingThroughTerrain() throws Exception {
        MGZTopPlatformObjectInstance platform = new MGZTopPlatformObjectInstance(
                new ObjectSpawn(START_PIXEL_X, START_PIXEL_Y, 0x5B, 0, 0, false, 0));
        Object grabState = newPlayerGrabState();
        setIntField(grabState, "routine", 4);
        setBooleanField(grabState, "grabbed", true);
        playerStates(platform).put(sprite, grabState);

        sprite.setObjectControlled(true);
        sprite.setMgzTopPlatformCarrySolidContactObject(platform);
        sprite.setWallCling(true);
        sprite.setAir(true);
        sprite.setOnObject(false);

        TerrainApproachCandidate candidate = findNearbyRightWallApproachCandidate();
        assertNotNull(candidate, "Expected MGZ repro area to provide a nearby wall-approach terrain sample");

        sprite.setCentreX((short) candidate.centreX());
        sprite.setCentreY((short) candidate.centreY());
        sprite.setXSpeed(TEST_CROSSING_SPEED);
        sprite.setYSpeed((short) 0);

        invokeMoveGrabbedPlayer(platform, sprite, grabState);

        int maxResolvedCentreX = candidate.centreX() + candidate.clearance();
        assertTrue(sprite.getCentreX() <= maxResolvedCentreX,
                "Grabbed carry motion should resolve nearby wall contact instead of moving through terrain");
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

    private TerrainApproachCandidate findNearbyFloorApproachCandidate() {
        int minX = START_PIXEL_X - 0x80;
        int maxX = START_PIXEL_X + 0x80;
        int minY = START_PIXEL_Y - 0x80;
        int maxY = START_PIXEL_Y + 0x80;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                TerrainCheckResult floor = ObjectTerrainUtils.checkFloorDist(x, y, sprite.getYRadius());
                if (!floor.foundSurface()) {
                    continue;
                }
                if (floor.distance() <= 0 || floor.distance() >= 6) {
                    continue;
                }
                return new TerrainApproachCandidate(x, y, floor.distance());
            }
        }
        return null;
    }

    private TerrainApproachCandidate findNearbySlopedFloorApproachCandidate() {
        int minX = START_PIXEL_X - 0x80;
        int maxX = START_PIXEL_X + 0x80;
        int minY = START_PIXEL_Y - 0x80;
        int maxY = START_PIXEL_Y + 0x80;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                TerrainCheckResult floor = ObjectTerrainUtils.checkFloorDist(x, y, sprite.getYRadius());
                if (!floor.foundSurface()) {
                    continue;
                }
                int angle = floor.angle() & 0xFF;
                if ((angle & 0x01) != 0 || angle == 0) {
                    continue;
                }
                if (floor.distance() <= 0 || floor.distance() >= 6) {
                    continue;
                }
                return new TerrainApproachCandidate(x, y, floor.distance());
            }
        }
        return null;
    }

    private TerrainApproachCandidate findNearbyRightWallApproachCandidate() {
        int minX = START_PIXEL_X - 0x80;
        int maxX = START_PIXEL_X + 0x80;
        int minY = START_PIXEL_Y - 0x80;
        int maxY = START_PIXEL_Y + 0x80;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                TerrainCheckResult wall = ObjectTerrainUtils.checkRightWallDist(x + sprite.getXRadius(), y);
                if (!wall.foundSurface()) {
                    continue;
                }
                if (wall.distance() <= 0 || wall.distance() >= 6) {
                    continue;
                }
                return new TerrainApproachCandidate(x, y, wall.distance());
            }
        }
        return null;
    }

    private static Object newPlayerGrabState() throws Exception {
        Constructor<?> ctor = Class
                .forName("com.openggf.game.sonic3k.objects.MGZTopPlatformObjectInstance$PlayerGrabState")
                .getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
    }

    private static CollapsingBridgeObjectInstance newMgzStompBridge() throws Exception {
        CollapsingBridgeObjectInstance bridge = new CollapsingBridgeObjectInstance(
                new ObjectSpawn(0, 0, 0x0F, 0x20, 0x00, false, 0));
        Method initMgz = CollapsingBridgeObjectInstance.class.getDeclaredMethod("initMGZ", int.class);
        initMgz.setAccessible(true);
        initMgz.invoke(bridge, 0x20);
        return bridge;
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

    private static void invokeMoveGrabbedPlayer(MGZTopPlatformObjectInstance platform,
                                                Sonic player,
                                                Object playerState) throws Exception {
        Method moveGrabbedPlayer = MGZTopPlatformObjectInstance.class.getDeclaredMethod(
                "moveGrabbedPlayer",
                com.openggf.sprites.playable.AbstractPlayableSprite.class,
                Class.forName("com.openggf.game.sonic3k.objects.MGZTopPlatformObjectInstance$PlayerGrabState"));
        moveGrabbedPlayer.setAccessible(true);
        moveGrabbedPlayer.invoke(platform, player, playerState);
    }

    private void invokeSharedTouchHurt(ObjectInstance source) throws Exception {
        ObjectManager objectManager = GameServices.level().getObjectManager();
        Field touchResponsesField = ObjectManager.class.getDeclaredField("touchResponses");
        touchResponsesField.setAccessible(true);
        Object touchResponses = touchResponsesField.get(objectManager);
        assertNotNull(touchResponses, "Expected ObjectManager touch responses to be available");

        Method applyHurt = touchResponses.getClass()
                .getDeclaredMethod("applyHurt",
                        com.openggf.game.PlayableEntity.class,
                        ObjectInstance.class,
                        TouchResponseResult.class);
        applyHurt.setAccessible(true);
        applyHurt.invoke(touchResponses, sprite, source,
                new TouchResponseResult(0, 0, 0, TouchCategory.HURT));
    }

    private int activeLostRingCount() throws Exception {
        Object ringManager = GameServices.level().getRingManager();
        assertNotNull(ringManager, "Expected level ring manager to be available");

        Field lostRingsField = ringManager.getClass().getDeclaredField("lostRings");
        lostRingsField.setAccessible(true);
        Object lostRings = lostRingsField.get(ringManager);

        Field activeRingCountField = lostRings.getClass().getDeclaredField("activeRingCount");
        activeRingCountField.setAccessible(true);
        return activeRingCountField.getInt(lostRings);
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
