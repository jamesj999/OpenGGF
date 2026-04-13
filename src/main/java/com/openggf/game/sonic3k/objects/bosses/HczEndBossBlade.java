package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.WaterSystem;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.boss.AbstractBossChild;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;
import java.util.logging.Logger;

/**
 * HCZ end boss propeller blade child (ROM: loc_6B5C4).
 *
 * <p>Three blade segments sit at different Y offsets below the boss and
 * counter-rotate as the propeller spins. Blade index 0 (the bottom blade)
 * is the only one that fires: when the boss sets {@code bladeFireSignal},
 * this blade detaches and flies horizontally as a projectile.
 *
 * <p>Spawned by the boss as:
 * <pre>
 *   spawnChild(() -> new HczEndBossBlade(this, 0, 0x23, 0x12)); // fires
 *   spawnChild(() -> new HczEndBossBlade(this, 1, 0x1B, 0x0A)); // passive
 *   spawnChild(() -> new HczEndBossBlade(this, 2, 0x13, 0x0A)); // passive
 * </pre>
 *
 * <p>State machine (7 routines, ROM stride 2):
 * <ol start="0">
 *   <li>ATTACHED (0): Follow parent at fixed offset; animate blade frame.</li>
 *   <li>WAIT_FIRE (2): Blade index 0 only — wait for {@code boss.isBladeFireSignal()}.</li>
 *   <li>WAIT_CLEAR (4): Acknowledge fire signal; wait for it to be cleared.</li>
 *   <li>FLY (6): Horizontal projectile flight at constant velocity.</li>
 *   <li>FALL (8): Gravity-accelerated fall after hitting water level.</li>
 *   <li>UNDERWATER_FALL (10): Continue falling with floor check.</li>
 *   <li>SPIN_DOWN (12): Animate to rest then self-destruct.</li>
 * </ol>
 *
 * <p>Animation: Map_HCZEndBoss frames 6–7, alternating every 4 game frames.
 *
 * <p>Collision when flying: collision_flags = 0 (visual only; blade does not hurt player).
 */
public class HczEndBossBlade extends AbstractBossChild implements TouchResponseProvider {
    private static final Logger LOG = Logger.getLogger(HczEndBossBlade.class.getName());

    // =========================================================================
    // State machine routines (ROM stride 2)
    // =========================================================================
    private static final int ROUTINE_ATTACHED       = 0;
    private static final int ROUTINE_WAIT_FIRE      = 2;
    private static final int ROUTINE_WAIT_CLEAR     = 4;
    private static final int ROUTINE_FLY            = 6;
    private static final int ROUTINE_FALL           = 8;
    private static final int ROUTINE_UNDERWATER_FALL = 10;
    private static final int ROUTINE_SPIN_DOWN      = 12;

    // =========================================================================
    // Collision
    // =========================================================================
    // ROM: collision_flags = 0 (blade is visual only; no player collision)

    // =========================================================================
    // Animation — Map_HCZEndBoss frames 6 and 7
    // =========================================================================
    private static final int BLADE_FRAME_A = 6;
    private static final int BLADE_FRAME_B = 7;
    /** Frames per animation step while attached or spinning down. */
    private static final int ANIM_SPEED = 4;

    // =========================================================================
    // Flight physics (ROM: fixed-point 8.8, 0x100 = 1 pixel/frame)
    // =========================================================================
    /** Horizontal velocity when fired (magnitude; direction is boss-facing-dependent). */
    private static final int FLY_XVEL = 0x100;
    /** Gravity applied each frame during FALL and UNDERWATER_FALL (subpixels). */
    private static final int GRAVITY = 0x38;
    /** Floor Y boundary below which the blade self-destructs (pixels). */
    private static final int FLOOR_Y_LIMIT = 0x1000;
    /** Off-screen margin for deletion while flying. */
    private static final int OFFSCREEN_MARGIN = 32;

    // =========================================================================
    // FLY animation (ROM: byte_6BE19 — dc.b 3, $D, $F, $11, $F4)
    // 3 frames at delay 3 (4 ticks each) = 12 ticks total flight, then FALL.
    // =========================================================================
    private static final int[] FLY_ANIM_FRAMES = {0x0D, 0x0F, 0x11};
    private static final int FLY_ANIM_DELAY = 3;

    // =========================================================================
    // Spin-down animation
    // =========================================================================
    /** Number of animation cycles to complete before self-destructing in SPIN_DOWN. */
    private static final int SPIN_DOWN_CYCLES = 6;

    // =========================================================================
    // Instance state
    // =========================================================================
    private final HczEndBossInstance boss;
    /** 0 = bottom (fires), 1 = middle, 2 = top. */
    private final int bladeIndex;
    /** X offset from boss center when attached (pixels, signed). */
    private final int xOffset;
    /** Y offset from boss center when attached (pixels). */
    private final int yOffset;

    private int routine;
    private int animFrame;       // 0 = BLADE_FRAME_A, 1 = BLADE_FRAME_B
    private int animCounter;

    // Fixed-point position for projectile (16:8 — integer pixel * 256)
    private int xFixed;
    private int yFixed;
    // Integer velocity (sub-pixel units: 0x100 = 1 px/frame)
    private int xVel;
    private int yVel;

    private int spinDownCycleCount;

    // FLY animation state (ROM: byte_6BE19)
    private int flyAnimIndex;
    private int flyAnimTimer;

    // =========================================================================
    // Constructor
    // =========================================================================

    /**
     * @param boss       Parent boss instance.
     * @param bladeIndex 0 = bottom (fires), 1 = middle, 2 = top.
     * @param xOffset    Horizontal offset from boss center (pixels, positive = right of boss).
     * @param yOffset    Vertical offset from boss center (pixels, positive = below boss).
     */
    public HczEndBossBlade(HczEndBossInstance boss, int bladeIndex, int xOffset, int yOffset) {
        super(boss, "HCZEndBossBlade[" + bladeIndex + "]", 3, 0);
        this.boss = boss;
        this.bladeIndex = bladeIndex;
        this.xOffset = xOffset;
        this.yOffset = yOffset;
        this.routine = ROUTINE_ATTACHED;
        this.animFrame = 0;
        this.animCounter = 0;
        this.xVel = 0;
        this.yVel = 0;
        this.spinDownCycleCount = 0;
    }

    // =========================================================================
    // Main update
    // =========================================================================

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        if (!beginUpdate(frameCounter)) {
            return;
        }

        // Children always follow defeat signal
        if (boss.isDefeatSignal()) {
            collisionFlags = 0;
            setDestroyed(true);
            return;
        }

        switch (routine) {
            case ROUTINE_ATTACHED      -> updateAttached();
            case ROUTINE_WAIT_FIRE     -> updateWaitFire();
            case ROUTINE_WAIT_CLEAR    -> updateWaitClear();
            case ROUTINE_FLY           -> updateFly();
            case ROUTINE_FALL          -> updateFall();
            case ROUTINE_UNDERWATER_FALL -> updateUnderwaterFall();
            case ROUTINE_SPIN_DOWN     -> updateSpinDown();
            default                    -> { }
        }
    }

    // =========================================================================
    // Routine handlers
    // =========================================================================

    /**
     * ROM routine 0 (ATTACHED): track parent position, animate blade.
     * Index 0 transitions to WAIT_FIRE after one frame of initialization.
     * Indices 1 and 2 remain ATTACHED permanently (they never fire).
     */
    private void updateAttached() {
        // Track boss position with offset
        currentX = boss.getState().x + xOffset;
        currentY = boss.getState().y + yOffset;
        updateDynamicSpawn();
        collisionFlags = 0;
        tickAnimation();

        // Only the bottom blade proceeds to WAIT_FIRE
        if (bladeIndex == 0) {
            routine = ROUTINE_WAIT_FIRE;
        }
    }

    /**
     * ROM routine 2 (WAIT_FIRE): bottom blade only.
     * Follow parent; wait for {@code boss.isBladeFireSignal()}.
     */
    private void updateWaitFire() {
        currentX = boss.getState().x + xOffset;
        currentY = boss.getState().y + yOffset;
        updateDynamicSpawn();
        collisionFlags = 0;
        tickAnimation();

        if (boss.isBladeFireSignal()) {
            // Acknowledge fire signal but wait for it to clear before flying
            routine = ROUTINE_WAIT_CLEAR;
        }
    }

    /**
     * ROM routine 4 (WAIT_CLEAR): acknowledge signal then wait for boss to clear it.
     * ROM pattern: blade clears the bit itself, then waits one frame for the boss to
     * re-sample. Here the blade clears it immediately and proceeds.
     */
    private void updateWaitClear() {
        currentX = boss.getState().x + xOffset;
        currentY = boss.getState().y + yOffset;
        updateDynamicSpawn();

        // Clear the signal on the boss and launch the blade
        boss.clearBladeFireSignal();
        launchBlade();
    }

    /**
     * ROM routine 6 (FLY): horizontal projectile.
     * Advances fixed-point position by velocity each frame.
     * Transitions to FALL when fly animation completes (ROM: byte_6BE19
     * — 3 frames at delay 3, then $F4 command triggers FALL).
     * Self-destructs when off-screen.
     */
    private void updateFly() {
        // Advance fixed-point position (16:8 format: xFixed += xVel, position = xFixed >> 8)
        xFixed += xVel;
        currentX = xFixed >> 8;
        currentY = yFixed >> 8;  // y unchanged during pure flight
        updateDynamicSpawn();

        tickAnimation();

        // Off-screen deletion
        if (!isOnScreen(OFFSCREEN_MARGIN)) {
            setDestroyed(true);
            return;
        }

        // Tick fly animation timer — transition to FALL when animation completes
        // ROM: byte_6BE19: delay 3 (4 ticks per frame), 3 frames, then $F4 → FALL
        flyAnimTimer--;
        if (flyAnimTimer < 0) {
            flyAnimTimer = FLY_ANIM_DELAY;
            flyAnimIndex++;
            if (flyAnimIndex >= FLY_ANIM_FRAMES.length) {
                // Animation complete — transition to FALL (ROM: $F4 command)
                routine = ROUTINE_FALL;
            }
        }
    }

    /**
     * ROM routine 8 (FALL): gravity-accelerated fall after hitting water.
     * Blade no longer moves horizontally.
     */
    private void updateFall() {
        xVel = 0;
        yVel += GRAVITY;
        yFixed += yVel;
        currentY = yFixed >> 8;
        updateDynamicSpawn();

        tickAnimation();

        // Off-screen deletion
        if (!isOnScreen(OFFSCREEN_MARGIN)) {
            setDestroyed(true);
            return;
        }

        // Proceed to UNDERWATER_FALL once blade is clearly submerged
        routine = ROUTINE_UNDERWATER_FALL;
    }

    /**
     * ROM routine 10 (UNDERWATER_FALL): continue falling with floor check.
     */
    private void updateUnderwaterFall() {
        yVel += GRAVITY;
        yFixed += yVel;
        currentY = yFixed >> 8;
        updateDynamicSpawn();

        tickAnimation();

        // Destroy on floor boundary
        if (currentY >= FLOOR_Y_LIMIT) {
            setDestroyed(true);
            return;
        }

        // Off-screen deletion
        if (!isOnScreen(OFFSCREEN_MARGIN)) {
            setDestroyed(true);
        }
    }

    /**
     * ROM routine 12 (SPIN_DOWN): animate to rest then self-destruct.
     * After SPIN_DOWN_CYCLES complete animation cycles the blade is destroyed.
     */
    private void updateSpinDown() {
        collisionFlags = 0;
        tickAnimation();
        if (animFrame == 0 && animCounter == 0) {
            spinDownCycleCount++;
            if (spinDownCycleCount >= SPIN_DOWN_CYCLES) {
                setDestroyed(true);
            }
        }
    }

    // =========================================================================
    // Fire launch helper
    // =========================================================================

    /**
     * Detaches the blade from the boss and fires it horizontally.
     * ROM: xVel = 0x100 if boss facing left, -0x100 if boss facing right.
     * Blade is purely visual — collision_flags remains 0.
     */
    private void launchBlade() {
        // Snap fixed-point position to current world coordinates
        xFixed = currentX << 8;
        yFixed = currentY << 8;

        // ROM: blade fires opposite to boss travel direction
        // (boss faces left while moving left, blade ejects rightward, and vice-versa)
        xVel = boss.isFacingRight() ? -FLY_XVEL : FLY_XVEL;
        yVel = 0;

        // Initialize fly animation timer (ROM: byte_6BE19 — 3 frames at delay 3)
        flyAnimIndex = 0;
        flyAnimTimer = FLY_ANIM_DELAY;

        // collision_flags stays 0 — blade is visual only (ROM: collision_flags = 0)
        routine = ROUTINE_FLY;

        LOG.fine(() -> "HCZ End Boss Blade[0]: launched xVel=" + xVel
                + " from x=" + currentX + " y=" + currentY);
    }

    // =========================================================================
    // Animation helper
    // =========================================================================

    /**
     * Advance the blade animation by one counter tick.
     * Cycles between BLADE_FRAME_A and BLADE_FRAME_B at ANIM_SPEED rate.
     */
    private void tickAnimation() {
        animCounter++;
        if (animCounter >= ANIM_SPEED) {
            animCounter = 0;
            animFrame = 1 - animFrame; // toggle 0 <-> 1
        }
    }

    // =========================================================================
    // Water level access
    // =========================================================================

    /**
     * Returns the current gameplay water level Y coordinate in world space.
     * Uses {@code services().waterSystem()} via ObjectServices (same pattern as
     * {@code BreathingBubbleInstance} and {@code BuggernautBadnikInstance}).
     * Falls back to a large sentinel value if water system is unavailable.
     */
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
            LOG.fine(() -> "HczEndBossBlade.getWaterLevelY: " + e.getMessage());
        }
        return FLOOR_Y_LIMIT;
    }

    // =========================================================================
    // Collision (TouchResponseProvider)
    // =========================================================================

    @Override
    public int getCollisionFlags() {
        return collisionFlags;
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    // =========================================================================
    // Rendering
    // =========================================================================

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) {
            return;
        }

        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.HCZ_END_BOSS);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        int frame = (animFrame == 0) ? BLADE_FRAME_A : BLADE_FRAME_B;

        // Mirror around boss center: blades on the left side flip when boss faces right
        boolean hFlip = boss.isFacingRight();
        renderer.drawFrameIndex(frame, currentX, currentY, hFlip, false);
    }
}
