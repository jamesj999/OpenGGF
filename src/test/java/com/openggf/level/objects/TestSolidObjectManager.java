package com.openggf.level.objects;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.PhysicsFeatureSet;
import com.openggf.game.RuntimeManager;
import com.openggf.game.solid.PlayerSolidContactResult;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.game.sonic1.objects.Sonic1CollapsingLedgeObjectInstance;
import com.openggf.game.sonic3k.objects.AizTransitionFloorObjectInstance;
import com.openggf.game.sonic3k.objects.CnzTrapDoorInstance;
import com.openggf.graphics.GLCommand;
import com.openggf.physics.Sensor;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.game.PlayableEntity;
import com.openggf.tests.TestEnvironment;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestSolidObjectManager {

    @BeforeEach
    public void setUp() {
        TestEnvironment.resetAll();
    }

    @AfterEach
    public void tearDown() {
        RuntimeManager.destroyCurrent();
    }

    @Test
    public void manualCheckpointObjectSeesStandingStateInsideUpdate() {
        TestPlayableSprite player = new TestPlayableSprite((short) 0, (short) 0);
        player.setWidth(20);
        player.setHeight(20);
        player.setCentreX((short) 100);
        player.setCentreY((short) 83);
        player.setYSpeed((short) 0x100);
        player.setAir(true);

        ManualCheckpointProbeObject object = new ManualCheckpointProbeObject(100, 100);
        ObjectManager manager = buildManager(object);

        manager.update(0, player, List.of(), 0, false, true, false);

        assertTrue(object.standingSeenInsideUpdate);
        assertEquals(1, object.manualCheckpointCount);
        assertEquals(0, object.compatibilityCallbackCount);
    }

    @Test
    public void legacyAutoObjectStillReceivesOnePostUpdateCompatibilityCallback() {
        TestPlayableSprite player = new TestPlayableSprite((short) 0, (short) 0);
        player.setWidth(20);
        player.setHeight(20);
        player.setCentreX((short) 100);
        player.setCentreY((short) 83);
        player.setYSpeed((short) 0x100);
        player.setAir(true);

        AutoCheckpointProbeObject object = new AutoCheckpointProbeObject(100, 100);
        ObjectManager manager = buildManager(object);

        manager.update(0, player, List.of(), 0, false, true, false);

        assertEquals(1, object.compatibilityCallbackCount);
    }

    @Test
    public void topSolidHistoryProviderUsesPreviousPlayerPositionForNewLanding() {
        SolidObjectParams params = new SolidObjectParams(16, 8, 8);
        HistoryTopSolidObject object = new HistoryTopSolidObject(100, 100, params);
        ObjectManager manager = buildManager(object);

        TestPlayableSprite player = new TestPlayableSprite((short) 0, (short) 0);
        player.setWidth(20);
        player.setHeight(38);
        player.setCentreX((short) 100);
        int rejectCentreY = 100 - 4 - params.airHalfHeight() - player.getYRadius() - 1;
        int contactCentreY = rejectCentreY + 2;
        player.setCentreY((short) rejectCentreY);
        player.endOfTick();

        player.setCentreY((short) contactCentreY);
        player.setYSpeed((short) 0x100);
        player.setAir(true);
        player.endOfTick();

        manager.update(0, player, List.of(), 0, false, true, false);

        assertTrue(player.getAir());
        assertFalse(player.isOnObject());

        player.setCentreY((short) (contactCentreY + 1));
        player.endOfTick();

        manager.update(0, player, List.of(), 0, false, true, false);

        assertFalse(player.getAir());
        assertTrue(player.isOnObject());
        assertEquals(0, player.getYSpeed());
    }

    @Test
    public void compatibilityAutoObjectPreservesPerPlayerCallbackTimingWithSidekickMutation() {
        TestPlayableSprite player = createStandingProbePlayer();
        TestPlayableSprite sidekick = createStandingProbePlayer();

        FirstContactDisablesSolidityProbeObject object =
                new FirstContactDisablesSolidityProbeObject(100, 100);
        ObjectManager manager = buildManager(object);

        manager.update(0, player, List.of(sidekick), 0, false, true, false);

        assertEquals(1, object.compatibilityCallbackCount);
        assertEquals(player, object.firstCallbackPlayer);
        assertTrue(object.mainPlayerSawCallback);
        assertFalse(object.sidekickSawCallback);
    }

    @Test
    public void testStandingContactOnFlatObject() {
        SolidObjectParams params = new SolidObjectParams(16, 8, 8);
        TestSolidObject object = new TestSolidObject(100, 100, params);
        ObjectManager manager = buildManager(object);

        TestPlayableSprite player = new TestPlayableSprite((short) 0, (short) 0);
        player.setWidth(20);
        player.setHeight(20);
        player.setCentreX((short) 100);
        int centreY = 100 - params.groundHalfHeight() - player.getYRadius();
        player.setCentreY((short) centreY);
        player.setYSpeed((short) 0);

        assertTrue(manager.hasStandingContact(player));
    }

    @Test
    public void walkingPastRidingBoundsClearsOnObjectAndSetsAirEvenWithLatchedInteract() {
        SolidObjectParams params = new SolidObjectParams(16, 8, 8);
        TestSolidObject object = new TestSolidObject(100, 100, params);
        ObjectManager manager = buildManager(object);

        TestPlayableSprite player = new TestPlayableSprite((short) 0, (short) 0);
        player.setWidth(20);
        player.setHeight(20);
        player.setCentreX((short) 100);
        int centreY = 100 - params.groundHalfHeight() - player.getYRadius();
        player.setCentreY((short) centreY);
        player.setYSpeed((short) 0);
        player.setAir(true);

        manager.updateSolidContacts(player);
        assertTrue(player.isOnObject());
        assertFalse(player.getAir());

        player.setLatchedSolidObject(object.getSpawn().objectId(), object);
        player.setCentreX((short) (100 + (params.halfWidth() * 2) + 1));

        manager.updateSolidContacts(player);

        assertFalse(player.isOnObject(),
                "S3K SolidObjectFull_1P/SolidObjectTop_1P clear Status_OnObj when riding bounds are left");
        assertTrue(player.getAir(),
                "S3K SolidObjectFull_1P/SolidObjectTop_1P set Status_InAir on riding walkoff");
        assertEquals(object.getSpawn().objectId(), player.getLatchedSolidObjectId(),
                "The ROM interact slot can remain latched after Status_OnObj is cleared");
    }

    @Test
    public void walkingToExactRightRidingBoundaryClearsOnObjectWithoutStickyExtension() {
        SolidObjectParams params = new SolidObjectParams(16, 8, 8);
        TestSolidObject object = new TestSolidObject(100, 100, params);
        ObjectManager manager = buildManager(object);

        TestPlayableSprite player = new TestPlayableSprite((short) 0, (short) 0);
        player.setWidth(20);
        player.setHeight(20);
        player.setCentreX((short) 100);
        int centreY = 100 - params.groundHalfHeight() - player.getYRadius();
        player.setCentreY((short) centreY);
        player.setYSpeed((short) 0);
        player.setAir(true);

        manager.updateSolidContacts(player);
        assertTrue(player.isOnObject());
        assertFalse(player.getAir());

        player.setCentreX((short) (100 + params.halfWidth()));

        manager.updateSolidContacts(player);

        assertFalse(player.isOnObject(),
                "S3K SolidObjectFull_1P/SolidObjectTop_1P treat relX == width*2 as outside ride bounds");
        assertTrue(player.getAir(),
                "Leaving exact ride bounds sets Status_InAir instead of extending support with a sticky buffer");
    }

    @Test
    public void testHeadroomDistanceUpward() {
        SolidObjectParams params = new SolidObjectParams(16, 8, 8);
        TestSolidObject object = new TestSolidObject(100, 70, params);
        ObjectManager manager = buildManager(object);

        TestPlayableSprite player = new TestPlayableSprite((short) 0, (short) 0);
        player.setWidth(20);
        player.setHeight(20);
        player.setCentreX((short) 100);
        player.setCentreY((short) 100);

        int distance = manager.getHeadroomDistance(player, 0x00);

        assertEquals(3, distance);
    }

    @Test
    public void testCollapsingLedgeUsesSlopedSurfaceProfile() {
        ObjectSpawn spawn = new ObjectSpawn(100, 100, 0x1A, 0, 0, false, 0);
        Sonic1CollapsingLedgeObjectInstance ledge = new Sonic1CollapsingLedgeObjectInstance(spawn);
        ObjectManager manager = buildManager(ledge);

        TestPlayableSprite player = new TestPlayableSprite((short) 0, (short) 0);
        player.setWidth(20);
        player.setHeight(20);
        player.setAir(true);
        player.setYSpeed((short) 0);

        // ROM: SlopeObject uses absolute slope â€” surfaceY = objectY - slopeSample.
        // Stable centreY on surface = surfaceY - yRadius - 1 (where Platform3's +3 offset
        // cancels the +4 in the relY formula, leaving distY=3, newY = centreY - 3 + 3 = centreY).

        // Left-side sample (heightmap value 0x20=32): surfaceY=100-32=68, stable centreY=48.
        // Use an interior X so top resolution wins over side resolution.
        player.setCentreX((short) 64);
        player.setCentreY((short) 48);
        manager.updateSolidContacts(player);
        int leftCenterY = player.getCentreY();
        assertEquals(48, leftCenterY);

        // Right-side sample (heightmap value 0x30=48): surfaceY=100-48=52, stable centreY=32.
        player.setAir(true);
        player.setYSpeed((short) 0);
        player.setCentreX((short) 136);
        player.setCentreY((short) 32);
        manager.updateSolidContacts(player);
        int rightCenterY = player.getCentreY();
        assertEquals(32, rightCenterY);

        // Shape must not be flat: right edge is 16px higher than left edge.
        assertEquals(16, leftCenterY - rightCenterY);
    }

    @Test
    public void testCollapsingLedgeFragmentWalkOffWindowRemainsSolid() throws Exception {
        ObjectSpawn spawn = new ObjectSpawn(100, 100, 0x1A, 0, 0, false, 0);
        Sonic1CollapsingLedgeObjectInstance ledge = new Sonic1CollapsingLedgeObjectInstance(spawn);

        setPrivateInt(ledge, "routine", 6);
        setPrivateBoolean(ledge, "collapseFlag", true);
        assertTrue(ledge.isSolidFor(null));

        // Disassembly parity: once collapse flag is cleared in routine 6, the ledge no longer
        // runs walk-off collision and should not remain solid.
        setPrivateBoolean(ledge, "collapseFlag", false);
        assertFalse(ledge.isSolidFor(null));
    }

    @Test
    public void testNearTopSideContactDoesNotSetPushingFlag() {
        SolidObjectParams params = new SolidObjectParams(16, 8, 8);
        TestSolidObject object = new TestSolidObject(100, 100, params);
        ObjectManager manager = buildManager(object);

        TestPlayableSprite player = new TestPlayableSprite((short) 0, (short) 0);
        player.setWidth(20);
        player.setHeight(20);
        player.setAir(false);
        player.setXSpeed((short) 0x100);
        // Left edge of object, near top edge: side graze while walking across tops.
        player.setCentreX((short) 85);
        player.setCentreY((short) 71);

        manager.updateSolidContacts(player);

        assertFalse(player.getPushing());
    }

    @Test
    public void testMidSideContactStillSetsPushingFlag() {
        SolidObjectParams params = new SolidObjectParams(16, 8, 8);
        TestSolidObject object = new TestSolidObject(100, 100, params);
        ObjectManager manager = buildManager(object);

        TestPlayableSprite player = new TestPlayableSprite((short) 0, (short) 0);
        player.setWidth(20);
        player.setHeight(20);
        player.setAir(false);
        player.setXSpeed((short) 0x100);
        // Left edge of object, deeper than top-edge buffer: should count as push.
        player.setCentreX((short) 85);
        player.setCentreY((short) 81);

        manager.updateSolidContacts(player);

        assertTrue(player.getPushing());
    }

    @Test
    public void optedInFullSolidRightEdgeIsInclusiveLikeRomBhiCheck() {
        SolidObjectParams params = new SolidObjectParams(19, 14, 15);
        TestSolidObject object = new InclusiveRightEdgeSolidObject(100, 100, params);
        ObjectManager manager = buildManager(object);

        TestPlayableSprite player = new TestPlayableSprite((short) 0, (short) 0);
        player.setWidth(20);
        player.setHeight(38);
        player.setAir(false);
        player.setXSpeed((short) -0x100);
        player.setGSpeed((short) -0x100);
        player.setCentreX((short) (100 + params.halfWidth()));
        player.setCentreY((short) 100);

        manager.updateSolidContacts(player);

        assertTrue(player.getPushing(),
                "SolidObject_cont uses cmp/bhi, so relX == width*2 is still a side contact");
        assertEquals(100 + params.halfWidth(), player.getCentreX(),
                "Inclusive exact-edge contact has d0 == 0 in SolidObject_cont and must not shove X by 1px");
        assertEquals(0, player.getXSpeed());
        assertEquals(0, player.getGSpeed());
    }

    @Test
    public void upwardBottomCollisionPreservesGroundSpeed() {
        SolidObjectParams params = new SolidObjectParams(16, 8, 8);
        TestSolidObject object = new TestSolidObject(100, 100, params);
        ObjectManager manager = buildManager(object);

        TestPlayableSprite player = new TestPlayableSprite((short) 0, (short) 0);
        player.useFeatureSet(PhysicsFeatureSet.SONIC_2);
        player.setWidth(20);
        player.setHeight(20);
        player.setAir(true);
        player.setYSpeed((short) -0x400);
        player.setGSpeed((short) 0x0B54);
        player.setCentreX((short) 100);
        player.setCentreY((short) 119);

        manager.updateSolidContacts(player);

        assertEquals(0x0B54, player.getGSpeed() & 0xFFFF);
        assertEquals(0, player.getYSpeed());
        assertTrue(player.getAir());
        assertFalse(player.isOnObject());
    }

    @Test
    public void upwardBottomCollisionCanClearGroundSpeedPerFeatureSet() {
        SolidObjectParams params = new SolidObjectParams(16, 8, 8);
        TestSolidObject object = new TestSolidObject(100, 100, params);
        ObjectManager manager = buildManager(object);

        TestPlayableSprite player = new TestPlayableSprite((short) 0, (short) 0);
        player.useFeatureSet(PhysicsFeatureSet.SONIC_3K);
        player.setWidth(20);
        player.setHeight(20);
        player.setAir(true);
        player.setYSpeed((short) -0x400);
        player.setGSpeed((short) 0x0813);
        player.setCentreX((short) 100);
        player.setCentreY((short) 119);

        manager.updateSolidContacts(player);

        assertEquals(0, player.getGSpeed());
        assertEquals(0, player.getYSpeed());
        assertTrue(player.getAir());
        assertFalse(player.isOnObject());
    }

    @Test
    public void groundedWallModeBottomCollisionPreservesGroundSpeed() {
        SolidObjectParams params = new SolidObjectParams(0x1B, 0x10, 0x11);
        TestSolidObject object = new TestSolidObject(100, 100, params);
        ObjectManager manager = buildManager(object);

        TestPlayableSprite player = new TestPlayableSprite((short) 0, (short) 0);
        player.useFeatureSet(PhysicsFeatureSet.SONIC_3K);
        player.setWidth(28);
        player.setHeight(38);
        player.setAir(false);
        player.setRolling(true);
        player.setGroundMode(com.openggf.game.GroundMode.RIGHTWALL);
        player.setAngle((byte) 0xC0);
        player.setYSpeed((short) -0x0351);
        player.setGSpeed((short) 0x0351);
        player.setCentreX((short) 101);
        player.setCentreY((short) 129);

        manager.updateSolidContacts(player);

        assertEquals(0, player.getYSpeed());
        assertEquals(0x0351, player.getGSpeed() & 0xFFFF);
        assertFalse(player.getAir());
    }

    @Test
    public void sonic1RollingAirborneFullSolidIgnoresUndersideAtCurrentRadiusBoundary() {
        SolidObjectParams params = new SolidObjectParams(0x2B, 0x48, 0x49);
        TestSolidObject object = new TestSolidObject(100, 100, params);
        ObjectManager manager = buildManager(object);

        TestPlayableSprite player = new TestPlayableSprite((short) 0, (short) 0);
        player.useFeatureSet(PhysicsFeatureSet.SONIC_1);
        player.setWidth(28);
        player.setHeight(38);
        player.setAir(true);
        player.setRolling(true);
        player.setYSpeed((short) -0x00F0);
        player.setCentreX((short) 100);
        // S1 SolidObject uses the player's CURRENT obHeight for the full-rect overlap.
        // At deltaY = airHalfHeight + rollingYRadius, ROM remains just outside the box.
        player.setCentreY((short) (100 + params.airHalfHeight() + player.getYRadius()));

        manager.updateSolidContacts(player);

        assertEquals(-0x00F0, player.getYSpeed(),
                "Rolling airborne full-solid overlap should not use standing radius for the underside check");
        assertEquals(100 + params.airHalfHeight() + 14, player.getCentreY());
        assertTrue(player.getAir());
        assertFalse(player.isOnObject());
    }

    @Test
    public void sonic3kRollingAirborneFullSolidUsesTallerUndersideOverlapWindow() {
        SolidObjectParams params = new SolidObjectParams(0x1B, 8, 0x10);
        TestSolidObject object = new TestSolidObject(100, 100, params);
        ObjectManager manager = buildManager(object);

        TestPlayableSprite player = new TestPlayableSprite((short) 0, (short) 0);
        player.useFeatureSet(PhysicsFeatureSet.SONIC_3K);
        player.setWidth(28);
        player.setHeight(38);
        player.setAir(true);
        player.setRolling(true);
        player.setYSpeed((short) -0x00F0);
        player.setGSpeed((short) 0x0813);
        player.setCentreX((short) 100);
        // This is the AIZ spring-style boundary: current-radius-only overlap misses it,
        // but the taller S2/S3K underside box should still resolve the bottom hit.
        player.setCentreY((short) (100 + params.airHalfHeight() + player.getYRadius()));

        manager.updateSolidContacts(player);

        assertEquals(0, player.getYSpeed());
        assertEquals(0, player.getGSpeed());
        assertEquals(100 + params.airHalfHeight() + player.getStandYRadius(), player.getCentreY());
        assertTrue(player.getAir());
        assertFalse(player.isOnObject());
    }

    @Test
    public void testSonic1TopSolidEdgeLandingZoneRejectionAndAcceptance() {
        GameModule previous = GameModuleRegistry.getCurrent();
        GameModuleRegistry.setCurrent(new Sonic1GameModule());
        try {
            SolidObjectParams params = new SolidObjectParams(32, 8, 8);
            TestSolidObject object = new TestSolidObject(100, 100, params, true);
            ObjectManager manager = buildManager(object);

            // S1 uses obActWid = halfWidth - 0x0B = 21px as the top-landing half-width.
            // Player at 5px inside the COLLISION edge (X = 100 - 32 + 5 = 73) is OUTSIDE
            // the 21px landing zone (valid range: [79, 121]). Landing must fail.
            TestPlayableSprite playerOutside = new TestPlayableSprite((short) 0, (short) 0);
            playerOutside.setWidth(20);
            playerOutside.setHeight(20);
            playerOutside.setAir(true);
            playerOutside.setYSpeed((short) 0x100);
            int maxTop = params.groundHalfHeight() + playerOutside.getYRadius();
            int targetDistY = 10;
            int centreY = 100 - 4 - maxTop + targetDistY;
            playerOutside.setCentreX((short) (100 - params.halfWidth() + 5)); // X = 73
            playerOutside.setCentreY((short) centreY);

            manager.updateSolidContacts(playerOutside);

            assertFalse(playerOutside.isOnObject());
            assertTrue(playerOutside.getAir());

            // Player at 5px inside the LANDING ZONE edge (X = 100 - 21 + 5 = 84) is INSIDE
            // the 21px landing zone. Landing must succeed.
            int landingHalfWidth = params.halfWidth() - 0x0B; // 32 - 11 = 21
            TestPlayableSprite playerInside = new TestPlayableSprite((short) 0, (short) 0);
            playerInside.setWidth(20);
            playerInside.setHeight(20);
            playerInside.setAir(true);
            playerInside.setYSpeed((short) 0x100);
            playerInside.setCentreX((short) (100 - landingHalfWidth + 5)); // X = 84
            playerInside.setCentreY((short) centreY);

            manager.updateSolidContacts(playerInside);

            assertTrue(playerInside.isOnObject());
            assertFalse(playerInside.getAir());
        } finally {
            GameModuleRegistry.setCurrent(previous);
        }
    }

    @Test
    public void cnzTrapDoorSolidObjectTopAcceptsExactSurfaceBoundaryAndLandsOnePixelInside() {
        ObjectSpawn spawn = new ObjectSpawn(100, 100, 0x44, 0, 0, false, 0);
        CnzTrapDoorInstance object = new CnzTrapDoorInstance(spawn);
        ObjectManager manager = buildManager(object);
        object.snapshotPreUpdatePosition();
        SolidObjectParams params = object.getSolidParams();

        TestPlayableSprite exactBoundary = new TestPlayableSprite((short) 0, (short) 0);
        exactBoundary.useFeatureSet(PhysicsFeatureSet.SONIC_3K);
        exactBoundary.setWidth(20);
        exactBoundary.setHeight(38);
        exactBoundary.setAir(true);
        exactBoundary.setYSpeed((short) 0x100);
        exactBoundary.setCentreX((short) 100);
        int maxTop = params.groundHalfHeight() + exactBoundary.getYRadius();
        exactBoundary.setCentreY((short) (100 - 4 - maxTop));

        manager.updateSolidContacts(exactBoundary);

        assertTrue(exactBoundary.isOnObject(),
                "S3K SolidObjectTop accepts the ROM d0 == 0 boundary (sonic3k.asm:41996-42015)");
        assertFalse(exactBoundary.getAir());
        assertEquals(0, exactBoundary.getYSpeed());
        assertEquals(100 - params.groundHalfHeight() - exactBoundary.getYRadius() - 1,
                exactBoundary.getCentreY());

        TestPlayableSprite insideBoundary = new TestPlayableSprite((short) 0, (short) 0);
        insideBoundary.useFeatureSet(PhysicsFeatureSet.SONIC_3K);
        insideBoundary.setWidth(20);
        insideBoundary.setHeight(38);
        insideBoundary.setAir(true);
        insideBoundary.setYSpeed((short) 0x100);
        insideBoundary.setCentreX((short) 100);
        insideBoundary.setCentreY((short) (100 - 4 - maxTop + 1));

        manager.updateSolidContacts(insideBoundary);

        assertTrue(insideBoundary.isOnObject());
        assertFalse(insideBoundary.getAir());
        assertEquals(0, insideBoundary.getYSpeed());
        assertEquals(100 - params.groundHalfHeight() - insideBoundary.getYRadius() - 1,
                insideBoundary.getCentreY());
    }

    @Test
    public void aizTransitionFloorDelaysSonicExactBoundaryWhileAllowingInsideLanding() {
        AizTransitionFloorObjectInstance floor = new AizTransitionFloorObjectInstance();
        ObjectManager manager = buildManager(floor);
        SolidObjectParams params = floor.getSolidParams();

        TestPlayableSprite sonic = new TestPlayableSprite((short) 0, (short) 0);
        sonic.useFeatureSet(PhysicsFeatureSet.SONIC_3K);
        sonic.setWidth(20);
        sonic.setHeight(38);
        sonic.setAir(false);
        sonic.setYSpeed((short) 0);
        sonic.setCentreX((short) floor.getX());
        int exactBoundaryY = floor.getY() - 4 - params.airHalfHeight() - sonic.getYRadius();
        sonic.setCentreY((short) exactBoundaryY);

        TestPlayableSprite sidekick = new TestPlayableSprite((short) 0, (short) 0);
        sidekick.useFeatureSet(PhysicsFeatureSet.SONIC_3K);
        sidekick.setWidth(20);
        sidekick.setHeight(38);
        sidekick.setAir(false);
        sidekick.setYSpeed((short) 0);
        sidekick.setCentreX((short) floor.getX());
        sidekick.setCentreY((short) (exactBoundaryY + 3));

        for (int i = 0; i < 20; i++) {
            manager.processImmediateInlineSolidCheckpoint(floor, sonic, List.of(sidekick));
            assertFalse(sonic.isOnObject(),
                    "AIZ transition floor exact-boundary checks reject during the fire-refresh window");
            assertEquals(exactBoundaryY, sonic.getCentreY());
            assertTrue(sidekick.isOnObject(),
                    "Inside-boundary landings still follow SolidObjectTop first landing");
        }

        manager.processImmediateInlineSolidCheckpoint(floor, sonic, List.of(sidekick));

        assertTrue(sonic.isOnObject(),
                "AIZ transition floor accepts Sonic after the refresh window reaches SolidObjectTop landing");
        assertEquals(exactBoundaryY + 3, sonic.getCentreY(),
                "SolidObjectTop first landing applies y_pos += d0 + 3 (sonic3k.asm:42013-42015)");
    }

    @Test
    public void testNarrowTopLandingWidthRejectsOuterEdgeStanding() {
        SolidObjectParams params = new SolidObjectParams(0x2B, 0x60, 0x61);
        TestSolidObject object = new TestSolidObject(100, 100, params, false, 0x20);
        ObjectManager manager = buildManager(object);

        TestPlayableSprite player = new TestPlayableSprite((short) 0, (short) 0);
        player.setWidth(20);
        player.setHeight(20);
        player.setAir(true);
        player.setYSpeed((short) 0x100);

        // Inside collision width ($2B), but outside standable width ($20).
        player.setCentreX((short) (100 + 0x28));
        int maxTop = params.groundHalfHeight() + player.getYRadius();
        player.setCentreY((short) (100 - 4 - maxTop + 8));

        manager.updateSolidContacts(player);

        assertFalse(player.isOnObject());
        assertTrue(player.getAir());
    }

    @Test
    public void testTopSolidCanOptIntoFullCollisionLandingWidth() {
        GameModule previous = GameModuleRegistry.getCurrent();
        GameModuleRegistry.setCurrent(new Sonic1GameModule());
        try {
            SolidObjectParams params = new SolidObjectParams(0x28, 8, 8);
            TestSolidObject object = new TestSolidObject(100, 100, params, true, null, true);
            ObjectManager manager = buildManager(object);

            TestPlayableSprite player = new TestPlayableSprite((short) 0, (short) 0);
            player.setWidth(20);
            player.setHeight(20);
            player.setAir(true);
            player.setYSpeed((short) 0x100);
            player.setCentreX((short) (100 - 35));
            int maxTop = params.groundHalfHeight() + player.getYRadius();
            player.setCentreY((short) (100 - 4 - maxTop + 10));

            manager.updateSolidContacts(player);

            assertTrue(player.isOnObject());
            assertFalse(player.getAir());
        } finally {
            GameModuleRegistry.setCurrent(previous);
        }
    }

    @Test
    public void testNarrowTopLandingWidthStillAllowsCenterStanding() {
        SolidObjectParams params = new SolidObjectParams(0x2B, 0x60, 0x61);
        TestSolidObject object = new TestSolidObject(100, 100, params, false, 0x20);
        ObjectManager manager = buildManager(object);

        TestPlayableSprite player = new TestPlayableSprite((short) 0, (short) 0);
        player.setWidth(20);
        player.setHeight(20);
        player.setAir(true);
        player.setYSpeed((short) 0x100);

        player.setCentreX((short) (100 + 0x18));
        int maxTop = params.groundHalfHeight() + player.getYRadius();
        player.setCentreY((short) (100 - 4 - maxTop + 8));

        manager.updateSolidContacts(player);

        assertTrue(player.isOnObject());
        assertFalse(player.getAir());
    }

    @Test
    public void testRidingStatePersistsWhileInsideCollisionWidth() {
        SolidObjectParams params = new SolidObjectParams(0x2B, 0x60, 0x61);
        TestSolidObject object = new TestSolidObject(100, 100, params, false, 0x20);
        ObjectManager manager = buildManager(object);

        TestPlayableSprite player = new TestPlayableSprite((short) 0, (short) 0);
        player.setWidth(20);
        player.setHeight(20);
        player.setAir(true);
        player.setYSpeed((short) 0x100);

        int maxTop = params.groundHalfHeight() + player.getYRadius();

        // Land first to establish riding state.
        player.setCentreX((short) (100 + 0x10));
        player.setCentreY((short) (100 - 4 - maxTop + 8));
        manager.updateSolidContacts(player);
        assertTrue(manager.isRidingObject(player));

        // Move to X that is still inside the full collision width but outside the
        // narrower top-standing width. ExitPlatform-style continuation should keep
        // the player riding until they actually leave the object's collision box.
        player.setCentreX((short) (100 + 0x28));
        manager.updateSolidContacts(player);

        assertTrue(manager.isRidingObject(player));
    }

    @Test
    public void unifiedRideExitClearsOnObjectWithoutForcingAirSameFrame() {
        GameModule previous = GameModuleRegistry.getCurrent();
        GameModuleRegistry.setCurrent(new Sonic1GameModule());
        try {
            SolidObjectParams params = new SolidObjectParams(16, 8, 8);
            TestSolidObject object = new TestSolidObject(100, 100, params, true);
            ObjectManager manager = buildManager(object);

            TestPlayableSprite player = new TestPlayableSprite((short) 0, (short) 0);
            player.useFeatureSet(PhysicsFeatureSet.SONIC_1);
            player.setWidth(20);
            player.setHeight(20);
            player.setAir(false);
            player.setOnObject(true);
            player.setYSpeed((short) 0);
            player.setCentreX((short) 100);
            player.setCentreY((short) (100 - params.groundHalfHeight() - player.getYRadius()));

            manager.update(0, player, List.of(), 0, false, true, true);

            assertTrue(manager.isRidingObject(player));
            assertTrue(player.isOnObject());
            assertFalse(player.getAir());

            player.setCentreX((short) (100 + params.halfWidth() + 12));
            manager.update(0, player, List.of(), 1, false, true, true);

            assertFalse(manager.isRidingObject(player));
            assertFalse(player.isOnObject());
            assertFalse(player.getAir());
        } finally {
            GameModuleRegistry.setCurrent(previous);
        }
    }

    @Test
    public void testLandingFromAirRollOnObjectAdjustsYWhenUnrolling() {
        GameModule previous = GameModuleRegistry.getCurrent();
        GameModuleRegistry.setCurrent(new Sonic1GameModule());
        try {
            SolidObjectParams params = new SolidObjectParams(16, 8, 8);
            TestSolidObject object = new TestSolidObject(100, 100, params);
            ObjectManager manager = buildManager(object);

            TestPlayableSprite player = new TestPlayableSprite((short) 0, (short) 0);
            player.setWidth(20);
            player.setHeight(38);
            player.setAir(true);
            player.setYSpeed((short) 0x100);
            player.setRolling(true);

            // Within top landing window while rolling in air.
            player.setCentreX((short) 100);
            int rollingRadius = player.getYRadius(); // 14
            int distY = 8;
            int centerY = 100 - 4 - (params.groundHalfHeight() + rollingRadius) + distY;
            player.setCentreY((short) centerY);

            manager.updateSolidContacts(player);

            assertTrue(player.isOnObject());
            assertFalse(player.getAir());
            assertFalse(player.getRolling());

            int expectedStandingCenterY = 100 - params.groundHalfHeight() - 19 - 1;
            assertEquals(expectedStandingCenterY, player.getCentreY());
        } finally {
            GameModuleRegistry.setCurrent(previous);
        }
    }

    private static void setPrivateInt(Object instance, String fieldName, int value) throws Exception {
        Field field = instance.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setInt(instance, value);
    }

    private static void setPrivateBoolean(Object instance, String fieldName, boolean value) throws Exception {
        Field field = instance.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setBoolean(instance, value);
    }

    private TestPlayableSprite createStandingProbePlayer() {
        TestPlayableSprite player = new TestPlayableSprite((short) 0, (short) 0);
        player.setWidth(20);
        player.setHeight(20);
        player.setCentreX((short) 100);
        player.setCentreY((short) 83);
        player.setYSpeed((short) 0x100);
        player.setAir(true);
        return player;
    }

    private ObjectManager buildManager(ObjectInstance instance) {
        ObjectRegistry registry = new ObjectRegistry() {
            @Override
            public ObjectInstance create(ObjectSpawn spawn) {
                return instance;
            }

            @Override
            public void reportCoverage(List<ObjectSpawn> spawns) {
                // No-op for tests.
            }

            @Override
            public String getPrimaryName(int objectId) {
                return "TEST";
            }
        };

        ObjectManager objectManager = new ObjectManager(List.of(), registry, 0, null, null);
        objectManager.reset(0);
        objectManager.addDynamicObject(instance);
        return objectManager;
    }

    private static class TestSolidObject implements ObjectInstance, SolidObjectProvider {
        private final ObjectSpawn spawn;
        private final SolidObjectParams params;
        private final boolean topSolidOnly;
        private final Integer topLandingHalfWidth;
        private final boolean useCollisionHalfWidthForTopLanding;

        private TestSolidObject(int x, int y, SolidObjectParams params) {
            this(x, y, params, false, null, false);
        }

        private TestSolidObject(int x, int y, SolidObjectParams params, boolean topSolidOnly) {
            this(x, y, params, topSolidOnly, null, false);
        }

        private TestSolidObject(int x, int y, SolidObjectParams params, boolean topSolidOnly,
                Integer topLandingHalfWidth) {
            this(x, y, params, topSolidOnly, topLandingHalfWidth, false);
        }

        private TestSolidObject(int x, int y, SolidObjectParams params, boolean topSolidOnly,
                Integer topLandingHalfWidth, boolean useCollisionHalfWidthForTopLanding) {
            this.spawn = new ObjectSpawn(x, y, 0, 0, 0, false, 0);
            this.params = params;
            this.topSolidOnly = topSolidOnly;
            this.topLandingHalfWidth = topLandingHalfWidth;
            this.useCollisionHalfWidthForTopLanding = useCollisionHalfWidthForTopLanding;
        }

        @Override
        public ObjectSpawn getSpawn() {
            return spawn;
        }

        @Override
        public void update(int frameCounter, PlayableEntity player) {
            // No-op for tests.
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            // No-op for tests.
        }

        @Override
        public boolean isHighPriority() {
            return false;
        }

        @Override
        public boolean isDestroyed() {
            return false;
        }

        @Override
        public SolidObjectParams getSolidParams() {
            return params;
        }

        @Override
        public boolean isTopSolidOnly() {
            return topSolidOnly;
        }

        @Override
        public int getTopLandingHalfWidth(PlayableEntity player, int collisionHalfWidth) {
            return topLandingHalfWidth != null ? topLandingHalfWidth : collisionHalfWidth;
        }

        @Override
        public boolean usesCollisionHalfWidthForTopLanding() {
            return useCollisionHalfWidthForTopLanding;
        }
    }

    private static final class InclusiveRightEdgeSolidObject extends TestSolidObject {
        private InclusiveRightEdgeSolidObject(int x, int y, SolidObjectParams params) {
            super(x, y, params);
        }

        @Override
        public boolean usesInclusiveRightEdge() {
            return true;
        }
    }

    private static final class HistoryTopSolidObject extends TestSolidObject {
        private HistoryTopSolidObject(int x, int y, SolidObjectParams params) {
            super(x, y, params, true);
        }

        @Override
        public int getTopSolidPlayerPositionHistoryFrames(PlayableEntity player) {
            return 1;
        }
    }

    private static final class ManualCheckpointProbeObject extends AbstractObjectInstance
            implements SolidObjectProvider, SolidObjectListener {
        private final SolidObjectParams params = new SolidObjectParams(16, 8, 8);
        private boolean standingSeenInsideUpdate;
        private int manualCheckpointCount;
        private int compatibilityCallbackCount;

        private ManualCheckpointProbeObject(int x, int y) {
            super(new ObjectSpawn(x, y, 0, 0, 0, false, 0), "ManualCheckpointProbe");
        }

        @Override
        public SolidExecutionMode solidExecutionMode() {
            return SolidExecutionMode.MANUAL_CHECKPOINT;
        }

        @Override
        public SolidObjectParams getSolidParams() {
            return params;
        }

        @Override
        public void update(int frameCounter, PlayableEntity player) {
            PlayerSolidContactResult result = services().solidExecution().resolveSolidNow(player);
            standingSeenInsideUpdate = result.standingNow();
            manualCheckpointCount++;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            // No-op for tests.
        }

        @Override
        public boolean isHighPriority() {
            return false;
        }

        @Override
        public boolean isDestroyed() {
            return false;
        }

        @Override
        public boolean isSkipSolidContactThisFrame() {
            return false;
        }

        @Override
        public void onSolidContact(PlayableEntity player, SolidContact contact, int frameCounter) {
            compatibilityCallbackCount++;
        }
    }

    private static final class AutoCheckpointProbeObject extends AbstractObjectInstance
            implements SolidObjectProvider, SolidObjectListener {
        private final SolidObjectParams params = new SolidObjectParams(16, 8, 8);
        private int compatibilityCallbackCount;

        private AutoCheckpointProbeObject(int x, int y) {
            super(new ObjectSpawn(x, y, 0, 0, 0, false, 0), "AutoCheckpointProbe");
        }

        @Override
        public SolidObjectParams getSolidParams() {
            return params;
        }

        @Override
        public void update(int frameCounter, PlayableEntity player) {
            // No-op for tests.
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            // No-op for tests.
        }

        @Override
        public boolean isHighPriority() {
            return false;
        }

        @Override
        public boolean isDestroyed() {
            return false;
        }

        @Override
        public boolean isSkipSolidContactThisFrame() {
            return false;
        }

        @Override
        public void onSolidContact(PlayableEntity player, SolidContact contact, int frameCounter) {
            compatibilityCallbackCount++;
        }
    }

    private static final class FirstContactDisablesSolidityProbeObject extends AbstractObjectInstance
            implements SolidObjectProvider, SolidObjectListener {
        private final SolidObjectParams params = new SolidObjectParams(16, 8, 8);
        private boolean solid = true;
        private int compatibilityCallbackCount;
        private PlayableEntity firstCallbackPlayer;
        private boolean mainPlayerSawCallback;
        private boolean sidekickSawCallback;

        private FirstContactDisablesSolidityProbeObject(int x, int y) {
            super(new ObjectSpawn(x, y, 0, 0, 0, false, 0), "FirstContactDisablesSolidityProbe");
        }

        @Override
        public SolidObjectParams getSolidParams() {
            return params;
        }

        @Override
        public boolean isSolidFor(PlayableEntity player) {
            return solid;
        }

        @Override
        public void update(int frameCounter, PlayableEntity player) {
            // No-op for tests.
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            // No-op for tests.
        }

        @Override
        public boolean isHighPriority() {
            return false;
        }

        @Override
        public boolean isDestroyed() {
            return false;
        }

        @Override
        public boolean isSkipSolidContactThisFrame() {
            return false;
        }

        @Override
        public void onSolidContact(PlayableEntity player, SolidContact contact, int frameCounter) {
            compatibilityCallbackCount++;
            if (firstCallbackPlayer == null) {
                firstCallbackPlayer = player;
                solid = false;
                mainPlayerSawCallback = true;
            } else {
                sidekickSawCallback = true;
            }
        }
    }

    private static final class TestPlayableSprite extends AbstractPlayableSprite {
        private TestPlayableSprite(short x, short y) {
            super("TEST", x, y);
        }

        private void useFeatureSet(PhysicsFeatureSet fs) {
            setPhysicsFeatureSet(fs);
        }

        @Override
        protected void defineSpeeds() {
            runAccel = 0;
            runDecel = 0;
            friction = 0;
            max = 0;
            jump = 0;
            angle = 0;
            slopeRunning = 0;
            slopeRollingDown = 0;
            slopeRollingUp = 0;
            rollDecel = 0;
            minStartRollSpeed = 0;
            minRollSpeed = 0;
            maxRoll = 0;
            rollHeight = 28;
            runHeight = 38;
        }

        @Override
        protected void createSensorLines() {
            groundSensors = new Sensor[0];
            ceilingSensors = new Sensor[0];
            pushSensors = new Sensor[0];
        }

        @Override
        public void draw() {
            // No-op for tests.
        }
    }
}
