package com.openggf.game.sonic3k.objects;

import org.junit.Test;
import com.openggf.level.objects.ObjectSpawn;
import static org.junit.Assert.*;

public class TestCutsceneKnucklesAiz1Instance {

    @Test
    public void initPositionIsCorrect() {
        var spawn = new ObjectSpawn(0x1400, 0x440, 0, 0, 0, false, 0);
        var knux = new CutsceneKnucklesAiz1Instance(spawn);
        assertEquals(0x1400, knux.getX());
        assertEquals(0x440, knux.getY());
    }

    @Test
    public void startsInWaitRoutine() {
        var spawn = new ObjectSpawn(0x1400, 0x440, 0, 0, 0, false, 0);
        var knux = new CutsceneKnucklesAiz1Instance(spawn);
        assertEquals(0, knux.getRoutine());
    }

}
