package com.openggf.game.sonic3k.objects;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TestS3kBossExplosionController {

    @Test
    public void controllerSpawnsExplosionsEveryThreeFrames() {
        // subtype 2: timer=$28 (40), xRange=$80, yRange=$80
        // ROM: 3-frame initial wait, then 1 explosion every 3 frames
        // Timer counts $28â†’$00 inclusive = 41 spawns (subq.b + bmi)
        var controller = new S3kBossExplosionController(160, 112, 2);
        int spawnCount = 0;
        // Run for enough frames to exhaust the timer
        for (int frame = 0; frame < 200; frame++) {
            controller.tick();
            spawnCount += controller.drainPendingExplosions().size();
            if (controller.isFinished()) break;
        }
        // Timer $28â†’$00 = 40 spawns. Next decrement ($00â†’$FF) triggers bmi â†’ delete, no spawn.
        assertEquals(40, spawnCount, "Should spawn 40 explosions");
    }

    @Test
    public void initialWaitBeforeFirstExplosion() {
        var controller = new S3kBossExplosionController(160, 112, 2);
        // ROM: initial $2E = 2 (3-1), Obj_Wait counts 2â†’1â†’0â†’-1 = 3 frames
        for (int i = 0; i < 3; i++) {
            controller.tick();
            assertEquals(0, controller.drainPendingExplosions().size(), "No explosions during initial wait frame " + i);
        }
        // Frame 4: first explosion
        controller.tick();
        assertEquals(1, controller.drainPendingExplosions().size());
    }

    @Test
    public void threeFrameSpacingBetweenExplosions() {
        var controller = new S3kBossExplosionController(160, 112, 2);
        // Skip initial wait
        for (int i = 0; i < 3; i++) {
            controller.tick();
            controller.drainPendingExplosions();
        }
        // First explosion
        controller.tick();
        assertEquals(1, controller.drainPendingExplosions().size());
        // Next 2 frames: no explosion (wait)
        controller.tick();
        assertEquals(0, controller.drainPendingExplosions().size());
        controller.tick();
        assertEquals(0, controller.drainPendingExplosions().size());
        // Third frame after first: second explosion
        controller.tick();
        assertEquals(1, controller.drainPendingExplosions().size());
    }

    @Test
    public void explosionOffsetsAreWithinRange() {
        var controller = new S3kBossExplosionController(160, 112, 2);
        // Skip to first explosion
        for (int i = 0; i < 4; i++) {
            controller.tick();
        }
        for (var explosion : controller.drainPendingExplosions()) {
            int dx = Math.abs(explosion.x() - 160);
            int dy = Math.abs(explosion.y() - 112);
            assertTrue(dx <= 0x80, "X offset within Â±0x80");
            assertTrue(dy <= 0x80, "Y offset within Â±0x80");
        }
    }
}


