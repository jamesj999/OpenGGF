package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
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
    private static final int ROUTINE_CLOSING = 0xC;  // Obj_CNZMinibossClosing (144968) — T6
    // Routine E (Lower2, 144972) and End (144984) land in T6.

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
        // CNZMiniboss_Index dispatch (sonic3k.asm:144874). Routines 0/2/4/6/8/A
        // are implemented; C/E (Closing / Lower2) are T6 territory. Each routine
        // body that matches the ROM's `Obj_Wait` tail (Lower, Move, Opening)
        // calls tickWait() itself at the end — Init / WaitHit / one-shot $34
        // callbacks have no Obj_Wait in ROM and therefore don't tick here.
        switch (state.routine) {
            case ROUTINE_INIT -> updateInit();
            case ROUTINE_LOWER -> updateLower();
            case ROUTINE_MOVE, ROUTINE_MOVE_DUP -> updateMove();
            case ROUTINE_OPENING -> updateOpening();
            case ROUTINE_WAIT_HIT -> updateWaitHit();
            default -> {
                // Routines C/E are T6 territory. A log at FINE level gives
                // downstream tasks a signal if a routine counter lands here
                // unexpectedly (e.g. a future typo writes an out-of-range
                // index). No-op in production; visible via Logger config.
                final int routine = state.routine;
                LOG.warning(() -> "CNZ miniboss: unhandled routine "
                        + Integer.toHexString(routine));
            }
        }
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

    // ---- Test-only accessors for the Task 4/5 state machine ----
    // Package-private, read-only — exposed so TestCnzMinibossSwingPhase /
    // TestCnzMinibossOpeningPhase can assert on $2E/$34 timer state and
    // routine transitions without widening the public API. T6 follows the
    // same convention.

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
     * (sonic3k.asm:145442). Package-private — test-only.
     *
     * <p>Deliberately bypasses {@link AbstractBossInstance#onPlayerAttack} /
     * {@code BossHitHandler.processHit}: those would decrement the shared
     * hit counter and set invulnerability, which is T6's territory. For
     * T5 we only need to prove that WaitHit transitions to Closing when
     * {@link #statusBit6TopHit} flips, so this helper writes that bit
     * directly without touching collision/invulnerability state.
     */
    void simulateHitForTest() {
        statusBit6TopHit = true;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Rendering parity remains out of workstream-D scope (plan section 9).
        // The full sprite/DPLC pipeline will be wired alongside the T4-T6
        // state machine; leaving this empty keeps the seam explicit and
        // stops accidental placeholder rendering from masking parity gaps.
    }
}
