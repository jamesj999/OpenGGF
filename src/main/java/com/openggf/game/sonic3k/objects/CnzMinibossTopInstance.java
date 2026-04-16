package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.events.S3kCnzEventWriteSupport;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;

import java.util.List;

/**
 * Minimal CNZ miniboss top piece for Task 7.
 *
 * <p>ROM anchors:
 * {@code Obj_CNZMinibossTop}, {@code loc_6DD64}, {@code loc_6DDA2}, and
 * {@code CNZMiniboss_BlockExplosion}.
 *
 * <p>The full ROM object bounces around the arena, checks wall/floor/ceiling
 * contacts, and publishes snapped impact coordinates through
 * {@code Events_bg+$00/$02}. Task 7 only needs the collision handoff itself:
 * when the top piece reports an arena hit, the engine must queue the same
 * chunk-destruction coordinates through the CNZ event bridge and notify the
 * base object that one destruction row has been earned.
 */
public final class CnzMinibossTopInstance extends AbstractObjectInstance {
    private CnzMinibossInstance boss;
    private boolean arenaCollisionPending;
    private int pendingChunkWorldX;
    private int pendingChunkWorldY;

    public CnzMinibossTopInstance(ObjectSpawn spawn) {
        super(spawn, "CNZMinibossTop");
    }

    /**
     * Test seam used to make the parent/base dependency explicit without
     * requiring the full child-object spawn chain from the real boss.
     */
    public void attachBossForTest(CnzMinibossInstance boss) {
        this.boss = boss;
    }

    /**
     * Schedules one ROM-shaped arena collision.
     *
     * <p>The inputs are already expected to be world coordinates aligned to the
     * same block grid that {@code CNZMiniboss_BlockExplosion} uses after masking
     * with {@code $FFE0} and adding {@code $10}. The test intentionally passes
     * the snapped values directly so Task 7 stays focused on the publication
     * path rather than on collision geometry.
     */
    public void forceArenaCollisionForTest(int chunkWorldX, int chunkWorldY) {
        arenaCollisionPending = true;
        pendingChunkWorldX = chunkWorldX;
        pendingChunkWorldY = chunkWorldY;
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        if (!arenaCollisionPending) {
            return;
        }
        arenaCollisionPending = false;

        /**
         * ROM: the top piece writes the snapped impact coordinates into the CNZ
         * event scratch area before spawning the explosion child. Task 7 exports
         * the same effect through the explicit write-support helper so the
         * object -> bridge -> events dependency is visible and testable.
         */
        S3kCnzEventWriteSupport.queueArenaChunkDestruction(
                services(), pendingChunkWorldX, pendingChunkWorldY);

        /**
         * The base-lowering reaction remains object-owned. The bridge is
         * responsible for the shared event state; the base is responsible for
         * its own follow-on motion.
         */
        if (boss != null) {
            boss.onArenaChunkDestroyed();
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Task 7 covers state publication only.
    }
}
