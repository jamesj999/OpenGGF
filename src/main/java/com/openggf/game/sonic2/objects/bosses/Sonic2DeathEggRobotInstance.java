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
import java.util.concurrent.ThreadLocalRandom;

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
 * - 4: Countdown - 60 frames, then fade music
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
    static final int COLLISION_HEAD = 0x2A;
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
    // Only for children that use PositionChildren: FrontLowerLeg, FrontForearm,
    // UpperArm, FrontThigh, BackLowerLeg, BackForearm, BackThigh
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

    // Targeting sensor data
    private int targetedPlayerX; // ROM: objoff_28(a0) - reported by targeting sensor

    // Defeat sub-state
    private int defeatPhase;    // 0=fall, 2=explode, 4=walk player right
    private boolean controlsLocked;

    // Child references (10 permanent)
    private BodyPartChild shoulder;
    private BodyPartChild frontLowerLeg;
    private ForearmChild frontForearm;
    private BodyPartChild upperArm;
    private BodyPartChild frontThigh;
    private HeadChild head;
    private JetChild jet;
    private BodyPartChild backLowerLeg;
    private ForearmChild backForearm;
    private BodyPartChild backThigh;

    // Pending transient spawn flags
    private boolean sensorSpawned;

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
        facingLeft = false;
        defeatPhase = 0;
        controlsLocked = false;
        targetedPlayerX = 0;
        frontPunchTriggered = false;
        backPunchTriggered = false;
        sensorSpawned = false;

        // Spawn 10 permanent children (ROM: loc_3D52A)
        spawnChildren();

        // Advance to WaitEggman (auto-skip Eggman boarding for now)
        bodyRoutine = BODY_WAIT_EGGMAN;
    }

    private void spawnChildren() {
        shoulder = new BodyPartChild(this, "Shoulder", 4, FRAME_SHOULDER, 5);
        frontLowerLeg = new BodyPartChild(this, "FrontLowerLeg", 4, FRAME_LOWER_LEG, 4);
        frontForearm = new ForearmChild(this, "FrontForearm", 4, true);
        upperArm = new BodyPartChild(this, "UpperArm", 4, FRAME_ARM, 4);
        frontThigh = new BodyPartChild(this, "FrontThigh", 4, FRAME_THIGH, 4);
        head = new HeadChild(this, 4);
        jet = new JetChild(this, 4);
        backLowerLeg = new BodyPartChild(this, "BackLowerLeg", 5, FRAME_LOWER_LEG, 4);
        backForearm = new ForearmChild(this, "BackForearm", 5, false);
        backThigh = new BodyPartChild(this, "BackThigh", 5, FRAME_THIGH, 4);

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
        // ROM: btst #status.npc.misc,status(a0); bne.s +
        // Auto-trigger after head signals boarding (or skip for now)
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
            // Signal head to enter active state
            if (head != null) {
                head.setHeadRoutine(4);
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
            // Set collision
            state.hitCount = DEATH_EGG_ROBOT_HP;
            initChildCollisions();
            // Signal head to enter ready state
            if (head != null) {
                head.setHeadRoutine(6);
            }
            return;
        }
        // ROM: SndID_Rumbling every frame during rise
        AudioManager.getInstance().playSfx(Sonic2Sfx.RUMBLING.id);
        // ObjectMove (constant velocity, no gravity)
        state.yFixed += (state.yVel << 8);
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

        // ROM: cmpi.b #2,d0 / bne.s + => for attack type 2 (jet stomp), signal head
        if (currentAttack == 2 && head != null) {
            head.setHeadRoutine(4);
            head.setHeadAnim(2);
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
    // ========================================================================

    private void updateAttackWalkPunch(int frameCounter, AbstractPlayableSprite player) {
        switch (attackPhase) {
            case 0 -> { // Wait $20 frames
                actionTimer--;
                if (actionTimer < 0) {
                    attackPhase = 2;
                }
            }
            case 2 -> { // Walk forward (group animation)
                if (stepGroupAnimation(WALK_FORWARD_SCRIPT)) {
                    attackPhase = 4;
                    actionTimer = 0x40;
                }
            }
            case 4 -> { // Pause $40 frames
                actionTimer--;
                if (actionTimer < 0) {
                    attackPhase = 6;
                    // Trigger front forearm punch
                    frontPunchTriggered = true;
                }
            }
            case 6 -> { // Walk backward (group animation)
                if (stepGroupAnimation(WALK_BACKWARD_SCRIPT)) {
                    // Return to select attack
                    bodyRoutine = BODY_SELECT_ATTACK;
                    actionTimer = 0x40;
                }
            }
        }
    }

    // ========================================================================
    // ATTACK TYPE 2: JET STOMP
    // ========================================================================

    private void updateAttackJetStomp(int frameCounter, AbstractPlayableSprite player) {
        switch (attackPhase) {
            case 0 -> { // Wait
                actionTimer--;
                if (actionTimer < 0) {
                    attackPhase = 2;
                }
            }
            case 2 -> { // Crouch (group animation)
                if (stepGroupAnimation(CROUCH_SCRIPT)) {
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
                    sensorSpawned = true;
                    return;
                }
                // Fire sound every 32 frames
                if ((frameCounter & 0x1F) == 0) {
                    AudioManager.getInstance().playSfx(Sonic2Sfx.FIRE.id);
                }
                // ObjectMove
                state.yFixed += (state.yVel << 8);
                state.updatePositionFromFixed();
                positionChildren();
            }
            case 6 -> { // Wait for sensor to report player X
                if (targetedPlayerX != 0) {
                    attackPhase = 8;
                    state.x = targetedPlayerX;
                    state.xFixed = state.x << 16;
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
                        camera.setShakeOffsets(0, 4); // ROM: screen shake on stomp
                    }
                    // Signal head ready
                    if (head != null) {
                        head.setHeadRoutine(6);
                    }
                    AudioManager.getInstance().playSfx(Sonic2Sfx.SMASH.id);
                    return;
                }
                state.yFixed += (state.yVel << 8);
                state.updatePositionFromFixed();
                positionChildren();
            }
            case 0x0A -> { // Stand up (group animation)
                if (stepGroupAnimation(STAND_UP_SCRIPT)) {
                    positionChildren();
                    // Check orientation to determine if we drop bombs
                    boolean playerOnSameSide = isPlayerOnFacingSide(player);
                    if (!playerOnSameSide) {
                        // Return to select attack
                        bodyRoutine = BODY_SELECT_ATTACK;
                    } else {
                        // Drop bombs, then return
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
    // ========================================================================

    private void updateAttackStompBombs(int frameCounter, AbstractPlayableSprite player) {
        switch (attackPhase) {
            case 0 -> { // Wait
                actionTimer--;
                if (actionTimer < 0) {
                    attackPhase = 2;
                    // Set p2_pushing flag for walk animation
                    backPunchTriggered = false;
                }
            }
            case 2 -> { // Walk toward player (group animation)
                if (stepGroupAnimation(WALK_FORWARD_SCRIPT)) {
                    // Check orientation to player
                    boolean playerOnSameSide = isPlayerOnFacingSide(player);
                    if (playerOnSameSide) {
                        attackPhase = 4;
                        actionTimer = 0x40;
                        // Trigger back forearm punch
                        backPunchTriggered = true;
                    } else {
                        // Walk toward player and drop bombs
                        attackPhase = 8;
                        actionTimer = 0x20;
                        spawnBombs(player);
                    }
                }
            }
            case 4 -> { // Pause with punch active
                actionTimer--;
                if (actionTimer < 0) {
                    attackPhase = 6;
                    // Trigger back forearm punch launch
                    backPunchTriggered = true;
                    actionTimer = 0x40;
                }
            }
            case 6 -> { // Walk backward
                if (stepGroupAnimation(WALK_BACKWARD_SCRIPT)) {
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
        }
    }

    /** Defeat phase 0: Fall with gravity, bounce at floor Y=$15C */
    private void updateDefeatFall(int frameCounter) {
        spawnDefeatExplosion();
        // ObjectMoveAndFall
        state.yFixed += (state.yVel << 8);
        state.yVel += GRAVITY;
        state.updatePositionFromFixed();

        if (state.y >= DEFEAT_FLOOR_Y) {
            state.y = DEFEAT_FLOOR_Y;
            state.yFixed = state.y << 16;
            int absVel = state.yVel;
            if (absVel < 0) {
                // Negative velocity means still going up - just advance
                defeatPhase = 2;
                actionTimer = DEFEAT_EXPLODE_TIMER;
                return;
            }
            int bounceVel = absVel >> 2;
            if (bounceVel >= DEFEAT_BOUNCE_THRESHOLD) {
                state.yVel = -bounceVel;
            } else {
                // Stop bouncing
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
            // Lock controls and extend camera
            defeatPhase = 4;
            controlsLocked = true;
            Camera camera = Camera.getInstance();
            if (camera != null) {
                camera.setMaxX((short) DEFEAT_CAMERA_MAX_X);
            }
        }
    }

    /** Defeat phase 4: Force player right, break apart when camera reaches $840 */
    private void updateDefeatWalkPlayer(int frameCounter, AbstractPlayableSprite player) {
        // ROM: move.w #(button_right_mask<<8)|button_right_mask,(Ctrl_1_Logical).w
        // Player is forced to walk right - this is handled externally via control lock

        Camera camera = Camera.getInstance();
        if (camera != null && camera.getX() >= DEFEAT_CAMERA_WALK_TARGET) {
            // Break apart body pieces
            breakApart();

            // Trigger screen shake and rumbling
            if (camera != null) {
                camera.setShakeOffsets(0, 4); // ROM: Screen_Shaking_Flag + DEZ_Shake_Timer
            }

            // Delete head
            if (head != null) {
                head.setDestroyed(true);
            }

            // Transition to ending setup
            // TODO: Full ending sequence (fade to white, game mode change)
            AudioManager.getInstance().fadeOutMusic();
        }
    }

    // ========================================================================
    // HIT DETECTION (custom - head is only hittable part)
    // ========================================================================

    /**
     * ROM: ObjC7_CheckHit (s2.asm:83187-83220)
     * Check if head has been hit and process damage/flash on body.
     */
    private void checkHit() {
        if (state.hitCount <= 0) {
            triggerDefeatSequence();
            return;
        }
        if (state.invulnerable) {
            // Flash palette line 2
            paletteFlasher.update();
            state.invulnerabilityTimer--;
            if (state.invulnerabilityTimer <= 0) {
                state.invulnerable = false;
                paletteFlasher.stopFlash();
                // Restore collision flags
                if (head != null) {
                    head.setCollisionActive(true);
                }
            }
        }
    }

    /**
     * Called by HeadChild when the head takes a hit.
     */
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
        // ROM: ObjC7_Beaten (s2.asm:83222-83238)
        GameServices.gameState().addScore(1000);
        bodyRoutine = BODY_DEFEAT;
        defeatPhase = 0;
        animPhase = 0;
        state.xVel = 0;
        state.yVel = 0;
        // Remove all collision
        removeAllCollision();
        // Break apart body parts
        breakApart();
        // Delete head
        if (head != null) {
            head.setDestroyed(true);
        }
    }

    // ========================================================================
    // CHILD POSITIONING
    // ========================================================================

    /** Position all children relative to body (ROM: ObjC7_PositionChildren) */
    private void positionChildren() {
        int flipSign = facingLeft ? -1 : 1;

        // 7 children positioned via ObjC7_ChildDeltas
        if (frontLowerLeg != null) {
            frontLowerLeg.setPosition(
                    state.x + (CHILD_DELTAS[0][0] * flipSign),
                    state.y + CHILD_DELTAS[0][1]);
        }
        if (frontForearm != null && !frontForearm.isPunching()) {
            frontForearm.setPosition(
                    state.x + (CHILD_DELTAS[1][0] * flipSign),
                    state.y + CHILD_DELTAS[1][1]);
        }
        if (upperArm != null) {
            upperArm.setPosition(
                    state.x + (CHILD_DELTAS[2][0] * flipSign),
                    state.y + CHILD_DELTAS[2][1]);
        }
        if (frontThigh != null) {
            frontThigh.setPosition(
                    state.x + (CHILD_DELTAS[3][0] * flipSign),
                    state.y + CHILD_DELTAS[3][1]);
        }
        if (backLowerLeg != null) {
            backLowerLeg.setPosition(
                    state.x + (CHILD_DELTAS[4][0] * flipSign),
                    state.y + CHILD_DELTAS[4][1]);
        }
        if (backForearm != null && !backForearm.isPunching()) {
            backForearm.setPosition(
                    state.x + (CHILD_DELTAS[5][0] * flipSign),
                    state.y + CHILD_DELTAS[5][1]);
        }
        if (backThigh != null) {
            backThigh.setPosition(
                    state.x + (CHILD_DELTAS[6][0] * flipSign),
                    state.y + CHILD_DELTAS[6][1]);
        }

        // 3 children positioned via loc_3E282 (relative delta from body with x-flip)
        if (shoulder != null) {
            shoulder.setPosition(
                    state.x + (SHOULDER_DX * flipSign),
                    state.y + SHOULDER_DY);
        }
        if (head != null) {
            head.setPosition(
                    state.x + (HEAD_DX * flipSign),
                    state.y + HEAD_DY);
        }
        if (jet != null) {
            jet.setPosition(
                    state.x + (JET_DX * flipSign),
                    state.y + JET_DY);
        }
    }

    /** Initialize child collision flags (ROM: ObjC7_InitCollision, s2.asm:83282) */
    private void initChildCollisions() {
        // Collision flags are stored in CHILD_COLLISION array
        // Children are indexed by their SST offset order
        // Head ($2A) is the only hittable part
    }

    /** Remove all child collision (ROM: ObjC7_RemoveCollision, s2.asm:83324) */
    private void removeAllCollision() {
        // Clear collision on all children
    }

    /** Break apart body pieces with per-part velocities (ROM: ObjC7_Break, s2.asm:83241) */
    private void breakApart() {
        // Set break velocities on 8 body parts (excludes head and jet)
        AbstractBossChild[] breakParts = {
                shoulder, frontLowerLeg, frontForearm, upperArm,
                frontThigh, backLowerLeg, backForearm, backThigh
        };
        for (int i = 0; i < breakParts.length && i < BREAK_VELOCITIES.length; i++) {
            if (breakParts[i] != null && breakParts[i] instanceof BodyPartChild bpc) {
                bpc.startFalling(BREAK_VELOCITIES[i][0], BREAK_VELOCITIES[i][1]);
            } else if (breakParts[i] != null && breakParts[i] instanceof ForearmChild fc) {
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
        // ROM: loc_3E168 - update render_flags.x_flip on all children
    }

    private void spawnBombs(AbstractPlayableSprite player) {
        // ROM: CreateEggmanBombs (s2.asm:83336)
        // Spawn 2 bombs with arc trajectories
        ObjectRenderManager renderManager = levelManager.getObjectRenderManager();
        if (renderManager == null || levelManager.getObjectManager() == null) return;

        int xSign = facingLeft ? -1 : 1;

        // Bomb 1: x_vel=$60, y_vel=-$800
        BombChild bomb1 = new BombChild(this, state.x + (JET_DX * xSign),
                state.y + JET_DY, 0x60 * xSign, -0x800);
        childComponents.add(bomb1);

        // Bomb 2: x_vel=$C0, y_vel=-$A00
        BombChild bomb2 = new BombChild(this, state.x + (JET_DX * xSign),
                state.y + JET_DY, 0xC0 * xSign, -0xA00);
        childComponents.add(bomb2);
    }

    /** Simplified group animation stepper. Returns true when complete. */
    private boolean stepGroupAnimation(int[][] script) {
        // Group animations move children by delta values per step
        // For now, just count down and return true when done
        actionTimer--;
        return actionTimer < 0;
    }

    // ========================================================================
    // GROUP ANIMATION SCRIPTS (simplified from ROM data)
    // ========================================================================

    /** Walk forward script step count (ROM: off_3E2F6, 5 steps) */
    private static final int[][] WALK_FORWARD_SCRIPT = {
            { 0, 1, 2, 3 } // 4 animation steps, $FF terminator
    };

    /** Walk backward script step count (ROM: off_3E300, 5 steps) */
    private static final int[][] WALK_BACKWARD_SCRIPT = {
            { 5, 6, 7, 8 }
    };

    /** Crouch script (ROM: off_3E3D0, 3 steps) */
    private static final int[][] CROUCH_SCRIPT = {
            { 0, 1, 2 }
    };

    /** Stand-up script (ROM: off_3E30A, 10 steps) */
    private static final int[][] STAND_UP_SCRIPT = {
            { 0, 1, 2, 3, 4, 5, 6, 7, 8 }
    };

    // ========================================================================
    // ACCESSORS (for tests and children)
    // ========================================================================

    public int getBodyRoutine() {
        return bodyRoutine;
    }

    public int getCurrentFrame() {
        return currentFrame;
    }

    public int getAttackIndex() {
        return attackIndex;
    }

    public int getCurrentAttack() {
        return currentAttack;
    }

    public int getDefeatPhase() {
        return defeatPhase;
    }

    public boolean isFacingLeft() {
        return facingLeft;
    }

    /** For tests: expose head child for collision verification. */
    public HeadChild getHead() {
        return head;
    }

    /** For tests: expose action timer. */
    public int getActionTimer() {
        return actionTimer;
    }

    /** Report player X from targeting sensor */
    void reportTargetedPlayerX(int x) {
        this.targetedPlayerX = x;
    }

    boolean isFrontPunchTriggered() {
        boolean val = frontPunchTriggered;
        frontPunchTriggered = false;
        return val;
    }

    boolean isBackPunchTriggered() {
        boolean val = backPunchTriggered;
        backPunchTriggered = false;
        return val;
    }

    // ========================================================================
    // RENDERING
    // ========================================================================

    @Override
    public int getPriorityBucket() {
        return 4;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = levelManager.getObjectRenderManager();
        if (renderManager == null) return;

        PatternSpriteRenderer renderer = renderManager.getRenderer(
                Sonic2ObjectArtKeys.DEZ_BOSS);
        if (renderer == null || !renderer.isReady()) return;

        renderer.drawFrameIndex(currentFrame, state.x, state.y, facingLeft, false);
    }

    // ========================================================================
    // INNER CLASS: BodyPartChild - Generic body part (shoulder, arm, legs, thighs)
    // ========================================================================

    static class BodyPartChild extends AbstractBossChild {
        private int frame;
        private boolean falling;
        private int xVel;
        private int yVel;
        private int fallTimer;

        BodyPartChild(Sonic2DeathEggRobotInstance parent, String name,
                      int priority, int frame, int initialPriority) {
            super(parent, name, initialPriority, Sonic2ObjectIds.DEATH_EGG_ROBOT);
            this.frame = frame;
            this.priority = priority;
            this.falling = false;
            this.xVel = 0;
            this.yVel = 0;
            this.fallTimer = 0x80; // ROM: move.w #$80,objoff_2A(a3)
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
                // ObjectMoveAndFall
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
            PatternSpriteRenderer renderer = renderManager.getRenderer(
                    Sonic2ObjectArtKeys.DEZ_BOSS);
            if (renderer == null || !renderer.isReady()) return;
            boolean flip = ((Sonic2DeathEggRobotInstance) parent).facingLeft;
            renderer.drawFrameIndex(frame, currentX, currentY, flip, false);
        }

        public int getCollisionFlags() {
            return 0;
        }
    }

    // ========================================================================
    // INNER CLASS: ForearmChild - Forearm with punch mechanics
    // ========================================================================

    static class ForearmChild extends AbstractBossChild {
        private final boolean isFront;
        private boolean punching;
        private int punchPhase;
        private int punchTimer;
        private int xVel;
        private int yVel;
        private int savedY;
        private boolean falling;
        private int fallTimer;
        private int fallXVel;
        private int fallYVel;

        ForearmChild(Sonic2DeathEggRobotInstance parent, String name,
                     int priority, boolean isFront) {
            super(parent, name, priority, Sonic2ObjectIds.DEATH_EGG_ROBOT);
            this.isFront = isFront;
            this.punching = false;
            this.punchPhase = 0;
            this.punchTimer = 0;
            this.xVel = 0;
            this.yVel = 0;
            this.savedY = 0;
            this.falling = false;
            this.fallTimer = 0x80;
        }

        boolean isPunching() {
            return punching;
        }

        void startFalling(int xVel, int yVel) {
            this.falling = true;
            this.fallXVel = xVel;
            this.fallYVel = yVel;
            this.fallTimer = 0x80;
            this.punching = false;
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
                currentX += (fallXVel >> 8);
                currentY += (fallYVel >> 8);
                fallYVel += GRAVITY;
                updateDynamicSpawn();
                return;
            }

            Sonic2DeathEggRobotInstance boss = (Sonic2DeathEggRobotInstance) parent;

            // Check if punch was triggered
            if (!punching) {
                boolean triggered = isFront ? boss.isFrontPunchTriggered()
                        : boss.isBackPunchTriggered();
                if (triggered) {
                    punching = true;
                    punchPhase = 0;
                    punchTimer = 0x10; // Wind up for 16 frames
                    savedY = currentY;
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
                        // Calculate Y velocity from distance to player
                        int dy = 0;
                        if (player != null) {
                            dy = Math.abs(player.getCentreY() - currentY);
                        }
                        int yVelIdx = Math.min(3, (dy & 0xC0) >> 5);
                        int[] Y_VEL_TABLE = { 0x200, 0x100, 0x80, 0 };
                        yVel = Y_VEL_TABLE[yVelIdx];
                        if (player != null && player.getCentreY() < currentY) {
                            yVel = -yVel;
                        }
                        // X velocity = $800 in facing direction
                        xVel = boss.facingLeft ? -FOREARM_PUNCH_SPEED : FOREARM_PUNCH_SPEED;
                        AudioManager.getInstance().playSfx(Sonic2Sfx.SPINDASH_RELEASE.id);
                    } else {
                        // Add $20 to y_vel for wind-up animation
                        yVel += 0x20;
                        currentY += (yVel >> 8);
                    }
                }
                case 2 -> { // Travel
                    punchTimer--;
                    if (punchTimer < 0) {
                        punchPhase = 4;
                        xVel = -xVel; // Reverse
                        punchTimer = 0x20;
                        // Calculate return Y velocity
                        int dy = savedY - currentY;
                        yVel = dy << 3;
                    } else {
                        currentX += (xVel >> 8);
                        currentY += (yVel >> 8);
                    }
                }
                case 4 -> { // Return
                    punchTimer--;
                    if (punchTimer < 0) {
                        punching = false;
                        xVel = 0;
                        yVel = 0;
                    } else {
                        currentX += (xVel >> 8);
                        currentY += (yVel >> 8);
                    }
                }
            }
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            if (punching) {
                // Hidden during certain phases in ROM (btst p2_pushing check)
                // For now always render
            }
            ObjectRenderManager renderManager = ((Sonic2DeathEggRobotInstance) parent).levelManager
                    .getObjectRenderManager();
            if (renderManager == null) return;
            PatternSpriteRenderer renderer = renderManager.getRenderer(
                    Sonic2ObjectArtKeys.DEZ_BOSS);
            if (renderer == null || !renderer.isReady()) return;
            boolean flip = ((Sonic2DeathEggRobotInstance) parent).facingLeft;
            renderer.drawFrameIndex(FRAME_FOREARM, currentX, currentY, flip, false);
        }

        public int getCollisionFlags() {
            return 0;
        }
    }

    // ========================================================================
    // INNER CLASS: HeadChild - The only hittable part!
    // ========================================================================

    static class HeadChild extends AbstractBossChild implements com.openggf.level.objects.TouchResponseProvider, com.openggf.level.objects.TouchResponseAttackable {
        private int headRoutine;
        private int headAnim;
        private int waitTimer;
        private boolean eggmanBoarded;
        private boolean collisionActive;
        private int animFrame;
        private int animTimer;

        HeadChild(Sonic2DeathEggRobotInstance parent, int priority) {
            super(parent, "Head", priority, Sonic2ObjectIds.DEATH_EGG_ROBOT);
            this.headRoutine = 0;
            this.headAnim = 0;
            this.waitTimer = 0;
            this.eggmanBoarded = false;
            this.collisionActive = false;
            this.animFrame = 0;
            this.animTimer = 0;
        }

        boolean isEggmanBoarded() {
            // Auto-signal boarding after initialization for now
            // ROM: checks DEZ_Eggman p1_standing flag
            if (headRoutine == 0) {
                headRoutine = 2;
            }
            if (headRoutine == 2) {
                // ROM: loc_3DC02 - check if Eggman is standing
                // Auto-trigger after a brief wait
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

        void setHeadAnim(int anim) {
            this.headAnim = anim;
        }

        void setCollisionActive(boolean active) {
            this.collisionActive = active;
        }

        @Override
        public void update(int frameCounter, AbstractPlayableSprite player) {
            if (!beginUpdate(frameCounter)) return;

            switch (headRoutine) {
                case 4 -> { // Animate (Eggman entering)
                    // ROM: Ani_objC7_a animation
                    animTimer++;
                    if (animTimer > 7) {
                        animTimer = 0;
                        animFrame++;
                    }
                }
                case 6 -> { // Active during fight
                    collisionActive = true;
                }
                case 8 -> { // Defeated - collision_property = -1
                    collisionActive = false;
                }
            }
            updateDynamicSpawn();
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            ObjectRenderManager renderManager = ((Sonic2DeathEggRobotInstance) parent).levelManager
                    .getObjectRenderManager();
            if (renderManager == null) return;
            PatternSpriteRenderer renderer = renderManager.getRenderer(
                    Sonic2ObjectArtKeys.DEZ_BOSS);
            if (renderer == null || !renderer.isReady()) return;
            boolean flip = ((Sonic2DeathEggRobotInstance) parent).facingLeft;
            renderer.drawFrameIndex(FRAME_HEAD_CLOSED, currentX, currentY, flip, false);
        }

        @Override
        public int getCollisionFlags() {
            if (!collisionActive) return 0;
            Sonic2DeathEggRobotInstance boss = (Sonic2DeathEggRobotInstance) parent;
            if (boss.state.invulnerable || boss.state.defeated) return 0;
            return COLLISION_HEAD;
        }

        @Override
        public int getCollisionProperty() {
            Sonic2DeathEggRobotInstance boss = (Sonic2DeathEggRobotInstance) parent;
            return boss.state.hitCount;
        }

        @Override
        public void onPlayerAttack(AbstractPlayableSprite player,
                                   com.openggf.level.objects.TouchResponseResult result) {
            // Relay hit to parent body
            ((Sonic2DeathEggRobotInstance) parent).onHeadHit();
        }
    }

    // ========================================================================
    // INNER CLASS: JetChild - Jet exhaust animation
    // ========================================================================

    static class JetChild extends AbstractBossChild {
        private int jetAnim;
        private int jetFrame;
        private int animTimer;

        JetChild(Sonic2DeathEggRobotInstance parent, int priority) {
            super(parent, "Jet", priority, Sonic2ObjectIds.DEATH_EGG_ROBOT);
            this.jetAnim = 0;
            this.jetFrame = FRAME_JET_OFF;
            this.animTimer = 0;
        }

        @Override
        public void update(int frameCounter, AbstractPlayableSprite player) {
            if (!beginUpdate(frameCounter)) return;

            Sonic2DeathEggRobotInstance boss = (Sonic2DeathEggRobotInstance) parent;
            // Animate jet: alternate between JET_OFF and JET_ON based on boss state
            if (boss.bodyRoutine >= BODY_RISE) {
                animTimer++;
                if (animTimer > 1) {
                    animTimer = 0;
                    jetFrame = (jetFrame == FRAME_JET_OFF) ? FRAME_JET_ON : FRAME_JET_OFF;
                }
            }
            updateDynamicSpawn();
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            ObjectRenderManager renderManager = ((Sonic2DeathEggRobotInstance) parent).levelManager
                    .getObjectRenderManager();
            if (renderManager == null) return;
            PatternSpriteRenderer renderer = renderManager.getRenderer(
                    Sonic2ObjectArtKeys.DEZ_BOSS);
            if (renderer == null || !renderer.isReady()) return;
            boolean flip = ((Sonic2DeathEggRobotInstance) parent).facingLeft;
            renderer.drawFrameIndex(jetFrame, currentX, currentY, flip, false);
        }

        public int getCollisionFlags() {
            return 0;
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
        /** ROM: cmpi.w #$170,d0 - bomb ground level */
        private static final int BOMB_GROUND_Y = 0x170;
        /** ROM: move.w #$40,objoff_2A(a0) - ground timer before detonate */
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

            // Check if parent is defeated -> detonate immediately
            if (boss.state.defeated) {
                startDetonate();
                return;
            }

            if (!onGround) {
                // ObjectMoveAndFall
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
            PatternSpriteRenderer renderer = renderManager.getRenderer(
                    Sonic2ObjectArtKeys.DEZ_BOSS);
            if (renderer == null || !renderer.isReady()) return;
            if (!detonating) {
                renderer.drawFrameIndex(FRAME_BOMB, currentX, currentY, false, false);
            }
            // When detonating, use fiery explosion art (separate sheet)
        }

        public int getCollisionFlags() {
            if (detonating && detonateFrame >= 5) return 0;
            if (detonating) return 0;
            return 0x89; // ROM: move.b #$89,collision_flags(a0)
        }
    }
}
