package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.boss.AbstractBossInstance;

import java.util.List;

/**
 * CNZ Act 1 miniboss base, promoted to {@link AbstractBossInstance}.
 *
 * <p>ROM anchors:
 * {@code Obj_CNZMiniboss}, {@code Obj_CNZMinibossInit},
 * {@code Obj_CNZMinibossLower}, and {@code Obj_CNZMinibossLower2}
 * in {@code sonic3k.asm} (dispatch table at line 144874).
 *
 * <p>Workstream D Task 3 moves the Task 7 scaffold onto the shared boss
 * base so downstream tasks (T4-T6) can add the 8-routine state machine,
 * per-hit coil behaviour, and defeat sequencer without re-threading the
 * boss-state plumbing. This commit preserves the Task 7 seams that the
 * headless harness depends on:
 * <ul>
 *   <li>{@link #onArenaChunkDestroyed()} still lowers the base by one
 *       0x20-pixel arena row per top-piece impact.</li>
 *   <li>{@link #getCentreX()}/{@link #getCentreY()} mirror the boss
 *       position held in {@link com.openggf.level.objects.boss.BossStateContext}.</li>
 *   <li>{@link #appendRenderCommands(List)} remains a no-op — the real
 *       rendering lands with the full state machine in a later task.</li>
 * </ul>
 *
 * <p>Hit count is seeded from
 * {@link Sonic3kConstants#CNZ_MINIBOSS_HIT_COUNT} (ROM
 * {@code Obj_CNZMinibossInit} — {@code sonic3k.asm:144888},
 * {@code move.b #6,collision_property(a0)}).
 */
public final class CnzMinibossInstance extends AbstractBossInstance {
    /**
     * Each top-piece impact corresponds to one 0x20-pixel arena block row.
     *
     * <p>ROM: {@code Obj_CNZMinibossTop} snaps impact positions to the 0x20 grid
     * before calling {@code CNZMiniboss_BlockExplosion}. The base's visible
     * lowering later happens one pixel per frame via {@code Obj_CNZMinibossLower2},
     * but the Task 7 seam only needs to prove that the base reacts to the same
     * row-granularity destruction events. Advancing by one row per published hit
     * is therefore the narrowest faithful adaptation for this slice and remains
     * exercised by {@code TestS3kCnzMinibossArenaHeadless}.
     */
    private static final int LOWERING_STEP_PIXELS = 0x20;

    public CnzMinibossInstance(ObjectSpawn spawn) {
        super(spawn, "CNZMiniboss");
    }

    @Override
    protected void initializeBossState() {
        // ROM: routine 0 = Obj_CNZMinibossInit (sonic3k.asm:144885).
        // state.hitCount is already seeded to CNZ_MINIBOSS_HIT_COUNT by the
        // super constructor via getInitialHitCount(); state.x/y are seeded
        // from the spawn. T4-T6 will layer on the wait timer, descent y_vel,
        // and coil arming that CNZMiniboss_Index dispatches to.
        state.routine = 0;
    }

    @Override
    protected int getInitialHitCount() {
        // ROM: Obj_CNZMinibossInit — sonic3k.asm:144888,
        // move.b #6,collision_property(a0).
        return Sonic3kConstants.CNZ_MINIBOSS_HIT_COUNT;
    }

    @Override
    protected void updateBossLogic(int frameCounter, PlayableEntity player) {
        // T4-T6 will dispatch routines 0/2/4/6/8/A/C/E per CNZMiniboss_Index
        // (sonic3k.asm:144874). Left intentionally empty here so the Task 7
        // arena/lowering contract stays isolated from the incoming state
        // machine.
    }

    @Override
    protected void onHitTaken(int remainingHits) {
        // T6 will implement the palette-flash SFX and per-hit coil behaviour
        // from CNZMiniboss_CheckPlayerHit. Task 3 only establishes the seam.
    }

    @Override
    protected int getCollisionSizeIndex() {
        // Placeholder until Task 6 wires the ROM-accurate hitbox from
        // ObjDat_CNZMiniboss.
        return 0x0F;
    }

    @Override
    protected int getBossHitSfxId() {
        // Matches every other S3K miniboss (AIZ/HCZ/MGZ) and the
        // Boss_HandleHits pattern shared via AbstractBossInstance.
        return Sonic3kSfx.BOSS_HIT.id;
    }

    @Override
    protected int getBossExplosionSfxId() {
        return Sonic3kSfx.EXPLODE.id;
    }

    /**
     * Consumes one arena-destruction step from the top piece.
     *
     * <p>The real boss tracks more state than a single Y shift, but the Task 7
     * contract — still exercised by {@code TestS3kCnzMinibossArenaHeadless} —
     * is that a top hit causes the base to lower. Keeping the effect in this
     * dedicated method documents that the top-piece collision is what drives
     * the lowering handoff.
     */
    public void onArenaChunkDestroyed() {
        state.y += LOWERING_STEP_PIXELS;
    }

    /**
     * Returns the ROM-style centre X coordinate, backed by
     * {@code state.x} so the position stays consistent with the shared
     * boss-state plumbing in {@link AbstractBossInstance}.
     */
    public int getCentreX() {
        return state.x;
    }

    /**
     * Returns the ROM-style centre Y coordinate, backed by
     * {@code state.y} so lowering writes flow through the same field
     * that {@link AbstractBossInstance#getY()} exposes.
     */
    public int getCentreY() {
        return state.y;
    }

    /**
     * Returns the remaining ROM hit counter.
     *
     * <p>Surfaces the shared boss-state hit counter for the Task 3 test,
     * which asserts it is seeded from
     * {@link Sonic3kConstants#CNZ_MINIBOSS_HIT_COUNT} via
     * {@link #getInitialHitCount()}. T6 will also rely on this counter
     * inside {@link #onHitTaken(int)}.
     */
    public int getRemainingHits() {
        return state.hitCount;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Rendering parity remains out of workstream-D scope (plan section 9).
        // The full sprite/DPLC pipeline will be wired alongside the T4-T6
        // state machine; leaving this empty keeps the seam explicit and
        // stops accidental placeholder rendering from masking parity gaps.
    }
}
