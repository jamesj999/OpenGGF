package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.events.Sonic3kAIZEvents;
import com.openggf.graphics.GLCommand;
import com.openggf.level.Palette;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.objects.boss.AbstractBossInstance;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.SwingMotion;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * AIZ Act 2 end boss (Object 0x92) — Eggman's fire-breathing machine.
 *
 * <p>ROM: Obj_AIZEndBoss (sonic3k.asm:137997).
 * Emerges from the waterfall, fires flamethrower projectiles via arm/propeller children,
 * then submerges, repositions to one of 4 random positions, and re-emerges. After two
 * attack cycles the boss is defeated, spawning an Egg Capsule.
 *
 * <p>State machine (AIZ_EndBossIndex, routines 0–14 stepping by 2):
 * <ol start="0">
 *   <li>Init: spawn ship + 2 arms (each arm spawns propeller child)</li>
 *   <li>Emerge: flickering reveal from waterfall (animation byte_69D98)</li>
 *   <li>Revealed: animation + becomes hittable, spawns flame column</li>
 *   <li>Hover: sine oscillation (Swing_UpAndDown)</li>
 *   <li>Attack wait: countdown (fire window)</li>
 *   <li>Re-emerge: reuses routine 2 (submerge splash, reposition)</li>
 *   <li>Camera scroll: incrementally move camera min/max X</li>
 *   <li>Move + wait: travel to new random position</li>
 * </ol>
 *
 * <p>Character-specific data:
 * <ul>
 *   <li>Sonic: camera lock at $4880, target $48E0, Y offset $15A</li>
 *   <li>Knuckles: camera lock at $4100, target $4160, Y offset $5DA</li>
 * </ul>
 */
public class AizEndBossInstance extends AbstractBossInstance {
    private static final Logger LOG = Logger.getLogger(AizEndBossInstance.class.getName());

    // ===== Routine constants (ROM: AIZ_EndBossIndex, stride 2) =====
    private static final int ROUTINE_INIT = 0;
    private static final int ROUTINE_EMERGE = 2;
    private static final int ROUTINE_REVEALED = 4;
    private static final int ROUTINE_HOVER = 6;
    private static final int ROUTINE_ATTACK_WAIT = 8;
    private static final int ROUTINE_RE_EMERGE = 10;
    private static final int ROUTINE_CAMERA_SCROLL = 12;
    private static final int ROUTINE_MOVE_WAIT = 14;
    private static final int ROUTINE_DEFEATED = 16;

    // ===== Boss constants =====
    private static final int HIT_COUNT = 8;
    private static final int COLLISION_SIZE = 0x10;     // ROM: ObjDat_AIZEndBoss collision $10
    private static final int COLLISION_FLAGS_ACTIVE = 0x16; // ROM: move.b #$16,collision_flags
    private static final int INVULN_TIME = 0x20;        // ROM: move.b #$20,$20(a0)

    // ===== Character-specific data (ROM: AIZBossSonicDat / AIZBossKnuxDat) =====
    private static final int SONIC_CAMERA_LOCK_X = 0x4880;
    private static final int SONIC_TARGET_X = 0x48E0;
    private static final int SONIC_Y_OFFSET = 0x15A;
    private static final int KNUX_CAMERA_LOCK_X = 0x4100;
    private static final int KNUX_TARGET_X = 0x4160;
    private static final int KNUX_Y_OFFSET = 0x5DA;

    // ===== Timing constants (ROM frame counts) =====
    private static final int WAIT_BEFORE_MUSIC = 120;   // ROM: move.w #$78,$2E
    private static final int HOVER_TIME = 0x1F;          // ROM: move.w #$1F,$2E
    private static final int FIRE_TIME_SONIC = 0x2F;     // ROM: move.w #$2F,$2E
    private static final int FIRE_SIGNAL_WAIT = 0x8F;    // ROM: move.w #$8F,$2E
    private static final int RETREAT_WAIT = 0x3F;         // ROM: move.w #$3F,$2E
    private static final int REPOSITION_TIME = 0x7F;      // ROM: move.w #$7F,$2E
    private static final int POST_DEFEAT_SONIC = 0xBF;    // ROM: move.w #$BF
    private static final int POST_DEFEAT_KNUX = 0xFF;     // ROM: move.w #$FF

    // ===== Swing parameters (ROM: loc_6933A) =====
    private static final int SWING_AMPLITUDE = 0xC0;     // ROM: move.w #$C0,$3E(a0)
    private static final int SWING_INITIAL_VEL = 0xC0;   // ROM: move.w #$C0,y_vel(a0)
    private static final int SWING_ACCEL = 0x10;          // ROM: move.w #$10,$40(a0)

    // ===== Random reposition targets (ROM: word_69AC8) =====
    // Each entry: X offset from _unkFA84, Y offset from _unkFA86
    private static final int[][] REPOSITION_TARGETS = {
            {0x058, 0x76},   // angle 0
            {0x0A0, 0x46},   // angle 4
            {0x160, 0x46},   // angle 8
            {0x1A8, 0x76},   // angle $C
    };

    // ===== Palette flash colors (ROM: sub_69C5C) =====
    // Palette line 2 positions: +$08, +$0E, +$12, +$14, +$16, +$1A, +$1C
    // (color indices 4, 7, 9, 10, 11, 13, 14 within the 16-color line)
    private static final int[] FLASH_PAL_INDICES = {4, 7, 9, 10, 11, 13, 14};
    private static final int[] FLASH_NORMAL_COLORS = {0x0222, 0x0008, 0x004C, 0x0006, 0x0020, 0x0A24, 0x0622};
    private static final int[] FLASH_HIT_COLORS = {0x0AAA, 0x0AAA, 0x0AAA, 0x0CCC, 0x0EEE, 0x0666, 0x0888};

    // ===== Instance state =====
    private int cameraLockX;    // _unkFA82
    private int targetMaxX;     // _unkFA84
    private int yBase;          // _unkFA86

    private int waitTimer = -1;
    private Runnable waitCallback;

    /** ROM: angle ($26) — selects position index (0, 4, 8, or $C). */
    private int angle;
    /** ROM: bit flags in $38(a0). */
    private int flags38;
    private static final int FLAG_RENDER_FACE_RIGHT = 0x01;
    private static final int FLAG_PROPELLER_FIRE = 0x02;  // signals propellers to extend/fire
    private static final int FLAG_EMERGE_DONE = 0x04;
    private static final int FLAG_HIDDEN = 0x40;           // boss hidden (submerged)
    private static final int FLAG_SECOND_CYCLE = 0x80;     // set after first attack cycle
    private static final int FLAG_DEFEAT_STARTED = 0x10;

    /** ROM: _unkFAA2 — signals propeller arms that fire window is active. */
    private boolean fireSignalActive;
    /** ROM: _unkFAA3 — signals children that boss is defeated. */
    private boolean defeatSignal;
    /** ROM: _unkFAA8 — set when egg capsule should spawn. */
    private boolean eggCapsuleSignal;

    private boolean collisionEnabled;
    private boolean highPriorityArt;
    private int mappingFrame;

    // Swing state
    private int swingVelocity;
    private boolean swingDown;

    // Defeat sequence
    private S3kBossExplosionController defeatExplosionController;
    private boolean defeatRenderComplete;

    // Children references
    private AizEndBossArmChild leftArm;
    private AizEndBossArmChild rightArm;

    public AizEndBossInstance(ObjectSpawn spawn) {
        super(spawn, "AIZEndBoss");
    }

    // ===== Lifecycle =====

    @Override
    protected void initializeBossState() {
        state.routine = ROUTINE_INIT;
        state.hitCount = HIT_COUNT;
        waitTimer = -1;
        waitCallback = null;
        defeatRenderComplete = false;
        defeatExplosionController = null;
        fireSignalActive = false;
        defeatSignal = false;
        eggCapsuleSignal = false;
        collisionEnabled = false;
        highPriorityArt = false;
        mappingFrame = 0;
        flags38 = 0;
        angle = 0;

        // Select character-specific data (ROM: Obj_AIZEndBoss character_id check)
        PlayerCharacter character = getPlayerCharacter();
        boolean isKnuckles = (character == PlayerCharacter.KNUCKLES);
        cameraLockX = isKnuckles ? KNUX_CAMERA_LOCK_X : SONIC_CAMERA_LOCK_X;
        targetMaxX = isKnuckles ? KNUX_TARGET_X : SONIC_TARGET_X;
        yBase = isKnuckles ? KNUX_Y_OFFSET : SONIC_Y_OFFSET;
    }

    @Override
    protected int getInitialHitCount() {
        return HIT_COUNT;
    }

    @Override
    protected int getCollisionSizeIndex() {
        return COLLISION_SIZE;
    }

    @Override
    protected boolean usesDefeatSequencer() {
        return false; // Custom defeat sequence (ROM: loc_69C36 / loc_69482)
    }

    @Override
    protected int getInvulnerabilityDuration() {
        return INVULN_TIME;
    }

    @Override
    protected int getPaletteLineForFlash() {
        return -1; // Custom palette flash (ROM: sub_69C5C, palette line 2)
    }

    @Override
    protected int getBossHitSfxId() {
        return Sonic3kSfx.BOSS_HIT.id;
    }

    @Override
    protected int getBossExplosionSfxId() {
        return Sonic3kSfx.EXPLODE.id;
    }

    // ===== Collision =====

    @Override
    public int getCollisionFlags() {
        if (!collisionEnabled || state.invulnerable || state.defeated) {
            return 0;
        }
        return 0xC0 | COLLISION_SIZE;
    }

    @Override
    public void onPlayerAttack(PlayableEntity playerEntity, TouchResponseResult result) {
        if (state.invulnerable || state.defeated) {
            return;
        }
        state.hitCount--;
        state.invulnerabilityTimer = INVULN_TIME;
        state.invulnerable = true;
        services().playSfx(Sonic3kSfx.BOSS_HIT.id);
        onHitTaken(state.hitCount);

        if (state.hitCount <= 0) {
            state.hitCount = 0;
            state.defeated = true;
            services().gameState().addScore(1000);
            onDefeatStarted();
        }
    }

    @Override
    protected void onHitTaken(int remainingHits) {
        // Hit reaction is handled by invulnerability flash in updateCustomFlash()
    }

    // ===== Main update loop =====

    @Override
    protected void updateBossLogic(int frameCounter, PlayableEntity playerEntity) {
        // Custom palette flash on hit
        if (state.invulnerable) {
            updateCustomFlash();
            state.invulnerabilityTimer--;
            if (state.invulnerabilityTimer <= 0) {
                state.invulnerable = false;
                restoreNormalPalette();
                if (!state.defeated) {
                    collisionEnabled = true;
                }
            }
        }

        // Defeat explosion controller
        if (defeatExplosionController != null && !defeatExplosionController.isFinished()) {
            defeatExplosionController.tick();
            spawnPendingExplosions();
        }

        switch (state.routine) {
            case ROUTINE_INIT -> updateInit();
            case ROUTINE_EMERGE, ROUTINE_RE_EMERGE -> updateEmerge();
            case ROUTINE_REVEALED -> updateRevealed();
            case ROUTINE_HOVER -> updateHover();
            case ROUTINE_ATTACK_WAIT -> updateAttackWait();
            case ROUTINE_CAMERA_SCROLL -> updateCameraScroll();
            case ROUTINE_MOVE_WAIT -> updateMoveWait();
            case ROUTINE_DEFEATED -> updateDefeated();
        }
    }

    // ===== Routine implementations =====

    /** ROM: Obj_AIZEndBossWait — Wait for camera to reach arena, then lock and load art. */
    private void updateInit() {
        int cameraX = services().camera().getX();
        if (cameraX < cameraLockX) {
            return;
        }

        // Lock camera at boss arena (ROM: loc_691D4)
        services().camera().setMinX((short) cameraX);
        services().camera().setMaxX((short) cameraX);

        // Set Boss_flag to lock screen (ROM: st (Boss_flag).w)
        Sonic3kAIZEvents events = getAizEvents();
        if (events != null) {
            events.setBossFlag(true);
        }
        services().gameState().setCurrentBossId(0x92);

        // Fade out current music
        services().fadeOutMusic();

        // Load boss palette (ROM: PalLoad_Line1, Pal_AIZEndBoss → palette line 2)
        loadBossPalette();

        // Transition to wait-then-play-music
        waitTimer = WAIT_BEFORE_MUSIC;
        waitCallback = this::startBossMusic;
        state.routine = ROUTINE_EMERGE; // Will be overridden after wait
        // But first we wait — handle via the emerge routine checking waitTimer

        // Actually, the ROM flows: lock → Obj_Wait($78) → Obj_AIZEndBossMusic → Obj_AIZEndBossMain
        // Let's use ROUTINE_ATTACK_WAIT as a generic "wait" state for the pre-music delay
        state.routine = ROUTINE_ATTACK_WAIT;
    }

    /** ROM: Obj_AIZEndBossMusic — Play boss music, transition to main init. */
    private void startBossMusic() {
        services().playMusic(Sonic3kMusic.BOSS.id);
        doMainInit();
    }

    /**
     * ROM: Obj_AIZEndBossInit (routine 0) — Set up children and attributes.
     * Called after music starts playing.
     */
    private void doMainInit() {
        // ROM: SetUp_ObjAttributes, ObjDat_AIZEndBoss
        // collision_property = 8 (already set)
        // render_flags bit 0 = 1 (face right)
        flags38 |= FLAG_RENDER_FACE_RIGHT;

        // Spawn Robotnik ship child (ROM: Child1_MakeRoboShip, subtype=8, offset 0,-$14)
        // TODO: Spawn ship child when Obj_RobotnikShip is implemented

        // Spawn 2 arm children (ROM: ChildObjDat_69D18)
        // Left arm at offset +$14, -4 (subtype 0)
        // Right arm at offset -$14, -4 (subtype != 0)
        spawnArmChildren();

        // Begin first emerge sequence (ROM: loc_692A0)
        beginEmerge(false);
    }

    /** ROM: loc_692A0 — Begin submerge/emerge cycle. */
    private void beginEmerge(boolean isReEmerge) {
        state.routine = isReEmerge ? ROUTINE_RE_EMERGE : ROUTINE_EMERGE;
        services().playSfx(Sonic3kSfx.WATERFALL_SPLASH.id);

        // ROM: bset #3,$38 ; bset #6,$38 — hidden + emerge flag
        flags38 |= FLAG_EMERGE_DONE | FLAG_HIDDEN;

        // Clear collision during emergence (ROM: clr.b collision_flags)
        collisionEnabled = false;

        // Restore normal palette (ROM: bsr.w sub_69C94)
        restoreNormalPalette();

        // Set facing based on angle (ROM: cmpi.w #8,angle)
        if (angle >= 8) {
            flags38 &= ~FLAG_RENDER_FACE_RIGHT;
        } else {
            flags38 |= FLAG_RENDER_FACE_RIGHT;
        }

        // Spawn waterfall splash child
        // TODO: Spawn splash child when implemented
        // ROM: ChildObjDat_69D2E, subtype = isReEmerge ? 2 : 0

        // Set up emerge animation
        emergeAnimFrame = 0;
        emergeAnimTimer = 0;
        waitTimer = -1;
        waitCallback = this::onEmergeComplete;
    }

    // Emerge animation state (ROM: byte_69D98 — flickering between frame $2B and frame 0)
    private int emergeAnimFrame;
    private int emergeAnimTimer;
    // ROM animation: alternating $2B (hidden) and 0 (visible) with delays
    // Simplified: flicker for ~40 frames then become visible
    private static final int EMERGE_FLICKER_DURATION = 40;

    /** ROM: loc_692E2 — Emerge animation (flickering reveal). */
    private void updateEmerge() {
        if (waitTimer > 0) {
            waitTimer--;
            return;
        }
        if (waitTimer == 0) {
            waitTimer = -1;
            if (waitCallback != null) {
                Runnable cb = waitCallback;
                waitCallback = null;
                cb.run();
                return;
            }
        }

        // Animate emerge flicker
        emergeAnimTimer++;
        if (emergeAnimTimer < EMERGE_FLICKER_DURATION) {
            // ROM: alternates between mapping_frame $2B (invisible) and 0 (visible)
            // Every 2 frames toggle visibility
            boolean visible = (emergeAnimTimer % 4) < 2;
            if (visible) {
                flags38 &= ~FLAG_HIDDEN;
                mappingFrame = 0;
            } else {
                flags38 |= FLAG_HIDDEN;
            }
        } else {
            // Emerge complete — call the callback
            onEmergeComplete();
        }
    }

    /** ROM: loc_69302 — Post-emerge: become visible, hittable, spawn flame column. */
    private void onEmergeComplete() {
        state.routine = ROUTINE_REVEALED;
        flags38 &= ~FLAG_HIDDEN;
        highPriorityArt = true;

        // ROM: move.b #$16,collision_flags — becomes hittable
        collisionEnabled = true;

        // Spawn flame column child (ROM: ChildObjDat_69D36, offset 0,-$30)
        // TODO: Spawn flame column when implemented

        // Set callback to transition to hover after revealed animation
        revealedAnimTimer = 0;
        waitCallback = this::beginHover;
    }

    private int revealedAnimTimer;
    // ROM: byte_69DB3 — multi-delay animation frames $1B(0), $1B(4), $1C(5), $1D(6), 0(0)
    private static final int REVEALED_ANIM_DURATION = 16; // sum of delays: 1+5+6+7+1 = 20

    /** ROM: loc_6932C — Revealed animation. */
    private void updateRevealed() {
        revealedAnimTimer++;
        // Animate through reveal frames
        if (revealedAnimTimer < 1) {
            mappingFrame = 0x1B;
        } else if (revealedAnimTimer < 6) {
            mappingFrame = 0x1B;
        } else if (revealedAnimTimer < 12) {
            mappingFrame = 0x1C;
        } else if (revealedAnimTimer < 19) {
            mappingFrame = 0x1D;
        } else {
            mappingFrame = 0;
            if (waitCallback != null) {
                Runnable cb = waitCallback;
                waitCallback = null;
                cb.run();
            }
        }
    }

    /** ROM: loc_6933A — Set up hover oscillation. */
    private void beginHover() {
        state.routine = ROUTINE_HOVER;
        waitTimer = HOVER_TIME;
        waitCallback = this::onHoverComplete;

        // ROM: Swing parameters
        swingVelocity = SWING_INITIAL_VEL;
        state.yVel = SWING_INITIAL_VEL;
        swingDown = false;
        flags38 &= ~FLAG_RENDER_FACE_RIGHT; // ROM: bclr #0,$38
    }

    /** ROM: loc_69368 — Hovering with sine oscillation. */
    private void updateHover() {
        // ROM: Swing_UpAndDown
        SwingMotion.Result swingResult = SwingMotion.update(
                SWING_ACCEL, swingVelocity, SWING_AMPLITUDE, swingDown);
        swingVelocity = swingResult.velocity();
        swingDown = swingResult.directionDown();
        state.yVel = swingVelocity;

        // ROM: MoveSprite2
        applyVelocity();

        // Countdown and callback
        if (waitTimer > 0) {
            waitTimer--;
        } else if (waitTimer == 0) {
            waitTimer = -1;
            if (waitCallback != null) {
                Runnable cb = waitCallback;
                waitCallback = null;
                cb.run();
            }
        }
    }

    /** ROM: loc_6937E — Post-hover: signal propellers to fire or prepare for retreat. */
    private void onHoverComplete() {
        // ROM: bset #1,$38 — signal propellers
        flags38 |= FLAG_PROPELLER_FIRE;

        if ((flags38 & FLAG_SECOND_CYCLE) == 0) {
            // First cycle: fire phase
            // ROM: move.w #4,angle ; move.w #$2F,$2E ; callback=loc_693DC
            angle = 4;
            waitTimer = FIRE_TIME_SONIC;
            waitCallback = this::onFireTimerExpired;
        } else {
            // Second cycle (post-defeat path): longer wait
            // ROM: Sonic=$BF, Knuckles=$FF
            PlayerCharacter character = getPlayerCharacter();
            waitTimer = (character == PlayerCharacter.KNUCKLES) ? POST_DEFEAT_KNUX : POST_DEFEAT_SONIC;
            waitCallback = this::beginRetreat;
        }

        state.routine = ROUTINE_HOVER; // Continue hovering during fire window
    }

    /** ROM: loc_693DC — Fire signal active, propellers spawn projectiles. */
    private void onFireTimerExpired() {
        // ROM: st (_unkFAA2).w — signal propeller arms to fire
        fireSignalActive = true;
        waitTimer = FIRE_SIGNAL_WAIT;
        waitCallback = this::beginRetreat;
    }

    /** ROM: loc_693C0 — Retreat into water. */
    private void beginRetreat() {
        state.routine = ROUTINE_ATTACK_WAIT;
        waitTimer = RETREAT_WAIT;
        waitCallback = this::beginReSubmerge;
        // ROM: andi.b #$F5,$38 — clear bits 1 and 2
        flags38 &= ~(FLAG_PROPELLER_FIRE | FLAG_EMERGE_DONE);
        fireSignalActive = false;
    }

    /** ROM: loc_693FA — Re-submerge (splash + move to new position). */
    private void beginReSubmerge() {
        state.routine = ROUTINE_RE_EMERGE;
        services().playSfx(Sonic3kSfx.WATERFALL_SPLASH.id);

        // Clear collision while submerged
        collisionEnabled = false;
        restoreNormalPalette();

        // Spawn waterfall splash with subtype 2
        // TODO: Spawn falling splash child

        // Set up emerge animation
        emergeAnimFrame = 0;
        emergeAnimTimer = 0;
        waitCallback = this::onReSubmergeComplete;
    }

    /** ROM: loc_6942A — After submerging, decide next phase. */
    private void onReSubmergeComplete() {
        if ((flags38 & FLAG_SECOND_CYCLE) == 0) {
            // First time: scroll camera, set second cycle flag
            state.routine = ROUTINE_CAMERA_SCROLL;
            flags38 |= FLAG_SECOND_CYCLE;
        } else {
            // Second time: move to new position directly
            state.routine = ROUTINE_MOVE_WAIT;
        }

        // ROM: bclr #7,art_tile — clear high priority
        highPriorityArt = false;
        mappingFrame = 0;

        // Pick random reposition target (ROM: loc_69A66)
        selectRandomPosition();

        waitCallback = this::loopBackToEmerge;
    }

    /** ROM: loc_69456 — Incrementally scroll camera right during reposition. */
    private void updateCameraScroll() {
        // ROM: addq.w #2,(Camera_min_X_pos) until >= _unkFA84
        int camMinX = services().camera().getMinX() & 0xFFFF;
        if (camMinX < targetMaxX) {
            camMinX += 2;
            services().camera().setMinX((short) camMinX);
            int camMaxX = (services().camera().getMaxX() & 0xFFFF) + 2;
            services().camera().setMaxX((short) camMaxX);
        }

        // Also do move+wait
        updateMoveWait();
    }

    /** ROM: loc_6946A — Move toward target position and count down wait timer. */
    private void updateMoveWait() {
        applyVelocity();

        if (waitTimer > 0) {
            waitTimer--;
        } else if (waitTimer == 0) {
            waitTimer = -1;
            if (waitCallback != null) {
                Runnable cb = waitCallback;
                waitCallback = null;
                cb.run();
            }
        }
    }

    /** ROM: loc_69476 — Loop back to emerge for next attack cycle. */
    private void loopBackToEmerge() {
        state.xVel = 0;
        state.yVel = 0;
        beginEmerge(true);
    }

    /** ROM: loc_693F0 — Generic wait state (countdown + hit check). */
    private void updateAttackWait() {
        if (waitTimer > 0) {
            waitTimer--;
        } else if (waitTimer == 0) {
            waitTimer = -1;
            if (waitCallback != null) {
                Runnable cb = waitCallback;
                waitCallback = null;
                cb.run();
            }
        }
    }

    // ===== Defeat sequence =====

    @Override
    protected void onDefeatStarted() {
        state.routine = ROUTINE_DEFEATED;
        state.xVel = 0;
        state.yVel = 0;
        waitTimer = -1;
        waitCallback = null;
        flags38 |= FLAG_DEFEAT_STARTED | FLAG_HIDDEN;
        collisionEnabled = false;
        mappingFrame = 0;

        // Signal children
        defeatSignal = true;

        // ROM: BossDefeated_StopTimer — timer stop handled by gameState

        // ROM: Wait_FadeToLevelMusic then callback loc_69482
        services().fadeOutMusic();

        // Spawn 6 debris explosion children (ROM: ChildObjDat_69D66)
        defeatExplosionController = new S3kBossExplosionController(state.x, state.y, 0);

        // After explosions: spawn egg capsule
        defeatPhaseTimer = 60; // Wait for explosions before capsule
    }

    private int defeatPhaseTimer;

    private void updateDefeated() {
        if (defeatExplosionController != null && !defeatExplosionController.isFinished()) {
            defeatExplosionController.tick();
            spawnPendingExplosions();
        }

        if (defeatPhaseTimer > 0) {
            defeatPhaseTimer--;
            if (defeatPhaseTimer == 0) {
                spawnEggCapsuleAndFinish();
            }
        }
    }

    /** ROM: loc_694A4 — Spawn Egg Capsule, clear Boss_flag, restore level music. */
    private void spawnEggCapsuleAndFinish() {
        eggCapsuleSignal = true;

        // Clear Boss_flag (ROM: clr.b (Boss_flag).w)
        Sonic3kAIZEvents events = getAizEvents();
        if (events != null) {
            events.setBossFlag(false);
        }
        services().gameState().setCurrentBossId(0);

        // ROM: Load PLC_EggCapsule + PLC_Animals + PLC_Explosion
        // TODO: Spawn Obj_EggCapsule when implemented

        // Restore camera and player control
        int newMaxX = targetMaxX + 0x158;
        services().camera().setMaxX((short) newMaxX);

        // Use S3kBossDefeatSignpostFlow for post-defeat handling (signpost, results, transition)
        // apparentAct = 1 (act 2 display), cleanup callback restores palette
        ObjectManager objectManager = services().objectManager();
        if (objectManager != null) {
            S3kBossDefeatSignpostFlow defeatFlow = new S3kBossDefeatSignpostFlow(
                    state.x, 1, null);
            objectManager.addDynamicObject(defeatFlow);
        }

        // Mark render as complete since boss is now hidden
        defeatRenderComplete = true;
    }

    // ===== Random position selection (ROM: loc_69A66) =====

    private void selectRandomPosition() {
        int newAngle;
        do {
            newAngle = (ThreadLocalRandom.current().nextInt(4)) * 4; // 0, 4, 8, or $C
        } while (newAngle == angle);
        angle = newAngle;

        int targetIndex = angle / 4;
        int targetX = targetMaxX + REPOSITION_TARGETS[targetIndex][0];
        int targetY = yBase + REPOSITION_TARGETS[targetIndex][1];

        // Calculate velocity to reach target in REPOSITION_TIME+1 frames (ROM: 128 frames)
        int frames = REPOSITION_TIME + 1;
        state.xVel = ((targetX - state.x) << 8) / frames;
        state.yVel = ((targetY - state.y) << 8) / frames;

        waitTimer = REPOSITION_TIME;

        // Set facing based on X velocity direction
        if (state.xVel < 0) {
            flags38 &= ~FLAG_RENDER_FACE_RIGHT;
        } else {
            flags38 |= FLAG_RENDER_FACE_RIGHT;
        }
    }

    // ===== Velocity & position helpers =====

    private void applyVelocity() {
        // 16:8 fixed-point position update (ROM: MoveSprite2)
        int xPos24 = (state.x << 8) | (state.xFixed & 0xFF);
        xPos24 += state.xVel;
        state.x = xPos24 >> 8;
        state.xFixed = xPos24 & 0xFF;

        int yPos24 = (state.y << 8) | (state.yFixed & 0xFF);
        yPos24 += state.yVel;
        state.y = yPos24 >> 8;
        state.yFixed = yPos24 & 0xFF;
    }

    // ===== Custom palette flash (ROM: sub_69C5C + sub_69BE2) =====

    /**
     * ROM: sub_69C5C — Custom palette flash on palette line 2.
     * Alternates between normal and flash colors based on invulnerability timer bit 0.
     */
    private void updateCustomFlash() {
        if (!state.invulnerable) return;
        var level = services().currentLevel();
        if (level == null || level.getPaletteCount() <= 2) return;

        Palette pal = level.getPalette(2);
        boolean flash = (state.invulnerabilityTimer & 1) == 0;
        int[] colors = flash ? FLASH_HIT_COLORS : FLASH_NORMAL_COLORS;
        for (int i = 0; i < FLASH_PAL_INDICES.length; i++) {
            byte[] bytes = {(byte) ((colors[i] >> 8) & 0xFF), (byte) (colors[i] & 0xFF)};
            pal.getColor(FLASH_PAL_INDICES[i]).fromSegaFormat(bytes, 0);
        }
        var gm = services().graphicsManager();
        if (gm.isGlInitialized()) {
            gm.cachePaletteTexture(pal, 2);
        }
    }

    private void restoreNormalPalette() {
        var level = services().currentLevel();
        if (level == null || level.getPaletteCount() <= 2) return;

        Palette pal = level.getPalette(2);
        for (int i = 0; i < FLASH_PAL_INDICES.length; i++) {
            byte[] bytes = {(byte) ((FLASH_NORMAL_COLORS[i] >> 8) & 0xFF),
                    (byte) (FLASH_NORMAL_COLORS[i] & 0xFF)};
            pal.getColor(FLASH_PAL_INDICES[i]).fromSegaFormat(bytes, 0);
        }
        var gm = services().graphicsManager();
        if (gm.isGlInitialized()) {
            gm.cachePaletteTexture(pal, 2);
        }
    }

    private void loadBossPalette() {
        try {
            byte[] line = services().rom().readBytes(
                    Sonic3kConstants.PAL_AIZ_END_BOSS_ADDR, 32);
            services().updatePalette(2, line);
        } catch (Exception e) {
            LOG.fine(() -> "AizEndBossInstance.loadBossPalette: " + e.getMessage());
        }
    }

    // ===== Child spawning =====

    private void spawnArmChildren() {
        ObjectManager objectManager = services().objectManager();
        if (objectManager == null) return;

        // Left arm (subtype 0): offset +$14, -4
        leftArm = new AizEndBossArmChild(this, 0x14, -4, 0);
        objectManager.addDynamicObject(leftArm);
        childComponents.add(leftArm);

        // Right arm (subtype 1): offset -$14, -4
        rightArm = new AizEndBossArmChild(this, -0x14, -4, 1);
        objectManager.addDynamicObject(rightArm);
        childComponents.add(rightArm);
    }

    private void spawnPendingExplosions() {
        if (defeatExplosionController == null) return;
        ObjectManager objectManager = services().objectManager();
        if (objectManager == null) return;

        for (var exp : defeatExplosionController.drainPendingExplosions()) {
            S3kBossExplosionChild explosion = new S3kBossExplosionChild(
                    exp.x(), exp.y());
            objectManager.addDynamicObject(explosion);
            if (exp.playSfx()) {
                services().playSfx(Sonic3kSfx.EXPLODE.id);
            }
        }
    }

    // ===== Rendering =====

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed() || defeatRenderComplete) return;
        if ((flags38 & FLAG_HIDDEN) != 0) return;

        ObjectRenderManager renderManager = services().renderManager();
        if (renderManager == null) return;

        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic3kObjectArtKeys.AIZ_END_BOSS);
        if (renderer == null || !renderer.isReady()) return;

        boolean hFlip = (flags38 & FLAG_RENDER_FACE_RIGHT) != 0;
        renderer.drawFrameIndex(mappingFrame, state.x, state.y, hFlip, false);
    }

    @Override
    public boolean isHighPriority() {
        return highPriorityArt;
    }

    @Override
    public int getPriorityBucket() {
        // ROM: ObjDat_AIZEndBoss priority $0280 → $280/$80 = bucket 5
        return 5;
    }

    // ===== Accessors for children =====

    public boolean isFireSignalActive() {
        return fireSignalActive;
    }

    public boolean isPropellerFireRequested() {
        return (flags38 & FLAG_PROPELLER_FIRE) != 0;
    }

    public void clearPropellerFire() {
        flags38 &= ~FLAG_PROPELLER_FIRE;
    }

    public boolean isDefeatSignal() {
        return defeatSignal;
    }

    public boolean isHidden() {
        return (flags38 & FLAG_HIDDEN) != 0;
    }

    public int getAngle() {
        return angle;
    }

    public int getMappingFrame() {
        return mappingFrame;
    }

    // ===== Helpers =====

    private Sonic3kAIZEvents getAizEvents() {
        try {
            return ((Sonic3kLevelEventManager) services().levelEventProvider()).getAizEvents();
        } catch (Exception e) {
            return null;
        }
    }

    private PlayerCharacter getPlayerCharacter() {
        try {
            return ((Sonic3kLevelEventManager) services().levelEventProvider()).getPlayerCharacter();
        } catch (Exception e) {
            return PlayerCharacter.SONIC_AND_TAILS;
        }
    }
}
