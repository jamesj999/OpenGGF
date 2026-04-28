package com.openggf.sprites.playable;

import com.openggf.game.EngineServices;
import com.openggf.game.GameServices;
import com.openggf.game.PhysicsFeatureSet;
import com.openggf.game.RuntimeManager;
import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.physics.Direction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

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

        void usePhysicsFeatureSet(PhysicsFeatureSet featureSet) {
            setPhysicsFeatureSet(featureSet);
        }
    }

    private static final class DestroyedRideObject extends AbstractObjectInstance {
        private DestroyedRideObject(int objectId) {
            super(new ObjectSpawn(0x1200, 0x0800, objectId, 0, 0, false, 0), "DestroyedRideObject");
            setDestroyed(true);
        }

        @Override
        public void update(int frameCounter, PlayableEntity player) {
            // Test sentinel only.
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            // No rendering needed for this test sentinel.
        }
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
    void s3kDespawnMarkerReturnsToCatchUpFlightRoutine() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.usePhysicsFeatureSet(PhysicsFeatureSet.SONIC_3K);
        tails.setCpuControlled(true);
        tails.setCentreX((short) 0x0545);
        tails.setCentreY((short) 0x0270);
        tails.setSubpixelRaw(0x0300, 0x2700);
        tails.setXSpeed((short) 0x041C);
        tails.setYSpeed((short) 0x0020);
        tails.setGSpeed((short) 0x041C);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.setInitialState(SidekickCpuController.State.NORMAL);

        controller.despawn();

        assertEquals(SidekickCpuController.State.CATCH_UP_FLIGHT, controller.getState(),
                "S3K sub_13ECA writes Tails_CPU_routine=2, not the S2 SPAWNING approach");
        assertEquals((short) 0x7F00, tails.getCentreX(),
                "S3K despawn marker uses x_pos=$7F00");
        assertEquals((short) 0x0000, tails.getCentreY());
        assertEquals(0x0300, tails.getXSubpixelRaw());
        assertEquals(0x2700, tails.getYSubpixelRaw());
        assertEquals((short) 0x041C, tails.getXSpeed());
        assertEquals((short) 0x0020, tails.getYSpeed());
        assertEquals((short) 0x041C, tails.getGSpeed());
        assertEquals(0, tails.getDoubleJumpFlag(),
                "S3K sub_13ECA clears double_jump_flag");
        assertTrue(tails.getAir());
        assertTrue(tails.isControlLocked());
        assertTrue(tails.isObjectControlled());
    }

    @Test
    void s3kOffscreenDestroyedRideSlotDespawnsEvenWhenInteractIdIsUnchanged() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.usePhysicsFeatureSet(PhysicsFeatureSet.SONIC_3K);
        tails.setCpuControlled(true);
        tails.setCentreX((short) 0x12BE);
        tails.setCentreY((short) 0x08A9);
        tails.setAir(false);
        tails.setOnObject(true);
        tails.setLatchedSolidObject(0x4E, new DestroyedRideObject(0x4E));
        tails.setRenderFlagOnScreen(false);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.hydrateFromRomCpuState(6, 0, 0, 0x4E, false);
        tails.setLatchedSolidObject(0x4E, new DestroyedRideObject(0x4E));
        tails.setOnObject(true);
        tails.setRenderFlagOnScreen(false);

        controller.update(2262);

        assertEquals(SidekickCpuController.State.CATCH_UP_FLIGHT, controller.getState(),
                "S3K sub_13EFC reads a freed ride slot as a mismatch and jumps through sub_13ECA");
        assertEquals((short) 0x7F00, tails.getCentreX());
        assertEquals((short) 0x0000, tails.getCentreY());
        assertTrue(tails.getAir());
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
