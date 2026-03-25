package com.openggf.game.sonic1.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic1.audio.Sonic1Music;

import com.openggf.level.objects.ObjectSpawn;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Object 0x3D — Green Hill Zone Boss (Eggman with swinging ball on chain).
 * ROM: _incObj/3D Boss - Green Hill (part 1).asm, (part 2).asm
 *
 * State machine (BGHZ_ShipIndex, routineSecondary):
 *   0: DESCENT      — Enter from top, oscillate Y via sine, stop at Y = boss_ghz_y + 0x38
 *   2: APPROACH     — Move left to boss_ghz_x + 0xA0, spawn ball, timer = 0x77
 *   4: COMBAT_MOVE  — Move sideways with timer 0x77 or 0x7F per direction
 *   6: COMBAT_REVERSE — Pause at direction change, toggle direction, timer 0x3F, back to 4
 *   8: DEFEAT_WAIT  — Timer 0xB3 countdown with explosions
 *  10: ASCENT       — Multi-stage upward movement, play zone music at timer = 0x30
 *  12: ESCAPE       — X vel 0x400, Y vel -0x40, extend camera right, delete when off-screen
 *
 * Face and flame are rendered as overlays on the ship (not separate object instances).
 */
public class Sonic1GHZBossInstance extends AbstractS1EggmanBossInstance {

    // State machine constants (routineSecondary values, matching ROM's even-numbered index)
    private static final int STATE_DESCENT = 0;
    private static final int STATE_APPROACH = 2;
    private static final int STATE_COMBAT_MOVE = 4;
    private static final int STATE_COMBAT_REVERSE = 6;
    private static final int STATE_DEFEAT_WAIT = 8;
    private static final int STATE_ASCENT = 10;
    private static final int STATE_ESCAPE = 12;

    // Position constants from DynamicLevelEvents.asm
    private static final int BOSS_GHZ_X = 0x2960;
    private static final int BOSS_GHZ_Y = 0x300;
    private static final int BOSS_GHZ_END = BOSS_GHZ_X + 0x160; // $2AC0

    // Approach target: boss_ghz_x + $A0 = $2A00
    private static final int APPROACH_TARGET_X = BOSS_GHZ_X + 0xA0;

    // Y stop target for descent: boss_ghz_y + $38 = $338
    private static final int DESCENT_TARGET_Y = BOSS_GHZ_Y + 0x38;

    // Timers
    private static final int BALL_SPAWN_TIMER = 0x77;
    private static final int COMBAT_TIMER_NORMAL = 0x3F;
    private static final int COMBAT_TIMER_DOUBLE = 0x7F;
    private static final int DEFEAT_TIMER = 0xB3;

    // Sine oscillation
    private int sineAngle; // objoff_3F — sine counter for Y oscillation

    // Combat timer
    private int timer; // objoff_3C

    // Wrecking ball child
    private GHZBossWreckingBall wreckingBall;

    public Sonic1GHZBossInstance(ObjectSpawn spawn) {
        super(spawn, "GHZ Boss");
    }

    @Override
    protected void initializeBossState() {
        state.routineSecondary = STATE_DESCENT;
        state.xVel = 0;
        state.yVel = 0x100; // Move ship down initially

        // Store initial position in fixed-point (objoff_30/objoff_38)
        // These are the "base" positions that BossMove updates
        state.xFixed = state.x << 16;
        state.yFixed = state.y << 16;

        sineAngle = 0;
        timer = 0;
        faceAnim = Sonic1BossAnimations.ANIM_FACE_NORMAL_1;
        flameAnim = Sonic1BossAnimations.ANIM_BLANK;
    }

    @Override
    protected int getInitialHitCount() {
        return 8; // obColProp = 8
    }

    @Override
    protected int getCollisionSizeIndex() {
        // ROM: obColType = $F (category 0, size index 0x0F = 24x24)
        return 0x0F;
    }

    @Override
    protected boolean usesDefeatSequencer() {
        return false; // GHZ boss has custom defeat logic in states 8-12
    }

    @Override
    protected void onHitTaken(int remainingHits) {
        // ROM: sfx_HitBoss is played by BossHitHandler
        // Face shows hit animation
        faceAnim = Sonic1BossAnimations.ANIM_FACE_HIT;
    }

    @Override
    protected void onDefeatStarted() {
        // ROM: loc_1784C — boss defeated, transition to state 8
        state.routineSecondary = STATE_DEFEAT_WAIT;
        timer = DEFEAT_TIMER; // $B3 = 179 frames
    }

    @Override
    protected void updateBossLogic(int frameCounter, PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        switch (state.routineSecondary) {
            case STATE_DESCENT -> updateDescent();
            case STATE_APPROACH -> updateApproach();
            case STATE_COMBAT_MOVE -> updateCombatMove();
            case STATE_COMBAT_REVERSE -> updateCombatReverse();
            case STATE_DEFEAT_WAIT -> updateDefeatWait(frameCounter);
            case STATE_ASCENT -> updateAscent();
            case STATE_ESCAPE -> updateEscape();
        }

        // Apply sine Y oscillation and update display position (loc_177E6)
        updateSineOscillation();

        // Update face animation based on state
        updateFaceAnimation(player);

        // Update flame animation based on movement
        updateFlameAnimation();
    }

    // === State 0: DESCENT ===
    // ROM: BGHZ_ShipStart
    private void updateDescent() {
        state.yVel = 0x100;
        bossMove();

        if ((state.yFixed >> 16) >= DESCENT_TARGET_Y) {
            state.yFixed = DESCENT_TARGET_Y << 16;
            state.yVel = 0;
            state.routineSecondary = STATE_APPROACH;
        }
    }

    // === State 2: APPROACH ===
    // ROM: BGHZ_MakeBall
    private void updateApproach() {
        state.xVel = -0x100;
        state.yVel = -0x40;
        bossMove();

        if ((state.xFixed >> 16) <= APPROACH_TARGET_X) {
            state.xFixed = APPROACH_TARGET_X << 16;
            state.xVel = 0;
            state.yVel = 0;
            state.routineSecondary = STATE_COMBAT_MOVE;

            // Spawn wrecking ball
            spawnWreckingBall();

            timer = BALL_SPAWN_TIMER; // $77
        }
    }

    // === State 4: COMBAT_MOVE ===
    // ROM: BGHZ_ShipMove
    private void updateCombatMove() {
        timer--;
        if (timer < 0) {
            // Timer expired — advance to reverse state
            state.routineSecondary = STATE_COMBAT_REVERSE;
            timer = COMBAT_TIMER_NORMAL; // $3F

            // Set velocity for next movement direction
            state.xVel = 0x100;

            // Check if at approach target — if so, use double timer and slower speed
            if ((state.xFixed >> 16) == APPROACH_TARGET_X) {
                timer = COMBAT_TIMER_DOUBLE; // $7F
                state.xVel = 0x40;
            }

            // ROM: BGHZ_Reverse — if status bit 0 is NOT set, negate velocity
            if ((state.renderFlags & 1) == 0) {
                state.xVel = -state.xVel;
            }
        }
    }

    // === State 6: COMBAT_REVERSE ===
    // ROM: loc_17954
    private void updateCombatReverse() {
        timer--;
        if (timer < 0) {
            // Toggle direction
            state.renderFlags ^= 1; // bchg #0,obStatus
            timer = COMBAT_TIMER_NORMAL; // $3F
            state.routineSecondary = STATE_COMBAT_MOVE;
            state.xVel = 0;
        } else {
            bossMove();
        }
    }

    // === State 8: DEFEAT_WAIT ===
    // ROM: loc_1797A
    private void updateDefeatWait(int frameCounter) {
        timer--;
        if (timer < 0) {
            // Timer expired — start ascent
            state.renderFlags |= 1; // bset #0,obStatus — face right
            state.renderFlags &= ~0x80; // bclr #7,obStatus
            state.xVel = 0;
            state.routineSecondary = STATE_ASCENT;
            timer = -0x26; // Start ascent counter at -$26

            // ROM: v_bossstatus = 1 (boss defeated flag)
            services().gameState().setCurrentBossId(0);
        } else {
            // Spawn explosions every 8 frames (BossDefeated)
            if ((frameCounter & 7) == 0) {
                spawnDefeatExplosion();
            }
        }
    }

    // === State 10: ASCENT ===
    // ROM: loc_179AC — multi-stage upward movement
    private void updateAscent() {
        timer++;

        if (timer == 0) {
            // Timer just reached 0 — clear Y velocity
            state.yVel = 0;
        } else if (timer < 0) {
            // Timer negative — accelerate upward
            state.yVel += 0x18;
        } else if (timer < 0x30) {
            // Timer 1-$2F — decelerate
            state.yVel -= 8;
        } else if (timer == 0x30) {
            // Timer = $30 — stop and play zone music
            state.yVel = 0;
            services().playMusic(Sonic1Music.GHZ.id);
        } else if (timer >= 0x38) {
            // Timer >= $38 — advance to escape
            state.routineSecondary = STATE_ESCAPE;
        }

        bossMove();
    }

    // === State 12: ESCAPE ===
    // ROM: loc_179F6
    private void updateEscape() {
        state.xVel = 0x400;
        state.yVel = -0x40;

        if (runCameraExpandEscape(BOSS_GHZ_END)) {
            return; // Destroyed (off-screen)
        }

        bossMove();
    }

    /**
     * Apply sine oscillation to Y position and update display coordinates.
     * ROM: loc_177E6
     *   CalcSine(objoff_3F) >> 6 + objoff_38 -> obY
     *   objoff_30 -> obX
     *   objoff_3F += 2
     */
    private void updateSineOscillation() {
        int sinVal = TrigLookupTable.sinHex(sineAngle & 0xFF);
        int yOffset = sinVal >> 6;

        state.y = (state.yFixed >> 16) + yOffset;
        state.x = state.xFixed >> 16;

        sineAngle = (sineAngle + 2) & 0xFF;
    }

    /**
     * Update face animation based on boss state.
     * ROM: BGHZ_FaceMain (routine 4)
     */
    private void updateFaceAnimation(AbstractPlayableSprite player) {
        if (state.routineSecondary >= STATE_DEFEAT_WAIT) {
            // After defeat: panic face, then defeat face during ascent/escape
            if (state.routineSecondary >= STATE_ASCENT) {
                faceAnim = Sonic1BossAnimations.ANIM_FACE_PANIC;
            } else {
                faceAnim = Sonic1BossAnimations.ANIM_FACE_DEFEAT;
            }
            return;
        }

        // During combat: check if being hit (invulnerable = flash)
        if (state.invulnerable) {
            faceAnim = Sonic1BossAnimations.ANIM_FACE_HIT;
            return;
        }

        // ROM: Check if player is hurt (routine >= 4) — laugh
        if (player != null && player.isHurt()) {
            faceAnim = Sonic1BossAnimations.ANIM_FACE_LAUGH;
            return;
        }

        // Default: normal face (speed 1 during approach, varies)
        if (state.routineSecondary == STATE_APPROACH &&
                (state.xFixed >> 16) == APPROACH_TARGET_X) {
            faceAnim = Sonic1BossAnimations.ANIM_FACE_LAUGH;
        } else {
            faceAnim = Sonic1BossAnimations.ANIM_FACE_NORMAL_1;
        }
    }

    /**
     * Update flame animation based on movement.
     * ROM: BGHZ_FlameMain (routine 6)
     */
    private void updateFlameAnimation() {
        if (state.routineSecondary == STATE_ESCAPE) {
            flameAnim = Sonic1BossAnimations.ANIM_ESCAPE_FLAME;
        } else if (state.xVel != 0) {
            flameAnim = Sonic1BossAnimations.ANIM_FLAME_1;
        } else {
            flameAnim = Sonic1BossAnimations.ANIM_BLANK;
        }
    }

    private void spawnWreckingBall() {
        if (wreckingBall != null) {
            return; // Already spawned
        }
        wreckingBall = new GHZBossWreckingBall(this);
        childComponents.add(wreckingBall);
        if (services().objectManager() != null) {
            services().objectManager().addDynamicObject(wreckingBall);
        }
    }

    @Override
    public int getPriorityBucket() {
        return 3; // ROM: obPriority = 3
    }

    @Override
    public int getCollisionFlags() {
        // No collision during defeat states
        if (state.routineSecondary >= STATE_DEFEAT_WAIT) {
            return 0;
        }
        return super.getCollisionFlags();
    }
}
