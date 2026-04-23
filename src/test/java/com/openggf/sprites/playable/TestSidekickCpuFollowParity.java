package com.openggf.sprites.playable;

import com.openggf.game.EngineServices;
import com.openggf.game.RuntimeManager;
import com.openggf.physics.Direction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

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
}
