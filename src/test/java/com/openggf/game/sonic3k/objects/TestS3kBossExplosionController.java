package com.openggf.game.sonic3k.objects;

import org.junit.Test;
import static org.junit.Assert.*;

public class TestS3kBossExplosionController {

    @Test
    public void controllerSpawnsExplosionsEveryTwoFrames() {
        var controller = new S3kBossExplosionController(160, 112, 2);
        int spawnCount = 0;
        for (int frame = 0; frame < 40; frame++) {
            controller.tick();
            spawnCount += controller.drainPendingExplosions().size();
        }
        assertEquals("Should spawn exactly 20 explosions", 20, spawnCount);
    }

    @Test
    public void controllerIsFinishedAfterTimerExpires() {
        var controller = new S3kBossExplosionController(160, 112, 2);
        for (int frame = 0; frame < 42; frame++) {
            controller.tick();
            controller.drainPendingExplosions();
        }
        assertTrue("Controller should be finished after timer", controller.isFinished());
    }

    @Test
    public void noExplosionsOnOddFrames() {
        var controller = new S3kBossExplosionController(160, 112, 2);
        controller.tick();
        assertEquals(1, controller.drainPendingExplosions().size());
        controller.tick();
        assertEquals(0, controller.drainPendingExplosions().size());
        controller.tick();
        assertEquals(1, controller.drainPendingExplosions().size());
    }

    @Test
    public void explosionOffsetsAreWithinRange() {
        var controller = new S3kBossExplosionController(160, 112, 2);
        controller.tick();
        for (var explosion : controller.drainPendingExplosions()) {
            int dx = Math.abs(explosion.x() - 160);
            int dy = Math.abs(explosion.y() - 112);
            assertTrue("X offset within ±0x80", dx <= 0x80);
            assertTrue("Y offset within ±0x80", dy <= 0x80);
        }
    }
}
