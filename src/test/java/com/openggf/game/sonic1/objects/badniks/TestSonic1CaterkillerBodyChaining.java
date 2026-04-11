package com.openggf.game.sonic1.objects.badniks;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.RuntimeManager;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchCategory;
import com.openggf.level.objects.TouchResponseResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestSonic1CaterkillerBodyChaining {

    @BeforeEach
    public void setUp() {
        RuntimeManager.createGameplay();
        GameServices.camera().resetState();
    }

    @AfterEach
    public void tearDown() {
        RuntimeManager.destroyCurrent();
    }

    @Test
    public void bodySegmentUsesImmediateParentStateForMovementAndYDelta() {
        LevelManager levelManager = GameServices.level();
        Sonic1CaterkillerBadnikInstance head = new Sonic1CaterkillerBadnikInstance(
                new ObjectSpawn(0, 0, 0x78, 0, 0, false, 0));

        FakeParentState parentState = new FakeParentState();
        parentState.secondaryState = 1; // moving
        parentState.inertia = 0;
        parentState.xVelocity = 0x100; // +1 pixel/frame in 16.8 fixed-point
        parentState.ringBuffer[4] = 2; // apply +2 Y delta

        Sonic1CaterkillerBodyInstance body = new Sonic1CaterkillerBodyInstance(
                head, parentState, 100, 50, true, false, 0, 4);

        body.update(0, null);

        assertEquals(101, body.getX(), "Body X should follow immediate parent velocity");
        assertEquals(52, body.getY(), "Body Y should consume immediate parent ring-buffer delta");
        assertEquals(2, body.readRingBuffer(4), "Body should publish consumed delta for its own child chain");
    }

    @Test
    public void bodySegmentUsesHurtCollisionCategory() {
        LevelManager levelManager = GameServices.level();
        Sonic1CaterkillerBadnikInstance head = new Sonic1CaterkillerBadnikInstance(
                new ObjectSpawn(0, 0, 0x78, 0, 0, false, 0));
        FakeParentState parentState = new FakeParentState();

        Sonic1CaterkillerBodyInstance body = new Sonic1CaterkillerBodyInstance(
                head, parentState, 0, 0, true, false, 0, 4);

        assertEquals(0x8B, body.getCollisionFlags(), "Body touch should route through HURT category with size index 0x0B");
    }

    @Test
    public void bodyTouchTriggersFragmentBehavior() {
        LevelManager levelManager = GameServices.level();
        Sonic1CaterkillerBadnikInstance head = new Sonic1CaterkillerBadnikInstance(
                new ObjectSpawn(0, 0, 0x78, 0, 0, false, 0));
        FakeParentState parentState = new FakeParentState();

        Sonic1CaterkillerBodyInstance body = new Sonic1CaterkillerBodyInstance(
                head, parentState, 0, 0, true, false, 0, 4);

        assertFalse(head.isFragmenting(), "Head should not start in fragment mode");
        assertFalse(body.isFragmenting(), "Body should not start in fragment mode");

        body.onTouchResponse(null, new TouchResponseResult(0x0B, 8, 8, TouchCategory.HURT), 0);

        assertTrue(head.isFragmenting(), "Body contact should trigger head fragment mode");
        body.update(0, null);
        assertTrue(body.isFragmenting(), "Body should enter fragment mode after head fragment trigger");
    }

    @Test
    public void bodyDeletesWhenHeadIsUnloaded() {
        AbstractObjectInstance.updateCameraBounds(0, 0, 320, 224, 0);
        LevelManager levelManager = GameServices.level();
        Sonic1CaterkillerBadnikInstance head = new Sonic1CaterkillerBadnikInstance(
                new ObjectSpawn(0, 0, 0x78, 0, 0, false, 0));
        FakeParentState parentState = new FakeParentState();

        Sonic1CaterkillerBodyInstance body = new Sonic1CaterkillerBodyInstance(
                head, parentState, 32, 32, true, false, 0, 4);

        head.onUnload();
        body.update(0, null);

        assertTrue(body.isDestroyed(), "Body should delete when parent head enters delete/unload path");
        assertFalse(body.isFragmenting(), "Body should not enter fragment mode when head is unloading");
    }

    @Test
    public void fragmentingBodyDeletesWhenOffScreen() {
        AbstractObjectInstance.updateCameraBounds(0, 0, 320, 224, 0);
        LevelManager levelManager = GameServices.level();
        Sonic1CaterkillerBadnikInstance head = new Sonic1CaterkillerBadnikInstance(
                new ObjectSpawn(0, 0, 0x78, 0, 0, false, 0));
        FakeParentState parentState = new FakeParentState();

        Sonic1CaterkillerBodyInstance body = new Sonic1CaterkillerBodyInstance(
                head, parentState, 1000, 0, true, false, 0, 4);

        head.triggerFragmentFromBodyHit();
        body.update(0, null); // enters fragment mode
        body.update(1, null); // fragment physics + off-screen delete check

        assertTrue(body.isDestroyed(), "Fragmenting body segments should self-delete once off-screen");
    }

    private static final class FakeParentState implements CaterkillerParentState {
        int secondaryState;
        int inertia;
        int xVelocity;
        int animControl;
        final byte[] ringBuffer = new byte[16];

        @Override
        public int getSecondaryState() {
            return secondaryState;
        }

        @Override
        public int getInertia() {
            return inertia;
        }

        @Override
        public int getXVelocity() {
            return xVelocity;
        }

        @Override
        public int getAnimControl() {
            return animControl;
        }

        @Override
        public int readRingBuffer(int index) {
            return ringBuffer[index & 0x0F];
        }

        @Override
        public void writeRingBuffer(int index, int value) {
            ringBuffer[index & 0x0F] = (byte) value;
        }
    }
}


