package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.LevelFrameStep;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.CnzCannonInstance;
import com.openggf.game.sonic3k.objects.CnzCylinderInstance;
import com.openggf.game.sonic3k.objects.CorkFloorObjectInstance;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Tails;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
public class TestS3kCnzDirectedTraversalHeadless {
    private static final int CANNON_X = 0x1600;
    private static final int CANNON_Y = 0x0680;

    @Test
    void cnzCannonCapturesForcesRollingAndLaunchesThePlayer() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();

        AbstractPlayableSprite player = fixture.sprite();
        // ROM Obj_CNZCannon (sonic3k.asm:66870) calls SolidObjectTop with
        // d1=$10 (half-width) and d3=$29 (top offset). MvSonicOnPtfm puts the
        // player feet at cannon.y - $29, so player centre Y must be
        // cannon.y - $29 - yRadius + small overlap to land standing.
        player.setCentreX((short) CANNON_X);
        player.setCentreY((short) (CANNON_Y - 0x29 - player.getYRadius() + 3));
        player.setAir(false);
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) 0);
        player.setRolling(false);
        player.setControlLocked(false);
        player.setObjectControlled(false);

        ObjectManager objectManager = GameServices.level().getObjectManager();
        CnzCannonInstance cannon = new CnzCannonInstance(new ObjectSpawn(
                CANNON_X, CANNON_Y, Sonic3kObjectIds.CNZ_CANNON, 0, 0, false, 0));
        objectManager.addDynamicObject(cannon);

        fixture.camera().updatePosition(true);

        boolean captured = false;
        for (int i = 0; i < 12; i++) {
            fixture.stepFrame(false, false, false, false, false);
            if (player.isObjectControlled()) {
                captured = true;
                break;
            }
        }

        assertTrue(captured, "CNZ cannon should capture the player before launching");
        assertTrue(player.isControlLocked(), "CNZ cannon should lock player control while captured");
        assertTrue(player.getRolling(), "CNZ cannon should force rolling state while captured");
        assertEquals(7, player.getXRadius(), "CNZ cannon should use the ROM rolling x-radius");
        assertEquals(14, player.getYRadius(), "CNZ cannon should use the ROM rolling y-radius");

        invokeLaunchDelayHook(cannon, 0);

        player.setJumpInputPressed(true);
        fixture.stepFrame(false, false, false, false, true);

        assertFalse(player.isObjectControlled(), "CNZ cannon should release object control after launch");
        assertFalse(player.isControlLocked(), "CNZ cannon should release control lock after launch");
        assertTrue(player.getXSpeed() != 0 || player.getYSpeed() != 0,
                "CNZ cannon should impart a launch vector");
    }

    @Test
    void cnzCylinderSubtype00UsesRomVerticalControllerInsteadOfGenericRouteMotion() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();

        AbstractPlayableSprite player = fixture.sprite();
        player.setCentreX((short) 0);
        player.setCentreY((short) 0);
        player.setAir(true);

        CnzCylinderInstance cylinder = new CnzCylinderInstance(new ObjectSpawn(
                0x38C0, 0x0800, Sonic3kObjectIds.CNZ_CYLINDER, 0x00, 0, false, 0));

        int startX = cylinder.getX();
        int startY = cylinder.getY();
        cylinder.onSolidContact(player, new SolidContact(true, false, false, false, true), 0);
        for (int frame = 0; frame < 8; frame++) {
            cylinder.update(frame, player);
        }

        assertEquals(startX, cylinder.getX(),
                "ROM mode 0 cylinder should not drift horizontally");
        assertTrue(cylinder.getY() != startY,
                "ROM mode 0 cylinder should move via its vertical velocity controller");
    }

    @Test
    void cnzCylinderMode0SeedsSpeedCapWithoutHotStartingLiveVelocity() throws Exception {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();

        CnzCylinderInstance cylinder = new CnzCylinderInstance(new ObjectSpawn(
                0x38C0, 0x0800, Sonic3kObjectIds.CNZ_CYLINDER, 0x00, 0, false, 0));

        assertEquals(0x04E0, getCylinderInt(cylinder, "speedCap"));
        assertEquals(0, getCylinderInt(cylinder, "mode0Velocity"),
                "ROM init stores the cap in $3E(a0) but leaves y_vel(a0) at rest");

        cylinder.update(0, fixture.sprite());

        assertEquals(0x38C0, getCylinderInt(cylinder, "centerX"));
    }

    @Test
    void cnzCylinderCircularSubtypeMutatesQuadrantsInsteadOfUsingFixedRouteSeed() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();

        AbstractPlayableSprite player = fixture.sprite();
        player.setCentreX((short) 0x38C0);
        player.setCentreY((short) 0x0800);
        player.setAir(true);

        CnzCylinderInstance cylinder = new CnzCylinderInstance(new ObjectSpawn(
                0x38C0, 0x0800, Sonic3kObjectIds.CNZ_CYLINDER, 0x49, 0, false, 0));

        int[] xs = new int[280];
        int[] ys = new int[280];
        for (int frame = 0; frame < 280; frame++) {
            cylinder.update(frame, player);
            xs[frame] = cylinder.getX();
            ys[frame] = cylinder.getY();
        }

        assertTrue(hasMovementOnBothSidesOfOrigin(xs, 0x38C0),
                "ROM circular cylinder should traverse multiple horizontal sides of the origin");
        assertTrue(hasMovementOnBothSidesOfOrigin(ys, 0x0800),
                "ROM circular cylinder should traverse multiple vertical sides of the origin");
        assertTrue(hasEdgeSegment(xs, ys, 0x38C0, 0x0800, true),
                "ROM circular cylinder should include a horizontal-edge segment");
        assertTrue(hasEdgeSegment(xs, ys, 0x38C0, 0x0800, false),
                "ROM circular cylinder should include a vertical-edge segment");
    }

    @Test
    void cnzCylinderCircularSubtypeFollowsSquareQuadrantTransitionsFromOff321Ee() throws Exception {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();

        CnzCylinderInstance cylinder = new CnzCylinderInstance(new ObjectSpawn(
                0x38C0, 0x0800, Sonic3kObjectIds.CNZ_CYLINDER, 0x49, 0, false, 0));

        int[] quadrants = new int[400];
        for (int frame = 0; frame < quadrants.length; frame++) {
            cylinder.update(frame, fixture.sprite());
            quadrants[frame] = getCylinderInt(cylinder, "routeQuadrant");
        }

        assertTrue(arrayContains(quadrants, 0));
        assertTrue(arrayContains(quadrants, 1));
        assertTrue(arrayContains(quadrants, 2));
        assertTrue(arrayContains(quadrants, 3),
                "Routines 9-12 should mutate $44(a0) through all square-route quadrants");
    }

    @Test
    void cnzCorkFloorUsesCurrentFramePreContactRollingCheckpoint() throws Exception {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();

        AbstractPlayableSprite player = fixture.sprite();
        player.setCentreX((short) 0x1180);
        player.setCentreY((short) 0x04EE);
        player.setAir(true);
        player.setRolling(true);
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0x0180);
        player.setGSpeed((short) 0);

        ObjectManager objectManager = GameServices.level().getObjectManager();
        CorkFloorObjectInstance floor = new CorkFloorObjectInstance(new ObjectSpawn(
                0x1180, 0x0520, Sonic3kObjectIds.CORK_FLOOR, 1, 0, false, 0));
        objectManager.addDynamicObject(floor);

        fixture.camera().updatePosition(true);
        fixture.stepFrame(false, false, false, false, false);

        assertTrue((boolean) getPrivateField(floor, "savedPreContactRolling"),
                "CNZ cork floor should capture the current-frame rolling state before contact resolution clears it");
        assertTrue((boolean) getPrivateField(floor, "broken"),
                "CNZ cork floor should use the current-frame checkpoint to break on the landing frame");
    }

    @Test
    void cnzCylinderCapturesPlayerAppliesRollingRadiiAndReleasesAtSubtypeExit() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();

        AbstractPlayableSprite player = fixture.sprite();
        player.setCentreX((short) 0x38C0);
        player.setCentreY((short) (0x0800 - 0x20 - player.getYRadius() + 3));
        player.setAir(false);
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) 0);
        player.setRolling(false);
        player.setControlLocked(false);
        player.setObjectControlled(false);

        ObjectManager objectManager = GameServices.level().getObjectManager();
        CnzCylinderInstance cylinder = new CnzCylinderInstance(new ObjectSpawn(
                0x38C0, 0x0800, Sonic3kObjectIds.CNZ_CYLINDER, 0x01, 0, false, 0));
        objectManager.addDynamicObject(cylinder);

        fixture.camera().updatePosition(true);

        boolean captured = false;
        for (int i = 0; i < 12; i++) {
            fixture.stepFrame(false, false, false, false, false);
            if (player.isObjectControlled()) {
                captured = true;
                break;
            }
        }

        assertTrue(captured, "CNZ cylinder should capture the player before traversal");
        assertTrue(player.isControlLocked(), "CNZ cylinder should lock player control while captured");
        // ROM sub_324C0 (sonic3k.asm:67985) at capture explicitly does
        // bclr #Status_Roll, status(a1) (line 68005) and writes
        // default_y_radius / default_x_radius to y_radius / x_radius
        // (lines 68003-68004). Rolling radii (7, 14) and Status_Roll are
        // only set when the rider jumps OFF the cylinder (loc_325F2 path,
        // lines 68062-68065).
        assertFalse(player.getRolling(),
                "CNZ cylinder must clear Status_Roll on capture per sub_324C0:68005");
        assertEquals(player.getStandXRadius(), player.getXRadius(),
                "CNZ cylinder must restore default_x_radius on capture per sub_324C0:68004");
        assertEquals(player.getStandYRadius(), player.getYRadius(),
                "CNZ cylinder must restore default_y_radius on capture per sub_324C0:68003");

        int capturedX = player.getCentreX();
        int capturedY = player.getCentreY();
        player.setAir(true);
        player.setCentreY((short) (player.getCentreY() - 0x20));

        boolean released = false;
        for (int frame = 0; frame < 3; frame++) {
            fixture.stepFrame(false, false, false, false, false);
            if (!player.isObjectControlled()) {
                released = true;
                break;
            }
        }

        assertTrue(released, "CNZ cylinder should release control once standing contact is lost");
        assertFalse(player.isControlLocked(), "CNZ cylinder should release control lock at route exit");
        assertFalse(player.isObjectControlled(), "CNZ cylinder should release object control at route exit");
        assertEquals(capturedX, player.getCentreX(), "CNZ cylinder should keep the rider on the same X line at release");
        assertTrue(player.getCentreY() <= capturedY - 0x20,
                "CNZ cylinder should release after the rider has been lifted clear of the standing contact");
    }

    @Test
    void cnzCylinderDrivesRomTwistFramesAndPlayerPriorityWhileCaptured() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();

        AbstractPlayableSprite player = fixture.sprite();
        prepareRiderForCylinderStanding(player, 0x38C0, 0x0800);

        ObjectManager objectManager = GameServices.level().getObjectManager();
        CnzCylinderInstance cylinder = new CnzCylinderInstance(new ObjectSpawn(
                0x38C0, 0x0800, Sonic3kObjectIds.CNZ_CYLINDER, 0x01, 0, false, 0));
        objectManager.addDynamicObject(cylinder);

        fixture.camera().updatePosition(true);

        boolean captured = false;
        for (int frame = 0; frame < 32; frame++) {
            fixture.stepFrame(false, false, false, false, false);
            if (player.isObjectControlled()) {
                captured = true;
                break;
            }
        }

        assertTrue(captured, "CNZ cylinder should capture the player before testing twist-frame parity");

        int[] twistFrames = new int[14];
        int[] priorities = new int[14];
        for (int frame = 0; frame < twistFrames.length; frame++) {
            fixture.stepFrame(false, false, false, false, false);
            twistFrames[frame] = player.getMappingFrame();
            priorities[frame] = player.getPriorityBucket();
            assertTrue(player.isObjectControlled(),
                    "CNZ cylinder should keep control while the rider stays on the cylinder");
        }

        assertEquals(0x55, twistFrames[0], "CNZ cylinder should start with the ROM twist frame table");
        assertTrue(hasMultipleValues(twistFrames),
                "CNZ cylinder should advance through multiple ROM twist frames while held");
        assertTrue(arrayContains(priorities, RenderPriority.PLAYER_DEFAULT - 1),
                "CNZ cylinder should lower the rider priority during part of the twist cycle");
        assertTrue(arrayContains(priorities, RenderPriority.PLAYER_DEFAULT),
                "CNZ cylinder should restore the default rider priority during part of the twist cycle");
    }

    @Test
    void cnzCylinderUsesTheExactPlayerTwistFramesTableWhileCaptured() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();

        AbstractPlayableSprite player = fixture.sprite();
        prepareRiderForCylinderStanding(player, 0x38C0, 0x0800);

        ObjectManager objectManager = GameServices.level().getObjectManager();
        CnzCylinderInstance cylinder = new CnzCylinderInstance(new ObjectSpawn(
                0x38C0, 0x0800, Sonic3kObjectIds.CNZ_CYLINDER, 0x01, 0, false, 0));
        objectManager.addDynamicObject(cylinder);

        fixture.camera().updatePosition(true);
        waitForCylinderCapture(fixture, player);

        int[] expected = new int[24];
        int[] actual = new int[expected.length];

        for (int frame = 0; frame < expected.length; frame++) {
            fixture.stepFrame(false, false, false, false, false);
            expected[frame] = expectedTwistFrame(frame);
            actual[frame] = player.getMappingFrame();
        }

        assertArrayEquals(expected, actual);
    }

    @Test
    void cnzCylinderDropsPriorityOnlyWhenThresholdByteFallsBelowObject35() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();

        AbstractPlayableSprite player = fixture.sprite();
        prepareRiderForCylinderStanding(player, 0x38C0, 0x0800);

        ObjectManager objectManager = GameServices.level().getObjectManager();
        CnzCylinderInstance cylinder = new CnzCylinderInstance(new ObjectSpawn(
                0x38C0, 0x0800, Sonic3kObjectIds.CNZ_CYLINDER, 0x01, 0, false, 0));
        objectManager.addDynamicObject(cylinder);

        fixture.camera().updatePosition(true);
        waitForCylinderCapture(fixture, player);

        int[] priorities = new int[12];
        for (int frame = 0; frame < priorities.length; frame++) {
            fixture.stepFrame(false, false, false, false, false);
            priorities[frame] = player.getPriorityBucket();
        }

        assertTrue(arrayContains(priorities, RenderPriority.PLAYER_DEFAULT));
        assertTrue(arrayContains(priorities, RenderPriority.PLAYER_DEFAULT - 1));
        assertEquals(RenderPriority.PLAYER_DEFAULT - 1, priorities[3],
                "The drop should occur only on the low-threshold half of the twist cycle");
    }

    @Test
    void cnzCylinderKeepsControlUntilThePlayerJumpsOut() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();

        AbstractPlayableSprite player = fixture.sprite();
        prepareRiderForCylinderStanding(player, 0x38C0, 0x0800);

        ObjectManager objectManager = GameServices.level().getObjectManager();
        CnzCylinderInstance cylinder = new CnzCylinderInstance(new ObjectSpawn(
                0x38C0, 0x0800, Sonic3kObjectIds.CNZ_CYLINDER, 0x01, 0, false, 0));
        objectManager.addDynamicObject(cylinder);

        fixture.camera().updatePosition(true);

        boolean captured = false;
        for (int frame = 0; frame < 32; frame++) {
            fixture.stepFrame(false, false, false, false, false);
            if (player.isObjectControlled()) {
                captured = true;
                break;
            }
        }

        assertTrue(captured, "CNZ cylinder should capture the player before testing the jump release");

        player.setJumpInputPressed(true);
        boolean released = false;
        for (int frame = 0; frame < 3; frame++) {
            fixture.stepFrame(false, false, false, false, true);
            if (!player.isObjectControlled()) {
                released = true;
                break;
            }
        }

        assertTrue(released, "CNZ cylinder should release control when the rider jumps out");
        assertFalse(player.isControlLocked(), "CNZ cylinder should unlock control after the jump release");
        assertTrue(player.isJumping(), "CNZ cylinder should mark the rider as jumping when they jump out");
        assertTrue(player.getAir(), "CNZ cylinder should release the rider into the air");
        assertTrue(player.getYSpeed() != 0, "CNZ cylinder should impart the ROM release Y speed");
    }

    @Test
    void cnzCylinderDoesNotCaptureFromSideContactWithoutStandingBit() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();

        AbstractPlayableSprite player = fixture.sprite();
        prepareRiderForCylinder(player, 0x38E8, 0x0800);

        CnzCylinderInstance cylinder = new CnzCylinderInstance(new ObjectSpawn(
                0x38C0, 0x0800, Sonic3kObjectIds.CNZ_CYLINDER, 0x01, 0, false, 0));

        cylinder.onSolidContact(player, new SolidContact(false, true, false, false, true), 0);
        cylinder.update(0, player);

        assertFalse(player.isObjectControlled(),
                "The slot-init branch must stay closed when btst d6,status(a0) would fail");
    }

    @Test
    void cnzCylinderMaintainsIndependentRiderStateForPlayerAndSidekick() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();

        AbstractPlayableSprite player = fixture.sprite();
        AbstractPlayableSprite sidekick = ensureCnzSidekick();

        prepareRiderForCylinderStanding(player, 0x38C0, 0x0800);
        prepareRiderForCylinderStanding(sidekick, 0x38D0, 0x0800);

        ObjectManager objectManager = GameServices.level().getObjectManager();
        CnzCylinderInstance cylinder = new CnzCylinderInstance(new ObjectSpawn(
                0x38C0, 0x0800, Sonic3kObjectIds.CNZ_CYLINDER, 0x01, 0, false, 0));
        objectManager.addDynamicObject(cylinder);

        fixture.camera().updatePosition(true);

        boolean captured = false;
        for (int frame = 0; frame < 32; frame++) {
            stepDualRiderFrame(frame + 1, player, sidekick);
            if (player.isObjectControlled() && sidekick.isObjectControlled()) {
                captured = true;
                break;
            }
        }

        assertTrue(captured, "CNZ cylinder should capture both riders before testing rider-state parity");
        assertTrue(player.isControlLocked(), "CNZ cylinder should lock player control while captured");
        assertTrue(sidekick.isControlLocked(), "CNZ cylinder should lock sidekick control while captured");
        // ROM sub_324C0 (sonic3k.asm:67985) at capture explicitly does
        // bclr #Status_Roll, status(a1) (line 68005) and writes
        // default_y_radius / default_x_radius (lines 68003-68004).
        assertFalse(player.getRolling(),
                "CNZ cylinder must clear Status_Roll on capture per sub_324C0:68005 (player)");
        assertFalse(sidekick.getRolling(),
                "CNZ cylinder must clear Status_Roll on capture per sub_324C0:68005 (sidekick)");
        assertEquals(player.getStandXRadius(), player.getXRadius(),
                "CNZ cylinder must restore default_x_radius on capture per sub_324C0:68004 (player)");
        assertEquals(sidekick.getStandXRadius(), sidekick.getXRadius(),
                "CNZ cylinder must restore default_x_radius on capture per sub_324C0:68004 (sidekick)");
        assertEquals(player.getStandYRadius(), player.getYRadius(),
                "CNZ cylinder must restore default_y_radius on capture per sub_324C0:68003 (player)");
        assertEquals(sidekick.getStandYRadius(), sidekick.getYRadius(),
                "CNZ cylinder must restore default_y_radius on capture per sub_324C0:68003 (sidekick)");
        assertFalse(player.getCentreX() == sidekick.getCentreX()
                        && player.getCentreY() == sidekick.getCentreY(),
                "CNZ cylinder should preserve distinct world-space rider positions instead of collapsing both riders together");
    }

    @Test
    void cnzCylinderReleasesWhenStandingContactIsLostWithoutJumpInput() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();

        AbstractPlayableSprite player = fixture.sprite();
        prepareRiderForCylinderStanding(player, 0x38C0, 0x0800);

        ObjectManager objectManager = GameServices.level().getObjectManager();
        CnzCylinderInstance cylinder = new CnzCylinderInstance(new ObjectSpawn(
                0x38C0, 0x0800, Sonic3kObjectIds.CNZ_CYLINDER, 0x01, 0, false, 0));
        objectManager.addDynamicObject(cylinder);

        fixture.camera().updatePosition(true);

        boolean captured = false;
        for (int frame = 0; frame < 32; frame++) {
            fixture.stepFrame(false, false, false, false, false);
            if (player.isObjectControlled()) {
                captured = true;
                break;
            }
        }

        assertTrue(captured, "CNZ cylinder should capture the player before testing the release path");

        player.setAir(true);
        player.setCentreY((short) (player.getCentreY() - 0x20));
        player.setJumpInputPressed(false);

        boolean released = false;
        for (int frame = 0; frame < 3; frame++) {
            fixture.stepFrame(false, false, false, false, false);
            if (!player.isObjectControlled()) {
                released = true;
                break;
            }
        }

        assertTrue(released,
                "CNZ cylinder should release object control once standing contact is lost without jump input");
        assertFalse(player.isObjectControlled(),
                "CNZ cylinder should release object control once standing contact is lost without jump input");
    }

    @Test
    void cnzCylinderStandingLossClearsSlotWithoutJumpSetup() throws Exception {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();

        AbstractPlayableSprite player = fixture.sprite();
        prepareRiderForCylinderStanding(player, 0x38C0, 0x0800);

        ObjectManager objectManager = GameServices.level().getObjectManager();
        CnzCylinderInstance cylinder = new CnzCylinderInstance(new ObjectSpawn(
                0x38C0, 0x0800, Sonic3kObjectIds.CNZ_CYLINDER, 0x01, 0, false, 0));
        objectManager.addDynamicObject(cylinder);
        fixture.camera().updatePosition(true);

        waitForCylinderCapture(fixture, player);

        player.setJumpInputPressed(false);
        player.setAir(true);
        player.setCentreY((short) (player.getCentreY() - 0x20));
        fixture.stepFrame(false, false, false, false, false);

        assertFalse(player.isObjectControlled());
        assertFalse(player.isJumping());
        assertEquals(0, player.getYSpeed());
        assertFalse(isPlayerOneSlotActive(cylinder));
    }

    @Test
    void cnzCylinderJumpReleaseClearsSlotOnTheJumpFrame() throws Exception {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();

        AbstractPlayableSprite player = fixture.sprite();
        prepareRiderForCylinderStanding(player, 0x38C0, 0x0800);

        ObjectManager objectManager = GameServices.level().getObjectManager();
        CnzCylinderInstance cylinder = new CnzCylinderInstance(new ObjectSpawn(
                0x38C0, 0x0800, Sonic3kObjectIds.CNZ_CYLINDER, 0x01, 0, false, 0));
        objectManager.addDynamicObject(cylinder);
        fixture.camera().updatePosition(true);

        waitForCylinderCapture(fixture, player);

        player.setJumpInputPressed(true);
        fixture.stepFrame(false, false, false, false, true);

        assertTrue(player.isJumping());
        assertTrue(player.getAir());
        assertTrue(player.getYSpeed() < 0);
        assertFalse(player.isObjectControlled());
        assertFalse(isPlayerOneSlotActive(cylinder));
    }

    @Test
    void cnzCylinderForcedReleaseClearsInvalidRiderStateWithoutUsingTheJumpPath() throws Exception {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();

        AbstractPlayableSprite player = fixture.sprite();
        prepareRiderForCylinderStanding(player, 0x38C0, 0x0800);

        ObjectManager objectManager = GameServices.level().getObjectManager();
        CnzCylinderInstance cylinder = new CnzCylinderInstance(new ObjectSpawn(
                0x38C0, 0x0800, Sonic3kObjectIds.CNZ_CYLINDER, 0x01, 0, false, 0));
        objectManager.addDynamicObject(cylinder);
        fixture.camera().updatePosition(true);

        waitForCylinderCapture(fixture, player);

        player.setHurt(true);
        fixture.stepFrame(false, false, false, false, false);

        assertFalse(player.isObjectControlled());
        assertFalse(player.isJumping());
        assertEquals(0, player.getYSpeed());
        assertFalse(isPlayerOneSlotActive(cylinder));
    }

    private static void invokeLaunchDelayHook(CnzCannonInstance cannon, int frames) {
        try {
            java.lang.reflect.Method method = CnzCannonInstance.class.getDeclaredMethod("setLaunchDelayFramesForTest", int.class);
            method.setAccessible(true);
            method.invoke(cannon, frames);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to set CNZ cannon launch delay for test", e);
        }
    }

    private static Object getPrivateField(Object target, String fieldName) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static int getCylinderInt(CnzCylinderInstance cylinder, String fieldName) throws Exception {
        java.lang.reflect.Field field = CnzCylinderInstance.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(cylinder);
    }

    private static boolean isPlayerOneSlotActive(CnzCylinderInstance cylinder) throws Exception {
        java.lang.reflect.Field slotField = CnzCylinderInstance.class.getDeclaredField("playerOneSlot");
        slotField.setAccessible(true);
        Object slot = slotField.get(cylinder);
        java.lang.reflect.Field activeField = slot.getClass().getDeclaredField("active");
        activeField.setAccessible(true);
        return activeField.getBoolean(slot);
    }

    private static void prepareRiderForCylinder(AbstractPlayableSprite rider, int x, int y) {
        rider.setCentreX((short) x);
        rider.setCentreY((short) y);
        rider.setAir(false);
        rider.setXSpeed((short) 0);
        rider.setYSpeed((short) 0);
        rider.setGSpeed((short) 0);
        rider.setRolling(false);
        rider.setControlLocked(false);
        rider.setObjectControlled(false);
        rider.setJumpInputPressed(false);
    }

    private static void prepareRiderForCylinderStanding(AbstractPlayableSprite rider, int x, int cylinderY) {
        int standingY = cylinderY - 0x20 - rider.getYRadius() + 3;
        prepareRiderForCylinder(rider, x, standingY);
    }

    private static void waitForCylinderCapture(HeadlessTestFixture fixture, AbstractPlayableSprite player) {
        boolean captured = false;
        for (int frame = 0; frame < 32; frame++) {
            fixture.stepFrame(false, false, false, false, false);
            if (player.isObjectControlled()) {
                captured = true;
                break;
            }
        }
        assertTrue(captured, "Cylinder should capture the rider before exercising release branches");
    }

    private static int expectedTwistFrame(int updateIndex) {
        int[] table = {0x55, 0x59, 0x5A, 0x5B, 0x5A, 0x59, 0x55, 0x56, 0x57, 0x58, 0x57, 0x56};
        int twistAngle = (updateIndex * 2) & 0xFF;
        int frameIndex = ((twistAngle + 0x0B) & 0xFF) / 0x16;
        return table[frameIndex];
    }

    private static void stepDualRiderFrame(int frameCounter,
                                           AbstractPlayableSprite player,
                                           AbstractPlayableSprite sidekick) {
        player.setJumpInputPressed(false);
        player.setDirectionalInputPressed(false, false, false, false);
        sidekick.setJumpInputPressed(false);
        sidekick.setDirectionalInputPressed(false, false, false, false);

        LevelFrameStep.execute(GameServices.level(), GameServices.camera(), () -> {
            SpriteManager.tickPlayablePhysics(player, false, false, false, false, false,
                    false, false, false, GameServices.level(), frameCounter);
            SpriteManager.tickPlayablePhysics(sidekick, false, false, false, false, false,
                    false, false, false, GameServices.level(), frameCounter);
        });
    }

    private static AbstractPlayableSprite ensureCnzSidekick() {
        if (!GameServices.sprites().getSidekicks().isEmpty()) {
            return GameServices.sprites().getSidekicks().getFirst();
        }

        Tails sidekick = new Tails("cnz_sidekick", (short) 0x38C0, (short) 0x0800);
        prepareRiderForCylinderStanding(sidekick, 0x38C0, 0x0800);
        sidekick.setCpuControlled(true);
        GameServices.sprites().addSprite(sidekick, "tails");
        return sidekick;
    }

    private static boolean hasEdgeSegment(int[] xs, int[] ys, int originX, int originY, boolean horizontalEdge) {
        for (int i = 0; i < xs.length; i++) {
            int dx = Math.abs(xs[i] - originX);
            int dy = Math.abs(ys[i] - originY);
            if (horizontalEdge) {
                if (dx >= 30 && dy < 30) {
                    return true;
                }
            } else if (dy >= 30 && dx < 30) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasMovementOnBothSidesOfOrigin(int[] values, int origin) {
        boolean below = false;
        boolean above = false;
        for (int value : values) {
            if (value < origin) {
                below = true;
            } else if (value > origin) {
                above = true;
            }
        }
        return below && above;
    }

    private static boolean hasMultipleValues(int[] values) {
        for (int i = 1; i < values.length; i++) {
            if (values[i] != values[0]) {
                return true;
            }
        }
        return false;
    }

    private static boolean arrayContains(int[] values, int expected) {
        for (int value : values) {
            if (value == expected) {
                return true;
            }
        }
        return false;
    }
}
