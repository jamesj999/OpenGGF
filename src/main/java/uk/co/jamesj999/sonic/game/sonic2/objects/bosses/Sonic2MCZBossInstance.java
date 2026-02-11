package uk.co.jamesj999.sonic.game.sonic2.objects.bosses;

import uk.co.jamesj999.sonic.audio.AudioManager;
import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.game.GameServices;
import uk.co.jamesj999.sonic.game.sonic2.Sonic2ObjectArtKeys;
import uk.co.jamesj999.sonic.game.sonic2.audio.Sonic2Music;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2ObjectIds;
import uk.co.jamesj999.sonic.game.sonic2.objects.EggPrisonObjectInstance;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.objects.boss.AbstractBossInstance;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.physics.TrigLookupTable;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * MCZ Act 2 Boss (Object 0x57) - Drill-digger boss.
 * ROM Reference: s2.asm:65272-65922 (Obj57)
 *
 * The boss descends from the ceiling with drills running vertically, spawning
 * falling rocks and spikes. At ground level the drills rotate to horizontal
 * and the boss moves back and forth. After a countdown (or hitting arena walls)
 * it re-ascends to repeat the cycle.
 *
 * Multi-sprite rendering: The ROM uses 4 child sprites (sub2-sub5) within the
 * main object for diggers, face, and hover thingies. Our engine renders them
 * as separate frame draws in appendRenderCommands().
 *
 * State Machine (boss_routine):
 * - SUB0 (0x00): Rising / countdown before descent
 * - SUB2 (0x02): Descending with rocks/spikes
 * - SUB4 (0x04): Reaching ground, digger rotation to horizontal
 * - SUB6 (0x06): Horizontal battle phase
 * - SUB8 (0x08): Defeated - explosions
 * - SUBA (0x0A): Hover down after defeat
 * - SUBC (0x0C): Escape right
 */
public class Sonic2MCZBossInstance extends AbstractBossInstance {

    // State machine constants (ROM: boss_routine values)
    private static final int SUB0_RISING = 0x00;
    private static final int SUB2_DESCENDING = 0x02;
    private static final int SUB4_GROUND = 0x04;
    private static final int SUB6_HORIZONTAL = 0x06;
    private static final int SUB8_DEFEATED = 0x08;
    private static final int SUBA_HOVER_DOWN = 0x0A;
    private static final int SUBC_ESCAPE = 0x0C;

    // Position constants (ROM: s2.asm:65294-65295)
    private static final int SPAWN_X = 0x21A0;
    private static final int SPAWN_Y = 0x560;
    /** Y threshold above screen where boss repositions (ROM: cmpi.w #$560) */
    private static final int CEILING_Y = 0x560;
    /** Y threshold where stones stop spawning (ROM: cmpi.w #$620) */
    private static final int STONE_THRESHOLD_Y = 0x620;
    /** Y position at ground level (ROM: cmpi.w #$660) */
    private static final int GROUND_Y = 0x660;
    /** Arena left boundary (ROM: cmpi.w #$2120) */
    private static final int ARENA_LEFT_X = 0x2120;
    /** Arena right boundary (ROM: cmpi.w #$2200) */
    private static final int ARENA_RIGHT_X = 0x2200;
    /** Player X threshold for side selection (ROM: cmpi.w #$2190) */
    private static final int PLAYER_X_THRESHOLD = 0x2190;
    /** Stone/spike spawn Y position (ROM: move.w #$5F0,y_pos(a1)) */
    private static final int DEBRIS_SPAWN_Y = 0x5F0;
    /** Stone/spike minimum X (ROM: addi.w #$20F0,d1) */
    private static final int DEBRIS_MIN_X = 0x20F0;
    /** Stone/spike maximum X (ROM: cmpi.w #$2230,d1) */
    private static final int DEBRIS_MAX_X = 0x2230;
    /** Debris delete boundary (ROM: cmpi.w #$6F0) */
    private static final int DEBRIS_DELETE_Y = 0x6F0;

    // Velocity constants (8.8 fixed-point)
    /** Initial descent velocity (ROM: move.w #$C0,(Boss_Y_vel).w) */
    private static final int INITIAL_DESCENT_VEL = 0xC0;
    /** Fast descent velocity after reposition (ROM: move.w #$100,(Boss_Y_vel).w) */
    private static final int FAST_DESCENT_VEL = 0x100;
    /** Ascent velocity (ROM: move.w #-$C0,(Boss_Y_vel).w) */
    private static final int ASCENT_VEL = -0xC0;
    /** Horizontal velocity (ROM: move.w #-$200,(Boss_X_vel).w) */
    private static final int HORIZONTAL_VEL = 0x200;
    /** Escape X velocity (ROM: move.w #$400,(Boss_X_vel).w) */
    private static final int ESCAPE_X_VEL = 0x400;
    /** Escape Y velocity (ROM: move.w #-$40,(Boss_Y_vel).w) */
    private static final int ESCAPE_Y_VEL = -0x40;
    /** SubA gravity (ROM: addi.w #$10,(Boss_Y_vel).w) */
    private static final int SUBA_GRAVITY = 0x10;
    /** SubA upward acceleration (ROM: subi_.w #8,(Boss_Y_vel).w) */
    private static final int SUBA_ACCEL_UP = -8;
    /** Digger fall gravity (ROM: addi.w #$38,obj57_sub5_y_vel) */
    private static final int DIGGER_FALL_GRAVITY = 0x38;

    // Timing constants
    /** Initial countdown before first descent (ROM: move.w #$28,(Boss_Countdown).w) */
    private static final int INITIAL_COUNTDOWN = 0x28;
    /** Countdown between cycles (ROM: move.w #$64,(Boss_Countdown).w) */
    private static final int CYCLE_COUNTDOWN = 0x64;
    /** Countdown threshold for enabling collision in SUB6 (ROM: cmpi.w #$28) */
    private static final int COLLISION_ENABLE_THRESHOLD = 0x28;
    /** Defeat explosion duration (ROM: move.w #$B3,(Boss_Countdown).w) */
    private static final int DEFEAT_COUNTDOWN = 0xB3;

    // Animation frame indices (from ROM Ani_obj57 / Obj57_MapUnc_316EC)
    // Main vehicle body
    private static final int FRAME_BODY_LIGHT_ON = 0;     // frame 0: tile $09 = lamp ON
    private static final int FRAME_BODY_LIGHT_OFF = 1;   // frame 1: tile $00 = lamp OFF
    // Digger frames (vertical)
    private static final int FRAME_DIGGER_VERT_1 = 2;    // frame 2: vertical digger phase 1
    private static final int FRAME_DIGGER_VERT_2 = 3;    // frame 3: vertical digger phase 2
    private static final int FRAME_DIGGER_VERT_3 = 4;    // frame 4: vertical digger phase 3
    // Hover thingies
    private static final int FRAME_HOVER_FIRE_ON_1 = 5;  // frame 5: hover fire on small
    private static final int FRAME_HOVER_FIRE_ON_2 = 6;  // frame 6: hover fire on large
    private static final int FRAME_HOVER_NO_FIRE = 7;    // frame 7: hover no fire
    // Digger frames (horizontal)
    private static final int FRAME_DIGGER_DIAG = 8;      // frame 8: diagonal transition
    private static final int FRAME_DIGGER_HORIZ_1 = 9;   // frame 9: horizontal phase 1
    private static final int FRAME_DIGGER_HORIZ_2 = 10;  // frame 10: horizontal phase 2
    private static final int FRAME_DIGGER_HORIZ_3 = 11;  // frame 11: horizontal phase 3
    // Face frames
    private static final int FRAME_FACE_NORMAL_1 = 14;   // frame 14 ($E): Robotnik normal 1
    private static final int FRAME_FACE_NORMAL_2 = 15;   // frame 15 ($F): Robotnik normal 2
    private static final int FRAME_FACE_GRIN_1 = 16;     // frame 16 ($10): Robotnik grin 1
    private static final int FRAME_FACE_GRIN_2 = 17;     // frame 17 ($11): Robotnik grin 2
    private static final int FRAME_FACE_HIT = 18;        // frame 18 ($12): Robotnik hit/grin when hit
    private static final int FRAME_FACE_BURNT = 19;      // frame 19 ($13): Robotnik burnt face

    // Digger offset from center (ROM: subi.w #$28,sub5_x_pos)
    private static final int DIGGER_X_OFFSET = 0x28;

    // Internal state
    private int countdown;
    private boolean flipped; // render_flags.x_flip
    private boolean screenShaking; // ROM: Screen_Shaking_Flag
    private int sineCounter;
    private int currentFrameCounter;
    private int vintRuncount; // pseudo-random counter for stone/spike spawning

    // Animation state (simplified from ROM's Boss_AnimationArray)
    private int hoverFrame;
    private int hoverAnimTimer;
    private int diggerFrame;     // Current digger animation frame
    private int diggerAnimTimer;
    private int diggerAnimIndex; // Which animation sequence we're in
    private boolean diggerTransitioning; // true for one tick when showing diagonal transition frame
    private int bodyFrame;
    private int faceFrame;
    private int faceAnimTimer;
    private int faceAnimIndex;
    // ROM: boss_hurt_sonic flag - set by collision system when Sonic is hurt by
    // the boss's drills (BossCollision_MCZ checks invulnerable_time(a0) == $78),
    // NOT when Sonic hits the boss. Causes Eggman to grin before reascending.
    private boolean bossHurtSonic;

    // Defeat-specific state
    // Digger fall-apart positions (ROM: obj57_sub5/sub2 y_pos2, y_vel)
    private int leftDiggerXFixed;
    private int leftDiggerYFixed;
    private int leftDiggerYVel;
    private int rightDiggerXFixed;
    private int rightDiggerYFixed;
    private int rightDiggerYVel;
    private boolean diggersDetached;

    public Sonic2MCZBossInstance(ObjectSpawn spawn, LevelManager levelManager) {
        super(spawn, levelManager, "MCZ Boss");
    }

    @Override
    protected void initializeBossState() {
        // ROM: Obj57_Init (s2.asm:65285-65327)
        state.x = SPAWN_X;
        state.y = SPAWN_Y;
        state.xFixed = state.x << 16;
        state.yFixed = state.y << 16;

        // ROM: move.w #$C0,(Boss_Y_vel).w - initial descent
        state.xVel = 0;
        state.yVel = INITIAL_DESCENT_VEL;

        // ROM: move.b #2,boss_routine(a0) - boss starts descending from spawn
        state.routineSecondary = SUB2_DESCENDING;

        flipped = false;
        screenShaking = true; // ROM: move.b #1,(Screen_Shaking_Flag).w
        sineCounter = 0;
        bossHurtSonic = false;

        // ROM: move.w #$28,(Boss_Countdown).w
        countdown = INITIAL_COUNTDOWN;

        // Initialize animation state
        // ROM: Obj57_InitAnimationData (s2.asm:65330-65342)
        // Animation 0: hover thingies (fire on) - starts with frames 5,6 alternating
        hoverFrame = FRAME_HOVER_FIRE_ON_1;
        hoverAnimTimer = 1;
        // Animation 1: digger (vertical) - starts with frame 2
        diggerFrame = FRAME_DIGGER_VERT_1;
        diggerAnimTimer = 1;
        diggerAnimIndex = 5; // animation 5 = vertical loop
        // Animation 2: main vehicle - starts with light OFF
        bodyFrame = FRAME_BODY_LIGHT_OFF;
        // Animation 3: face - starts with frame 14 (normal)
        faceFrame = FRAME_FACE_NORMAL_1;
        faceAnimTimer = 7;
        faceAnimIndex = 0xD; // animation D = normal face

        // Digger fall-apart state
        leftDiggerYVel = -0x380;  // ROM: move.w #-$380,obj57_sub5_y_vel
        rightDiggerYVel = -0x380; // ROM: move.w #-$380,obj57_sub2_y_vel
        diggersDetached = false;
    }

    @Override
    protected void updateBossLogic(int frameCounter, AbstractPlayableSprite player) {
        currentFrameCounter = frameCounter;
        vintRuncount++;

        // Update animations
        updateAnimations();

        switch (state.routineSecondary) {
            case SUB0_RISING -> updateSub0Rising(player);
            case SUB2_DESCENDING -> updateSub2Descending();
            case SUB4_GROUND -> updateSub4Ground(player);
            case SUB6_HORIZONTAL -> updateSub6Horizontal(player);
            case SUB8_DEFEATED -> updateSub8Defeated();
            case SUBA_HOVER_DOWN -> updateSubAHoverDown();
            case SUBC_ESCAPE -> updateSubCEscape();
        }
    }

    /**
     * SUB0: Boss ascending / countdown before repositioning and descending.
     * ROM: Obj57_Main_Sub0 (s2.asm:65362-65413)
     */
    private void updateSub0Rising(AbstractPlayableSprite player) {
        countdown--;
        if (countdown >= 0) {
            // Still counting down - continue rising or stationary
            // ROM: check countdown == $28 to disable collision
            if (countdown == COLLISION_ENABLE_THRESHOLD) {
                // ROM: move.b #0,(Boss_CollisionRoutine).w
                // collision disabled during rising
            }
            handleHits();
            return;
        }

        // Countdown finished - apply movement
        // ROM: bsr.w Boss_MoveObject
        applyBossMovement();

        // ROM: cmpi.w #$560,(Boss_Y_pos).w
        if (state.y > CEILING_Y) {
            // Still below ceiling, check for screen shake & stones
            if (state.y < STONE_THRESHOLD_Y) {
                setScreenShaking(true);
                spawnStoneOrSpike();
            }
            handleHits();
            return;
        }

        // Reached ceiling - reposition over player and start descent
        state.yVel = FAST_DESCENT_VEL;

        // ROM: Select X position based on player position
        if (player != null) {
            int playerX = player.getCentreX();
            if (playerX < PLAYER_X_THRESHOLD) {
                state.x = ARENA_RIGHT_X;
            } else {
                state.x = ARENA_LEFT_X;
            }
        }
        state.xFixed = state.x << 16;

        // ROM: addq.b #2,boss_routine(a0) - next routine (descending)
        state.routineSecondary = SUB2_DESCENDING;

        // Set flip based on player position
        flipped = false;
        if (player != null && player.getCentreX() > state.x) {
            flipped = true;
        }

        handleHits();
    }

    /**
     * SUB2: Boss descending with rocks/spikes falling.
     * ROM: Obj57_Main_Sub2 (s2.asm:65416-65440)
     */
    private void updateSub2Descending() {
        applyBossMovement();
        spawnStoneOrSpike();

        // ROM: cmpi.w #$620,(Boss_Y_pos).w
        if (state.y >= STONE_THRESHOLD_Y) {
            // Stop screen shake, move to next routine
            state.routineSecondary = SUB4_GROUND;
            setScreenShaking(false);
        }

        handleHits();
    }

    /**
     * SUB4: Reaching ground level, digger rotation to horizontal.
     * ROM: Obj57_Main_Sub4 (s2.asm:65443-65487)
     */
    private void updateSub4Ground(AbstractPlayableSprite player) {
        applyBossMovement();

        // ROM: cmpi.w #$660,(Boss_Y_pos).w
        if (state.y >= GROUND_Y) {
            state.y = GROUND_Y;
            state.yFixed = state.y << 16;

            // Transition to horizontal phase
            state.routineSecondary = SUB6_HORIZONTAL;

            // ROM: Set digger animations to horizontal transition (anim 6)
            diggerAnimIndex = 6;
            diggerAnimTimer = 1;

            // ROM: Set face to normal
            faceAnimIndex = 0xD;
            faceFrame = FRAME_FACE_NORMAL_1;
            faceAnimTimer = 7;

            // ROM: Set body light on - stays ON for entire SUB6 phase
            // ROM: $FC subanimation loop at anim_frame 2 holds frame 0 (light ON) indefinitely
            bodyFrame = FRAME_BODY_LIGHT_ON;

            // ROM: hover fire off
            hoverFrame = FRAME_HOVER_NO_FIRE;
            hoverAnimTimer = 0x30;

            // ROM: Set countdown and horizontal velocity
            countdown = CYCLE_COUNTDOWN;

            // Select direction based on player position
            flipped = false;
            if (player != null && player.getCentreX() > state.x) {
                flipped = true;
            }

            state.xVel = flipped ? HORIZONTAL_VEL : -HORIZONTAL_VEL;
            state.yVel = 0;
        }

        handleHits();
    }

    /**
     * SUB6: Horizontal battle phase - moving back and forth, hittable.
     * ROM: Obj57_Main_Sub6 (s2.asm:65490-65550)
     */
    private void updateSub6Horizontal(AbstractPlayableSprite player) {
        countdown--;

        // ROM: Enable collision when countdown drops below $28
        if (countdown <= COLLISION_ENABLE_THRESHOLD && countdown > 0) {
            // Collision enabled (handled by getCollisionFlags)
        }

        if (countdown < 0) {
            // Timer expired - check for hurt sonic flag
            if (bossHurtSonic) {
                bossHurtSonic = false;
                // ROM: Obj57_Main_Sub6_ReAscend1 (s2.asm:65516-65518)
                // move.b #$30,7(a1) - only extends face timer, does NOT change face frame
                // The face stays on its current frame for $30 more ticks before resuming
                faceAnimTimer = 0x30;
                reascend();
                return;
            }

            // Apply movement and check arena boundaries
            applyBossMovement();

            // ROM: cmpi.w #$2120,(Boss_X_pos).w
            if (state.x <= ARENA_LEFT_X) {
                state.x = ARENA_LEFT_X;
                state.xFixed = state.x << 16;
                reascend();
                return;
            }

            // ROM: cmpi.w #$2200,(Boss_X_pos).w
            if (state.x >= ARENA_RIGHT_X) {
                state.x = ARENA_RIGHT_X;
                state.xFixed = state.x << 16;
                reascend();
                return;
            }
        }

        handleHits();
    }

    /**
     * Transition from horizontal phase back to rising (Sub0).
     * ROM: Obj57_Main_Sub6_ReAscend2 (s2.asm:65520-65532)
     */
    private void reascend() {
        state.xVel = 0;
        state.routineSecondary = SUB0_RISING;

        // ROM: Set digger animations back to vertical transition (anim B)
        diggerAnimIndex = 0xB;
        diggerAnimTimer = 1;

        // ROM: hover fire on
        hoverFrame = FRAME_HOVER_FIRE_ON_1;
        hoverAnimTimer = 0;

        // ROM: body light off (Boss_AnimationArray[5] zeroed on reascend)
        bodyFrame = FRAME_BODY_LIGHT_OFF;

        // ROM: face normal
        faceAnimIndex = 0xD;
        faceFrame = FRAME_FACE_NORMAL_1;
        faceAnimTimer = 7;

        countdown = CYCLE_COUNTDOWN;
        state.yVel = ASCENT_VEL;
    }

    /**
     * SUB8: Boss defeated - explosions.
     * ROM: Obj57_Main_Sub8 (s2.asm:65725-65757)
     */
    private void updateSub8Defeated() {
        // ROM: st.b boss_defeated(a0) - state.defeated already set by triggerDefeat()
        setScreenShaking(false);

        countdown--;
        if (countdown >= 0) {
            // Explosion phase
            // ROM: Set burnt face, keep body light off
            faceFrame = FRAME_FACE_BURNT;
            bodyFrame = FRAME_BODY_LIGHT_OFF;

            // ROM: Boss_LoadExplosion checks (Vint_runcount+3) & 7 == 0
            // Only spawn explosion every 8th frame (~22 total over 179 frames)
            if ((vintRuncount & 7) == 0) {
                spawnDefeatExplosion();
            }
        } else {
            // Countdown finished - transition to hover down
            flipped = true; // ROM: bset x_flip
            state.xVel = 0;
            state.yVel = 0;
            state.routineSecondary = SUBA_HOVER_DOWN;

            // ROM: face grin when hit
            faceFrame = FRAME_FACE_HIT;

            // ROM: move.w #-$12,(Boss_Countdown).w
            countdown = -0x12;
        }

        transferDiggerPositions();
    }

    /**
     * SUBA: Slowly hovering down after defeat, no explosions.
     * ROM: Obj57_Main_SubA (s2.asm:65759-65817)
     */
    private void updateSubAHoverDown() {
        countdown++;
        if (countdown == 0) {
            // Reset Y velocity
            state.yVel = 0;
        } else if (countdown < 0) {
            // Still counting up to 0 - apply gravity
            if (state.y < STONE_THRESHOLD_Y) {
                countdown--; // Stay in this state longer if above threshold
            }
            state.yVel += SUBA_GRAVITY;
        } else if (countdown < 0x18) {
            // Accelerate upward
            state.yVel += SUBA_ACCEL_UP;
        } else if (countdown == 0x18) {
            // Play level music and load animal PLCs
            state.yVel = 0;
            AudioManager.getInstance().playMusic(Sonic2Music.MYSTIC_CAVE.id);
        } else if (countdown >= 0x20) {
            // ROM: face normal, hover fire on, transition to escape
            faceFrame = FRAME_FACE_NORMAL_1;
            faceAnimIndex = 0xD;
            hoverFrame = FRAME_HOVER_FIRE_ON_1;
            hoverAnimTimer = 0;
            state.routineSecondary = SUBC_ESCAPE;
        }

        // Apply movement with sine offset
        applyBossMovement();
        applySineOffset();
        transferDiggerPositions();
    }

    /**
     * SUBC: Escape right at high speed.
     * ROM: Obj57_Main_SubC (s2.asm:65820-65854)
     */
    private void updateSubCEscape() {
        state.xVel = ESCAPE_X_VEL;
        state.yVel = ESCAPE_Y_VEL;

        Camera camera = Camera.getInstance();
        // ROM: cmpi.w #$2240,(Camera_Max_X_pos).w
        if (camera.getMaxX() < 0x2240) {
            camera.setMaxX((short) (camera.getMaxX() + 2));
        } else {
            // Check if off screen
            if (!isOnScreen()) {
                // Spawn egg prison and delete self
                spawnEggPrison();
                setDestroyed(true);
                return;
            }
        }

        applyBossMovement();
        applySineOffset();
        transferDiggerPositions();
    }

    /**
     * Apply Boss_MoveObject: velocity to position.
     */
    private void applyBossMovement() {
        state.xFixed += (state.xVel << 8);
        state.yFixed += (state.yVel << 8);
        state.updatePositionFromFixed();
    }

    /**
     * Apply sine wave offset to Y position.
     * ROM: Obj57_AddSinusOffset (s2.asm:65678-65685)
     */
    private void applySineOffset() {
        int sine = TrigLookupTable.sinHex(sineCounter & 0xFF);
        int offset = sine >> 6;
        state.y = (state.yFixed >> 16) + offset;
        sineCounter = (sineCounter + 2) & 0xFF;
    }

    /**
     * Transfer positions for digger sub-sprites during defeat fall-apart.
     * ROM: Obj57_TransferPositions / Obj57_FallApart (s2.asm:65553-65629)
     */
    private void transferDiggerPositions() {
        if (!state.defeated) {
            return;
        }
        if (!diggersDetached) {
            diggersDetached = true;
            // ROM: sub5 at boss_x ± $28, sub2 at boss_x (no offset)
            leftDiggerXFixed = flipped ? state.xFixed + (DIGGER_X_OFFSET << 16)
                                       : state.xFixed - (DIGGER_X_OFFSET << 16);
            leftDiggerYFixed = state.yFixed;
            rightDiggerXFixed = state.xFixed; // sub2 has no offset
            rightDiggerYFixed = state.yFixed;
        }

        // ROM: cmpi.w #$78,(Boss_Countdown).w - only fall apart when countdown < $78
        if (countdown >= 0x78) {
            return;
        }

        // Left digger drifts left and falls
        // ROM: subi_.w #1,sub5_x_pos(a0)
        leftDiggerXFixed -= (1 << 16);
        if (flipped) {
            leftDiggerXFixed += (2 << 16); // Opposite direction when flipped
        }
        leftDiggerYVel += DIGGER_FALL_GRAVITY;
        leftDiggerYFixed += (leftDiggerYVel << 8);
        if ((leftDiggerYFixed >> 16) >= DEBRIS_DELETE_Y) {
            leftDiggerYVel = 0;
        }

        // Right digger drifts right and falls
        // ROM: addi_.w #1,sub2_x_pos(a0)
        rightDiggerXFixed += (1 << 16);
        if (flipped) {
            rightDiggerXFixed -= (2 << 16); // Opposite direction when flipped
        }
        rightDiggerYVel += DIGGER_FALL_GRAVITY;
        rightDiggerYFixed += (rightDiggerYVel << 8);
        if ((rightDiggerYFixed >> 16) >= DEBRIS_DELETE_Y) {
            rightDiggerYVel = 0;
        }
    }

    /**
     * Spawn a stone or spike at random position.
     * ROM: Obj57_SpawnStoneSpike (s2.asm:65632-65665)
     */
    private void spawnStoneOrSpike() {
        if (levelManager.getObjectManager() == null) {
            return;
        }

        // ROM: move.b (Vint_runcount+3).w,d1 - pseudo-random using frame counter
        int d1 = vintRuncount & 0xFF;
        boolean isSpike;

        // ROM: sf d2 (d2=0=spike default), andi.b #$1F,d1 / beq.s Obj57_LoadStoneSpike
        // d2=0 means spike (falls through to set frame $14 + collision $B1)
        // d2=true means stone (branches over spike setup, keeps frame $D)
        if ((d1 & 0x1F) == 0) {
            isSpike = true; // ROM: d2=0 -> spike
        } else if ((d1 & 0x07) != 0) {
            return; // No spawn this frame
        } else {
            isSpike = false; // ROM: st.b d2 -> stone
        }

        // Generate random X position in [DEBRIS_MIN_X, DEBRIS_MAX_X]
        int x;
        do {
            x = DEBRIS_MIN_X + (ThreadLocalRandom.current().nextInt(0x200));
        } while (x > DEBRIS_MAX_X);

        MCZFallingDebrisInstance debris = new MCZFallingDebrisInstance(
                x, DEBRIS_SPAWN_Y, isSpike);
        levelManager.getObjectManager().addDynamicObject(debris);
    }

    /**
     * Handle hit detection - delegates to parent but also checks for face animation.
     * ROM: Obj57_HandleHits (s2.asm:65668-65675)
     */
    private void handleHits() {
        // The parent's hitHandler manages invulnerability/flash
        // ROM: Obj57_HandleHits checks invulnerable_time == $1F for face grin
        if (state.invulnerable && state.invulnerabilityTimer == 0x1F) {
            faceFrame = FRAME_FACE_HIT;
            faceAnimTimer = 0xC0;
        }
    }

    /**
     * Update animation state for all sub-sprites.
     * Simplified version of ROM's AnimateBoss with Boss_AnimationArray.
     */
    private void updateAnimations() {
        // Hover thingies animation (fire on/off cycle)
        if (hoverAnimTimer > 0) {
            hoverAnimTimer--;
        } else {
            // Toggle between fire frames
            if (hoverFrame == FRAME_HOVER_FIRE_ON_1) {
                hoverFrame = FRAME_HOVER_FIRE_ON_2;
                hoverAnimTimer = 1;
            } else if (hoverFrame == FRAME_HOVER_FIRE_ON_2) {
                hoverFrame = FRAME_HOVER_FIRE_ON_1;
                hoverAnimTimer = 1;
            }
            // FRAME_HOVER_NO_FIRE stays until explicitly changed
        }

        // Body light: frame set directly by state transitions (ON in SUB6, OFF on reascend)
        // No auto-cycling needed - ROM uses $FC subanimation loop to hold frame indefinitely

        // Digger animation - simplified cycling through vertical or horizontal frames
        if (diggerAnimTimer > 0) {
            diggerAnimTimer--;
        } else {
            updateDiggerAnimation();
            diggerAnimTimer = 1;
        }

        // Face animation
        if (faceAnimTimer > 0) {
            faceAnimTimer--;
        } else {
            updateFaceAnimation();
            faceAnimTimer = 7;
        }
    }

    /**
     * Update digger frame based on current animation sequence.
     */
    private void updateDiggerAnimation() {
        // Handle diagonal transition frame: show for exactly one tick, then advance
        if (diggerTransitioning) {
            diggerTransitioning = false;
            if (state.routineSecondary == SUB6_HORIZONTAL || diggerAnimIndex >= 8) {
                diggerFrame = FRAME_DIGGER_HORIZ_1;
                diggerAnimIndex = 8;
            } else {
                diggerFrame = FRAME_DIGGER_VERT_1;
                diggerAnimIndex = 5;
            }
            return;
        }

        if (state.routineSecondary == SUB6_HORIZONTAL || diggerAnimIndex >= 8) {
            // Horizontal digger cycle: frames 9, 10, 11
            if (diggerFrame >= FRAME_DIGGER_HORIZ_1 && diggerFrame <= FRAME_DIGGER_HORIZ_3) {
                diggerFrame++;
                if (diggerFrame > FRAME_DIGGER_HORIZ_3) {
                    diggerFrame = FRAME_DIGGER_HORIZ_1;
                }
            } else {
                // Transitioning: show diagonal for one tick
                diggerFrame = FRAME_DIGGER_DIAG;
                diggerTransitioning = true;
            }
        } else {
            // Vertical digger cycle: frames 2, 3, 4
            if (diggerFrame >= FRAME_DIGGER_VERT_1 && diggerFrame <= FRAME_DIGGER_VERT_3) {
                diggerFrame++;
                if (diggerFrame > FRAME_DIGGER_VERT_3) {
                    diggerFrame = FRAME_DIGGER_VERT_1;
                }
            } else {
                // Transitioning back: show diagonal for one tick
                diggerFrame = FRAME_DIGGER_DIAG;
                diggerTransitioning = true;
            }
        }
    }

    /**
     * Update face animation frame.
     */
    private void updateFaceAnimation() {
        if (faceAnimIndex == 0xD) {
            // Normal face: alternate between normal frames
            faceFrame = (faceFrame == FRAME_FACE_NORMAL_1) ?
                    FRAME_FACE_NORMAL_2 : FRAME_FACE_NORMAL_1;
        }
        // Hit/grin/burnt frames are set directly and don't auto-cycle
    }

    private void setScreenShaking(boolean shaking) {
        this.screenShaking = shaking;
        GameServices.gameState().setScreenShakeActive(shaking);
    }

    /** Returns true when Screen_Shaking_Flag is active (descent phases). */
    public boolean isScreenShaking() {
        return screenShaking;
    }

    private void spawnEggPrison() {
        if (levelManager.getObjectManager() == null) {
            return;
        }
        ObjectSpawn prisonSpawn = new ObjectSpawn(
                0x2180, 0x0660,
                Sonic2ObjectIds.EGG_PRISON,
                0, 0, false, 0
        );
        EggPrisonObjectInstance prison = new EggPrisonObjectInstance(prisonSpawn, "Egg Prison");
        levelManager.getObjectManager().addDynamicObject(prison);
    }

    @Override
    protected int getInitialHitCount() {
        return 8; // ROM: move.b #8,boss_hitcount2(a0)
    }

    @Override
    protected void onHitTaken(int remainingHits) {
        // ROM: Obj57_HandleHits - face hit when invulnerability starts
        faceFrame = FRAME_FACE_HIT;
        faceAnimTimer = 0xC0;
        // NOTE: bossHurtSonic is NOT set here. In the ROM, boss_hurt_sonic is set
        // by BossCollision_MCZ when the boss's drills hurt Sonic (checks
        // invulnerable_time(a0) == $78 on the player). It is NOT set when
        // Sonic successfully attacks the boss. See s2.asm:85248, 85273.
        // TODO: Wire boss_hurt_sonic via the collision system (BossCollision_MCZ
        // equivalent) when drill hitbox contact hurts the player.
    }

    @Override
    protected int getCollisionSizeIndex() {
        return 0x0F; // ROM: move.b #$F,collision_flags(a0)
    }

    @Override
    protected boolean usesDefeatSequencer() {
        return false; // MCZ boss has custom defeat logic
    }

    @Override
    protected void onDefeatStarted() {
        // ROM: Obj57_FinalDefeat (s2.asm:65715-65722)
        countdown = DEFEAT_COUNTDOWN;
        state.routineSecondary = SUB8_DEFEATED;
    }

    @Override
    public int getCollisionFlags() {
        // Collision only enabled during horizontal phase when countdown < $28
        if (state.routineSecondary == SUB6_HORIZONTAL && countdown <= COLLISION_ENABLE_THRESHOLD && countdown > 0) {
            return super.getCollisionFlags();
        }
        // Also enabled during other phases that call HandleHits
        if (state.routineSecondary != SUB8_DEFEATED && state.routineSecondary != SUBA_HOVER_DOWN && state.routineSecondary != SUBC_ESCAPE) {
            return super.getCollisionFlags();
        }
        return 0;
    }

    @Override
    public int getPriorityBucket() {
        return 3; // ROM: move.b #3,priority(a0)
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = levelManager.getObjectRenderManager();
        if (renderManager == null) {
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.MCZ_BOSS);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        int bx = state.x;
        int by = state.y;

        // VDP multi-sprite priority (front to back):
        //   mainspr(hover) > sub2(center digger) > sub3(body) > sub4(face) > sub5(offset digger)
        // Our engine: later drawFrameIndex calls render in front, so draw back-to-front.

        // 1. Offset digger (sub5) - BACK
        // ROM: sub5 at boss_x ± $28
        if (diggersDetached) {
            int leftX = leftDiggerXFixed >> 16;
            int leftY = leftDiggerYFixed >> 16;
            if (leftY < DEBRIS_DELETE_Y) {
                renderer.drawFrameIndex(diggerFrame, leftX, leftY, flipped, false);
            }
        } else {
            int offsetDiggerX = flipped ? bx + DIGGER_X_OFFSET : bx - DIGGER_X_OFFSET;
            renderer.drawFrameIndex(diggerFrame, offsetDiggerX, by, flipped, false);
        }

        // 2. Face (sub4)
        renderer.drawFrameIndex(faceFrame, bx, by, flipped, false);

        // 3. Body (sub3)
        renderer.drawFrameIndex(bodyFrame, bx, by, flipped, false);

        // 4. Center digger (sub2) - no X offset
        if (diggersDetached) {
            int rightX = rightDiggerXFixed >> 16;
            int rightY = rightDiggerYFixed >> 16;
            if (rightY < DEBRIS_DELETE_Y) {
                renderer.drawFrameIndex(diggerFrame, rightX, rightY, flipped, false);
            }
        } else {
            renderer.drawFrameIndex(diggerFrame, bx, by, flipped, false);
        }

        // 5. Hover thingies (mainspr) - FRONT
        renderer.drawFrameIndex(hoverFrame, bx, by, flipped, false);
    }
}
