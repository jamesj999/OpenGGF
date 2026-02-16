package uk.co.jamesj999.sonic.game.sonic3k.objects;

import org.junit.Before;
import org.junit.Test;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import static org.junit.Assert.*;

public class TestAizPlaneIntroInstance {

    private AizPlaneIntroInstance intro;

    @Before
    public void setUp() {
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
}
