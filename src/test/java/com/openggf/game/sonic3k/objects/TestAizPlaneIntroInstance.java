package com.openggf.game.sonic3k.objects;

import org.junit.Before;
import org.junit.Test;
import com.openggf.camera.Camera;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.Sonic;

import static org.junit.Assert.*;

public class TestAizPlaneIntroInstance {

    private AizPlaneIntroInstance intro;

    @Before
    public void setUp() {
        Camera.resetInstance();
        intro = new AizPlaneIntroInstance(
                new ObjectSpawn(0x60, 0x30, 0, 0, 0, false, 0));
    }

    @Test
    public void initialRoutineIsZero() {
        assertEquals(0, intro.getRoutine());
    }

    @Test
    public void initSetsCorrectPosition() {
        assertEquals(0x60, intro.getX());
        assertEquals(0x30, intro.getY());
    }

    @Test
    public void routineAdvancesBy2() {
        intro.advanceRoutine();
        assertEquals(2, intro.getRoutine());
    }

    @Test
    public void waveSpawnIntervalIs5Frames() {
        assertEquals(5, AizPlaneIntroInstance.WAVE_SPAWN_INTERVAL);
    }

    @Test
    public void knucklesSpawnTriggerAt0x918() {
        assertEquals(0x918, AizPlaneIntroInstance.KNUCKLES_SPAWN_X);
    }

    @Test
    public void explosionTriggerAt0x13D0() {
        assertEquals(0x13D0, AizPlaneIntroInstance.EXPLOSION_TRIGGER_X);
    }

    @Test
    public void initLocksPlayerButDoesNotFreezeCamera() {
        Sonic player = new Sonic("sonic", (short) 0, (short) 0);
        player.setCentreX((short) 0x40);
        player.setCentreY((short) 0x420);

        Camera camera = Camera.getInstance();
        camera.setFocusedSprite(player);
        camera.setX((short) 0x200);
        camera.setY((short) 0x200);
        camera.setFrozen(false);

        intro.update(0, player);

        assertEquals(2, intro.getRoutine());
        assertTrue(player.isControlLocked());
        assertTrue(player.isObjectControlled());
        assertTrue(player.isHidden());
        // ROM: camera is NOT frozen; it stays at origin naturally via
        // Level_started_flag. Intro objects use screen-coordinate rendering.
        assertFalse(camera.getFrozen());
    }

    @Test
    public void initLocksFocusedPlayerWhenPlayerParamIsNull() {
        Sonic focusedPlayer = new Sonic("sonic", (short) 0, (short) 0);
        focusedPlayer.setCentreX((short) 0x40);
        focusedPlayer.setCentreY((short) 0x420);

        Camera camera = Camera.getInstance();
        camera.setFocusedSprite(focusedPlayer);
        camera.setFrozen(false);

        intro.update(0, null);

        assertEquals(2, intro.getRoutine());
        assertTrue(focusedPlayer.isControlLocked());
        assertTrue(focusedPlayer.isObjectControlled());
        assertTrue(focusedPlayer.isHidden());
        assertFalse(camera.getFrozen());
    }

    @Test
    public void introEventuallyPassesKnucklesTriggerGate() {
        Sonic player = new Sonic("sonic", (short) 0, (short) 0);
        player.setCentreX((short) 0x40);
        player.setCentreY((short) 0x420);

        Camera camera = Camera.getInstance();
        camera.setFocusedSprite(player);

        for (int frame = 0; frame < 2500 && intro.getRoutine() < 24; frame++) {
            intro.update(frame, player);
        }

        assertTrue("Intro routine should progress past Knuckles trigger gate",
                intro.getRoutine() >= 24);
    }

    @Test
    public void introProgressesPastKnucklesGateWhenPlayerParamIsNull() {
        Sonic focusedPlayer = new Sonic("sonic", (short) 0, (short) 0);
        focusedPlayer.setCentreX((short) 0x40);
        focusedPlayer.setCentreY((short) 0x420);

        Camera camera = Camera.getInstance();
        camera.setFocusedSprite(focusedPlayer);

        for (int frame = 0; frame < 2500 && intro.getRoutine() < 24; frame++) {
            intro.update(frame, null);
        }

        assertTrue("Intro routine should progress past Knuckles trigger gate with focused fallback",
                intro.getRoutine() >= 24);
        assertTrue("Focused player should advance rightward after intro scroll gate opens",
                focusedPlayer.getCentreX() > 0x40);
    }
}
