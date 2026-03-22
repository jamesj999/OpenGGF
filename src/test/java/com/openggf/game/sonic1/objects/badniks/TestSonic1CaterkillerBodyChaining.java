package com.openggf.game.sonic1.objects.badniks;

import org.junit.Before;
import org.junit.Test;
import com.openggf.camera.Camera;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchCategory;
import com.openggf.level.objects.TouchResponseResult;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestSonic1CaterkillerBodyChaining {

    @Before
    public void setUp() {
        // Ensure Camera singleton exists with clean state (matches TestBuzzBomberLifecycle pattern)
        Camera.getInstance().resetState();
    }

    @Test
    public void bodySegmentUsesImmediateParentStateForMovementAndYDelta() {
        LevelManager levelManager = LevelManager.getInstance();
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

        assertEquals("Body X should follow immediate parent velocity", 101, body.getX());
        assertEquals("Body Y should consume immediate parent ring-buffer delta", 52, body.getY());
        assertEquals("Body should publish consumed delta for its own child chain", 2, body.readRingBuffer(4));
    }

    @Test
    public void bodySegmentUsesHurtCollisionCategory() {
        LevelManager levelManager = LevelManager.getInstance();
        Sonic1CaterkillerBadnikInstance head = new Sonic1CaterkillerBadnikInstance(
                new ObjectSpawn(0, 0, 0x78, 0, 0, false, 0));
        FakeParentState parentState = new FakeParentState();

        Sonic1CaterkillerBodyInstance body = new Sonic1CaterkillerBodyInstance(
                head, parentState, 0, 0, true, false, 0, 4);

        assertEquals("Body touch should route through HURT category with size index 0x0B",
                0x8B, body.getCollisionFlags());
    }

    @Test
    public void bodyTouchTriggersFragmentBehavior() {
        LevelManager levelManager = LevelManager.getInstance();
        Sonic1CaterkillerBadnikInstance head = new Sonic1CaterkillerBadnikInstance(
                new ObjectSpawn(0, 0, 0x78, 0, 0, false, 0));
        FakeParentState parentState = new FakeParentState();

        Sonic1CaterkillerBodyInstance body = new Sonic1CaterkillerBodyInstance(
                head, parentState, 0, 0, true, false, 0, 4);

        assertFalse("Head should not start in fragment mode", head.isFragmenting());
        assertFalse("Body should not start in fragment mode", body.isFragmenting());

        body.onTouchResponse(null, new TouchResponseResult(0x0B, 8, 8, TouchCategory.HURT), 0);

        assertTrue("Body contact should trigger head fragment mode", head.isFragmenting());
        body.update(0, null);
        assertTrue("Body should enter fragment mode after head fragment trigger", body.isFragmenting());
    }

    @Test
    public void bodyDeletesWhenHeadIsUnloaded() {
        AbstractObjectInstance.updateCameraBounds(0, 0, 320, 224, 0);
        LevelManager levelManager = LevelManager.getInstance();
        Sonic1CaterkillerBadnikInstance head = new Sonic1CaterkillerBadnikInstance(
                new ObjectSpawn(0, 0, 0x78, 0, 0, false, 0));
        FakeParentState parentState = new FakeParentState();

        Sonic1CaterkillerBodyInstance body = new Sonic1CaterkillerBodyInstance(
                head, parentState, 32, 32, true, false, 0, 4);

        head.onUnload();
        body.update(0, null);

        assertTrue("Body should delete when parent head enters delete/unload path", body.isDestroyed());
        assertFalse("Body should not enter fragment mode when head is unloading", body.isFragmenting());
    }

    @Test
    public void fragmentingBodyDeletesWhenOffScreen() {
        AbstractObjectInstance.updateCameraBounds(0, 0, 320, 224, 0);
        LevelManager levelManager = LevelManager.getInstance();
        Sonic1CaterkillerBadnikInstance head = new Sonic1CaterkillerBadnikInstance(
                new ObjectSpawn(0, 0, 0x78, 0, 0, false, 0));
        FakeParentState parentState = new FakeParentState();

        Sonic1CaterkillerBodyInstance body = new Sonic1CaterkillerBodyInstance(
                head, parentState, 1000, 0, true, false, 0, 4);

        head.triggerFragmentFromBodyHit();
        body.update(0, null); // enters fragment mode
        body.update(1, null); // fragment physics + off-screen delete check

        assertTrue("Fragmenting body segments should self-delete once off-screen", body.isDestroyed());
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
