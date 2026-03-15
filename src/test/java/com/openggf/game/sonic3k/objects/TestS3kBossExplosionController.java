package com.openggf.game.sonic3k.objects;

import org.junit.Test;
import static org.junit.Assert.*;

public class TestS3kBossExplosionController {

    @Test
    public void controllerSpawnsExplosionEveryFrameAfterInitialWait() {
        // subtype 2: timer=0x28 (40), 2-frame initial wait, then 1 explosion/frame
        var controller = new S3kBossExplosionController(160, 112, 2);
        int spawnCount = 0;
        // 2 wait frames + 40 timer frames = 42 total ticks
        for (int frame = 0; frame < 42; frame++) {
            controller.tick();
            spawnCount += controller.drainPendingExplosions().size();
        }
        // ROM: 2 wait frames (no spawns) + 40 timer decrements (1 spawn each) = 40 explosions
        assertEquals("Should spawn 40 explosions (one per frame after 2-frame wait)", 40, spawnCount);
    }

    @Test
    public void initialWaitProducesNoExplosions() {
        var controller = new S3kBossExplosionController(160, 112, 2);
        // First 2 ticks are wait frames — no explosions
        controller.tick();
        assertEquals(0, controller.drainPendingExplosions().size());
        controller.tick();
        assertEquals(0, controller.drainPendingExplosions().size());
        // Third tick starts spawning
        controller.tick();
        assertEquals(1, controller.drainPendingExplosions().size());
    }

    @Test
    public void controllerIsFinishedAfterTimerExpires() {
        var controller = new S3kBossExplosionController(160, 112, 2);
        for (int frame = 0; frame < 44; frame++) {
            controller.tick();
            controller.drainPendingExplosions();
        }
        assertTrue("Controller should be finished after timer", controller.isFinished());
    }

    @Test
    public void explosionOffsetsAreWithinRange() {
        var controller = new S3kBossExplosionController(160, 112, 2);
        // Skip initial wait
        controller.tick();
        controller.tick();
        // First explosion frame
        controller.tick();
        for (var explosion : controller.drainPendingExplosions()) {
            int dx = Math.abs(explosion.x() - 160);
            int dy = Math.abs(explosion.y() - 112);
            assertTrue("X offset within ±0x80", dx <= 0x80);
            assertTrue("Y offset within ±0x80", dy <= 0x80);
        }
    }
}
