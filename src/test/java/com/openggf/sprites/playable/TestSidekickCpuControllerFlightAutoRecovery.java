package com.openggf.sprites.playable;

import com.openggf.game.EngineServices;
import com.openggf.game.RuntimeManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SidekickCpuController.FLIGHT_AUTO_RECOVERY (ROM routine 0x04,
 * Tails_FlySwim_Unknown at sonic3k.asm:26534).
 */
class TestSidekickCpuControllerFlightAutoRecovery {

    @BeforeEach
    void setUp() {
        RuntimeManager.configureEngineServices(EngineServices.fromLegacySingletonsForBootstrap());
        RuntimeManager.createGameplay();
    }

    @AfterEach
    void tearDown() {
        RuntimeManager.destroyCurrent();
    }

    static class TestableSprite extends AbstractPlayableSprite {
        TestableSprite(String code) { super(code, (short) 0, (short) 0); }
        @Override public void draw() {}
        @Override public void defineSpeeds() {}
        @Override protected void createSensorLines() {}
    }

    private TestableSprite sonicAt(int x, int y) {
        TestableSprite sonic = new TestableSprite("sonic");
        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) x);
        Arrays.fill(yHistory, (short) y);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 16);
        sonic.setCentreX((short) x);
        sonic.setCentreY((short) y);
        return sonic;
    }

    @Test
    void flightSteersXByDistanceOver16ClampedTo0xC() {
        TestableSprite sonic = sonicAt(0x1000, 0x0400);
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setCentreX((short) 0x1F00);   // 0xF00 away from Sonic (to the right)
        tails.setCentreY((short) 0x0400);
        tails.setAir(true);
        tails.setDoubleJumpFlag(1);
        // Tails_FlySwim_Unknown does NOT apply the -0x20 lead offset
        // (that's a NORMAL-routine adjustment at loc_13DA6, not here).
        // Sonic.x_vel defaults to 0 so the test doesn't pull in the
        // "speed match" term; step = clamp(|dx|>>4, 0xC) + |0| + 1 = 0xD.

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.forceStateForTest(SidekickCpuController.State.FLIGHT_AUTO_RECOVERY, 0);

        controller.update(10);

        // targetX = 0x1000. |dx| = 0xF00, |dx| >> 4 = 0xF0, clamped to 0xC;
        // +0 (Sonic idle) +1 = 0xD.
        // Tails is to the right of Sonic, so X decreases by 0xD.
        assertEquals(0x1EF3, tails.getCentreX() & 0xFFFF,
                "X steps toward Sonic by (clamp(|dx|>>4, 0xC) + |Sonic.x_vel| + 1) = 0xD");
    }

    @Test
    void flightTimerRollsBackToCatchUpAfter300FramesOffscreen() {
        TestableSprite sonic = sonicAt(0x1000, 0x0400);
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setCentreX((short) 0x3000);   // Far off-screen
        tails.setCentreY((short) 0x0400);
        tails.setAir(true);
        // setRenderFlagOnScreen(boolean) sets renderFlagOnScreenValid=true internally.
        tails.setRenderFlagOnScreen(false);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.forceStateForTest(SidekickCpuController.State.FLIGHT_AUTO_RECOVERY, 0);

        for (int i = 0; i < 5 * 60; i++) {
            // Re-apply off-screen each tick since the controller is allowed to
            // change the sprite's render state (it doesn't, but defensive).
            tails.setRenderFlagOnScreen(false);
            controller.update(i);
        }

        assertSame(SidekickCpuController.State.CATCH_UP_FLIGHT, controller.getState(),
                "After 5s off-screen, FLIGHT_AUTO_RECOVERY rolls back to CATCH_UP_FLIGHT");
    }

    @Test
    void flightTransitionsToNormalWhenCloseEnoughAndSonicAlive() {
        TestableSprite sonic = sonicAt(0x1000, 0x0400);
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setCentreX((short) 0x1000);   // Already aligned horizontally
        tails.setCentreY((short) 0x0400);   // Already aligned vertically
        tails.setAir(true);
        tails.setDoubleJumpFlag(1);         // Flight gravity active during catch-up
        tails.setControlLocked(true);       // routine 4 carries object_control=$81 until NORMAL transition
        // On-screen so the off-screen timer doesn't fire.
        tails.setRenderFlagOnScreen(true);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.forceStateForTest(SidekickCpuController.State.FLIGHT_AUTO_RECOVERY, 0);

        controller.update(10);

        assertSame(SidekickCpuController.State.NORMAL, controller.getState(),
                "Tails aligned with Sonic + Sonic alive = transition to NORMAL (routine 0x06)");
        assertFalse(tails.isObjectControlled(),
                "Transition clears Tails's object_control");
        assertFalse(tails.isControlLocked(),
                "Transition clears the engine control-lock mirror so NORMAL CPU input reaches movement");
        assertEquals(0, tails.getDoubleJumpFlag(),
                "Transition clears Tails's double_jump_flag so NORMAL runs with normal air "
                        + "gravity (+0x38) instead of the FLY-gated flight gravity (+0x08). ROM "
                        + "loc_1384A (sonic3k.asm:26213) auto-clears the flag while "
                        + "object_control bit 0 is set; the engine's NORMAL transition clears "
                        + "object_control, so we must clear double_jump_flag explicitly.");
    }

    @Test
    void normalTransitionsToFlightAutoRecoveryWhenLeaderDies() {
        TestableSprite sonic = sonicAt(0x1000, 0x0400);
        sonic.setDead(true);
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setCentreX((short) 0x0F00);
        tails.setCentreY((short) 0x0400);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

        controller.update(10);

        assertSame(SidekickCpuController.State.FLIGHT_AUTO_RECOVERY, controller.getState(),
                "Dead Sonic drives Tails into flight AI (routine 0x04)");
        assertEquals(1, tails.getDoubleJumpFlag(),
                "Flight transition sets double_jump_flag=1 so flight gravity applies");
        assertTrue(tails.getAir(), "Flight transition sets air bit");
    }
}
