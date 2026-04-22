package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.Sonic3kObjectArtProvider;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.objects.boss.AbstractBossInstance;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.SwingMotion;

import java.util.List;
import java.util.logging.Logger;

/**
 * S3K MGZ Act 2 "Drilling Robotnik" mini-event instance.
 *
 * <h3>ROM reference (sonic3k.asm:142384-142436)</h3>
 * {@code Obj_MGZ2DrillingRobotnik}: first-frame init changes main to
 * {@code Obj_Wait} (120 frames), queues {@code ArtKosM_MGZEndBoss} +
 * {@code ArtKosM_MGZEndBossDebris}, loads PLC #$6D (Robotnik ship/explosion/egg
 * capsule art), loads {@code Pal_MGZEndBoss} to palette line 1, and fades music.
 * Then {@code Obj_MGZ2DrillingRobotnikGo} plays {@code mus_EndBoss} and
 * {@code Obj_MGZ2DrillingRobotnikStart} dispatches routine 0 →
 * {@code loc_6BFCA}:
 * <ul>
 *   <li>SetUp_ObjAttributes(ObjDat_MGZDrillBoss) → mapping_frame = 0, collision = $F.</li>
 *   <li>{@code CreateChild1_Normal(Child1_MakeRoboShip3)} → spawn {@code Obj_RobotnikShip3}
 *       with subtype 9 (pilot-pod mapping frame). That child then spawns
 *       {@code Child1_MakeRoboHead} (Robotnik's animated face).</li>
 *   <li>{@code CreateChild1_Normal(ChildObjDat_6D7C0)} → 4 drill-piece children.</li>
 * </ul>
 * {@code MGZ2_SpecialCheckHit} (sonic3k.asm:144369): mini-event instances
 * refresh {@code collision_property} to 1 on every fatal hit (the event flag
 * at $46 is set), so the player is never credited with a kill. The event
 * instance still runs its scripted drill-drop → swing → ceiling-escape flow.
 *
 * <h3>Composite rendering (inlined)</h3>
 * The ROM spawns each sub-sprite as a separate child object; we draw them all
 * inline from the parent's {@link #appendRenderCommands} so the whole
 * silhouette shares a single render pass and z-order is deterministic. The
 * parts are:
 * <ol>
 *   <li>4 × drill-piece children (ROM: {@code ChildObjDat_6D7C0}) — mapping
 *       frames 1, 2, 6, 6 of {@code Map_MGZEndBoss} at the child-data offsets
 *       (-$14,+$0F), (-$1C,+$10), (+$08,+$18), (-$0C,+$18).</li>
 *   <li>Drill body — {@code Map_MGZEndBoss} frame 0 at the anchor.</li>
 *   <li>Pilot pod — {@code Map_RobotnikShip} frame 9 at offset (-6, +4).</li>
 *   <li>Robotnik head — {@code Map_RobotnikShip} frames 0/1 (blinking) /
 *       2 (hurt face) / 3 (defeated), at offset (0, -$1C) from the pod.</li>
 * </ol>
 */
public final class MgzDrillingRobotnikInstance extends AbstractBossInstance {
    private static final Logger LOG = Logger.getLogger(MgzDrillingRobotnikInstance.class.getName());

    /** ROM: Obj_MGZ2DrillingRobotnik $2E(a0) = 2*60 — initial wait frames. */
    private static final int INIT_WAIT_FRAMES = 2 * 60;

    private static final int ROUTINE_INIT = 0;
    private static final int ROUTINE_START_DROP = 2;
    private static final int ROUTINE_DRILL_DROP = 4;
    private static final int ROUTINE_HANG = 6;
    private static final int ROUTINE_CEILING_ESCAPE = 0x16;
    private static final int ROUTINE_ESCAPE_WAIT = 0x18;

    /** ROM: move.w #-$800,y_vel — initial upward velocity into ceiling. */
    private static final int INITIAL_Y_VEL = -0x800;
    /** ROM: addi.w #$40,d0 — per-frame gravity applied during DROP. */
    private static final int GRAVITY_PER_FRAME = 0x40;
    /** ROM: cmpi.w #$C0,d0 / bge — DROP ends when y_vel reaches this terminal. */
    private static final int DROP_TERMINAL_Y_VEL = 0xC0;
    /** ROM: move.w #-$400,y_vel before flee (loc_6C1B2). */
    private static final int FLEE_Y_VEL = -0x400;

    /** ROM: move.b #5,$39(a0) / Swing_UpAndDown_Count. */
    private static final int SWING_HALF_CYCLES = 5;
    /** ROM: Swing_Setup1 sets max speed to $C0 and accel to $10. */
    private static final int SWING_MAX_SPEED = 0xC0;
    private static final int SWING_ACCEL = 0x10;
    /** ROM: move.w #$7F,$2E(a0) during the upward escape. */
    private static final int ESCAPE_TIMER = 0x7F;

    /** ObjDat_MGZDrillBoss collision_flags byte = $F (ENEMY category, size $F). */
    private static final int BODY_COLLISION_FLAGS = 0x0F;
    private static final int COLLISION_SIZE = 0x0F;
    /** ROM: collision_property = -1 (loc_6BFCA:142441). Nonzero HP → bounce path. */
    private static final int ROM_COLLISION_PROPERTY = 0xFF;
    /** I-frames after a hit (matches AbstractBossInstance default). */
    private static final int INVULNERABILITY_TIME = 0x20;
    /** ROM: ObjDat_MGZDrillBoss priority word = $300 → render bucket 6. */
    private static final int PRIORITY_BUCKET = 6;
    private static final int OBJECT_PATTERN_BASE = 0x20000;
    private static final int MGZ_BOSS_PALETTE_LINE = 1;
    private static final int ROBOTNIK_SHIP_PALETTE_LINE = 0;

    /** Drilling-pose mapping frame (ObjDat_MGZDrillBoss initial mapping_frame). */
    private static final int FRAME_DRILL_POSE = 0;

    private static final int Y_RADIUS_OFFSCREEN = 0x24;
    private static final int FLEE_ABOVE_CAMERA_MARGIN = 0x60;

    /** ROM: Child1_MakeRoboShip3 pod-child offset (-6, +4). */
    private static final int POD_OFFSET_X = -6;
    private static final int POD_OFFSET_Y = 4;
    /** Subtype 9 → pilot pod frame (Map_RobotnikShip frame 9). */
    private static final int POD_FRAME = 9;
    /** Obj_RobotnikShipWait → Obj_RobotnikShipReady switches to frame $A. */
    private static final int POD_ESCAPE_FRAME = 10;
    /** Child1_MakeRoboShipFlame uses Map_RobotnikShip frame 6 at (+$1E, 0). */
    private static final int SHIP_FLAME_FRAME = 6;
    /** Map_RobotnikShip frame 6 inherits the ship's palette line 0. */
    private static final int SHIP_FLAME_PALETTE_LINE = ROBOTNIK_SHIP_PALETTE_LINE;
    private static final int SHIP_FLAME_OFFSET_X = 0x1E;
    private static final int SHIP_FLAME_OFFSET_Y = 0;
    /** ROM: Child1_MakeRoboHead offset (0, -$1C) from the pod. */
    private static final int HEAD_OFFSET_X = 0;
    private static final int HEAD_OFFSET_Y = -0x1C;
    /** ROM: AniRaw_RobotnikHead frame delay (first byte = 5). */
    private static final int HEAD_ANIM_DELAY = 5;
    /** ROM: Obj_RobotnikHeadMain — status bit 6 set → mapping_frame = 2 (hurt face). */
    private static final int HEAD_FRAME_HURT = 2;

    /**
     * ROM: {@code ChildObjDat_6D7C0} (sonic3k.asm:144579). Four children
     * spawned from loc_6BFCA at these offsets, using the listed mapping frames
     * of {@code Map_MGZEndBoss}. Format: {mappingFrame, offX, offY}.
     */
    private static final int[][] DRILL_PIECES = {
            {1, -0x14, 0x0F},   // ChildObjDat_6D7C0 entry 0 / word_6D77C
            {4, 0x14, -0x14},   // loc_6C948 at angle $C via byte_6D24A/byte_6D284
            {6, 0x08, 0x18},    // ChildObjDat_6D7C0 entry 2 / word_6D79A
            {6, -0x0C, 0x18},   // ChildObjDat_6D7C0 entry 3 / word_6D79A
    };
    private static final int FRAME_DRILL_HEAD = 0x0F;
    private static final int DRILL_HEAD_OFFSET_X = 0x14;
    private static final int DRILL_HEAD_OFFSET_Y = -0x34;
    /** loc_6CF20 uses word_6D7A0: make_art_tile(ArtTile_MGZEndBoss,0,0). */
    private static final int FRAME_THRUSTER_FLAME = 0x19;
    private static final int THRUSTER_FLAME_PALETTE_LINE = 0;
    private static final int[][] THRUSTER_FLAMES = {
            {0x08, 0x28},
            {-0x0C, 0x28},
    };

    private static final int[] FLASH_COLOR_INDICES = {11, 13, 14};
    private static final int[] FLASH_COLORS_NORMAL = {0x0020, 0x0866, 0x0644};
    private static final int[] FLASH_COLORS_BRIGHT = {0x0EEE, 0x0888, 0x0AAA};

    /**
     * ROM: {@code ChildObjDat_6D7EA} / {@code _6D7F2} (sonic3k.asm:144597) spawn
     * 10 debris chunks from {@code loc_6C024} during the drill drop once the
     * drill is no higher than {@code Camera_Y+$120}. Spawn offset is (+$18, -$40)
     * (flipped to (-$18, -$40) for events 2 & 3). Each chunk gets a different
     * mapping frame + indexed velocity from {@code RawAni_6D3FC} / {@code word_6D406}
     * (loc_6D3E2).
     */
    private static final int DEBRIS_SPAWN_OFFSET_X = 0x18;
    private static final int DEBRIS_SPAWN_OFFSET_Y = -0x40;
    /** ROM: RawAni_6D3FC — mapping_frame per debris index (10 entries). */
    private static final int[] FALLING_DEBRIS_FRAMES = {
            0, 1, 2, 0, 0, 1, 0, 2, 0, 1,
    };
    /** ROM: word_6D406 — (x_vel, y_vel) pairs indexed by debris slot. */
    private static final int[][] FALLING_DEBRIS_VELOCITIES = {
            {-0x400, -0x400}, { 0x400, -0x400},
            { -0x80, -0x400}, {  0x80, -0x400},
            {-0x300, -0x200}, { 0x300, -0x200},
            {-0x200, -0x300}, { 0x200, -0x300},
            { -0x80, -0x200}, {  0x80, -0x200},
    };
    /** ROM: MoveSprite_LightGravity — add $18 to y_vel per frame. */
    private static final int DEBRIS_GRAVITY = 0x18;
    /** Pixels below camera bottom at which a falling-debris chunk self-deletes. */
    private static final int DEBRIS_OFFSCREEN_MARGIN = 0x40;

    private int yVel;
    private int ySubpixel;
    private int waitTimer;
    private boolean flipX;
    private boolean artQueued;
    private boolean palettesLoaded;
    private boolean bossMusicPlayed;
    private boolean hit;
    /** Per-render counter that drives head blink / i-frame flash. */
    private int renderTick;
    private int swingHalfCyclesRemaining;
    private boolean swingDirectionDown;
    private int escapeTimer;
    /** True once the 10 falling-debris chunks have been initialised (ROM: bset #7,$38). */
    private boolean fallingDebrisSpawned;
    /** 10 × 16:8 fixed-point (x, y, xVel, yVel) rows; last slot is `alive` flag. */
    private final int[] fallingDebrisX = new int[10];
    private final int[] fallingDebrisY = new int[10];
    private final int[] fallingDebrisVx = new int[10];
    private final int[] fallingDebrisVy = new int[10];
    private final boolean[] fallingDebrisAlive = new boolean[10];
    public MgzDrillingRobotnikInstance(ObjectSpawn spawn, boolean flipX) {
        super(spawn, "MGZ2DrillingRobotnik");
        this.flipX = flipX;
    }

    @Override
    protected void initializeBossState() {
        // NOTE: AbstractBossInstance calls this from its constructor before our
        // subclass field initializers run, so the fallingDebris* arrays are
        // still null at this point. We rely on Java's array default (all zeros /
        // all false) once their initializers do fire. Do NOT index the arrays
        // here — that would NPE. Primitive field resets are safe because JVM
        // already zero-initialised them.
        state.x = spawn.x();
        state.y = spawn.y();
        state.xFixed = state.x << 16;
        state.yFixed = state.y << 16;
        state.routine = ROUTINE_INIT;
        state.hitCount = getInitialHitCount();
        yVel = INITIAL_Y_VEL;
        ySubpixel = 0;
        waitTimer = INIT_WAIT_FRAMES;
        artQueued = false;
        palettesLoaded = false;
        bossMusicPlayed = false;
        hit = false;
        renderTick = 0;
        swingHalfCyclesRemaining = SWING_HALF_CYCLES;
        swingDirectionDown = false;
        escapeTimer = ESCAPE_TIMER;
        fallingDebrisSpawned = false;
    }

    @Override
    protected int getInitialHitCount() {
        // ROM: collision_property = -1; mini-event HP is refreshed on every fatal
        // hit, so Robotnik can be hit many times but never "defeated" by the player.
        return 0xFF;
    }

    @Override
    protected void updateBossLogic(int frameCounter, PlayableEntity playerEntity) {
        queueInitialAssetsIfNeeded();

        // ROM: Obj_MGZ2DrillingRobotnik init queues art + PLC #$6D + palette and
        // sits in Obj_Wait for 120 frames before becoming DrillingRobotnikStart.
        if (waitTimer > 0) {
            waitTimer--;
            if (waitTimer == 0) {
                playBossMusicOnce();
                state.routine = ROUTINE_START_DROP;
            }
            updateCustomFlash();
            return;
        }

        switch (state.routine) {
            case ROUTINE_START_DROP -> updateStartDrop();
            case ROUTINE_DRILL_DROP -> updateDrillDrop();
            case ROUTINE_HANG -> updateHang();
            case ROUTINE_CEILING_ESCAPE -> updateCeilingEscape();
            case ROUTINE_ESCAPE_WAIT -> updateEscapeWait();
            default -> {
            }
        }

        updateFallingDebris();
        updateCustomFlash();

        state.xFixed = state.x << 16;
        state.yFixed = state.y << 16;
    }

    /** ROM: loc_6C014 — play collapse SFX, then enter the drill drop. */
    private void updateStartDrop() {
        state.routine = ROUTINE_DRILL_DROP;
        services().playSfx(Sonic3kSfx.COLLAPSE.id);
    }

    /** ROM: loc_6C024 — gravity accumulation until terminal velocity. */
    private void updateDrillDrop() {
        yVel += GRAVITY_PER_FRAME;
        if (yVel >= DROP_TERMINAL_Y_VEL) {
            setupSwing();
            state.routine = ROUTINE_HANG;
            return;
        }
        // ROM: spawn the arrival debris while the drill is at or below Camera_Y+$120.
        if (!fallingDebrisSpawned) {
            int cameraY = services().camera().getY() & 0xFFFF;
            if (state.y <= cameraY + 0x120) {
                spawnFallingDebris();
            }
        }
        applyYVelocity();
    }

    private void setupSwing() {
        yVel = SWING_MAX_SPEED;
        swingDirectionDown = false;
        swingHalfCyclesRemaining = SWING_HALF_CYCLES;
    }

    /**
     * ROM: {@code CreateChild3_NormalRepeated(ChildObjDat_6D7EA)} — spawns 10
     * debris chunks at offset (+$18, -$40) from the drill. Each gets a distinct
     * mapping frame (from RawAni_6D3FC) and velocity (from word_6D406), then
     * falls under light gravity.
     */
    private void spawnFallingDebris() {
        int spawnOffX = flipX ? -DEBRIS_SPAWN_OFFSET_X : DEBRIS_SPAWN_OFFSET_X;
        int spawnX = state.x + spawnOffX;
        int spawnY = state.y + DEBRIS_SPAWN_OFFSET_Y;
        fallingDebrisSpawned = true;
        for (int i = 0; i < 10; i++) {
            fallingDebrisX[i] = spawnX << 8;
            fallingDebrisY[i] = spawnY << 8;
            int vx = FALLING_DEBRIS_VELOCITIES[i][0];
            int vy = FALLING_DEBRIS_VELOCITIES[i][1];
            // Mirror X-velocities when the parent is facing left.
            fallingDebrisVx[i] = flipX ? -vx : vx;
            fallingDebrisVy[i] = vy;
            fallingDebrisAlive[i] = true;
        }
    }

    /** ROM: loc_6CFB2 — per-frame debris update (MoveSprite_LightGravity). */
    private void updateFallingDebris() {
        if (!fallingDebrisSpawned) {
            return;
        }
        int cameraBottom = (services().camera().getY() & 0xFFFF) + 240;
        for (int i = 0; i < 10; i++) {
            if (!fallingDebrisAlive[i]) {
                continue;
            }
            fallingDebrisX[i] += fallingDebrisVx[i];
            fallingDebrisY[i] += fallingDebrisVy[i];
            fallingDebrisVy[i] += DEBRIS_GRAVITY;
            int worldY = fallingDebrisY[i] >> 8;
            if (worldY > cameraBottom + DEBRIS_OFFSCREEN_MARGIN) {
                fallingDebrisAlive[i] = false;
            }
        }
    }

    /** ROM: loc_6C07E / loc_6C0B2 — hang in place for a scripted duration. */
    private void updateHang() {
        SwingMotion.Result swing = SwingMotion.update(SWING_ACCEL, yVel, SWING_MAX_SPEED, swingDirectionDown);
        yVel = swing.velocity();
        swingDirectionDown = swing.directionDown();
        if (swing.directionChanged()) {
            swingHalfCyclesRemaining--;
        }
        if (state.invulnerable || swingHalfCyclesRemaining < 0) {
            enterCeilingEscape();
        }
        applyYVelocity();
    }

    /** ROM: loc_6C092 / loc_6C1B2 — switch into the upward ceiling-escape path. */
    private void enterCeilingEscape() {
        yVel = FLEE_Y_VEL;
        state.routine = ROUTINE_CEILING_ESCAPE;
        escapeTimer = ESCAPE_TIMER;
    }

    /** ROM: loc_6C1D4 — move upward, check the ceiling, then wait for cleanup. */
    private void updateCeilingEscape() {
        var ceiling = ObjectTerrainUtils.checkCeilingDist(state.x, state.y, Y_RADIUS_OFFSCREEN);
        if (ceiling.distance() < 0) {
            spawnFallingDebris();
            state.routine = ROUTINE_ESCAPE_WAIT;
            advanceEscapeTimer();
            return;
        }
        applyYVelocity();
        advanceEscapeTimer();
    }

    /** ROM: loc_6C2B2 — keep moving while Obj_Wait counts down to loc_6C200. */
    private void updateEscapeWait() {
        applyYVelocity();
        advanceEscapeTimer();
    }

    private void advanceEscapeTimer() {
        escapeTimer--;
        if (escapeTimer > 0) {
            return;
        }
        restoreMgzPalette();
        services().playMusic(Sonic3kMusic.MGZ2.id);
        setDestroyed(true);
        LOG.fine(() -> "MGZ2 Drilling Robotnik cleanup completed at y=" + state.y);
    }

    private void applyYVelocity() {
        int fixedY = (state.y << 8) | (ySubpixel & 0xFF);
        fixedY += yVel;
        state.y = fixedY >> 8;
        ySubpixel = fixedY & 0xFF;
    }

    /**
     * ROM init-time side effects (sonic3k.asm:142384-142401):
     * queue MGZ end-boss art, load PLC #$6D (shared Robotnik ship art), and
     * load Pal_MGZEndBoss into palette line 1.
     */
    private void queueInitialAssetsIfNeeded() {
        if (artQueued) {
            return;
        }
        ensureArtLoaded();
        loadBossPalette();
        artQueued = true;
    }

    /** ROM: Obj_MGZ2DrillingRobotnikGo (sonic3k.asm:142404) — Play_Music(mus_EndBoss). */
    private void playBossMusicOnce() {
        if (bossMusicPlayed) {
            return;
        }
        services().playMusic(Sonic3kMusic.BOSS.id);
        bossMusicPlayed = true;
    }

    private void ensureArtLoaded() {
        ObjectRenderManager renderManager = services().renderManager();
        if (renderManager == null) {
            return;
        }
        if (renderManager.getArtProvider() instanceof Sonic3kObjectArtProvider s3kProvider) {
            s3kProvider.ensureStandaloneArtLoaded(Sonic3kObjectArtKeys.MGZ_ENDBOSS);
            s3kProvider.ensureStandaloneArtLoaded(Sonic3kObjectArtKeys.ROBOTNIK_SHIP);
            s3kProvider.ensureStandaloneArtLoaded(Sonic3kObjectArtKeys.MGZ_ENDBOSS_DEBRIS);
        }
        if (services().graphicsManager() != null) {
            renderManager.ensurePatternsCached(services().graphicsManager(), OBJECT_PATTERN_BASE);
        }
    }

    /**
     * ROM: {@code lea Pal_MGZEndBoss(pc),a1 / jmp PalLoad_Line1} at the end of
     * loc_6BFCA (sonic3k.asm:142400-142401). S&K-side ROM offset 0x06D97C.
     */
    private void loadBossPalette() {
        if (palettesLoaded) {
            return;
        }
        try {
            byte[] line = services().rom().readBytes(Sonic3kConstants.PAL_MGZ_ENDBOSS_ADDR, 32);
            S3kPaletteWriteSupport.applyLine(
                    services().paletteOwnershipRegistryOrNull(),
                    services().currentLevel(),
                    services().graphicsManager(),
                    S3kPaletteOwners.MGZ_END_BOSS,
                    S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE,
                    MGZ_BOSS_PALETTE_LINE,
                    line);
            palettesLoaded = true;
        } catch (Exception e) {
            LOG.fine(() -> "MgzDrillingRobotnikInstance.loadBossPalette: " + e.getMessage());
        }
    }

    @Override
    protected void onHitTaken(int remainingHits) {
        // No-op: the mini-event never advances on HP change.
    }

    /**
     * ROM: {@code MGZ2_SpecialCheckHit} plays sfx_BossHit, sets status bit 6
     * (hurt → head shows mapping_frame 2), and lets the scripted hang routine
     * detect the hurt state before switching to the ceiling-escape path.
     */
    @Override
    public void onPlayerAttack(PlayableEntity playerEntity, TouchResponseResult result) {
        if (state.invulnerable || state.defeated || waitTimer > 0) {
            return;
        }
        hit = true;
        state.invulnerable = true;
        state.invulnerabilityTimer = INVULNERABILITY_TIME;
        services().playSfx(Sonic3kSfx.BOSS_HIT.id);
    }

    @Override
    protected boolean usesBaseHitHandler() {
        return false;
    }

    @Override
    protected boolean usesDefeatSequencer() {
        return false;
    }

    @Override
    protected int getInvulnerabilityDuration() {
        return INVULNERABILITY_TIME;
    }

    @Override
    protected int getCollisionSizeIndex() {
        return COLLISION_SIZE;
    }

    @Override
    public int getCollisionFlags() {
        if (waitTimer > 0
                || state.routine == ROUTINE_CEILING_ESCAPE
                || state.routine == ROUTINE_ESCAPE_WAIT
                || state.invulnerable) {
            // No collision while waiting to emerge, during the palette-flash
            // i-frames, or once the ship has begun escaping into the ceiling.
            return 0;
        }
        return BODY_COLLISION_FLAGS;
    }

    @Override
    public int getCollisionProperty() {
        return ROM_COLLISION_PROPERTY;
    }

    /** True while Obj_Wait holds Robotnik invisible before the drill drop. */
    public boolean isHidden() {
        return waitTimer > 0;
    }

    @Override
    public int getX() {
        return state.x;
    }

    @Override
    public int getY() {
        return state.y;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isHidden()) {
            return;
        }
        renderTick++;

        PatternSpriteRenderer drillRenderer = getRenderer(Sonic3kObjectArtKeys.MGZ_ENDBOSS);
        PatternSpriteRenderer shipRenderer = getRenderer(Sonic3kObjectArtKeys.ROBOTNIK_SHIP);

        // ROM spawn order: parent drill body → ship child → head child → 4 debris
        // children (ChildObjDat_6D7C0). Higher slot numbers render later in the
        // SST list, which means they end up ON TOP of earlier slots. We mirror
        // that here so the drill-bit children are visible on top of the body.

        // 1) Drill body — frame 0 of Map_MGZEndBoss (includes the silver
        //    cockpit-top and side panels; the ROM's drill "cone" silhouette is
        //    made up of piece 1 (y=-32, 32×24) plus the drill-bit children
        //    overlaid on top of it).
        if (drillRenderer != null) {
            drillRenderer.drawFrameIndex(FRAME_DRILL_POSE, state.x, state.y, flipX, false);
        }

        // 2) Robotnik pod + head (ROM: Child1_MakeRoboShip3 + Child1_MakeRoboHead).
        if (shipRenderer != null) {
            int podOffX = flipX ? -POD_OFFSET_X : POD_OFFSET_X;
            int podX = state.x + podOffX;
            int podY = state.y + POD_OFFSET_Y;
            shipRenderer.drawFrameIndex(currentPodFrame(), podX, podY, flipX, false);

            if (isEscapePodActive()) {
                int flameOffX = flipX ? -SHIP_FLAME_OFFSET_X : SHIP_FLAME_OFFSET_X;
                shipRenderer.drawFrameIndex(SHIP_FLAME_FRAME,
                        podX + flameOffX,
                        podY + SHIP_FLAME_OFFSET_Y,
                        flipX,
                        false,
                        SHIP_FLAME_PALETTE_LINE);
            }

            int headOffX = flipX ? -HEAD_OFFSET_X : HEAD_OFFSET_X;
            int headX = podX + headOffX;
            int headY = podY + HEAD_OFFSET_Y;
            int headFrame = computeHeadFrame();
            shipRenderer.drawFrameIndex(headFrame, headX, headY, flipX, false);
        }

        // 3) Drill-piece children (ChildObjDat_6D7C0) and their visible
        //    sub-children. The second piece carries the drill head assembly
        //    (loc_6C9E8), while the lower thruster housings each have a flame/
        //    drill-tip child (loc_6CF20).
        if (drillRenderer != null) {
            for (int[] spec : DRILL_PIECES) {
                int frame = spec[0];
                int offX = flipX ? -spec[1] : spec[1];
                int offY = spec[2];
                drillRenderer.drawFrameIndex(frame, state.x + offX, state.y + offY, flipX, false);
            }
            int drillHeadOffX = flipX ? -DRILL_HEAD_OFFSET_X : DRILL_HEAD_OFFSET_X;
            drillRenderer.drawFrameIndex(
                    FRAME_DRILL_HEAD,
                    state.x + drillHeadOffX,
                    state.y + DRILL_HEAD_OFFSET_Y,
                    flipX,
                    false);
            if (shouldDrawThrusterFlames()) {
                for (int[] flameSpec : THRUSTER_FLAMES) {
                    int offX = flipX ? -flameSpec[0] : flameSpec[0];
                    drillRenderer.drawFrameIndex(
                            FRAME_THRUSTER_FLAME,
                            state.x + offX,
                            state.y + flameSpec[1],
                            flipX,
                            false,
                            THRUSTER_FLAME_PALETTE_LINE);
                }
            }
        }

        // 4) Falling debris chunks (ChildObjDat_6D7EA) — 10 particles spawned
        //    during drop that arc outward under gravity.
        if (fallingDebrisSpawned) {
            PatternSpriteRenderer debrisRenderer = getRenderer(Sonic3kObjectArtKeys.MGZ_ENDBOSS_DEBRIS);
            if (debrisRenderer != null) {
                for (int i = 0; i < 10; i++) {
                    if (!fallingDebrisAlive[i]) {
                        continue;
                    }
                    int drawX = fallingDebrisX[i] >> 8;
                    int drawY = fallingDebrisY[i] >> 8;
                    debrisRenderer.drawFrameIndex(FALLING_DEBRIS_FRAMES[i], drawX, drawY,
                            fallingDebrisVx[i] < 0, false);
                }
            }
        }
    }

    /**
     * ROM: {@code Obj_RobotnikHeadMain} (sonic3k.asm:136067). Blink between
     * mapping_frame 0 and 1 at the AniRaw_RobotnikHead delay of 5 frames.
     * If the parent's status bit 6 (hurt) is set, use mapping_frame 2.
     */
    private int computeHeadFrame() {
        if (state.invulnerable) {
            return HEAD_FRAME_HURT;
        }
        return ((renderTick / HEAD_ANIM_DELAY) & 1);
    }

    private int currentPodFrame() {
        return isEscapePodActive() ? POD_ESCAPE_FRAME : POD_FRAME;
    }

    private boolean isEscapePodActive() {
        return state.routine == ROUTINE_CEILING_ESCAPE || state.routine == ROUTINE_ESCAPE_WAIT;
    }

    private void updateCustomFlash() {
        if (!state.invulnerable) {
            if (hit) {
                hit = false;
                restoreNormalFlashPalette();
            }
            return;
        }
        applyFlashPalette((state.invulnerabilityTimer & 1) == 0 ? FLASH_COLORS_BRIGHT : FLASH_COLORS_NORMAL);
        state.invulnerabilityTimer--;
        if (state.invulnerabilityTimer > 0) {
            return;
        }
        state.invulnerable = false;
        hit = false;
        restoreNormalFlashPalette();
    }

    private void restoreNormalFlashPalette() {
        applyFlashPalette(FLASH_COLORS_NORMAL);
    }

    /**
     * ROM: loc_6CF62 skips Draw_And_Touch_Sprite when V_int_run_count bit 0 is
     * set, so the lower thruster flames blink every other frame.
     */
    private boolean shouldDrawThrusterFlames() {
        return (renderTick & 1) != 0;
    }

    /**
     * ROM: {@code loc_6C200} reloads {@code Pal_MGZ} to palette line 1 with
     * {@code PalLoad_Line1} when the drill encounter finishes.
     */
    private void restoreMgzPalette() {
        try {
            byte[] line = services().rom().readBytes(Sonic3kConstants.PAL_MGZ_ADDR, 32);
            S3kPaletteWriteSupport.applyLine(
                    services().paletteOwnershipRegistryOrNull(),
                    services().currentLevel(),
                    services().graphicsManager(),
                    S3kPaletteOwners.MGZ_END_BOSS,
                    S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE,
                    MGZ_BOSS_PALETTE_LINE,
                    line);
            S3kPaletteWriteSupport.resolvePendingWritesNow(
                    services().paletteOwnershipRegistryOrNull(),
                    services().currentLevel(),
                    services().graphicsManager());
        } catch (Exception e) {
            LOG.fine(() -> "MgzDrillingRobotnikInstance.restoreMgzPalette: " + e.getMessage());
        }
    }

    private void applyFlashPalette(int[] colors) {
        if (services().currentLevel() == null) {
            return;
        }
        var registry = services().paletteOwnershipRegistryOrNull();
        S3kPaletteWriteSupport.applyColors(
                registry,
                services().currentLevel(),
                services().graphicsManager(),
                S3kPaletteOwners.MGZ_END_BOSS,
                S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE,
                MGZ_BOSS_PALETTE_LINE,
                FLASH_COLOR_INDICES,
                colors);
        S3kPaletteWriteSupport.resolvePendingWritesNow(
                registry,
                services().currentLevel(),
                services().graphicsManager());
    }

    @Override
    public boolean isHighPriority() {
        // ROM: ObjDat_MGZDrillBoss uses make_art_tile(ArtTile_MGZEndBoss,1,0),
        // so the encounter renders behind high-priority FG tiles.
        return false;
    }

    @Override
    public int getPriorityBucket() {
        return PRIORITY_BUCKET;
    }

    @Override
    protected int getBossHitSfxId() {
        return Sonic3kSfx.BOSS_HIT.id;
    }

    @Override
    protected int getBossExplosionSfxId() {
        return Sonic3kSfx.EXPLODE.id;
    }
}
