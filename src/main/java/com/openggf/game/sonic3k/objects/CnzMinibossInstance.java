package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.boss.AbstractBossInstance;
import com.openggf.physics.SwingMotion;

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
 *
 * <p>Workstream D Task 4 layers in the first three dispatch-table routines
 * from {@code CNZMiniboss_Index} (sonic3k.asm:144874):
 * {@code Obj_CNZMinibossInit} (routine 0, line 144885),
 * {@code Obj_CNZMinibossLower} (routine 2, line 144898) and
 * {@code Obj_CNZMinibossMove} (routine 4, line 144912), plus the
 * {@code Obj_CNZMinibossGo2}/{@code Obj_CNZMinibossGo3} {@code $34(a0)}
 * post-wait callbacks (lines 144903/144918). Routines 6/8/A/C/E and the
 * {@code Obj_CNZMinibossChangeDir} sign flip are left untouched so T5/T6
 * can add them without reworking the wait-timer plumbing.
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

    // ---- Routine indices (CNZMiniboss_Index, sonic3k.asm:144874) ----
    private static final int ROUTINE_INIT = 0;   // Obj_CNZMinibossInit   (144885)
    private static final int ROUTINE_LOWER = 2;  // Obj_CNZMinibossLower  (144898)
    private static final int ROUTINE_MOVE = 4;   // Obj_CNZMinibossMove   (144912)
    // Routines 6/8/A/C/E (ChangeDir/Opening/WaitHit/Closing/Lower2) land in T5/T6.

    /**
     * ROM: Swing_UpAndDown uses {@code $3E(a0)} as the peak |y_vel|.
     * {@code SetUp_CNZMinibossSwing} (sonic3k.asm:145393) writes {@code $60}.
     */
    private static final int SWING_MAX_VEL = 0x60;
    /**
     * ROM: Swing_UpAndDown uses {@code $40(a0)} as the per-frame acceleration.
     * {@code SetUp_CNZMinibossSwing} (sonic3k.asm:145397) writes {@code 8}.
     */
    private static final int SWING_ACCEL = 8;

    // ---- Scratch state for the ROM's $2E(a0) wait + $34(a0) post-wait handler. ----
    /**
     * Current {@code x_vel} magnitude+sign, mirroring ROM {@code x_vel(a0)}.
     * Kept as a private short here rather than on {@code BossStateContext} because
     * only the CNZ miniboss swing uses it at the granularity the tests need.
     */
    private short xVel;
    /** Current {@code y_vel}, mirroring ROM {@code y_vel(a0)}. */
    private short yVel;
    /** ROM: {@code $2E(a0)} wait-frame counter. -1 indicates "no wait pending". */
    private int waitTimer = -1;
    /** ROM: {@code $34(a0)} post-wait handler pointer. Fires when {@link #waitTimer} expires. */
    private Runnable waitCallback;
    /**
     * ROM: bit 0 of {@code $38(a0)} — Swing_UpAndDown direction flag.
     * {@code false} = ascending (velocity decreasing toward {@code -SWING_MAX_VEL}),
     * {@code true} = descending (velocity increasing toward {@code +SWING_MAX_VEL}).
     * Cleared by {@code SetUp_CNZMinibossSwing} (sonic3k.asm:145398,
     * {@code bclr #0,$38(a0)}).
     */
    private boolean swingDirectionDown;

    public CnzMinibossInstance(ObjectSpawn spawn) {
        super(spawn, "CNZMiniboss");
    }

    @Override
    protected void initializeBossState() {
        // ROM: routine 0 = Obj_CNZMinibossInit (sonic3k.asm:144885).
        // state.hitCount is already seeded to CNZ_MINIBOSS_HIT_COUNT by the
        // super constructor via getInitialHitCount(); state.x/y are seeded
        // from the spawn. The remainder of Init (y_vel / wait timer /
        // $34 post-wait handler) runs on the first update() via updateInit().
        state.routine = ROUTINE_INIT;
    }

    @Override
    protected int getInitialHitCount() {
        // ROM: Obj_CNZMinibossInit — sonic3k.asm:144888,
        // move.b #6,collision_property(a0).
        return Sonic3kConstants.CNZ_MINIBOSS_HIT_COUNT;
    }

    @Override
    protected void updateBossLogic(int frameCounter, PlayableEntity player) {
        // CNZMiniboss_Index dispatch (sonic3k.asm:144874). Routines 6/8/A/C/E
        // will be added in T5/T6; the default branch keeps this update
        // idempotent until then so the routine counter can safely advance
        // past 4 without tripping an unimplemented branch.
        switch (state.routine) {
            case ROUTINE_INIT -> updateInit();
            case ROUTINE_LOWER -> updateLower();
            case ROUTINE_MOVE -> updateMove();
            default -> {
                // Routines 6/8/A/C/E handled by T5/T6.
            }
        }
        // ROM: Obj_Wait (sonic3k.asm:177944) — `subq.w #1,$2E(a0);
        // bmi.s loc_84892; rts`. Tick after the routine body so the post-wait
        // callback fires on the frame the timer reaches -1, matching the ROM
        // timing where `Obj_Wait` is the tail of each routine handler.
        tickWait();
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
     * ROM: Obj_CNZMinibossInit (sonic3k.asm:144885). The ROM sequence is:
     * <pre>
     *   jsr (SetUp_ObjAttributes).l      ; addq.b #2,routine(a0) (line 176909)
     *   move.b #6,collision_property(a0) ; hit counter — already seeded
     *   move.w #$80,y_vel(a0)            ; initial descent velocity
     *   move.w #$11F,$2E(a0)             ; wait 287 frames
     *   move.l #Obj_CNZMinibossGo2,$34(a0); post-wait handler
     *   jmp (CreateChild1_Normal).l      ; top piece — T5/T6 territory
     * </pre>
     * Top-piece child creation and {@code ObjDat_CNZMiniboss} attribute
     * copying stay deferred to T5/T6; this method only wires the state-machine
     * transition so the CNZMiniboss_Index dispatcher can keep advancing.
     */
    private void updateInit() {
        // ROM sonic3k.asm:144891 — move.w #$80,y_vel(a0).
        yVel = Sonic3kConstants.CNZ_MINIBOSS_INIT_Y_VEL;
        state.yVel = yVel;
        // ROM sonic3k.asm:144892-144893 — move.w #$11F,$2E(a0) +
        //                                 move.l #Obj_CNZMinibossGo2,$34(a0).
        setWait(Sonic3kConstants.CNZ_MINIBOSS_INIT_WAIT, this::onGo2);
        // ROM sonic3k.asm:176909 — SetUp_CNZMinibossInit's jsr
        // (SetUp_ObjAttributes).l ends with `addq.b #2,routine(a0)`, so
        // routine advances from 0 to ROUTINE_LOWER (2) on the same frame
        // that Init runs. This prevents Init from re-running next frame.
        state.routine = ROUTINE_LOWER;
    }

    /**
     * ROM: Obj_CNZMinibossLower (sonic3k.asm:144898). Two-line routine body:
     * {@code jsr (MoveSprite2).l} followed by {@code jmp (Obj_Wait).l}. The
     * Obj_Wait tail is handled by {@link #tickWait()} at the end of
     * {@link #updateBossLogic(int, PlayableEntity)} so both the move and
     * the wait decrement happen in the same update pass.
     */
    private void updateLower() {
        // ROM sonic3k.asm:144899 — jsr (MoveSprite2).l
        // (MoveSprite2 at sonic3k.asm:36053: `add.l d0,x_pos(a0)` /
        //  `add.l d0,y_pos(a0)` with d0 = vel<<8). BossStateContext.applyVelocity
        // performs the same 16:16 accumulation off state.xVel/state.yVel.
        state.xVel = xVel;
        state.yVel = yVel;
        state.applyVelocity();
    }

    /**
     * ROM: Obj_CNZMinibossMove (sonic3k.asm:144912). Three-line routine body:
     * {@code jsr (Swing_UpAndDown).l} + {@code jsr (MoveSprite2).l} +
     * {@code jmp (Obj_Wait).l}. Swing_UpAndDown lives at sonic3k.asm:177851
     * and is ported by {@link SwingMotion#update(int, int, int, boolean)}.
     */
    private void updateMove() {
        // ROM sonic3k.asm:144913 — jsr (Swing_UpAndDown).l. Swing uses
        // $40 (accel), $3E (max |y_vel|) and bit 0 of $38 (direction).
        SwingMotion.Result result = SwingMotion.update(
                SWING_ACCEL, yVel, SWING_MAX_VEL, swingDirectionDown);
        yVel = (short) result.velocity();
        swingDirectionDown = result.directionDown();

        // ROM sonic3k.asm:144914 — jsr (MoveSprite2).l (applies both x_vel
        // and y_vel). After Go3 fires, xVel carries the 0x100 swing magnitude.
        state.xVel = xVel;
        state.yVel = yVel;
        state.applyVelocity();
    }

    /**
     * ROM: Obj_CNZMinibossGo2 (sonic3k.asm:144903). Runs when
     * {@code Obj_CNZMinibossLower}'s wait timer expires:
     * <pre>
     *   move.b #4,routine(a0)                 ; ROUTINE_MOVE
     *   clr.w  y_vel(a0)                      ; overridden by swing setup
     *   bset   #1,$38(a0)                     ; $38 bit1 — T5/T6 flag
     *   move.w #$90,$2E(a0)                   ; wait 144 frames
     *   move.l #Obj_CNZMinibossGo3,$34(a0)    ; next handler
     *   bra.w  SetUp_CNZMinibossSwing         ; tail: writes $3E/$40/y_vel
     * </pre>
     */
    private void onGo2() {
        // ROM sonic3k.asm:144904 — move.b #4,routine(a0).
        state.routine = ROUTINE_MOVE;
        // ROM sonic3k.asm:144905 — clr.w y_vel(a0). Overridden below by
        // SetUp_CNZMinibossSwing, but we match the ROM write order so future
        // trace-replay of the $38 bit1 flag (T5/T6) lines up frame-for-frame.
        yVel = 0;
        // ROM sonic3k.asm:144906 — bset #1,$38(a0). $38 bits stay in T5/T6
        // (the coil/hit flags); leaving the comment here so T5 can wire it.
        // ROM sonic3k.asm:144907-144908 — move.w #$90,$2E(a0) +
        //                                 move.l #Obj_CNZMinibossGo3,$34(a0).
        setWait(Sonic3kConstants.CNZ_MINIBOSS_GO2_WAIT, this::onGo3);
        // ROM sonic3k.asm:144909 — bra.w SetUp_CNZMinibossSwing (tail call).
        setUpSwing();
    }

    /**
     * ROM: SetUp_CNZMinibossSwing (sonic3k.asm:145393). Seeds the
     * Swing_UpAndDown state used by {@code Obj_CNZMinibossMove} — peak
     * |y_vel| = {@code $60}, initial y_vel = {@code +$60}, accel = {@code 8},
     * direction bit cleared ({@code bclr #0,$38(a0)}).
     */
    private void setUpSwing() {
        // ROM sonic3k.asm:145394-145396 — move.w #$60,d0 / move.w d0,$3E(a0) /
        //                                 move.w d0,y_vel(a0).
        yVel = (short) SWING_MAX_VEL;
        // ROM sonic3k.asm:145397 — move.w #8,$40(a0). SWING_ACCEL constant.
        // (Acceleration is read per frame from SWING_ACCEL; no field to set.)
        // ROM sonic3k.asm:145398 — bclr #0,$38(a0). Swing starts ascending.
        swingDirectionDown = false;
    }

    /**
     * ROM: Obj_CNZMinibossGo3 (sonic3k.asm:144918). Runs when
     * {@code Obj_CNZMinibossMove}'s wait timer expires:
     * <pre>
     *   move.w #$100,x_vel(a0)   ; swing magnitude
     *   move.w #$9F,$2E(a0)      ; wait 159 frames
     *   ; falls through to Obj_CNZMinibossCloseGo (sonic3k.asm:144922) —
     *   ; advances routine to 6, clears $38 bit3, writes palette-rotation
     *   ; pointers. T5 picks up the CloseGo fallthrough so routines 6/8/A
     *   ; can dispatch without mid-state rendering hazards.
     * </pre>
     * Leaving the {@link #waitCallback} null for the new $9F-frame wait
     * matches the ROM state *between* Go3 writing {@code $2E} and CloseGo
     * writing {@code $34} when T5 lands, and keeps
     * {@code updateBossLogic}'s default branch quiescent if the wait
     * expires before T5 is merged.
     */
    private void onGo3() {
        // ROM sonic3k.asm:144919 — move.w #$100,x_vel(a0).
        xVel = Sonic3kConstants.CNZ_MINIBOSS_SWING_X_VEL;
        // ROM sonic3k.asm:144920 — move.w #$9F,$2E(a0). T5 will wire the
        // $34 Obj_CNZMinibossChangeDir callback, the bclr #3,$38 and the
        // palette-rotation pointer copy via the CloseGo fallthrough.
        setWait(Sonic3kConstants.CNZ_MINIBOSS_SWING_WAIT, null);
    }

    /**
     * ROM: Obj_Wait (sonic3k.asm:177944) dispatch helper — writes {@code $2E(a0)}
     * and the {@code $34(a0)} next-handler pointer. Clearing
     * {@code waitCallback} to null via a {@code setWait(frames, null)} call
     * (see {@link #onGo3()}) leaves the callback slot empty until T5 wires
     * the downstream handler.
     */
    private void setWait(int frames, Runnable callback) {
        waitTimer = frames;
        waitCallback = callback;
    }

    /**
     * ROM: Obj_Wait (sonic3k.asm:177944) —
     * {@code subq.w #1,$2E(a0); bmi.s loc_84892; rts}. When the timer goes
     * below zero, jumps to {@code $34(a0)} (our {@link #waitCallback}).
     */
    private void tickWait() {
        if (waitTimer < 0) {
            return;
        }
        waitTimer--;
        if (waitTimer >= 0) {
            return;
        }
        Runnable callback = waitCallback;
        waitCallback = null;
        if (callback != null) {
            callback.run();
        }
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

    // ---- Test-only accessors for the Task 4 state machine ----
    // Package-private, read-only — exposed so TestCnzMinibossSwingPhase
    // can assert on $2E/$34 timer state and routine transitions without
    // widening the public API. T5/T6 follow the same convention.

    /** ROM: current {@code routine(a0)}. Test-only visibility. */
    int getCurrentRoutine() {
        return state.routine;
    }

    /** ROM: current {@code x_vel(a0)}. Test-only visibility. */
    short getCurrentXVel() {
        return xVel;
    }

    /** ROM: current {@code y_vel(a0)}. Test-only visibility. */
    short getCurrentYVel() {
        return yVel;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Rendering parity remains out of workstream-D scope (plan section 9).
        // The full sprite/DPLC pipeline will be wired alongside the T4-T6
        // state machine; leaving this empty keeps the seam explicit and
        // stops accidental placeholder rendering from masking parity gaps.
    }
}
