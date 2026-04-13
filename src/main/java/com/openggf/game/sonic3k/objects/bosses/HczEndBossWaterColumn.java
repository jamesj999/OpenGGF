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
 * HCZ end boss water column platform (ROM: loc_6B26E).
 *
 * <p>Rises from the water surface to the turbine, acts as a solid-top platform,
 * and renders both the platform frame and the whirlpool/spray body.
 *
 * <h3>ROM state machine (6 routines, stride 2):</h3>
 * <ol start="0">
 *   <li>INIT (0, loc_6B2AA): place at Water_level - 8, set animation to byte_6BE0C
 *       (spin-up), spawn water surface children and bubble particles.</li>
 *   <li>ANIMATE (2, loc_6B2F2): Animate_Raw on byte_6BE0C. On completion ($F4),
 *       callback loc_6B2F8 sets routine 4, y_vel=-$100, animation byte_6BE15,
 *       and spawns spray child (loc_6B3DE).</li>
 *   <li>RISE (4, loc_6B31E): MoveSprite2 + Animate_Raw + sub_6BC8A height check.
 *       When segment_count >= 5, callback loc_6B32E sets routine 6 (HOLD).</li>
 *   <li>HOLD (6, loc_6B336): Animate_Raw, wait for boss bit 3 of $38 to clear
 *       (propellerActive goes false). Then transition to DESCEND.</li>
 *   <li>DESCEND (8, loc_6B31E): same handler as RISE, y_vel=$80, x_vel=$80
 *       (sign matches boss direction). When y_pos crosses $3A (water level),
 *       callback loc_6B37A sets up PLATFORM phase.</li>
 *   <li>PLATFORM (10, loc_6B394): SolidObjectTop + Animate_Raw + Obj_Wait ($F frames),
 *       then callback loc_6B3BC displaces player off and deletes sprite.</li>
 * </ol>
 *
 * <h3>sub_6BC8A — height tracking:</h3>
 * <pre>
 *   segment_count = ($3A - y_pos) AND $F0, >> 4
 *   Store in $39 (read by spray child for visual height)
 *   If rising and segment_count >= 5 → fire callback
 *   If y_pos > $3A (overshot) → fire callback
 * </pre>
 *
 * <h3>Rendering:</h3>
 * The platform object draws ONE mapping frame via Draw_Sprite. The growing
 * whirlpool body is drawn by a separate spray child (loc_6B3DE) which selects
 * progressively taller mapping frames based on the parent's $39 segment count.
 * Since the spray child is not yet its own class, this object also renders the
 * spray visual inline using the ROM's frame tables and Y-offset tables.
 *
 * <h3>Animation scripts (Animate_Raw format: delay, frames..., command):</h3>
 * <ul>
 *   <li>byte_6BE0C (spin-up): 3, $17,$17,$22,$16,$21,$15,$20, $F4</li>
 *   <li>byte_6BE15 (rise/hold): 3, $15,$20, $FC (loop)</li>
 *   <li>Spray animations per segment count 0-5 (sub_6B916 / off_6B954):</li>
 *   <li>  0: byte_6BE19: 3, $0D,$0F,$11, $FC</li>
 *   <li>  1: byte_6BE1E: 3, $24,$25,$26, $FC</li>
 *   <li>  2: byte_6BE23: 3, $27,$28,$29, $FC</li>
 *   <li>  3: byte_6BE28: 3, $2A,$2B,$2C, $FC</li>
 *   <li>  4: byte_6BE2D: 3, $2D,$2E,$2F, $FC</li>
 *   <li>  5: byte_6BE2D: 3, $2D,$2E,$2F, $FC</li>
 * </ul>
 *
 * <p>Solid top: ROM uses SolidObjectTop with d1=0x1F, d2=0x0C, d3=0x0C.
 *
 * <p>Suction (sub_6B9AC): horizontal push at 0x20000 subpixels when player is
 * above water level and near column X. Applied by spray child, replicated here.
 *
 * <p>Grab (sub_6B9E2): player within Y-zone table (word_6BAC2) and 32px H —
 * lock object_control, forced float animation.
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
    // Physics (ROM 8.8 fixed-point: 0x100 = 1 pixel/frame)
    // =========================================================================
    private static final int RISE_YVEL    = -0x100;   // routine 4 y_vel
    private static final int DESCEND_YVEL =  0x80;    // routine 8 y_vel
    private static final int DESCEND_XVEL =  0x80;    // routine 8 x_vel (sign from boss)

    // =========================================================================
    // Solid platform (ROM: d1=0x1F, d2=0x0C, d3=0x0C)
    // =========================================================================
    private static final int SOLID_HALF_WIDTH  = 0x1F;
    private static final int SOLID_HALF_HEIGHT = 0x0C;
    private static final SolidObjectParams SOLID_PARAMS =
            new SolidObjectParams(SOLID_HALF_WIDTH, SOLID_HALF_HEIGHT, SOLID_HALF_HEIGHT);

    // =========================================================================
    // Spin-up animation: byte_6BE0C
    // Delay=3 (4 ticks per frame), 7 frames, end command $F4
    // =========================================================================
    private static final int[] SPINUP_FRAMES = {0x17, 0x17, 0x22, 0x16, 0x21, 0x15, 0x20};
    private static final int SPINUP_DELAY = 3;

    // =========================================================================
    // Rise/hold animation: byte_6BE15
    // Delay=3, frames $15,$20, loop $FC
    // =========================================================================
    private static final int[] COLUMN_FRAMES = {0x15, 0x20};
    private static final int COLUMN_ANIM_DELAY = 3;

    // =========================================================================
    // Spray animation tables (sub_6B916 / off_6B954)
    // Indexed by segment count (0-5). Each is a looping 3-frame animation.
    // =========================================================================
    private static final int[][] SPRAY_FRAMES = {
        {0x0D, 0x0F, 0x11},   // segment 0: byte_6BE19
        {0x24, 0x25, 0x26},   // segment 1: byte_6BE1E
        {0x27, 0x28, 0x29},   // segment 2: byte_6BE23
        {0x2A, 0x2B, 0x2C},   // segment 3: byte_6BE28
        {0x2D, 0x2E, 0x2F},   // segment 4: byte_6BE2D
        {0x2D, 0x2E, 0x2F},   // segment 5: byte_6BE2D (same as 4)
    };
    private static final int SPRAY_ANIM_DELAY = 3;

    // =========================================================================
    // Spray Y offsets from water level (byte_6B948, read at +1 offset)
    // Indexed by segment count (0-5)
    // =========================================================================
    private static final int[] SPRAY_Y_OFFSETS = {
        -8,     // segment 0
        -0x10,  // segment 1
        -0x18,  // segment 2
        -0x20,  // segment 3
        -0x28,  // segment 4
        -0x28,  // segment 5
    };

    // =========================================================================
    // Grab zones per segment count (word_6BAC2, 4 bytes each)
    // Each entry: {y_top_offset, y_height}, relative to spray y_pos
    // =========================================================================
    private static final int[][] GRAB_ZONES = {
        {-0x18, 0x48},  // segment 0
        {-0x20, 0x58},  // segment 1
        {-0x28, 0x68},  // segment 2
        {-0x30, 0x78},  // segment 3
        {-0x38, 0x88},  // segment 4
        {-0x48, 0x88},  // segment 5
    };
    private static final int GRAB_H_DIST = 0x10;  // 16 px half-width (ROM: addi #$10, cmpi #$20)

    // =========================================================================
    // Platform phase wait timer (ROM: $2E = $F)
    // =========================================================================
    private static final int PLATFORM_WAIT = 0x0F;

    // =========================================================================
    // Height check: transition at 5 segments (sub_6BC8A)
    // =========================================================================
    private static final int RISE_SEGMENT_THRESHOLD = 5;

    // =========================================================================
    // Floor sentinel
    // =========================================================================
    private static final int FLOOR_Y_LIMIT = 0x1000;

    // =========================================================================
    // Instance state
    // =========================================================================
    private final HczEndBossInstance boss;
    private final HczEndBossTurbine turbine;

    private int routine;

    /** Fixed-point Y (16:8). */
    private int yFixed;
    /** Fixed-point X (16:8) — used during DESCEND for x_vel tracking. */
    private int xFixed;
    /** X velocity in 8.8 fixed-point — used during DESCEND. */
    private int xVel;
    /** Y velocity in 8.8 fixed-point. */
    private int yVel;

    /** ROM $3A: stored water level Y at init (base for height calculation). */
    private int waterBaseY;

    /** ROM $39: current segment count (height / 16). */
    private int segmentCount;
    /** Previous segment count (for spray animation reset). */
    private int prevSegmentCount = -1;

    // -- Column animation state (byte_6BE15: frames $15,$20 looping) --
    private int columnAnimIndex;
    private int columnAnimTimer;

    // -- Spin-up animation state (byte_6BE0C) --
    private int spinupIndex;
    private int spinupTimer;

    // -- Spray animation state (inline — replaces spray child loc_6B3DE) --
    private int sprayAnimIndex;
    private int sprayAnimTimer;

    /** Platform wait counter for ROUTINE_PLATFORM (ROM $2E). */
    private int platformWait;

    /** Whether solid platform is active for this frame. */
    private boolean solidActive;

    /** Whether a player is currently grabbed by the column. */
    private boolean playerGrabbed;

    // =========================================================================
    // Constructor
    // =========================================================================

    public HczEndBossWaterColumn(HczEndBossInstance boss, HczEndBossTurbine turbine) {
        super(boss, "HCZEndBossWaterColumn", 3, 0);
        this.boss = boss;
        this.turbine = turbine;
        this.routine = ROUTINE_INIT;
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

        // Self-destruct on defeat (sub_6BC42 checks boss status bit 7)
        if (boss.isDefeatSignal()) {
            solidActive = false;
            releaseGrabbedPlayer(player);
            setDestroyed(true);
            return;
        }

        switch (routine) {
            case ROUTINE_INIT     -> updateInit();
            case ROUTINE_ANIMATE  -> updateAnimate();
            case ROUTINE_RISE     -> updateRiseDescend(player);
            case ROUTINE_HOLD     -> updateHold(player);
            case ROUTINE_DESCEND  -> updateRiseDescend(player);
            case ROUTINE_PLATFORM -> updatePlatform(player);
            default               -> { }
        }
    }

    // =========================================================================
    // Routine 0: INIT (loc_6B2AA)
    // =========================================================================

    /**
     * ROM loc_6B2AA: SetUp_ObjAttributes2 with word_6BD3E, place at Water_level - 8,
     * store water Y in $3A, set spin-up animation (byte_6BE0C) with callback to
     * loc_6B2F8, spawn 2 water surface children and 20 bubble particles.
     * Then advance to routine 2 (ANIMATE).
     */
    private void updateInit() {
        int waterY = getWaterLevelY();
        // ROM: move.w (Water_level).w,d0; subq.w #8,d0; move.w d0,y_pos; move.w d0,$3A
        currentX = turbine.getCurrentX();
        currentY = waterY - 8;
        yFixed = currentY << 8;
        waterBaseY = currentY;  // $3A — base for height calculation
        xFixed = currentX << 8;
        xVel = 0;
        yVel = 0;
        segmentCount = 0;
        prevSegmentCount = -1;

        solidActive = false;

        // Init spin-up animation (byte_6BE0C)
        spinupIndex = 0;
        spinupTimer = SPINUP_DELAY;

        // Init column animation (will be used after spin-up)
        columnAnimIndex = 0;
        columnAnimTimer = COLUMN_ANIM_DELAY;

        // Init spray animation
        sprayAnimIndex = 0;
        sprayAnimTimer = SPRAY_ANIM_DELAY;

        updateDynamicSpawn();

        // Transition to ANIMATE immediately
        routine = ROUTINE_ANIMATE;
        LOG.fine("HCZ Water Column: INIT, waterBaseY=" + waterBaseY);
    }

    // =========================================================================
    // Routine 2: ANIMATE — spin-up (loc_6B2F2)
    // =========================================================================

    /**
     * ROM loc_6B2F2: just Animate_Raw on byte_6BE0C.
     * Animate_Raw decrements the delay counter. When it hits 0, advances the
     * frame index and resets delay. When the $F4 end command is reached,
     * the callback at $34 (loc_6B2F8) fires.
     */
    private void updateAnimate() {
        // Track turbine X during spin-up
        currentX = turbine.getCurrentX();

        // Animate_Raw tick
        spinupTimer--;
        if (spinupTimer < 0) {
            spinupTimer = SPINUP_DELAY;
            spinupIndex++;
            if (spinupIndex >= SPINUP_FRAMES.length) {
                // $F4 end command → callback loc_6B2F8
                onSpinupComplete();
                return;
            }
        }
    }

    /**
     * ROM loc_6B2F8: callback when spin-up animation completes.
     * Sets routine 4, animation to byte_6BE15, y_vel = -$100,
     * callback to loc_6B32E (→ routine 6), spawns spray child.
     */
    private void onSpinupComplete() {
        routine = ROUTINE_RISE;
        yVel = RISE_YVEL;
        yFixed = currentY << 8;

        // Init column animation (byte_6BE15: frames $15, $20 looping)
        columnAnimIndex = 0;
        columnAnimTimer = COLUMN_ANIM_DELAY;

        LOG.fine("HCZ Water Column: spin-up complete, entering RISE");
    }

    // =========================================================================
    // Routines 4 & 8: RISE / DESCEND (loc_6B31E — shared handler)
    // =========================================================================

    /**
     * ROM loc_6B31E: MoveSprite2 (apply velocities), Animate_Raw, sub_6BC8A.
     * Used for both RISE (routine 4) and DESCEND (routine 8).
     */
    private void updateRiseDescend(PlayableEntity player) {
        // Track turbine X during rise
        if (routine == ROUTINE_RISE) {
            currentX = turbine.getCurrentX();
            xFixed = currentX << 8;
        }

        // MoveSprite2: apply velocities
        yFixed += yVel;
        currentY = yFixed >> 8;
        xFixed += xVel;
        currentX = xFixed >> 8;
        updateDynamicSpawn();

        // Animate_Raw on byte_6BE15 (column platform animation)
        tickColumnAnimation();

        // sub_6BC8A: height tracking and transition check
        doHeightCheck(player);

        // Solid platform active during rise/descend
        solidActive = true;

        // Suction (replicated from spray child sub_6B9AC + sub_6B9E2)
        if (routine == ROUTINE_RISE) {
            applySuction(player);
        }
    }

    /**
     * ROM sub_6BC8A: calculate segment count from height, store in $39.
     * <pre>
     *   d0 = $3A - y_pos
     *   if d0 < 0 (bcs): fire callback ($34)
     *   d0 = (d0 AND $F0) >> 4  — segment count
     *   store $39
     *   if y_vel >= 0: return (descending — no threshold check)
     *   if segment_count >= 5: fire callback
     * </pre>
     */
    private void doHeightCheck(PlayableEntity player) {
        int heightDiff = waterBaseY - currentY;
        if (heightDiff < 0) {
            // y_pos went below water base — fire callback
            fireHeightCallback(player);
            return;
        }

        segmentCount = (heightDiff & 0xF0) >> 4;

        // Update spray animation if segment count changed
        if (segmentCount != prevSegmentCount) {
            prevSegmentCount = segmentCount;
            sprayAnimTimer = SPRAY_ANIM_DELAY;
            // Don't reset sprayAnimIndex — ROM resets anim_frame_timer but
            // frame index persists (Animate_Raw continues from current frame)
        }

        if (yVel >= 0) {
            // Descending — no threshold check
            return;
        }

        // Rising: check if reached 5 segments
        if (segmentCount >= RISE_SEGMENT_THRESHOLD) {
            fireHeightCallback(player);
        }
    }

    /**
     * Fire the appropriate callback based on current routine.
     * RISE → HOLD (loc_6B32E), DESCEND → PLATFORM (loc_6B37A).
     */
    private void fireHeightCallback(PlayableEntity player) {
        if (routine == ROUTINE_RISE) {
            // loc_6B32E: set routine 6 (HOLD)
            routine = ROUTINE_HOLD;
            yVel = 0;
            LOG.fine("HCZ Water Column: reached 5 segments, entering HOLD");
        } else if (routine == ROUTINE_DESCEND) {
            // loc_6B37A: set up PLATFORM phase
            routine = ROUTINE_PLATFORM;
            platformWait = PLATFORM_WAIT;
            xVel = 0;
            yVel = 0;
            currentY = waterBaseY;
            yFixed = currentY << 8;
            solidActive = true;
            LOG.fine("HCZ Water Column: reached water, entering PLATFORM");
        }
    }

    // =========================================================================
    // Routine 6: HOLD (loc_6B336)
    // =========================================================================

    /**
     * ROM loc_6B336: Animate_Raw. Check boss $38 bit 3 (propellerActive).
     * If set → hold. If clear → transition to DESCEND (loc_6B34A).
     */
    private void updateHold(PlayableEntity player) {
        // Track turbine position
        currentX = turbine.getCurrentX();
        xFixed = currentX << 8;
        updateDynamicSpawn();

        // Animate column frames
        tickColumnAnimation();

        solidActive = true;

        // Suction/grab during hold
        applySuction(player);

        // Check boss propeller state (ROM: btst #3,$38(a1))
        if (!boss.isPropellerActive()) {
            // loc_6B34A: transition to DESCEND
            routine = ROUTINE_DESCEND;

            // ROM: bset #3,$38(a0) — set own flag (not used elsewhere)
            // ROM: y_vel = $80
            yVel = DESCEND_YVEL;

            // ROM: x_vel = $80, negate if boss moving left
            xVel = DESCEND_XVEL;
            if (boss.getState().xVel < 0) {
                xVel = -xVel;
            }

            // Release grabbed player on descent
            releaseGrabbedPlayer(player);

            LOG.fine("HCZ Water Column: propeller stopped, entering DESCEND");
        }
    }

    // =========================================================================
    // Routine 10: PLATFORM (loc_6B394)
    // =========================================================================

    /**
     * ROM loc_6B394: SolidObjectTop + Animate_Raw + Obj_Wait.
     * Wait timer ($2E) = $F (15 frames). On expiry, callback loc_6B3BC
     * displaces player off object and deletes.
     */
    private void updatePlatform(PlayableEntity player) {
        solidActive = true;
        tickColumnAnimation();

        platformWait--;
        if (platformWait <= 0) {
            // loc_6B3BC: Displace_PlayerOffObject + Go_Delete_Sprite_2
            solidActive = false;
            releaseGrabbedPlayer(player);
            setDestroyed(true);
        }
    }

    // =========================================================================
    // Column animation (byte_6BE15: delay=3, frames $15,$20, $FC loop)
    // =========================================================================

    private void tickColumnAnimation() {
        columnAnimTimer--;
        if (columnAnimTimer < 0) {
            columnAnimTimer = COLUMN_ANIM_DELAY;
            columnAnimIndex = (columnAnimIndex + 1) % COLUMN_FRAMES.length;
        }
    }

    // =========================================================================
    // Spray animation (per-segment-count, all delay=3, 3 frames, $FC loop)
    // =========================================================================

    private void tickSprayAnimation() {
        sprayAnimTimer--;
        if (sprayAnimTimer < 0) {
            sprayAnimTimer = SPRAY_ANIM_DELAY;
            int maxFrames = getSprayFrameCount();
            sprayAnimIndex = (sprayAnimIndex + 1) % maxFrames;
        }
    }

    private int getSprayFrameCount() {
        int idx = Math.min(segmentCount, SPRAY_FRAMES.length - 1);
        return SPRAY_FRAMES[idx].length;
    }

    // =========================================================================
    // Suction (sub_6B9AC — spray child) + Grab (sub_6B9E2)
    // =========================================================================

    /**
     * ROM sub_6B9AC (in spray child): push player horizontally at 0x20000
     * subpixels/frame when above water level and within column X proximity.
     *
     * ROM sub_6B9E2 (in spray child): grab player when within Y-zone
     * (word_6BAC2) and 32px horizontal range.
     */
    private void applySuction(PlayableEntity player) {
        if (player instanceof AbstractPlayableSprite sprite) {
            applySuctionTo(sprite);
            applyGrab(sprite);
        }
        for (PlayableEntity sidekick : services().sidekicks()) {
            if (sidekick instanceof AbstractPlayableSprite sprite) {
                applySuctionTo(sprite);
                applyGrab(sprite);
            }
        }
    }

    /**
     * sub_6B9AC: horizontal push. If player is above water level,
     * push them toward/away from column X at 0x20000 subpixels (2 px/frame).
     */
    private void applySuctionTo(AbstractPlayableSprite sprite) {
        if (sprite.getDead() || sprite.isObjectControlled()) {
            return;
        }

        int waterY = getWaterLevelY();
        int spriteY = sprite.getCentreY();

        // ROM: cmp.w y_pos(a1),d1; bhs locret — only affects players above water+8
        if (spriteY >= waterY + 8) {
            return;
        }

        int spriteX = sprite.getCentreX();
        // ROM: push = $20000 (2.0 in 16.16), applied to x_pos directly
        // If sprite X > column X, push adds positive (away? or toward?)
        // ROM: cmp.w x_pos(a1),d0; bhs loc_9DC; neg.l d2; add.l d2,x_pos
        // If column_x >= sprite_x → don't negate → add positive (push right, toward column)
        // If column_x < sprite_x → negate → add negative (push left, toward column)
        // So this always pushes TOWARD the column
        if (spriteX < currentX) {
            sprite.shiftX(2);  // push right toward column
        } else if (spriteX > currentX) {
            sprite.shiftX(-2); // push left toward column
        }
    }

    /**
     * sub_6B9E2 + sub_6BA06: grab player when in Y-zone and close enough.
     * Y-zone is defined by word_6BAC2 indexed by segment count.
     * H range: 32 px total (ROM: addi #$10, cmpi #$20).
     */
    private void applyGrab(AbstractPlayableSprite sprite) {
        if (sprite.getDead()) {  // ROM: cmpi.b #6,routine(a2) — dead/dying
            return;
        }
        if (sprite.getInvulnerable()) {  // ROM: tst.b invulnerability_timer(a2)
            return;
        }
        if (boss.isDefeatSignal()) {
            return;
        }

        int idx = Math.min(segmentCount, GRAB_ZONES.length - 1);
        int[] zone = GRAB_ZONES[idx];

        // Spray Y position = Water_level + SPRAY_Y_OFFSETS[segmentCount]
        int sprayY = getWaterLevelY() + SPRAY_Y_OFFSETS[Math.min(segmentCount,
                SPRAY_Y_OFFSETS.length - 1)];

        int spriteY = sprite.getCentreY();
        int yTop = sprayY + zone[0];
        int yBottom = yTop + zone[1];

        if (spriteY < yTop || spriteY >= yBottom) {
            return;
        }

        int spriteX = sprite.getCentreX();
        int hDist = currentX - spriteX + GRAB_H_DIST;  // ROM: sub, addi #$10
        if (hDist < 0 || hDist >= (GRAB_H_DIST * 2)) { // ROM: cmpi #$20
            return;
        }

        // Player is in grab zone
        if (!sprite.isObjectControlled()) {
            // ROM sub_6BADA: lock player
            sprite.setObjectControlled(true);
            sprite.setAir(true);
            sprite.setForcedAnimationId(Sonic3kAnimationIds.FLOAT2.id());
            sprite.setXSpeed((short) 0);
            sprite.setYSpeed((short) 0);
            playerGrabbed = true;
        }
    }

    /**
     * Release a grabbed player (ROM sub_6BB02 equivalent).
     * ROM: clears object_control, sets y_vel = -$200.
     */
    private void releaseGrabbedPlayer(PlayableEntity player) {
        if (!playerGrabbed) {
            return;
        }
        playerGrabbed = false;
        if (player instanceof AbstractPlayableSprite sprite && sprite.isObjectControlled()) {
            sprite.setObjectControlled(false);
            sprite.setYSpeed((short) -0x200);
            sprite.setXSpeed((short) 0);
            sprite.setAir(true);
        }
        for (PlayableEntity sidekick : services().sidekicks()) {
            if (sidekick instanceof AbstractPlayableSprite sprite && sprite.isObjectControlled()) {
                sprite.setObjectControlled(false);
                sprite.setYSpeed((short) -0x200);
                sprite.setXSpeed((short) 0);
                sprite.setAir(true);
            }
        }
    }

    // =========================================================================
    // Water level access
    // =========================================================================

    private int getWaterLevelY() {
        try {
            WaterSystem ws = services().waterSystem();
            if (ws == null) {
                return FLOOR_Y_LIMIT;
            }
            int zoneId = services().featureZoneId();
            int actId = services().featureActId();
            if (ws.hasWater(zoneId, actId)) {
                return ws.getWaterLevelY(zoneId, actId);
            }
        } catch (Exception e) {
            LOG.fine(() -> "HczEndBossWaterColumn.getWaterLevelY: " + e.getMessage());
        }
        return FLOOR_Y_LIMIT;
    }

    // =========================================================================
    // SolidObjectProvider
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
        return true;
    }

    @Override
    public boolean dropOnFloor() {
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

        // ---------------------------------------------------------------
        // Spin-up phase: render single frame from byte_6BE0C
        // ---------------------------------------------------------------
        if (routine == ROUTINE_ANIMATE) {
            int frame = SPINUP_FRAMES[Math.min(spinupIndex, SPINUP_FRAMES.length - 1)];
            renderer.drawFrameIndex(frame, currentX, currentY, false, false);
            return;
        }

        // ---------------------------------------------------------------
        // RISE / HOLD / DESCEND / PLATFORM:
        // 1) Draw the platform frame (byte_6BE15 animation) at column position
        // 2) Draw the spray body at spray Y position (sub_6B916 Y offsets)
        // ---------------------------------------------------------------

        // 1) Column platform frame — ROM Draw_Sprite renders ONE frame
        int columnFrame = COLUMN_FRAMES[columnAnimIndex % COLUMN_FRAMES.length];
        renderer.drawFrameIndex(columnFrame, currentX, currentY, false, false);

        // 2) Spray body — inline rendering of what loc_6B3DE would draw
        //    The spray child picks animation table and Y position based on
        //    parent's $39 (segment count), then Animate_Raw cycles through
        //    the 3 frames. Rendered via Child_Draw_Sprite2.
        if (segmentCount >= 0 && segmentCount <= 5) {
            // Tick spray animation
            tickSprayAnimation();

            int sprayIdx = Math.min(segmentCount, SPRAY_FRAMES.length - 1);
            int[] frames = SPRAY_FRAMES[sprayIdx];
            int sprayFrame = frames[sprayAnimIndex % frames.length];

            // Spray Y = Water_level + SPRAY_Y_OFFSETS[segmentCount]
            int waterY = getWaterLevelY();
            int sprayYOffset = SPRAY_Y_OFFSETS[Math.min(segmentCount,
                    SPRAY_Y_OFFSETS.length - 1)];
            int sprayY = waterY + sprayYOffset;

            renderer.drawFrameIndex(sprayFrame, currentX, sprayY, false, false);
        }
    }
}
