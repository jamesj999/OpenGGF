package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.graphics.GLCommand;
import com.openggf.level.WaterSystem;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.boss.AbstractBossChild;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.logging.Logger;

/**
 * HCZ end boss water column child (ROM: loc_6B26E).
 *
 * <p>When the turbine is spinning, a column of water rises from the water surface
 * up to the turbine. While held at target height the column acts as a solid-top
 * platform and pulls nearby players toward its centre (suction), optionally
 * locking them if they are very close (grab). When the turbine stops, the column
 * descends back to the water surface and is deleted.
 *
 * <p>State machine (5 routines, ROM stride 2):
 * <ol start="0">
 *   <li>INIT (0): Place at water level directly below turbine X.</li>
 *   <li>RISE (4): Rise toward turbine at {@code y_vel = -0x100}; apply suction.</li>
 *   <li>HOLD (6): At target height; maintain suction; act as solid-top platform.</li>
 *   <li>DESCEND (8): Descend when turbine deactivates; track boss X.</li>
 *   <li>PLATFORM (10): Brief solid platform phase then self-destruct.</li>
 * </ol>
 *
 * <p>Solid top: ROM uses SolidObjectTop with d1=0x1F (half-width 31), d2=0x0C, d3=0x0C.
 *
 * <p>Suction (sub_6B9AC, line 141757):
 * <ul>
 *   <li>Horizontal: player within 128 px H and 192 px V — accelerate toward centre
 *       at 0x20 subpixels per frame.</li>
 *   <li>Grab (sub_6B9E2): player within 32 px H and 64 px V — lock them
 *       (object_control=1, clear velocities).</li>
 * </ul>
 * Player eject y_vel = -0x200 when released.
 */
public class HczEndBossWaterColumn extends AbstractBossChild implements SolidObjectProvider {

    private static final Logger LOG = Logger.getLogger(HczEndBossWaterColumn.class.getName());

    // =========================================================================
    // State machine routines (ROM stride 2)
    // =========================================================================
    private static final int ROUTINE_INIT     = 0;
    private static final int ROUTINE_ANIMATE  = 2;
    private static final int ROUTINE_RISE     = 4;
    private static final int ROUTINE_HOLD     = 6;
    private static final int ROUTINE_DESCEND  = 8;
    private static final int ROUTINE_PLATFORM = 10;

    // =========================================================================
    // Physics constants (ROM 8.8 fixed-point: 0x100 = 1 pixel/frame)
    // =========================================================================
    /** Rise velocity (upward = negative Y). */
    private static final int RISE_YVEL = -0x100;
    /** Descend velocity (downward = positive Y). */
    private static final int DESCEND_YVEL = 0x100;
    /** Player eject velocity when grab is released. */
    private static final int EJECT_YVEL = -0x200;

    // =========================================================================
    // Solid platform parameters (ROM: d1=0x1F, d2=0x0C, d3=0x0C)
    // =========================================================================
    private static final int SOLID_HALF_WIDTH    = 0x1F; // 31 px
    private static final int SOLID_HALF_HEIGHT   = 0x0C; // 12 px
    private static final SolidObjectParams SOLID_PARAMS =
            new SolidObjectParams(SOLID_HALF_WIDTH, SOLID_HALF_HEIGHT, SOLID_HALF_HEIGHT);

    // =========================================================================
    // Suction zone (sub_6B9AC)
    // =========================================================================
    /** Horizontal proximity for suction pull to begin (128 px). */
    private static final int SUCTION_H_DIST = 128;
    /** Vertical proximity for suction pull to begin (192 px). */
    private static final int SUCTION_V_DIST = 192;
    /** Horizontal acceleration applied toward column centre each frame (subpixels). */
    private static final int SUCTION_X_ACCEL = 0x20;
    /** Horizontal proximity for player grab / lock (32 px). */
    private static final int GRAB_H_DIST = 32;
    /** Vertical proximity for player grab / lock (64 px). */
    private static final int GRAB_V_DIST = 64;

    // =========================================================================
    // Animation: Map_HCZEndBoss frame 8 area, 4-frame cycle
    // =========================================================================
    private static final int ANIM_FRAME_BASE  = 8;
    private static final int ANIM_FRAME_COUNT = 4;
    private static final int ANIM_SPEED       = 4; // frames per step

    // =========================================================================
    // Spin-up animation (ROM: byte_6BE0C, routine 2)
    // Delay 3 (4 ticks per frame), 7 frames total = 28 ticks before RISE.
    // Frame sequence from ROM: $17, $17, $22, $16, $21, $15, $20
    // On completion ($F4 command): transition to RISE, set y_vel = -$100,
    // spawn water spray child.
    // =========================================================================
    private static final int[] SPINUP_FRAMES = {0x17, 0x17, 0x22, 0x16, 0x21, 0x15, 0x20};
    private static final int SPINUP_DELAY = 3; // 4 ticks per frame (0-indexed delay)

    // =========================================================================
    // Floor sentinel (far below level)
    // =========================================================================
    private static final int FLOOR_Y_LIMIT = 0x1000;

    // =========================================================================
    // Instance state
    // =========================================================================
    private final HczEndBossInstance boss;
    private final HczEndBossTurbine turbine;

    private int routine;
    private int animFrame;
    private int animCounter;

    /** Fixed-point Y position (16:8 — pixel * 256). */
    private int yFixed;
    /** Target Y when rising (turbine Y position). */
    private int targetY;

    /** Whether the current frame has solid platform active. */
    private boolean solidActive;

    /** Whether the player was grabbed last frame (prevents re-grab every frame). */
    private boolean playerGrabbed;

    /** Spin-up animation: current index into SPINUP_FRAMES. */
    private int spinupIndex;
    /** Spin-up animation: tick countdown (resets to SPINUP_DELAY each frame advance). */
    private int spinupTimer;

    // =========================================================================
    // Constructor
    // =========================================================================

    /**
     * @param boss    Parent boss instance.
     * @param turbine The turbine child — used to read turbine position and
     *                spinning state.
     */
    public HczEndBossWaterColumn(HczEndBossInstance boss, HczEndBossTurbine turbine) {
        super(boss, "HCZEndBossWaterColumn", 3, 0);
        this.boss    = boss;
        this.turbine = turbine;
        this.routine = ROUTINE_INIT;
        this.animFrame   = 0;
        this.animCounter = 0;
        this.solidActive = false;
        this.playerGrabbed = false;
    }

    // =========================================================================
    // Main update
    // =========================================================================

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        if (!beginUpdate(frameCounter)) {
            return;
        }

        // Self-destruct on defeat
        if (boss.isDefeatSignal()) {
            solidActive = false;
            releaseGrabbedPlayer(player);
            setDestroyed(true);
            return;
        }

        switch (routine) {
            case ROUTINE_INIT     -> updateInit();
            case ROUTINE_ANIMATE  -> updateAnimate();
            case ROUTINE_RISE     -> updateRise(player);
            case ROUTINE_HOLD     -> updateHold(player);
            case ROUTINE_DESCEND  -> updateDescend(player);
            case ROUTINE_PLATFORM -> updatePlatform(player);
            default               -> { }
        }
    }

    // =========================================================================
    // Routine handlers
    // =========================================================================

    /**
     * ROM routine 0 (INIT): position at water level directly below turbine X,
     * then transition to ANIMATE (spin-up) phase.
     */
    private void updateInit() {
        int waterY = getWaterLevelY();
        currentX = turbine.getCurrentX();
        currentY = waterY;
        yFixed   = currentY << 8;
        targetY  = turbine.getCurrentY();
        solidActive = false;
        updateDynamicSpawn();
        // Transition to spin-up animation (ROM routine 2)
        routine = ROUTINE_ANIMATE;
        spinupIndex = 0;
        spinupTimer = SPINUP_DELAY;
    }

    /**
     * ROM routine 2 (ANIMATE): plays the turbine spin-up animation (byte_6BE0C).
     * 7 frames at 4 ticks each = 28 frames total. On completion, transitions
     * to RISE with y_vel = -0x100.
     *
     * <p>ROM: loc_6B2F2 — single {@code Animate_Raw} call on byte_6BE0C.
     * Frame sequence: $17, $17, $22, $16, $21, $15, $20 (Map_HCZEndBoss frames).
     * End command $F4 triggers transition to routine 4 (RISE).
     */
    private void updateAnimate() {
        // Track turbine X position during spin-up
        currentX = turbine.getCurrentX();

        spinupTimer--;
        if (spinupTimer < 0) {
            spinupTimer = SPINUP_DELAY;
            spinupIndex++;
            if (spinupIndex >= SPINUP_FRAMES.length) {
                // Spin-up complete — transition to RISE (ROM routine 4)
                routine = ROUTINE_RISE;
                yFixed = currentY << 8;
                LOG.fine("HCZ Water Column: spin-up complete, entering RISE");
                return;
            }
        }
    }

    /**
     * ROM routine 4 (RISE): move upward at RISE_YVEL, track turbine X,
     * apply suction to player.
     */
    private void updateRise(PlayableEntity player) {
        // Track turbine X (boss horizontal position)
        currentX = turbine.getCurrentX();
        targetY  = turbine.getCurrentY();

        // Rise: advance fixed-point Y by velocity
        yFixed  += RISE_YVEL;
        currentY = yFixed >> 8;
        updateDynamicSpawn();

        tickAnimation();
        solidActive = true; // platform active while rising too

        // Apply suction
        applySuction(player);

        // Reached target height?
        if (currentY <= targetY) {
            currentY = targetY;
            yFixed   = currentY << 8;
            routine  = ROUTINE_HOLD;
            LOG.fine("HCZ Water Column: reached target Y=" + targetY + ", entering HOLD");
        }

        // Turbine stopped — abort rise, descend immediately
        if (!turbine.isSpinning()) {
            routine = ROUTINE_DESCEND;
        }
    }

    /**
     * ROM routine 6 (HOLD): column at target height, maintain suction,
     * act as solid-top platform.
     */
    private void updateHold(PlayableEntity player) {
        // Track turbine X
        currentX = turbine.getCurrentX();
        currentY = turbine.getCurrentY();
        yFixed   = currentY << 8;
        updateDynamicSpawn();

        tickAnimation();
        solidActive = true;

        applySuction(player);

        // Turbine wound down — start descend
        if (!turbine.isSpinning()) {
            routine = ROUTINE_DESCEND;
            LOG.fine("HCZ Water Column: turbine stopped, descending");
        }
    }

    /**
     * ROM routine 8 (DESCEND): descend back to water surface while tracking
     * boss X.
     */
    private void updateDescend(PlayableEntity player) {
        // Release any grabbed player on descent start
        releaseGrabbedPlayer(player);
        solidActive = false;

        // Track boss X (boss moves during this phase)
        currentX = boss.getState().x;

        // Descend
        yFixed  += DESCEND_YVEL;
        currentY = yFixed >> 8;
        updateDynamicSpawn();

        tickAnimation();

        // Reached water level?
        int waterY = getWaterLevelY();
        if (currentY >= waterY) {
            currentY = waterY;
            yFixed   = currentY << 8;
            solidActive = true;
            routine = ROUTINE_PLATFORM;
        }
    }

    /**
     * ROM routine 10 (PLATFORM): brief solid phase at water level, then destroy.
     * ROM: column sits as solid for a few frames to let the player land, then deletes.
     */
    private void updatePlatform(PlayableEntity player) {
        solidActive = true;
        tickAnimation();

        // One animation cycle at water level then destroy
        if (animFrame == 0 && animCounter == 0) {
            solidActive = false;
            releaseGrabbedPlayer(player);
            setDestroyed(true);
        }
    }

    // =========================================================================
    // Suction (sub_6B9AC + sub_6B9E2)
    // =========================================================================

    /**
     * Applies suction and grab behaviour to the main player and any sidekicks.
     * ROM sub_6B9AC: horizontal suction within 128 px H / 192 px V.
     * ROM sub_6B9E2: grab/lock within 32 px H / 64 px V.
     */
    private void applySuction(PlayableEntity player) {
        if (player instanceof AbstractPlayableSprite sprite) {
            applySuctionTo(sprite);
        }

        for (PlayableEntity sidekick : services().sidekicks()) {
            if (sidekick instanceof AbstractPlayableSprite sprite) {
                applySuctionTo(sprite);
            }
        }
    }

    /**
     * Apply suction + optional grab to a single sprite.
     */
    private void applySuctionTo(AbstractPlayableSprite sprite) {
        if (sprite.getDead()) {
            return;
        }

        int px = sprite.getCentreX();
        int py = sprite.getCentreY();

        int hDist = Math.abs(px - currentX);
        int vDist = Math.abs(py - currentY);

        // Outside outer suction zone — no effect
        if (hDist > SUCTION_H_DIST || vDist > SUCTION_V_DIST) {
            return;
        }

        // Inner grab zone (sub_6B9E2): lock player to column
        if (hDist <= GRAB_H_DIST && vDist <= GRAB_V_DIST) {
            if (!sprite.isObjectControlled()) {
                sprite.setObjectControlled(true);
                sprite.setForcedAnimationId(Sonic3kAnimationIds.FLOAT2.id());
                sprite.setXSpeed((short) 0);
                sprite.setYSpeed((short) 0);
                playerGrabbed = true;
            }
            // Nudge toward column centre — tiny corrections to keep player centered
            if (px < currentX) {
                sprite.shiftX(1);
            } else if (px > currentX) {
                sprite.shiftX(-1);
            }
            return;
        }

        // Outer suction zone: accelerate player horizontally toward column centre
        short xVel = sprite.getXSpeed();
        int xAccel;
        if (px < currentX) {
            // Player is left of column — pull right
            xAccel = SUCTION_X_ACCEL;
        } else {
            // Player is right of column — pull left
            xAccel = -SUCTION_X_ACCEL;
        }
        xVel = (short) (xVel + xAccel);
        sprite.setXSpeed(xVel);
        sprite.shiftX(xVel >> 8);
    }

    /**
     * Release a grabbed player with upward eject velocity.
     * ROM: y_vel = -0x200 on release.
     */
    private void releaseGrabbedPlayer(PlayableEntity player) {
        if (!playerGrabbed) {
            return;
        }
        playerGrabbed = false;
        if (player instanceof AbstractPlayableSprite sprite && sprite.isObjectControlled()) {
            sprite.setObjectControlled(false);
            sprite.setYSpeed((short) EJECT_YVEL);
            sprite.setXSpeed((short) 0);
            sprite.setAir(true);
        }
        for (PlayableEntity sidekick : services().sidekicks()) {
            if (sidekick instanceof AbstractPlayableSprite sprite && sprite.isObjectControlled()) {
                sprite.setObjectControlled(false);
                sprite.setYSpeed((short) EJECT_YVEL);
                sprite.setXSpeed((short) 0);
                sprite.setAir(true);
            }
        }
    }

    // =========================================================================
    // Animation helper
    // =========================================================================

    private void tickAnimation() {
        animCounter++;
        if (animCounter >= ANIM_SPEED) {
            animCounter = 0;
            animFrame   = (animFrame + 1) % ANIM_FRAME_COUNT;
        }
    }

    // =========================================================================
    // Water level access (same pattern as HczEndBossBlade)
    // =========================================================================

    private int getWaterLevelY() {
        try {
            WaterSystem ws = services().waterSystem();
            if (ws == null) {
                return FLOOR_Y_LIMIT;
            }
            int zoneId = services().featureZoneId();
            int actId  = services().featureActId();
            if (ws.hasWater(zoneId, actId)) {
                return ws.getWaterLevelY(zoneId, actId);
            }
        } catch (Exception e) {
            LOG.fine(() -> "HczEndBossWaterColumn.getWaterLevelY: " + e.getMessage());
        }
        return FLOOR_Y_LIMIT;
    }

    // =========================================================================
    // SolidObjectProvider — top-solid only platform (ROM: SolidObjectTop)
    // =========================================================================

    @Override
    public SolidObjectParams getSolidParams() {
        return SOLID_PARAMS;
    }

    @Override
    public boolean isTopSolidOnly() {
        return true;
    }

    @Override
    public boolean isSolidFor(PlayableEntity player) {
        return solidActive;
    }

    @Override
    public boolean usesStickyContactBuffer() {
        // Moving platform — keep sticky contact to avoid edge jitter
        return true;
    }

    @Override
    public boolean dropOnFloor() {
        // Column can push the player upward; run terrain check after repositioning
        return true;
    }

    // =========================================================================
    // Rendering
    // =========================================================================

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed() || routine == ROUTINE_INIT) {
            return;
        }

        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.HCZ_END_BOSS);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        // During spin-up animation, render the current spin-up frame
        if (routine == ROUTINE_ANIMATE) {
            int frame = SPINUP_FRAMES[spinupIndex];
            renderer.drawFrameIndex(frame, currentX, currentY, false, false);
            return;
        }

        int frameIndex = ANIM_FRAME_BASE + animFrame;
        renderer.drawFrameIndex(frameIndex, currentX, currentY, false, false);
    }
}
