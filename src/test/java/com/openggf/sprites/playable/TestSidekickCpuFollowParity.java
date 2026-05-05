package com.openggf.sprites.playable;

import com.openggf.game.EngineServices;
import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.PhysicsFeatureSet;
import com.openggf.game.RuntimeManager;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.physics.Direction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSidekickCpuFollowParity {

    @BeforeEach
    void configureRuntime() {
        RuntimeManager.configureEngineServices(EngineServices.fromLegacySingletonsForBootstrap());
        RuntimeManager.createGameplay();
    }

    @AfterEach
    void tearDownRuntime() {
        RuntimeManager.destroyCurrent();
    }

    static class TestableSprite extends AbstractPlayableSprite {
        TestableSprite(String code) {
            super(code, (short) 0, (short) 0);
        }

        @Override
        public void draw() {}

        @Override
        public void defineSpeeds() {}

        @Override
        protected void createSensorLines() {}

        void setPhysicsFeatureSetForTest(PhysicsFeatureSet featureSet) {
            setPhysicsFeatureSet(featureSet);
        }
    }

    @Test
    void followRightStillNudgesPositionWhenDxIsBelowThreshold() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 25);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 16);

        tails.setX((short) 10);
        tails.setDirection(Direction.RIGHT);
        tails.setGSpeed((short) 0x0100);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

        controller.update(1);

        assertEquals(11, tails.getX(),
            "ROM follow-right nudges x_pos by +1 even when |dx| < 16");
        assertFalse(controller.getInputLeft(),
            "ROM only overrides left/right input at |dx| >= 16");
        assertFalse(controller.getInputRight(),
            "ROM only overrides left/right input at |dx| >= 16");
    }

    @Test
    void followNudgeIsSuppressedWhileObjectControlBitZeroIsSet() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 25);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 16);

        tails.setX((short) 10);
        tails.setDirection(Direction.RIGHT);
        tails.setGSpeed((short) 0x0100);
        tails.setObjectControlled(true);
        tails.setObjectControlAllowsCpu(true);
        tails.setObjectControlSuppressesMovement(true);
        tails.setControlLocked(false);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

        controller.update(1);

        assertEquals(10, tails.getX(),
                "ROM loc_13E34 gates the +/-1 follow nudge on object_control bit 0");
    }

    @Test
    void followNudgeStillRunsForNonBitZeroObjectControl() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 25);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 16);

        tails.setX((short) 10);
        tails.setDirection(Direction.RIGHT);
        tails.setGSpeed((short) 0x0100);
        tails.setObjectControlled(true);
        tails.setObjectControlAllowsCpu(true);
        tails.setObjectControlSuppressesMovement(false);
        tails.setControlLocked(false);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

        controller.update(1);

        assertEquals(11, tails.getX(),
                "ROM only blocks the follow nudge when object_control bit 0 is set");
    }

    @Test
    void s3kLeadOffsetStillAppliesWhenLeaderStandingObjectReferenceIsAirborne() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x1976);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 16);
        sonic.setOnObject(true);
        sonic.setAir(true);
        sonic.setGSpeed((short) 0);

        tails.setCentreXPreserveSubpixel((short) 0x1966);
        tails.setDirection(Direction.RIGHT);
        tails.setGSpeed((short) 0x00EC);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

        controller.update(0x06C4);

        assertEquals(0x1966, tails.getCentreX() & 0xFFFF,
                "S3K loc_13DA6 gates the $20 lead offset on Status_OnObj, not a stale standing-object reference");
    }

    @Test
    void delayedCentreHistoryDoesNotDependOnCurrentHitboxHeight() {
        TestableSprite sonic = new TestableSprite("sonic");

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x0100);
        Arrays.fill(yHistory, (short) 0x0200);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 16);

        sonic.setRolling(true);

        assertEquals(0x0100, sonic.getCentreX(16) & 0xFFFF,
            "Historical centre X should remain ROM-accurate after a size change");
        assertEquals(0x0200, sonic.getCentreY(16) & 0xFFFF,
            "Historical centre Y should remain ROM-accurate after a size change");
    }

    @Test
    void panicDoesNotHoldDownOrRefacingUntilGroundSpeedStops() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setDirection(Direction.LEFT);
        tails.setGSpeed((short) 0x0200);

        sonic.setCentreX((short) 0x0200);
        tails.setCentreX((short) 0x0100);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.forceStateForTest(SidekickCpuController.State.PANIC, 0);

        controller.update(5);

        assertFalse(controller.getInputDown(),
                "ROM TailsCPU_Panic does not press down until inertia reaches zero");
        assertFalse(controller.getInputJump(),
                "ROM TailsCPU_Panic does not start revving while Tails is still moving");
        assertSame(Direction.LEFT, tails.getDirection(),
                "ROM TailsCPU_Panic does not reface toward Sonic until inertia reaches zero");
        assertSame(SidekickCpuController.State.PANIC, controller.getState());
    }

    @Test
    void inputHistoryRecordsLogicalInputRatherThanRawHeldButtons() {
        TestableSprite sonic = new TestableSprite("sonic");

        sonic.setDirectionalInputPressed(false, false, true, false);
        sonic.setJumpInputPressed(false);
        sonic.setLogicalInputState(false, false, false, true, false);

        sonic.endOfTick();

        assertEquals(AbstractPlayableSprite.INPUT_RIGHT, sonic.getInputHistory(0) & 0xFFFF,
                "ROM Sonic_RecordPos stores Ctrl_1_Logical, so forced-right walkoff must record RIGHT");
    }

    @Test
    void releasedAizIntroMarkerSuppressesFirstNormalMovementPulse() throws Exception {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setCentreX((short) 0x138A);
        tails.setCentreY((short) 0x041F);
        tails.setGSpeed((short) 0);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x138A);
        Arrays.fill(yHistory, (short) 0x041F);
        int historyPos = 20;
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, historyPos);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.setInitialState(SidekickCpuController.State.DORMANT_MARKER);
        controller.releaseAizIntroDormantMarker();
        tails.setControlLocked(false);
        tails.setObjectControlled(false);
        setControllerState(controller, SidekickCpuController.State.NORMAL);

        controller.update(0x0483);

        Assertions.assertAll(
                () -> assertFalse(controller.getInputRight(),
                        "the release-side object frame can run before the delayed RIGHT pulse is visible"),
                () -> assertTrue(controller.consumeSkipPhysicsThisFrame(),
                        "AIZ1 release suppresses movement/physics for the release-side object frame"));

        inputHistory[historyPos - 16] = AbstractPlayableSprite.INPUT_RIGHT;
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, historyPos);

        controller.update(0x0484);

        Assertions.assertAll(
                () -> assertTrue(controller.getInputRight(),
                        "ROM loc_13DD0 generates RIGHT again on the next object frame"),
                () -> assertFalse(controller.consumeSkipPhysicsThisFrame(),
                        "only the release-side object frame is suppressed; frame 1445 consumes "
                                + "the freshly generated follow input normally"));
    }

    @Test
    void releasedAizIntroMarkerConsumesSuppressionOnFlightToNormalTransition() throws Exception {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        sonic.setCentreX((short) 0x138A);
        sonic.setCentreY((short) 0x041F);
        tails.setCentreX((short) 0x138A);
        tails.setCentreY((short) 0x041F);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x138A);
        Arrays.fill(yHistory, (short) 0x041F);
        int historyPos = 20;
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, historyPos);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.setInitialState(SidekickCpuController.State.DORMANT_MARKER);
        controller.releaseAizIntroDormantMarker();
        setControllerState(controller, SidekickCpuController.State.FLIGHT_AUTO_RECOVERY);

        controller.update(0x05A4);

        Assertions.assertAll(
                () -> assertFalse(controller.getInputRight(),
                        "flight recovery transition does not run normal follow AI on the handoff tick"),
                () -> assertFalse(controller.consumeSkipPhysicsThisFrame(),
                        "ROM still runs the normal airborne gravity step on the flight-to-normal handoff"));

        inputHistory[historyPos - 16] = AbstractPlayableSprite.INPUT_RIGHT;
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, historyPos);

        controller.update(0x05A5);

        Assertions.assertAll(
                () -> assertTrue(controller.getInputRight(),
                        "the first normal tick after the handoff sees the delayed RIGHT pulse"),
                () -> assertFalse(controller.consumeSkipPhysicsThisFrame(),
                        "suppression must not leak into the first normal movement tick"));
    }

    @Test
    void normalPushBypassPreservesDelayedControlWordWhileTestingPushStatus() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setAir(false);
        tails.setPushing(true);
        tails.setCentreX((short) 0x1CED);
        tails.setCentreY((short) 0x03C0);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x1CED);
        Arrays.fill(yHistory, (short) 0x03C0);
        int historyPos = 20;
        inputHistory[3] = 0;
        inputHistory[4] = AbstractPlayableSprite.INPUT_RIGHT;
        statusHistory[3] = AbstractPlayableSprite.STATUS_ON_OBJECT;
        statusHistory[4] = AbstractPlayableSprite.STATUS_PUSHING;
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, historyPos);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

        controller.update(0x0971);

        Assertions.assertAll(
                () -> assertFalse(controller.getInputLeft()),
                () -> assertEquals(true, controller.getInputRight(),
                        "ROM loc_13DD0 tests the delayed status byte in d4, but preserves the already-loaded "
                                + "Ctrl_2 word in d1 when it branches to loc_13E9C "
                                + "(sonic3k.asm:26696-26705,26775-26785; s2.asm:38939-38946)"));
    }

    @Test
    void normalPushBypassKeepsRomPushBitAcrossIsolatedEngineClearOnJumpCadence() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setAir(false);
        tails.setPushing(true);
        tails.setCentreX((short) 0x1CED);
        tails.setCentreY((short) 0x03C0);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x1D40);
        Arrays.fill(yHistory, (short) 0x03C0);
        Arrays.fill(statusHistory, AbstractPlayableSprite.STATUS_ON_OBJECT);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

        controller.update(0x097F);
        tails.setPushing(false);
        controller.update(0x0980);

        Assertions.assertAll(
                () -> assertTrue(controller.getInputJump(),
                        "ROM loc_13DD0 tests Tails' current Status_Push before loc_13E9C's frame gate"),
                () -> assertTrue(controller.getInputJumpPress(),
                        "AIZ frame 2721 reaches loc_13E9C at Level_frame_counter=0x0980 even when the engine "
                                + "has an isolated cleared push flag on that CPU tick"));
    }

    @Test
    void normalPushGraceSuppressesGroundedFollowPulseInsideAizObjectBand() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setAir(false);
        tails.setCentreX((short) 0x1CED);
        tails.setCentreY((short) 0x03C0);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x1D40);
        Arrays.fill(yHistory, (short) 0x03C0);
        Arrays.fill(statusHistory, AbstractPlayableSprite.STATUS_ON_OBJECT);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);
        sonic.setOnObject(true);
        sonic.setAir(false);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

        tails.setPushing(true);
        controller.update(0x0971);
        tails.setPushing(false);
        controller.update(0x0972);

        Assertions.assertAll(
                () -> assertFalse(controller.getInputLeft()),
                () -> assertFalse(controller.getInputRight(),
                        "S3K loc_13DD0 (sonic3k.asm:26702-26705) bypasses follow steering only while "
                                + "Status_Push is currently set; AIZ object ordering keeps the engine-side "
                                + "bridge active only while the vertical delta is still in the local object band"),
                () -> assertFalse(controller.getInputJump(),
                        "Non-cadence bridge frames should not force loc_13E9C's jump press"));
    }

    @Test
    void groundedPushGracePreservesCurrentDelayedControlWord() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setAir(false);
        tails.setCentreX((short) 0x1CE0);
        tails.setCentreY((short) 0x03C0);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x1D20);
        Arrays.fill(yHistory, (short) 0x03C0);
        int historyPos = 20;
        inputHistory[3] = 0;
        inputHistory[4] = AbstractPlayableSprite.INPUT_RIGHT;
        statusHistory[3] = 0;
        statusHistory[4] = 0;
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, historyPos);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

        tails.setPushing(true);
        controller.update(0x13DE);
        tails.setPushing(false);
        controller.update(0x13DF);

        assertTrue(controller.getInputRight(),
                "CNZ grounded release mirrors ROM loc_13DD0: d4 tests the delayed Status_Push byte, "
                        + "but the already-loaded d1 Ctrl_2 word is preserved for the cylinder/P2 and "
                        + "Tails_InputAcceleration_Path paths (sonic3k.asm:26696-26705,26775-26785,"
                        + "67656-67672,27798-27805,28103-28122)");
    }

    @Test
    void groundedPushGraceUsesCurrentControlWordOutsideAizObjectOrderBridge() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setAir(false);
        tails.setCentreX((short) 0x1CED);
        tails.setCentreY((short) 0x03C0);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x1D40);
        Arrays.fill(yHistory, (short) 0x03C0);
        int historyPos = 20;
        inputHistory[3] = 0;
        inputHistory[4] = AbstractPlayableSprite.INPUT_RIGHT;
        statusHistory[3] = AbstractPlayableSprite.STATUS_ON_OBJECT;
        statusHistory[4] = AbstractPlayableSprite.STATUS_ON_OBJECT;
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, historyPos);
        sonic.setOnObject(true);
        sonic.setAir(false);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

        tails.setPushing(true);
        controller.update(0x0971);
        tails.setPushing(false);
        controller.update(0x0972);

        assertTrue(controller.getInputRight(),
                "Outside the AIZ object-order bridge, S3K loc_13DD0 keeps the already-loaded d1 "
                        + "Ctrl_2 word even when the delayed status has Status_OnObj. This fixture's "
                        + "current sample carries RIGHT; MGZ F1466 is the companion case where d1 is "
                        + "zero and the older RIGHT sample must not be re-read (sonic3k.asm:"
                        + "26696-26705,26775-26785).");
    }

    @Test
    void s3kClearedPushGraceStillAllowsGroundedFollowRightInput() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setAir(false);
        tails.setObjectControlled(false);
        tails.setCentreX((short) 0x1F35);
        tails.setCentreY((short) 0x049D);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x1F85);
        Arrays.fill(yHistory, (short) 0x038B);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

        tails.setPushing(true);
        controller.update(0x0AE0);
        tails.setPushing(false);
        controller.update(0x0AE1);
        tails.setPushing(false);
        controller.update(0x0AE2);

        assertTrue(controller.getInputRight(),
                "At AIZ F3075, Tails' current Status_Push is clear, so ROM loc_13DD0 "
                        + "(sonic3k.asm:26702-26705) falls through to FollowRight; "
                        + "Tails_InputAcceleration_Path then consumes Ctrl_2_logical RIGHT "
                        + "and adds Acceleration_P2=$000C (sonic3k.asm:27798-27805,"
                        + "28103-28122)");
    }

    @Test
    void s3kFarTargetPushGraceDoesNotBypassAutoJumpHeightAndDistanceGates() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setAir(false);
        tails.setObjectControlled(false);
        tails.setCentreX((short) 0x1FB5);
        tails.setCentreY((short) 0x0480);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x2235);
        Arrays.fill(yHistory, (short) 0x038C);
        Arrays.fill(inputHistory, (short) AbstractPlayableSprite.INPUT_RIGHT);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

        tails.setPushing(true);
        controller.update(0x0B3F);
        tails.setPushing(false);
        controller.update(0x0B40);

        Assertions.assertAll(
                () -> assertFalse(controller.getInputJump(),
                        "AIZ F3169 has only the engine-side push grace left while dy is outside "
                                + "the local object band; ROM does not branch from loc_13DD0 to "
                                + "loc_13E9C and must still pass the distance/height gates "
                                + "(sonic3k.asm:26702-26705,26760-26783)"),
                () -> assertFalse(controller.getInputJumpPress(),
                        "ROM keeps Tails grounded here instead of applying Tails_Jump's -$680 y_vel"),
                () -> assertTrue(controller.getInputRight(),
                        "With no push-bypass autojump, FollowRight leaves Ctrl_2_logical RIGHT and "
                                + "grounded Tails movement consumes Acceleration_P2=$000C "
                                + "(sonic3k.asm:27798-27805,28103-28122)"));
    }

    @Test
    void normalPushBypassAutoJumpAllowsFirstAirborneFollowSteeringOutsideAizBridge() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setAir(false);
        tails.setPushing(true);
        tails.setCentreX((short) 0x1CED);
        tails.setCentreY((short) 0x03C0);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x1D40);
        Arrays.fill(yHistory, (short) 0x03C0);
        Arrays.fill(statusHistory, AbstractPlayableSprite.STATUS_ON_OBJECT);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

        controller.update(0x0980);
        Assertions.assertAll(
                () -> assertTrue(controller.getInputJump(),
                        "ROM loc_13DD0 reaches loc_13E9C on the push-bypass cadence"),
                () -> assertTrue(controller.getInputJumpPress(),
                        "F2721 writes the jump press before Tails enters the rolling airborne routine"));

        tails.setAir(true);
        tails.setRolling(true);
        tails.setPushing(false);
        controller.update(0x0981);

        Assertions.assertAll(
                () -> assertFalse(controller.getInputLeft()),
                () -> assertTrue(controller.getInputRight(),
                        "Outside the AIZ object-order bridge, the first airborne tick after a push-bypass "
                                + "jump resumes follow steering immediately. MGZ1 F1472 carries RIGHT "
                                + "(input=7808) and Tails_InputAcceleration_Freespace applies +$18 x_vel "
                                + "(sonic3k.asm:26712-26741,28330-28401)."),
                () -> assertTrue(controller.getInputJump(),
                        "The held jump from loc_13E9C remains live during the one-frame airborne handoff"));

        controller.update(0x0982);

        assertTrue(controller.getInputRight(),
                "Follow steering should remain live on the next airborne tick as well");
    }

    @Test
    void normalAutoJumpCadenceUsesInlineFrameCounterForS3kObjectOrder() throws Exception {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setAir(false);
        tails.setCentreX((short) 0x1964);
        tails.setCentreY((short) 0x041E);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x1956);
        Arrays.fill(yHistory, (short) 0x03ED);
        Arrays.fill(inputHistory, (short) AbstractPlayableSprite.INPUT_JUMP);
        Arrays.fill(statusHistory, (byte) 0x06);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 16);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);
        controller.update(0x06C0);

        Assertions.assertAll(
                () -> assertEquals(true, controller.getInputJump(),
                        "ROM loc_13E9C holds A/B/C when Level_frame_counter low bits are zero"),
                () -> assertEquals(true, controller.getInputJumpPress(),
                        "ROM loc_13E9C writes Ctrl_2_logical on the Level_frame_counter cadence"));
    }

    @Test
    void normalAutoJumpCadenceUsesLevelFrameCounterWithoutInlinePlusOne() throws Exception {
        GameModule previous = GameModuleRegistry.getBootstrapDefault();
        try {
            GameModuleRegistry.setCurrent(new Sonic3kGameModule());

            TestableSprite sonic = new TestableSprite("sonic");
            TestableSprite tails = new TestableSprite("tails_p2");
            tails.setCpuControlled(true);
            tails.setAir(false);
            tails.setCentreX((short) 0x1964);
            tails.setCentreY((short) 0x041E);

            short[] xHistory = new short[64];
            short[] yHistory = new short[64];
            short[] inputHistory = new short[64];
            byte[] statusHistory = new byte[64];
            Arrays.fill(xHistory, (short) 0x1964);
            Arrays.fill(yHistory, (short) 0x03ED);
            sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 16);

            SidekickCpuController controller = new SidekickCpuController(tails, sonic);
            tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);
            controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

            setLevelFrameCounter(0x06BF);
            controller.update(0x06C0);
            assertFalse(controller.getInputJumpPress(),
                    "S3K inline player-slot CPU must read Level_frame_counter=$06BF here; "
                            + "falling back to frameCounter=$06C0 jumps one frame early");

            setLevelFrameCounter(0x06C0);
            controller.update(0x06C1);
            assertTrue(controller.getInputJumpPress(),
                    "ROM loc_13E9C fires when Level_frame_counter reaches the $40 cadence");
        } finally {
            GameModuleRegistry.setCurrent(previous);
        }
    }

    @Test
    void s3kCatchUpFlightOnlyBlocksOnLeaderObjectControlSignBit() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);

        sonic.setCentreX((short) 0x0B78);
        sonic.setCentreY((short) 0x0325);
        sonic.setObjectControlled(true);
        sonic.setObjectControlAllowsCpu(true);

        tails.setCentreX((short) 0x7F00);
        tails.setCentreY((short) 0x0000);
        tails.setXSpeed((short) 0x0445);
        tails.setGSpeed((short) 0x0445);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.forceStateForTest(SidekickCpuController.State.CATCH_UP_FLIGHT, 0);

        controller.update(0x1780);

        Assertions.assertAll(
                () -> assertSame(SidekickCpuController.State.FLIGHT_AUTO_RECOVERY, controller.getState(),
                        "ROM Tails_Catch_Up_Flying blocks only when Sonic object_control is negative "
                                + "(sonic3k.asm:26478-26488)"),
                () -> assertEquals(0x0B78, tails.getCentreX() & 0xFFFF),
                () -> assertEquals(0x0265, tails.getCentreY() & 0xFFFF),
                () -> assertEquals(0, tails.getXSpeed(),
                        "ROM loc_13B50 zeroes x_vel on the catch-up warp (sonic3k.asm:26503-26506)"),
                () -> assertEquals(0, tails.getGSpeed(),
                        "ROM loc_13B50 zeroes ground_vel on the catch-up warp (sonic3k.asm:26503-26506)"));
    }

    private static void setLevelFrameCounter(int value) throws Exception {
        Field frameCounter = GameServices.level().getClass().getDeclaredField("frameCounter");
        frameCounter.setAccessible(true);
        frameCounter.setInt(GameServices.level(), value);
    }

    private static void setControllerState(SidekickCpuController controller,
                                           SidekickCpuController.State state) throws Exception {
        Field stateField = SidekickCpuController.class.getDeclaredField("state");
        stateField.setAccessible(true);
        stateField.set(controller, state);
    }

}
