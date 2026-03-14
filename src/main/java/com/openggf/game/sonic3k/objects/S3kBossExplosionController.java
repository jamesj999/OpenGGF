package com.openggf.game.sonic3k.objects;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * S3K boss explosion controller (ROM: Obj_BossExplosionSpecial + Obj_BossExpControl2).
 * Plain Java object (not an ObjectInstance) — ticked directly by the owning boss.
 * Spawns child explosions at random offsets every 2 frames until timer expires.
 *
 * ROM: CreateBossExp02 parameters: timer=$28 (40), xRange=$80, yRange=$80
 */
public class S3kBossExplosionController {
    private static final int[][] SUBTYPE_PARAMS = {
            {0x20, 0x20, 0x20},
            {0x28, 0x80, 0x80},
            {0x80, 0x20, 0x20},
            {0x04, 0x10, 0x10},
    };
    private static final int SPAWN_INTERVAL = 2;

    private final int centreX;
    private final int centreY;
    private final int xRange;
    private final int yRange;
    private int timer;
    private int frameCount;
    private final List<PendingExplosion> pendingExplosions = new ArrayList<>();

    public record PendingExplosion(int x, int y) {}

    public S3kBossExplosionController(int centreX, int centreY, int subtype) {
        this.centreX = centreX;
        this.centreY = centreY;
        int paramIndex = Math.min((subtype & 0xFF) >> 1, SUBTYPE_PARAMS.length - 1);
        int[] params = SUBTYPE_PARAMS[paramIndex];
        this.timer = params[0];
        this.xRange = params[1];
        this.yRange = params[2];
        this.frameCount = 0;
    }

    public void tick() {
        if (timer <= 0) return;
        timer--;
        if (frameCount % SPAWN_INTERVAL == 0) {
            spawnExplosionChild();
        }
        frameCount++;
    }

    public boolean isFinished() {
        return timer <= 0;
    }

    private void spawnExplosionChild() {
        int random = ThreadLocalRandom.current().nextInt(0x10000);
        int xMask = (xRange * 2) - 1;
        int yMask = (yRange * 2) - 1;
        int xOffset = (random & xMask) - xRange;
        int yOffset = ((random >> 8) & yMask) - yRange;
        pendingExplosions.add(new PendingExplosion(centreX + xOffset, centreY + yOffset));
    }

    public List<PendingExplosion> drainPendingExplosions() {
        List<PendingExplosion> result = List.copyOf(pendingExplosions);
        pendingExplosions.clear();
        return result;
    }
}
