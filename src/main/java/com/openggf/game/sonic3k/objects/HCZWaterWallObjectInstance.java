package com.openggf.game.sonic3k.objects;

import com.openggf.debug.DebugRenderContext;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

/**
 * Object 0x3B - HCZ Water Wall / Geyser (Sonic 3 &amp; Knuckles, Hydrocity Zone).
 *
 * <p>Two completely separate behaviours based on subtype:
 * <ul>
 *   <li><b>Subtype 0 (Horizontal geyser):</b> Endpoint of the Act 1 water rush sequence.
 *       A wall of water erupts when the player is swept past it. Spawns debris and spray
 *       particles that move right and fall into the water.</li>
 *   <li><b>Subtype != 0 (Vertical geyser):</b> Triggered when the player is nearby.
 *       Water erupts upward, captures and launches the player.</li>
 * </ul>
 *
 * <p>ROM references: sonic3k.asm lines 64835-65307 (Obj_HCZWaterWall).
 * Mappings: Map_HCZWaterWall, Map_HCZWaterWallDebris.
 * Art: ArtKosM_HCZGeyserHorz (0x390C02), ArtKosM_HCZGeyserVert (0x391394).
 */
public class HCZWaterWallObjectInstance extends AbstractObjectInstance {

    private static final Logger LOG = Logger.getLogger(HCZWaterWallObjectInstance.class.getName());

    // Shared random for spray particle variation
    private static final Random RANDOM = new Random();

    // ===== Subtype 0: Horizontal Geyser Constants =====
    private static final int HORZ_Y_GUARD = 0x500;
    private static final int HORZ_INITIAL_TIMER = 0x20; // 32 frames
    private static final int HORZ_PLAYER_TRIGGER_OFFSET = 0x60;
    private static final int HORZ_SPRAY_MOVE_PX = 8;
    private static final int HORZ_CLEANUP_TIMER = 150;

    // Horizontal debris spawn table: {xOff, yOff, xVel, yVel} x 8
    // ROM: byte_3000C (sonic3k.asm line ~65036)
    private static final int[][] HORZ_DEBRIS_TABLE = {
            {0, -0x18, 0x400, -0x80},
            {0, -0x08, 0x600, -0x40},
            {0,  0x08, 0x600,  0x40},
            {0,  0x18, 0x400,  0x80},
            {0, -0x18, 0x300, -0x380},
            {0, -0x08, 0x400, -0x340},
            {0,  0x08, 0x300,  0x100},
            {0,  0x18, 0x500, -0x100},
    };

    // ===== Subtype != 0: Vertical Geyser Constants =====
    private static final int VERT_X_RANGE = 0x60;
    private static final int VERT_Y_RANGE_MIN = -0x40;
    private static final int VERT_Y_RANGE_MAX = -0x30;
    private static final int VERT_ART_LOAD_PULL_PX = 8;
    private static final int VERT_RISE_TIMER = 0x60; // 96 frames
    private static final int VERT_ERUPTION_TRIGGER = 0x28;
    private static final int VERT_RISE_PX = 8;
    private static final int VERT_ERUPTION_Y_VEL = -0xA00;
    private static final int VERT_ERUPTION_PLAYER_Y_VEL = -0xC00;
    private static final int VERT_ERUPTION_MOVE_PX = 0x0A;
    private static final int VERT_FALLING_Y_VEL = -0x800;
    private static final int VERT_FALLING_GRAVITY = 0x48;
    private static final int VERT_CLEANUP_TIMER = 0x1E; // 30 frames

    // Vertical debris spawn table: {xOff, yOff, xVel, yVel} x 8
    // ROM: byte_303EA (sonic3k.asm line ~65290)
    private static final int[][] VERT_DEBRIS_TABLE = {
            {-0x18, 0, -0x200, -0xB00},
            {-0x08, 0, -0x100, -0xC00},
            {-0x18, 0, -0x400, -0x800},
            {-0x08, 0, -0x300, -0xA00},
            { 0x08, 0,  0x300, -0xC00},
            { 0x18, 0,  0x400, -0xB00},
            { 0x08, 0,  0x100, -0xA00},
            { 0x18, 0,  0x200, -0x800},
    };

    // ===== State Machine =====
    private enum HorzPhase {
        Y_GUARD,        // Phase 1: Wait for player Y < 0x500 guard
        ART_LOAD,       // Phase 2: Load art (simulated instant)
        WAIT_PROXIMITY, // Phase 4: Wait for player proximity
        SPRAY_ANIM,     // Phase 5: Spray animation + move right
        CLEANUP         // Phase 6: Timer countdown before delete
    }

    private enum VertPhase {
        PROXIMITY_CHECK, // Phase 1: Wait for player proximity
        ART_LOAD,        // Phase 2: Load art + pull players up
        RISE,            // Phase 4: Rise phase (timer-driven)
        ERUPTION,        // Phase 5: Eruption (launch upward)
        FALLING,         // Phase 6: Falling water
        CLEANUP          // Phase 7: Timer countdown before delete
    }

    // Instance state
    private final boolean isHorizontal;
    private int x;
    private int y;
    private int timer;
    private boolean artLoaded;

    // Horizontal state
    private HorzPhase horzPhase = HorzPhase.Y_GUARD;

    // Vertical state
    private VertPhase vertPhase = VertPhase.PROXIMITY_CHECK;
    private int yVel;
    private int ySub;
    private boolean playersControlled;
    private boolean debrisSpawned;

    // Mapping frame for rendering
    private int mappingFrame;

    // Art key resolved at init based on subtype
    private final String artKey;

    public HCZWaterWallObjectInstance(ObjectSpawn spawn) {
        super(spawn, "HCZWaterWall");
        this.x = spawn.x();
        this.y = spawn.y();
        this.isHorizontal = (spawn.subtype() == 0);
        this.artKey = isHorizontal
                ? Sonic3kObjectArtKeys.HCZ_GEYSER_HORZ
                : Sonic3kObjectArtKeys.HCZ_GEYSER_VERT;
        this.mappingFrame = isHorizontal ? 0 : 1;
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (isDestroyed()) return;
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;

        if (isHorizontal) {
            updateHorizontal(frameCounter, player);
        } else {
            updateVertical(frameCounter, player);
        }
    }

    // =====================================================================
    // HORIZONTAL GEYSER (Subtype 0)
    // =====================================================================

    private void updateHorizontal(int frameCounter, AbstractPlayableSprite player) {
        switch (horzPhase) {
            case Y_GUARD -> updateHorzYGuard(player);
            case ART_LOAD -> updateHorzArtLoad();
            case WAIT_PROXIMITY -> updateHorzWaitProximity(player);
            case SPRAY_ANIM -> updateHorzSprayAnim(player);
            case CLEANUP -> updateHorzCleanup();
        }
    }

    /**
     * Phase 1: Y-check guard.
     * ROM: loc_2FF04 - If player 1 y_pos < 0x500, delete self.
     */
    private void updateHorzYGuard(AbstractPlayableSprite player) {
        if (player.getCentreY() < HORZ_Y_GUARD) {
            setDestroyed(true);
            return;
        }
        horzPhase = HorzPhase.ART_LOAD;
    }

    /**
     * Phase 2: Load art.
     * ROM: loc_2FF14 - Queue ArtKosM_HCZGeyserHorz.
     * We load synchronously, so just mark done and proceed to setup.
     */
    private void updateHorzArtLoad() {
        artLoaded = true;
        // ROM setup (loc_2FF32): render_flags=4, priority=$300, width=$80, height=$20
        // Timer $30 = $20 (32 frames initial animation)
        timer = HORZ_INITIAL_TIMER;
        horzPhase = HorzPhase.WAIT_PROXIMITY;
    }

    /**
     * Phase 4: Wait for player proximity.
     * ROM: loc_2FF7C - Checks player1 x_pos - 0x60 >= object x_pos.
     * When triggered, plays SFX and spawns 8 debris children.
     */
    private void updateHorzWaitProximity(AbstractPlayableSprite player) {
        int playerX = player.getCentreX();
        if (playerX - HORZ_PLAYER_TRIGGER_OFFSET < x) {
            // Not yet in range - check if off-screen for deletion
            if (!isOnScreenX(0x100)) {
                setDestroyed(true);
            }
            return;
        }

        // Triggered! Play geyser SFX
        try {
            services().playSfx(Sonic3kSfx.GEYSER.id);
        } catch (Exception e) {
            LOG.fine(() -> "HCZWaterWall: Failed to play SFX: " + e.getMessage());
        }

        // Spawn 8 debris children
        for (int i = 0; i < HORZ_DEBRIS_TABLE.length; i++) {
            int[] entry = HORZ_DEBRIS_TABLE[i];
            int debrisX = x + HORZ_PLAYER_TRIGGER_OFFSET + entry[0];
            int debrisY = y + entry[1];
            int debrisFrame = 7 - i; // mapping_frame = loop counter (7 down to 0)

            WaterWallDebrisChild debris = new WaterWallDebrisChild(
                    debrisX, debrisY, entry[2], entry[3],
                    debrisFrame, artKey);
            spawnDynamicObject(debris);
        }

        horzPhase = HorzPhase.SPRAY_ANIM;
    }

    /**
     * Phase 5: Spray animation.
     * ROM: loc_3003C - Moves right 8px/frame, spawns spray children.
     * When timer reaches 0, transitions to cleanup.
     */
    private void updateHorzSprayAnim(AbstractPlayableSprite player) {
        if (timer <= 0) {
            // ROM: clr.b (Palette_cycle_counters+$00).w
            HCZWaterRushObjectInstance.HCZWaterRushPaletteCycleGate.setActive(false);
            timer = HORZ_CLEANUP_TIMER;
            horzPhase = HorzPhase.CLEANUP;
            return;
        }

        timer--;
        x += HORZ_SPRAY_MOVE_PX;

        // Spawn a spray particle each frame
        spawnHorzSprayChild();
    }

    /**
     * Phase 6: Cleanup.
     * ROM: loc_30106 - Counts down timer, then deletes.
     */
    private void updateHorzCleanup() {
        timer--;
        if (timer <= 0) {
            setDestroyed(true);
        }
    }

    /**
     * Spawns a horizontal spray child (ROM: loc_301DE routine).
     * Random x offset: (random & 0xF) * 8 - 0x50, y offset: +0x18.
     * 75% main art, 25% bubble art.
     */
    private void spawnHorzSprayChild() {
        int randVal = RANDOM.nextInt(16);
        int sprayXOff = randVal * 8 - 0x50;
        int sprayX = x + sprayXOff;
        int sprayY = y + 0x18;
        boolean useBubbleArt = (RANDOM.nextInt(4) == 0); // 25% chance
        int animId = RANDOM.nextInt(4);

        WaterWallSprayChild spray = new WaterWallSprayChild(
                sprayX, sprayY, 0x400, 0,
                useBubbleArt, animId, artKey);
        spawnDynamicObject(spray);
    }

    // =====================================================================
    // VERTICAL GEYSER (Subtype != 0)
    // =====================================================================

    private void updateVertical(int frameCounter, AbstractPlayableSprite player) {
        switch (vertPhase) {
            case PROXIMITY_CHECK -> updateVertProximityCheck(player);
            case ART_LOAD -> updateVertArtLoad(player);
            case RISE -> updateVertRise(player);
            case ERUPTION -> updateVertEruption(player);
            case FALLING -> updateVertFalling();
            case CLEANUP -> updateVertCleanup();
        }
    }

    /**
     * Phase 1: Player proximity check.
     * ROM: loc_30294 - Player x within 0x60 AND y within -0x40 to -0x30 of object.
     */
    private void updateVertProximityCheck(AbstractPlayableSprite player) {
        int px = player.getCentreX();
        int py = player.getCentreY();
        int dx = px - x;
        int dy = py - y;

        boolean xInRange = (dx >= -VERT_X_RANGE && dx <= VERT_X_RANGE);
        boolean yInRange = (dy >= VERT_Y_RANGE_MIN && dy <= VERT_Y_RANGE_MAX);

        if (xInRange && yInRange) {
            vertPhase = VertPhase.ART_LOAD;
            // Set object_control = $81 for player(s)
            lockPlayers(player);
            return;
        }

        // Not in range - check for deletion
        if (!isOnScreenX(0x100)) {
            setDestroyed(true);
        }
    }

    /**
     * Phase 2: Load art + pull players up.
     * ROM: loc_302BE - Queues ArtKosM_HCZGeyserVert, pulls player up by 8px/frame.
     * Art loads synchronously in the engine, so proceed to setup after one frame.
     */
    private void updateVertArtLoad(AbstractPlayableSprite player) {
        // Pull player up by 8px each frame while "loading"
        pullPlayersUp(player, VERT_ART_LOAD_PULL_PX);

        if (!artLoaded) {
            artLoaded = true;
            return; // Simulate one frame of art loading
        }

        // ROM: loc_302FA - Visual setup
        // mapping_frame = 1, timer $30 = $60, player anim = BLANK (0x1C)
        mappingFrame = 1;
        timer = VERT_RISE_TIMER;
        setPlayerAnim(player, Sonic3kAnimationIds.BLANK);

        vertPhase = VertPhase.RISE;
    }

    /**
     * Phase 4: Rise phase.
     * ROM: loc_30338 - Object rises 8px/frame while timer > 0, players always
     * rise 8px/frame. When timer <= 0x28: transition to eruption, set player
     * anim to $1A (HURT), play SFX, spawn debris.
     */
    private void updateVertRise(AbstractPlayableSprite player) {
        // ROM: tst.w $30(a0) / beq loc_30346 / subq.w #1,$30(a0) / subq.w #8,y_pos
        // Object only moves up while timer > 0
        if (timer > 0) {
            timer--;
            y -= VERT_RISE_PX;
        }

        // ROM: subi.w #8,(Player_1+y_pos) — players ALWAYS move up
        pullPlayersUp(player, VERT_RISE_PX);

        // ROM: cmpi.w #$28,$30(a0) / bhi locret_303E8
        // Transition to eruption as soon as timer <= 0x28
        if (timer <= VERT_ERUPTION_TRIGGER && !debrisSpawned) {
            debrisSpawned = true;

            // ROM: move.b #$1A,(Player_1+anim).w — HURT animation, not SPRING
            setPlayerAnim(player, Sonic3kAnimationIds.HURT);

            // Play geyser SFX
            try {
                services().playSfx(Sonic3kSfx.GEYSER.id);
            } catch (Exception e) {
                LOG.fine(() -> "HCZWaterWall: Failed to play SFX: " + e.getMessage());
            }

            // ROM: move.b #1,(Palette_cycle_counters+$00).w
            HCZWaterRushObjectInstance.HCZWaterRushPaletteCycleGate.setActive(true);

            // Spawn 8 debris
            for (int i = 0; i < VERT_DEBRIS_TABLE.length; i++) {
                int[] entry = VERT_DEBRIS_TABLE[i];
                int debrisX = x + entry[0];
                int debrisY = y - 0x80 + entry[1];

                WaterWallDebrisChild debris = new WaterWallDebrisChild(
                        debrisX, debrisY, entry[2], entry[3],
                        i, artKey);
                spawnDynamicObject(debris);
            }

            // Transition to eruption phase immediately
            yVel = VERT_ERUPTION_Y_VEL;
            vertPhase = VertPhase.ERUPTION;
        }
    }

    /**
     * Phase 5: Eruption.
     * ROM: loc_3041A - Object moves up with gravity, players move up 0xA px/frame.
     * When timer reaches 0: release players, set player y_vel = -$C00.
     */
    private void updateVertEruption(AbstractPlayableSprite player) {
        timer--;

        // Move players up by constant amount
        pullPlayersUp(player, VERT_ERUPTION_MOVE_PX);

        // Move object with gravity
        SubpixelMotion.State motionState = new SubpixelMotion.State(x, y, 0, ySub, 0, yVel);
        SubpixelMotion.moveSprite(motionState, VERT_FALLING_GRAVITY);
        y = motionState.y;
        ySub = motionState.ySub;
        yVel = motionState.yVel;

        // Spawn spray pairs each frame
        spawnVertSprayPair();

        if (timer <= 0) {
            // Release players
            releasePlayers(player);

            // Set player velocities: x_vel=0, y_vel=-$C00
            player.setXSpeed((short) 0);
            player.setYSpeed((short) VERT_ERUPTION_PLAYER_Y_VEL);
            player.setAir(true);

            // Apply to sidekicks
            for (PlayableEntity sidekickEntity : services().sidekicks()) {
                if (sidekickEntity instanceof AbstractPlayableSprite sidekick) {
                    sidekick.setXSpeed((short) 0);
                    sidekick.setYSpeed((short) VERT_ERUPTION_PLAYER_Y_VEL);
                    sidekick.setAir(true);
                }
            }

            // Object continues falling with reduced velocity
            yVel = VERT_FALLING_Y_VEL;
            vertPhase = VertPhase.FALLING;
        }
    }

    /**
     * Phase 6: Falling water.
     * ROM: loc_3052A - MoveSprite + gravity. Delete when off screen.
     */
    private void updateVertFalling() {
        SubpixelMotion.State motionState = new SubpixelMotion.State(x, y, 0, ySub, 0, yVel);
        SubpixelMotion.moveSprite(motionState, VERT_FALLING_GRAVITY);
        y = motionState.y;
        ySub = motionState.ySub;
        yVel = motionState.yVel;

        if (!isOnScreen(0x80)) {
            // ROM: clr.b (Palette_cycle_counters+$00).w
            HCZWaterRushObjectInstance.HCZWaterRushPaletteCycleGate.setActive(false);
            timer = VERT_CLEANUP_TIMER;
            vertPhase = VertPhase.CLEANUP;
        }
    }

    /**
     * Phase 7: Cleanup.
     * ROM: loc_30106 - Counts down timer, then deletes.
     */
    private void updateVertCleanup() {
        timer--;
        if (timer <= 0) {
            setDestroyed(true);
        }
    }

    /**
     * Spawns a pair of vertical spray children.
     * ROM: sub_304DA - Random positioning, 75% main art / 25% bubble art.
     */
    private void spawnVertSprayPair() {
        for (int i = 0; i < 2; i++) {
            int randX = RANDOM.nextInt(16) * 0x40;
            if (RANDOM.nextBoolean()) randX = -randX;
            int sprayX = x + (randX >> 8);
            int sprayY = y;
            boolean useBubbleArt = (RANDOM.nextInt(4) == 0);
            int animId = RANDOM.nextInt(4);

            WaterWallSprayChild spray = new WaterWallSprayChild(
                    sprayX, sprayY, randX >> 4, -0x700,
                    useBubbleArt, animId, artKey);
            spawnDynamicObject(spray);
        }
    }

    // ===== Player Control Helpers =====

    private void lockPlayers(AbstractPlayableSprite player) {
        if (playersControlled) return;
        playersControlled = true;

        player.setObjectControlled(true);
        player.setControlLocked(true);

        for (PlayableEntity sidekickEntity : services().sidekicks()) {
            if (sidekickEntity instanceof AbstractPlayableSprite sidekick) {
                sidekick.setObjectControlled(true);
                sidekick.setControlLocked(true);
            }
        }
    }

    private void releasePlayers(AbstractPlayableSprite player) {
        if (!playersControlled) return;
        playersControlled = false;

        player.setObjectControlled(false);
        player.setControlLocked(false);

        for (PlayableEntity sidekickEntity : services().sidekicks()) {
            if (sidekickEntity instanceof AbstractPlayableSprite sidekick) {
                sidekick.setObjectControlled(false);
                sidekick.setControlLocked(false);
            }
        }
    }

    private void pullPlayersUp(AbstractPlayableSprite player, int pixels) {
        player.setY((short) (player.getY() - pixels));

        for (PlayableEntity sidekickEntity : services().sidekicks()) {
            if (sidekickEntity instanceof AbstractPlayableSprite sidekick) {
                sidekick.setY((short) (sidekick.getY() - pixels));
            }
        }
    }

    private void setPlayerAnim(AbstractPlayableSprite player, Sonic3kAnimationIds animId) {
        player.setAnimationId(animId);

        for (PlayableEntity sidekickEntity : services().sidekicks()) {
            if (sidekickEntity instanceof AbstractPlayableSprite sidekick) {
                sidekick.setAnimationId(animId);
            }
        }
    }

    // ===== Rendering =====

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(artKey);
        if (renderer != null) {
            renderer.drawFrameIndex(mappingFrame, x, y, false, false);
        } else {
            // Debug fallback: draw coloured box
            float r = isHorizontal ? 0.2f : 0.3f;
            float g = 0.5f;
            float b = 0.9f;
            int hw = isHorizontal ? 0x40 : 0x10;
            int hh = isHorizontal ? 0x10 : 0x30;
            appendDebugBox(commands, x, y, hw, hh, r, g, b);
        }
    }

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        // Draw trigger zone in debug mode
        if (isHorizontal) {
            if (horzPhase == HorzPhase.WAIT_PROXIMITY) {
                int triggerX = x + HORZ_PLAYER_TRIGGER_OFFSET;
                ctx.drawRect(triggerX, y, 4, 0x20, 0.0f, 0.8f, 1.0f);
            }
        } else {
            if (vertPhase == VertPhase.PROXIMITY_CHECK) {
                ctx.drawRect(x, y + VERT_Y_RANGE_MIN, VERT_X_RANGE, 8, 0.0f, 0.8f, 1.0f);
            }
        }
    }

    // ===== Utility =====

    private int getWaterLevel() {
        try {
            var ws = services().waterSystem();
            if (ws != null) {
                return ws.getWaterLevelY(services().romZoneId(), services().currentAct());
            }
        } catch (Exception e) {
            LOG.fine(() -> "HCZWaterWall.getWaterLevel: " + e.getMessage());
        }
        return 0;
    }

    private static ObjectSpawn createChildSpawn(int x, int y) {
        return new ObjectSpawn(x, y, 0x3B, 0, 4, false, 0);
    }

    private static void appendDebugBox(List<GLCommand> commands, int cx, int cy,
            int hw, int hh, float r, float g, float b) {
        int l = cx - hw, right = cx + hw, t = cy - hh, bot = cy + hh;
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1,
                GLCommand.BlendType.SOLID, r, g, b, l, t, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1,
                GLCommand.BlendType.SOLID, r, g, b, right, t, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1,
                GLCommand.BlendType.SOLID, r, g, b, right, t, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1,
                GLCommand.BlendType.SOLID, r, g, b, right, bot, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1,
                GLCommand.BlendType.SOLID, r, g, b, right, bot, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1,
                GLCommand.BlendType.SOLID, r, g, b, l, bot, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1,
                GLCommand.BlendType.SOLID, r, g, b, l, bot, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1,
                GLCommand.BlendType.SOLID, r, g, b, l, t, 0, 0));
    }

    // =====================================================================
    // CHILD: Water Wall Debris
    // =====================================================================

    /**
     * Debris child spawned by both horizontal and vertical geysers.
     * <p>
     * ROM: loc_3011A - Animates through 8 frames, moves with gravity (0x38).
     * When y &gt; Water_level: stops y_vel, halves x_vel twice, spawns splash.
     * Transitions to slow sinking with gravity=8, timer=9 frames.
     */
    static class WaterWallDebrisChild extends AbstractObjectInstance {

        private static final int GRAVITY = 0x38;
        private static final int SLOW_GRAVITY = 8;
        private static final int ANIM_RESET_TIMER = 2;
        private static final int SLOW_SINK_TIMER = 9;

        private enum DebrisState { FLYING, SINKING }

        private final SubpixelMotion.State motion;
        private int mappingFrame;
        private int animTimer = ANIM_RESET_TIMER;
        private DebrisState state = DebrisState.FLYING;
        private int sinkTimer = SLOW_SINK_TIMER;
        private boolean splashSpawned;
        private final String parentArtKey;

        WaterWallDebrisChild(int x, int y, int xVel, int yVel,
                int initialFrame, String parentArtKey) {
            super(createChildSpawn(x, y), "WaterWallDebris");
            this.motion = new SubpixelMotion.State(x, y, 0, 0, xVel, yVel);
            this.mappingFrame = initialFrame & 7;
            this.parentArtKey = parentArtKey;
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

        private void updateFlying() {
            // Animate
            animTimer--;
            if (animTimer <= 0) {
                animTimer = ANIM_RESET_TIMER;
                mappingFrame = (mappingFrame + 1) & 7;
            }

            // Move with gravity
            SubpixelMotion.moveSprite(motion, GRAVITY);

            // Check water level
            int waterLevel = getWaterLevel();
            if (waterLevel > 0 && motion.y > waterLevel) {
                // Stop vertical, halve horizontal twice (asr twice)
                motion.yVel = 0;
                motion.xVel >>= 2;

                // Spawn water splash at water level
                if (!splashSpawned) {
                    splashSpawned = true;
                    WaterWallSplashChild splash = new WaterWallSplashChild(
                            motion.x, waterLevel, parentArtKey);
                    spawnDynamicObject(splash);
                }

                state = DebrisState.SINKING;
            }
        }

        private void updateSinking() {
            // Slow gravity, timer-based deletion
            SubpixelMotion.moveSprite(motion, SLOW_GRAVITY);

            sinkTimer--;
            if (sinkTimer <= 0 || !isOnScreen(0x80)) {
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
            // ROM: Debris uses Map_HCZWaterWallDebris (8 frames, separate from main geyser).
            // Art tiles are at ArtTile_HCZGeyser+$58, registered under HCZ_GEYSER_DEBRIS.
            PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.HCZ_GEYSER_DEBRIS);
            if (renderer != null) {
                renderer.drawFrameIndex(mappingFrame, motion.x, motion.y, false, false);
            } else {
                appendDebugBox(commands, motion.x, motion.y, 8, 12, 0.3f, 0.6f, 1.0f);
            }
        }
    }

    // =====================================================================
    // CHILD: Water Wall Spray Particle
    // =====================================================================

    /**
     * Spray particle child.
     * <p>
     * ROM: loc_301DE - Moves with gravity (0x28).
     * When y &gt; Water_level: snaps to water level, advances anim by 4,
     * transitions to surface animation. Deletes when anim ends.
     */
    static class WaterWallSprayChild extends AbstractObjectInstance {

        private static final int GRAVITY = 0x28;

        private enum SprayState { FALLING, SURFACE }

        private final SubpixelMotion.State motion;
        private SprayState state = SprayState.FALLING;
        private int animId;
        private int animTimer;
        private int animFrame;
        private final boolean useBubbleArt;
        private final String parentArtKey;
        private int surfaceFrameCount;

        WaterWallSprayChild(int x, int y, int xVel, int yVel,
                boolean useBubbleArt, int animId, String parentArtKey) {
            super(createChildSpawn(x, y), "WaterWallSpray");
            this.motion = new SubpixelMotion.State(x, y, 0, 0, xVel, yVel);
            this.useBubbleArt = useBubbleArt;
            this.animId = animId;
            this.parentArtKey = parentArtKey;
            this.animTimer = 2 + RANDOM.nextInt(4);
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
                case FALLING -> updateFalling();
                case SURFACE -> updateSurface();
            }
        }

        private void updateFalling() {
            SubpixelMotion.moveSprite(motion, GRAVITY);

            int waterLevel = getWaterLevel();
            if (waterLevel > 0 && motion.y > waterLevel) {
                motion.y = waterLevel;
                // Advance to surface animation
                animId += 4;
                state = SprayState.SURFACE;
                surfaceFrameCount = 0;
            }

            // Off-screen check
            if (!isOnScreen(0x80)) {
                setDestroyed(true);
            }
        }

        private void updateSurface() {
            // Surface animation plays through, then deletes
            animTimer--;
            if (animTimer <= 0) {
                animTimer = 3;
                animFrame++;
                surfaceFrameCount++;
                if (surfaceFrameCount > 12) {
                    setDestroyed(true);
                }
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

        /**
         * Returns the mapping frame index for the current animation state.
         * <p>
         * ROM uses Ani_HCZWaterWall animations, with 25% of spray using
         * ArtTile_Bubbles (separate bubble art). Since we don't have bubble
         * art loaded, bubble spray uses frame 2 (single tiny dot) from the
         * geyser sheet, giving a small bubble-like appearance.
         * <p>
         * Non-bubble spray: anim 0-3 map to splash effect frames.
         * Surface anims (after hitting water): smaller fade-out frames.
         */
        private int getCurrentMappingFrame() {
            if (useBubbleArt) {
                // Bubble spray: tiny single-tile dot, frame 2
                return 2;
            }
            return switch (animId) {
                case 0 -> 2;  // tiny dot
                case 1 -> 3 + (animFrame % 3); // frames 3,4,5 (splash cycle)
                case 2, 3 -> 6 + (animFrame % 2); // frames 6,7 (splash cycle)
                case 4, 5, 6, 7 -> 8; // small surface frame
                default -> 2;
            };
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            // ROM: spray art_tile = ArtTile_HCZGeyser+$30 (75%) or ArtTile_Bubbles (25%).
            // Use the spray-specific sheet (tile indices offset by $30) for non-bubble,
            // or the bubble sheet for bubble spray.
            String renderKey = useBubbleArt
                    ? Sonic3kObjectArtKeys.HCZ_BUBBLES
                    : Sonic3kObjectArtKeys.HCZ_GEYSER_SPRAY;
            PatternSpriteRenderer renderer = getRenderer(renderKey);
            if (renderer != null) {
                int frame = getCurrentMappingFrame();
                renderer.drawFrameIndex(frame, motion.x, motion.y, false, false);
            }
        }
    }

    // =====================================================================
    // CHILD: Water Wall Splash
    // =====================================================================

    /**
     * Water splash child spawned when debris hits the water surface.
     * <p>
     * ROM: loc_3023E - Uses Map_HCZWaterWall with ArtTile_HCZGeyser+$30, palette 1.
     * Animates through splash frames, then deletes.
     */
    static class WaterWallSplashChild extends AbstractObjectInstance {

        private static final int TOTAL_FRAMES = 8;

        private final int x;
        private final int y;
        private int animTimer = 3;
        private int totalFramesPlayed;
        private final String parentArtKey;

        WaterWallSplashChild(int x, int y, String parentArtKey) {
            super(createChildSpawn(x, y), "WaterWallSplash");
            this.x = x;
            this.y = y;
            this.parentArtKey = parentArtKey;
        }

        @Override
        public int getX() {
            return x;
        }

        @Override
        public int getY() {
            return y;
        }

        @Override
        public void update(int frameCounter, PlayableEntity playerEntity) {
            if (isDestroyed()) return;

            animTimer--;
            if (animTimer <= 0) {
                animTimer = 3;
                totalFramesPlayed++;
                if (totalFramesPlayed >= TOTAL_FRAMES) {
                    setDestroyed(true);
                }
            }
        }

        /**
         * Returns mapping frame for splash animation (Ani_HCZWaterWall anim 8).
         * ROM sequence: frames 1,9,1,A,0,9,1,A,1,9,0,A,4,8,2,FC
         * Simplified: cycle through small splash frames from the geyser sheet.
         */
        private int getSplashMappingFrame() {
            // Anim 8 sequence uses geyser sheet frames 9 and 10 primarily
            return switch (totalFramesPlayed % 4) {
                case 0 -> 9;
                case 1 -> 10;
                case 2 -> 9;
                case 3 -> 10;
                default -> 9;
            };
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            // ROM: splash art_tile = ArtTile_HCZGeyser+$30, palette 1.
            // Use the spray-specific sheet (tile indices offset by $30).
            PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.HCZ_GEYSER_SPRAY);
            if (renderer != null) {
                renderer.drawFrameIndex(getSplashMappingFrame(), x, y, false, false);
            }
        }
    }
}
