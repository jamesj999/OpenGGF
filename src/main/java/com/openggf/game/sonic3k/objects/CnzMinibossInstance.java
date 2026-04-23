package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameModule;
import com.openggf.game.GameServices;
import com.openggf.game.LevelEventProvider;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.events.Sonic3kCNZEvents;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.boss.AbstractBossInstance;
import com.openggf.physics.SwingMotion;

import java.util.List;
import java.util.logging.Logger;

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
 * post-wait callbacks (lines 144903/144918).
 *
 * <p>Workstream D Task 5 extends the dispatch with the next three routines
 * and their {@code $34(a0)} callbacks, keeping the ROM dispatch table
 * canonical (sonic3k.asm:144874..144882):
 * <ul>
 *   <li>routine 6 — duplicate Move slot entered by
 *       {@code Obj_CNZMinibossCloseGo} (sonic3k.asm:144922). The Move
 *       body keeps swinging while the wait-timer cadence ({@code $9F}
 *       from Go3, then {@code $13F}) fires
 *       {@code Obj_CNZMinibossChangeDir} (sonic3k.asm:144935) to
 *       negate {@code x_vel}.</li>
 *   <li>routine 8 — {@code Obj_CNZMinibossOpening} (sonic3k.asm:144941),
 *       a {@code Animate_RawMultiDelay} body. Without the full S3K
 *       animation pipeline the engine models it as a finite wait whose
 *       {@code $34} callback is {@code Obj_CNZMinibossOpenGo}
 *       (sonic3k.asm:144945), which advances to routine A and sets
 *       {@code $38} bit 6 (Open state).</li>
 *   <li>routine A — {@code Obj_CNZMinibossWaitHit} (sonic3k.asm:144954).
 *       Gates on {@code btst #6,status(a0)}; if clear the body
 *       {@code rts} (no timer tick, no position update). On hit the
 *       handler {@code loc_6DB4E} (sonic3k.asm:144960) clears
 *       {@code $38} bit 6, installs the closing animation, writes
 *       {@code $34 = Obj_CNZMinibossCloseGo}, and advances routine to
 *       C. Routine C's body stays deferred to T6; T5 only performs the
 *       transition write.</li>
 * </ul>
 *
 * <p>Task 5 also relocates {@link #tickWait()} from a global dispatch
 * tail into the individual routine bodies that match the ROM's
 * {@code Obj_Wait} tail (Lower, Move, Opening). This mirrors the AIZ
 * miniboss pattern (see {@code AizMinibossInstance}) and removes the
 * 1-frame early-dispatch skew that T4 left pending — a wait timer
 * written by a {@code $34} callback now decrements the first time on
 * the subsequent frame, not the same frame it was set.
 */
public final class CnzMinibossInstance extends AbstractBossInstance {
    private static final Logger LOG = Logger.getLogger(CnzMinibossInstance.class.getName());

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
    private static final int ROUTINE_INIT = 0;     // Obj_CNZMinibossInit     (144885)
    private static final int ROUTINE_LOWER = 2;    // Obj_CNZMinibossLower    (144898)
    private static final int ROUTINE_MOVE = 4;     // Obj_CNZMinibossMove     (144912)
    private static final int ROUTINE_MOVE_DUP = 6; // Obj_CNZMinibossMove     (144912, duplicate slot)
    private static final int ROUTINE_OPENING = 8;  // Obj_CNZMinibossOpening  (144941)
    private static final int ROUTINE_WAIT_HIT = 0xA; // Obj_CNZMinibossWaitHit (144954)
    private static final int ROUTINE_CLOSING = 0xC;  // Obj_CNZMinibossClosing (144968)
    private static final int ROUTINE_LOWER2 = 0xE;   // Obj_CNZMinibossLower2  (144972)
    // Note: Obj_CNZMinibossEnd (sonic3k.asm:144984) is NOT a dispatch-table
    // routine — it is installed via $34(a0) by CNZMiniboss_BossDefeated
    // (sonic3k.asm:145467) after routine (a0) has been replaced with
    // Wait_FadeToLevelMusic. See onDefeatStarted() / onEndGo().

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

    /**
     * ROM: {@code Obj_CNZMinibossOpenGo} (sonic3k.asm:144949) writes
     * {@code move.b #$7F,$3B(a0)} — a {@code $7F} (127-frame) counter
     * representing the duration of the Opening animation. The engine
     * uses this as the routine-8 {@code $2E} wait since we lack the
     * full {@code Animate_RawMultiDelay} script engine.
     */
    private static final int CNZ_MINIBOSS_OPENING_WAIT = 0x7F;

    /**
     * Approximate duration of the Closing animation (sonic3k.asm:144968 —
     * {@code Obj_CNZMinibossClosing}) before its $34 callback
     * ({@link #onCloseGo}) fires to restart the swing cycle.
     *
     * <p>The ROM body is {@code jmp (Animate_RawMultiDelay).l}, whose
     * exact frame count comes from the {@code AniRaw_CNZMinibossClosing}
     * script. Without the full animation engine we seed a conservative
     * {@code $40} (64-frame) wait matching the opening duration order of
     * magnitude. The value only matters for the forced-entry test seam —
     * natural entry from {@link #handleWaitHitHandoff()} does not write a
     * timer, and the ROM's AniRaw engine drives the transition there.
     */
    private static final int CNZ_MINIBOSS_CLOSING_WAIT = 0x40;

    /**
     * Post-defeat wait before {@code Obj_CNZMinibossEndGo} fires.
     *
     * <p>ROM: {@code CNZMiniboss_BossDefeated} (sonic3k.asm:145464)
     * replaces the object entry with {@code Wait_FadeToLevelMusic} and
     * installs {@code Obj_CNZMinibossEnd} at {@code $34(a0)}. The fade
     * loop runs for approximately {@code $60} frames before yielding to
     * the {@code $34} callback. The engine models this with a single
     * wait timer since the fade itself is orthogonal to workstream-D.
     */
    private static final int CNZ_MINIBOSS_DEFEAT_WAIT = 0x60;

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
    /**
     * ROM: bit 6 of {@code $38(a0)} — Open-state flag.
     * Set by {@code Obj_CNZMinibossOpenGo} (sonic3k.asm:144948,
     * {@code bset #6,$38(a0)}) when routine advances to WaitHit.
     * Cleared by {@code loc_6DB4E} (sonic3k.asm:144962,
     * {@code bclr #6,$38(a0)}) when a hit lands during WaitHit, and by
     * {@code Obj_CNZMinibossCloseGo} (sonic3k.asm:144925,
     * {@code bclr #3,$38(a0)} — different bit, kept here alongside the
     * Open-state bit for documentation completeness).
     */
    private boolean openState;
    /**
     * ROM: bit 6 of {@code status(a0)} — top-hit-in-progress marker.
     * Set by {@code CNZMiniboss_CheckTopHit} (sonic3k.asm:145442,
     * {@code bset #6,status(a0)}) when the top-piece child reports a
     * successful hit on the base. Cleared by {@code loc_6DB4E}
     * (sonic3k.asm:144957-144962 path) once WaitHit reacts to the hit
     * and transitions to Closing.
     *
     * <p>Distinct from {@link #openState} ({@code $38} bit 6) despite the
     * overlapping bit index — the ROM uses two separate byte-offsets.
     */
    private boolean statusBit6TopHit;

    /**
     * ROM: bit 1 of {@code $38(a0)} — top-piece "Move" signal.
     * Set by {@code Obj_CNZMinibossGo2} (sonic3k.asm:144906,
     * {@code bset #1,$38(a0)}) when the base finishes its Lower routine
     * and drops into the Move/Swing body. The top-piece child polls this
     * bit inside {@code Obj_CNZMinibossTopWait} (sonic3k.asm:145027) and
     * only advances to {@code Wait2}/{@code Main} once the parent has
     * flagged it.
     */
    private boolean parentSignalBit1;

    /**
     * ROM: {@code $43(a0)} — {@code Obj_CNZMinibossLower2} countdown
     * (sonic3k.asm:144974). {@code -1} indicates "no Lower2 run in
     * progress", since the ROM normally writes a fresh counter on entry.
     */
    private int lower2Counter = -1;

    /**
     * ROM: {@code $42(a0)} — routine value to restore when the Lower2
     * counter expires (sonic3k.asm:144980,
     * {@code move.b $42(a0),routine(a0)}).
     */
    private int lower2PreviousRoutine;

    /**
     * Tracks whether {@link #onDefeatStarted()} has already fired.
     *
     * <p>ROM: {@code CNZMiniboss_CheckTopHit} only reaches
     * {@code CNZMiniboss_BossDefeated} on the hit that decrements
     * {@code $45(a0)} from 1 to 0 — subsequent top-piece hits are
     * ignored because the routine has already been replaced with
     * {@code Wait_FadeToLevelMusic}. This guard mirrors that once-only
     * semantic for the engine's {@code simulateHitForTest} path.
     */
    private boolean defeatInitiated;

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
        // After CNZMiniboss_BossDefeated (sonic3k.asm:145464), the ROM writes
        // Wait_FadeToLevelMusic into (a0) so the normal CNZMiniboss_Index
        // dispatcher stops running. The object simply ticks its $2E timer
        // until Obj_CNZMinibossEnd ($34 callback) fires. This mirrors that
        // behaviour: when defeated we skip routine dispatch entirely and
        // only advance the wait timer so onEndGo() eventually runs.
        if (state.defeated) {
            tickWait();
            return;
        }

        // CNZMiniboss_Index dispatch (sonic3k.asm:144874). All eight routines
        // in the ROM dispatch table (0/2/4/6/8/A/C/E) are handled. Each routine
        // body that matches the ROM's `Obj_Wait` tail (Lower, Move, Opening,
        // Closing) calls tickWait() itself at the end — Init / WaitHit /
        // Lower2 / one-shot $34 callbacks have no Obj_Wait in ROM and
        // therefore don't tick here.
        switch (state.routine) {
            case ROUTINE_INIT -> updateInit();
            case ROUTINE_LOWER -> updateLower();
            case ROUTINE_MOVE, ROUTINE_MOVE_DUP -> updateMove();
            case ROUTINE_OPENING -> updateOpening();
            case ROUTINE_WAIT_HIT -> updateWaitHit();
            case ROUTINE_CLOSING -> updateClosing();
            case ROUTINE_LOWER2 -> updateLower2();
            default -> {
                // With every ROM dispatch-table slot (0/2/4/6/8/A/C/E) now
                // covered above, this branch is unreachable during normal
                // boss life. A log at FINE level preserves the belt-and-
                // suspenders signal for a future out-of-range routine write
                // (e.g. a typo or a T12 trace-replay regression) without
                // spamming normal runs. Visible via Logger configuration.
                final int routine = state.routine;
                LOG.fine(() -> "CNZ miniboss: unhandled routine "
                        + Integer.toHexString(routine));
            }
        }
    }

    @Override
    protected void onHitTaken(int remainingHits) {
        // ROM: CNZMiniboss_CheckTopHit at sonic3k.asm:145435.
        //   subq.b #1,$45(a0)
        //   beq.s CNZMiniboss_BossDefeated
        //   bset #6,status(a0)          ; top-hit latch — handled separately
        //                                 by simulateHitForTest / the natural
        //                                 top-piece collision path.
        //   move.b #$20,$20(a0)         ; 32-frame stun — AbstractBossInstance
        //                                 handles invulnerability timing.
        //   Play_SFX sfx_BossHit        ; routed through getBossHitSfxId().
        //
        // ROM note: the real defeat counter is $45(a0) (= 4 at Init, line
        // 144889). The engine currently collapses this with collision_property
        // ($45 and the 6-entry counter at line 144888) into a single shared
        // state.hitCount seeded from CNZ_MINIBOSS_HIT_COUNT. Exact hit-count
        // parity is a T12 / post-D trace-replay concern; leaving this note
        // here for follow-up.
        if (remainingHits == 0 && !defeatInitiated) {
            defeatInitiated = true;
            onDefeatStarted();
        }
    }

    @Override
    protected boolean usesDefeatSequencer() {
        // ROM: CNZMiniboss_BossDefeated (sonic3k.asm:145464) replaces the
        // object routine with Wait_FadeToLevelMusic and installs
        // Obj_CNZMinibossEnd at $34(a0). That path is orthogonal to the
        // generic 179-frame exploding/fleeing/spawn-prison sequencer used by
        // Sonic 2 bosses — the CNZ miniboss has no flee-then-spawn phase,
        // only the fade-and-cleanup handoff. Returning false here routes
        // defeat through our onDefeatStarted() / onEndGo() pair and lets
        // updateBossLogic() keep ticking the $2E wait timer (via the
        // state.defeated early-return branch) until the $34 callback fires.
        return false;
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
     *
     * <p>Init does NOT tail-call {@code Obj_Wait} in the ROM — the
     * {@code jmp (CreateChild1_Normal).l} tail returns directly without
     * decrementing {@code $2E(a0)}. Therefore this body does not call
     * {@link #tickWait()}.
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
     * {@code jsr (MoveSprite2).l} followed by {@code jmp (Obj_Wait).l}.
     */
    private void updateLower() {
        // ROM sonic3k.asm:144899 — jsr (MoveSprite2).l
        // (MoveSprite2 at sonic3k.asm:36053: `add.l d0,x_pos(a0)` /
        //  `add.l d0,y_pos(a0)` with d0 = vel<<8). BossStateContext.applyVelocity
        // performs the same 16:16 accumulation off state.xVel/state.yVel.
        state.xVel = xVel;
        state.yVel = yVel;
        state.applyVelocity();
        // ROM sonic3k.asm:144900 — jmp (Obj_Wait).l tail.
        tickWait();
    }

    /**
     * ROM: Obj_CNZMinibossMove (sonic3k.asm:144912). Three-line routine body:
     * {@code jsr (Swing_UpAndDown).l} + {@code jsr (MoveSprite2).l} +
     * {@code jmp (Obj_Wait).l}. Swing_UpAndDown lives at sonic3k.asm:177851
     * and is ported by {@link SwingMotion#update(int, int, int, boolean)}.
     *
     * <p>Routine 6 (duplicate slot, sonic3k.asm:144878) dispatches to the
     * same body; the only difference between routine 4 and routine 6 is
     * which {@code $34} callback is armed on wait expiry
     * ({@code Obj_CNZMinibossGo3} for routine 4,
     * {@code Obj_CNZMinibossChangeDir} for routine 6).
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
        // ROM sonic3k.asm:144915 — jmp (Obj_Wait).l tail.
        tickWait();
    }

    /**
     * ROM: Obj_CNZMinibossOpening (sonic3k.asm:144941). The ROM body is a
     * tail-call into {@code Animate_RawMultiDelay}: it advances the
     * {@code $30(a0)} animation cursor and, once the script terminator
     * fires, jumps to the {@code $34(a0)} handler which is
     * {@code Obj_CNZMinibossOpenGo} (sonic3k.asm:144945).
     *
     * <p>The engine lacks a full {@code Animate_RawMultiDelay} pipeline,
     * so the routine is modelled as a fixed {@code CNZ_MINIBOSS_OPENING_WAIT}
     * wait (matching the {@code move.b #$7F,$3B(a0)} written by OpenGo
     * at sonic3k.asm:144949 — {@code $3B} is the ROM's Opening-frames
     * counter, which the animation script decrements). When the wait
     * expires, the armed callback {@link #onOpenGo()} runs and advances
     * state to routine A (WaitHit).
     */
    private void updateOpening() {
        // ROM sonic3k.asm:144942 — jmp (Animate_RawMultiDelay).l
        //   → tail Obj_Wait equivalent: decrement $2E; on expiry, fire $34.
        tickWait();
    }

    /**
     * ROM: Obj_CNZMinibossWaitHit (sonic3k.asm:144954):
     * <pre>
     *   btst   #6,status(a0)
     *   bne.s  loc_6DB4E
     *   rts
     * </pre>
     *
     * <p>With {@code status} bit 6 clear the body is a pure {@code rts} —
     * no timer decrement, no velocity application, no animation step. If
     * the top child's collision path sets {@link #statusBit6TopHit} via
     * {@code CNZMiniboss_CheckTopHit} (sonic3k.asm:145442), the routine
     * falls through to {@code loc_6DB4E} (sonic3k.asm:144960).
     */
    private void updateWaitHit() {
        // ROM sonic3k.asm:144955 — btst #6,status(a0).
        if (!statusBit6TopHit) {
            // ROM sonic3k.asm:144957 — rts (idle until top hit lands).
            return;
        }
        // ROM sonic3k.asm:144960 — loc_6DB4E.
        handleWaitHitHandoff();
    }

    /**
     * ROM: Obj_CNZMinibossClosing (sonic3k.asm:144968):
     * <pre>
     *   jmp (Animate_RawMultiDelay).l
     * </pre>
     *
     * <p>A pure animation body — the ROM defers the routine transition to the
     * animation script's terminator, which fires the {@code $34(a0)} callback
     * installed by {@link #handleWaitHitHandoff()} ({@link #onCloseGo()}).
     *
     * <p>Without a full {@code Animate_RawMultiDelay} pipeline the engine
     * models this as a wait-timer tick. In the natural flow,
     * {@code handleWaitHitHandoff} already armed {@link #waitCallback} =
     * {@link #onCloseGo} with {@link #waitTimer} = {@code -1}; the first
     * frame in this body seeds a conservative
     * {@link #CNZ_MINIBOSS_CLOSING_WAIT} countdown so subsequent frames can
     * tick it down and eventually fire the callback. When the routine is
     * forced by {@link #forceRoutineForTest(int)} the timer is seeded there
     * instead, skipping the self-seeding branch below.
     */
    private void updateClosing() {
        if (waitTimer < 0) {
            waitTimer = CNZ_MINIBOSS_CLOSING_WAIT;
            if (waitCallback == null) {
                waitCallback = this::onCloseGo;
            }
        }
        tickWait();
    }

    /**
     * ROM: Obj_CNZMinibossLower2 (sonic3k.asm:144972):
     * <pre>
     *   addq.w #1,y_pos(a0)
     *   subq.b #1,$43(a0)
     *   bmi.s loc_6DB7E
     *   rts
     * loc_6DB7E:
     *   move.b $42(a0),routine(a0)
     *   rts
     * </pre>
     *
     * <p>Drives the base's per-frame downward drift after each top-piece
     * impact snaps a fresh counter into {@code $43}. Counter underflow
     * ({@code bmi}) restores the saved routine from {@code $42(a0)} and
     * disarms the counter so the boss returns to its pre-Lower2 state.
     */
    private void updateLower2() {
        // ROM sonic3k.asm:144973 — addq.w #1,y_pos(a0). The ROM writes the
        // integer y_pos word directly without touching the fractional part.
        // Mirror that by nudging state.y AND state.yFixed so a subsequent
        // routine's MoveSprite2 (state.applyVelocity) accumulates from the
        // new pixel position, not the stale pre-Lower2 yFixed value.
        state.y += 1;
        state.yFixed += (1 << 16);
        // ROM sonic3k.asm:144974 — subq.b #1,$43(a0).
        lower2Counter--;
        if (lower2Counter < 0) {
            // ROM sonic3k.asm:144975-144976 — bmi.s loc_6DB7E.
            // ROM sonic3k.asm:144979-144981 — move.b $42(a0),routine(a0); rts.
            state.routine = lower2PreviousRoutine;
            // Disarm the counter so a future re-entry via forceRoutineForTest
            // re-seeds it cleanly rather than underflowing further on a stale
            // value.
            lower2Counter = -1;
        }
    }

    /**
     * ROM: CNZMiniboss_BossDefeated (sonic3k.asm:145464) +
     *       Obj_CNZMinibossEnd (sonic3k.asm:144984) +
     *       Obj_CNZMinibossEndGo (sonic3k.asm:144996).
     *
     * <pre>
     *   ; CNZMiniboss_BossDefeated (145465-145472)
     *   move.l #Wait_FadeToLevelMusic,(a0)
     *   bset   #7,status(a0)
     *   move.l #Obj_CNZMinibossEnd,$34(a0)
     *   st     (Events_fg_5).w
     *   jsr    (CreateChild1_Normal).l        ; a2 = coil-debris spawner
     *   jmp    (BossDefeated_StopTimer).l
     *
     *   ; Obj_CNZMinibossEnd (144984-144990)
     *   move.l #Obj_Wait,(a0)
     *   st     (_unkFAA8).w                   ; end-of-boss-level flag
     *   bset   #4,$38(a0)
     *   move.l #Obj_CNZMinibossEndGo,$34(a0)
     *   lea    Child6_CNZMinibossMakeDebris(pc),a2
     *   jmp    (CreateChild6_Simple).l
     *
     *   ; Obj_CNZMinibossEndGo (144996-145001)
     *   move.l #Obj_EndSignControlAwaitStart,(a0)
     *   clr.b  (Boss_flag).w
     *   jsr    (AfterBoss_Cleanup).l
     *   lea    (PLC_EndSignStuff).l,a1
     *   jmp    (Load_PLC_Raw).l
     * </pre>
     *
     * <p>The engine collapses the three ROM stages into two callbacks: this
     * method performs the combined BossDefeated / Obj_CNZMinibossEnd work
     * (flag writes + $34 arming), and {@link #onEndGo()} handles
     * Obj_CNZMinibossEndGo. The intermediate {@code Obj_Wait} stage between
     * them is modelled by the {@link #CNZ_MINIBOSS_DEFEAT_WAIT} timer
     * because the ROM's actual intermediate fade / debris spawn is
     * orthogonal to workstream-D.
     *
     * <p>Debris child spawn ({@code Child6_CNZMinibossMakeDebris}, ROM
     * line 144989) is deferred per workstream-D spec §9 — the headless
     * tests only assert on the Boss_flag clear / wall-grab release at
     * the end of the chain, not on the debris particles.
     */
    @Override
    protected void onDefeatStarted() {
        // ROM sonic3k.asm:145465 — move.l #Wait_FadeToLevelMusic,(a0).
        // ROM sonic3k.asm:145466 — bset #7,status(a0).
        // ROM sonic3k.asm:144987 — bset #4,$38(a0).
        // The engine doesn't model Wait_FadeToLevelMusic, status bit 7, or
        // $38 bit 4 separately; state.defeated + the wait-timer early-return
        // in updateBossLogic() cover the observable "routine dispatch is
        // paused while the post-fade callback counts down" contract.
        state.defeated = true;
        // Forward-looking insurance for a future T8 in-place respawn: clear
        // the top-piece "Move" signal so a fresh CnzMinibossTopInstance won't
        // observe a stale `bset #1,$38(a0)` from this object's previous life
        // before its own onGo2() latches it again.
        parentSignalBit1 = false;

        // ROM sonic3k.asm:145469 — st (Events_fg_5).w. This is the BG signal
        // that drives the post-boss arena-reveal chain in Sonic3kCNZEvents
        // (updateAct1Bg -> handleAct1Entry -> BG_FG_REFRESH).
        Sonic3kCNZEvents cnz = getCnzEvents();
        if (cnz != null) {
            cnz.setEventsFg5(true);
        }

        // ROM sonic3k.asm:145467 + 144988 — the combined BossDefeated /
        // Obj_CNZMinibossEnd chain installs Obj_CNZMinibossEndGo at $34(a0)
        // and relies on the fade/wait loop to tick it. Arm that here with a
        // conservative fade-shaped wait.
        setWait(CNZ_MINIBOSS_DEFEAT_WAIT, this::onEndGo);
    }

    /**
     * ROM: Obj_CNZMinibossEndGo (sonic3k.asm:144996):
     * <pre>
     *   move.l #Obj_EndSignControlAwaitStart,(a0)
     *   clr.b  (Boss_flag).w
     *   jsr    (AfterBoss_Cleanup).l
     *   lea    (PLC_EndSignStuff).l,a1
     *   jmp    (Load_PLC_Raw).l
     * </pre>
     *
     * <p>Final defeat step: clears the global {@code Boss_flag}, kicks off
     * the post-boss cleanup pass, and loads the end-sign PLC. The engine
     * only mirrors the {@code Boss_flag} clear here (plus the
     * wall-grab release which is the inverse of the arena-entry
     * {@code setWallGrabSuppressed(true)}) — {@code AfterBoss_Cleanup} and
     * {@code PLC_EndSignStuff} are owned by the wider CNZ event / PLC
     * systems and land in T10/T11.
     */
    private void onEndGo() {
        Sonic3kCNZEvents cnz = getCnzEvents();
        if (cnz != null) {
            // ROM sonic3k.asm:144998 — clr.b (Boss_flag).w.
            cnz.setBossFlag(false);
            // Inverse of the arena-entry setWallGrabSuppressed(true) in
            // Sonic3kCNZEvents#handleAct1Entry; re-enables wall-grab now
            // that the boss fight is over and the post-boss transition
            // will be driven by the existing Events_fg_5 / BG_FG_REFRESH
            // chain.
            cnz.setWallGrabSuppressed(false);
        }
        // ROM sonic3k.asm:144999 — jsr (AfterBoss_Cleanup).l — deferred to
        // the post-boss zone event flow (T10/T11 — music restart, camera
        // unlock handled by Sonic3kCNZEvents).
        // ROM sonic3k.asm:145000-145001 — PLC_EndSignStuff load — deferred
        // to the PLC slice (T10/T11).
    }

    /**
     * Resolves the active CNZ zone-events adapter, or {@code null} if the
     * level event manager isn't the expected Sonic3k type (e.g. during a
     * test fixture that hasn't finished CNZ bootstrap).
     */
    private static Sonic3kCNZEvents getCnzEvents() {
        GameModule module = GameServices.module();
        if (module == null) {
            return null;
        }
        LevelEventProvider provider = module.getLevelEventProvider();
        if (provider instanceof Sonic3kLevelEventManager events) {
            return events.getCnzEvents();
        }
        return null;
    }

    /**
     * ROM: loc_6DB4E (sonic3k.asm:144960):
     * <pre>
     *   move.b #$C,routine(a0)                 ; advance to Closing
     *   bclr   #6,$38(a0)                      ; clear Open-state flag
     *   move.l #AniRaw_CNZMinibossClosing,$30(a0)
     *   move.l #Obj_CNZMinibossCloseGo,$34(a0)
     *   rts
     * </pre>
     *
     * <p>The {@code $30} animation-cursor write and the Closing body itself
     * are T6 territory. For T5 we only perform the routine transition and
     * arm the CloseGo callback so the defeat loop is testable frame-for-frame
     * without mid-state rendering hazards.
     */
    private void handleWaitHitHandoff() {
        // ROM sonic3k.asm:144961 — move.b #$C,routine(a0).
        state.routine = ROUTINE_CLOSING;
        // ROM sonic3k.asm:144962 — bclr #6,$38(a0).
        openState = false;
        // Clear the top-hit latch so WaitHit can't re-fire if the code
        // ever re-dispatches to routine A before T6 wires the Closing body.
        statusBit6TopHit = false;
        // ROM sonic3k.asm:144964 — move.l #Obj_CNZMinibossCloseGo,$34(a0).
        // The ROM wait-timer for the Closing body comes from the animation
        // script (T6) — T5 arms the callback but leaves the timer -1 so
        // tickWait() stays quiescent until T6 wires the full Closing body.
        setWait(-1, this::onCloseGo);
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
        // ROM sonic3k.asm:144906 — bset #1,$38(a0). Top-piece "Move" signal
        // polled by Obj_CNZMinibossTopWait (sonic3k.asm:145027). Top-piece
        // Wait routine stalls on Refresh_ChildPosition until this latches.
        parentSignalBit1 = true;
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
     *   ; falls through to Obj_CNZMinibossCloseGo (sonic3k.asm:144922)
     * </pre>
     *
     * <p>T5 wires the {@code Obj_CNZMinibossCloseGo} fallthrough — the
     * $9F wait callback is {@link #onChangeDir()}, and the routine
     * advances to the duplicate-Move slot ({@link #ROUTINE_MOVE_DUP}).
     */
    private void onGo3() {
        // ROM sonic3k.asm:144919 — move.w #$100,x_vel(a0).
        xVel = Sonic3kConstants.CNZ_MINIBOSS_SWING_X_VEL;
        // ROM sonic3k.asm:144920 — move.w #$9F,$2E(a0). Then falls through
        // to Obj_CNZMinibossCloseGo which installs the ChangeDir callback
        // and advances the routine to the Move-duplicate slot (routine 6).
        setWait(Sonic3kConstants.CNZ_MINIBOSS_SWING_WAIT, this::onChangeDir);
        onCloseGo();
    }

    /**
     * ROM: Obj_CNZMinibossCloseGo (sonic3k.asm:144922):
     * <pre>
     *   move.b #6,routine(a0)
     *   move.l #Obj_CNZMinibossChangeDir,$34(a0)
     *   bclr   #3,$38(a0)
     *   lea    (PalSPtr_CNZMinibossNormal).l,a1
     *   lea    (Palette_rotation_data).w,a2
     *   move.l (a1)+,(a2)+
     *   move.l (a1)+,(a2)+
     *   clr.w  (a2)
     *   move.l #CNZMiniboss_MakeTimedSparks,(Palette_rotation_custom).w
     *   rts
     * </pre>
     *
     * <p>Entered two ways in the ROM:
     * <ol>
     *   <li>As a fallthrough from {@code Obj_CNZMinibossGo3} (routine 4 →
     *       routine 6 transition, with $9F wait still pending).</li>
     *   <li>As the {@code $34} callback fired when the Closing body
     *       completes its animation (routine C → routine 6 cycle restart).</li>
     * </ol>
     *
     * <p>The palette-rotation pointer copy and
     * {@code CNZMiniboss_MakeTimedSparks} custom handler are deferred to
     * T6 — that code is orthogonal to the state-machine transitions T5
     * covers, and the headless Opening test doesn't depend on it.
     */
    private void onCloseGo() {
        // ROM sonic3k.asm:144923 — move.b #6,routine(a0).
        state.routine = ROUTINE_MOVE_DUP;
        // ROM sonic3k.asm:144924 — move.l #Obj_CNZMinibossChangeDir,$34(a0).
        // The wait timer itself is preserved from whichever caller invoked
        // CloseGo: $9F if we fell through from Go3, or a fresh timer written
        // by the Closing callback in T6. T5's onGo3 writes $9F above, so
        // the first ChangeDir fires after $9F frames for the natural flow.
        waitCallback = this::onChangeDir;
        // ROM sonic3k.asm:144925 — bclr #3,$38(a0). Clears the in-hit-window
        // flag set by CNZMiniboss_CheckPlayerHit. Tracked alongside openState
        // for documentation; T6 wires the full $38 bit map.
        // ROM sonic3k.asm:144926-144931 — palette rotation pointer copy +
        // sparkle custom handler. Deferred to T6 (parallel to palette wiring).
    }

    /**
     * ROM: Obj_CNZMinibossChangeDir (sonic3k.asm:144935):
     * <pre>
     *   neg.w  x_vel(a0)
     *   move.w #$13F,$2E(a0)
     *   rts
     * </pre>
     *
     * <p>The ROM does NOT reassign {@code $34(a0)} here — the caller
     * ({@code Obj_CNZMinibossCloseGo}) already wrote ChangeDir as the
     * callback, and ChangeDir simply re-arms its own wait. This keeps
     * the boss swinging back and forth until a player hit kicks the
     * state machine into {@link #ROUTINE_OPENING} via
     * {@code CNZMiniboss_CheckPlayerHit} (sonic3k.asm:145413, which
     * writes {@code routine = 8} and {@code $34 = Obj_CNZMinibossOpenGo}).
     */
    private void onChangeDir() {
        // ROM sonic3k.asm:144936 — neg.w x_vel(a0).
        xVel = (short) -xVel;
        state.xVel = xVel;
        // ROM sonic3k.asm:144937 — move.w #$13F,$2E(a0).
        // $34 stays = ChangeDir so the swing keeps oscillating.
        setWait(Sonic3kConstants.CNZ_MINIBOSS_CHANGEDIR_WAIT, this::onChangeDir);
    }

    /**
     * ROM: Obj_CNZMinibossOpenGo (sonic3k.asm:144945):
     * <pre>
     *   move.b #$A,routine(a0)                 ; routine = WaitHit
     *   move.l #Obj_CNZMinibossChangeDir,$34(a0)
     *   bset   #6,$38(a0)                      ; Set Open state
     *   move.b #$7F,$3B(a0)
     *   lea    Child1_CNZCoilOpenSparks(pc),a2
     *   jmp    (CreateChild1_Normal).l
     * </pre>
     *
     * <p>Fires from the Opening body ({@link #updateOpening()}) when the
     * {@code Animate_RawMultiDelay}-equivalent wait expires. After this,
     * routine A ({@link #updateWaitHit()}) idles on
     * {@code btst #6,status(a0)} until the top child reports a hit.
     *
     * <p>The {@code $34 = ChangeDir} write is preserved for ROM parity —
     * it's inert in routine A (WaitHit never decrements $2E), but T6's
     * Closing body will re-read it. The
     * {@code Child1_CNZCoilOpenSparks} spawn and {@code $3B=$7F} counter
     * stay deferred to T6 (no art pipeline yet; no visible effect in the
     * headless WaitHit test).
     */
    private void onOpenGo() {
        // ROM sonic3k.asm:144946 — move.b #$A,routine(a0).
        state.routine = ROUTINE_WAIT_HIT;
        // ROM sonic3k.asm:144947 — move.l #Obj_CNZMinibossChangeDir,$34(a0).
        waitCallback = this::onChangeDir;
        waitTimer = -1; // WaitHit has no Obj_Wait tail — timer stays idle.
        // ROM sonic3k.asm:144948 — bset #6,$38(a0). Open-state flag.
        openState = true;
        // ROM sonic3k.asm:144949 — move.b #$7F,$3B(a0). $3B counter for
        // T6 (sparks spawn cadence / ring-spray framing). Not tracked
        // here because T5's WaitHit test only asserts the idle-vs-hit
        // routine state, not the coil-sparks child effect.
    }

    /**
     * ROM: Obj_Wait (sonic3k.asm:177944) dispatch helper — writes {@code $2E(a0)}
     * and the {@code $34(a0)} next-handler pointer. Setting
     * {@code frames = -1} leaves the timer quiescent (no post-wait fire)
     * while still letting the callback slot carry a pointer for ROM
     * parity with {@code $34} writes that occur outside of
     * {@code Obj_Wait} itself (e.g. {@link #handleWaitHitHandoff()}).
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

    /**
     * ROM: bit 1 of {@code $38(a0)} — top-piece "Move" signal.
     *
     * <p>Consumed by {@link CnzMinibossTopInstance#update(int,
     * com.openggf.game.PlayableEntity)} inside the {@code Wait} routine
     * ({@code Obj_CNZMinibossTopWait}, sonic3k.asm:145027) to decide
     * whether to fall through to {@code Wait2}/{@code Main} or stay in
     * {@code Refresh_ChildPosition}. Set by {@link #onGo2()} mirroring
     * {@code bset #1,$38(a0)} at sonic3k.asm:144906.
     */
    public boolean isParentSignalBit1Set() {
        return parentSignalBit1;
    }

    // ---- Test-only accessors for the Task 4/5 state machine ----
    // Package-private, read-only — exposed so TestCnzMinibossSwingPhase /
    // TestCnzMinibossOpeningPhase can assert on $2E/$34 timer state and
    // routine transitions without widening the public API. T6 follows the
    // same convention.

    /** ROM: current {@code routine(a0)}. Public for cross-package test access. */
    public int getCurrentRoutine() {
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

    /**
     * Forces the boss into a specific routine, emulating the state the
     * engine would be in mid-way through the ROM's natural dispatch
     * sequence. Package-private — test-only.
     *
     * <p>For routines that depend on a {@code $34} callback being armed
     * to behave correctly (most notably routine 6, whose body is
     * inert unless the wait timer is ticking toward a ChangeDir fire),
     * this helper also arms the relevant callback with
     * {@code waitTimer = 0} so the first {@link #tickWait()} fires it
     * immediately. This mirrors the ROM state that exists on the frame
     * when Go3 → CloseGo completes and the $9F wait is about to expire.
     */
    void forceRoutineForTest(int routine) {
        state.routine = routine;
        switch (routine) {
            case ROUTINE_MOVE_DUP -> {
                // ROM: Go3 → CloseGo leaves $34 = ChangeDir with $2E ticking
                // down from $9F; set the timer to 0 so the next tickWait()
                // fires ChangeDir immediately, which is what the T5 entry
                // test asserts (x_vel sign flips).
                waitTimer = 0;
                waitCallback = this::onChangeDir;
            }
            case ROUTINE_OPENING -> {
                // ROM: CheckPlayerHit writes routine=8 AND $34=OpenGo; the
                // Animate_RawMultiDelay body ticks $2E toward zero and fires
                // OpenGo when the animation completes. Seed a fresh wait so
                // updateOpening() has something to tick against.
                waitTimer = CNZ_MINIBOSS_OPENING_WAIT;
                waitCallback = this::onOpenGo;
            }
            case ROUTINE_WAIT_HIT -> {
                // ROM: WaitHit body has no Obj_Wait tail. Clear any stale
                // timer so the routine idles correctly until statusBit6TopHit
                // flips via simulateHitForTest().
                waitTimer = -1;
                waitCallback = null;
            }
            case ROUTINE_LOWER2 -> {
                // ROM: Lower2 has no Obj_Wait tail (subq.b $43 directly in
                // the body). Clear any stale wait state so it can't fire a
                // stray callback during the per-frame y++/$43 dance. Tests
                // that force Lower2 must follow up with
                // setLower2CounterForTest() to seed the $43 value.
                waitTimer = -1;
                waitCallback = null;
            }
            case ROUTINE_CLOSING -> {
                // ROM: Closing is normally entered from handleWaitHitHandoff
                // which writes $34 = onCloseGo with $2E = -1 (the AniRaw
                // engine drives the timing). When forced from a test, seed
                // a known wait length so updateClosing's first tick runs
                // against a deterministic counter.
                waitTimer = CNZ_MINIBOSS_CLOSING_WAIT;
                waitCallback = this::onCloseGo;
            }
            default -> {
                // Leave timer/callback untouched for routines that don't
                // require a specific callback to drive their body.
            }
        }
    }

    /**
     * Overrides the current {@code x_vel} magnitude+sign. Package-private —
     * test-only. Used by the Opening-phase test to seed a known swing
     * direction before forcing {@link #onChangeDir()} via
     * {@link #forceRoutineForTest(int)}.
     */
    void forceXVelForTest(short value) {
        xVel = value;
        state.xVel = value;
    }

    /**
     * Simulates the top-piece hit signal — equivalent to the ROM
     * {@code bset #6,status(a0)} at {@code CNZMiniboss_CheckTopHit}
     * (sonic3k.asm:145442). Public for cross-package test access (T11
     * integration test in {@code com.openggf.tests}); the {@code ForTest}
     * suffix is the visibility marker.
     *
     * <p>T5 behaviour preserved: flipping {@link #statusBit6TopHit} is
     * what lets {@link #updateWaitHit()} transition into Closing via
     * {@link #handleWaitHitHandoff()} on the next dispatch.
     *
     * <p>T6 addition: also advances the shared boss hit counter and fires
     * {@link #onHitTaken(int)}, mirroring the full {@code subq.b #1,$45(a0)}
     * + {@code beq.s CNZMiniboss_BossDefeated} fallthrough from
     * {@code CNZMiniboss_CheckTopHit}. Still bypasses {@code BossHitHandler}
     * — invulnerability / palette-flash timing is orthogonal to the state
     * machine these tests exercise, and running the real hit handler here
     * would tangle palette-flash side effects into every defeat-path test.
     */
    public void simulateHitForTest() {
        statusBit6TopHit = true;
        if (state.hitCount > 0) {
            state.hitCount--;
            onHitTaken(state.hitCount);
        }
    }

    /**
     * Test seam: arm {@link #ROUTINE_LOWER2} with an explicit countdown
     * ({@code $43(a0)}) and an explicit restore target for
     * {@code loc_6DB7E} (sonic3k.asm:144979 — {@code move.b $42(a0),routine(a0)}).
     * Package-private — test-only.
     *
     * <p>Tests must pass the routine they want restored when the counter
     * underflows so there is no ambiguity when the routine has already
     * been forced to {@link #ROUTINE_LOWER2} by
     * {@link #forceRoutineForTest(int)} beforehand.
     */
    void armLower2CounterForTest(int frames, int restoreRoutine) {
        state.routine = ROUTINE_LOWER2;
        lower2Counter = frames;
        lower2PreviousRoutine = restoreRoutine;
    }

    /**
     * @deprecated Use {@link #armLower2CounterForTest(int, int)} — the
     *     previous-routine inference is counterintuitive once the test
     *     has already forced the routine to {@link #ROUTINE_LOWER2}.
     *     This shim preserves the historical MOVE_DUP restore target
     *     so existing callers keep passing. Marked {@code forRemoval}
     *     so the next test-cleanup pass can drop the shim once the
     *     remaining caller in {@code TestCnzMinibossDefeatPhase}
     *     migrates to the explicit two-arg form.
     */
    @Deprecated(forRemoval = true)
    void setLower2CounterForTest(int frames) {
        armLower2CounterForTest(frames, ROUTINE_MOVE_DUP);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Rendering parity remains out of workstream-D scope (plan section 9).
        // The full sprite/DPLC pipeline will be wired alongside the T4-T6
        // state machine; leaving this empty keeps the seam explicit and
        // stops accidental placeholder rendering from masking parity gaps.
    }
}
