package uk.co.jamesj999.sonic.game.sonic2.objects.bosses;

import uk.co.jamesj999.sonic.audio.AudioManager;
import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.game.sonic2.Sonic2ObjectArtKeys;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2AudioConstants;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2ObjectIds;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.objects.boss.AbstractBossInstance;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

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

    // Mapping frame indices (ROM: mainspr_mapframe, sub*_mapframe)
    private static final int FRAME_EGGMAN_NORMAL = 0;
    private static final int FRAME_GENERATOR_LEFT = 1;
    private static final int FRAME_GENERATOR_RIGHT = 2;
    private static final int FRAME_PROPELLER = 5;
    private static final int FRAME_LOWER_COLLISION_MODE = 0x0C;
    private static final int FRAME_ZAP_MODE = 0x0F;

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

    // Multi-sprite positions
    private int sub2X, sub2Y;  // Right generator
    private int sub3X, sub3Y;  // Electrode bottom (right)
    private int sub4X, sub4Y;  // Electrode top (left)
    private int sub5X, sub5Y;  // Left generator

    // Multi-sprite mapping frames
    private int mainMapFrame;
    private int sub2MapFrame;
    private int sub3MapFrame;
    private int sub4MapFrame;
    private int sub5MapFrame;

    // Generator fall-off state during defeat
    private int leftGenYVel;   // ROM: objoff_2E
    private int leftGenYAccum; // ROM: objoff_3A (long)
    private int rightGenYVel;  // ROM: objoff_30
    private int rightGenYAccum;// ROM: objoff_34 (long)

    // Animation state
    private int[] bossAnimArray;  // ROM: Boss_AnimationArray (10 bytes = 5 entries × 2)

    // Render state
    private int lastFrameCounter;

    /**
     * Creates the main CNZ boss.
     */
    public Sonic2CNZBossInstance(ObjectSpawn spawn, LevelManager levelManager) {
        super(spawn, levelManager, "CNZ Boss");
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

        // ROM: move.b #0,mainspr_mapframe(a0)
        mainMapFrame = FRAME_EGGMAN_NORMAL;
        // ROM: sub2_mapframe = 5, sub3_mapframe = 1, sub4_mapframe = 6, sub5_mapframe = 2
        sub2MapFrame = FRAME_PROPELLER;
        sub3MapFrame = FRAME_GENERATOR_LEFT;
        sub4MapFrame = 4; // Electrode top
        sub5MapFrame = FRAME_GENERATOR_RIGHT;

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

        // Initialize sub-sprite positions to boss position
        sub2X = sub3X = sub4X = sub5X = state.x;
        sub2Y = sub3Y = sub4Y = sub5Y = state.y;

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
    protected void updateBossLogic(int frameCounter, AbstractPlayableSprite player) {
        lastFrameCounter = frameCounter;

        // ROM: loc_31A04 - Zap SFX tick every 32 frames when collision is active
        if (bossCollisionRoutine != COLLISION_OFF) {
            if ((frameCounter & 0x1F) == 0) {
                AudioManager.getInstance().playSfx(Sonic2AudioConstants.SFX_CNZ_BOSS_ZAP);
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
                mainMapFrame = FRAME_ZAP_MODE;
                bossCollisionRoutine = COLLISION_ZAP;
                bossAnimArray[3] = 0x20;
                bossAnimArray[9] = 0x20;
                bossAnimArray[0] = ANIM_ZAP_MODE;
                state.routine = ROUTINE_VERTICAL_DROP;
                state.xVel = 0;
                state.yVel = VELOCITY_DROP;
                dropPhase = 0;
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
            return;
        }

        // Check bit 6 of countdown high byte
        if ((bossCountdown & 0x40) != 0) {
            // Zap mode
            mainMapFrame = FRAME_ZAP_MODE;
            bossCollisionRoutine = COLLISION_ZAP;
            bossAnimArray[3] = 0x20;
            bossAnimArray[9] = 0x20;
            bossAnimArray[0] = ANIM_ZAP_MODE;
        } else {
            // Lower collision mode
            mainMapFrame = FRAME_LOWER_COLLISION_MODE;
            bossCollisionRoutine = COLLISION_LOWER;
            bossAnimArray[3] = 0;
            bossAnimArray[9] = 0;
            bossAnimArray[0] = ANIM_LOWER_COLLISION;
        }
    }

    /**
     * ROM: loc_31BA8 - Post-trigger timer state.
     */
    private void updatePostTrigger() {
        bossCollisionRoutine = COLLISION_OFF;
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

        if (dropPhase == 0) {
            // Descending
            if (state.y >= DROP_TARGET_Y) {
                state.xVel = 0;
                state.yVel = -VELOCITY_DROP;
                dropPhase = -1;
                // Switch to lower collision on ascent
                bossCollisionRoutine = COLLISION_LOWER;
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
                    mainMapFrame = FRAME_LOWER_COLLISION_MODE;
                } else {
                    state.xVel = -VELOCITY_HORIZONTAL;
                }
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
            mainMapFrame = FRAME_EGGMAN_NORMAL;

            // Spawn explosions every 8 frames
            if ((frameCounter & 7) == 0) {
                spawnDefeatExplosion();
            }
        } else {
            // Transition to bounce phase
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
            AudioManager.getInstance().playMusic(Sonic2AudioConstants.MUS_CASINO_NIGHT);
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

        Camera camera = Camera.getInstance();
        if (camera.getMaxX() < CAMERA_MAX_X_TARGET) {
            camera.setMaxXTarget((short) (camera.getMaxX() + 2));
        } else if (!isOnScreen()) {
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
        sub3X = state.x;
        sub3Y = state.y;
        sub4X = state.x;
        sub4Y = state.y;

        if (!state.defeated) {
            sub5X = state.x;
            sub5Y = state.y;
            sub2X = state.x;
            sub2Y = state.y;
            // Store initial Y for fall calculations
            leftGenYAccum = state.y << 8;
            rightGenYAccum = state.y << 8;
            return;
        }

        // Left generator falls first (at countdown <= 0x78)
        if (bossCountdown <= 0x78) {
            // Move horizontally based on facing
            if ((state.renderFlags & 1) != 0) {
                sub5X++;
            } else {
                sub5X--;
            }

            // Apply gravity
            leftGenYVel += GRAVITY;
            leftGenYAccum += leftGenYVel;
            sub5Y = leftGenYAccum >> 8;

            // Clamp at floor
            if (sub5Y >= GENERATOR_FLOOR_Y) {
                sub5Y = GENERATOR_FLOOR_Y;
                leftGenYVel = 0;
            }
        }

        // Right generator falls later (at countdown <= 60)
        if (bossCountdown <= 60) {
            // Move opposite direction
            if ((state.renderFlags & 1) != 0) {
                sub2X--;
            } else {
                sub2X++;
            }

            // Apply gravity
            rightGenYVel += GRAVITY;
            rightGenYAccum += rightGenYVel;
            sub2Y = rightGenYAccum >> 8;

            // Clamp at floor
            if (sub2Y >= GENERATOR_FLOOR_Y) {
                sub2Y = GENERATOR_FLOOR_Y;
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
     */
    private void spawnElectricBall() {
        if (levelManager.getObjectManager() == null) {
            return;
        }
        ObjectSpawn ballSpawn = new ObjectSpawn(state.x, state.y + 0x30, Sonic2ObjectIds.CNZ_BOSS, 4, 0, false, 0);
        CNZBossElectricBall ball = new CNZBossElectricBall(ballSpawn, levelManager, this);
        levelManager.getObjectManager().addDynamicObject(ball);
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
        ObjectRenderManager renderManager = levelManager != null
                ? levelManager.getObjectRenderManager() : null;
        if (renderManager == null) {
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.CNZ_BOSS);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        boolean flipped = (state.renderFlags & 1) != 0;

        // Render main boss multi-sprite
        // ROM order: mainspr, sub2, sub3, sub4, sub5

        // Main sprite (Eggman)
        if (mainMapFrame >= 0) {
            renderer.drawFrameIndex(mainMapFrame, state.x, state.y, flipped, false);
        }

        // Sub2 - Right generator / propeller
        if (sub2MapFrame >= 0) {
            renderer.drawFrameIndex(sub2MapFrame, sub2X, sub2Y, flipped, false);
        }

        // Sub3 - Electrode bottom
        if (sub3MapFrame >= 0) {
            renderer.drawFrameIndex(sub3MapFrame, sub3X, sub3Y, flipped, false);
        }

        // Sub4 - Electrode top
        if (sub4MapFrame >= 0) {
            renderer.drawFrameIndex(sub4MapFrame, sub4X, sub4Y, flipped, false);
        }

        // Sub5 - Left generator
        if (sub5MapFrame >= 0) {
            renderer.drawFrameIndex(sub5MapFrame, sub5X, sub5Y, flipped, false);
        }
    }

    @Override
    public int getPriorityBucket() {
        return 3;
    }

    @Override
    protected boolean isOnScreen() {
        Camera camera = Camera.getInstance();
        int screenX = state.x - camera.getX();
        int screenY = state.y - camera.getY();
        return screenX >= -64 && screenX <= camera.getWidth() + 64
                && screenY >= -64 && screenY <= camera.getHeight() + 64;
    }
}
