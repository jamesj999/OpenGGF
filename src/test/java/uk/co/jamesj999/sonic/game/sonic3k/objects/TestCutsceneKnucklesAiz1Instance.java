package uk.co.jamesj999.sonic.game.sonic3k.objects;

import org.junit.Test;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
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

    @Test
    public void paceTimerIs0x29Frames() {
        assertEquals(0x29, CutsceneKnucklesAiz1Instance.PACE_TIMER);
    }

    @Test
    public void standTimerIs0x7FFrames() {
        assertEquals(0x7F, CutsceneKnucklesAiz1Instance.STAND_TIMER);
    }

    @Test
    public void laughTimerIs0x3FFrames() {
        assertEquals(0x3F, CutsceneKnucklesAiz1Instance.LAUGH_TIMER);
    }

    @Test
    public void paceVelocityIs0x600() {
        assertEquals(0x600, CutsceneKnucklesAiz1Instance.PACE_VELOCITY);
    }

    @Test
    public void fallVelocityYIsMinus0x600() {
        assertEquals(-0x600, CutsceneKnucklesAiz1Instance.FALL_INIT_Y_VEL);
    }

    @Test
    public void fallVelocityXIs0x80() {
        assertEquals(0x80, CutsceneKnucklesAiz1Instance.FALL_INIT_X_VEL);
    }
}
