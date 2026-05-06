package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.AbstractLevelEventManager;
import com.openggf.game.GameModule;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.Sonic3kObjectArtProvider;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.events.S3kMgzEventWriteSupport;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.objects.boss.AbstractBossInstance;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.SwingMotion;

import java.util.List;
import java.util.logging.Level;
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
public class MgzDrillingRobotnikInstance extends AbstractBossInstance {
    private static final Logger LOG = Logger.getLogger(MgzDrillingRobotnikInstance.class.getName());

    /** ROM: Obj_MGZ2DrillingRobotnik $2E(a0) = 2*60 — initial wait frames. */
    private static final int INIT_WAIT_FRAMES = 2 * 60;

    private static final int ROUTINE_INIT = 0;
    private static final int ROUTINE_START_DROP = 2;
    private static final int ROUTINE_DRILL_DROP = 4;
    private static final int ROUTINE_HANG = 6;
    private static final int ROUTINE_CEILING_ESCAPE = 0x16;
    private static final int ROUTINE_ESCAPE_WAIT = 0x18;

    /*
     * End-boss routine mapping:
     * ROUTINE_INIT hidden/setup path Java 0x00 => ROM 0x00/0x02 (loc_6C354, then loc_6C3E6 wait)
     * ROUTINE_END_DESCEND 0x30 => ROM 0x04 loc_6C416
     * ROUTINE_END_SWING 0x32 => ROM 0x06 loc_6C43A
     * ROUTINE_END_ANGLE_SETTLE 0x33 => ROM 0x08 loc_6C45A
     * ROUTINE_END_PRE_FLOOR_DROP 0x35 => ROM 0x0A loc_6C3E6
     * ROUTINE_END_FLOOR_DROP 0x34 => ROM 0x0C loc_6C4B2
     * ROUTINE_END_IMPACT_WAIT 0x37 => ROM 0x0E loc_6C4F2
     * ROUTINE_END_RECOVER 0x36 => ROM 0x10 loc_6C514
     * ROUTINE_END_POST_RECOVER_WAIT 0x3C => ROM 0x12 loc_6C3E6
     * ROUTINE_END_POST_RECOVER_SETTLE 0x3E => ROM 0x14 loc_6C546
     * ROUTINE_END_AIR_WAIT 0x40 => ROM 0x16 loc_6C3E6
     * ROUTINE_END_AIR_RISE 0x42 => ROM 0x18 loc_6C514
     * ROUTINE_END_AIR_APPROACH 0x44 => ROM 0x1A loc_6C5C4
     * ROUTINE_END_AIR_SWEEP 0x46 => ROM 0x1C loc_6C5FE
     * ROUTINE_END_ATTACK_WAIT 0x48 => ROM 0x1E loc_6C4F2
     * ROUTINE_END_ATTACK_MOVE 0x20 => ROM 0x20 loc_6C514
     * ROUTINE_END_DEFEATED 0x3A => Java defeat handoff for kill branch at MGZ2_SpecialCheckHit / loc_6D60A
     * ROUTINE_END_ACTIVE 0x38 => Java-only/test helper; no production assignment found
     */
    private static final int ROUTINE_END_DESCEND = 0x30;
    private static final int ROUTINE_END_SWING = 0x32;
    private static final int ROUTINE_END_ANGLE_SETTLE = 0x33;
    private static final int ROUTINE_END_PRE_FLOOR_DROP = 0x35;
    private static final int ROUTINE_END_FLOOR_DROP = 0x34;
    private static final int ROUTINE_END_IMPACT_WAIT = 0x37;
    private static final int ROUTINE_END_RECOVER = 0x36;
    private static final int ROUTINE_END_ACTIVE = 0x38;
    private static final int ROUTINE_END_POST_RECOVER_WAIT = 0x3C;
    private static final int ROUTINE_END_POST_RECOVER_SETTLE = 0x3E;
    private static final int ROUTINE_END_AIR_WAIT = 0x40;
    private static final int ROUTINE_END_AIR_RISE = 0x42;
    private static final int ROUTINE_END_AIR_APPROACH = 0x44;
    private static final int ROUTINE_END_AIR_SWEEP = 0x46;
    private static final int ROUTINE_END_ATTACK_WAIT = 0x48;
    private static final int ROUTINE_END_ATTACK_MOVE = 0x20;
    private static final int ROUTINE_END_DEFEATED = 0x3A;

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
    // ROM loc_6BFCA later writes x_radius=$30/y_radius=$24 for object range and
    // floor logic, but Draw_And_Touch_Sprite still tests the parent through
    // Touch_Sizes[$0F] at x_pos/y_pos.
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
    /** ROM: loc_6C354 sets y_radius(a0) = $1C for ObjHitFloor_DoRoutine. */
    private static final int END_BOSS_Y_RADIUS = 0x1C;
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
    private static final int[] STATIC_DRILL_PIECE = {1, -0x14, 0x0F};
    /** ROM: byte_6D24A / byte_6D284, indexed by angle(a0) / 2. */
    private static final int[][] ANGLED_DRILL_PIECE_POSES = {
            {2, -0x1C, 0x10},
            {3, -0x0A, 0x18},
            {0x1E, 0x0C, 0x1C},
            {0x1F, 0x18, 0x14},
            {5, 0x2C, 0x08},
            {3, 0x20, -0x0C},
            {4, 0x14, -0x14},
            {2, -0x1C, 0x10},
    };
    /** ROM: byte_6D25A / byte_6D294, drill-tip child of loc_6C948. */
    private static final int[][] DRILL_HEAD_POSES = {
            {9, -0x17, 0},
            {0x0C, -0x11, 0x16},
            {0x20, 0, 0x20},
            {0x14, 0x10, 0x0F},
            {0x13, 0x11, 0},
            {0x12, 0x10, -0x10},
            {0x0F, 0, -0x20},
            {9, -0x17, 0},
    };
    /** ROM: byte_6D2D2, indexed by the parent's $3A child-pose byte. */
    private static final int[][] LOWER_DRILL_PIECE_POSES = {
            {0x06, 0x08, 0x18},
            {0x07, -0x08, 0x14},
            {0x08, 0x18, 0x14},
            {0x18, 0x18, 0x08},
            {0x23, 0x18, -0x04},
    };
    /** ROM: byte_6D2E6, flame/hurt child of each lower drill piece. */
    private static final int[][] THRUSTER_FLAME_POSES = {
            {0x19, 0, 0x10},
            {0x1A, -0x08, 0x08},
            {0x24, 0x08, 0x08},
            {0x1B, 0x10, 0},
            {0x25, 0x09, -0x07},
    };
    /** ROM: byte_6D708, stored through sub_6D6CC for loc_6D710. */
    private static final int[] AIR_ATTACK_PATTERN_SEQUENCE = {0, 8, 4, 0, 0, 4, 0, 8};
    /** ROM: word_6D744, word_6D754, word_6D764, byte_6D76C. */
    private static final int[][] AIR_ATTACK_PATTERNS = {
            {-0x40, 0x70, 0x200, 0, 0, 6},
            {0xA0, 0x120, 0x80, -0x200, 0x0C, 4},
            {0xA0, -0x50, 0x80, 0x200, 4, 8},
            {-0x40, 0x70, 0x200, 0, 0, 6},
    };
    /** word_6D788 collision_flags = $8B (HURT category, size $0B). */
    private static final int DRILL_HEAD_COLLISION_FLAGS = 0x8B;
    /** loc_6CF20 uses word_6D7A0: make_art_tile(ArtTile_MGZEndBoss,0,0). */
    private static final int THRUSTER_FLAME_PALETTE_LINE = 0;
    /** word_6D7A0 collision_flags = $9A (HURT category, size $1A). */
    private static final int THRUSTER_FLAME_COLLISION_FLAGS = 0x9A;
    /** loc_6CF20: bset #4,shield_reaction(a0). */
    private static final int THRUSTER_FLAME_SHIELD_REACTION = 0x10;

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
    private int xVel;
    private int xSubpixel;
    private int ySubpixel;
    private int waitTimer;
    private boolean flipX;
    private boolean artQueued;
    private boolean palettesLoaded;
    private boolean bossMusicPlayed;
    private boolean hit;
    private boolean endBossMode;
    private boolean floorImpactTriggered;
    /** Per-render counter that drives head blink / i-frame flash. */
    private int renderTick;
    private int swingHalfCyclesRemaining;
    private boolean swingDirectionDown;
    private int endBossAngle;
    /** Mirrors the ROM's $3A child-pose byte used by loc_6CEB0/loc_6CF20. */
    private int drillChildPose;
    private int escapeTimer;
    private int airAttackPhase;
    private int airAttackPatternCounter;
    private int airAttackPatternOffset;
    @com.openggf.game.rewind.RewindDeferred(reason = "explosion controller has mutable queued state needing explicit value codec")
    private S3kBossExplosionController endBossDefeatExplosionController;
    private boolean endBossDefeatHandoffComplete;
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
        endBossMode = spawn.objectId() == Sonic3kObjectIds.MGZ_END_BOSS;
        state.x = spawn.x();
        state.y = spawn.y();
        state.xFixed = state.x << 16;
        state.yFixed = state.y << 16;
        state.routine = ROUTINE_INIT;
        state.hitCount = getInitialHitCount();
        yVel = INITIAL_Y_VEL;
        xVel = 0;
        xSubpixel = 0;
        ySubpixel = 0;
        waitTimer = INIT_WAIT_FRAMES;
        artQueued = false;
        palettesLoaded = false;
        bossMusicPlayed = false;
        hit = false;
        floorImpactTriggered = false;
        renderTick = 0;
        swingHalfCyclesRemaining = SWING_HALF_CYCLES;
        swingDirectionDown = false;
        endBossAngle = 0x0C;
        drillChildPose = 0;
        escapeTimer = ESCAPE_TIMER;
        airAttackPhase = 0;
        airAttackPatternCounter = 0;
        airAttackPatternOffset = 0;
        endBossDefeatExplosionController = null;
        endBossDefeatHandoffComplete = false;
        fallingDebrisSpawned = false;
    }

    @Override
    protected int getInitialHitCount() {
        if (endBossMode) {
            return 8;
        }
        // ROM: collision_property = -1; mini-event HP is refreshed on every fatal
        // hit, so Robotnik can be hit many times but never "defeated" by the player.
        return 0xFF;
    }

    @Override
    protected void updateBossLogic(int frameCounter, PlayableEntity playerEntity) {
        queueInitialAssetsIfNeeded();

        // ROM: Obj_MGZ2DrillingRobotnik init queues art + PLC #$6D + palette and
        // sits in Obj_Wait for 120 frames before becoming DrillingRobotnikStart.
        if (state.routine == ROUTINE_INIT && waitTimer > 0) {
            waitTimer--;
            if (waitTimer == 0) {
                playBossMusicOnce();
                if (endBossMode) {
                    state.routine = ROUTINE_END_DESCEND;
                    yVel = 0x80;
                    waitTimer = 0xBF;
                } else {
                    state.routine = ROUTINE_START_DROP;
                }
            }
            updateCustomFlash();
            return;
        }

        if (endBossMode) {
            updateEndBossRoutine(playerEntity);
        } else {
            switch (state.routine) {
                case ROUTINE_START_DROP -> updateStartDrop();
                case ROUTINE_DRILL_DROP -> updateDrillDrop();
                case ROUTINE_HANG -> updateHang();
                case ROUTINE_CEILING_ESCAPE -> updateCeilingEscape();
                case ROUTINE_ESCAPE_WAIT -> updateEscapeWait();
                default -> {
                }
            }
        }

        updateFallingDebris();
        updateCustomFlash();

        state.xFixed = state.x << 16;
        state.yFixed = state.y << 16;
    }

    private void updateEndBossRoutine(PlayableEntity playerEntity) {
        switch (state.routine) {
            case ROUTINE_END_DESCEND -> {
                applyYVelocity();
                if (--waitTimer < 0) {
                    setupSwing();
                    waitTimer = 0x3F;
                    state.routine = ROUTINE_END_SWING;
                }
            }
            case ROUTINE_END_SWING -> {
                SwingMotion.Result swing = SwingMotion.update(SWING_ACCEL, yVel, SWING_MAX_SPEED, swingDirectionDown);
                yVel = swing.velocity();
                swingDirectionDown = swing.directionDown();
                applyYVelocity();
                if (--waitTimer < 0) {
                    waitTimer = 3;
                    state.routine = ROUTINE_END_ANGLE_SETTLE;
                }
            }
            case ROUTINE_END_ANGLE_SETTLE -> {
                SwingMotion.Result swing = SwingMotion.update(SWING_ACCEL, yVel, SWING_MAX_SPEED, swingDirectionDown);
                yVel = swing.velocity();
                swingDirectionDown = swing.directionDown();
                applyYVelocity();
                if (--waitTimer >= 0) {
                    return;
                }
                waitTimer = 3;
                endBossAngle -= 2;
                if (endBossAngle <= 4) {
                    waitTimer = 0x5F;
                    state.routine = ROUTINE_END_PRE_FLOOR_DROP;
                }
            }
            case ROUTINE_END_PRE_FLOOR_DROP -> {
                if (--waitTimer < 0) {
                    yVel = 0x400;
                    state.routine = ROUTINE_END_FLOOR_DROP;
                }
            }
            case ROUTINE_END_FLOOR_DROP -> {
                applyYVelocity();
                TerrainCheckResult floor = ObjectTerrainUtils.checkFloorDist(state.x, state.y, END_BOSS_Y_RADIUS);
                if (floor.hasCollision()) {
                    state.y += floor.distance();
                    ySubpixel = 0;
                    triggerFloorImpact();
                    waitTimer = 0x3F;
                    state.routine = ROUTINE_END_IMPACT_WAIT;
                }
            }
            case ROUTINE_END_IMPACT_WAIT -> {
                if (--waitTimer < 0) {
                    yVel = -0x400;
                    waitTimer = 0x17;
                    state.routine = ROUTINE_END_RECOVER;
                }
            }
            case ROUTINE_END_RECOVER -> {
                applyYVelocity();
                if (--waitTimer < 0) {
                    setupSwing();
                    waitTimer = 0x7F;
                    state.routine = ROUTINE_END_POST_RECOVER_WAIT;
                }
            }
            case ROUTINE_END_ACTIVE -> {
                SwingMotion.Result swing = SwingMotion.update(SWING_ACCEL, yVel, SWING_MAX_SPEED, swingDirectionDown);
                yVel = swing.velocity();
                swingDirectionDown = swing.directionDown();
                applyYVelocity();
            }
            case ROUTINE_END_POST_RECOVER_WAIT -> {
                if (--waitTimer < 0) {
                    waitTimer = 3;
                    state.routine = ROUTINE_END_POST_RECOVER_SETTLE;
                }
            }
            case ROUTINE_END_POST_RECOVER_SETTLE -> {
                SwingMotion.Result swing = SwingMotion.update(SWING_ACCEL, yVel, SWING_MAX_SPEED, swingDirectionDown);
                yVel = swing.velocity();
                swingDirectionDown = swing.directionDown();
                applyYVelocity();
                if (--waitTimer >= 0) {
                    return;
                }
                waitTimer = 3;
                endBossAngle -= 2;
                if (endBossAngle <= 0) {
                    waitTimer = 0x3F;
                    state.routine = ROUTINE_END_AIR_WAIT;
                }
            }
            case ROUTINE_END_AIR_WAIT -> {
                if (--waitTimer < 0) {
                    yVel = -0x400;
                    waitTimer = 0x1F;
                    state.routine = ROUTINE_END_AIR_RISE;
                }
            }
            case ROUTINE_END_AIR_RISE -> {
                applyYVelocity();
                if (--waitTimer < 0) {
                    enterAirApproach();
                }
            }
            case ROUTINE_END_AIR_APPROACH -> {
                applyXVelocity();
                if (playerEntity != null && (playerEntity.getCentreX() & 0xFFFF) >= state.x) {
                    xVel = 0x200;
                    state.routine = ROUTINE_END_AIR_SWEEP;
                } else {
                    SwingMotion.Result swing = SwingMotion.update(SWING_ACCEL, yVel, SWING_MAX_SPEED, swingDirectionDown);
                    yVel = swing.velocity();
                    swingDirectionDown = swing.directionDown();
                    applyYVelocity();
                    applyXVelocity();
                }
            }
            case ROUTINE_END_AIR_SWEEP -> {
                SwingMotion.Result swing = SwingMotion.update(SWING_ACCEL, yVel, SWING_MAX_SPEED, swingDirectionDown);
                yVel = swing.velocity();
                swingDirectionDown = swing.directionDown();
                applyYVelocity();
                applyXVelocity();
                if (state.x >= 0x3E00) {
                    enterAirAttackWait();
                }
            }
            case ROUTINE_END_ATTACK_WAIT -> {
                if (--waitTimer < 0) {
                    advanceAirAttackWait();
                }
            }
            case ROUTINE_END_ATTACK_MOVE -> {
                applyYVelocity();
                applyXVelocity();
                if (--waitTimer < 0) {
                    enterAirAttackWait();
                }
            }
            case ROUTINE_END_DEFEATED -> {
                updateEndBossDefeated();
            }
            default -> {
            }
        }
    }

    /**
     * ROM: final MGZ2 hit switches through Wait_FadeToLevelMusic, then
     * loc_6C2BE/loc_694AA load the floating Egg Capsule path. The Java object
     * retires after spawning the capsule; the capsule owns results and the
     * MGZ-to-CNZ palette fade handoff.
     */
    private void updateEndBossDefeated() {
        if (endBossDefeatExplosionController != null && !endBossDefeatExplosionController.isFinished()) {
            endBossDefeatExplosionController.tick();
            spawnPendingEndBossDefeatExplosions();
        }

        if (waitTimer > 0) {
            waitTimer--;
            return;
        }

        if (endBossDefeatHandoffComplete) {
            return;
        }
        endBossDefeatHandoffComplete = true;
        clearEndBossRuntimeState();
        restoreLevelMusicForCapsule();
        spawnFreeChild(Mgz2PostBossSequenceController::new);
        spawnEndBossDefeatDebris();
        spawnFreeChild(() -> Mgz2EndEggCapsuleInstance.createForCamera(
                services().camera().getX(), services().camera().getY()));
        setDestroyed(true);
    }

    private void spawnEndBossDefeatDebris() {
        for (int i = 0; i < 3; i++) {
            int index = i;
            spawnChild(() -> new MgzEndBossDefeatDebrisChild(state.x, state.y, index, flipX));
        }
    }

    private void spawnPendingEndBossDefeatExplosions() {
        if (endBossDefeatExplosionController == null) {
            return;
        }
        for (var pending : endBossDefeatExplosionController.drainPendingExplosions()) {
            if (pending.playSfx()) {
                services().playSfx(Sonic3kSfx.EXPLODE.id);
            }
            spawnChild(() -> new S3kBossExplosionChild(pending.x(), pending.y()));
        }
    }

    private void clearEndBossRuntimeState() {
        services().gameState().setCurrentBossId(0);
        setGenericBossFlag(false);
    }

    private void restoreLevelMusicForCapsule() {
        try {
            int levelMusic = services().getCurrentLevelMusicId();
            if (levelMusic > 0) {
                services().playMusic(levelMusic);
            }
        } catch (Exception e) {
            LOG.fine(() -> "MgzDrillingRobotnikInstance.restoreLevelMusicForCapsule: " + e.getMessage());
        }
    }

    private void enterAirAttackWait() {
        waitTimer = 0x9F;
        airAttackPhase = 0;
        airAttackPatternOffset = AIR_ATTACK_PATTERN_SEQUENCE[airAttackPatternCounter];
        airAttackPatternCounter = (airAttackPatternCounter + 1) & 7;
        state.routine = ROUTINE_END_ATTACK_WAIT;
    }

    private void advanceAirAttackWait() {
        if (airAttackPhase == 0) {
            waitTimer = 0x1F;
            airAttackPhase = 1;
            configureAirAttackFromCamera();
            return;
        }

        waitTimer = 0xFF;
        airAttackPhase = 0;
        state.routine = ROUTINE_END_ATTACK_MOVE;
    }

    private void configureAirAttackFromCamera() {
        Camera camera = services().camera();
        int cameraX = camera != null ? camera.getX() & 0xFFFF : 0;
        int cameraY = camera != null ? camera.getY() & 0xFFFF : 0;
        int[] pattern = airAttackPattern();
        state.x = (cameraX + pattern[0]) & 0xFFFF;
        state.y = (cameraY + pattern[1]) & 0xFFFF;
        xSubpixel = 0;
        ySubpixel = 0;
        xVel = pattern[2];
        yVel = pattern[3];
        endBossAngle = pattern[4];
        drillChildPose = pattern[5];
    }

    private void enterAirApproach() {
        xSubpixel = 0;
        ySubpixel = 0;
        state.x = 0x3E80;
        state.y = 0x0700;
        xVel = -0x80;
        flipX = true;
        state.routine = ROUTINE_END_AIR_APPROACH;
        drillChildPose = 6;
        setupSwing();
    }

    private void triggerFloorImpact() {
        if (floorImpactTriggered) {
            return;
        }
        floorImpactTriggered = true;
        services().playSfx(Sonic3kSfx.BOSS_HIT_FLOOR.id);
        S3kMgzEventWriteSupport.triggerBossCollapseHandoff(services());
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

    private void applyXVelocity() {
        int fixedX = (state.x << 8) | (xSubpixel & 0xFF);
        fixedX += xVel;
        state.x = fixedX >> 8;
        xSubpixel = fixedX & 0xFF;
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
        services().fadeOutMusic();
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
            s3kProvider.ensureBossExplosionArtLoaded();
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

    private void setGenericBossFlag(boolean active) {
        try {
            GameModule module = services().gameModule();
            if (module != null && module.getLevelEventProvider() instanceof AbstractLevelEventManager manager) {
                manager.setBossActive(active);
            }
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "MgzDrillingRobotnikInstance.setGenericBossFlag failed", e);
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
        if (state.invulnerable || state.defeated || isInitialHiddenWait()) {
            return;
        }
        if (endBossMode) {
            state.hitCount = endBossCanBeKilled()
                    ? Math.max(0, state.hitCount - 1)
                    : Math.max(1, state.hitCount - 1);
            hit = true;
            state.invulnerable = true;
            state.invulnerabilityTimer = INVULNERABILITY_TIME;
            services().playSfx(Sonic3kSfx.BOSS_HIT.id);
            if (state.hitCount == 0 && endBossCanBeKilled()) {
                startEndBossDefeat();
            }
            return;
        }
        hit = true;
        state.invulnerable = true;
        state.invulnerabilityTimer = INVULNERABILITY_TIME;
        services().playSfx(Sonic3kSfx.BOSS_HIT.id);
    }

    private void startEndBossDefeat() {
        state.defeated = true;
        state.routine = ROUTINE_END_DEFEATED;
        waitTimer = 0x3F;
        xVel = 0;
        yVel = 0;
        state.invulnerable = false;
        state.invulnerabilityTimer = 0;
        endBossDefeatHandoffComplete = false;
        endBossDefeatExplosionController = new S3kBossExplosionController(state.x, state.y, 0, services().rng());
        services().playSfx(Sonic3kSfx.EXPLODE.id);
        spawnFreeChild(() -> new S3kBossExplosionChild(state.x, state.y));
        if (services().gameState() != null) {
            services().gameState().addScore(1000);
        }
        services().fadeOutMusic();
    }

    /**
     * ROM: Obj_MGZEndBoss keeps $46 set during the terrain-destruction phase.
     * MGZ2_SpecialCheckHit resets collision_property to 1 instead of killing
     * until loc_6C598 clears $46 for the post-collapse air fight.
     */
    private boolean endBossCanBeKilled() {
        return switch (state.routine) {
            case ROUTINE_END_ACTIVE,
                 ROUTINE_END_AIR_APPROACH,
                 ROUTINE_END_AIR_SWEEP,
                 ROUTINE_END_ATTACK_WAIT,
                 ROUTINE_END_ATTACK_MOVE -> true;
            default -> false;
        };
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
        if (isInitialHiddenWait()
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
    public TouchResponseProvider.TouchRegion[] getMultiTouchRegions() {
        if (isHidden()
                || state.routine == ROUTINE_CEILING_ESCAPE
                || state.routine == ROUTINE_ESCAPE_WAIT
                || state.defeated
                || isDestroyed()) {
            return null;
        }

        DrillPart drillHead = drillHeadPart();
        DrillPart firstFlame = thrusterFlamePart(0);
        DrillPart secondFlame = thrusterFlamePart(1);
        int flameFlags = shouldDrawThrusterFlames() ? THRUSTER_FLAME_COLLISION_FLAGS : 0;
        return new TouchResponseProvider.TouchRegion[] {
                new TouchResponseProvider.TouchRegion(state.x, state.y, getCollisionFlags()),
                new TouchResponseProvider.TouchRegion(
                        state.x + renderOffsetX(drillHead.offX()),
                        state.y + drillHead.offY(),
                        DRILL_HEAD_COLLISION_FLAGS),
                new TouchResponseProvider.TouchRegion(
                        state.x + renderOffsetX(firstFlame.offX()),
                        state.y + firstFlame.offY(),
                        flameFlags,
                        THRUSTER_FLAME_SHIELD_REACTION),
                new TouchResponseProvider.TouchRegion(
                        state.x + renderOffsetX(secondFlame.offX()),
                        state.y + secondFlame.offY(),
                        flameFlags,
                        THRUSTER_FLAME_SHIELD_REACTION),
        };
    }

    @Override
    public int getCollisionProperty() {
        if (endBossMode) {
            return state.hitCount;
        }
        return ROM_COLLISION_PROPERTY;
    }

    /** True while Obj_Wait holds Robotnik invisible before the drill drop. */
    public boolean isHidden() {
        return isInitialHiddenWait();
    }

    private boolean isInitialHiddenWait() {
        return state.routine == ROUTINE_INIT && waitTimer > 0;
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

        // The ROM uses separate child sprites queued by priority. Higher
        // priority words are farther back in the S3K sprite table, so the
        // inlined composite draws $380 pieces first, then $300, then lower
        // front buckets like the ship/head/flames.

        // 1) Back drill piece — word_6D77C priority $380, mapping frame 1.
        if (drillRenderer != null) {
            drawDrillPart(drillRenderer, STATIC_DRILL_PIECE[0], STATIC_DRILL_PIECE[1], STATIC_DRILL_PIECE[2]);
            drawDrillPart(drillRenderer, lowerDrillPart(1));
        }

        // 2) Drill body — ObjDat_MGZDrillBoss priority $300, frame 0 of
        //    Map_MGZEndBoss. The angled child also starts in the $300 bucket.
        if (drillRenderer != null) {
            drillRenderer.drawFrameIndex(currentDrillBodyFrame(), state.x, state.y, flipX, false);
            drawDrillPart(drillRenderer, angledDrillPiece());
            if (shouldDrawThrusterFlames()) {
                drawThrusterFlame(drillRenderer, 1);
            }
        }

        // 3) Robotnik pod + head (ROM: Child1_MakeRoboShip3 + Child1_MakeRoboHead,
        //    ObjDat_RobotnikShip/Head priority $280).
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

        // 4) Front drill-piece children (ChildObjDat_6D7C0) and their visible
        //    sub-children. The second piece carries the drill head assembly
        //    (loc_6C9E8), while the lower thruster housings each have a flame/
        //    drill-tip child (loc_6CF20).
        if (drillRenderer != null) {
            DrillPart drillHead = drillHeadPart();
            drillRenderer.drawFrameIndex(
                    drillHead.frame(),
                    state.x + renderOffsetX(drillHead.offX()),
                    state.y + drillHead.offY(),
                    flipX,
                    false);
            drawDrillPart(drillRenderer, lowerDrillPart(0));
            if (shouldDrawThrusterFlames()) {
                drawThrusterFlame(drillRenderer, 0);
            }
        }

        // 5) Falling debris chunks (ChildObjDat_6D7EA) — 10 particles spawned
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

    private int currentDrillBodyFrame() {
        // ROM loc_6C598 writes #6 to $3A(a0), the SST child-sprite count
        // (mainspr_childsprites), not the parent's mapping_frame. The end-boss
        // parent keeps Map_MGZEndBoss frame 0 while the extra air-phase pieces
        // are rendered as child sprites.
        return FRAME_DRILL_POSE;
    }

    private void drawDrillPart(PatternSpriteRenderer drillRenderer, DrillPart part) {
        drawDrillPart(drillRenderer, part.frame(), part.offX(), part.offY());
    }

    private void drawDrillPart(PatternSpriteRenderer drillRenderer, int frame, int offX, int offY) {
        drillRenderer.drawFrameIndex(frame, state.x + renderOffsetX(offX), state.y + offY, flipX, false);
    }

    private void drawThrusterFlame(PatternSpriteRenderer drillRenderer, int lowerIndex) {
        DrillPart flame = thrusterFlamePart(lowerIndex);
        drillRenderer.drawFrameIndex(
                flame.frame(),
                state.x + renderOffsetX(flame.offX()),
                state.y + flame.offY(),
                flipX,
                false,
                THRUSTER_FLAME_PALETTE_LINE);
    }

    private int renderOffsetX(int offX) {
        return flipX ? -offX : offX;
    }

    private DrillPart angledDrillPiece() {
        return partFromTable(ANGLED_DRILL_PIECE_POSES, endBossAngleIndex());
    }

    private DrillPart drillHeadPart() {
        DrillPart base = angledDrillPiece();
        DrillPart head = partFromTable(DRILL_HEAD_POSES, endBossAngleIndex());
        return new DrillPart(head.frame(), base.offX() + head.offX(), base.offY() + head.offY());
    }

    private DrillPart lowerDrillPart(int index) {
        DrillPart pose = partFromTable(LOWER_DRILL_PIECE_POSES, drillChildPoseIndex());
        int offX = pose.offX();
        if (index == 1) {
            offX -= 0x14;
        }
        return new DrillPart(pose.frame(), offX, pose.offY());
    }

    private DrillPart thrusterFlamePart(int lowerIndex) {
        DrillPart lower = lowerDrillPart(lowerIndex);
        DrillPart flame = partFromTable(THRUSTER_FLAME_POSES, drillChildPoseIndex());
        return new DrillPart(flame.frame(), lower.offX() + flame.offX(), lower.offY() + flame.offY());
    }

    private int endBossAngleIndex() {
        return tableIndex(endBossAngle, ANGLED_DRILL_PIECE_POSES.length);
    }

    private int drillChildPoseIndex() {
        return tableIndex(drillChildPose, LOWER_DRILL_PIECE_POSES.length);
    }

    private int[] airAttackPattern() {
        int index = Math.min((airAttackPatternOffset & 0x0C) / 4, AIR_ATTACK_PATTERNS.length - 1);
        return AIR_ATTACK_PATTERNS[index];
    }

    private static int tableIndex(int value, int length) {
        int index = (value & 0xFE) / 2;
        if (index < 0) {
            return 0;
        }
        return Math.min(index, length - 1);
    }

    private static DrillPart partFromTable(int[][] table, int index) {
        int[] row = table[index];
        return new DrillPart(row[0], row[1], row[2]);
    }

    private record DrillPart(int frame, int offX, int offY) {
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
