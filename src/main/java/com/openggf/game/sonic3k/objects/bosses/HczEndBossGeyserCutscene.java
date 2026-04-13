package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.HCZWaterRushObjectInstance;
import com.openggf.game.sonic3k.scroll.SwScrlHcz;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.scroll.ZoneScrollHandler;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.logging.Logger;

/**
 * HCZ2 end-boss post-defeat geyser cutscene (ROM: loc_6B7BC-loc_6B8C8).
 *
 * <p>Spawned by {@link HczEndBossInstance} when the egg capsule is opened.
 * Runs three sequential phases:
 *
 * <ol>
 *   <li><b>SHAKE (95 frames)</b> -- screen shakes, sfx_Rumble2 plays every 8 frames.</li>
 *   <li><b>GEYSER_RISE</b> -- a column sprite rises from Camera_Y + 0x130 at 6 px/frame.
 *       When the geyser top reaches player_Y - 0x60 the player is grabbed (HURT anim set)
 *       and the phase transitions to CARRY.</li>
 *   <li><b>CARRY (95 frames)</b> -- geyser and grabbed player both move up 6 px/frame.
 *       After 95 frames the engine requests a transition to MGZ Act 1
 *       (zone 0x02, act 0) matching the ROM {@code StartNewLevel} call.</li>
 * </ol>
 *
 * <p>Art: uses Map_HCZWaterWall frame 1 (tall vertical water column, 12 pieces)
 * tiled from the geyser top down to the bottom of the screen. Splash animation
 * (frames 3-5) plays at the top.
 *
 * <p>Debris: 8 {@link GeyserDebrisChild} objects are spawned at geyser setup time
 * (ROM: loc_6BCB2). These use Map_HCZWaterWallDebris with 8 cycling frames,
 * fly outward with gravity, and stop at the water level.
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
    // Movement constants (ROM: loc_6B882)
    // =========================================================================
    /** Rise and carry speed in pixels per frame (ROM: #6). */
    private static final int GEYSER_RISE_SPEED = 6;
    /**
     * Vertical offset above player centre Y at which the geyser grabs the player.
     * ROM: {@code subi.w #$60,d0; cmp.w y_pos(a1),d0; bhs.s loc_6B8B2}
     */
    private static final int GRAB_Y_OFFSET     = 0x60;

    // =========================================================================
    // Spawn position constants (ROM: loc_6B864)
    // =========================================================================
    /**
     * Geyser column spawn Y offset below the camera top edge.
     * ROM: {@code Camera_Y + $130} places the geyser base 0x130 px below the camera.
     */
    private static final int SPAWN_CAMERA_Y_OFFSET = 0x130;

    // =========================================================================
    // Art: Map_HCZWaterWall frame indices (dedicated geyser cutscene art)
    // Frame 1 = tall vertical water column (12 pieces, ~96 px tall)
    // Frames 3-5 = splash sprites at the geyser top
    // =========================================================================
    private static final int COLUMN_FRAME_INDEX = 1;
    private static final int SPLASH_FRAME_BASE  = 3;
    private static final int SPLASH_FRAME_COUNT = 3; // frames 3, 4, 5
    private static final int SPLASH_ANIM_SPEED  = 4; // ticks per splash frame

    // =========================================================================
    // Debris spawn table (ROM: byte_303EA, sonic3k.asm ~65190)
    // {xOff, yOff, xVel, yVel} x 8 — same table as the vertical water wall.
    // Debris Y is offset by -0x80 from geyser y_pos (ROM: subi.w #$80,d3).
    // =========================================================================
    private static final int[][] DEBRIS_TABLE = {
            {-0x18, 0, -0x200, -0xB00},
            {-0x08, 0, -0x100, -0xC00},
            {-0x18, 0, -0x400, -0x800},
            {-0x08, 0, -0x300, -0xA00},
            { 0x08, 0,  0x300, -0xC00},
            { 0x18, 0,  0x400, -0xB00},
            { 0x08, 0,  0x100, -0xA00},
            { 0x18, 0,  0x200, -0x800},
    };

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

    /** True once debris children have been spawned. */
    private boolean debrisSpawned;

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
        this.debrisSpawned = false;
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
    // Phase: SHAKE (ROM: loc_6B7EC-loc_6B804)
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
            // Shake complete -- set up the geyser column (ROM: loc_6B832)
            clearScreenShake();
            setupGeyserColumn(player);
            LOG.fine("HCZ Geyser Cutscene: shake done, geyser rising from Y=" + geyserTopY);
        }
    }

    // =========================================================================
    // Geyser setup (ROM: loc_6B832-loc_6B864)
    // =========================================================================

    /**
     * Sets up the geyser column after the shake phase completes.
     * ROM: loc_6B832 -- SetUp_ObjAttributes with ObjDat3_6BD7E,
     * plays sfx_Geyser, activates palette cycling, sets up position,
     * and spawns 8 debris children (loc_6BCB2).
     */
    private void setupGeyserColumn(AbstractPlayableSprite player) {
        // ROM: st (Screen_shake_flag).w -- keep shake active during rise
        // ROM: st (Palette_cycle_counters+$00).w -- activate palette cycling
        HCZWaterRushObjectInstance.HCZWaterRushPaletteCycleGate.setActive(true);

        // ROM: sfx_Geyser
        try {
            services().playSfx(Sonic3kSfx.GEYSER.id);
        } catch (Exception e) {
            LOG.fine(() -> "HCZ Geyser Cutscene: Failed to play SFX: " + e.getMessage());
        }

        // ROM: move.w x_pos(a1),d0 / move.w d0,x_pos(a0)
        // ROM: Camera_Y + $130 -> y_pos(a0)
        var camera = services().camera();
        if (player != null) {
            geyserX = player.getCentreX();
        }
        geyserTopY = camera.getY() + SPAWN_CAMERA_Y_OFFSET;

        // Spawn 8 debris children (ROM: jmp loc_6BCB2)
        spawnDebris();

        timer = -1;
        phase = PHASE_GEYSER_RISE;
    }

    // =========================================================================
    // Debris spawning (ROM: loc_6BCB2)
    // =========================================================================

    /**
     * Spawns 8 water debris children around the geyser.
     * ROM: loc_6BCB2 -- reads byte_303EA table, spawns debris at
     * geyser X + xOff, geyser Y - 0x80 + yOff with xVel/yVel.
     * Debris uses Map_HCZWaterWallDebris with art_tile ArtTile_HCZCutsceneGeyser+$58.
     */
    private void spawnDebris() {
        if (debrisSpawned) return;
        debrisSpawned = true;

        int debrisBaseY = geyserTopY - 0x80; // ROM: subi.w #$80,d3

        for (int i = 0; i < DEBRIS_TABLE.length; i++) {
            int[] entry = DEBRIS_TABLE[i];
            int debrisX = geyserX + entry[0];
            int debrisY = debrisBaseY + entry[1];

            // ROM: mapping_frame = d1 (loop counter 7..0, so frame = 7-i)
            // dbf d1 counts 7,6,5,...0 and mapping_frame = d1
            int mappingFrame = (DEBRIS_TABLE.length - 1) - i;

            GeyserDebrisChild debris = new GeyserDebrisChild(
                    debrisX, debrisY, entry[2], entry[3], mappingFrame);
            spawnDynamicObject(debris);
        }
        LOG.fine("HCZ Geyser Cutscene: spawned " + DEBRIS_TABLE.length + " debris children");
    }

    // =========================================================================
    // Phase: GEYSER_RISE (ROM: loc_6B882)
    // =========================================================================

    /**
     * Rises the geyser column at 6 px/frame. When the column top reaches
     * player_Y - 0x60 the player is grabbed (object_control set, HURT anim,
     * velocities cleared) and the phase transitions to CARRY.
     *
     * <p>ROM: {@code sub.w d1,y_pos(a0); subi.w #$60,d0; cmp.w y_pos(a1),d0;
     * bhs.s loc_6B8B2} -- if the geyser top minus 0x60 is still above or at the
     * player, keep rising; otherwise grab.
     */
    private void updateGeyserRise(AbstractPlayableSprite player) {
        geyserTopY -= GEYSER_RISE_SPEED;

        if (player == null || playerGrabbed) {
            return;
        }

        int playerY = player.getCentreY();
        int dist    = playerY - geyserTopY;

        if (dist <= GRAB_Y_OFFSET) {
            // Grab the player (ROM: loc_6B882 grab branch)
            // ROM: move.b #$81,object_control(a1)
            player.setObjectControlled(true);
            player.setXSpeed((short) 0);
            player.setYSpeed((short) 0);

            // ROM: move.b #$1A,anim(a1) -- HURT animation
            player.setAnimationId(Sonic3kAnimationIds.HURT);

            playerGrabbed = true;
            timer = CARRY_DURATION;
            phase = PHASE_CARRY;
            LOG.fine("HCZ Geyser Cutscene: player grabbed at geyserY=" + geyserTopY
                    + " playerY=" + playerY);
        }
    }

    // =========================================================================
    // Phase: CARRY (ROM: loc_6B8C8)
    // =========================================================================

    /**
     * Carries the grabbed player upward at 6 px/frame for 95 frames, then
     * requests a zone transition to MGZ Act 1 (ROM: {@code StartNewLevel #$0200}).
     */
    private void updateCarry(AbstractPlayableSprite player) {
        // ROM: subq.w #6,y_pos(a0)
        geyserTopY -= GEYSER_RISE_SPEED;

        // ROM: subq.w #6,y_pos(a1) -- directly subtract from player Y each frame
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

            // ROM: StartNewLevel #$0200 -> MGZ Act 1
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
     *
     * <p>ROM: ObjDat3_6BD7E specifies Map_HCZWaterWall, palette 2,
     * width $20 (32px), height $60 (96px), mapping_frame 1.
     */
    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed() || phase == PHASE_SHAKE) {
            return;
        }

        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.HCZ_GEYSER_CUTSCENE);
        if (renderer == null) {
            // Fallback: try the regular vertical geyser art (same ArtKosM_HCZGeyserVert)
            renderer = getRenderer(Sonic3kObjectArtKeys.HCZ_GEYSER_VERT);
        }
        if (renderer == null) {
            // Last resort: draw a debug placeholder so it's not completely invisible
            appendDebugColumn(commands);
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

    /**
     * Draws a simple debug column when no art renderer is available.
     * Renders a semi-transparent blue rectangle from geyserTopY to screen bottom.
     */
    private void appendDebugColumn(List<GLCommand> commands) {
        var camera = services().camera();
        int screenBottomY = camera.getY() + 224;
        int halfWidth = 0x20; // ROM: width_pixels = $20
        int l = geyserX - halfWidth;
        int r = geyserX + halfWidth;
        int t = geyserTopY;
        int bot = screenBottomY;

        // Draw a filled quad as 2 triangles (6 vertices)
        float cr = 0.2f, cg = 0.5f, cb = 1.0f;
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1,
                GLCommand.BlendType.SOLID, cr, cg, cb, l, t, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1,
                GLCommand.BlendType.SOLID, cr, cg, cb, r, t, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1,
                GLCommand.BlendType.SOLID, cr, cg, cb, r, bot, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1,
                GLCommand.BlendType.SOLID, cr, cg, cb, l, bot, 0, 0));
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

    // =========================================================================
    // Inner class: Geyser Debris Child (ROM: loc_3011A / loc_6BCB2)
    // =========================================================================

    /**
     * Water debris child spawned by the geyser cutscene.
     *
     * <p>ROM: loc_3011A -- animates through 8 mapping frames (Map_HCZWaterWallDebris),
     * moves with MoveSprite2 and gravity ($38). When Y exceeds Water_level, stops
     * Y velocity and halves X velocity twice, then continues sinking until off-screen.
     *
     * <p>Art: ArtTile_HCZCutsceneGeyser+$58, palette 2. Uses existing
     * {@link Sonic3kObjectArtKeys#HCZ_GEYSER_DEBRIS} art key (same debris mapping
     * frames, compatible tile layout).
     */
    static class GeyserDebrisChild extends AbstractObjectInstance {

        private static final int GRAVITY = 0x38;        // ROM: addi.w #$38,y_vel
        private static final int SLOW_GRAVITY = 8;      // ROM: loc_301A8 addi.w #8,y_vel
        private static final int ANIM_TIMER_RESET = 2;  // ROM: move.b #2,anim_frame_timer

        private enum DebrisState { FLYING, SINKING }

        private final SubpixelMotion.State motion;
        private int mappingFrame;
        private int animTimer = ANIM_TIMER_RESET;
        private DebrisState state = DebrisState.FLYING;
        private boolean splashSpawned;

        GeyserDebrisChild(int x, int y, int xVel, int yVel, int initialFrame) {
            super(new ObjectSpawn(x, y, 0, 0, 0, false, 0), "GeyserCutsceneDebris");
            this.motion = new SubpixelMotion.State(x, y, 0, 0, xVel, yVel);
            this.mappingFrame = initialFrame & 7;
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
        public void update(int frameCounter, PlayableEntity playerEntity) {
            if (isDestroyed()) return;

            switch (state) {
                case FLYING -> updateFlying();
                case SINKING -> updateSinking();
            }
        }

        /**
         * ROM: loc_3011A -- animate frame, move with gravity, check water level.
         */
        private void updateFlying() {
            // Animate: cycle mapping_frame 0-7 every 2 ticks
            animTimer--;
            if (animTimer <= 0) {
                animTimer = ANIM_TIMER_RESET;
                mappingFrame = (mappingFrame + 1) & 7;
            }

            // Move with gravity
            SubpixelMotion.moveSprite(motion, GRAVITY);

            // Check water level (ROM: cmp.w y_pos(a0),Water_level / bhs)
            int waterLevel = getWaterLevel();
            if (waterLevel > 0 && motion.y > waterLevel) {
                // ROM: stop Y velocity, halve X velocity twice (asr twice)
                motion.yVel = 0;
                motion.xVel >>= 2;
                state = DebrisState.SINKING;
            }
        }

        /**
         * ROM: loc_301A8 -- keep moving with reduced gravity until off-screen.
         */
        private void updateSinking() {
            SubpixelMotion.moveSprite(motion, SLOW_GRAVITY);

            // ROM: tst.b render_flags(a0) / bpl Delete_Current_Sprite
            if (!isOnScreen(0x80)) {
                setDestroyed(true);
            }
        }

        private int getWaterLevel() {
            try {
                var ws = services().waterSystem();
                if (ws != null) {
                    return ws.getWaterLevelY(services().romZoneId(), services().currentAct());
                }
            } catch (Exception e) {
                // fallback
            }
            return 0;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            // Use the existing debris art (Map_HCZWaterWallDebris).
            // The cutscene debris uses ArtTile_HCZCutsceneGeyser+$58, palette 2,
            // which is compatible with the HCZ_GEYSER_DEBRIS art key.
            PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.HCZ_GEYSER_DEBRIS);
            if (renderer != null) {
                renderer.drawFrameIndex(mappingFrame, motion.x, motion.y, false, false);
            } else {
                // Debug fallback: small blue box
                int sz = 4;
                commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1,
                        GLCommand.BlendType.SOLID, 0.3f, 0.6f, 1.0f,
                        motion.x - sz, motion.y - sz, 0, 0));
                commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1,
                        GLCommand.BlendType.SOLID, 0.3f, 0.6f, 1.0f,
                        motion.x + sz, motion.y - sz, 0, 0));
                commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1,
                        GLCommand.BlendType.SOLID, 0.3f, 0.6f, 1.0f,
                        motion.x + sz, motion.y + sz, 0, 0));
                commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1,
                        GLCommand.BlendType.SOLID, 0.3f, 0.6f, 1.0f,
                        motion.x - sz, motion.y + sz, 0, 0));
            }
        }

        @Override
        public int getPriorityBucket() {
            return 1; // ROM: priority $280
        }
    }
}
