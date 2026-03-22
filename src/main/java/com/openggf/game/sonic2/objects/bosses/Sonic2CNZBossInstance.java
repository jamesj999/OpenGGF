package com.openggf.game.sonic2.objects.bosses;

import com.openggf.camera.Camera;
import com.openggf.game.sonic2.audio.Sonic2Music;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic2.audio.Sonic2Sfx;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.game.sonic2.events.Sonic2CNZEvents;
import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.boss.AbstractBossInstance;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Casino Night Zone Boss (Object 0x51) - Eggman's electricity generator boss.
 * <p>
 * ROM Reference: s2.asm:65911 (Obj51)
 * <p>
 * This boss is a multi-sprite object consisting of:
 * - Main body (Eggpod with Eggman)
 * - Two electricity generators
 * - Electricity field between generators
 * - Spawns electric ball projectile child objects
 * <p>
 * State machine (boss_routine):
 * - 0: Horizontal patrol with attack triggers
 * - 2: Post-trigger timer (ball spawned)
 * - 4: Vertical drop attack
 * - 6: Defeat exploding
 * - 8: Bounce/settle after defeat
 * - A: Flee off-screen
 */
public class Sonic2CNZBossInstance extends AbstractBossInstance {

    // Boss routine states (ROM: boss_routine)
    private static final int ROUTINE_PATROL = 0x00;
    private static final int ROUTINE_POST_TRIGGER = 0x02;
    private static final int ROUTINE_VERTICAL_DROP = 0x04;
    private static final int ROUTINE_DEFEAT_EXPLODE = 0x06;
    private static final int ROUTINE_DEFEAT_BOUNCE = 0x08;
    private static final int ROUTINE_FLEE = 0x0A;

    // Position constants (ROM)
    private static final int INITIAL_X = 0x2A46;
    private static final int INITIAL_Y = 0x654;
    private static final int BOUNDARY_LEFT = 0x28C0;
    private static final int BOUNDARY_RIGHT = 0x29C0;
    private static final int DROP_TARGET_Y = 0x680;
    private static final int CAMERA_MAX_X_TARGET = 0x2B20;

    // Velocity constants (8.8 fixed-point)
    private static final int VELOCITY_HORIZONTAL = 0x180;
    private static final int VELOCITY_DROP = 0x180;
    private static final int VELOCITY_FLEE_X = 0x400;
    private static final int VELOCITY_FLEE_Y = -0x40;

    // Timing constants
    private static final int CNZ_INVULNERABLE_DURATION = 48;    // ROM: 48 frames (not default 32)
    private static final int COOLDOWN_DURATION = 0x40;          // 64 frames
    private static final int TRIGGER_COUNTDOWN = 0x50;          // 80 frames

    // Collision routine states (ROM: Boss_CollisionRoutine)
    private static final int COLLISION_OFF = 0;
    private static final int COLLISION_LOWER = 1;   // Lower hurtbox (electrode tips)
    private static final int COLLISION_ZAP = 2;     // Full zap field

    // Player Y thresholds for triggering attacks
    private static final int PLAYER_Y_BALL_TRIGGER = 0x6B0;
    private static final int PLAYER_Y_DROP_TRIGGER = 0x67C;

    // Generator Y positions during defeat fall-off
    private static final int GENERATOR_FLOOR_Y = 0x6F0;

    // Mapping frame indices (Java 0-indexed, ROM is 1-indexed with entry 0 as null)
    // ROM frame N = Java frame N-1 for frames 1+
    // The main boss body is ALWAYS rendered as frame 0 (Eggman + machine)
    // Electricity frames are ADDITIONAL overlays when in collision mode
    private static final int FRAME_BOSS_BODY = 0;           // Main boss with Eggman (always rendered)
    private static final int FRAME_GENERATOR_LEFT = 1;      // Left generator arm
    private static final int FRAME_GENERATOR_RIGHT = 2;     // Right generator arm
    private static final int FRAME_ELECTRODE_SMALL = 3;     // Electrode piece
    private static final int FRAME_ELECTRODE_EXTENDED = 4;  // Electrode extended
    private static final int FRAME_PROPELLER_1 = 5;         // Propeller frame 1
    private static final int FRAME_PROPELLER_2 = 6;         // Propeller frame 2
    // Electricity frames (these are overlays, not replacements)
    private static final int FRAME_ELEC_LOWER_1 = 11;       // Lower electricity frame 1 (ROM 0x0C)
    private static final int FRAME_ELEC_LOWER_2 = 12;       // Lower electricity frame 2 (ROM 0x0D)
    private static final int FRAME_ELEC_LOWER_3 = 13;       // Lower electricity frame 3 (ROM 0x0E)
    private static final int FRAME_ZAP_WIDE_1 = 14;         // Zap field wide frame 1 (ROM 0x0F)
    private static final int FRAME_ZAP_WIDE_2 = 15;         // Zap field wide frame 2 (ROM 0x10)
    private static final int FRAME_ZAP_WIDE_3 = 16;         // Zap field wide frame 3 (ROM 0x11)

    // Animation IDs (ROM: Ani_obj51)
    private static final int ANIM_FACE_NORMAL = 8;
    private static final int ANIM_LOWER_COLLISION = 4;
    private static final int ANIM_HURT = 6;
    private static final int ANIM_LAUGH = 5;
    private static final int ANIM_ZAP_MODE = 9;
    private static final int ANIM_DEFEATED = 3;

    // Boss global position (shared via Boss_X_pos / Boss_Y_pos in ROM)
    private int bossXPos;
    private int bossYPos;

    // Direction toggle (ROM: objoff_38) - 0 = moving left, 2 = moving right
    private int dirToggle;

    // Timers
    private int bossCountdown;
    private int cooldownTimer;  // ROM: objoff_3F
    private int dropPhase;      // ROM: objoff_3E
    private int triggerCount;   // ROM: objoff_2D (capped at 3)

    // Collision routine (ROM: Boss_CollisionRoutine)
    private int bossCollisionRoutine;

    // Generator positions for defeat fall-off animation
    private int rightGenX, rightGenY;  // Right generator (sub2 in ROM)
    private int leftGenX, leftGenY;    // Left generator (sub5 in ROM)

    // Multi-sprite mapping frames
    private int mainMapFrame;       // Electricity overlay frame (or -1 for none)
    private int propellerFrame;     // Propeller animation frame
    private int electricityFrame;   // Current electricity animation frame
    private int electricityTimer;   // Timer for electricity animation

    // Electrode/generator animation
    // ROM: sub2 (electrode) and sub5 (generator) are separate sprites
    // Their frames are controlled by Boss_AnimationArray based on collision mode:
    // - LOWER mode: animation frozen at initial frames (electrode small, generator left)
    // - ZAP mode: animation running (electrode extended, generator right)
    private int electrodeFrame;     // Current electrode frame (3 or 4)
    private int generatorFrame;     // Current generator frame (1 or 2)

    // Generator fall-off state during defeat
    private int leftGenYVel;   // ROM: objoff_2E
    private int leftGenYAccum; // ROM: objoff_3A (long)
    private int rightGenYVel;  // ROM: objoff_30
    private int rightGenYAccum;// ROM: objoff_34 (long)

    // Animation state
    private int[] bossAnimArray;  // ROM: Boss_AnimationArray (10 bytes = 5 entries × 2)

    // Render state
    private int lastFrameCounter;

    private final Sonic2CNZEvents cnzEvents;

    /**
     * Creates the main CNZ boss (generic factory path, no event handler reference).
     */
    public Sonic2CNZBossInstance(ObjectSpawn spawn) {
        this(spawn, null);
    }

    /**
     * Creates the main CNZ boss with a reference to the zone event handler
     * for boss defeat callbacks.
     */
    public Sonic2CNZBossInstance(ObjectSpawn spawn, Sonic2CNZEvents cnzEvents) {
        super(spawn, "CNZ Boss");
        this.cnzEvents = cnzEvents;
    }

    @Override
    protected void initializeBossState() {
        // ROM: move.w #$2A46,x_pos(a0) / move.w #$654,y_pos(a0)
        state.x = INITIAL_X;
        state.y = INITIAL_Y;
        state.xFixed = state.x << 16;
        state.yFixed = state.y << 16;
        bossXPos = state.x;
        bossYPos = state.y;

        // ROM: move.b #0,mainspr_mapframe(a0) - starts with no electricity overlay
        mainMapFrame = -1;  // No electricity overlay initially
        propellerFrame = FRAME_PROPELLER_1;
        electricityFrame = FRAME_ELEC_LOWER_1;
        electricityTimer = 0;

        // ROM: sub2_mapframe = 5 (electrode extended), sub5_mapframe = 2 (generator right)
        // Initially in LOWER mode, animation is frozen at first frame of each sequence
        electrodeFrame = FRAME_ELECTRODE_SMALL;    // Electrode small (ROM frame 4 = Java 3)
        generatorFrame = FRAME_GENERATOR_LEFT;     // Generator left arm (ROM frame 2 = Java 1)

        // ROM: move.w #0,(Boss_Y_vel).w / move.w #-$180,(Boss_X_vel).w
        state.xVel = -VELOCITY_HORIZONTAL;
        state.yVel = 0;

        // Initialize state
        state.routine = ROUTINE_PATROL;
        dirToggle = 0;  // Moving left initially
        cooldownTimer = 0;
        dropPhase = 0;
        triggerCount = 0;
        bossCollisionRoutine = COLLISION_OFF;
        state.sineCounter = 0;

        // ROM: move.w #1,(Boss_Countdown).w
        bossCountdown = 1;

        // Initialize generator positions to boss position (for defeat fall-off animation)
        rightGenX = leftGenX = state.x;
        rightGenY = leftGenY = state.y;

        // Initialize animation array (ROM: loc_319D6)
        bossAnimArray = new int[]{
                8, 0,  // Entry 0: anim=8, frame=0
                1, 0,  // Entry 1: anim=1, frame=0
                0x10, 0,  // Entry 2: anim=$10 (timing), frame=0
                3, 0,  // Entry 3: anim=3, frame=0
                2, 0   // Entry 4: anim=2, frame=0
        };

        // Initialize generator fall-off state
        leftGenYVel = 0;
        leftGenYAccum = 0;
        rightGenYVel = 0;
        rightGenYAccum = 0;

        state.renderFlags = 0;
    }

    @Override
    protected int getInitialHitCount() {
        return 8;
    }

    @Override
    protected int getInvulnerabilityDuration() {
        return CNZ_INVULNERABLE_DURATION;
    }

    @Override
    protected int getCollisionSizeIndex() {
        return 0x0F;
    }

    @Override
    protected void onHitTaken(int remainingHits) {
        // Set hurt animation
        if (state.invulnerabilityTimer == getInvulnerabilityDuration() - 1) {
            bossAnimArray[6] = ANIM_HURT;
        }
    }

    @Override
    protected boolean usesDefeatSequencer() {
        return false; // CNZ has custom defeat logic
    }

    @Override
    protected void onDefeatStarted() {
        bossCountdown = DEFEAT_TIMER_START;
        state.routine = ROUTINE_DEFEAT_EXPLODE;
    }

    @Override
    protected void updateBossLogic(int frameCounter, PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        lastFrameCounter = frameCounter;

        // ROM: loc_31A04 - Zap SFX tick every 32 frames when collision is active
        if (bossCollisionRoutine != COLLISION_OFF) {
            if ((frameCounter & 0x1F) == 0) {
                services().playSfx(Sonic2Sfx.CNZ_BOSS_ZAP.id);
            }
        }

        // Dispatch by boss_routine
        switch (state.routine) {
            case ROUTINE_PATROL -> updatePatrol(player);
            case ROUTINE_POST_TRIGGER -> updatePostTrigger();
            case ROUTINE_VERTICAL_DROP -> updateVerticalDrop();
            case ROUTINE_DEFEAT_EXPLODE -> updateDefeatExplode(frameCounter);
            case ROUTINE_DEFEAT_BOUNCE -> updateDefeatBounce();
            case ROUTINE_FLEE -> updateFlee();
        }

        // Standard tail (ROM: loc_31C08)
        if (state.routine < ROUTINE_DEFEAT_EXPLODE) {
            updateHoverAndPosition();
        } else if (state.routine >= ROUTINE_DEFEAT_EXPLODE) {
            // During defeat, skip hover but still update positions
            state.x = bossXPos;
            state.y = bossYPos;
        }

        alignGeneratorPositions();
        updateFaceAnimation(player);
    }

    /**
     * ROM: loc_31A36 - Patrol state with attack triggers.
     */
    private void updatePatrol(AbstractPlayableSprite player) {
        // Boundary check and direction swap
        if (dirToggle == 0) {
            // Moving left - check left boundary (ROM: loc_31A48)
            if (bossXPos <= BOUNDARY_LEFT) {
                bossXPos = BOUNDARY_LEFT;
                state.yVel = 0;
                state.xVel = VELOCITY_HORIZONTAL;
                dirToggle = 2;
                state.renderFlags |= 1;  // X-flip
                triggerCount = 0;
            }
        } else {
            // Moving right - check right boundary (ROM: loc_31A78)
            if (bossXPos >= BOUNDARY_RIGHT) {
                bossXPos = BOUNDARY_RIGHT;
                state.yVel = 0;
                state.xVel = -VELOCITY_HORIZONTAL;
                dirToggle = 0;
                state.renderFlags &= ~1;  // Clear X-flip
                triggerCount = 0;
            }
        }

        // Apply velocity (ROM: Boss_MoveObject)
        applyBossVelocity();

        // ROM: loc_31AA4 - Cooldown check
        if (cooldownTimer > 0) {
            cooldownTimer--;
            updatePeriodicModeToggle();
            return;
        }

        // ROM: loc_31AB6 - Player proximity check
        int dx = player.getCentreX() - state.x + 0x10;
        if (dx >= 0 && dx < 0x20) {
            int playerY = player.getCentreY();

            // Check for ball spawn trigger (player low)
            if (playerY >= PLAYER_Y_BALL_TRIGGER) {
                if (triggerCount < 3) {
                    triggerCount++;
                    state.routine = ROUTINE_POST_TRIGGER;
                    bossAnimArray[0] = ANIM_FACE_NORMAL;
                    bossAnimArray[3] = 0;  // Reset anim timer at offset 3
                    bossAnimArray[9] = 0;  // Reset anim timer at offset 9
                    bossCollisionRoutine = COLLISION_OFF;
                    spawnElectricBall();
                    bossCountdown = TRIGGER_COUNTDOWN;
                    return;
                }
            }
            // Check for vertical drop trigger (player mid-height)
            else if (playerY >= PLAYER_Y_DROP_TRIGGER) {
                mainMapFrame = FRAME_ZAP_WIDE_1;
                electricityFrame = FRAME_ZAP_WIDE_1;
                bossCollisionRoutine = COLLISION_ZAP;
                bossAnimArray[3] = 0x20;
                bossAnimArray[9] = 0x20;
                bossAnimArray[0] = ANIM_ZAP_MODE;
                state.routine = ROUTINE_VERTICAL_DROP;
                state.xVel = 0;
                state.yVel = VELOCITY_DROP;
                dropPhase = 0;
                electricityTimer = 0;
                return;
            }
        }

        updatePeriodicModeToggle();
    }

    /**
     * ROM: loc_31B46 - Periodic mode toggle between lower collision and zap.
     */
    private void updatePeriodicModeToggle() {
        bossCountdown++;
        if ((bossCountdown & 0x3F) != 0) {
            // Update electricity animation while in active mode
            updateElectricityAnimation();
            return;
        }

        // Check bit 6 of countdown high byte
        if ((bossCountdown & 0x40) != 0) {
            // Zap mode - wide electricity field
            mainMapFrame = FRAME_ZAP_WIDE_1;
            electricityFrame = FRAME_ZAP_WIDE_1;
            bossCollisionRoutine = COLLISION_ZAP;
            bossAnimArray[3] = 0x20;
            bossAnimArray[9] = 0x20;
            bossAnimArray[0] = ANIM_ZAP_MODE;
            // ROM: Animation running in ZAP mode - show extended/active frames
            electrodeFrame = FRAME_ELECTRODE_EXTENDED;
            generatorFrame = FRAME_GENERATOR_RIGHT;
        } else {
            // Lower collision mode - narrow electricity
            mainMapFrame = FRAME_ELEC_LOWER_1;
            electricityFrame = FRAME_ELEC_LOWER_1;
            bossCollisionRoutine = COLLISION_LOWER;
            bossAnimArray[3] = 0;
            bossAnimArray[9] = 0;
            bossAnimArray[0] = ANIM_LOWER_COLLISION;
            // ROM: Animation frozen in LOWER mode - show initial/small frames
            electrodeFrame = FRAME_ELECTRODE_SMALL;
            generatorFrame = FRAME_GENERATOR_LEFT;
        }
        electricityTimer = 0;
    }

    /**
     * Update electricity animation cycling through frames.
     * ROM: Ani_obj51 uses delay=1 which means frame advances every 2 ticks
     * (delay value + 1 = ticks per frame in Sonic 2 animation system)
     */
    private void updateElectricityAnimation() {
        if (bossCollisionRoutine == COLLISION_OFF) {
            mainMapFrame = -1;  // No electricity overlay
            return;
        }

        electricityTimer++;
        // ROM: delay=1 means frame changes every 2 ticks (delay + 1)
        if ((electricityTimer & 0x01) == 0) {
            if (bossCollisionRoutine == COLLISION_ZAP) {
                // Cycle through zap frames 14, 15, 16
                electricityFrame++;
                if (electricityFrame > FRAME_ZAP_WIDE_3) {
                    electricityFrame = FRAME_ZAP_WIDE_1;
                }
            } else if (bossCollisionRoutine == COLLISION_LOWER) {
                // Cycle through lower frames 11, 12, 13
                electricityFrame++;
                if (electricityFrame > FRAME_ELEC_LOWER_3) {
                    electricityFrame = FRAME_ELEC_LOWER_1;
                }
            }
            mainMapFrame = electricityFrame;
        }
    }

    /**
     * ROM: loc_31BA8 - Post-trigger timer state.
     */
    private void updatePostTrigger() {
        bossCollisionRoutine = COLLISION_OFF;
        mainMapFrame = -1;  // No electricity during post-trigger
        bossCountdown--;

        if (bossCountdown == 0) {
            bossAnimArray[3] = 0x20;
            bossAnimArray[9] = 0x20;
        } else if (bossCountdown <= -0x14) {
            bossAnimArray[3] = 0;
            bossAnimArray[9] = 0;
            state.routine = ROUTINE_PATROL;
            bossCountdown = -1;
            cooldownTimer = COOLDOWN_DURATION;
        }
    }

    /**
     * ROM: loc_31C22 - Vertical drop attack.
     */
    private void updateVerticalDrop() {
        applyBossVelocity();
        updateElectricityAnimation();  // Keep electricity animating during drop

        if (dropPhase == 0) {
            // Descending
            if (state.y >= DROP_TARGET_Y) {
                state.xVel = 0;
                state.yVel = -VELOCITY_DROP;
                dropPhase = -1;
                // Switch to lower collision on ascent
                bossCollisionRoutine = COLLISION_LOWER;
                electricityFrame = FRAME_ELEC_LOWER_1;
                mainMapFrame = electricityFrame;
                bossAnimArray[3] = 0;
                bossAnimArray[9] = 0;
                bossAnimArray[0] = ANIM_LOWER_COLLISION;
            }
        } else {
            // Ascending
            if (state.y < INITIAL_Y) {
                state.routine = ROUTINE_PATROL;
                state.yVel = 0;
                // Resume horizontal movement based on facing
                if ((state.renderFlags & 1) != 0) {
                    state.xVel = VELOCITY_HORIZONTAL;
                } else {
                    state.xVel = -VELOCITY_HORIZONTAL;
                }
                // Clear electricity overlay when returning to patrol
                mainMapFrame = -1;
                bossCollisionRoutine = COLLISION_OFF;
            }
        }
    }

    /**
     * ROM: loc_31D5C - Defeat exploding state.
     */
    private void updateDefeatExplode(int frameCounter) {
        bossCountdown--;

        if (bossCountdown >= 0) {
            bossCollisionRoutine = COLLISION_OFF;
            mainMapFrame = -1;  // No electricity overlay during defeat

            // Spawn explosions every 8 frames
            if ((frameCounter & 7) == 0) {
                spawnDefeatExplosion();
            }
        } else {
            // Transition to bounce phase
            // ROM: Remove arena wall at this point (line 66296 in s2.asm)
            if (cnzEvents != null) {
                cnzEvents.onBossDefeated();
            }

            state.renderFlags |= 1;  // Face right
            state.xVel = 0;
            state.yVel = 0;
            state.routine = ROUTINE_DEFEAT_BOUNCE;

            // Set animation to defeated face
            bossAnimArray[6] = ANIM_DEFEATED;
            bossAnimArray[0] = ANIM_FACE_NORMAL;

            bossCountdown = -0x12;
        }

        bossXPos = state.xFixed >> 16;
        bossYPos = state.yFixed >> 16;
    }

    /**
     * ROM: loc_31DCC - Defeat bounce/settle phase.
     */
    private void updateDefeatBounce() {
        bossCountdown++;

        if (bossCountdown < 0) {
            // Apply upward gravity (bounce effect)
            state.yVel += 0x18;
        } else if (bossCountdown == 0) {
            state.yVel = 0;
        } else if (bossCountdown < 0x18) {
            // Slow ascent
            state.yVel -= 8;
        } else if (bossCountdown == 0x18) {
            state.yVel = 0;
            // Play level music and load animal PLCs
            services().playMusic(Sonic2Music.CASINO_NIGHT.id);
        } else if (bossCountdown >= 0x20) {
            state.routine = ROUTINE_FLEE;
        }

        applyBossVelocity();
    }

    /**
     * ROM: loc_31E2A - Flee off-screen state.
     */
    private void updateFlee() {
        state.xVel = VELOCITY_FLEE_X;
        state.yVel = VELOCITY_FLEE_Y;

        Camera camera = services().camera();
        if (camera.getMaxX() < CAMERA_MAX_X_TARGET) {
            camera.setMaxXTarget((short) (camera.getMaxX() + 2));
        } else if (!isOnScreen()) {
            // Clear boss ID so palette cycling stops
            services().gameState().setCurrentBossId(0);
            setDestroyed(true);
        }

        applyBossVelocity();
    }

    /**
     * ROM: loc_31CDC - Hover sine wave and update position.
     */
    private void updateHoverAndPosition() {
        // Sine wave hover using inherited helper
        int yOffset = calculateHoverOffset();
        state.y = bossYPos + yOffset;
        state.x = bossXPos;
    }

    /**
     * Apply velocity to boss position (ROM: Boss_MoveObject).
     */
    private void applyBossVelocity() {
        state.xFixed += (state.xVel << 8);
        state.yFixed += (state.yVel << 8);
        bossXPos = state.xFixed >> 16;
        bossYPos = state.yFixed >> 16;
    }

    /**
     * ROM: loc_31E76 - Align generator positions and handle defeat fall-off.
     */
    private void alignGeneratorPositions() {
        if (!state.defeated) {
            leftGenX = state.x;
            leftGenY = state.y;
            rightGenX = state.x;
            rightGenY = state.y;
            // Store initial Y for fall calculations
            leftGenYAccum = state.y << 8;
            rightGenYAccum = state.y << 8;
            return;
        }

        // Left generator falls first (at countdown <= 0x78)
        if (bossCountdown <= 0x78) {
            // Move horizontally based on facing
            if ((state.renderFlags & 1) != 0) {
                leftGenX++;
            } else {
                leftGenX--;
            }

            // Apply gravity
            leftGenYVel += GRAVITY;
            leftGenYAccum += leftGenYVel;
            leftGenY = leftGenYAccum >> 8;

            // Clamp at floor
            if (leftGenY >= GENERATOR_FLOOR_Y) {
                leftGenY = GENERATOR_FLOOR_Y;
                leftGenYVel = 0;
            }
        }

        // Right generator falls later (at countdown <= 60)
        if (bossCountdown <= 60) {
            // Move opposite direction
            if ((state.renderFlags & 1) != 0) {
                rightGenX--;
            } else {
                rightGenX++;
            }

            // Apply gravity
            rightGenYVel += GRAVITY;
            rightGenYAccum += rightGenYVel;
            rightGenY = rightGenYAccum >> 8;

            // Clamp at floor
            if (rightGenY >= GENERATOR_FLOOR_Y) {
                rightGenY = GENERATOR_FLOOR_Y;
                rightGenYVel = 0;
            }
        }
    }

    /**
     * ROM: loc_31C92 - Face animation based on hit/player hurt.
     */
    private void updateFaceAnimation(AbstractPlayableSprite player) {
        // Check if player is hurt (ROM checks routine == 4)
        if (player.getYSpeed() > 0 && player.getAir() && !player.isJumping()) {
            int currentAnim = bossAnimArray[6] & 0x0F;
            if (currentAnim != ANIM_HURT) {
                bossAnimArray[6] = (bossAnimArray[6] & 0xF0) | ANIM_LAUGH;
            }
        }
    }

    /**
     * ROM: loc_31BF2 - Spawn electric ball child.
     * Ball is spawned as a child object with subtype 4, linked to parent via objoff_34.
     * The ball's position is set to parent position in its init, then adjusted by
     * objoff_28 (ballRiseOffset) during attach phase.
     */
    private void spawnElectricBall() {
        if (services().objectManager() == null) {
            return;
        }
        // ROM: move.b #4,boss_subtype(a1) / move.l a0,objoff_34(a1)
        // The spawn coordinates are informational - ball uses parent position directly
        ObjectSpawn ballSpawn = new ObjectSpawn(state.x, state.y, Sonic2ObjectIds.CNZ_BOSS, 4, 0, false, 0);
        CNZBossElectricBall ball = new CNZBossElectricBall(ballSpawn, this);
        services().objectManager().addDynamicObject(ball);
    }

    // ========================================================================
    // PUBLIC ACCESSORS - Used by CNZBossElectricBall
    // ========================================================================

    /**
     * Get the current boss countdown value for electric ball timing.
     */
    public int getBossCountdown() {
        return bossCountdown;
    }

    /**
     * Check if boss is in defeat sequence.
     */
    public boolean isInDefeatSequence() {
        return state.routine >= ROUTINE_DEFEAT_EXPLODE;
    }

    // ========================================================================
    // COLLISION
    // ========================================================================

    @Override
    public int getCollisionFlags() {
        // Main boss collision
        if (state.routine >= ROUTINE_DEFEAT_EXPLODE || state.defeated) {
            return 0;
        }
        if (state.invulnerable) {
            return 0;
        }
        // Category BOSS (0xC0) + size index 0x0F
        return 0xC0 | 0x0F;
    }

    /**
     * Check boss-specific hurtbox collision with player.
     * This is separate from attackable collision.
     */
    public boolean checkBossHurtCollision(AbstractPlayableSprite player) {
        if (bossCollisionRoutine == COLLISION_OFF) {
            return false;
        }

        int hurtX, hurtY, halfWidth, halfHeight;

        if (bossCollisionRoutine == COLLISION_LOWER) {
            // Lower collision hurtbox
            hurtX = state.x;
            hurtY = state.y + 0x28;
            halfWidth = 0x10;
            halfHeight = 0x08;
        } else {
            // Zap collision hurtbox
            hurtX = state.x + 4;
            hurtY = state.y + 0x20;
            halfWidth = 0x20;
            halfHeight = 0x08;
        }

        int playerX = player.getCentreX();
        int playerY = player.getCentreY();

        return Math.abs(playerX - hurtX) < halfWidth
                && Math.abs(playerY - hurtY) < halfHeight;
    }

    // ========================================================================
    // RENDERING
    // ========================================================================

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = services() != null
                ? services().renderManager() : null;
        if (renderManager == null) {
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.CNZ_BOSS);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        boolean flipped = (state.renderFlags & 1) != 0;

        // Update propeller animation (ROM: Anim 3 - delay 7, cycles frames 6,7)
        // Delay 7 means change every 8 frames (delay + 1)
        if ((lastFrameCounter & 0x07) == 0) {
            propellerFrame = (propellerFrame == FRAME_PROPELLER_1) ? FRAME_PROPELLER_2 : FRAME_PROPELLER_1;
        }

        // Note: electrode/generator frames are controlled by collision mode in updatePeriodicModeToggle()
        // - LOWER mode: electrode small (3), generator left (1)
        // - ZAP mode: electrode extended (4), generator right (2)

        // ROM renders 4 child sub-sprites at the boss position in this order:
        // (Rendering order matters for proper layering)
        // - sub2 = frame 5 (ROM) = electrode extended (ROM cycles frames 4,5 via Anim 1)
        // - sub3 = frame 1 (ROM) = main body with Eggman
        // - sub4 = frame 6 (ROM) = propeller (animates between frames 6,7)
        // - sub5 = frame 2 (ROM) = generator arm (second electrode)
        //
        // Additionally, mainspr_mapframe holds the electricity overlay frame (or 0 for none)
        //
        // ROM frame indices are 1-indexed in mappings, Java is 0-indexed
        // ROM frame 1 = Java frame 0, ROM frame 5 = Java frame 4, etc.

        // 1. Render sub2: electrode/claws (animated between frames 4-5 in ROM = 3-4 in Java)
        renderer.drawFrameIndex(electrodeFrame, state.x, state.y, flipped, false);

        // 2. Render sub3: main boss body (frame 1 in ROM = frame 0 in Java)
        renderer.drawFrameIndex(FRAME_BOSS_BODY, state.x, state.y, flipped, false);

        // 3. Render sub4: propeller animation (frames 6-7 in ROM = 5-6 in Java)
        renderer.drawFrameIndex(propellerFrame, state.x, state.y, flipped, false);

        // 4. Render sub5: generator arm (separate from electrode)
        // ROM: sub5 uses generator frames (1-2), not electrode frames (3-4)
        // During defeat, this detaches and falls separately
        if (!state.defeated || leftGenY == state.y) {
            renderer.drawFrameIndex(generatorFrame, state.x, state.y, flipped, false);
        }

        // 5. Render mainspr_mapframe: electricity overlay if active
        // ROM frames $0C-$0E = lower electricity, $0F-$11 = wide zap
        // Java frames 11-13 = lower, 14-16 = wide zap
        if (mainMapFrame >= FRAME_ELEC_LOWER_1 && mainMapFrame <= FRAME_ZAP_WIDE_3) {
            renderer.drawFrameIndex(mainMapFrame, state.x, state.y, flipped, false);
        }

        // 6. Render fallen generator arms during defeat (at their fallen positions)
        // Left generator (sub5) falls at countdown <= 0x78
        // Right generator (sub2 electrode) falls at countdown <= 60
        if (state.defeated) {
            if (leftGenY != state.y) {
                renderer.drawFrameIndex(FRAME_GENERATOR_LEFT, leftGenX, leftGenY, flipped, false);
            }
            if (rightGenY != state.y) {
                renderer.drawFrameIndex(FRAME_GENERATOR_RIGHT, rightGenX, rightGenY, flipped, false);
            }
        }
    }

    @Override
    public int getPriorityBucket() {
        return 3;
    }

    @Override
    protected boolean isOnScreen() {
        Camera camera = services().camera();
        int screenX = state.x - camera.getX();
        int screenY = state.y - camera.getY();
        return screenX >= -64 && screenX <= camera.getWidth() + 64
                && screenY >= -64 && screenY <= camera.getHeight() + 64;
    }
}
