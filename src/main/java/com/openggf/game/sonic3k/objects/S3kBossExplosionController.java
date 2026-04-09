package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameRng;

import java.util.ArrayList;
import java.util.List;

/**
 * S3K boss explosion controller (ROM: Obj_BossExplosionSpecial → Obj_CreateBossExplosion).
 * Plain Java object (not an ObjectInstance) — ticked directly by the owning boss.
 *
 * ROM flow (s3.asm Obj_BossExpControl / Obj_NormalExpControl):
 * 1. Initial Obj_Wait: $2E = 3-1 = 2 (3 frames before first explosion)
 * 2. Obj_NormalExpControl: decrement $39 timer, spawn explosion, set $2E = 3-1
 * 3. Obj_Wait: 3 frames between each explosion
 * 4. Repeat until $39 goes negative (bmi → delete)
 *
 * CreateBossExp02: timer=$28, xRange=$80, yRange=$80, routine=2
 * Result: 41 explosions ($28→$00 inclusive), each spaced 3 frames apart.
 * Total duration: 3 + 41*3 = 126 frames (~2.1 seconds).
 *
 * SFX (sfx_Explode) is played by the controller (sub_52850), not by each child.
 */
public class S3kBossExplosionController {
    private static final int[][] SUBTYPE_PARAMS = {
            {0x20, 0x20, 0x20},
            {0x28, 0x80, 0x80},
            {0x80, 0x20, 0x20},
            {0x04, 0x10, 0x10},
    };
    // ROM: move.w #3-1,$2E(a0) — Obj_Wait counts $2E down, fires at -1 = 3 frame cycle
    private static final int SPAWN_INTERVAL = 3;

    private final int centreX;
    private final int centreY;
    private final int xRange;
    private final int yRange;
    private final GameRng rng;
    private int timer;
    private int intervalCounter;
    private final List<PendingExplosion> pendingExplosions = new ArrayList<>();

    public record PendingExplosion(int x, int y, boolean playSfx) {}

    public S3kBossExplosionController(int centreX, int centreY, int subtype) {
        this(centreX, centreY, subtype, new GameRng(GameRng.Flavour.S3K));
    }

    public S3kBossExplosionController(int centreX, int centreY, int subtype, GameRng rng) {
        this.centreX = centreX;
        this.centreY = centreY;
        this.rng = rng;
        int paramIndex = Math.min((subtype & 0xFF) >> 1, SUBTYPE_PARAMS.length - 1);
        int[] params = SUBTYPE_PARAMS[paramIndex];
        this.timer = params[0];
        this.xRange = params[1];
        this.yRange = params[2];
        // ROM: initial $2E = 3-1 (same as between-explosion wait)
        this.intervalCounter = SPAWN_INTERVAL;
    }

    public void tick() {
        if (timer < 0) return;
        intervalCounter--;
        if (intervalCounter >= 0) {
            return;
        }
        // ROM: Obj_NormalExpControl fires when Obj_Wait counter goes negative
        timer--;
        if (timer < 0) {
            return;
        }
        // ROM: sub_52850 plays sfx_Explode, spawns child, applies random offset
        spawnExplosionChild();
        intervalCounter = SPAWN_INTERVAL - 1; // ROM: move.w #3-1,$2E(a0)
    }

    public boolean isFinished() {
        return timer < 0;
    }

    private void spawnExplosionChild() {
        // ROM: sub_52850 random offset calculation (s3.asm:101267-101285)
        int random = rng.nextRaw();
        int xMask = (xRange * 2) - 1;
        int yMask = (yRange * 2) - 1;
        int xOffset = (random & xMask) - xRange;
        int yOffset = ((random >> 8) & yMask) - yRange;
        pendingExplosions.add(new PendingExplosion(centreX + xOffset, centreY + yOffset, true));
    }

    public List<PendingExplosion> drainPendingExplosions() {
        List<PendingExplosion> result = List.copyOf(pendingExplosions);
        pendingExplosions.clear();
        return result;
    }
}
