package com.openggf.game.sonic2.objects.bosses;

import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.game.sonic2.audio.Sonic2Music;
import com.openggf.game.sonic2.audio.Sonic2Sfx;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.graphics.GLCommand;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.boss.AbstractBossChild;
import com.openggf.level.objects.boss.AbstractBossInstance;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * DEZ Death Egg Robot (Object 0xC7).
 * ROM Reference: s2.asm ObjC7 (Eggrobo)
 *
 * Final boss of Sonic 2. A giant mech with 10 articulated body parts and
 * 3 transient object types. Has 12 HP (not the usual 8). The head is the
 * only hittable part. Palette flash uses line 2.
 *
 * Body State Machine (routine_secondary, 8 sub-states):
 * - 0: Init - spawn 10 children, frame=3, priority=5, position children
 * - 2: WaitEggman - wait for Head's status.misc flag (Eggman boarding)
 * - 4: Countdown - 60 frames after music fade, then play final boss music
 * - 6: Rise - y_vel=-$100, rumbling sound, 121 frames ($79)
 * - 8: WaitReady - 31 frames ($1F), then collision_flags=$16, HP=12, init child collisions
 * - A: SelectAttack - cycle through attack pattern {2,0,2,4} using modulo-4 counter
 * - C: ExecuteAttack - run selected attack type
 * - E: Defeat - fall, bounce at floor Y=$15C, explode, then ending sequence
 *
 * 3 Attack Types (cycle: 2, 0, 2, 4):
 * - Type 0: Walk-and-Punch (forearm launch)
 * - Type 2: Jet-Stomp (fly up, target player, stomp down, screen shake)
 * - Type 4: Stomp-Turn-Bombs (walk toward player, drop 2 bombs)
 */
public class Sonic2DeathEggRobotInstance extends AbstractBossInstance {

    // ========================================================================
    // BODY STATE MACHINE CONSTANTS
    // ========================================================================

    /** Body routine_secondary values */
    private static final int BODY_INIT = 0x00;
    private static final int BODY_WAIT_EGGMAN = 0x02;
    private static final int BODY_COUNTDOWN = 0x04;
    private static final int BODY_RISE = 0x06;
    private static final int BODY_WAIT_READY = 0x08;
    private static final int BODY_SELECT_ATTACK = 0x0A;
    private static final int BODY_EXECUTE_ATTACK = 0x0C;
    private static final int BODY_DEFEAT = 0x0E;

    // ========================================================================
    // TIMING CONSTANTS (from ROM)
    // ========================================================================

    /** ROM: move.b #60,anim_frame_duration(a0) (loc_3D5A8) */
    private static final int COUNTDOWN_TIMER = 60;
    /** ROM: move.b #$79,anim_frame_duration(a0) (loc_3D5C2) */
    private static final int RISE_TIMER = 0x79;
    /** ROM: move.b #$1F,anim_frame_duration(a0) (loc_3D62E) */
    private static final int WAIT_READY_TIMER = 0x1F;
    /** ROM: move.b #$20,anim_frame_duration(a0) (loc_3D640) */
    private static final int ATTACK_SELECT_PAUSE = 0x20;
    /** ROM: move.b #$40,anim_frame_duration(a0) (loc_3D922) */
    private static final int DEFEAT_EXPLODE_TIMER = 0x40;
    /** ROM: move.b #60,objoff_2A(a0) - flash/invuln duration */
    private static final int DEZ_BOSS_INVULN_DURATION = 60; // $3C

    // ========================================================================
    // VELOCITY CONSTANTS (8.8 fixed-point)
    // ========================================================================

    /** ROM: move.w #-$100,y_vel(a0) (loc_3D5C2) */
    private static final int RISE_VELOCITY = -0x100;
    /** ROM: move.w #-$200,y_vel(a0) - jet stomp ascent */
    private static final int JET_ASCENT_VELOCITY = -0x200;
    /** ROM: move.w #$800,y_vel(a0) - jet stomp descent */
    private static final int JET_DESCENT_VELOCITY = 0x800;
    /** ROM: move.w #$800,d2 - forearm punch speed */
    private static final int FOREARM_PUNCH_SPEED = 0x800;

    // ========================================================================
    // COLLISION CONSTANTS
    // ========================================================================

    /** ROM: move.b #$16,collision_flags(a0) - body collision (hurts player) */
    static final int COLLISION_BODY = 0x16;
    /** ROM: move.b #$2A,collision_flags(a1) - head collision (hittable!) */
    static final int COLLISION_HEAD = 0xC0 | 0x2A; // 0xEA — BOSS category for proper bounce
    /** HP = 12 (final boss, NOT the usual 8) */
    private static final int DEATH_EGG_ROBOT_HP = 12;

    /** Per-child collision flags from ObjC7_ChildCollision (s2.asm:83296-83306) */
    static final int[] CHILD_COLLISION = {
            0x00,  // Shoulder
            0x8F,  // FrontLowerLeg
            0x9C,  // FrontForearm
            0x00,  // UpperArm
            0x86,  // FrontThigh
            0x2A,  // Head (hittable!)
            0x8B,  // Jet
            0x8F,  // BackLowerLeg
            0x9C,  // BackForearm
            0x8B   // BackThigh
    };

    // ========================================================================
    // ATTACK PATTERN (from ROM byte_3D680)
    // ========================================================================

    /** ROM: dc.b 2, 0, 2, 4 - attack type cycle */
    static final int[] ATTACK_PATTERN = { 2, 0, 2, 4 };

    // ========================================================================
    // DEFEAT CONSTANTS
    // ========================================================================

    /** ROM: cmpi.w #$15C,d0 - floor level for defeat bounce */
    static final int DEFEAT_FLOOR_Y = 0x15C;
    /** ROM: cmpi.w #$100,d0 - bounce threshold */
    static final int DEFEAT_BOUNCE_THRESHOLD = 0x100;
    /** ROM: move.w #$1000,(Camera_Max_X_pos).w */
    private static final int DEFEAT_CAMERA_MAX_X = 0x1000;
    /** ROM: cmpi.w #$840,(Camera_X_pos).w */
    private static final int DEFEAT_CAMERA_WALK_TARGET = 0x840;
    /** ROM: cmpi.w #$EC0,x_pos(a1) - ending trigger X */
    private static final int ENDING_TRIGGER_X = 0xEC0;

    /** Break-apart velocities for 8 body parts (from ObjC7_BreakSpeeds, s2.asm:83258-83267) */
    static final int[][] BREAK_VELOCITIES = {
            {  0x200, -0x400 },  // Shoulder
            { -0x100, -0x100 },  // FrontLowerLeg
            {  0x300, -0x300 },  // FrontForearm
            { -0x100, -0x400 },  // UpperArm
            {  0x180, -0x200 },  // FrontThigh
            { -0x200, -0x300 },  // BackLowerLeg
            {  0x000, -0x400 },  // BackForearm
            {  0x100, -0x300 }   // BackThigh
    };

    /** ROM: move.b #$40,(DEZ_Shake_Timer).w - stomp screen shake timer */
    private static final int STOMP_SHAKE_TIMER = 0x40;

    // ========================================================================
    // MAPPING FRAME INDICES (from ObjC7_MapUnc_3E5F8)
    // ========================================================================

    private static final int FRAME_BODY = 3;
    private static final int FRAME_SHOULDER = 4;
    private static final int FRAME_ARM = 5;
    private static final int FRAME_FOREARM = 6;
    private static final int FRAME_THIGH = 0x0A;
    private static final int FRAME_LOWER_LEG = 0x0B;
    private static final int FRAME_JET_OFF = 0x0C;
    private static final int FRAME_JET_ON = 0x0D;
    private static final int FRAME_BOMB = 0x0E;
    private static final int FRAME_SENSOR = 0x10;
    private static final int FRAME_LOCK = 0x14;
    private static final int FRAME_HEAD_CLOSED = 0x15;

    // ========================================================================
    // CHILD POSITION DELTAS (from ObjC7_ChildDeltas, s2.asm:83536-83544)
    // ========================================================================

    /** Child deltas: [childIndex]{dx, dy} - only 7 children use these */
    private static final int[][] CHILD_DELTAS = {
            { -4, 60 },   // FrontLowerLeg (objoff_2E)
            { -12, 8 },   // FrontForearm (objoff_30)
            { 12, -8 },   // UpperArm (objoff_32)
            { 4, 36 },    // FrontThigh (objoff_34)
            { -4, 60 },   // BackLowerLeg (objoff_3A)
            { -12, 8 },   // BackForearm (objoff_3C)
            { 4, 36 }     // BackThigh (objoff_3E)
    };

    /** Shoulder position delta from body (loc_3E282 with byte_3DA38) */
    private static final int SHOULDER_DX = 0x0C;
    private static final int SHOULDER_DY = -0x14;

    /** Head position delta from body (loc_3E282 with byte_3DBF2) */
    private static final int HEAD_DX = 0;
    private static final int HEAD_DY = -0x34;

    /** Jet position delta from body (loc_3E282 with byte_3DC70) */
    private static final int JET_DX = 0x38;
    private static final int JET_DY = 0x18;

    /** Bomb spawn delta from body (loc_3E282 with byte_3DF00: dc.w $38, -$14) */
    private static final int BOMB_SPAWN_DX = 0x38;
    private static final int BOMB_SPAWN_DY = -0x14;

    // ========================================================================
    // ROM-ACCURATE GROUP ANIMATION SYSTEM (loc_3E1AA)
    //
    // Each signed byte delta is applied as: (byte << 4) << 8 = byte << 12
    // added to 32-bit position. Net effect: byte / 16 pixels per substep.
    // Substep counter increments each frame; when it reaches the speed
    // threshold, the keyframe advances.
    //
    // Keyframe format: { pieceCount, speed, {objoffId, dx, dy}... }
    //   objoffId: 0 = body, or the child's objoff slot index
    //   dx, dy: signed bytes (Java uses int for clarity)
    //
    // Script format: keyframe table reference + sequence of frame indices.
    //   Frame byte flags: $40 = play sound, $80 = reverse, $C0 = end.
    // ========================================================================

    // --- objoff slot identifiers (matching ROM SST offsets) ---
    private static final int SLOT_BODY = 0;
    private static final int SLOT_FRONT_LOWER_LEG = 0x2E;  // objoff_2E
    private static final int SLOT_FRONT_FOREARM = 0x30;     // objoff_30
    private static final int SLOT_UPPER_ARM = 0x32;         // objoff_32
    private static final int SLOT_FRONT_THIGH = 0x34;       // objoff_34
    private static final int SLOT_BACK_LOWER_LEG = 0x3A;    // objoff_3A
    private static final int SLOT_BACK_FOREARM = 0x3C;      // objoff_3C
    private static final int SLOT_BACK_THIGH = 0x3E;        // objoff_3E

    // --- Keyframe tables (from ROM ObjC7_GroupAni_*) ---
    // Each keyframe: { speed, slot0, dx0, dy0, slot1, dx1, dy1, ... }
    // First element is speed threshold. Then triplets of (slot, dx, dy).

    // ObjC7_GroupAni_3E318: 9 keyframes for half-step walk (off_3E2F6/off_3E300/off_3E30A)
    private static final int[][] HALF_STEP_KEYFRAMES = {
        // 0: byte_3E32A - speed 8
        { 8,  SLOT_BODY,0xE0,0x0C, SLOT_FRONT_FOREARM,0xE0,0x0C, SLOT_UPPER_ARM,0xE0,0x0C, SLOT_BACK_FOREARM,0xE0,0x0C, SLOT_FRONT_THIGH,0xF8,0x04, SLOT_BACK_THIGH,0xF8,0x04 },
        // 1: byte_3E33E - speed 8
        { 8,  SLOT_BODY,0xEC,0x14, SLOT_FRONT_FOREARM,0xEC,0x14, SLOT_UPPER_ARM,0xEC,0x14, SLOT_BACK_FOREARM,0xEC,0x14, SLOT_FRONT_THIGH,0xFA,0x06, SLOT_BACK_THIGH,0xFA,0x06 },
        // 2: byte_3E352 - speed 8
        { 8,  SLOT_BODY,0xF8,0x14, SLOT_FRONT_FOREARM,0xF8,0x14, SLOT_UPPER_ARM,0xF8,0x14, SLOT_BACK_FOREARM,0xF8,0x14, SLOT_FRONT_THIGH,0xFE,0x04, SLOT_BACK_THIGH,0xFE,0x04 },
        // 3: byte_3E366 - speed 8
        { 8,  SLOT_BODY,0xFC,0x0C, SLOT_FRONT_FOREARM,0xFC,0x0C, SLOT_UPPER_ARM,0xFC,0x0C, SLOT_BACK_FOREARM,0xFC,0x0C, SLOT_FRONT_THIGH,0x00,0x02, SLOT_BACK_THIGH,0x00,0x02 },
        // 4: byte_3E37A - speed 8 (body only, standing still)
        { 8,  SLOT_BODY,0x00,0x00 },
        // 5: byte_3E380 - speed 8
        { 8,  SLOT_BODY,0x04,0xE8, SLOT_FRONT_FOREARM,0x04,0xE8, SLOT_UPPER_ARM,0x04,0xE8, SLOT_BACK_FOREARM,0x04,0xE8, SLOT_FRONT_THIGH,0x02,0xFA, SLOT_BACK_THIGH,0x02,0xFA },
        // 6: byte_3E394 - speed 8
        { 8,  SLOT_BODY,0x0C,0xE8, SLOT_FRONT_FOREARM,0x0C,0xE8, SLOT_UPPER_ARM,0x0C,0xE8, SLOT_BACK_FOREARM,0x0C,0xE8, SLOT_FRONT_THIGH,0x04,0xFC, SLOT_BACK_THIGH,0x04,0xFC },
        // 7: byte_3E3A8 - speed 8
        { 8,  SLOT_BODY,0x18,0xF4, SLOT_FRONT_FOREARM,0x18,0xF4, SLOT_UPPER_ARM,0x18,0xF4, SLOT_BACK_FOREARM,0x18,0xF4, SLOT_FRONT_THIGH,0x04,0xFC, SLOT_BACK_THIGH,0x04,0xFC },
        // 8: byte_3E3BC - speed 8
        { 8,  SLOT_BODY,0x18,0xFC, SLOT_FRONT_FOREARM,0x18,0xFC, SLOT_UPPER_ARM,0x18,0xFC, SLOT_BACK_FOREARM,0x18,0xFC, SLOT_FRONT_THIGH,0x06,0xFE, SLOT_BACK_THIGH,0x06,0xFE },
    };

    // ObjC7_GroupAni_3E3D8: 3 keyframes for crouch/rise (off_3E3D0)
    private static final int[][] CROUCH_KEYFRAMES = {
        // 0: byte_3E3DE - speed $10 (crouch down: all children Y +4)
        { 0x10, SLOT_BODY,0x00,0x04, SLOT_FRONT_FOREARM,0x00,0x04, SLOT_UPPER_ARM,0x00,0x04, SLOT_BACK_FOREARM,0x00,0x04, SLOT_FRONT_THIGH,0x00,0x04, SLOT_BACK_THIGH,0x00,0x04 },
        // 1: byte_3E3F2 - speed $10 (hold: body only, no movement)
        { 0x10, SLOT_BODY,0x00,0x00 },
        // 2: byte_3E3F8 - speed 8 (spring up: all children Y -8)
        { 8,    SLOT_BODY,0x00,0xF8, SLOT_FRONT_FOREARM,0x00,0xF8, SLOT_UPPER_ARM,0x00,0xF8, SLOT_BACK_FOREARM,0x00,0xF8, SLOT_FRONT_THIGH,0x00,0xF8, SLOT_BACK_THIGH,0x00,0xF8 },
    };

    // ObjC7_GroupAni_3E438: 12 keyframes for full walk cycle (off_3E40C/off_3E42C)
    private static final int[][] WALK_CYCLE_KEYFRAMES = {
        // 0: byte_3E450 - speed $20
        { 0x20, SLOT_FRONT_THIGH,0xF8,0xF8, SLOT_FRONT_LOWER_LEG,0xF8,0xF8, SLOT_BODY,0x00,0xFC, SLOT_FRONT_FOREARM,0x04,0xFB, SLOT_UPPER_ARM,0x03,0xFB, SLOT_BACK_FOREARM,0xFC,0xFB, SLOT_BACK_THIGH,0x00,0xFE },
        // 1: byte_3E468 - speed $10
        { 0x10, SLOT_FRONT_THIGH,0xF0,0xFC, SLOT_FRONT_LOWER_LEG,0xF0,0xFC, SLOT_BODY,0xF0,0xFC, SLOT_FRONT_FOREARM,0xF4,0xFB, SLOT_UPPER_ARM,0xF3,0xFB, SLOT_BACK_FOREARM,0xEC,0xFB, SLOT_BACK_THIGH,0xF8,0x00 },
        // 2: byte_3E480 - speed $10
        { 0x10, SLOT_FRONT_THIGH,0xF8,0x04, SLOT_FRONT_LOWER_LEG,0xF8,0x04, SLOT_BODY,0xF8,0x04, SLOT_FRONT_FOREARM,0xFC,0x03, SLOT_UPPER_ARM,0xFB,0x03, SLOT_BACK_FOREARM,0xF4,0x03 },
        // 3: byte_3E494 - speed $10
        { 0x10, SLOT_FRONT_THIGH,0xFC,0x10, SLOT_FRONT_LOWER_LEG,0xF8,0x10, SLOT_BODY,0x00,0x08, SLOT_FRONT_FOREARM,0xF8,0x0A, SLOT_UPPER_ARM,0xFA,0x0A, SLOT_BACK_FOREARM,0x08,0x0A, SLOT_BACK_THIGH,0x00,0x08 },
        // 4: byte_3E4AC - speed $20
        { 0x20, SLOT_FRONT_THIGH,0xFE,0xFE, SLOT_BODY,0xF4,0xFC, SLOT_FRONT_FOREARM,0xF0,0xFD, SLOT_UPPER_ARM,0xF1,0xFD, SLOT_BACK_FOREARM,0xF8,0xFD, SLOT_BACK_THIGH,0xEC,0xFA, SLOT_BACK_LOWER_LEG,0xE8,0xFC },
        // 5: byte_3E4C4 - speed $20
        { 0x20, SLOT_BACK_THIGH,0xF8,0xFC, SLOT_BACK_LOWER_LEG,0xF8,0xFC, SLOT_FRONT_FOREARM,0xFC,0xFF, SLOT_UPPER_ARM,0xFD,0xFF, SLOT_BACK_FOREARM,0x04,0xFF },
        // 6: byte_3E4D6 - speed $10
        { 0x10, SLOT_BACK_THIGH,0xF0,0xFC, SLOT_BACK_LOWER_LEG,0xF0,0xFC, SLOT_BODY,0xF0,0xFC, SLOT_FRONT_FOREARM,0xEC,0xFB, SLOT_UPPER_ARM,0xED,0xFB, SLOT_BACK_FOREARM,0xF4,0xFB, SLOT_FRONT_THIGH,0xF8,0x00 },
        // 7: byte_3E4EE - speed $10
        { 0x10, SLOT_BACK_THIGH,0xF8,0x04, SLOT_BACK_LOWER_LEG,0xF8,0x04, SLOT_BODY,0xF8,0x04, SLOT_FRONT_FOREARM,0xF4,0x03, SLOT_UPPER_ARM,0xF5,0x03, SLOT_BACK_FOREARM,0xFC,0x03 },
        // 8: byte_3E502 - speed $10
        { 0x10, SLOT_BACK_THIGH,0xFC,0x10, SLOT_BACK_LOWER_LEG,0xF8,0x10, SLOT_BODY,0x00,0x08, SLOT_FRONT_FOREARM,0x08,0x0A, SLOT_UPPER_ARM,0x06,0x0A, SLOT_BACK_FOREARM,0xF8,0x0A, SLOT_FRONT_THIGH,0x00,0x08 },
        // 9: byte_3E51A - speed $20
        { 0x20, SLOT_BACK_THIGH,0xFE,0xFE, SLOT_BODY,0xF4,0xFC, SLOT_FRONT_FOREARM,0xF8,0xFD, SLOT_UPPER_ARM,0xF7,0xFD, SLOT_BACK_FOREARM,0xF1,0xFD, SLOT_FRONT_THIGH,0xEC,0xFA, SLOT_FRONT_LOWER_LEG,0xE8,0xFC },
        // 10: byte_3E532 - speed $20
        { 0x20, SLOT_FRONT_THIGH,0xF8,0xFC, SLOT_FRONT_LOWER_LEG,0xF8,0xFC, SLOT_FRONT_FOREARM,0x04,0xFF, SLOT_UPPER_ARM,0x03,0xFF, SLOT_BACK_FOREARM,0xFC,0xFF },
        // 11: byte_3E544 - speed $10 (landing: all children Y +8)
        { 0x10, SLOT_BACK_THIGH,0x00,0x08, SLOT_BACK_LOWER_LEG,0x00,0x08, SLOT_BODY,0x00,0x08, SLOT_FRONT_FOREARM,0x00,0x08, SLOT_UPPER_ARM,0x00,0x08, SLOT_BACK_FOREARM,0x00,0x08, SLOT_FRONT_THIGH,0x00,0x08 },
    };

    // --- Group animation script definitions ---
    // Each script entry: positive = keyframe index, negative = flag byte
    // FLAG_SOUND = next byte is sound ID, then next is the real keyframe
    // FLAG_REVERSE = negate all deltas for this keyframe (lower 6 bits = keyframe index)
    // FLAG_END = animation complete
    private static final int FLAG_SOUND = -0x40;    // $40 prefix
    private static final int FLAG_REVERSE = -0x80;  // $80 prefix
    private static final int FLAG_END = -0xC0;      // $C0 prefix / $FF

    // ROM script: off_3E2F6 — half-step walk forward (steps 0-3, end)
    // Keyframe table: HALF_STEP_KEYFRAMES
    private static final int SCRIPT_HALF_WALK_FWD = 0;
    // ROM script: off_3E300 — half-step walk backward (steps 5-8, end)
    private static final int SCRIPT_HALF_WALK_BWD = 1;
    // ROM script: off_3E30A — full walk cycle (steps 0-8, end)
    private static final int SCRIPT_FULL_WALK = 2;
    // ROM script: off_3E3D0 — crouch (steps 0-2, end)
    private static final int SCRIPT_CROUCH = 3;
    // ROM script: off_3E40C — walk cycle with hammer sounds (4 half-cycles)
    private static final int SCRIPT_WALK_ATTACK_FWD = 4;
    // ROM script: off_3E42C — walk backward reversed with hammer
    private static final int SCRIPT_WALK_ATTACK_BWD = 5;

    // Script data: sequences of keyframe indices and flags
    // Positive values = keyframe index; negative values = flags
    private static final int[][] SCRIPT_SEQUENCES = {
        // 0: off_3E2F6 — half-step walk forward
        { 0, 1, 2, 3 },
        // 1: off_3E300 — half-step walk backward
        { 5, 6, 7, 8 },
        // 2: off_3E30A — full walk cycle (used in stomp recovery)
        { 0, 1, 2, 3, 4, 5, 6, 7, 8 },
        // 3: off_3E3D0 — crouch
        { 0, 1, 2 },
        // 4: off_3E40C — walk attack forward (with hammer sounds at key points)
        // ROM: 0, 1, 2, 3, $40, SndID_Hammer, 4, 5, 6, 7, 8, $40, SndID_Hammer, ...
        // We encode sound triggers as negative markers and play them in the engine
        { 0, 1, 2, 3, -1/*sound*/, 4, 5, 6, 7, 8, -1/*sound*/, 9, 10, 1, 2, 3, -1/*sound*/, 4, 5, 6, 7, 8, -1/*sound*/ },
        // 5: off_3E42C — walk attack backward (reversed keyframes + landing)
        // ROM: $88, $87, $86, $85, $B, $40, SndID_Hammer
        // $88 = $80|8 = reversed keyframe 8, etc.
        { -108/*rev8*/, -107/*rev7*/, -106/*rev6*/, -105/*rev5*/, 11, -1/*sound*/ },
    };

    // Keyframe table reference per script
    private static final int[][] SCRIPT_KEYFRAME_TABLE_REF = {
        null, // 0: HALF_STEP_KEYFRAMES
        null, // 1: HALF_STEP_KEYFRAMES
        null, // 2: HALF_STEP_KEYFRAMES
        null, // 3: CROUCH_KEYFRAMES
        null, // 4: WALK_CYCLE_KEYFRAMES
        null, // 5: WALK_CYCLE_KEYFRAMES
    };

    /** Get the keyframe table for a script */
    private static int[][] getKeyframeTable(int scriptId) {
        return switch (scriptId) {
            case SCRIPT_HALF_WALK_FWD, SCRIPT_HALF_WALK_BWD, SCRIPT_FULL_WALK -> HALF_STEP_KEYFRAMES;
            case SCRIPT_CROUCH -> CROUCH_KEYFRAMES;
            case SCRIPT_WALK_ATTACK_FWD, SCRIPT_WALK_ATTACK_BWD -> WALK_CYCLE_KEYFRAMES;
            default -> HALF_STEP_KEYFRAMES;
        };
    }

    // ========================================================================
    // HEAD GLOW ANIMATION (ROM: Ani_objC7_a)
    // Speed 7: frame changes every 8 VBlanks
    // Frames: $15,$15,$15,$15,$15,$15,$15,$15, 0, 1, 2, $FA (loop)
    // ========================================================================
    static final int[] HEAD_GLOW_FRAMES = {
        0x15, 0x15, 0x15, 0x15, 0x15, 0x15, 0x15, 0x15, 0, 1, 2
    };
    static final int HEAD_GLOW_SPEED = 7; // changes every (7+1)=8 frames

    // ========================================================================
    // JET FLAME ANIMATION (ROM: Ani_objC7_b)
    // ========================================================================
    // Anim 0: speed 1, $C, $D, $FF (idle flicker loop)
    static final int[] JET_ANIM_0 = { 0x0C, 0x0D };
    // Anim 1: speed 1, rising pattern (24 frames, $FA loop)
    static final int[] JET_ANIM_1 = {
        0x0C,0x0D,0x0C,0x0C,0x0D,0x0D,0x0C,0x0C,0x0C,0x0D,0x0D,0x0D,
        0x0C,0x0C,0x0C,0x0C,0x0C,0x0D,0x0D,0x0D,0x0D,0x0D,0x0D
    };
    // Anim 2: speed 1, descending pattern (23 frames, $FD -> go to anim 1)
    static final int[] JET_ANIM_2 = {
        0x0D,0x0D,0x0D,0x0D,0x0D,0x0D,0x0C,0x0C,0x0C,0x0C,0x0C,0x0D,
        0x0D,0x0D,0x0C,0x0C,0x0C,0x0D,0x0D,0x0C,0x0C,0x0D,0x0C
    };
    // Anim 3: speed 0, $D, $15, $FF (special: jet + head flicker)
    static final int[] JET_ANIM_3 = { 0x0D, 0x15 };

    // ========================================================================
    // TARGETING SENSOR ANIMATION (ROM: Ani_objC7_c)
    // Speed 3, frames: $13, $12, $11, $10, $16, $FF (loop)
    // ========================================================================
    static final int[] SENSOR_ANIM_FRAMES = { 0x13, 0x12, 0x11, 0x10, 0x16 };
    static final int SENSOR_ANIM_SPEED = 3;

    // ========================================================================
    // INTERNAL STATE
    // ========================================================================

    private int bodyRoutine;
    private int actionTimer;
    private int attackIndex;    // ROM: angle(a0) - cycles through ATTACK_PATTERN
    private int currentAttack;  // Current attack type (0, 2, or 4)
    private int attackPhase;    // ROM: prev_anim(a0) - sub-phase within attack
    private int animPhase;      // ROM: anim(a0) - for defeat sub-dispatch
    private boolean facingLeft;
    private int currentFrame;

    // Flags for forearm punch coordination
    private boolean frontPunchTriggered;
    private boolean backPunchTriggered;

    // Group animation state (ROM: loc_3E1AA)
    private int groupAnimScript = -1;   // Currently playing script ID (-1 = none)
    private int groupAnimFrameIdx;      // Current index within the script sequence
    private int groupAnimSubStep;       // ROM: objoff_1F - sub-step counter within keyframe

    // Subpixel position tracking for body (32-bit fixed: 16.16)
    // Children track their own xFixed/yFixed via ArticulatedChild
    private long bodyXFixed;  // 32-bit position as long to avoid sign issues
    private long bodyYFixed;

    // Targeting sensor data
    private int targetedPlayerX; // ROM: objoff_28(a0) - reported by targeting sensor

    // Defeat sub-state
    private int defeatPhase;    // 0=fall, 2=explode, 4=walk player right
    private boolean controlsLocked;

    // Child references (10 permanent)
    private ArticulatedChild shoulder;
    private ArticulatedChild frontLowerLeg;
    private ForearmChild frontForearm;
    private ArticulatedChild upperArm;
    private ArticulatedChild frontThigh;
    private HeadChild head;
    private JetChild jet;
    private ArticulatedChild backLowerLeg;
    private ForearmChild backForearm;
    private ArticulatedChild backThigh;

    // Targeting sensor (transient child)
    private SensorChild sensorChild;

    public Sonic2DeathEggRobotInstance(ObjectSpawn spawn, LevelManager levelManager) {
        super(spawn, levelManager, "DeathEggRobot");
    }

    // ========================================================================
    // AbstractBossInstance OVERRIDES
    // ========================================================================

    @Override
    protected void initializeBossState() {
        bodyRoutine = BODY_INIT;
        state.routine = 0x02; // Body routine in ObjC7_Index
        currentFrame = FRAME_BODY;
        actionTimer = 0;
        attackIndex = 0;
        currentAttack = 0;
        attackPhase = 0;
        animPhase = 0;
        facingLeft = true; // ROM: Egg Robo faces left toward the approaching player
        defeatPhase = 0;
        controlsLocked = false;
        targetedPlayerX = 0;
        frontPunchTriggered = false;
        backPunchTriggered = false;
        groupAnimScript = -1;
        groupAnimFrameIdx = 0;
        groupAnimSubStep = 0;

        bodyXFixed = (long) state.x << 16;
        bodyYFixed = (long) state.y << 16;

        // Spawn 10 permanent children (ROM: loc_3D52A)
        spawnChildren();

        // Advance to WaitEggman (auto-skip Eggman boarding for now)
        bodyRoutine = BODY_WAIT_EGGMAN;
    }

    private void spawnChildren() {
        shoulder = new ArticulatedChild(this, "Shoulder", 4, FRAME_SHOULDER, 5);
        frontLowerLeg = new ArticulatedChild(this, "FrontLowerLeg", 4, FRAME_LOWER_LEG, 4);
        frontForearm = new ForearmChild(this, "FrontForearm", 4, true);
        upperArm = new ArticulatedChild(this, "UpperArm", 4, FRAME_ARM, 4);
        frontThigh = new ArticulatedChild(this, "FrontThigh", 4, FRAME_THIGH, 4);
        head = new HeadChild(this, 4);
        jet = new JetChild(this, 4);
        backLowerLeg = new ArticulatedChild(this, "BackLowerLeg", 5, FRAME_LOWER_LEG, 4);
        backForearm = new ForearmChild(this, "BackForearm", 5, false);
        backThigh = new ArticulatedChild(this, "BackThigh", 5, FRAME_THIGH, 4);

        childComponents.add(shoulder);
        childComponents.add(frontLowerLeg);
        childComponents.add(frontForearm);
        childComponents.add(upperArm);
        childComponents.add(frontThigh);
        childComponents.add(head);
        childComponents.add(jet);
        childComponents.add(backLowerLeg);
        childComponents.add(backForearm);
        childComponents.add(backThigh);

        // Register children with ObjectManager for rendering (matches Silver Sonic pattern)
        if (levelManager.getObjectManager() != null) {
            for (var child : childComponents) {
                if (child instanceof com.openggf.level.objects.boss.AbstractBossChild bossChild) {
                    levelManager.getObjectManager().addDynamicObject(bossChild);
                }
            }
        }

        positionChildren();
    }

    @Override
    protected int getInitialHitCount() {
        return DEATH_EGG_ROBOT_HP;
    }

    @Override
    protected void onHitTaken(int remainingHits) {
        // ROM: ObjC7_CheckHit - hits are processed on the body, head relays
    }

    @Override
    protected int getCollisionSizeIndex() {
        return COLLISION_BODY & 0x3F;
    }

    @Override
    public int getCollisionFlags() {
        if (bodyRoutine < BODY_WAIT_READY || state.defeated) {
            return 0; // No collision before fight starts or after defeat
        }
        if (state.invulnerable) {
            return 0;
        }
        return COLLISION_BODY;
    }

    @Override
    protected int getInvulnerabilityDuration() {
        return DEZ_BOSS_INVULN_DURATION;
    }

    @Override
    protected int getPaletteLineForFlash() {
        return 2; // DEZ boss flashes palette line 2, not 1
    }

    @Override
    protected boolean usesDefeatSequencer() {
        return false; // Custom defeat logic
    }

    // ========================================================================
    // MAIN UPDATE LOOP
    // ========================================================================

    @Override
    protected void updateBossLogic(int frameCounter, AbstractPlayableSprite player) {
        switch (bodyRoutine) {
            case BODY_INIT -> updateInit();
            case BODY_WAIT_EGGMAN -> updateWaitEggman();
            case BODY_COUNTDOWN -> updateCountdown();
            case BODY_RISE -> updateRise(frameCounter);
            case BODY_WAIT_READY -> updateWaitReady();
            case BODY_SELECT_ATTACK -> updateSelectAttack();
            case BODY_EXECUTE_ATTACK -> updateExecuteAttack(frameCounter, player);
            case BODY_DEFEAT -> updateDefeat(frameCounter, player);
        }
    }

    // ========================================================================
    // BODY STATE IMPLEMENTATIONS
    // ========================================================================

    /** State 0: Init - already handled in initializeBossState */
    private void updateInit() {
        // No-op, handled by initializeBossState
    }

    /** State 2: WaitEggman - wait for head to signal Eggman has boarded */
    private void updateWaitEggman() {
        if (head != null && head.isEggmanBoarded()) {
            bodyRoutine = BODY_COUNTDOWN;
            actionTimer = COUNTDOWN_TIMER;
            AudioManager.getInstance().fadeOutMusic();
        }
    }

    /** State 4: Countdown - 60 frames, then fade music */
    private void updateCountdown() {
        actionTimer--;
        if (actionTimer < 0) {
            bodyRoutine = BODY_RISE;
            actionTimer = RISE_TIMER;
            state.yVel = RISE_VELOCITY;
            // ROM: objoff_38 = jet. Set jet routine 4 to start jet animation.
            if (jet != null) {
                jet.setJetRoutine(4);
            }
            AudioManager.getInstance().playMusic(Sonic2Music.FINAL_BOSS.id);
        }
    }

    /** State 6: Rise - move upward with rumbling sound */
    private void updateRise(int frameCounter) {
        actionTimer--;
        if (actionTimer == 0) {
            bodyRoutine = BODY_WAIT_READY;
            state.yVel = 0;
            actionTimer = WAIT_READY_TIMER;
            state.hitCount = DEATH_EGG_ROBOT_HP;
            initChildCollisions();
            if (jet != null) {
                jet.setJetRoutine(6);
            }
            return;
        }
        // ROM: SndID_Rumbling every frame during rise
        AudioManager.getInstance().playSfx(Sonic2Sfx.RUMBLING.id);
        // ObjectMove (constant velocity, no gravity)
        bodyYFixed += ((long) state.yVel << 8);
        state.y = (int)(bodyYFixed >> 16);
        state.yFixed = (int) bodyYFixed;
        state.updatePositionFromFixed();
        positionChildren();
    }

    /** State 8: WaitReady - brief pause before attacks begin */
    private void updateWaitReady() {
        checkHit();
        actionTimer--;
        if (actionTimer < 0) {
            bodyRoutine = BODY_SELECT_ATTACK;
        }
    }

    /** State A: SelectAttack - pick next attack from pattern {2,0,2,4} */
    private void updateSelectAttack() {
        checkHit();
        bodyRoutine = BODY_EXECUTE_ATTACK;
        actionTimer = ATTACK_SELECT_PAUSE;

        // ROM: angle(a0) incremented, then andi #3
        attackIndex = (attackIndex + 1) & 3;
        currentAttack = ATTACK_PATTERN[attackIndex];
        attackPhase = 0;

        // ROM: for attack type 2 (jet stomp), signal jet
        if (currentAttack == 2 && jet != null) {
            jet.setJetRoutine(4);
            jet.setJetAnim(2);
        }
    }

    /** State C: ExecuteAttack - dispatch to current attack type */
    private void updateExecuteAttack(int frameCounter, AbstractPlayableSprite player) {
        checkHit();
        switch (currentAttack) {
            case 0 -> updateAttackWalkPunch(frameCounter, player);
            case 2 -> updateAttackJetStomp(frameCounter, player);
            case 4 -> updateAttackStompBombs(frameCounter, player);
        }
    }

    // ========================================================================
    // ATTACK TYPE 0: WALK AND PUNCH
    // ROM: loc_3D6AA (anim=0)
    // ========================================================================

    private void updateAttackWalkPunch(int frameCounter, AbstractPlayableSprite player) {
        switch (attackPhase) {
            case 0 -> { // Wait $20 frames
                actionTimer--;
                if (actionTimer < 0) {
                    attackPhase = 2;
                    resetGroupAnim();
                }
            }
            case 2 -> { // Walk forward (ROM: off_3E40C walk cycle with hammer sounds)
                if (stepGroupAnimation(SCRIPT_WALK_ATTACK_FWD)) {
                    attackPhase = 4;
                    actionTimer = 0x40;
                }
            }
            case 4 -> { // Pause $40 frames
                actionTimer--;
                if (actionTimer < 0) {
                    attackPhase = 6;
                    resetGroupAnim();
                }
            }
            case 6 -> { // Walk backward (ROM: off_3E42C reversed walk cycle)
                if (stepGroupAnimation(SCRIPT_WALK_ATTACK_BWD)) {
                    bodyRoutine = BODY_SELECT_ATTACK;
                    actionTimer = 0x40;
                }
            }
        }
    }

    // ========================================================================
    // ATTACK TYPE 2: JET STOMP
    // ROM: loc_3D702 (anim=2)
    // ========================================================================

    private void updateAttackJetStomp(int frameCounter, AbstractPlayableSprite player) {
        switch (attackPhase) {
            case 0 -> { // Wait
                actionTimer--;
                if (actionTimer < 0) {
                    attackPhase = 2;
                    resetGroupAnim();
                }
            }
            case 2 -> { // Crouch (ROM: off_3E3D0)
                if (stepGroupAnimation(SCRIPT_CROUCH)) {
                    attackPhase = 4;
                    actionTimer = 0x80;
                    state.xVel = 0;
                    state.yVel = JET_ASCENT_VELOCITY;
                }
            }
            case 4 -> { // Fly up for $80 frames
                actionTimer--;
                if (actionTimer < 0) {
                    attackPhase = 6;
                    state.yVel = 0;
                    // Spawn targeting sensor
                    targetedPlayerX = 0;
                    spawnSensor(player);
                    return;
                }
                // Fire sound every 32 frames
                if ((frameCounter & 0x1F) == 0) {
                    AudioManager.getInstance().playSfx(Sonic2Sfx.FIRE.id);
                }
                // ObjectMove
                bodyYFixed += ((long) state.yVel << 8);
                state.y = (int)(bodyYFixed >> 16);
                state.yFixed = (int) bodyYFixed;
                state.updatePositionFromFixed();
                positionChildren();
            }
            case 6 -> { // Wait for sensor to report player X
                if (sensorChild != null) {
                    sensorChild.update(frameCounter, player);
                }
                if (targetedPlayerX != 0) {
                    attackPhase = 8;
                    state.x = targetedPlayerX;
                    bodyXFixed = (long) state.x << 16;
                    state.xFixed = (int) bodyXFixed;
                    // Set facing based on position
                    facingLeft = targetedPlayerX < 0x780;
                    updateChildFacing();
                    state.yVel = JET_DESCENT_VELOCITY;
                    actionTimer = 0x20;
                }
            }
            case 8 -> { // Descend for $20 frames
                actionTimer--;
                if (actionTimer < 0) {
                    attackPhase = 0x0A;
                    state.yVel = 0;
                    // Screen shake
                    Camera camera = Camera.getInstance();
                    if (camera != null) {
                        camera.setShakeOffsets(0, 4);
                    }
                    if (jet != null) {
                        jet.setJetRoutine(6);
                    }
                    AudioManager.getInstance().playSfx(Sonic2Sfx.SMASH.id);
                    resetGroupAnim();
                    return;
                }
                bodyYFixed += ((long) state.yVel << 8);
                state.y = (int)(bodyYFixed >> 16);
                state.yFixed = (int) bodyYFixed;
                state.updatePositionFromFixed();
                positionChildren();
            }
            case 0x0A -> { // Stand up (ROM: off_3E30A full walk cycle)
                if (stepGroupAnimation(SCRIPT_FULL_WALK)) {
                    positionChildren();
                    boolean playerOnSameSide = isPlayerOnFacingSide(player);
                    if (!playerOnSameSide) {
                        bodyRoutine = BODY_SELECT_ATTACK;
                    } else {
                        attackPhase = 0x0C;
                        actionTimer = 0x60;
                        spawnBombs(player);
                    }
                }
            }
            case 0x0C -> { // Wait after bombs
                actionTimer--;
                if (actionTimer < 0) {
                    bodyRoutine = BODY_SELECT_ATTACK;
                }
            }
        }
    }

    // ========================================================================
    // ATTACK TYPE 4: STOMP TURN BOMBS
    // ROM: loc_3D83C (anim=4)
    // ========================================================================

    private void updateAttackStompBombs(int frameCounter, AbstractPlayableSprite player) {
        switch (attackPhase) {
            case 0 -> { // Wait
                actionTimer--;
                if (actionTimer < 0) {
                    attackPhase = 2;
                    resetGroupAnim();
                    backPunchTriggered = false;
                }
            }
            case 2 -> { // Walk toward player (ROM: off_3E2F6 half-step forward)
                if (stepGroupAnimation(SCRIPT_HALF_WALK_FWD)) {
                    boolean playerOnSameSide = isPlayerOnFacingSide(player);
                    if (playerOnSameSide) {
                        attackPhase = 8;
                        actionTimer = 0x20;
                        spawnBombs(player);
                    } else {
                        attackPhase = 4;
                        actionTimer = 0x40;
                        frontPunchTriggered = true;
                    }
                }
            }
            case 4 -> { // Pause with punch active
                actionTimer--;
                if (actionTimer < 0) {
                    attackPhase = 6;
                    backPunchTriggered = true;
                    actionTimer = 0x40;
                    resetGroupAnim();
                }
            }
            case 6 -> { // Walk backward (ROM: off_3E300 half-step backward)
                if (stepGroupAnimation(SCRIPT_HALF_WALK_BWD)) {
                    bodyRoutine = BODY_SELECT_ATTACK;
                }
            }
            case 8 -> { // Wait after bombs
                actionTimer--;
                if (actionTimer < 0) {
                    bodyRoutine = BODY_SELECT_ATTACK;
                }
            }
        }
    }

    // ========================================================================
    // DEFEAT SEQUENCE (State E)
    // ========================================================================

    private void updateDefeat(int frameCounter, AbstractPlayableSprite player) {
        switch (defeatPhase) {
            case 0 -> updateDefeatFall(frameCounter);
            case 2 -> updateDefeatExplode(frameCounter);
            case 4 -> updateDefeatWalkPlayer(frameCounter, player);
            case 6 -> {} // Terminal: ending triggered, waiting for game mode change
        }
    }

    /** Defeat phase 0: Fall with gravity, bounce at floor Y=$15C */
    private void updateDefeatFall(int frameCounter) {
        spawnDefeatExplosion();
        // ObjectMoveAndFall
        bodyYFixed += ((long) state.yVel << 8);
        state.yVel += GRAVITY;
        state.y = (int)(bodyYFixed >> 16);
        state.yFixed = (int) bodyYFixed;
        state.updatePositionFromFixed();

        if (state.y >= DEFEAT_FLOOR_Y) {
            state.y = DEFEAT_FLOOR_Y;
            bodyYFixed = (long) state.y << 16;
            state.yFixed = (int) bodyYFixed;
            int absVel = state.yVel;
            if (absVel < 0) {
                defeatPhase = 2;
                actionTimer = DEFEAT_EXPLODE_TIMER;
                return;
            }
            int bounceVel = absVel >> 2;
            if (bounceVel >= DEFEAT_BOUNCE_THRESHOLD) {
                state.yVel = -bounceVel;
            } else {
                defeatPhase = 2;
                actionTimer = DEFEAT_EXPLODE_TIMER;
            }
        }
    }

    /** Defeat phase 2: 64 frames of explosions, then lock controls */
    private void updateDefeatExplode(int frameCounter) {
        actionTimer--;
        if (actionTimer >= 0) {
            spawnDefeatExplosion();
        } else {
            defeatPhase = 4;
            controlsLocked = true;
            Camera camera = Camera.getInstance();
            if (camera != null) {
                camera.setMaxX((short) DEFEAT_CAMERA_MAX_X);
            }
        }
    }

    /** Defeat phase 4: Force player right, trigger ending when camera reaches $840 */
    private void updateDefeatWalkPlayer(int frameCounter, AbstractPlayableSprite player) {
        Camera camera = Camera.getInstance();
        if (camera != null && camera.getX() >= DEFEAT_CAMERA_WALK_TARGET) {
            camera.setShakeOffsets(0, 4);
            if (head != null) {
                head.setDestroyed(true);
            }
            AudioManager.getInstance().fadeOutMusic();
            defeatPhase = 6;
        }
    }

    // ========================================================================
    // HIT DETECTION (custom - head is only hittable part)
    // ========================================================================

    private void checkHit() {
        if (state.hitCount <= 0 && !state.defeated) {
            triggerDefeatSequence();
        }
    }

    void onHeadHit() {
        if (state.invulnerable || state.defeated) {
            return;
        }
        state.hitCount--;
        state.invulnerabilityTimer = DEZ_BOSS_INVULN_DURATION;
        state.invulnerable = true;
        AudioManager.getInstance().playSfx(Sonic2Sfx.BOSS_HIT.id);
        paletteFlasher.startFlash();

        if (state.hitCount <= 0) {
            triggerDefeatSequence();
        }
    }

    private void triggerDefeatSequence() {
        if (state.defeated) return;
        state.defeated = true;
        GameServices.gameState().addScore(1000);
        bodyRoutine = BODY_DEFEAT;
        defeatPhase = 0;
        animPhase = 0;
        state.xVel = 0;
        state.yVel = 0;
        removeAllCollision();
        breakApart();
        if (jet != null) {
            jet.setDestroyed(true);
        }
    }

    // ========================================================================
    // ROM-ACCURATE GROUP ANIMATION ENGINE (loc_3E1AA)
    // ========================================================================

    /** Reset group animation state for a new script */
    private void resetGroupAnim() {
        groupAnimScript = -1;
        groupAnimFrameIdx = 0;
        groupAnimSubStep = 0;
    }

    /**
     * Step through a group animation script. Returns true when the script completes.
     *
     * ROM: loc_3E1AA — reads per-keyframe delta tables and applies them
     * to body + children using signed byte deltas scaled to 1/16 pixel per substep.
     */
    private boolean stepGroupAnimation(int scriptId) {
        // Detect new animation start
        if (groupAnimScript != scriptId) {
            groupAnimScript = scriptId;
            groupAnimFrameIdx = 0;
            groupAnimSubStep = 0;
        }

        int[] sequence = SCRIPT_SEQUENCES[scriptId];
        int[][] keyframes = getKeyframeTable(scriptId);

        // Skip sound markers (encoded as -1 in the sequence)
        while (groupAnimFrameIdx < sequence.length && sequence[groupAnimFrameIdx] == -1) {
            // Play hammer sound
            AudioManager.getInstance().playSfx(Sonic2Sfx.HAMMER.id);
            groupAnimFrameIdx++;
        }

        // Check for end of sequence
        if (groupAnimFrameIdx >= sequence.length) {
            resetGroupAnim();
            return true;
        }

        int entry = sequence[groupAnimFrameIdx];

        // Decode reversed keyframes (encoded as negative values <= -100)
        boolean reversed = false;
        int keyframeIdx;
        if (entry <= -100) {
            reversed = true;
            keyframeIdx = -(entry + 100); // -108 -> keyframe 8, -107 -> 7, etc.
        } else {
            keyframeIdx = entry;
        }

        // Look up keyframe data
        int[] kf = keyframes[keyframeIdx];
        int speed = kf[0];

        // Apply deltas to body and children
        applyKeyframeDeltas(kf, reversed);

        // Advance substep counter
        groupAnimSubStep++;
        if (groupAnimSubStep >= speed) {
            groupAnimSubStep = 0;
            groupAnimFrameIdx++;

            // Skip sound markers after advancing
            while (groupAnimFrameIdx < sequence.length && sequence[groupAnimFrameIdx] == -1) {
                AudioManager.getInstance().playSfx(Sonic2Sfx.HAMMER.id);
                groupAnimFrameIdx++;
            }

            if (groupAnimFrameIdx >= sequence.length) {
                resetGroupAnim();
                return true;
            }
        }

        // Update body pixel position from fixed-point
        state.x = (int)(bodyXFixed >> 16);
        state.y = (int)(bodyYFixed >> 16);
        state.xFixed = (int) bodyXFixed;
        state.yFixed = (int) bodyYFixed;

        // Update children pixel positions from their fixed-point
        syncChildPixelPositions();

        // Position children that aren't in the keyframe (shoulder, head, jet)
        positionNonAnimatedChildren();

        return false;
    }

    /**
     * Apply ROM-accurate keyframe deltas to body and children.
     * ROM math: signedByte -> ext.w -> asl.w #4 -> ext.l -> asl.l #8 -> add.l to pos
     * Net effect: delta_subpixels = signedByte << 12
     */
    private void applyKeyframeDeltas(int[] kf, boolean reversed) {
        // kf[0] = speed; then triplets of (slot, dx, dy) starting at index 1
        for (int i = 1; i < kf.length; i += 3) {
            int slot = kf[i];
            int dx = (byte) kf[i + 1]; // sign-extend to byte range
            int dy = (byte) kf[i + 2]; // sign-extend to byte range

            // ROM: asl.w #4, asl.l #8 = total shift left 12
            long dxFixed = (long) dx << 12;
            long dyFixed = (long) dy << 12;

            // ROM: if x-flipped, negate X delta
            if (facingLeft) {
                dxFixed = -dxFixed;
            }

            // ROM: if reversed ($80 prefix), negate both
            if (reversed) {
                dxFixed = -dxFixed;
                dyFixed = -dyFixed;
            }

            // Apply to the appropriate object
            if (slot == SLOT_BODY) {
                bodyXFixed += dxFixed;
                bodyYFixed += dyFixed;
            } else {
                ArticulatedChild child = resolveChildBySlot(slot);
                if (child != null) {
                    child.xFixed += dxFixed;
                    child.yFixed += dyFixed;
                }
            }
        }
    }

    /** Resolve a child by its ROM objoff slot identifier */
    private ArticulatedChild resolveChildBySlot(int slot) {
        return switch (slot) {
            case SLOT_FRONT_LOWER_LEG -> frontLowerLeg;
            case SLOT_FRONT_FOREARM -> frontForearm;
            case SLOT_UPPER_ARM -> upperArm;
            case SLOT_FRONT_THIGH -> frontThigh;
            case SLOT_BACK_LOWER_LEG -> backLowerLeg;
            case SLOT_BACK_FOREARM -> backForearm;
            case SLOT_BACK_THIGH -> backThigh;
            default -> null;
        };
    }

    /** Update children's pixel positions from their fixed-point tracking */
    private void syncChildPixelPositions() {
        if (frontLowerLeg != null) frontLowerLeg.syncFromFixed();
        if (frontForearm != null && !frontForearm.isPunching()) frontForearm.syncFromFixed();
        if (upperArm != null) upperArm.syncFromFixed();
        if (frontThigh != null) frontThigh.syncFromFixed();
        if (backLowerLeg != null) backLowerLeg.syncFromFixed();
        if (backForearm != null && !backForearm.isPunching()) backForearm.syncFromFixed();
        if (backThigh != null) backThigh.syncFromFixed();
    }

    // ========================================================================
    // CHILD POSITIONING
    // ========================================================================

    /** Position all children relative to body (ROM: ObjC7_PositionChildren) */
    private void positionChildren() {
        int flipSign = facingLeft ? -1 : 1;

        // 7 children positioned via ObjC7_ChildDeltas
        if (frontLowerLeg != null) {
            frontLowerLeg.setPositionAndSnap(state.x + (CHILD_DELTAS[0][0] * flipSign), state.y + CHILD_DELTAS[0][1]);
        }
        if (frontForearm != null && !frontForearm.isPunching()) {
            frontForearm.setPositionAndSnap(state.x + (CHILD_DELTAS[1][0] * flipSign), state.y + CHILD_DELTAS[1][1]);
        }
        if (upperArm != null) {
            upperArm.setPositionAndSnap(state.x + (CHILD_DELTAS[2][0] * flipSign), state.y + CHILD_DELTAS[2][1]);
        }
        if (frontThigh != null) {
            frontThigh.setPositionAndSnap(state.x + (CHILD_DELTAS[3][0] * flipSign), state.y + CHILD_DELTAS[3][1]);
        }
        if (backLowerLeg != null) {
            backLowerLeg.setPositionAndSnap(state.x + (CHILD_DELTAS[4][0] * flipSign), state.y + CHILD_DELTAS[4][1]);
        }
        if (backForearm != null && !backForearm.isPunching()) {
            backForearm.setPositionAndSnap(state.x + (CHILD_DELTAS[5][0] * flipSign), state.y + CHILD_DELTAS[5][1]);
        }
        if (backThigh != null) {
            backThigh.setPositionAndSnap(state.x + (CHILD_DELTAS[6][0] * flipSign), state.y + CHILD_DELTAS[6][1]);
        }

        positionNonAnimatedChildren();
    }

    /** Position children that use loc_3E282 (relative offset, NOT group animation) */
    private void positionNonAnimatedChildren() {
        int flipSign = facingLeft ? -1 : 1;

        if (shoulder != null) {
            shoulder.setPosition(state.x + (SHOULDER_DX * flipSign), state.y + SHOULDER_DY);
        }
        if (head != null) {
            head.setPosition(state.x + (HEAD_DX * flipSign), state.y + HEAD_DY);
        }
        if (jet != null) {
            jet.setPosition(state.x + (JET_DX * flipSign), state.y + JET_DY);
        }
    }

    /** Initialize child collision flags (ROM: ObjC7_InitCollision, s2.asm:83282) */
    private void initChildCollisions() {
        for (int i = 0; i < childComponents.size() && i < CHILD_COLLISION.length; i++) {
            if (childComponents.get(i) instanceof AbstractBossChild child) {
                child.setCollisionFlags(CHILD_COLLISION[i]);
            }
        }
    }

    /** Remove all child collision (ROM: ObjC7_RemoveCollision, s2.asm:83324) */
    private void removeAllCollision() {
        for (var component : childComponents) {
            if (component instanceof AbstractBossChild child) {
                child.setCollisionFlags(0);
            }
        }
    }

    /** Break apart body pieces with per-part velocities (ROM: ObjC7_Break, s2.asm:83241) */
    private void breakApart() {
        AbstractBossChild[] breakParts = {
                shoulder, frontLowerLeg, frontForearm, upperArm,
                frontThigh, backLowerLeg, backForearm, backThigh
        };
        for (int i = 0; i < breakParts.length && i < BREAK_VELOCITIES.length; i++) {
            if (breakParts[i] instanceof ArticulatedChild ac) {
                ac.startFalling(BREAK_VELOCITIES[i][0], BREAK_VELOCITIES[i][1]);
            } else if (breakParts[i] instanceof ForearmChild fc) {
                fc.startFalling(BREAK_VELOCITIES[i][0], BREAK_VELOCITIES[i][1]);
            }
        }
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    private boolean isPlayerOnFacingSide(AbstractPlayableSprite player) {
        if (player == null) return false;
        int playerX = player.getCentreX();
        if (facingLeft) {
            return playerX < state.x;
        } else {
            return playerX > state.x;
        }
    }

    private void updateChildFacing() {
        for (var component : childComponents) {
            if (component instanceof AbstractBossChild child && !child.isDestroyed()) {
                child.setFlipX(facingLeft);
            }
        }
    }

    private void spawnBombs(AbstractPlayableSprite player) {
        ObjectRenderManager renderManager = levelManager.getObjectRenderManager();
        if (renderManager == null || levelManager.getObjectManager() == null) return;

        int xSign = facingLeft ? -1 : 1;
        int spawnX = state.x + (BOMB_SPAWN_DX * xSign);
        int spawnY = state.y + BOMB_SPAWN_DY;

        BombChild bomb1 = new BombChild(this, spawnX, spawnY, 0x60 * xSign, -0x800);
        childComponents.add(bomb1);

        BombChild bomb2 = new BombChild(this, spawnX, spawnY, 0xC0 * xSign, -0xA00);
        childComponents.add(bomb2);
    }

    /** Spawn the targeting sensor child */
    private void spawnSensor(AbstractPlayableSprite player) {
        if (player == null) return;
        sensorChild = new SensorChild(this, player.getCentreX(), player.getCentreY());
        // Register with ObjectManager for rendering if available
        if (levelManager.getObjectManager() != null) {
            levelManager.getObjectManager().addDynamicObject(sensorChild);
        }
    }

    /** Report player X from targeting sensor */
    void reportTargetedPlayerX(int x) {
        this.targetedPlayerX = x;
    }

    // ========================================================================
    // ACCESSORS (for tests and children)
    // ========================================================================

    public int getBodyRoutine() { return bodyRoutine; }
    public int getCurrentFrame() { return currentFrame; }
    public int getAttackIndex() { return attackIndex; }
    public int getCurrentAttack() { return currentAttack; }
    public int getDefeatPhase() { return defeatPhase; }
    public boolean isFacingLeft() { return facingLeft; }
    public HeadChild getHead() { return head; }
    public int getActionTimer() { return actionTimer; }

    boolean consumeFrontPunchTrigger() {
        boolean val = frontPunchTriggered;
        frontPunchTriggered = false;
        return val;
    }

    boolean consumeBackPunchTrigger() {
        boolean val = backPunchTriggered;
        backPunchTriggered = false;
        return val;
    }

    // ========================================================================
    // RENDERING
    // ========================================================================

    @Override
    public int getPriorityBucket() {
        return 5;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = levelManager.getObjectRenderManager();
        if (renderManager == null) return;

        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.DEZ_BOSS);
        if (renderer == null || !renderer.isReady()) return;

        renderer.drawFrameIndex(currentFrame, state.x, state.y, facingLeft, false);
    }

    // ========================================================================
    // INNER CLASS: ArticulatedChild - Body part with subpixel position tracking
    // ========================================================================

    static class ArticulatedChild extends AbstractBossChild {
        int frame;
        long xFixed;  // 32-bit subpixel position (as long for Java sign safety)
        long yFixed;
        boolean falling;  // package-private for ForearmChild access
        private int xVel;
        private int yVel;
        private int fallTimer;

        ArticulatedChild(Sonic2DeathEggRobotInstance parent, String name,
                         int priority, int frame, int initialPriority) {
            super(parent, name, initialPriority, Sonic2ObjectIds.DEATH_EGG_ROBOT);
            this.frame = frame;
            this.priority = priority;
            this.falling = false;
            this.xVel = 0;
            this.yVel = 0;
            this.fallTimer = 0x80;
            this.xFixed = (long) currentX << 16;
            this.yFixed = (long) currentY << 16;
        }

        void startFalling(int xVel, int yVel) {
            this.falling = true;
            this.xVel = xVel;
            this.yVel = yVel;
            this.fallTimer = 0x80;
        }

        @Override
        public void update(int frameCounter, AbstractPlayableSprite player) {
            if (!beginUpdate(frameCounter)) return;
            if (falling) {
                fallTimer--;
                if (fallTimer < 0) {
                    setDestroyed(true);
                    return;
                }
                currentX += (xVel >> 8);
                currentY += (yVel >> 8);
                yVel += GRAVITY;
            }
            updateDynamicSpawn();
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            ObjectRenderManager renderManager = ((Sonic2DeathEggRobotInstance) parent).levelManager
                    .getObjectRenderManager();
            if (renderManager == null) return;
            PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.DEZ_BOSS);
            if (renderer == null || !renderer.isReady()) return;
            boolean flip = ((Sonic2DeathEggRobotInstance) parent).facingLeft;
            renderer.drawFrameIndex(frame, currentX, currentY, flip, false);
        }

        /** Sync pixel position from fixed-point (called by parent after group animation) */
        void syncFromFixed() {
            setPosition((int)(xFixed >> 16), (int)(yFixed >> 16));
        }

        /** Set pixel position and snap fixed-point to match (called by positionChildren) */
        void setPositionAndSnap(int x, int y) {
            setPosition(x, y);
            xFixed = (long) x << 16;
            yFixed = (long) y << 16;
        }

        public int getCollisionFlags() {
            return collisionFlags;
        }
    }

    // ========================================================================
    // INNER CLASS: ForearmChild - Forearm with punch mechanics + subpixel tracking
    // ========================================================================

    static class ForearmChild extends ArticulatedChild {
        private final boolean isFront;
        private boolean punching;
        private int punchPhase;
        private int punchTimer;
        private int punchXVel;
        private int punchYVel;
        private int savedY;

        ForearmChild(Sonic2DeathEggRobotInstance parent, String name,
                     int priority, boolean isFront) {
            super(parent, name, priority, FRAME_FOREARM, priority);
            this.isFront = isFront;
            this.punching = false;
            this.punchPhase = 0;
            this.punchTimer = 0;
            this.punchXVel = 0;
            this.punchYVel = 0;
            this.savedY = 0;
        }

        boolean isPunching() {
            return punching;
        }

        @Override
        void startFalling(int xVel, int yVel) {
            super.startFalling(xVel, yVel);
            this.punching = false;
        }

        @Override
        public void update(int frameCounter, AbstractPlayableSprite player) {
            if (!beginUpdate(frameCounter)) return;

            if (falling) {
                super.update(frameCounter, player);
                return;
            }

            Sonic2DeathEggRobotInstance boss = (Sonic2DeathEggRobotInstance) parent;

            // Check if punch was triggered
            if (!punching) {
                boolean triggered = isFront ? boss.consumeFrontPunchTrigger()
                        : boss.consumeBackPunchTrigger();
                if (triggered) {
                    punching = true;
                    punchPhase = 0;
                    punchTimer = 0x10;
                    savedY = currentY;
                    punchYVel = 0;
                }
            }

            if (punching) {
                updatePunch(player);
            }
            updateDynamicSpawn();
        }

        private void updatePunch(AbstractPlayableSprite player) {
            Sonic2DeathEggRobotInstance boss = (Sonic2DeathEggRobotInstance) parent;
            switch (punchPhase) {
                case 0 -> { // Wind up
                    punchTimer--;
                    if (punchTimer < 0) {
                        punchPhase = 2;
                        punchTimer = 0x20;
                        int dy = 0;
                        if (player != null) {
                            dy = Math.abs(player.getCentreY() - currentY);
                        }
                        int yVelIdx = Math.min(3, (dy & 0xC0) >> 5);
                        int[] Y_VEL_TABLE = { 0x200, 0x100, 0x80, 0 };
                        punchYVel = Y_VEL_TABLE[yVelIdx];
                        if (player != null && player.getCentreY() < currentY) {
                            punchYVel = -punchYVel;
                        }
                        punchXVel = boss.facingLeft ? -FOREARM_PUNCH_SPEED : FOREARM_PUNCH_SPEED;
                        AudioManager.getInstance().playSfx(Sonic2Sfx.SPINDASH_RELEASE.id);
                    } else {
                        punchYVel += 0x20;
                        currentY += (punchYVel >> 8);
                    }
                }
                case 2 -> { // Travel
                    punchTimer--;
                    if (punchTimer < 0) {
                        punchPhase = 4;
                        punchXVel = -punchXVel;
                        punchTimer = 0x20;
                        int dy = savedY - currentY;
                        punchYVel = dy << 3;
                    } else {
                        currentX += (punchXVel >> 8);
                        currentY += (punchYVel >> 8);
                    }
                }
                case 4 -> { // Return
                    punchTimer--;
                    if (punchTimer < 0) {
                        punching = false;
                        punchXVel = 0;
                        punchYVel = 0;
                    } else {
                        currentX += (punchXVel >> 8);
                        currentY += (punchYVel >> 8);
                    }
                }
            }
        }
    }

    // ========================================================================
    // INNER CLASS: HeadChild - The only hittable part!
    // ROM: Ani_objC7_a: speed 7, $15×8, 0, 1, 2, $FA (loop)
    // ========================================================================

    static class HeadChild extends AbstractBossChild implements com.openggf.level.objects.TouchResponseProvider, com.openggf.level.objects.TouchResponseAttackable {
        private int headRoutine;
        private int waitTimer;
        private boolean eggmanBoarded;
        private int glowIndex;     // Current index in HEAD_GLOW_FRAMES
        private int glowTimer;     // Frame counter for glow animation speed

        HeadChild(Sonic2DeathEggRobotInstance parent, int priority) {
            super(parent, "Head", priority, Sonic2ObjectIds.DEATH_EGG_ROBOT);
            this.headRoutine = 0;
            this.waitTimer = 0;
            this.eggmanBoarded = false;
            this.glowIndex = 0;
            this.glowTimer = 0;
        }

        boolean isEggmanBoarded() {
            if (headRoutine == 0) {
                headRoutine = 2;
            }
            if (headRoutine == 2) {
                waitTimer++;
                if (waitTimer > 30) {
                    headRoutine = 4;
                    waitTimer = 0x40;
                    eggmanBoarded = true;
                }
            }
            return eggmanBoarded;
        }

        void setHeadRoutine(int routine) {
            this.headRoutine = routine;
        }

        @Override
        public void update(int frameCounter, AbstractPlayableSprite player) {
            if (!beginUpdate(frameCounter)) return;

            switch (headRoutine) {
                case 4 -> { // AnimateSprite with Ani_objC7_a
                    stepGlow();
                }
                case 6 -> { // Active during fight
                    stepGlow();
                }
                case 8 -> { // Defeated
                    collisionFlags = 0;
                }
            }
            updateDynamicSpawn();
        }

        /** ROM-accurate head glow: speed 7, $15×8, 0, 1, 2, $FA (loop) */
        private void stepGlow() {
            glowTimer++;
            if (glowTimer > HEAD_GLOW_SPEED) { // > 7 means every 8th frame
                glowTimer = 0;
                glowIndex++;
                if (glowIndex >= HEAD_GLOW_FRAMES.length) {
                    glowIndex = 0; // $FA = loop back to start
                }
            }
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            ObjectRenderManager renderManager = ((Sonic2DeathEggRobotInstance) parent).levelManager
                    .getObjectRenderManager();
            if (renderManager == null) return;
            PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.DEZ_BOSS);
            if (renderer == null || !renderer.isReady()) return;
            boolean flip = ((Sonic2DeathEggRobotInstance) parent).facingLeft;
            int headFrame = HEAD_GLOW_FRAMES[glowIndex];
            renderer.drawFrameIndex(headFrame, currentX, currentY, flip, false);
        }

        @Override
        public int getCollisionFlags() {
            if (collisionFlags == 0) return 0;
            Sonic2DeathEggRobotInstance boss = (Sonic2DeathEggRobotInstance) parent;
            if (boss.state.invulnerable || boss.state.defeated) return 0;
            return collisionFlags;
        }

        @Override
        public int getCollisionProperty() {
            Sonic2DeathEggRobotInstance boss = (Sonic2DeathEggRobotInstance) parent;
            return boss.state.hitCount;
        }

        @Override
        public void onPlayerAttack(AbstractPlayableSprite player,
                                   com.openggf.level.objects.TouchResponseResult result) {
            ((Sonic2DeathEggRobotInstance) parent).onHeadHit();
        }
    }

    // ========================================================================
    // INNER CLASS: JetChild - Jet exhaust with ROM-accurate animations
    // ROM: Ani_objC7_b (4 animations)
    // ========================================================================

    static class JetChild extends AbstractBossChild {
        private int jetRoutine;
        private int jetAnimId;  // Current animation ID (0-3)
        private int jetFrame;
        private int animIdx;    // Current frame index within animation
        private int animTimer;  // Speed counter

        JetChild(Sonic2DeathEggRobotInstance parent, int priority) {
            super(parent, "Jet", priority, Sonic2ObjectIds.DEATH_EGG_ROBOT);
            this.jetRoutine = 0;
            this.jetAnimId = 0;
            this.jetFrame = FRAME_JET_OFF;
            this.animIdx = 0;
            this.animTimer = 0;
        }

        void setJetRoutine(int routine) {
            this.jetRoutine = routine;
            // ROM: routine 2/8 → anim 0 (idle), routine 4 → anim 3, routine 6 → anim 1
            switch (routine) {
                case 2, 8 -> setJetAnim(0);
                case 4 -> setJetAnim(3);
                case 6 -> setJetAnim(1);
            }
        }

        void setJetAnim(int anim) {
            if (this.jetAnimId != anim) {
                this.jetAnimId = anim;
                this.animIdx = 0;
                this.animTimer = 0;
            }
        }

        private int[] getCurrentAnimFrames() {
            return switch (jetAnimId) {
                case 0 -> JET_ANIM_0;
                case 1 -> JET_ANIM_1;
                case 2 -> JET_ANIM_2;
                case 3 -> JET_ANIM_3;
                default -> JET_ANIM_0;
            };
        }

        private int getAnimSpeed() {
            return switch (jetAnimId) {
                case 0, 1, 2 -> 1; // speed 1: every 2nd frame
                case 3 -> 0;       // speed 0: every frame
                default -> 1;
            };
        }

        @Override
        public void update(int frameCounter, AbstractPlayableSprite player) {
            if (!beginUpdate(frameCounter)) return;

            Sonic2DeathEggRobotInstance boss = (Sonic2DeathEggRobotInstance) parent;
            if (boss.bodyRoutine >= BODY_RISE && jetRoutine > 0) {
                int[] frames = getCurrentAnimFrames();
                int speed = getAnimSpeed();
                animTimer++;
                if (animTimer > speed) {
                    animTimer = 0;
                    animIdx++;
                    if (animIdx >= frames.length) {
                        // Handle terminators
                        if (jetAnimId == 2) {
                            // $FD: go to previous anim (anim 1)
                            setJetAnim(1);
                        } else {
                            // $FF/$FA: loop
                            animIdx = 0;
                        }
                    }
                }
                if (animIdx < frames.length) {
                    jetFrame = frames[animIdx];
                }
            }
            updateDynamicSpawn();
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            ObjectRenderManager renderManager = ((Sonic2DeathEggRobotInstance) parent).levelManager
                    .getObjectRenderManager();
            if (renderManager == null) return;
            PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.DEZ_BOSS);
            if (renderer == null || !renderer.isReady()) return;
            boolean flip = ((Sonic2DeathEggRobotInstance) parent).facingLeft;
            renderer.drawFrameIndex(jetFrame, currentX, currentY, flip, false);
        }

        public int getCollisionFlags() {
            return collisionFlags;
        }
    }

    // ========================================================================
    // INNER CLASS: SensorChild - Targeting sensor with velocity-tracked animation
    // ROM: ObjC7_TargettingSensor (s2.asm:82951-83044)
    // ========================================================================

    static class SensorChild extends AbstractBossChild {
        private int sensorRoutine;  // 0=init, 2=tracking, 4=lock-on
        private int countdown;      // ROM: objoff_2A
        private int beepInterval;   // ROM: angle — current beep interval
        private int beepCounter;    // Frames until next beep
        private int animIdx;        // Current frame in SENSOR_ANIM_FRAMES
        private int animTimer;      // Speed counter
        private int targetX;        // Tracked X position
        private int targetY;        // Tracked Y position

        SensorChild(Sonic2DeathEggRobotInstance parent, int playerX, int playerY) {
            super(parent, "Sensor", 1, Sonic2ObjectIds.DEATH_EGG_ROBOT);
            this.currentX = playerX;
            this.currentY = playerY;
            this.targetX = playerX;
            this.targetY = playerY;
            this.sensorRoutine = 0;
            this.countdown = 0xA0; // 160 frames
            this.beepInterval = 0x18; // Initial interval = 24 frames
            this.beepCounter = 0x18;
            this.animIdx = 0;
            this.animTimer = 0;
        }

        @Override
        public void update(int frameCounter, AbstractPlayableSprite player) {
            if (!beginUpdate(frameCounter)) return;

            Sonic2DeathEggRobotInstance boss = (Sonic2DeathEggRobotInstance) parent;

            switch (sensorRoutine) {
                case 0 -> { // Init
                    sensorRoutine = 2;
                }
                case 2 -> { // Tracking
                    countdown--;
                    if (countdown <= 0) {
                        sensorRoutine = 4;
                        countdown = 0x40; // 64 frames for lock-on
                        beepCounter = 4;
                    } else {
                        // Track toward player position with lag
                        if (player != null) {
                            targetX = player.getCentreX();
                            targetY = player.getCentreY();
                        }
                        // Smooth approach
                        currentX += (targetX - currentX) >> 3;
                        currentY += (targetY - currentY) >> 3;

                        // Beep with decreasing interval
                        beepCounter--;
                        if (beepCounter <= 0) {
                            AudioManager.getInstance().playSfx(Sonic2Sfx.BEEP.id);
                            if (beepInterval > 4) {
                                beepInterval--;
                            }
                            beepCounter = beepInterval;
                        }
                    }
                    stepAnim();
                }
                case 4 -> { // Lock-on
                    countdown--;
                    if (countdown <= 0) {
                        // Report final position to body and self-destruct
                        boss.reportTargetedPlayerX(currentX);
                        setDestroyed(true);
                        return;
                    }
                    // Fast beeping
                    beepCounter--;
                    if (beepCounter <= 0) {
                        AudioManager.getInstance().playSfx(Sonic2Sfx.BEEP.id);
                        beepCounter = 4;
                    }
                    stepAnim();
                }
            }
            updateDynamicSpawn();
        }

        private void stepAnim() {
            animTimer++;
            if (animTimer > SENSOR_ANIM_SPEED) {
                animTimer = 0;
                animIdx++;
                if (animIdx >= SENSOR_ANIM_FRAMES.length) {
                    animIdx = 0; // $FF loop
                }
            }
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            ObjectRenderManager renderManager = ((Sonic2DeathEggRobotInstance) parent).levelManager
                    .getObjectRenderManager();
            if (renderManager == null) return;
            PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.DEZ_BOSS);
            if (renderer == null || !renderer.isReady()) return;
            renderer.drawFrameIndex(SENSOR_ANIM_FRAMES[animIdx], currentX, currentY, false, false);
        }

        public int getCollisionFlags() {
            return 0; // Sensor has no collision
        }
    }

    // ========================================================================
    // INNER CLASS: BombChild - Projectile with arc trajectory
    // ========================================================================

    static class BombChild extends AbstractBossChild {
        private int xVel;
        private int yVel;
        private int groundTimer;
        private boolean onGround;
        private boolean detonating;
        private int detonateFrame;
        private int detonateTimer;
        private static final int BOMB_GROUND_Y = 0x170;
        private static final int BOMB_GROUND_TIMER = 0x40;

        BombChild(Sonic2DeathEggRobotInstance parent, int spawnX, int spawnY,
                  int xVel, int yVel) {
            super(parent, "Bomb", 5, Sonic2ObjectIds.DEATH_EGG_ROBOT);
            this.currentX = spawnX;
            this.currentY = spawnY;
            this.xVel = xVel;
            this.yVel = yVel;
            this.onGround = false;
            this.detonating = false;
            this.groundTimer = BOMB_GROUND_TIMER;
        }

        @Override
        public void update(int frameCounter, AbstractPlayableSprite player) {
            if (!beginUpdate(frameCounter)) return;

            Sonic2DeathEggRobotInstance boss = (Sonic2DeathEggRobotInstance) parent;

            if (detonating) {
                detonateTimer--;
                if (detonateTimer < 0) {
                    detonateTimer = 7;
                    detonateFrame++;
                    if (detonateFrame >= 7) {
                        setDestroyed(true);
                        return;
                    }
                }
                updateDynamicSpawn();
                return;
            }

            if (boss.state.defeated) {
                startDetonate();
                return;
            }

            if (!onGround) {
                currentX += (xVel >> 8);
                currentY += (yVel >> 8);
                yVel += GRAVITY;
                if (currentY >= BOMB_GROUND_Y) {
                    currentY = BOMB_GROUND_Y;
                    onGround = true;
                    xVel = 0;
                    yVel = 0;
                }
            } else {
                groundTimer--;
                if (groundTimer < 0) {
                    startDetonate();
                }
            }
            updateDynamicSpawn();
        }

        private void startDetonate() {
            detonating = true;
            detonateFrame = 0;
            detonateTimer = 7;
            AudioManager.getInstance().playSfx(Sonic2Sfx.BOSS_EXPLOSION.id);
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            ObjectRenderManager renderManager = ((Sonic2DeathEggRobotInstance) parent).levelManager
                    .getObjectRenderManager();
            if (renderManager == null) return;
            PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.DEZ_BOSS);
            if (renderer == null || !renderer.isReady()) return;
            if (!detonating) {
                renderer.drawFrameIndex(FRAME_BOMB, currentX, currentY, false, false);
            }
        }

        public int getCollisionFlags() {
            if (detonating && detonateFrame >= 5) return 0;
            return 0x89;
        }
    }
}
