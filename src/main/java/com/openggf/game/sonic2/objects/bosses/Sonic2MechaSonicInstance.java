package com.openggf.game.sonic2.objects.bosses;

import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.sonic2.Sonic2LevelEventManager;
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
 * DEZ Silver Sonic / Mecha Sonic (Object 0xAF).
 * ROM Reference: s2.asm ObjAF (Silver Sonic)
 *
 * First boss of Death Egg Zone. A robotic hedgehog that performs dashing,
 * jumping, and spikeball attacks in a circular pattern of 16 attack slots.
 *
 * State Machine (main routine):
 * - 0: Init (set radii, spawn children, HP=8)
 * - 2: Wait for Camera_X >= $224, lock camera, boss_id=9, fade music
 * - 4: 60-frame countdown, then play boss music
 * - 6: Descend at constant y_vel=$100, fire sound every 32 frames
 * - 8: Idle on ground (100-frame timer), buzz at 50 remaining
 * - A: Attack dispatch (reads from 16-entry attack table)
 * - C: Defeat (256 iterations of explosions, then unlock camera)
 */
public class Sonic2MechaSonicInstance extends AbstractBossInstance {

    // State machine routine constants
    private static final int ROUTINE_INIT = 0x00;
    private static final int ROUTINE_WAIT_CAMERA = 0x02;
    private static final int ROUTINE_COUNTDOWN = 0x04;
    private static final int ROUTINE_DESCEND = 0x06;
    private static final int ROUTINE_IDLE = 0x08;
    private static final int ROUTINE_ATTACK = 0x0A;
    private static final int ROUTINE_DEFEAT = 0x0C;

    // Attack types (routine_secondary values from attack table)
    private static final int ATTACK_DASH_ACROSS = 0x00;
    private static final int ATTACK_AIM_AND_DASH = 0x06;
    private static final int ATTACK_AIM_DASH_WALK = 0x10;
    private static final int ATTACK_AIM_JUMP_SPIKEBALLS = 0x1E;

    // Attack pattern table (16 entries, circular via & 0x0F)
    // ROM: byte_398B0
    private static final int[] ATTACK_TABLE = {
            0x06, 0x00, 0x10, 0x06, 0x06, 0x1E, 0x00, 0x10,
            0x06, 0x06, 0x10, 0x06, 0x00, 0x06, 0x10, 0x1E
    };

    // Position constants
    /** Floor Y position (ROM: ObjCheckFloorDist triggers when d1 < 0) */
    private static final int FLOOR_Y = 0x1B0;
    /** Camera lock X position (ROM: $224) */
    private static final int CAMERA_LOCK_X = 0x224;
    /** Descent constant velocity (ROM: move.w #$100,y_vel(a0)) */
    private static final int DESCENT_SPEED = 0x100;

    // Velocity constants (8.8 fixed-point)
    /** Dash speed (ROM: move.w #$800,d0) */
    private static final int DASH_SPEED = 0x800;
    /** Slow dash speed (ROM: move.w #$400,d0) */
    private static final int SLOW_DASH_SPEED = 0x400;
    /** Deceleration rate per frame (ROM: $20) */
    private static final int DECEL_RATE = 0x20;
    /** Jump Y velocity (ROM: move.w #-$600,y_vel(a0)) */
    private static final int JUMP_Y_VEL = -0x600;

    // Timing constants
    /** Idle timer duration (ROM: move.b #$64,objoff_2A(a0)) */
    private static final int IDLE_DURATION = 0x64;
    /** Countdown for boss music (60 frames) */
    private static final int MUSIC_COUNTDOWN = 60;
    /** Aim hold duration (ROM: $20 frames) */
    private static final int AIM_HOLD_DURATION = 0x20;
    /** Invulnerability duration (ROM: move.b #$20,objoff_30(a0)) */
    private static final int INVULN_DURATION = 0x20;
    /** Defeat explosion timer (ROM: move.w #$FF,objoff_32(a0)) */
    private static final int DEFEAT_TIMER = 0xFF;
    /** Buzz sound threshold (play at 50 frames remaining) */
    private static final int BUZZ_THRESHOLD = 0x32;

    // Collision size indices
    /** Standing collision (ROM: move.b #$1A,collision_flags(a0)) */
    private static final int COLLISION_STANDING = 0x1A;
    /** Ball form collision (ROM: move.b #$9A,collision_flags(a0)) */
    private static final int COLLISION_BALL = 0x9A;

    // Rendering frame indices (from ROM mappings ObjAF_MapUnc_39E68)
    private static final int FRAME_STAND = 0;
    private static final int FRAME_AIM = 1;
    private static final int FRAME_LASER = 2;
    private static final int FRAME_WALK_A = 3;
    private static final int FRAME_WALK_B = 4;
    private static final int FRAME_WALK_C = 5;
    private static final int FRAME_BALL_A = 6;
    private static final int FRAME_BALL_B = 7;
    private static final int FRAME_BALL_C = 8;
    private static final int FRAME_JUMP_UP = 9;
    private static final int FRAME_JUMP_ARC = 10;
    private static final int FRAME_CROUCH = 11;
    private static final int FRAME_DEFEATED = 12;
    private static final int FRAME_DESCEND = 13;
    private static final int FRAME_IDLE = 14;
    // Frames 15-22 are spikeball projectile frames ($0F-$16)

    // Spikeball data table (8 entries from ROM byte_39D92)
    // Each entry: {x_offset, y_offset, x_vel, y_vel, mapping_frame}
    // ROM format: 6 bytes per entry (x_off, y_off, x_vel, y_vel, frame, render_flags)
    private static final int[][] SPIKEBALL_DATA = {
            {0x00, -0x18, 0x000, -0x300, 0x0F},   // Up
            {-0x10, -0x10, -0x200, -0x200, 0x10},  // Upper-left
            {-0x18, 0x00, -0x300, 0x000, 0x11},    // Left
            {-0x10, 0x10, -0x200, 0x200, 0x12},    // Lower-left
            {0x00, 0x18, 0x000, 0x300, 0x13},      // Down
            {0x10, 0x10, 0x200, 0x200, 0x14},      // Lower-right
            {0x18, 0x00, 0x300, 0x000, 0x15},       // Right
            {0x10, -0x10, 0x200, -0x200, 0x16}      // Upper-right
    };

    // Internal state
    private int actionTimer;
    private int attackIndex;  // objoff_2F (cycles through 16-entry table)
    private int attackSubRoutine;
    private int attackPhase;  // sub-phase within attack
    private int dashRepeatCount;
    private int defeatTimer;
    private int currentFrame;
    private boolean facingLeft;
    private boolean ballForm;
    private int walkAnimTimer;
    private int walkAnimFrame;
    private int ballAnimTimer;
    private int ballAnimFrame;
    private boolean spikeballsFired;
    /** ROM: objoff_2D - direction toggle, alternates via not.b each dash cycle */
    private boolean dashDirectionToggle;

    // Child component references (ROM: objoff_3A, objoff_3C, objoff_3E)
    private MechaSonicDEZWindow dezWindow;
    private MechaSonicTargetingSensor targetingSensor;
    private MechaSonicLEDWindow ledWindow;

    public Sonic2MechaSonicInstance(ObjectSpawn spawn, LevelManager levelManager) {
        super(spawn, levelManager, "DEZ Mecha Sonic");
    }

    @Override
    protected void initializeBossState() {
        // ROM: ObjAF_Init
        // Set radii: y=$1B, x=$10 (stored in y_radius, x_radius)
        // collision_flags = $00 initially (set to $1A when idle)
        // HP = 8

        state.routine = ROUTINE_INIT;
        state.routineSecondary = 0;
        attackIndex = 0;
        actionTimer = 0;
        defeatTimer = 0;
        currentFrame = FRAME_DESCEND;
        facingLeft = false;
        ballForm = false;
        walkAnimTimer = 0;
        walkAnimFrame = 0;
        ballAnimTimer = 0;
        ballAnimFrame = 0;
        spikeballsFired = false;
        dashDirectionToggle = false; // ROM: objoff_2D starts at 0

        // Advance to wait-for-camera routine immediately
        state.routine = ROUTINE_WAIT_CAMERA;

        // Spawn child objects
        spawnChildObjects();
    }

    private void spawnChildObjects() {
        if (levelManager.getObjectManager() == null) {
            return;
        }

        // ROM order from ObjAF_Init:
        // 1. LED Window (objoff_3E via ChildObject_39DC2): InheritParentXYFlip (copies parent pos)
        ledWindow = new MechaSonicLEDWindow(this);
        childComponents.add(ledWindow);
        levelManager.getObjectManager().addDynamicObject(ledWindow);

        // 2. Targeting Sensor (objoff_3C via ChildObject_39DC6): Follows at (+$C/-$C, -$C) offset
        targetingSensor = new MechaSonicTargetingSensor(this);
        childComponents.add(targetingSensor);
        levelManager.getObjectManager().addDynamicObject(targetingSensor);

        // 3. DEZ Window (objoff_3A via ChildObject_39DCA): Fixed at ($2C0, $139)
        dezWindow = new MechaSonicDEZWindow(this);
        childComponents.add(dezWindow);
        levelManager.getObjectManager().addDynamicObject(dezWindow);
    }

    @Override
    protected void updateBossLogic(int frameCounter, AbstractPlayableSprite player) {
        switch (state.routine) {
            case ROUTINE_WAIT_CAMERA -> updateWaitCamera();
            case ROUTINE_COUNTDOWN -> updateCountdown();
            case ROUTINE_DESCEND -> updateDescend(frameCounter);
            case ROUTINE_IDLE -> updateIdle();
            case ROUTINE_ATTACK -> updateAttack(player);
            case ROUTINE_DEFEAT -> updateDefeat(frameCounter);
        }
    }

    // ========================================================================
    // Routine 2: Wait for camera X >= $224, set boss_id, fade music
    // ROM: loc_397AC / loc_397BA
    // ========================================================================

    private void updateWaitCamera() {
        Camera camera = Camera.getInstance();
        if (camera.getX() >= CAMERA_LOCK_X) {
            // ROM: addq.b #2,routine(a0)
            state.routine = ROUTINE_COUNTDOWN;
            actionTimer = MUSIC_COUNTDOWN;
            // ROM: move.w #$100,y_vel(a0) - set constant descent velocity
            state.yVel = DESCENT_SPEED;
            // ROM: Lock camera min/max X to $224
            camera.setMinX((short) CAMERA_LOCK_X);
            camera.setMaxX((short) CAMERA_LOCK_X);
            // ROM: move.b #9,(Current_Boss_ID).w - set boss_id in the boss itself
            GameServices.gameState().setCurrentBossId(9);
            // ROM: moveq #signextendB(MusID_FadeOut),d0 - fade music
            AudioManager.getInstance().fadeOutMusic();
        }
    }

    // ========================================================================
    // Routine 4: 60-frame countdown, then play boss music
    // ROM: loc_397E6 / loc_397F0
    // ========================================================================

    private void updateCountdown() {
        actionTimer--;
        if (actionTimer < 0) {
            // ROM: bmi.s loc_397F0
            AudioManager.getInstance().playMusic(Sonic2Music.BOSS.id);
            state.routine = ROUTINE_DESCEND;
            currentFrame = FRAME_DESCEND;
        }
    }

    // ========================================================================
    // Routine 6: Descend at constant velocity $100 (no gravity)
    // ROM: loc_397FE - uses ObjectMove (not ObjectMoveAndFall)
    // ========================================================================

    private void updateDescend(int frameCounter) {
        // ROM: Play fire sound when (Vint_runcount & $1F) == 0
        if ((frameCounter & 0x1F) == 0) {
            AudioManager.getInstance().playSfx(Sonic2Sfx.FIRE.id);
        }

        // ROM: ObjCheckFloorDist checks floor distance
        // If d1 < 0 (touching/below floor), snap to floor
        if (state.y >= FLOOR_Y) {
            // ROM: loc_39830 - landed
            state.y = FLOOR_Y;
            state.yFixed = state.y << 16;
            state.yVel = 0;
            // ROM: move.b #$1A,collision_flags(a0)
            // ROM: bra.w loc_399D6 (transition to idle)
            transitionToIdle();
            return;
        }

        // ROM: ObjectMove - apply velocity at constant speed (no gravity added)
        state.applyVelocity();

        currentFrame = FRAME_DESCEND;
    }

    // ========================================================================
    // Routine 8: Idle on ground
    // ROM: loc_3984A
    // ========================================================================

    private void updateIdle() {
        currentFrame = FRAME_STAND;
        ballForm = false;

        actionTimer--;

        if (actionTimer == 0) {
            // ROM: beq.s loc_39886 - timer expired, start attack
            state.routine = ROUTINE_ATTACK;
            // ROM: read attack table
            attackSubRoutine = ATTACK_TABLE[attackIndex & 0x0F];
            attackIndex = (attackIndex + 1) & 0xFF; // ROM: addq.b #1
            attackPhase = 0;
            dashRepeatCount = 0;
            spikeballsFired = false;
            // ROM: movea.w objoff_3C(a0),a1; move.b #$16,routine(a1)
            // Enable targeting sensor collision
            if (targetingSensor != null) {
                targetingSensor.setCollisionEnabled(true);
            }
            return;
        }

        // Play buzz sound at 50 frames remaining
        // ROM: cmpi.b #$32,objoff_2A(a0)
        if (actionTimer == BUZZ_THRESHOLD) {
            AudioManager.getInstance().playSfx(Sonic2Sfx.MECHA_SONIC_BUZZ.id);
        }
    }

    /**
     * Transition to idle state (used after descent landing and attack completion).
     * ROM: loc_399D6
     */
    private void transitionToIdle() {
        state.routine = ROUTINE_IDLE;
        actionTimer = IDLE_DURATION;
        currentFrame = FRAME_STAND;
        ballForm = false;
        state.xVel = 0;
        // ROM: movea.w objoff_3C(a0),a1; move.b #$18,routine(a1)
        // Disable targeting sensor collision during idle
        if (targetingSensor != null) {
            targetingSensor.setCollisionEnabled(false);
        }
    }

    // ========================================================================
    // Routine A: Attack dispatch
    // ROM: loc_398C0
    // ========================================================================

    private void updateAttack(AbstractPlayableSprite player) {
        switch (attackSubRoutine) {
            case ATTACK_DASH_ACROSS -> updateDashAcross(player);
            case ATTACK_AIM_AND_DASH -> updateAimAndDash(player);
            case ATTACK_AIM_DASH_WALK -> updateAimDashWalk(player);
            case ATTACK_AIM_JUMP_SPIKEBALLS -> updateAimJumpSpikeballs(player);
        }
    }

    /**
     * Attack $00: Dash Across.
     * ROM: loc_3991E -> loc_39946 -> loc_39976
     * Wait $20 frames, dash at $800 speed, decelerate at $20/frame, repeat 2x.
     */
    private void updateDashAcross(AbstractPlayableSprite player) {
        switch (attackPhase) {
            case 0 -> {
                // Phase 0: Wait setup
                // ROM: loc_3991E - set mapping_frame=3, objoff_2C=2 (repeat count)
                actionTimer = AIM_HOLD_DURATION;
                attackPhase = 1;
                dashRepeatCount = 2; // ROM: move.b #2,objoff_2C(a0)
                currentFrame = FRAME_WALK_A;
                ballForm = false;
            }
            case 1 -> {
                // Phase 1: Wait timer
                // ROM: loc_39946
                actionTimer--;
                if (actionTimer < 0) {
                    attackPhase = 2;
                    actionTimer = 0x40; // ROM: move.b #$40,objoff_2A(a0)
                    startDash(DASH_SPEED);
                }
            }
            case 2 -> {
                // Phase 2: Dashing
                // ROM: loc_39976
                actionTimer--;
                if (actionTimer < 0) {
                    // Dash complete
                    dashRepeatCount--;
                    if (dashRepeatCount > 0) {
                        // ROM: loc_399C2 -> loc_3992E - repeat
                        attackPhase = 1;
                        actionTimer = AIM_HOLD_DURATION;
                        state.xVel = 0;
                    } else {
                        // ROM: loc_399D6 - finished
                        transitionToIdle();
                    }
                } else {
                    // Decelerate
                    applyDeceleration();
                    updateBallAnimation();
                }
            }
        }
    }

    /**
     * Attack $06: Aim & Dash.
     * ROM: loc_39A0A -> loc_39A1C -> loc_39A2A -> loc_39A44 -> loc_39A56 -> loc_39A68 -> loc_39A7C -> loc_39A96
     * Aim animation, laser beam (hold $20 frames), dash at $800, decelerate,
     * walk animation ending, then return to idle.
     */
    private void updateAimAndDash(AbstractPlayableSprite player) {
        switch (attackPhase) {
            case 0 -> {
                // Phase 0: Aim setup
                // ROM: loc_39A0A
                currentFrame = FRAME_WALK_A; // mapping_frame=3
                ballForm = false;
                attackPhase = 1;
            }
            case 1 -> {
                // Phase 1: Aim animation plays, wait for completion
                // ROM: loc_39A1C - AnimateSprite_Checked with anim 3
                // Simplified: advance immediately
                attackPhase = 2;
                actionTimer = AIM_HOLD_DURATION;
                currentFrame = FRAME_LASER; // anim 4 = laser
            }
            case 2 -> {
                // Phase 2: Laser hold
                // ROM: loc_39A44
                actionTimer--;
                if (actionTimer < 0) {
                    attackPhase = 3;
                    actionTimer = 0x40;
                    startDash(DASH_SPEED);
                }
            }
            case 3 -> {
                // Phase 3: Dashing
                // ROM: loc_39A68
                actionTimer--;
                if (actionTimer < 0) {
                    // ROM: loc_39A7C - stop, play walk-out anim
                    attackPhase = 4;
                    facingLeft = !facingLeft; // bchg x_flip
                    state.xVel = 0;
                    state.yVel = 0;
                    ballForm = false;
                } else {
                    applyDeceleration();
                    updateBallAnimation();
                }
            }
            case 4 -> {
                // Phase 4: Walk-out animation then idle
                // ROM: loc_39A96 - AnimateSprite_Checked with anim 5, then idle
                transitionToIdle();
            }
        }
    }

    /**
     * Attack $10: Aim, Dash, Walk.
     * ROM: loc_39A0A -> ... -> loc_39AAA -> loc_39ABC -> loc_39ACE -> loc_39AE8 -> loc_39AF4 -> ...
     * Same as Aim & Dash but with a jump at the end.
     */
    private void updateAimDashWalk(AbstractPlayableSprite player) {
        switch (attackPhase) {
            case 0 -> {
                // Phase 0: Aim setup (same as Aim & Dash)
                currentFrame = FRAME_WALK_A;
                ballForm = false;
                attackPhase = 1;
            }
            case 1 -> {
                // Phase 1: Aim animation
                attackPhase = 2;
                actionTimer = AIM_HOLD_DURATION;
                currentFrame = FRAME_LASER;
            }
            case 2 -> {
                // Phase 2: Laser hold
                actionTimer--;
                if (actionTimer < 0) {
                    attackPhase = 3;
                    actionTimer = 0x40;
                    startDash(SLOW_DASH_SPEED); // ROM: move.w #$400,d0
                }
            }
            case 3 -> {
                // Phase 3: Dashing at $400, then jump trigger
                // ROM: loc_39ACE
                actionTimer--;
                if (actionTimer == 60) {
                    // ROM: cmpi.b #60,objoff_2A(a0) -> bsr.w loc_39AE8
                    attackPhase = 4;
                    state.yVel = JUMP_Y_VEL;
                }
                updateBallAnimation();
            }
            case 4 -> {
                // Phase 4: In the air after jump
                // ROM: loc_39AF4
                actionTimer--;
                if (actionTimer < 0) {
                    // ROM: bmi.w loc_39A7C
                    attackPhase = 5;
                    facingLeft = !facingLeft;
                    state.xVel = 0;
                    state.yVel = 0;
                    ballForm = false;
                    return;
                }
                // ROM: ObjCheckFloorDist - check floor
                if (state.y >= FLOOR_Y) {
                    // ROM: loc_39B1A - landed
                    state.y = FLOOR_Y;
                    state.yFixed = state.y << 16;
                    state.yVel = 0;
                    // Continue in walk phase after landing
                }
                // ROM: addi.w #$38,y_vel(a0) - gravity
                state.yVel += GRAVITY;
                state.applyVelocity();
                // Floor snap
                if (state.y > FLOOR_Y) {
                    state.y = FLOOR_Y;
                    state.yFixed = state.y << 16;
                }
                updateBallAnimation();
            }
            case 5 -> {
                // Phase 5: Walk-out then idle
                transitionToIdle();
            }
        }
    }

    /**
     * Attack $1E: Aim, Jump + Spikeballs.
     * ROM: loc_39A0A -> ... -> loc_39AAA -> loc_39ABC -> loc_39ACE -> ... -> loc_39B44
     * Aim, laser, dash at $400, jump (y_vel=-$600, gravity $38),
     * fire 8 spikeballs at apex, land, walk-out.
     */
    private void updateAimJumpSpikeballs(AbstractPlayableSprite player) {
        switch (attackPhase) {
            case 0 -> {
                // Phase 0: Aim setup
                currentFrame = FRAME_WALK_A;
                ballForm = false;
                attackPhase = 1;
            }
            case 1 -> {
                // Phase 1: Aim animation
                attackPhase = 2;
                actionTimer = AIM_HOLD_DURATION;
                currentFrame = FRAME_LASER;
            }
            case 2 -> {
                // Phase 2: Laser hold
                actionTimer--;
                if (actionTimer < 0) {
                    attackPhase = 3;
                    actionTimer = 0x40;
                    startDash(SLOW_DASH_SPEED);
                }
            }
            case 3 -> {
                // Phase 3: Dashing at $400, then jump with spikeballs
                // ROM: loc_39ACE -> loc_39B44
                actionTimer--;
                if (actionTimer == 60) {
                    // ROM: bsr.w loc_39AE8 - set y_vel
                    state.yVel = JUMP_Y_VEL;
                    attackPhase = 4;
                    spikeballsFired = false;
                }
                updateBallAnimation();
            }
            case 4 -> {
                // Phase 4: In the air, fire spikeballs at apex
                // ROM: loc_39B44
                actionTimer--;
                if (actionTimer < 0) {
                    // ROM: bmi.w loc_39A7C - timer expired
                    attackPhase = 5;
                    facingLeft = !facingLeft;
                    state.xVel = 0;
                    state.yVel = 0;
                    ballForm = false;
                    return;
                }

                // Fire spikeballs at apex (when yVel crosses from negative to positive)
                // ROM: tst.w y_vel(a0) / bmi.s / st.b objoff_2E(a0) / bsr.w loc_39D82
                if (!spikeballsFired && state.yVel >= 0) {
                    spikeballsFired = true;
                    fireSpikeballs();
                    AudioManager.getInstance().playSfx(Sonic2Sfx.SPIKE_SWITCH.id);
                }

                // ROM: ObjCheckFloorDist
                if (state.y >= FLOOR_Y) {
                    // ROM: loc_39B84 - landed
                    state.y = FLOOR_Y;
                    state.yFixed = state.y << 16;
                    state.yVel = 0;
                }

                // ROM: addi.w #$38,y_vel(a0) - gravity
                state.yVel += GRAVITY;
                state.applyVelocity();
                // Floor snap
                if (state.y > FLOOR_Y) {
                    state.y = FLOOR_Y;
                    state.yFixed = state.y << 16;
                }
                updateBallAnimation();
            }
            case 5 -> {
                // Phase 5: Walk-out then idle
                transitionToIdle();
            }
        }
    }

    // ========================================================================
    // Attack helpers
    // ========================================================================

    /**
     * Start a dash using the ROM's direction toggle system.
     * ROM: loc_39D60 - uses objoff_2D toggle (not player-facing).
     * When objoff_2D is 0: negate speed (go left). Then toggle.
     * When objoff_2D is nonzero: keep positive (go right). Then toggle.
     */
    private void startDash(int speed) {
        ballForm = true;
        // ROM: loc_39D60
        int xVel = speed;
        if (!dashDirectionToggle) {
            xVel = -xVel; // ROM: neg.w d0
        }
        dashDirectionToggle = !dashDirectionToggle; // ROM: not.b objoff_2D(a0)
        state.xVel = xVel;
        facingLeft = (xVel < 0);
        ballAnimFrame = 0;
        ballAnimTimer = 0;
        currentFrame = FRAME_BALL_A;
        // ROM: moveq #signextendB(SndID_SpindashRelease),d0
        AudioManager.getInstance().playSfx(Sonic2Sfx.SPINDASH_RELEASE.id);
    }

    /**
     * Apply deceleration to x velocity.
     * ROM: loc_39D72
     */
    private void applyDeceleration() {
        if (state.xVel > 0) {
            state.xVel -= DECEL_RATE;
            if (state.xVel < 0) {
                state.xVel = 0;
            }
        } else if (state.xVel < 0) {
            state.xVel += DECEL_RATE;
            if (state.xVel > 0) {
                state.xVel = 0;
            }
        }
        state.applyVelocity();
    }

    private void fireSpikeballs() {
        if (levelManager.getObjectManager() == null) {
            return;
        }

        // Spawn 8 spikeballs with position offsets from ROM byte_39D92
        for (int i = 0; i < 8; i++) {
            int xOffset = SPIKEBALL_DATA[i][0];
            int yOffset = SPIKEBALL_DATA[i][1];
            int xVel = SPIKEBALL_DATA[i][2];
            int yVel = SPIKEBALL_DATA[i][3];
            int mappingFrame = SPIKEBALL_DATA[i][4];

            MechaSonicSpikeball spikeball = new MechaSonicSpikeball(
                    this,
                    state.x + xOffset, state.y + yOffset,
                    xVel, yVel, mappingFrame);
            levelManager.getObjectManager().addDynamicObject(spikeball);
        }
    }

    // ========================================================================
    // Animation helpers
    // ========================================================================

    private void updateBallAnimation() {
        ballAnimTimer++;
        if (ballAnimTimer >= 3) {
            ballAnimTimer = 0;
            ballAnimFrame = (ballAnimFrame + 1) % 3;
        }
        currentFrame = FRAME_BALL_A + ballAnimFrame;
        ballForm = true;
    }

    private void updateWalkAnimation() {
        walkAnimTimer++;
        if (walkAnimTimer >= 6) {
            walkAnimTimer = 0;
            walkAnimFrame = (walkAnimFrame + 1) % 3;
        }
        currentFrame = FRAME_WALK_A + walkAnimFrame;
    }

    // ========================================================================
    // Routine C: Defeat sequence
    // ROM: loc_39B92 / loc_39BA4
    // ========================================================================

    private void updateDefeat(int frameCounter) {
        // ROM: subq.w #1,objoff_32(a0) / bmi.s loc_39BA4
        defeatTimer--;

        if (defeatTimer < 0) {
            // ROM: loc_39BA4 - defeat complete
            // ROM: move.w #$1000,(Camera_Max_X_pos).w
            Camera camera = Camera.getInstance();
            camera.setMaxX((short) 0x1000);
            // ROM: addq.b #2,(Dynamic_Resize_Routine).w
            try {
                Sonic2LevelEventManager eventManager = Sonic2LevelEventManager.getInstance();
                int currentRoutine = eventManager.getEventRoutine();
                eventManager.setEventRoutine(currentRoutine + 2);
            } catch (Exception e) {
                // In tests, event manager may not be available
            }
            GameServices.gameState().setCurrentBossId(0);
            // ROM: move.w (Level_Music).w,d0 / PlayMusic (resume zone music)
            // Note: ROM has a bug where it reads a byte instead of word, causing
            // music to not resume. We use the fixBugs version.
            AudioManager.getInstance().playMusic(Sonic2Music.DEATH_EGG.id);
            // Delete self
            setDestroyed(true);
            return;
        }

        // ROM: Boss_LoadExplosion - spawn explosions
        if (defeatTimer % EXPLOSION_INTERVAL == 0) {
            spawnDefeatExplosion();
        }

        currentFrame = FRAME_DEFEATED;
    }

    // ========================================================================
    // Collision overrides
    // ROM: loc_39D1C / loc_39D24
    // ========================================================================

    @Override
    public int getCollisionFlags() {
        if (state.invulnerable || state.defeated) {
            return 0;
        }
        if (state.routine < ROUTINE_IDLE) {
            return 0; // No collision before idle phase
        }
        // ROM: loc_39D24 - check mapping_frame for ball frames
        // If mapping frame is 6, 7, or 8 -> $9A (ball collision)
        // Otherwise -> $1A (standing collision)
        if (currentFrame == FRAME_BALL_A || currentFrame == FRAME_BALL_B
                || currentFrame == FRAME_BALL_C) {
            return COLLISION_BALL;
        }
        return COLLISION_STANDING;
    }

    @Override
    protected int getCollisionSizeIndex() {
        return COLLISION_STANDING;
    }

    @Override
    protected int getInitialHitCount() {
        return DEFAULT_HIT_COUNT;
    }

    @Override
    protected void onHitTaken(int remainingHits) {
        // Update DEZ Window animation based on remaining hits
        if (dezWindow != null) {
            if (remainingHits <= 2) {
                dezWindow.setAnimId(4); // Panicked
            } else if (remainingHits <= 4) {
                dezWindow.setAnimId(3); // Worried
            }
        }
    }

    @Override
    protected int getInvulnerabilityDuration() {
        return INVULN_DURATION;
    }

    @Override
    protected boolean usesDefeatSequencer() {
        return false; // Custom defeat logic
    }

    @Override
    protected void onDefeatStarted() {
        state.routine = ROUTINE_DEFEAT;
        defeatTimer = DEFEAT_TIMER;
        state.xVel = 0;
        state.yVel = 0;
        ballForm = false;
        currentFrame = FRAME_DEFEATED;
        // ROM: bset #status.npc.misc,status(a0) - signals DEZ window
        // ROM: Delete targeting sensor and LED window
        if (targetingSensor != null) {
            targetingSensor.setDestroyed(true);
        }
        if (ledWindow != null) {
            ledWindow.setDestroyed(true);
        }
        // DEZ window transitions to closing animation
        if (dezWindow != null) {
            dezWindow.setAnimId(1); // Window closing
        }
    }

    @Override
    public int getPriorityBucket() {
        return 4;
    }

    // ========================================================================
    // Rendering
    // ========================================================================

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = levelManager.getObjectRenderManager();
        if (renderManager == null) {
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(
                Sonic2ObjectArtKeys.DEZ_SILVER_SONIC);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        renderer.drawFrameIndex(currentFrame, state.x, state.y, facingLeft, false);
    }

    // ========================================================================
    // Accessors for tests
    // ========================================================================

    public int getAttackIndex() {
        return attackIndex;
    }

    public int getCurrentRoutine() {
        return state.routine;
    }

    public boolean isBallForm() {
        return ballForm;
    }

    public int getDefeatTimer() {
        return defeatTimer;
    }

    public int getCurrentFrame() {
        return currentFrame;
    }

    public boolean isDashDirectionToggle() {
        return dashDirectionToggle;
    }

    public int getAttackSubRoutine() {
        return attackSubRoutine;
    }

    // ========================================================================
    // Child Objects
    // ========================================================================

    /**
     * DEZ Window child: Fixed at ($2C0, $139), shows Robotnik watching.
     * ROM: objoff_3A, routine $1A/$1C/$1E/$20/$22
     * Uses Ani_objAF_c animation script with 5 animations.
     */
    static class MechaSonicDEZWindow extends AbstractBossChild {
        private static final int WINDOW_X = 0x2C0;
        private static final int WINDOW_Y = 0x139;

        // Animation definitions from ROM Ani_objAF_c (byte_39E4C..byte_39E64)
        // Each animation: {speed, frame0, frame1, ..., terminator}
        // $FC = loop back to start, $FA = loop back 2 entries, $FF = hold last frame
        private static final int[][] WINDOW_ANIMS = {
                {3, 4, 3, 2, 1, 0},  // Anim 0: window opening (hold at end)
                {3, 0, 1, 2, 3, 4},  // Anim 1: window closing (loop)
                {3, 5, 5},           // Anim 2: Robotnik watching (loop)
                {3, 5, 6},           // Anim 3: Robotnik worried (loop)
                {3, 7, 7},           // Anim 4: Robotnik panicked (loop)
        };
        // Terminator types per animation
        private static final int[] WINDOW_ANIM_TERM = {
                0xFF, // Anim 0: hold last frame
                0xFC, // Anim 1: loop back to start (actually $FA = loop back 2, but same effect for small anims)
                0xFF, // Anim 2: loop
                0xFF, // Anim 3: loop
                0xFF, // Anim 4: loop
        };

        private int animId;
        private int animFrame;     // Index into WINDOW_ANIMS[animId]
        private int animTimer;
        private int mappingFrame;  // Actual displayed mapping frame

        MechaSonicDEZWindow(Sonic2MechaSonicInstance parent) {
            super(parent, "DEZ Window", 6, Sonic2ObjectIds.MECHA_SONIC);
            this.currentX = WINDOW_X;
            this.currentY = WINDOW_Y;
            // ROM: loc_39C12 - move.b #4,mapping_frame(a0) - start at frame 4 (closed window)
            this.animId = 0; // Will start opening when fight begins
            this.animFrame = 0;
            this.animTimer = 0;
            this.mappingFrame = 4; // Initial: closed window
        }

        void setAnimId(int newAnimId) {
            if (newAnimId != animId && newAnimId >= 0 && newAnimId < WINDOW_ANIMS.length) {
                animId = newAnimId;
                animFrame = 0;
                animTimer = 0;
                mappingFrame = WINDOW_ANIMS[animId][0 + 1]; // Skip speed byte (index 1)
            }
        }

        @Override
        public void update(int frameCounter, AbstractPlayableSprite player) {
            if (!beginUpdate(frameCounter)) {
                return;
            }

            // ROM: Ani_objAF_c animation system
            int[] anim = WINDOW_ANIMS[animId];
            int speed = anim[0];
            int frameCount = anim.length - 1; // Subtract speed byte

            animTimer++;
            if (animTimer > speed) {
                animTimer = 0;
                animFrame++;
                if (animFrame >= frameCount) {
                    // Loop or hold based on animation
                    if (animId == 0) {
                        // Anim 0 (opening): hold last frame, then switch to anim 2 (watching)
                        animFrame = frameCount - 1;
                        setAnimId(2);
                        return;
                    } else {
                        // All others loop
                        animFrame = 0;
                    }
                }
                mappingFrame = anim[animFrame + 1]; // +1 to skip speed byte
            }
            updateDynamicSpawn();
        }

        @Override
        public void syncPositionWithParent() {
            // Fixed position, does not follow parent
            this.currentX = WINDOW_X;
            this.currentY = WINDOW_Y;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            if (parent.isDestroyed()) {
                return;
            }
            ObjectRenderManager renderManager =
                    LevelManager.getInstance().getObjectRenderManager();
            if (renderManager == null) {
                return;
            }
            PatternSpriteRenderer renderer = renderManager.getRenderer(
                    Sonic2ObjectArtKeys.DEZ_WINDOW);
            if (renderer == null || !renderer.isReady()) {
                return;
            }
            renderer.drawFrameIndex(mappingFrame, currentX, currentY, false, false);
        }
    }

    /**
     * Targeting Sensor child: Toggles collision flags.
     * ROM: objoff_3C, routines $14/$16/$18
     * Follows parent at (+$C/-$C, -$C) offset.
     * Routine $16: collision_flags = 0 (disabled)
     * Routine $18: collision_flags = $98 (enabled, harmful)
     */
    static class MechaSonicTargetingSensor extends AbstractBossChild {
        private static final int X_OFFSET_RIGHT = 0x0C;
        private static final int X_OFFSET_LEFT = -0x0C;
        private static final int Y_OFFSET = -0x0C;
        private boolean collisionEnabled;

        MechaSonicTargetingSensor(Sonic2MechaSonicInstance parent) {
            super(parent, "Targeting Sensor", 4, Sonic2ObjectIds.MECHA_SONIC);
            this.collisionEnabled = false; // ROM: starts at routine $14, collision = 0
        }

        void setCollisionEnabled(boolean enabled) {
            this.collisionEnabled = enabled;
        }

        boolean isCollisionEnabled() {
            return collisionEnabled;
        }

        /**
         * Get collision flags for the targeting sensor.
         * ROM: routine $16 -> collision_flags = 0 (disabled)
         * ROM: routine $18 -> collision_flags = $98 (enabled, harmful)
         */
        public int getCollisionFlags() {
            return collisionEnabled ? 0x98 : 0x00;
        }

        @Override
        public void update(int frameCounter, AbstractPlayableSprite player) {
            if (!beginUpdate(frameCounter)) {
                return;
            }
            syncPositionWithParent();
            updateDynamicSpawn();
        }

        @Override
        public void syncPositionWithParent() {
            // ROM: loc_39D4A - Obj_AlignChildXY with d0=$C, d1=-$C
            // Targeting sensor follows parent at (+$C/-$C, -$C) offset
            if (parent != null && !parent.isDestroyed()) {
                Sonic2MechaSonicInstance mechParent = (Sonic2MechaSonicInstance) parent;
                int xOff = mechParent.facingLeft ? X_OFFSET_LEFT : X_OFFSET_RIGHT;
                this.currentX = parent.getX() + xOff;
                this.currentY = parent.getY() + Y_OFFSET;
            }
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            // Invisible object (no rendering)
        }
    }

    /**
     * LED Window child: Follows parent position directly (InheritParentXYFlip).
     * ROM: objoff_3E, routine $10
     * Uses off_39E30 animation script with 3 LED animations.
     */
    static class MechaSonicLEDWindow extends AbstractBossChild {
        // LED animation definitions from ROM off_39E30
        // byte_39E36: anim 0 - speed 1, frames $B,$C loop (normal LED blink)
        // byte_39E3A: anim 1 - speed 1, frames $D,$E loop (targeting active)
        // byte_39E3E: anim 2 - speed 1, frames $9,$A loop (dash active)
        private static final int[][] LED_ANIMS = {
                {1, 0x0B, 0x0C},  // Anim 0: normal LED blink
                {1, 0x0D, 0x0E},  // Anim 1: targeting active
                {1, 0x09, 0x0A},  // Anim 2: dash active
        };

        private int animId;
        private int animFrame;
        private int animTimer;
        private int mappingFrame;

        MechaSonicLEDWindow(Sonic2MechaSonicInstance parent) {
            super(parent, "LED Window", 3, Sonic2ObjectIds.MECHA_SONIC);
            this.animId = 0;
            this.animFrame = 0;
            this.animTimer = 0;
            // ROM: loc_39BEA - move.b #$B,mapping_frame(a0)
            this.mappingFrame = 0x0B;
        }

        void setAnimId(int newAnimId) {
            if (newAnimId != animId && newAnimId >= 0 && newAnimId < LED_ANIMS.length) {
                animId = newAnimId;
                animFrame = 0;
                animTimer = 0;
                mappingFrame = LED_ANIMS[animId][1]; // First frame (skip speed byte)
            }
        }

        @Override
        public void update(int frameCounter, AbstractPlayableSprite player) {
            if (!beginUpdate(frameCounter)) {
                return;
            }
            syncPositionWithParent();

            // ROM: off_39E30 animation system
            int[] anim = LED_ANIMS[animId];
            int speed = anim[0];
            int frameCount = anim.length - 1; // Subtract speed byte

            animTimer++;
            if (animTimer > speed) {
                animTimer = 0;
                animFrame = (animFrame + 1) % frameCount;
                mappingFrame = anim[animFrame + 1]; // +1 to skip speed byte
            }

            // Update LED anim based on parent attack state
            Sonic2MechaSonicInstance mechParent = (Sonic2MechaSonicInstance) parent;
            if (mechParent.state.routine == ROUTINE_ATTACK) {
                if (mechParent.ballForm) {
                    setAnimId(2); // Dash active
                } else {
                    setAnimId(1); // Targeting active
                }
            } else {
                setAnimId(0); // Normal blink
            }

            updateDynamicSpawn();
        }

        @Override
        public void syncPositionWithParent() {
            // ROM: loc_39BCC - InheritParentXYFlip (copies parent position directly)
            if (parent != null && !parent.isDestroyed()) {
                this.currentX = parent.getX();
                this.currentY = parent.getY();
            }
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            if (parent.isDestroyed()) {
                return;
            }
            ObjectRenderManager renderManager =
                    LevelManager.getInstance().getObjectRenderManager();
            if (renderManager == null) {
                return;
            }
            PatternSpriteRenderer renderer = renderManager.getRenderer(
                    Sonic2ObjectArtKeys.DEZ_SILVER_SONIC);
            if (renderer == null || !renderer.isReady()) {
                return;
            }
            Sonic2MechaSonicInstance mechParent = (Sonic2MechaSonicInstance) parent;
            renderer.drawFrameIndex(mappingFrame, currentX, currentY,
                    mechParent.facingLeft, false);
        }
    }

    /**
     * Spikeball projectile fired by Mecha Sonic.
     * Uses mapping frames $0F-$16 from the Silver Sonic sheet.
     * Moves in a fixed direction with no gravity.
     */
    static class MechaSonicSpikeball extends AbstractBossChild {
        private final int xVel;
        private final int yVel;
        private final int mappingFrame;
        private int xFixed;
        private int yFixed;
        private int lifetime;

        MechaSonicSpikeball(Sonic2MechaSonicInstance parent,
                            int startX, int startY,
                            int xVel, int yVel, int mappingFrame) {
            super(parent, "Spikeball", 4, Sonic2ObjectIds.MECHA_SONIC);
            this.currentX = startX;
            this.currentY = startY;
            this.xFixed = startX << 16;
            this.yFixed = startY << 16;
            this.xVel = xVel;
            this.yVel = yVel;
            this.mappingFrame = mappingFrame;
            this.lifetime = 0;
        }

        @Override
        public void update(int frameCounter, AbstractPlayableSprite player) {
            if (!beginUpdate(frameCounter)) {
                return;
            }
            // Move in fixed direction (whole pixel velocities)
            xFixed += (xVel << 8);
            yFixed += (yVel << 8);
            currentX = xFixed >> 16;
            currentY = yFixed >> 16;
            updateDynamicSpawn();

            lifetime++;
            // Delete after going off screen (roughly 120 frames)
            if (lifetime > 120) {
                setDestroyed(true);
            }
        }

        @Override
        public void syncPositionWithParent() {
            // Spikeballs move independently, don't follow parent
        }

        @Override
        public boolean isDestroyed() {
            // Don't inherit parent destruction - spikeballs persist after boss defeat
            return super.isDestroyed();
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            ObjectRenderManager renderManager =
                    LevelManager.getInstance().getObjectRenderManager();
            if (renderManager == null) {
                return;
            }
            PatternSpriteRenderer renderer = renderManager.getRenderer(
                    Sonic2ObjectArtKeys.DEZ_SILVER_SONIC);
            if (renderer == null || !renderer.isReady()) {
                return;
            }
            renderer.drawFrameIndex(mappingFrame, currentX, currentY, false, false);
        }
    }
}
