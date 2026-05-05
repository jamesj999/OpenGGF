package com.openggf.sprites.playable;

import com.openggf.game.session.EngineContext;
import com.openggf.game.RuntimeManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SidekickCpuController.CATCH_UP_FLIGHT (ROM routine 0x02,
 * Tails_Catch_Up_Flying at sonic3k.asm:26474).
 */
class TestSidekickCpuControllerCatchUpFlight {

    @BeforeEach
    void setUp() {
        RuntimeManager.configureEngineServices(EngineContext.fromLegacySingletonsForBootstrap());
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

    @Test
    void catchUpTeleportsTailsToSonicMinus0xC0Y_onCtrl2ABCPress() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        sonic.setCentreX((short) 0x1000);
        sonic.setCentreY((short) 0x0400);
        tails.setCentreX((short) 0x0200);  // Far away
        tails.setCentreY((short) 0x0500);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.forceStateForTest(SidekickCpuController.State.CATCH_UP_FLIGHT, 0);
        controller.setController2Input(0, AbstractPlayableSprite.INPUT_JUMP);  // A/B/C press

        controller.update(0);

        assertEquals(0x1000, tails.getCentreX() & 0xFFFF,
                "Catch-up teleport writes Sonic.x to Tails");
        assertEquals(0x0340, tails.getCentreY() & 0xFFFF,
                "Catch-up teleport writes Sonic.y - 0xC0 to Tails");
        assertEquals((short) 0, tails.getXSpeed(), "Velocities zeroed");
        assertEquals((short) 0, tails.getYSpeed(), "Velocities zeroed");
        assertEquals((short) 0, tails.getGSpeed(), "Velocities zeroed");
        assertEquals(0, tails.getDoubleJumpFlag(),
                "ROM loc_13B50 clears double_jump_flag; catch-up flight is CPU/object-control driven");
        assertTrue(tails.getAir(), "status air bit set");
        assertTrue(tails.isObjectControlled(), "ROM loc_13B50 writes object_control=$81");
        assertSame(SidekickCpuController.State.FLIGHT_AUTO_RECOVERY, controller.getState(),
                "Transitions to routine 0x04 (FLIGHT_AUTO_RECOVERY) on trigger");
    }

    @Test
    void catchUp64FrameGateTriggersOnlyEvery64Frames() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        sonic.setCentreX((short) 0x1000);
        sonic.setCentreY((short) 0x0400);
        tails.setCentreX((short) 0x0200);
        tails.setCentreY((short) 0x0500);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.forceStateForTest(SidekickCpuController.State.CATCH_UP_FLIGHT, 0);

        // Frames not divisible by 64 — should NOT trigger
        controller.update(1);
        assertSame(SidekickCpuController.State.CATCH_UP_FLIGHT, controller.getState(),
                "Frame 1 is not a 64-frame boundary; stay in CATCH_UP");

        controller.update(63);
        assertSame(SidekickCpuController.State.CATCH_UP_FLIGHT, controller.getState(),
                "Frame 63 is not a 64-frame boundary; stay in CATCH_UP");

        // Frame 64 = 0x40 = divisible by 64 — SHOULD trigger
        controller.update(64);
        assertSame(SidekickCpuController.State.FLIGHT_AUTO_RECOVERY, controller.getState(),
                "Frame 64 hits the 64-frame gate; transition to FLIGHT_AUTO_RECOVERY");
    }

    @Test
    void catchUp64FrameGateSuppressedWhenSonicIsObjectControlled() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        sonic.setObjectControlled(true);  // ROM checks bit 7 of object_control (bmi)

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.forceStateForTest(SidekickCpuController.State.CATCH_UP_FLIGHT, 0);

        controller.update(64);

        assertSame(SidekickCpuController.State.CATCH_UP_FLIGHT, controller.getState(),
                "Sonic object-controlled suppresses the 64-frame gate");
    }

    @Test
    void catchUpWaitPreservesMarkerObjectControlAndAirState() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setAir(false);
        tails.setControlLocked(false);
        tails.setObjectControlled(false);
        tails.setForcedAnimationId(-1);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.forceStateForTest(SidekickCpuController.State.CATCH_UP_FLIGHT, 0);

        controller.update(1);

        assertSame(SidekickCpuController.State.CATCH_UP_FLIGHT, controller.getState(),
                "Frame 1 has no catch-up trigger");
        assertTrue(tails.getAir(), "ROM routine 2 wait preserves status.in_air from the marker");
        assertTrue(tails.isControlLocked(), "ROM routine 2 wait preserves object_control bit 0");
        assertTrue(tails.isObjectControlled(), "ROM routine 2 wait preserves object_control bit 7");
        assertTrue(tails.getForcedAnimationId() >= 0, "Wait remains in the fly animation state");
    }
}
