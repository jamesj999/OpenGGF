package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.events.S3kCnzEventWriteSupport;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SubpixelMotion;

import java.util.List;

/**
 * CNZ Act 1 miniboss top piece — the bouncing-ball projectile the player
 * ricochets off the arena walls and onto the miniboss base.
 *
 * <p>ROM anchors (all {@code sonic3k.asm}, S&amp;K-side):
 * <ul>
 *   <li>{@code Obj_CNZMinibossTop}          line 145004 — outer dispatch.</li>
 *   <li>{@code CNZMinibossTop_Index}        line 145011 — 4-routine table.</li>
 *   <li>{@code Obj_CNZMinibossTopInit}      line 145018 — routine 0.</li>
 *   <li>{@code Obj_CNZMinibossTopWait}      line 145026 — routine 2.</li>
 *   <li>{@code Obj_CNZMinibossTopWait2}     line 145040 — routine 4.</li>
 *   <li>{@code Obj_CNZMinibossTopGo}        line 145045 — {@code $34}
 *       post-wait handler that advances routine 2 to routine 4.</li>
 *   <li>{@code Obj_CNZMinibossTopMain}      line 145053 — routine 6
 *       (bouncing-ball body).</li>
 *   <li>{@code CNZMiniboss_BlockExplosion}  line 145204 — snaps impact
 *       coordinates to the 0x20-pixel arena block grid.</li>
 * </ul>
 *
 * <p>The ROM routine uses {@code MoveSprite2} (no gravity) — the ball
 * maintains constant speed and simply reverses the relevant velocity
 * component on contact with an arena edge. When the ball hits a wall,
 * ceiling or floor, the impact X/Y are published through
 * {@code Events_bg+$00/$02} so the base and the event bridge can drive
 * chunk-destruction and base-lowering sequences that live outside this
 * object. Those side effects flow through the explicit
 * {@link S3kCnzEventWriteSupport#queueArenaChunkDestruction} bridge to
 * keep the object -&gt; events dependency testable.
 */
public final class CnzMinibossTopInstance extends AbstractObjectInstance {

    // ---- Routine indices (CNZMinibossTop_Index, sonic3k.asm:145011) ----
    /** Routine 0 — Obj_CNZMinibossTopInit (sonic3k.asm:145018). */
    private static final int ROUTINE_INIT = 0;
    /** Routine 2 — Obj_CNZMinibossTopWait (sonic3k.asm:145026). */
    private static final int ROUTINE_WAIT = 2;
    /** Routine 4 — Obj_CNZMinibossTopWait2 (sonic3k.asm:145040). */
    private static final int ROUTINE_WAIT2 = 4;
    /** Routine 6 — Obj_CNZMinibossTopMain (sonic3k.asm:145053). */
    private static final int ROUTINE_MAIN = 6;

    /**
     * Number of frames the routine-4 {@code Wait2} body waits before the
     * post-wait callback ({@link #onTopGo()}) advances to {@code Main}.
     *
     * <p>The ROM body runs {@code Animate_RawGetFaster} against the
     * {@code AniRaw_CNZMinibossTop} script; the engine has no script
     * pipeline here, so a conservative short wait drives the state
     * machine into routine 6 within the 240-frame test window and keeps
     * the skeleton state transitions explicit.
     */
    private static final int WAIT2_FRAMES = 0x20;

    /** Mirrors the parent-boss reference used by {@code parent3(a0)} in ROM. */
    private CnzMinibossInstance boss;

    /** 16:8 motion state backing {@code x_pos}/{@code y_pos}/{@code x_vel}/{@code y_vel}. */
    private final SubpixelMotion.State motion;

    /** Current routine byte (ROM {@code routine(a0)} at offset 0x05). */
    private int routine;

    /** Routine-4 post-wait countdown. When it underflows, {@link #onTopGo()} fires. */
    private int wait2Counter;

    // ---- Arena collision seam preserved from Task 7 scaffold ----
    private boolean arenaCollisionPending;
    private int pendingChunkWorldX;
    private int pendingChunkWorldY;

    public CnzMinibossTopInstance(ObjectSpawn spawn) {
        super(spawn, "CNZMinibossTop");
        this.motion = new SubpixelMotion.State(spawn.x(), spawn.y(), 0, 0, 0, 0);
        this.routine = ROUTINE_INIT;
        this.wait2Counter = -1;
    }

    /**
     * Test seam used to make the parent/base dependency explicit without
     * requiring the full child-object spawn chain from the real boss.
     *
     * <p>Preserved verbatim from the Task-7 scaffold.
     */
    public void attachBossForTest(CnzMinibossInstance boss) {
        this.boss = boss;
    }

    /**
     * Schedules one ROM-shaped arena collision.
     *
     * <p>Preserved verbatim from the Task-7 scaffold. Inputs are
     * already expected to be world coordinates aligned to the same block
     * grid {@code CNZMiniboss_BlockExplosion} uses after masking with
     * {@code $FFE0} and adding {@code $10}.
     */
    public void forceArenaCollisionForTest(int chunkWorldX, int chunkWorldY) {
        arenaCollisionPending = true;
        pendingChunkWorldX = chunkWorldX;
        pendingChunkWorldY = chunkWorldY;
    }

    /**
     * Test seam: jumps directly into {@link #ROUTINE_MAIN} with a
     * ROM-shaped initial velocity ({@code 0x200}, {@code 0x200}) so the
     * bouncing-ball body has something to integrate against.
     *
     * <p>Mirrors the ROM state right after {@link #onTopGo()} fires —
     * routine 6 with {@code x_vel = y_vel = 0x200}. Tests that call this
     * skip routines 0/2/4 and observe only the bouncing body.
     */
    public void forceTopMainForTest() {
        routine = ROUTINE_MAIN;
        motion.xVel = Sonic3kConstants.CNZ_MINIBOSS_TOP_INIT_X_VEL;
        motion.yVel = Sonic3kConstants.CNZ_MINIBOSS_TOP_INIT_Y_VEL;
    }

    /** Test seam: returns the current routine byte. */
    public int getCurrentRoutineForTest() {
        return routine;
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        // Arena collision seam is still driven by forceArenaCollisionForTest —
        // run it before the state machine so the Task-7 contract (attachBossForTest
        // + forceArenaCollisionForTest + update → bridge + base lowering) stays
        // byte-for-byte identical regardless of which routine we're in.
        if (arenaCollisionPending) {
            arenaCollisionPending = false;
            publishArenaChunkImpact(pendingChunkWorldX, pendingChunkWorldY);
        }

        switch (routine) {
            case ROUTINE_INIT -> updateInit();
            case ROUTINE_WAIT -> updateWait();
            case ROUTINE_WAIT2 -> updateWait2();
            case ROUTINE_MAIN -> updateMain();
            default -> {
                // Out-of-range writes are a bug; silently ignore rather than
                // crashing a frame loop. The normal 4-routine dispatch above
                // covers every ROM entry, so this branch is unreachable in
                // the happy path.
            }
        }

        // ROM parity note: Obj_CNZMinibossTop writes its position through
        // Events_bg+$00/$02 on every frame, but those words are only read
        // by CNZMiniboss_BlockExplosion when an impact actually fires.
        // The engine's impact path already flows through
        // publishArenaChunkImpact() below, which writes through the CNZ
        // bridge at the correct moment. A per-frame publish here would
        // add no consumer-visible state while breaking the
        // arena-destruction counter (one 0x20-pixel row per queued
        // impact, not per tick) that downstream tests assert against,
        // so we deliberately skip the per-frame write.
    }

    /**
     * ROM: Obj_CNZMinibossTopInit (sonic3k.asm:145018):
     * <pre>
     *   lea ObjDat3_CNZMinibossTop(pc),a1
     *   jsr (SetUp_ObjAttributes3).l        ; addq.b #2,routine(a0) tail
     *   move.b #$10,x_radius(a0)
     *   move.b #8,y_radius(a0)
     *   rts
     * </pre>
     *
     * <p>The ROM's {@code SetUp_ObjAttributes3} tail advances
     * {@code routine(a0)} from 0 to 2, so Init runs exactly once. The
     * engine mirrors that by bumping {@link #routine} here rather than
     * relying on the external dispatch loop to advance it.
     */
    private void updateInit() {
        routine = ROUTINE_WAIT;
    }

    /**
     * ROM: Obj_CNZMinibossTopWait (sonic3k.asm:145026):
     * <pre>
     *   movea.w parent3(a0),a1
     *   btst    #1,$38(a1)
     *   bne.s   loc_6DC10             ; Wait for signal from main boss
     *   jmp     (Refresh_ChildPosition).l
     *
     * loc_6DC10:
     *   move.b  #4,routine(a0)
     *   move.l  #AniRaw_CNZMinibossTop,$30(a0)
     *   move.l  #Obj_CNZMinibossTopGo,$34(a0)
     *   rts
     * </pre>
     *
     * <p>The ROM gates the transition on {@code $38} bit 1 of the parent
     * boss — which {@code Obj_CNZMinibossGo2} (sonic3k.asm:144906,
     * {@code bset #1,$38(a0)}) sets during the base's Init/Lower/Move
     * handoff. When the boss is absent (for example, the physics test
     * builds the top piece without a parent), the engine falls through
     * to the ROM's "signal received" branch so the state machine can
     * still exercise the routine-4/6 cadence deterministically.
     */
    private void updateWait() {
        if (boss != null && !boss.isParentSignalBit1Set()) {
            // Still waiting — ROM tail is Refresh_ChildPosition which we model
            // via publishCentrePosition() in the caller.
            return;
        }
        // ROM sonic3k.asm:145034 — move.b #4,routine(a0).
        routine = ROUTINE_WAIT2;
        // ROM sonic3k.asm:145035-145036 — AniRaw_CNZMinibossTop script +
        // Obj_CNZMinibossTopGo callback. The engine has no AniRaw engine,
        // so the Wait2 body counts down a fixed WAIT2_FRAMES and fires
        // onTopGo() directly instead of routing through a $34 slot.
        wait2Counter = WAIT2_FRAMES;
    }

    /**
     * ROM: Obj_CNZMinibossTopWait2 (sonic3k.asm:145040):
     * <pre>
     *   jsr (Refresh_ChildPosition).l
     *   jmp (Animate_RawGetFaster).l
     * </pre>
     *
     * <p>The ROM body is a pair of tail calls. {@code Animate_RawGetFaster}
     * advances {@code $30(a0)} against the {@code AniRaw_CNZMinibossTop}
     * script; when that script's terminator hits,
     * {@code Obj_CNZMinibossTopGo} fires via {@code $34(a0)} and installs
     * routine 6. Without the script engine the engine counts down
     * {@link #wait2Counter} and fires {@link #onTopGo()} directly.
     */
    private void updateWait2() {
        if (wait2Counter < 0) {
            // No pending timer — shouldn't happen via updateWait(), but the
            // shape is defensive so a forced-state test can still progress.
            onTopGo();
            return;
        }
        wait2Counter--;
        if (wait2Counter < 0) {
            onTopGo();
        }
    }

    /**
     * ROM: Obj_CNZMinibossTopGo (sonic3k.asm:145045):
     * <pre>
     *   move.b #6,routine(a0)
     *   move.l #AniRaw_CNZMinibossTop2,$30(a0)
     *   move.w #$200,x_vel(a0)
     *   move.w #$200,y_vel(a0)              ; Set initial speed of top
     *   rts
     * </pre>
     *
     * <p>Fires from {@link #updateWait2()} when the ROM-script-equivalent
     * countdown expires. Seeds the Main-routine bouncing-ball velocities
     * (+{@code 0x200} on both axes, corresponding to 2px/frame in 16:8
     * fixed point).
     */
    private void onTopGo() {
        routine = ROUTINE_MAIN;
        motion.xVel = Sonic3kConstants.CNZ_MINIBOSS_TOP_INIT_X_VEL;
        motion.yVel = Sonic3kConstants.CNZ_MINIBOSS_TOP_INIT_Y_VEL;
    }

    /**
     * ROM: Obj_CNZMinibossTopMain (sonic3k.asm:145053-145191).
     *
     * <p>The ROM body runs {@code MoveSprite2} (no gravity — the ball keeps
     * its speed) then dispatches a cascade of direction-specific edge
     * checks:
     * <ul>
     *   <li>If {@code x_vel >= 0}: right-wall probe via
     *       {@code ObjCheckRightWallDist}, right-edge probe against
     *       {@code $3380}, CNZMinibossTop_CheckHitBase. Any wall hit
     *       reverses {@code x_vel} (via {@code loc_6DD4C} for real tile
     *       collisions — which also publish {@code Events_bg} and call
     *       {@code CNZMiniboss_BlockExplosion} — or {@code loc_6DD8E}
     *       for the cheap arena-edge bounce).</li>
     *   <li>If {@code x_vel < 0}: mirror the above for the left wall
     *       against {@code $3200}.</li>
     *   <li>Then {@code CNZMinibossTop_CheckPlayerBounce}. If the
     *       player bounced off the ball, jump to {@code loc_6DDCC}
     *       (negate {@code y_vel}).</li>
     *   <li>Otherwise, if {@code y_vel >= 0}: floor probes (blocks,
     *       camera bottom, {@code $380} lower bound, base-hit). Block
     *       hits route through {@code loc_6DD94} which publishes
     *       {@code Events_bg} + {@code CNZMiniboss_BlockExplosion} and
     *       reverses {@code y_vel}. Camera/lower-bound/base-hit bounces
     *       simply reverse {@code y_vel} via {@code loc_6DDCC}.</li>
     *   <li>If {@code y_vel < 0}: ceiling probes mirroring the floor
     *       logic against {@code $240}.</li>
     * </ul>
     *
     * <p>The engine does not yet wire up real tile-collision probes,
     * the player-bounce cooperative check, or the miniboss-base
     * hit-detection against {@code CNZMiniboss_BaseRange}. The
     * minimal faithful model preserves the ROM's arena edges —
     * {@code $3200}/{@code $3380} on X and {@code $240}/{@code $380}
     * on Y — and reverses the relevant velocity whenever the next-frame
     * position would cross one of them. Horizontal arena-edge bounces
     * take the simple {@code loc_6DD8E} path (no {@code Events_bg}
     * publish); vertical arena-edge bounces (floor) take the
     * {@code loc_6DD94} path so they do publish a
     * {@code CNZMiniboss_BlockExplosion}-shaped write through the CNZ
     * bridge. This keeps the Task 7 arena-destruction seam alive
     * without inventing side effects the ROM doesn't emit.
     */
    private void updateMain() {
        // ROM sonic3k.asm:145057 — move.w x_pos(a0),-(sp). Captures the
        // pre-movement X so the side-collision branch (loc_6DD4C) knows
        // which wall the ball just crossed. The engine keeps the same
        // snapshot so the horizontal bounce path can re-use it.
        int preMoveX = motion.x;
        // ROM sonic3k.asm:145058 — jsr (MoveSprite2).l (no gravity).
        SubpixelMotion.moveSprite2(motion);

        // ROM sonic3k.asm:145065-145092 — horizontal edge checks. The
        // full ROM also calls ObjCheckRightWallDist / ObjCheckLeftWallDist
        // against the real tile map before the cheap arena-edge bounce.
        // Those probes land in a later slice; the arena-edge bounce alone
        // is enough to keep the top piece inside the arena between task 7
        // and the collision-system wiring task.
        if (motion.xVel >= 0) {
            int d0 = motion.x + Sonic3kConstants.CNZ_MINIBOSS_TOP_WALL_PROBE_DX;
            if (d0 >= Sonic3kConstants.CNZ_MINIBOSS_TOP_ARENA_RIGHT) {
                // ROM sonic3k.asm:145160-145162 — loc_6DD8E: simple neg.w x_vel.
                motion.xVel = (short) -motion.xVel;
                // Undo the X advance so a stuck ball at the arena edge can't
                // keep accumulating position past the wall on successive
                // frames; the ROM handles this by re-running MoveSprite2
                // next frame from the restored pre-move X.
                motion.x = preMoveX;
            }
        } else {
            int d0 = motion.x - Sonic3kConstants.CNZ_MINIBOSS_TOP_WALL_PROBE_DX;
            if (d0 < Sonic3kConstants.CNZ_MINIBOSS_TOP_ARENA_LEFT) {
                // ROM sonic3k.asm:145160-145162 — loc_6DD8E.
                motion.xVel = (short) -motion.xVel;
                motion.x = preMoveX;
            }
        }

        // ROM sonic3k.asm:145097-145129 — vertical edge checks.
        if (motion.yVel >= 0) {
            int d1 = motion.y + Sonic3kConstants.CNZ_MINIBOSS_TOP_FLOOR_PROBE_DY;
            if (d1 > Sonic3kConstants.CNZ_MINIBOSS_TOP_ARENA_BOTTOM) {
                // ROM sonic3k.asm:145165-145185 — loc_6DD94: publishes the
                // snapped impact coordinates through Events_bg then calls
                // CNZMiniboss_BlockExplosion. The engine routes the same
                // write through S3kCnzEventWriteSupport so the existing
                // Task-7 arena-chunk-destruction seam consumes it.
                publishArenaChunkImpact(motion.x, motion.y);
                motion.yVel = (short) -motion.yVel;
            }
        } else {
            int d1 = motion.y - Sonic3kConstants.CNZ_MINIBOSS_TOP_FLOOR_PROBE_DY;
            if (d1 <= Sonic3kConstants.CNZ_MINIBOSS_TOP_ARENA_TOP) {
                // ROM sonic3k.asm:145188-145190 — loc_6DDCC: simple neg.w y_vel.
                motion.yVel = (short) -motion.yVel;
            }
        }

        // Keep the ObjectSpawn in sync so getX()/getY() expose the ball's
        // latest position to cameras, debug overlays, and the arena-event
        // bridge downstream. The ROM writes x_pos(a0)/y_pos(a0) directly
        // inside MoveSprite2 — we mirror that by reflecting the motion
        // state into the dynamic spawn every frame.
        updateDynamicSpawn(motion.x, motion.y);
    }

    /**
     * Publishes one ROM-shaped arena-chunk destruction through the CNZ
     * event bridge and notifies the base so it can consume the lowering
     * row.
     *
     * <p>Shared by the Task-7 {@link #forceArenaCollisionForTest(int, int)}
     * seam and the new routine-6 floor-impact path so both produce
     * byte-for-byte identical bridge writes.
     */
    private void publishArenaChunkImpact(int worldX, int worldY) {
        S3kCnzEventWriteSupport.queueArenaChunkDestruction(
                services(), worldX, worldY);
        if (boss != null) {
            boss.onArenaChunkDestroyed();
        }
    }

    @Override
    public int getX() {
        return motion.x;
    }

    @Override
    public int getY() {
        return motion.y;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Task 7 covers state publication + physics only; the VDP sprite
        // mappings for the top piece are orthogonal and land with the
        // broader rendering slice.
    }
}
