package com.openggf.sprites.playable;

import com.openggf.game.EngineServices;
import com.openggf.game.GameServices;
import com.openggf.game.RuntimeManager;
import com.openggf.physics.Direction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSidekickCpuDespawnParity {

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
        public void defineSpeeds() {
            runHeight = 38;
            rollHeight = 28;
            standXRadius = 9;
            standYRadius = 19;
            rollXRadius = 7;
            rollYRadius = 14;
            setWidth(18);
            setHeight(runHeight);
        }

        @Override
        protected void createSensorLines() {}
    }

    @Test
    void despawnPreservesMotionAndMatchesRomStatusReset() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setRolling(true);
        tails.setRollingJump(true);
        tails.setOnObject(true);
        tails.setPushing(true);
        tails.setDirection(Direction.LEFT);
        tails.setCentreX((short) 0x0545);
        tails.setCentreY((short) 0x0270);
        tails.setSubpixelRaw(0x0300, 0x2700);
        tails.setXSpeed((short) 0x041C);
        tails.setYSpeed((short) 0x0000);
        tails.setGSpeed((short) 0x041C);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.setInitialState(SidekickCpuController.State.NORMAL);

        controller.despawn();

        assertEquals(SidekickCpuController.State.SPAWNING, controller.getState());
        assertEquals((short) 0x4000, tails.getCentreX());
        assertEquals((short) 0x0000, tails.getCentreY());
        assertEquals(0x0300, tails.getXSubpixelRaw());
        assertEquals(0x2700, tails.getYSubpixelRaw());
        assertEquals((short) 0x041C, tails.getXSpeed());
        assertEquals((short) 0x0000, tails.getYSpeed());
        assertEquals((short) 0x041C, tails.getGSpeed());
        assertTrue(tails.getAir());
        assertFalse(tails.getRolling());
        assertFalse(tails.getRollingJump());
        assertFalse(tails.isOnObject());
        assertFalse(tails.getPushing());
        assertEquals(Direction.RIGHT, tails.getDirection());
        assertTrue(tails.isControlLocked());
        assertTrue(tails.isObjectControlled());
    }

    @Test
    void offscreenObjectSwitchDespawnsUsingLatchedInteractObjectId() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setCentreX((short) 0x0545);
        tails.setCentreY((short) 0x0270);
        tails.setAir(false);
        tails.setOnObject(true);
        tails.setLatchedSolidObjectId(0x11);

        GameServices.camera().setX((short) 0x058C);
        GameServices.camera().setY((short) 0x0200);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.hydrateFromRomCpuState(6, 0, 90, 0x01, false);
        tails.setLatchedSolidObjectId(0x11);
        tails.setOnObject(true);
        tails.setRenderFlagOnScreen(false);

        controller.update(1131);

        assertEquals(SidekickCpuController.State.SPAWNING, controller.getState());
        assertEquals((short) 0x4000, tails.getCentreX());
        assertEquals((short) 0x0000, tails.getCentreY());
    }

    @Test
    void renderFlagBottomMarginKeepsDespawnTimerReset() {
        TestableSprite sonic = new TestableSprite("sonic");
        sonic.setCentreX((short) 0x0610);
        sonic.setCentreY((short) 0x0200);

        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setCentreX((short) 0x0610);
        tails.setCentreY((short) 0x02F0);
        tails.setAir(true);

        GameServices.camera().setX((short) 0x058C);
        GameServices.camera().setY((short) 0x0200);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.hydrateFromRomCpuState(6, 0, 299, 0x11, false);
        tails.setRenderFlagOnScreen(true);

        controller.update(3532);

        assertEquals(SidekickCpuController.State.NORMAL, controller.getState());
        assertNotEquals((short) 0x4000, tails.getCentreX());
        assertNotEquals((short) 0x0000, tails.getCentreY());
    }

    @Test
    void despawnTimerUsesCachedRenderFlagInsteadOfCurrentCameraGeometry() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setCentreX((short) 0x0700);
        tails.setCentreY((short) 0x0200);
        tails.setOnObject(true);
        tails.setLatchedSolidObjectId(0x11);

        GameServices.camera().setX((short) 0x05A0);
        GameServices.camera().setY((short) 0x0200);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.hydrateFromRomCpuState(6, 0, 299, 0x11, false);

        tails.setRenderFlagOnScreen(true);
        controller.update(1);
        assertEquals(SidekickCpuController.State.NORMAL, controller.getState());

        tails.setRenderFlagOnScreen(false);
        controller.update(2);
        assertEquals(SidekickCpuController.State.NORMAL, controller.getState());
    }
}
