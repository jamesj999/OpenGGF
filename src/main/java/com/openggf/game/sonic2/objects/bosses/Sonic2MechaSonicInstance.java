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
 * DEZ Silver Sonic / Mecha Sonic (Object 0xAF).
 * ROM Reference: s2.asm ObjAF (Silver Sonic)
 *
 * First boss of Death Egg Zone. A robotic hedgehog that performs dashing,
 * jumping, and spikeball attacks in a circular pattern of 16 attack slots.
 *
 * State Machine (main routine):
 * - 0: Init (set radii, spawn children, HP=8, boss_id=9)
 * - 2: Wait for Camera_X >= $224, lock camera
 * - 4: 60-frame countdown, then play boss music
 * - 6: Descend with gravity, fire sound every 32 frames
 * - 8: Idle on ground (100-frame timer), buzz at 50 remaining
 * - A: Attack dispatch (reads from 16-entry attack table)
 * - C: Defeat (255 frames of explosions, then unlock camera)
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
    private static final int ATTACK_AIM_DASH_JUMP = 0x10;
    private static final int ATTACK_AIM_JUMP_SPIKEBALLS = 0x1E;

    // Attack pattern table (16 entries, circular via & 0x0F)
    // ROM: ObjAF_AttackTable
    private static final int[] ATTACK_TABLE = {
            0x06, 0x00, 0x10, 0x06, 0x06, 0x1E, 0x00, 0x10,
            0x06, 0x06, 0x10, 0x06, 0x00, 0x06, 0x10, 0x1E
    };

    // Position constants
    /** Floor Y position (ROM: cmpi.w #$1B0,y_pos(a0)) */
    private static final int FLOOR_Y = 0x1B0;
    /** Camera lock X position (ROM: $224) */
    private static final int CAMERA_LOCK_X = 0x224;

    // Velocity constants (8.8 fixed-point)
    /** Dash speed (ROM: move.w #$800,x_vel(a0)) */
    private static final int DASH_SPEED = 0x800;
    /** Slow dash speed (ROM: move.w #$400,x_vel(a0)) */
    private static final int SLOW_DASH_SPEED = 0x400;
    /** Deceleration rate per frame (ROM: $20) */
    private static final int DECEL_RATE = 0x20;
    /** Jump Y velocity (ROM: move.w #-$600,y_vel(a0)) */
    private static final int JUMP_Y_VEL = -0x600;

    // Timing constants
    /** Idle timer duration (ROM: move.w #$64,objoff_2E(a0)) */
    private static final int IDLE_DURATION = 0x64;
    /** Countdown for boss music (60 frames) */
    private static final int MUSIC_COUNTDOWN = 60;
    /** Aim hold duration (ROM: $20 frames) */
    private static final int AIM_HOLD_DURATION = 0x20;
    /** Invulnerability duration (ROM: move.b #$20,boss_invulnerable_time) */
    private static final int INVULN_DURATION = 0x20;
    /** Defeat explosion timer (ROM: move.w #$FF,objoff_3C(a0)) */
    private static final int DEFEAT_TIMER = 0xFF;
    /** Fire sound interval during descent (every 32 frames) */
    private static final int FIRE_SOUND_INTERVAL = 32;
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

    // Spikeball velocity table (8 directions, whole pixels -> 8.8 fixed-point)
    // ROM order: Up, UpperLeft, Left, LowerLeft, Down, LowerRight, Right, UpperRight
    private static final int[][] SPIKEBALL_VELOCITIES = {
            {0x000, -0x300},   // Up: (0, -3)
            {-0x200, -0x200},  // Upper-left: (-2, -2)
            {-0x300, 0x000},   // Left: (-3, 0)
            {-0x200, 0x200},   // Lower-left: (-2, 2)
            {0x000, 0x300},    // Down: (0, 3)
            {0x200, 0x200},    // Lower-right: (2, 2)
            {0x300, 0x000},    // Right: (3, 0)
            {0x200, -0x200}    // Upper-right: (2, -2)
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
        // HP = 8, boss_id = 9

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

        // Advance to wait-for-camera routine immediately
        state.routine = ROUTINE_WAIT_CAMERA;

        // Spawn child objects
        spawnChildObjects();
    }

    private void spawnChildObjects() {
        if (levelManager.getObjectManager() == null) {
            return;
        }

        // 1. DEZ Window (objoff_3A): Fixed at ($2C0, $139), shows Robotnik watching
        dezWindow = new MechaSonicDEZWindow(this);
        childComponents.add(dezWindow);
        levelManager.getObjectManager().addDynamicObject(dezWindow);

        // 2. Targeting Sensor (objoff_3C): Toggles collision
        targetingSensor = new MechaSonicTargetingSensor(this);
        childComponents.add(targetingSensor);
        levelManager.getObjectManager().addDynamicObject(targetingSensor);

        // 3. LED Window (objoff_3E): Follows parent with offset, LED animations
        ledWindow = new MechaSonicLEDWindow(this);
        childComponents.add(ledWindow);
        levelManager.getObjectManager().addDynamicObject(ledWindow);
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
    // Routine 2: Wait for camera X >= $224
    // ========================================================================

    private void updateWaitCamera() {
        Camera camera = Camera.getInstance();
        if (camera.getX() >= CAMERA_LOCK_X) {
            // Lock camera min/max X
            camera.setMinX((short) CAMERA_LOCK_X);
            camera.setMaxX((short) CAMERA_LOCK_X);
            state.routine = ROUTINE_COUNTDOWN;
            actionTimer = MUSIC_COUNTDOWN;
        }
    }

    // ========================================================================
    // Routine 4: 60-frame countdown, then play boss music
    // ========================================================================

    private void updateCountdown() {
        actionTimer--;
        if (actionTimer <= 0) {
            AudioManager.getInstance().playMusic(Sonic2Music.BOSS.id);
            state.routine = ROUTINE_DESCEND;
            // Set initial gravity descent
            state.yVel = 0;
            currentFrame = FRAME_DESCEND;
        }
    }

    // ========================================================================
    // Routine 6: Descend with gravity
    // ========================================================================

    private void updateDescend(int frameCounter) {
        // Apply gravity
        state.yVel += GRAVITY;
        state.applyVelocity();

        // Play fire sound every 32 frames
        if ((frameCounter % FIRE_SOUND_INTERVAL) == 0) {
            AudioManager.getInstance().playSfx(Sonic2Sfx.FIRE.id);
        }

        currentFrame = FRAME_DESCEND;

        // Check for floor contact
        if (state.y >= FLOOR_Y) {
            state.y = FLOOR_Y;
            state.yFixed = state.y << 16;
            state.yVel = 0;
            state.routine = ROUTINE_IDLE;
            actionTimer = IDLE_DURATION;
            currentFrame = FRAME_STAND;
            ballForm = false;
        }
    }

    // ========================================================================
    // Routine 8: Idle on ground
    // ========================================================================

    private void updateIdle() {
        currentFrame = FRAME_STAND;
        ballForm = false;

        actionTimer--;

        // Play buzz sound at 50 frames remaining
        if (actionTimer == BUZZ_THRESHOLD) {
            AudioManager.getInstance().playSfx(Sonic2Sfx.MECHA_SONIC_BUZZ.id);
        }

        if (actionTimer <= 0) {
            // Start attack dispatch
            state.routine = ROUTINE_ATTACK;
            attackSubRoutine = ATTACK_TABLE[attackIndex & 0x0F];
            attackIndex = (attackIndex + 1) & 0x0F;
            attackPhase = 0;
            dashRepeatCount = 0;
            spikeballsFired = false;
        }
    }

    // ========================================================================
    // Routine A: Attack dispatch
    // ========================================================================

    private void updateAttack(AbstractPlayableSprite player) {
        switch (attackSubRoutine) {
            case ATTACK_DASH_ACROSS -> updateDashAcross(player);
            case ATTACK_AIM_AND_DASH -> updateAimAndDash(player);
            case ATTACK_AIM_DASH_JUMP -> updateAimDashJump(player);
            case ATTACK_AIM_JUMP_SPIKEBALLS -> updateAimJumpSpikeballs(player);
        }
    }

    /**
     * Attack $00: Dash Across.
     * Wait $20 frames, dash at $800 speed, decelerate at $20/frame, repeat 2x.
     */
    private void updateDashAcross(AbstractPlayableSprite player) {
        switch (attackPhase) {
            case 0 -> {
                // Phase 0: Wait
                actionTimer = AIM_HOLD_DURATION;
                attackPhase = 1;
                facePlayer(player);
                currentFrame = FRAME_STAND;
                ballForm = false;
            }
            case 1 -> {
                // Phase 1: Wait timer
                actionTimer--;
                if (actionTimer <= 0) {
                    attackPhase = 2;
                    startDash();
                }
            }
            case 2 -> {
                // Phase 2: Dashing
                updateBallAnimation();
                state.applyVelocity();
                // Decelerate
                if (state.xVel > 0) {
                    state.xVel -= DECEL_RATE;
                    if (state.xVel <= 0) {
                        state.xVel = 0;
                        dashComplete();
                    }
                } else if (state.xVel < 0) {
                    state.xVel += DECEL_RATE;
                    if (state.xVel >= 0) {
                        state.xVel = 0;
                        dashComplete();
                    }
                }
            }
        }
    }

    /**
     * Attack $06: Aim & Dash.
     * Aim animation, laser beam (hold $20 frames), dash at $800, decelerate, floor-snap, stop.
     */
    private void updateAimAndDash(AbstractPlayableSprite player) {
        switch (attackPhase) {
            case 0 -> {
                // Phase 0: Aim
                facePlayer(player);
                currentFrame = FRAME_AIM;
                ballForm = false;
                actionTimer = AIM_HOLD_DURATION;
                attackPhase = 1;
            }
            case 1 -> {
                // Phase 1: Laser hold
                currentFrame = FRAME_LASER;
                actionTimer--;
                if (actionTimer <= 0) {
                    attackPhase = 2;
                    startDash();
                }
            }
            case 2 -> {
                // Phase 2: Dashing
                updateBallAnimation();
                state.applyVelocity();
                // Decelerate
                if (state.xVel > 0) {
                    state.xVel -= DECEL_RATE;
                    if (state.xVel <= 0) {
                        state.xVel = 0;
                        finishAttack();
                    }
                } else if (state.xVel < 0) {
                    state.xVel += DECEL_RATE;
                    if (state.xVel >= 0) {
                        state.xVel = 0;
                        finishAttack();
                    }
                }
                // Floor snap
                if (state.y > FLOOR_Y) {
                    state.y = FLOOR_Y;
                    state.yFixed = state.y << 16;
                }
            }
        }
    }

    /**
     * Attack $10: Aim, Dash, Walk.
     * Same as Aim & Dash but walks on ground after decelerating.
     */
    private void updateAimDashJump(AbstractPlayableSprite player) {
        switch (attackPhase) {
            case 0 -> {
                // Phase 0: Aim
                facePlayer(player);
                currentFrame = FRAME_AIM;
                ballForm = false;
                actionTimer = AIM_HOLD_DURATION;
                attackPhase = 1;
            }
            case 1 -> {
                // Phase 1: Laser hold
                currentFrame = FRAME_LASER;
                actionTimer--;
                if (actionTimer <= 0) {
                    attackPhase = 2;
                    startDash();
                }
            }
            case 2 -> {
                // Phase 2: Dashing
                updateBallAnimation();
                state.applyVelocity();
                // Decelerate
                if (state.xVel > 0) {
                    state.xVel -= DECEL_RATE;
                    if (state.xVel <= 0) {
                        state.xVel = 0;
                        attackPhase = 3;
                        actionTimer = IDLE_DURATION;
                        ballForm = false;
                    }
                } else if (state.xVel < 0) {
                    state.xVel += DECEL_RATE;
                    if (state.xVel >= 0) {
                        state.xVel = 0;
                        attackPhase = 3;
                        actionTimer = IDLE_DURATION;
                        ballForm = false;
                    }
                }
                // Floor snap
                if (state.y > FLOOR_Y) {
                    state.y = FLOOR_Y;
                    state.yFixed = state.y << 16;
                }
            }
            case 3 -> {
                // Phase 3: Walk on ground
                updateWalkAnimation();
                // Walk towards center of arena
                int centerX = state.x;
                Camera camera = Camera.getInstance();
                int arenaCenter = camera.getX() + 160;
                if (centerX < arenaCenter) {
                    state.xVel = 0x100; // Walk right
                    facingLeft = false;
                } else {
                    state.xVel = -0x100; // Walk left
                    facingLeft = true;
                }
                state.applyVelocity();
                actionTimer--;
                if (actionTimer <= 0) {
                    state.xVel = 0;
                    finishAttack();
                }
            }
        }
    }

    /**
     * Attack $1E: Aim, Jump + Spikeballs.
     * Aim, laser, dash at $400, jump (y_vel=-$600, gravity $38),
     * fire 8 spikeballs at apex, land.
     */
    private void updateAimJumpSpikeballs(AbstractPlayableSprite player) {
        switch (attackPhase) {
            case 0 -> {
                // Phase 0: Aim
                facePlayer(player);
                currentFrame = FRAME_AIM;
                ballForm = false;
                actionTimer = AIM_HOLD_DURATION;
                attackPhase = 1;
            }
            case 1 -> {
                // Phase 1: Laser hold
                currentFrame = FRAME_LASER;
                actionTimer--;
                if (actionTimer <= 0) {
                    attackPhase = 2;
                    // Slower dash
                    ballForm = true;
                    state.xVel = facingLeft ? -SLOW_DASH_SPEED : SLOW_DASH_SPEED;
                    currentFrame = FRAME_BALL_A;
                }
            }
            case 2 -> {
                // Phase 2: Dash then jump
                updateBallAnimation();
                state.applyVelocity();
                // After a few frames, jump
                actionTimer++;
                if (actionTimer >= 8) {
                    attackPhase = 3;
                    state.yVel = JUMP_Y_VEL;
                    spikeballsFired = false;
                }
            }
            case 3 -> {
                // Phase 3: In the air
                state.yVel += GRAVITY;
                state.applyVelocity();
                currentFrame = FRAME_JUMP_ARC;
                ballForm = false;

                // Fire spikeballs at apex (when yVel crosses 0)
                if (!spikeballsFired && state.yVel >= 0) {
                    fireSpikeballs();
                    spikeballsFired = true;
                }

                // Land
                if (state.y >= FLOOR_Y) {
                    state.y = FLOOR_Y;
                    state.yFixed = state.y << 16;
                    state.yVel = 0;
                    state.xVel = 0;
                    finishAttack();
                }
            }
        }
    }

    // ========================================================================
    // Attack helpers
    // ========================================================================

    private void facePlayer(AbstractPlayableSprite player) {
        if (player != null) {
            facingLeft = player.getCentreX() < state.x;
        }
    }

    private void startDash() {
        ballForm = true;
        state.xVel = facingLeft ? -DASH_SPEED : DASH_SPEED;
        ballAnimFrame = 0;
        ballAnimTimer = 0;
        currentFrame = FRAME_BALL_A;
    }

    private void dashComplete() {
        dashRepeatCount++;
        if (dashRepeatCount < 2) {
            // Repeat dash in opposite direction
            facingLeft = !facingLeft;
            startDash();
        } else {
            finishAttack();
        }
    }

    private void finishAttack() {
        state.routine = ROUTINE_IDLE;
        actionTimer = IDLE_DURATION;
        currentFrame = FRAME_STAND;
        ballForm = false;
        state.xVel = 0;
        state.yVel = 0;
    }

    private void fireSpikeballs() {
        if (levelManager.getObjectManager() == null) {
            return;
        }

        // Spawn 8 spikeballs in all directions
        for (int i = 0; i < 8; i++) {
            int xVel = SPIKEBALL_VELOCITIES[i][0];
            int yVel = SPIKEBALL_VELOCITIES[i][1];
            int mappingFrame = 0x0F + i; // Frames $0F-$16

            MechaSonicSpikeball spikeball = new MechaSonicSpikeball(
                    this, state.x, state.y, xVel, yVel, mappingFrame);
            levelManager.getObjectManager().addDynamicObject(spikeball);
        }

        AudioManager.getInstance().playSfx(Sonic2Sfx.FIRE.id);
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
    // ========================================================================

    private void updateDefeat(int frameCounter) {
        defeatTimer--;

        // Spawn explosions every 8 frames
        if (defeatTimer >= 0 && defeatTimer % EXPLOSION_INTERVAL == 0) {
            spawnDefeatExplosion();
        }

        if (defeatTimer <= 0) {
            // Unlock camera
            Camera camera = Camera.getInstance();
            camera.setMaxX((short) 0x1000);
            // Advance DEZ events (Dynamic_Resize_Routine += 2)
            GameServices.gameState().setCurrentBossId(0);
            // Resume music
            AudioManager.getInstance().playMusic(Sonic2Music.DEATH_EGG.id);
            // Delete self
            setDestroyed(true);
        }

        currentFrame = FRAME_DEFEATED;
    }

    // ========================================================================
    // Collision overrides
    // ========================================================================

    @Override
    public int getCollisionFlags() {
        if (state.invulnerable || state.defeated) {
            return 0;
        }
        if (state.routine < ROUTINE_IDLE) {
            return 0; // No collision before idle phase
        }
        // Ball form uses enemy-style collision ($9A), standing uses boss type ($1A)
        if (ballForm) {
            return COLLISION_BALL;
        }
        return 0xC0 | (getCollisionSizeIndex() & 0x3F);
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
        // No special action on hit
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

    // ========================================================================
    // Child Objects
    // ========================================================================

    /**
     * DEZ Window child: Fixed at ($2C0, $139), shows Robotnik watching.
     * ROM: objoff_3A
     */
    static class MechaSonicDEZWindow extends AbstractBossChild {
        private static final int WINDOW_X = 0x2C0;
        private static final int WINDOW_Y = 0x139;
        private int animFrame;
        private int animTimer;

        MechaSonicDEZWindow(Sonic2MechaSonicInstance parent) {
            super(parent, "DEZ Window", 3, Sonic2ObjectIds.MECHA_SONIC);
            this.currentX = WINDOW_X;
            this.currentY = WINDOW_Y;
            this.animFrame = 0;
            this.animTimer = 0;
        }

        @Override
        public void update(int frameCounter, AbstractPlayableSprite player) {
            if (!beginUpdate(frameCounter)) {
                return;
            }
            // Simple LED animation cycling
            animTimer++;
            if (animTimer >= 8) {
                animTimer = 0;
                animFrame = (animFrame + 1) % 8;
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
            renderer.drawFrameIndex(animFrame, currentX, currentY, false, false);
        }
    }

    /**
     * Targeting Sensor child: Toggles collision flags.
     * ROM: objoff_3C
     * When boss is attacking: collision $98 (harmful)
     * Otherwise: collision $00 (harmless)
     */
    static class MechaSonicTargetingSensor extends AbstractBossChild {
        MechaSonicTargetingSensor(Sonic2MechaSonicInstance parent) {
            super(parent, "Targeting Sensor", 4, Sonic2ObjectIds.MECHA_SONIC);
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
        public void appendRenderCommands(List<GLCommand> commands) {
            // Invisible object (no rendering)
        }
    }

    /**
     * LED Window child: Follows parent at offset, shows LED animations.
     * ROM: objoff_3E - follows parent at (+$C/-$C, -$C)
     */
    static class MechaSonicLEDWindow extends AbstractBossChild {
        private static final int X_OFFSET_RIGHT = 0x0C;
        private static final int X_OFFSET_LEFT = -0x0C;
        private static final int Y_OFFSET = -0x0C;
        private int ledFrame;
        private int ledTimer;

        MechaSonicLEDWindow(Sonic2MechaSonicInstance parent) {
            super(parent, "LED Window", 3, Sonic2ObjectIds.MECHA_SONIC);
            this.ledFrame = 0;
            this.ledTimer = 0;
        }

        @Override
        public void update(int frameCounter, AbstractPlayableSprite player) {
            if (!beginUpdate(frameCounter)) {
                return;
            }
            syncPositionWithParent();

            // LED animation cycling
            ledTimer++;
            if (ledTimer >= 6) {
                ledTimer = 0;
                ledFrame = (ledFrame + 1) % 4;
            }
            updateDynamicSpawn();
        }

        @Override
        public void syncPositionWithParent() {
            if (parent != null && !parent.isDestroyed()) {
                Sonic2MechaSonicInstance mechParent = (Sonic2MechaSonicInstance) parent;
                int xOff = mechParent.facingLeft ? X_OFFSET_LEFT : X_OFFSET_RIGHT;
                this.currentX = parent.getX() + xOff;
                this.currentY = parent.getY() + Y_OFFSET;
            }
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            if (parent.isDestroyed()) {
                return;
            }
            // LED renders as part of main Silver Sonic sheet
            // Uses same renderer but specific LED-related frames
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
            // The LED window is a visual overlay - render at offset position
            // Uses same art as main sprite with a specific sub-frame
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
