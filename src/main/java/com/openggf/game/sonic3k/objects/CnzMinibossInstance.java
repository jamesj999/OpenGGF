package com.openggf.game.sonic3k.objects;

import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;

import java.util.List;

/**
 * Minimal CNZ Act 1 miniboss base for Task 7.
 *
 * <p>ROM anchors:
 * {@code Obj_CNZMiniboss}, {@code Obj_CNZMinibossLower}, and
 * {@code Obj_CNZMinibossLower2} in {@code sonic3k.asm}.
 *
 * <p>The full boss owns camera locks, PLC/palette loads, timed swing motion,
 * coil vulnerability windows, and defeat cleanup. Task 7 intentionally stops
 * earlier: the tests only need a base object that can consume the
 * top-piece-produced arena-destruction steps and expose the resulting lowering
 * through a stable centre-coordinate accessor. That keeps the
 * object -> bridge -> events seam explicit without dragging in Task 8 or the
 * rest of the boss choreography.
 */
public final class CnzMinibossInstance extends AbstractObjectInstance {
    /**
     * Each top-piece impact corresponds to one 0x20-pixel arena block row.
     *
     * <p>ROM: {@code Obj_CNZMinibossTop} snaps impact positions to the 0x20 grid
     * before calling {@code CNZMiniboss_BlockExplosion}. The base's visible
     * lowering later happens one pixel per frame via {@code Obj_CNZMinibossLower2},
     * but Task 7 only needs to prove that the base reacts to the same
     * row-granularity destruction events. Advancing by one row per published hit
     * is therefore the narrowest faithful adaptation for this slice.
     */
    private static final int LOWERING_STEP_PIXELS = 0x20;

    private int centreX;
    private int centreY;

    public CnzMinibossInstance(ObjectSpawn spawn) {
        super(spawn, "CNZMiniboss");
        this.centreX = spawn.x();
        this.centreY = spawn.y();
    }

    /**
     * Consumes one arena-destruction step from the top piece.
     *
     * <p>The real boss tracks more state than a single Y shift, but the only
     * Task 7 contract is that a top hit causes the base to lower. Keeping the
     * effect in this dedicated method documents that the top-piece collision is
     * what drives the lowering handoff.
     */
    public void onArenaChunkDestroyed() {
        centreY += LOWERING_STEP_PIXELS;
    }

    /**
     * Returns the ROM-style centre X coordinate.
     */
    public int getCentreX() {
        return centreX;
    }

    /**
     * Returns the ROM-style centre Y coordinate.
     */
    public int getCentreY() {
        return centreY;
    }

    @Override
    public int getX() {
        return centreX;
    }

    @Override
    public int getY() {
        return centreY;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Task 7 validates the bridge and lowering contract only. Rendering and
        // animation parity remain outside this bounded slice.
    }
}
