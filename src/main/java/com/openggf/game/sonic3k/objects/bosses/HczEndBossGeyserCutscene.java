package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.scroll.SwScrlHcz;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.scroll.ZoneScrollHandler;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.logging.Logger;

/**
 * HCZ2 end-boss post-defeat geyser cutscene (ROM: loc_6B7BC–loc_6B8C8).
 *
 * <p>Spawned by {@link HczEndBossInstance} when the egg capsule is opened.
 * Runs three sequential phases:
 *
 * <ol>
 *   <li><b>SHAKE (95 frames)</b> — screen shakes, sfx_Rumble2 plays every 8 frames.</li>
 *   <li><b>GEYSER_RISE</b> — a column sprite rises from Camera_Y + 0x130 at 6 px/frame.
 *       When the geyser top reaches player_Y − 0x60 the player is grabbed and
 *       the phase transitions to CARRY.</li>
 *   <li><b>CARRY (95 frames)</b> — geyser and grabbed player both move up 6 px/frame.
 *       After 95 frames the engine requests a transition to MGZ Act 1
 *       (zone 0x02, act 0) matching the ROM {@code StartNewLevel} call.</li>
 * </ol>
 *
 * <p>Art: uses Map_HCZEndBoss frame 8 as a placeholder column segment, drawn
 * repeatedly from the bottom of the screen up to the current geyser Y position.
 * Full KosM art loading (ArtKosM_HCZGeyserVert) is deferred to a later task.
 *
 * <p>Screen shake is applied via {@link SwScrlHcz#setScreenShakeOffset} on the
 * existing HCZ scroll handler, exactly as the wall-stop shake in
 * {@code Sonic3kHCZEvents}.
 */
public class HczEndBossGeyserCutscene extends AbstractObjectInstance {

    private static final Logger LOG = Logger.getLogger(HczEndBossGeyserCutscene.class.getName());

    // =========================================================================
    // Phase constants
    // =========================================================================
    private static final int PHASE_SHAKE       = 0;
    private static final int PHASE_GEYSER_RISE = 1;
    private static final int PHASE_CARRY       = 2;
    private static final int PHASE_DONE        = 3;

    // =========================================================================
    // Timing constants (ROM: loc_6B7BC)
    // =========================================================================
    /** Duration of the initial screen-shake phase (frames). ROM: $5F. */
    private static final int SHAKE_DURATION    = 0x5F;  // 95 frames
    /** How often to replay sfx_Rumble2 during the shake phase (frames). */
    private static final int RUMBLE_INTERVAL   = 8;
    /** Duration of the carry phase before zone transition (frames). ROM: $5F. */
    private static final int CARRY_DURATION    = 0x5F;  // 95 frames

    // =========================================================================
    // Movement constants (ROM: loc_6B820)
    // =========================================================================
    /** Rise and carry speed in pixels per frame (ROM: #6). */
    private static final int GEYSER_RISE_SPEED = 6;
    /**
     * Vertical offset above player centre Y at which the geyser grabs the player.
     * ROM: {@code cmpi.w #$60,d0} — grab when geyser top is within 0x60 px of player.
     */
    private static final int GRAB_Y_OFFSET     = 0x60;

    // =========================================================================
    // Spawn position constants (ROM: loc_6B7F0)
    // =========================================================================
    /**
     * Geyser column spawn Y offset below the camera top edge.
     * ROM: {@code Camera_Y + $130} places the geyser base 0x130 px below the camera.
     */
    private static final int SPAWN_CAMERA_Y_OFFSET = 0x130;

    // =========================================================================
    // Art: Map_HCZWaterWall frame indices (dedicated geyser cutscene art)
    // Frame 1 = tall vertical water column (12 pieces)
    // Frames 3-5 = splash sprites at the geyser top
    // =========================================================================
    private static final int COLUMN_FRAME_INDEX = 1;
    private static final int SPLASH_FRAME_BASE  = 3;
    private static final int SPLASH_FRAME_COUNT = 3; // frames 3, 4, 5
    private static final int SPLASH_ANIM_SPEED  = 4; // ticks per splash frame

    // =========================================================================
    // Next level (ROM: StartNewLevel zone $0200 = MGZ Act 1)
    // =========================================================================
    private static final int NEXT_ZONE = Sonic3kZoneIds.ZONE_MGZ;  // 0x02
    private static final int NEXT_ACT  = 0;                        // Act 1 (0-based)

    // =========================================================================
    // ROM screen-shake table (ScreenShakeArray, sonic3k.asm ~104226)
    // Same table used by Sonic3kHCZEvents for timed shakes.
    // =========================================================================
    private static final byte[] SCREEN_SHAKE_TABLE = {
            1, -1, 1, -1, 2, -2, 2, -2, 3, -3, 3, -3, 4, -4, 4, -4,
            5, -5, 5, -5
    };

    // =========================================================================
    // Instance state
    // =========================================================================
    private int phase       = PHASE_SHAKE;
    private int timer;
    private int rumbleTimer;

    /** Geyser column top Y (world coordinates), updated each frame in RISE/CARRY. */
    private int geyserTopY;

    /** Geyser column X (player_1 X at spawn time, held constant). */
    private int geyserX;

    /** True once the player has been grabbed by the rising column. */
    private boolean playerGrabbed;

    /** Splash animation frame index (cycles through 0..SPLASH_FRAME_COUNT-1). */
    private int splashFrame;
    /** Splash animation tick counter. */
    private int splashTimer;

    // =========================================================================
    // Constructor
    // =========================================================================

    /**
     * @param spawnX  Player_1 X position at capsule-open time (ROM: Player_1 x_pos).
     * @param spawnY  Camera Y + SPAWN_CAMERA_Y_OFFSET at spawn time.
     */
    public HczEndBossGeyserCutscene(int spawnX, int spawnY) {
        super(new ObjectSpawn(spawnX, spawnY, 0, 0, 0, false, 0), "HCZGeyserCutscene");
        this.geyserX    = spawnX;
        this.geyserTopY = spawnY;
        this.timer      = SHAKE_DURATION;
        this.rumbleTimer = 0;
        this.playerGrabbed = false;
        LOG.fine("HCZ Geyser Cutscene: spawned at X=" + spawnX + " Y=" + spawnY);
    }

    // =========================================================================
    // Main update
    // =========================================================================

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        AbstractPlayableSprite player = playerEntity instanceof AbstractPlayableSprite aps ? aps : null;

        switch (phase) {
            case PHASE_SHAKE       -> updateShake(player);
            case PHASE_GEYSER_RISE -> updateGeyserRise(player);
            case PHASE_CARRY       -> updateCarry(player);
            case PHASE_DONE        -> { /* terminal */ }
            default                -> { }
        }
    }

    // =========================================================================
    // Phase: SHAKE (ROM: loc_6B7BC–loc_6B800)
    // =========================================================================

    /**
     * Ticks the screen shake and Rumble2 SFX for 95 frames, then advances
     * to GEYSER_RISE.
     *
     * <p>ROM behavior: Screen_shake_flag set each frame; sfx_Rumble2 replayed every
     * 8 frames; timer counts down from $5F to 0.
     */
    private void updateShake(AbstractPlayableSprite player) {
        // Apply screen shake via the HCZ scroll handler
        applyScreenShake(timer);

        // Replay sfx_Rumble2 every 8 frames (ROM: btst #3,d0 / bne.s skip_sfx)
        rumbleTimer--;
        if (rumbleTimer <= 0) {
            services().playSfx(Sonic3kSfx.RUMBLE_2.id);
            rumbleTimer = RUMBLE_INTERVAL;
        }

        timer--;
        if (timer <= 0) {
            // Shake complete — spawn geyser at camera_Y + 0x130
            clearScreenShake();
            var camera = services().camera();
            geyserTopY = camera.getY() + SPAWN_CAMERA_Y_OFFSET;
            timer = -1;
            phase = PHASE_GEYSER_RISE;
            LOG.fine("HCZ Geyser Cutscene: shake done, geyser rising from Y=" + geyserTopY);
        }
    }

    // =========================================================================
    // Phase: GEYSER_RISE (ROM: loc_6B800–loc_6B840)
    // =========================================================================

    /**
     * Rises the geyser column at 6 px/frame. When the column top reaches
     * player_Y − 0x60 the player is grabbed (object_control set, velocities
     * cleared) and the phase transitions to CARRY.
     *
     * <p>ROM: {@code sub.w d1,d2; cmpi.w #$60,d2; bhi.s loc_6B830} — if the
     * vertical distance from the geyser top to the player center is greater
     * than 0x60 keep rising; otherwise grab.
     */
    private void updateGeyserRise(AbstractPlayableSprite player) {
        geyserTopY -= GEYSER_RISE_SPEED;

        if (player == null || playerGrabbed) {
            return;
        }

        int playerY = player.getCentreY();
        int dist    = playerY - geyserTopY;

        if (dist <= GRAB_Y_OFFSET) {
            // Grab the player
            player.setObjectControlled(true);
            player.setXSpeed((short) 0);
            player.setYSpeed((short) 0);
            playerGrabbed = true;
            timer = CARRY_DURATION;
            phase = PHASE_CARRY;
            LOG.fine("HCZ Geyser Cutscene: player grabbed at geyserY=" + geyserTopY
                    + " playerY=" + playerY);
        }
    }

    // =========================================================================
    // Phase: CARRY (ROM: loc_6B840–loc_6B8C8)
    // =========================================================================

    /**
     * Carries the grabbed player upward at 6 px/frame for 95 frames, then
     * requests a zone transition to MGZ Act 1 (ROM: {@code StartNewLevel #$0200}).
     */
    private void updateCarry(AbstractPlayableSprite player) {
        // Move geyser upward each frame
        geyserTopY -= GEYSER_RISE_SPEED;

        // Move the grabbed player with the geyser
        // ROM: subq.w #6,y_pos(a1) — directly subtract from player Y each frame
        if (player != null && playerGrabbed) {
            player.setY((short) (player.getY() - GEYSER_RISE_SPEED));
        }

        timer--;
        if (timer <= 0) {
            // Release player control before transitioning
            if (player != null && playerGrabbed) {
                player.deferObjectControlRelease();
            }
            playerGrabbed = false;
            phase = PHASE_DONE;
            setDestroyed(true);

            // ROM: StartNewLevel #$0200 → MGZ Act 1
            services().requestZoneAndAct(NEXT_ZONE, NEXT_ACT, true);
            LOG.info("HCZ Geyser Cutscene: carry complete, requesting MGZ Act 1");
        }
    }

    // =========================================================================
    // Rendering
    // =========================================================================

    /**
     * Draws the geyser using dedicated cutscene art (Map_HCZWaterWall).
     * Frame 1 is a tall vertical water column (12 mapping pieces, ~96 px tall).
     * The column is tiled from the geyser top downward to fill the screen.
     * Frames 3-5 are splash sprites animated at the geyser top.
     */
    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed() || phase == PHASE_SHAKE) {
            return;
        }

        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.HCZ_GEYSER_CUTSCENE);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        // Draw column segments from geyserTopY downward to the screen bottom
        var camera = services().camera();
        int screenBottomY = camera.getY() + 224; // standard 224-px screen height
        int segmentHeight = 96;                   // frame 1 is ~96 px tall (12 pieces * 8px)
        int segY = geyserTopY;
        while (segY < screenBottomY) {
            renderer.drawFrameIndex(COLUMN_FRAME_INDEX, geyserX, segY, false, false);
            segY += segmentHeight;
        }

        // Draw splash animation at geyser top
        tickSplashAnimation();
        int splashFrameIndex = SPLASH_FRAME_BASE + splashFrame;
        renderer.drawFrameIndex(splashFrameIndex, geyserX, geyserTopY, false, false);
    }

    /**
     * Ticks the splash animation cycle (frames 3-5 of Map_HCZWaterWall).
     */
    private void tickSplashAnimation() {
        splashTimer++;
        if (splashTimer >= SPLASH_ANIM_SPEED) {
            splashTimer = 0;
            splashFrame = (splashFrame + 1) % SPLASH_FRAME_COUNT;
        }
    }

    // =========================================================================
    // Screen shake helpers
    // =========================================================================

    /**
     * Apply a shake offset via the HCZ scroll handler.
     * Uses the ROM's timed shake table indexed by the remaining timer value.
     * When the timer is outside the table range a small alternating offset is used.
     *
     * @param remaining frames remaining in shake phase
     */
    private void applyScreenShake(int remaining) {
        SwScrlHcz handler = resolveHczScrollHandler();
        if (handler == null) {
            return;
        }
        // Map remaining timer (SHAKE_DURATION..1) into the table
        // Use modulo of the table length for a repeating pattern
        int idx = remaining % SCREEN_SHAKE_TABLE.length;
        handler.setScreenShakeOffset(SCREEN_SHAKE_TABLE[idx]);
    }

    /** Clears screen shake on the HCZ scroll handler. */
    private void clearScreenShake() {
        SwScrlHcz handler = resolveHczScrollHandler();
        if (handler != null) {
            handler.setScreenShakeOffset(0);
        }
    }

    /**
     * Resolves the {@link SwScrlHcz} scroll handler from the parallax manager,
     * or returns null if unavailable (headless / unit-test context).
     */
    private SwScrlHcz resolveHczScrollHandler() {
        try {
            var parallax = services().parallaxManager();
            if (parallax == null) return null;
            ZoneScrollHandler handler = parallax.getHandler(Sonic3kZoneIds.ZONE_HCZ);
            return (handler instanceof SwScrlHcz hcz) ? hcz : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public int getPriorityBucket() {
        return 1;
    }
}
