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
        // Sonic is on-object to suppress the -0x20 lead offset, keeping targetX = 0x1000
        // (otherwise targetX = 0x0FE0 and the step calculation would be against a different value).
        // Sonic.x_vel defaults to 0 so the test doesn't pull in the
        // "speed match" term; step = clamp(|dx|>>4, 0xC) + |0| + 1 = 0xD.
        sonic.setOnObject(true);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.forceStateForTest(SidekickCpuController.State.FLIGHT_AUTO_RECOVERY, 0);

        controller.update(10);

        // targetX = 0x1000 (lead suppressed). |dx| = 0xF00, |dx| >> 4 = 0xF0, clamped to 0xC;
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
        // On-screen so the off-screen timer doesn't fire.
        tails.setRenderFlagOnScreen(true);
        // Set Sonic on-object so the -0x20 lead offset is suppressed.
        // Without this, targetX = 0x0FE0 and dx = 0x20 -- not close enough to transition
        // on the first tick.
        sonic.setOnObject(true);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.forceStateForTest(SidekickCpuController.State.FLIGHT_AUTO_RECOVERY, 0);

        controller.update(10);

        assertSame(SidekickCpuController.State.NORMAL, controller.getState(),
                "Tails aligned with Sonic + Sonic alive = transition to NORMAL (routine 0x06)");
        assertFalse(tails.isObjectControlled(),
                "Transition clears Tails's object_control");
    }
}
