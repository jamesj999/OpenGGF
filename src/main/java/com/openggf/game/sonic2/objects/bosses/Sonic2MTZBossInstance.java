package com.openggf.game.sonic2.objects.bosses;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.game.sonic2.audio.Sonic2Music;
import com.openggf.game.sonic2.audio.Sonic2Sfx;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.game.sonic2.objects.EggPrisonObjectInstance;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.boss.AbstractBossChild;
import com.openggf.level.objects.boss.AbstractBossInstance;
import com.openggf.level.objects.boss.BossChildComponent;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.logging.Logger;

/**
 * MTZ Act 3 Boss (Object 0x54) - Orbiting shield boss with laser attack.
 * ROM Reference: s2.asm:66680-67265 (Obj54)
 *
 * <p>The boss uses separate Boss_X/Y_pos variables (16.16 fixed-point) for
 * logical movement, with a sine-wave float applied to y_pos for display.
 * It uses multi-sprite rendering with 2 sub-sprites (Robotnik face, pod bottom).
 *
 * <p>State machine (boss_routine / 10 sub-states):
 * <ul>
 *   <li>Sub0: Descend until Y≥0x4A0, face player, start horizontal pacing</li>
 *   <li>Sub2: Horizontal pacing (X bounces 0x2AD0↔0x2BD0), advance after 2 turns</li>
 *   <li>Sub4: Decelerate to center (0x2B50), advance when stopped</li>
 *   <li>Sub6: Expand orbs (radius 0x27→0x68), advance when inner timer expires</li>
 *   <li>Sub8: Contract orbs (radius→0x27), then restart cycle (→Sub0)</li>
 *   <li>SubA: After hit - retract orbs, descend, advance to SubC</li>
 *   <li>SubC: Decision: attacks remain → re-expand + restart, else → laser dive (SubE)</li>
 *   <li>SubE: Laser dive attack (3 sub-phases: approach, dive+fire, return)</li>
 *   <li>Sub10: Defeat explosions ($EF frames), then flee</li>
 *   <li>Sub12: Flee right (xVel=$400, yVel=-$40), delete when off-screen</li>
 * </ul>
 *
 * <p>Children: 7 orbiting shield orbs (Obj53), 1 laser shooter (Obj54 subtype 6).
 */
public class Sonic2MTZBossInstance extends AbstractBossInstance {
    private static final Logger LOGGER = Logger.getLogger(Sonic2MTZBossInstance.class.getName());

    // =========================================================================
    // Position constants (ROM addresses inline in Obj54_Init)
    // =========================================================================

    /** Spawn X position. ROM: move.w #$2B50,x_pos(a0) */
    private static final int SPAWN_X = 0x2B50;
    /** Spawn Y position. ROM: move.w #$380,y_pos(a0) */
    private static final int SPAWN_Y = 0x380;
    /** Center X for return-to-center. ROM: cmpi.w #$2B50 in Sub4 */
    private static final int CENTER_X = 0x2B50;
    /** Left pacing boundary. ROM: cmpi.w #$2AD0 in Sub2 */
    private static final int BOUNDARY_LEFT = 0x2AD0;
    /** Right pacing boundary. ROM: cmpi.w #$2BD0 in Sub2 */
    private static final int BOUNDARY_RIGHT = 0x2BD0;
    /** Bottom Y for descent. ROM: cmpi.w #$4A0 in Sub0 */
    private static final int Y_BOTTOM = 0x4A0;
    /** Y threshold for deceleration. ROM: cmpi.w #$470 in Sub4/SubE */
    private static final int Y_DECEL = 0x470;
    /** Base float Y. ROM: cmpi.w #$420 in SubE phase 2 */
    private static final int Y_BASE = 0x420;

    // =========================================================================
    // Velocity constants
    // =========================================================================

    /** Horizontal pacing speed. ROM: move.w #$100 / #-$100 */
    private static final int VEL_HORIZONTAL = 0x100;
    /** Initial descent speed. ROM: move.w #$100,(Boss_Y_vel).w in Init */
    private static final int VEL_DESCEND = 0x100;
    /** Dive/retract Y speed. ROM: move.w #$180 / #-$180 */
    private static final int VEL_DIVE = 0x180;
    /** Flee X speed. ROM: move.w #$400,(Boss_X_vel).w in Sub12 */
    private static final int VEL_FLEE_X = 0x400;
    /** Flee Y speed. ROM: move.w #-$40,(Boss_Y_vel).w in Sub12 */
    private static final int VEL_FLEE_Y = -0x40;

    // =========================================================================
    // SubE (laser dive) boundaries
    // =========================================================================

    /** SubE phase 0: far-left trigger. ROM: cmpi.w #$2AF0 */
    private static final int DIVE_BOUNDARY_LEFT = 0x2AF0;
    /** SubE phase 0: far-right trigger. ROM: cmpi.w #$2BB0 */
    private static final int DIVE_BOUNDARY_RIGHT = 0x2BB0;

    // =========================================================================
    // Orb radius constants
    // =========================================================================

    /** Default outer orb radius. ROM: move.b #$27,objoff_33(a0) */
    private static final int ORB_RADIUS_DEFAULT = 0x27;
    /** Expanded outer orb radius. ROM: cmpi.b #$68,objoff_33(a0) */
    private static final int ORB_RADIUS_EXPANDED = 0x68;

    // =========================================================================
    // Timing / laser constants
    // =========================================================================

    /** Laser fire interval in frames. ROM: move.w #$1E,(Boss_Countdown).w */
    private static final int LASER_COUNTDOWN = 0x1E;
    /** Laser shots per dive attack. ROM: move.b #3,objoff_2D(a0) */
    private static final int LASER_SHOTS_PER_ATTACK = 3;
    /** SubE display pause after laser fire. ROM: move.b #$10,objoff_2F(a0) */
    private static final int LASER_FIRE_PAUSE = 0x10;
    /** Defeat timer start. ROM: move.w #$EF,(Boss_Countdown).w */
    private static final int DEFEAT_COUNTDOWN = 0xEF;
    /** Defeat explosion cutoff. ROM: cmpi.w #60,(Boss_Countdown).w */
    private static final int DEFEAT_EXPLOSION_CUTOFF = 60;
    /** Camera max X for flee phase. ROM: cmpi.w #$2BF0 */
    private static final int CAMERA_MAX_X_FLEE = 0x2BF0;

    // =========================================================================
    // Hit system constants
    // =========================================================================

    /** Invulnerability duration. ROM: move.b #$40,boss_invulnerable_time(a0) */
    private static final int INVULN_DURATION = 0x40;
    /** Initial attack cycle count. ROM: move.b #7,objoff_3E(a0) */
    private static final int INITIAL_ATTACK_CYCLES = 7;

    // =========================================================================
    // Mapping frames
    // =========================================================================

    /** Main boss body frame. ROM: move.b #2,mainspr_mapframe(a0) */
    private static final int FRAME_BODY = 2;
    /** Robotnik face sub-sprite frame. ROM: move.b #$C,sub2_mapframe(a0) */
    private static final int FRAME_FACE = 0x0C;
    /** Pod bottom sub-sprite frame. ROM: move.b #0,sub3_mapframe(a0) */
    private static final int FRAME_POD = 0;
    /** Laser shooter frame. ROM: move.b #$13,mapping_frame(a1) */
    private static final int FRAME_LASER_SHOOTER = 0x13;
    /** Angry face frame (after hit). ROM: ori.b #5 to anim byte */
    private static final int FRAME_FACE_ANGRY = 0x0D;
    /** Laughing face frame (player hurt). ROM: ori.b #4 to anim byte */
    private static final int FRAME_FACE_LAUGH = 0x0E;
    /** Defeat face frame. ROM: move.b #7 to anim byte */
    private static final int FRAME_FACE_DEFEAT = 0x0F;

    // =========================================================================
    // State fields (matching ROM RAM variables)
    // =========================================================================

    // Boss_X_pos / Boss_Y_pos as 32-bit accumulators (16.16 fixed-point)
    private int bossXFixed;
    private int bossYFixed;

    /** objoff_2B: bit7=direction (0=left,1=right), bit6=turn counter */
    private int flags2B;

    /** objoff_2C: number of orbs that have broken away */
    private int orbBreakCount;

    /** objoff_2D: laser fire count (3→0 per attack) */
    private int laserFireCount;

    /** objoff_2E: SubE sub-phase (0, 2, 4) */
    private int diveSubPhase;

    /** objoff_2F: SubE display pause timer */
    private int divePauseTimer;

    /** objoff_33: outer orb orbit radius */
    private int outerOrbRadius;

    /** objoff_39: inner orbit parameter / contraction timer */
    private int innerOrbParam;

    /** objoff_3A: orb break state (0=normal, -1=expanding, 0x80=contracting) */
    private int orbBreakState;

    /** objoff_3E: remaining attack cycles before laser dive */
    private int attackCyclesRemaining;

    /** boss_sine_count: float oscillation counter */
    private int sineCount;

    /** Boss_Countdown: shared timer for laser fire + defeat */
    private int bossCountdown;

    /** Current face animation frame index */
    private int faceFrame;

    /** Whether boss has been initialized */
    private boolean initialized;

    // Laser shooter child reference
    private MTZLaserShooter laserShooter;

    public Sonic2MTZBossInstance(ObjectSpawn spawn) {
        super(spawn, "MTZ Boss");
    }

    @Override
    protected void initializeBossState() {
        // ROM: Obj54_Init (s2.asm:66694-66753)
        state.routine = 0; // boss_routine = Sub0
        state.x = SPAWN_X;
        state.y = SPAWN_Y;

        bossXFixed = SPAWN_X << 16;
        bossYFixed = SPAWN_Y << 16;

        state.xVel = 0;     // Boss_X_vel = 0
        state.yVel = VEL_DESCEND; // Boss_Y_vel = $100

        flags2B = 0;
        orbBreakCount = 0;
        laserFireCount = 0;
        diveSubPhase = 0;
        divePauseTimer = 0;
        outerOrbRadius = ORB_RADIUS_DEFAULT;  // objoff_33 = $27
        innerOrbParam = ORB_RADIUS_DEFAULT;    // objoff_39 = $27
        orbBreakState = 0;
        attackCyclesRemaining = INITIAL_ATTACK_CYCLES; // objoff_3E = 7
        sineCount = 0x40; // boss_sine_count = $40
        bossCountdown = 0;
        faceFrame = FRAME_FACE;

        initialized = true;

        // Spawn laser shooter child
        laserShooter = new MTZLaserShooter(this);
        childComponents.add(laserShooter);
        var objectManager = services().objectManager();
        if (objectManager != null) {
            objectManager.addDynamicObject(laserShooter);
        }

        // Spawn 7 orbiting shield orbs (Obj53)
        spawnOrbs();
    }

    private void spawnOrbs() {
        // ROM: Obj53_Init spawns 7 orbs with phase offsets
        // byte_329CC: $24, $6C, $B4, $FC, $48, $90, $D8
        int[] phaseOffsets = {0x24, 0x6C, 0xB4, 0xFC, 0x48, 0x90, 0xD8};
        // byte_329D3: 0, 1, 1, 0, 1, 1, 0
        int[] tiltFlags = {0, 1, 1, 0, 1, 1, 0};

        var objectManager = services().objectManager();
        for (int i = 0; i < 7; i++) {
            MTZBossOrb orb = new MTZBossOrb(this, i, phaseOffsets[i], tiltFlags[i]);
            childComponents.add(orb);
            if (objectManager != null) {
                objectManager.addDynamicObject(orb);
            }
        }
    }

    // =========================================================================
    // Boss_MoveObject equivalent
    // =========================================================================

    /**
     * ROM: Boss_MoveObject (s2.asm:60795-60808)
     * Updates Boss_X/Y_pos using Boss_X/Y_vel in 16.16 fixed-point.
     */
    private void bossMoveObject() {
        bossXFixed += (state.xVel << 8);
        bossYFixed += (state.yVel << 8);
    }

    /** Get Boss_X_pos integer part. */
    public int getBossX() {
        return bossXFixed >> 16;
    }

    /** Get Boss_Y_pos integer part. */
    public int getBossY() {
        return bossYFixed >> 16;
    }

    // =========================================================================
    // Obj54_Float: sine wave hover
    // =========================================================================

    /**
     * ROM: Obj54_Float (s2.asm:66808-66815)
     * y_pos = Boss_Y_pos + (sin(boss_sine_count) >> 6)
     * boss_sine_count += 4
     */
    private int applyFloat() {
        int sine = TrigLookupTable.sinHex(sineCount & 0xFF);
        int offset = sine >> 6;
        sineCount = (sineCount + 4) & 0xFF;
        return offset;
    }

    /**
     * ROM: loc_328C0 (Sub12 float variant with sine_count += 2)
     */
    private int applyFloatSlow() {
        int sine = TrigLookupTable.sinHex(sineCount & 0xFF);
        int offset = sine >> 6;
        sineCount = (sineCount + 2) & 0xFF;
        return offset;
    }

    // =========================================================================
    // Common tail routines
    // =========================================================================

    /** ROM: Obj54_MoveAndShow - update position + float, then display */
    private void moveAndShow() {
        state.x = getBossX();
        state.y = getBossY() + applyFloat();
    }

    /** ROM: Obj54_Display - animate face + display (no position update) */
    private void displayOnly() {
        // Face animation handled in animateFace()
    }

    // =========================================================================
    // Main update
    // =========================================================================

    @Override
    protected void updateBossLogic(int frameCounter, PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        if (!initialized) {
            return;
        }

        switch (state.routine) {
            case 0x00 -> updateSub0Descend(player);
            case 0x02 -> updateSub2HorizontalPace();
            case 0x04 -> updateSub4ReturnToCenter();
            case 0x06 -> updateSub6ExpandOrbs();
            case 0x08 -> updateSub8ContractOrbs();
            case 0x0A -> updateSubARetractAfterHit();
            case 0x0C -> updateSubCDecision(player);
            case 0x0E -> updateSubELaserDive();
            case 0x10 -> updateSub10DefeatExplosions(frameCounter);
            case 0x12 -> updateSub12Flee();
        }

        // Check hits (except during defeat sequence)
        if (state.routine < 0x10) {
            checkHit(player);
        }
    }

    // =========================================================================
    // Sub0: Descend
    // ROM: Obj54_MainSub0 (s2.asm:66775-66805)
    // =========================================================================

    private void updateSub0Descend(AbstractPlayableSprite player) {
        bossMoveObject();

        if (getBossY() >= Y_BOTTOM) {
            state.routine = 0x02; // → Sub2
            state.yVel = 0;

            // Face player: set direction and X velocity
            flags2B &= ~0x80; // clear direction bit
            state.renderFlags &= ~1; // clear x_flip
            state.xVel = -VEL_HORIZONTAL; // default: move left

            int playerX = player.getCentreX();
            if (playerX >= getBossX()) {
                // Player is to the right
                state.xVel = VEL_HORIZONTAL;
                flags2B |= 0x80; // set direction bit (right)
                state.renderFlags |= 1; // set x_flip
            }
        }

        state.x = getBossX();
        state.y = getBossY() + applyFloat();
    }

    // =========================================================================
    // Sub2: Horizontal pacing
    // ROM: Obj54_MainSub2 (s2.asm:66818-66862)
    // =========================================================================

    private void updateSub2HorizontalPace() {
        bossMoveObject();

        boolean facingRight = (flags2B & 0x80) != 0;

        if (!facingRight) {
            // Moving left: check left boundary
            if (getBossX() < BOUNDARY_LEFT) {
                flags2B ^= 0x80; // toggle direction
                state.xVel = VEL_HORIZONTAL;
                state.renderFlags |= 1; // x_flip

                // Check turn counter (bit 6)
                if ((flags2B & 0x40) != 0) {
                    // Second turn: advance to Sub4
                    state.routine = 0x04;
                    state.yVel = -VEL_HORIZONTAL; // yVel = -$100
                } else {
                    flags2B |= 0x40; // set turn counter
                }
            }
        } else {
            // Moving right: check right boundary
            if (getBossX() >= BOUNDARY_RIGHT) {
                flags2B ^= 0x80; // toggle direction
                state.xVel = -VEL_HORIZONTAL;
                state.renderFlags &= ~1; // clear x_flip

                if ((flags2B & 0x40) != 0) {
                    state.routine = 0x04;
                    state.yVel = -VEL_HORIZONTAL;
                } else {
                    flags2B |= 0x40;
                }
            }
        }

        moveAndShow();
    }

    // =========================================================================
    // Sub4: Return to center
    // ROM: Obj54_MainSub4 (s2.asm:66865-66889)
    // =========================================================================

    private void updateSub4ReturnToCenter() {
        bossMoveObject();

        // Y clamp: stop rising at Y_DECEL
        if (getBossY() < Y_DECEL) {
            state.yVel = 0;
        }

        // X clamp: stop at center
        boolean facingRight = (flags2B & 0x80) != 0;
        if (!facingRight) {
            if (getBossX() < CENTER_X) {
                state.xVel = 0;
            }
        } else {
            if (getBossX() >= CENTER_X) {
                state.xVel = 0;
            }
        }

        // When both velocities are zero, advance
        if (state.xVel == 0 && state.yVel == 0) {
            state.routine = 0x06; // → Sub6
        }

        moveAndShow();
    }

    // =========================================================================
    // Sub6: Expand orbs
    // ROM: Obj54_MainSub6 (s2.asm:66892-66905)
    // =========================================================================

    private void updateSub6ExpandOrbs() {
        // Expand outer radius until max
        if (outerOrbRadius < ORB_RADIUS_EXPANDED) {
            outerOrbRadius++;
            innerOrbParam++;
        } else {
            // Contract inner param
            innerOrbParam--;
            if (innerOrbParam <= 0) {
                innerOrbParam = 0;
                state.routine = 0x08; // → Sub8
            }
        }

        moveAndShow();
    }

    // =========================================================================
    // Sub8: Contract orbs, restart cycle
    // ROM: Obj54_MainSub8 (s2.asm:66908-66923)
    // =========================================================================

    private void updateSub8ContractOrbs() {
        // Contract outer radius to default
        if (outerOrbRadius >= ORB_RADIUS_DEFAULT) {
            outerOrbRadius--;
        } else {
            // Expand inner param back to default
            innerOrbParam++;
            if (innerOrbParam >= ORB_RADIUS_DEFAULT) {
                // Restart cycle
                state.yVel = VEL_DESCEND; // yVel = $100
                state.routine = 0x00; // → Sub0
                flags2B &= ~0x40; // clear turn counter
            }
        }

        moveAndShow();
    }

    // =========================================================================
    // SubA: Retract after hit
    // ROM: Obj54_MainSubA (s2.asm:66926-66953)
    // =========================================================================

    private void updateSubARetractAfterHit() {
        // Retract inner param, signal orb break
        if (innerOrbParam > 0) {
            innerOrbParam--;
        } else {
            orbBreakState = -1; // signal orbs to detach (-1 = 0xFF)
        }

        // Contract outer radius
        if (outerOrbRadius >= ORB_RADIUS_DEFAULT) {
            outerOrbRadius--;
        }

        bossMoveObject();

        // Y clamp at Y_BASE
        if (getBossY() < Y_BASE) {
            state.yVel = 0;
        }

        // Check orb break count: if orbs still breaking away, stay in SubA
        if (orbBreakCount != 0) {
            // Still waiting for orbs
        } else {
            // All orbs done: signal and advance
            if (orbBreakState != 0) {
                orbBreakState = (byte) 0x80; // contracting state
            }
            state.routine = 0x0C; // → SubC
        }

        moveAndShow();
    }

    // =========================================================================
    // SubC: Decision point
    // ROM: Obj54_MainSubC (s2.asm:66956-66987)
    // =========================================================================

    private void updateSubCDecision(AbstractPlayableSprite player) {
        if (attackCyclesRemaining > 0) {
            // Still have attack cycles: check orb state
            if (orbBreakState != 0) {
                // Still processing orb contraction
            } else {
                // Re-expand inner param
                if (innerOrbParam < ORB_RADIUS_DEFAULT) {
                    innerOrbParam++;
                } else {
                    // Restart normal cycle
                    state.yVel = VEL_DESCEND;
                    state.routine = 0x00; // → Sub0
                    flags2B &= ~0x40;
                }
            }
        } else {
            // No attack cycles left: initiate laser dive
            state.yVel = -VEL_DIVE; // yVel = -$180
            // Face player
            state.xVel = -VEL_HORIZONTAL;
            state.renderFlags &= ~1;
            if ((flags2B & 0x80) != 0) {
                state.xVel = VEL_HORIZONTAL;
                state.renderFlags |= 1;
            }
            state.routine = 0x0E; // → SubE
            diveSubPhase = 0;
            divePauseTimer = 0;
            flags2B &= ~0x40; // clear turn counter
        }

        moveAndShow();
    }

    // =========================================================================
    // SubE: Laser dive attack (3 sub-phases)
    // ROM: Obj54_MainSubE (s2.asm:66990-67079)
    // =========================================================================

    private void updateSubELaserDive() {
        // ROM: pause timer check
        if (divePauseTimer > 0) {
            divePauseTimer--;
            state.x = getBossX();
            state.y = getBossY() + applyFloat();
            return;
        }

        switch (diveSubPhase) {
            case 0 -> updateDivePhase0Approach();
            case 2 -> updateDivePhase1DiveAndFire();
            case 4 -> updateDivePhase2Return();
        }
    }

    /**
     * SubE Phase 0 (loc_32650): Approach far edge, then start dive.
     * ROM: Fly to far boundary, then reverse with dive yVel.
     */
    private void updateDivePhase0Approach() {
        bossMoveObject();

        // Y clamp
        if (getBossY() < Y_BASE) {
            state.yVel = 0;
        }

        boolean facingRight = (flags2B & 0x80) != 0;
        if (!facingRight) {
            // Moving left: check far-left trigger
            if (getBossX() < DIVE_BOUNDARY_LEFT) {
                diveSubPhase = 2;
                state.yVel = VEL_DIVE; // yVel = $180 (dive down)
                laserFireCount = LASER_SHOTS_PER_ATTACK;
                bossCountdown = LASER_COUNTDOWN;
                state.renderFlags |= 1; // face right for return
            }
        } else {
            // Moving right: check far-right trigger
            if (getBossX() >= DIVE_BOUNDARY_RIGHT) {
                diveSubPhase = 2;
                state.yVel = VEL_DIVE;
                laserFireCount = LASER_SHOTS_PER_ATTACK;
                bossCountdown = LASER_COUNTDOWN;
                state.renderFlags &= ~1; // face left for return
            }
        }

        moveAndShow();
    }

    /**
     * SubE Phase 1 (loc_326B8): Dive down, fire lasers, bounce at bottom.
     */
    private void updateDivePhase1DiveAndFire() {
        bossMoveObject();

        // Bottom Y clamp: reverse Y and advance phase
        if (getBossY() >= Y_BOTTOM) {
            state.yVel = -VEL_DIVE; // reverse: yVel = -$180
            diveSubPhase = 4;
            flags2B ^= 0x80; // toggle direction
        } else {
            // X boundary clamping
            boolean facingRight = (flags2B & 0x80) != 0;
            if (!facingRight) {
                if (getBossX() < BOUNDARY_LEFT) {
                    state.xVel = 0;
                }
            } else {
                if (getBossX() >= BOUNDARY_RIGHT) {
                    state.xVel = 0;
                }
            }
        }

        // Fire laser
        fireLaser();

        moveAndShow();
    }

    /**
     * SubE Phase 2 (loc_32704): Return to base height, resume horizontal.
     */
    private void updateDivePhase2Return() {
        bossMoveObject();

        // At Y_DECEL: set horizontal velocity based on direction
        if (getBossY() < Y_DECEL) {
            boolean facingRight = (flags2B & 0x80) != 0;
            state.xVel = facingRight ? VEL_HORIZONTAL : -VEL_HORIZONTAL;
        }

        // At Y_BASE: stop Y movement, reset to phase 0
        if (getBossY() < Y_BASE) {
            state.yVel = 0;
            diveSubPhase = 0;
        }

        // Fire laser
        fireLaser();

        moveAndShow();
    }

    // =========================================================================
    // Laser firing
    // ROM: Obj54_FireLaser (s2.asm:67082-67096)
    // =========================================================================

    private void fireLaser() {
        bossCountdown--;
        if (bossCountdown > 0) {
            return;
        }
        if (laserFireCount <= 0) {
            return;
        }
        laserFireCount--;

        // Spawn laser projectile (placeholder - laser object not yet implemented)
        LOGGER.fine("MTZ boss firing laser (remaining: " + laserFireCount + ")");

        bossCountdown = LASER_COUNTDOWN;
        divePauseTimer = LASER_FIRE_PAUSE; // pause after firing
    }

    // =========================================================================
    // Sub10: Defeat explosions
    // ROM: Obj54_MainSub10 (s2.asm:67144-67178)
    // =========================================================================

    private void updateSub10DefeatExplosions(int frameCounter) {
        bossCountdown--;

        if (bossCountdown >= DEFEAT_EXPLOSION_CUTOFF) {
            if (bossCountdown >= 0) {
                spawnDefeatExplosion();
                faceFrame = FRAME_FACE_DEFEAT;
            }
        } else if (bossCountdown < 0) {
            // Transition to flee
            state.renderFlags |= 1; // face right
            state.xVel = 0;
            state.yVel = 0;
            state.routine = 0x12; // → Sub12
            bossCountdown = -0x12;
            faceFrame = FRAME_FACE;

            // ROM: jsr PlayLevelMusic
            services().playMusic(Sonic2Music.METROPOLIS.id);
        }

        // Update position from boss coordinates
        state.x = getBossX();
        state.y = getBossY();
    }

    // =========================================================================
    // Sub12: Flee
    // ROM: Obj54_MainSub12 (s2.asm:67181-67218)
    // =========================================================================

    private void updateSub12Flee() {
        state.xVel = VEL_FLEE_X;
        state.yVel = VEL_FLEE_Y;

        Camera camera = services().camera();
        if (camera.getMaxX() < CAMERA_MAX_X_FLEE) {
            camera.setMaxX((short) (camera.getMaxX() + 2));
        } else if (!isOnScreen()) {
            // Spawn EggPrison and delete
            spawnEggPrison();
            setDestroyed(true);
            return;
        }

        // ROM: Boss_defeated_flag - triggers animal PLC loading
        // Handled by defeat sequencer / EggPrison spawn

        bossMoveObject();

        // Float with slower sine increment
        state.x = getBossX();
        state.y = getBossY() + applyFloatSlow();
    }

    private void spawnEggPrison() {
        if (services().objectManager() == null) {
            return;
        }
        // Spawn at center of arena
        ObjectSpawn prisonSpawn = new ObjectSpawn(
                CENTER_X, Y_BOTTOM - 0x20,
                Sonic2ObjectIds.EGG_PRISON,
                0, 0, false, 0);
        EggPrisonObjectInstance prisonInstance = new EggPrisonObjectInstance(prisonSpawn, "Egg Prison");
        services().objectManager().addDynamicObject(prisonInstance);
    }

    // =========================================================================
    // Hit checking
    // ROM: Obj54_CheckHit (s2.asm:67230-67265)
    // =========================================================================

    /**
     * ROM: Obj54_CheckHit / Obj54_AnimateFace
     * On first frame of invulnerability ($3F), triggers SubA retract and
     * decrements attack cycle counter.
     */
    private void checkHit(AbstractPlayableSprite player) {
        if (state.routine >= 0x10) {
            return; // no hit check during defeat
        }

        // Face animation: show laughing face if player is hurt
        // ROM: cmpi.b #4,(MainCharacter+routine).w
        if (player.isHurt()) {
            if (faceFrame != FRAME_FACE_LAUGH) {
                faceFrame = FRAME_FACE_LAUGH;
            }
        }
    }

    @Override
    protected void onHitTaken(int remainingHits) {
        // ROM: Obj54_AnimateFace hit reaction (s2.asm:67109-67125)
        // On first invuln frame: set angry face, trigger SubA
        faceFrame = FRAME_FACE_ANGRY;

        if (attackCyclesRemaining > 0) {
            state.routine = 0x0A; // → SubA (retract after hit)
            state.yVel = -VEL_DIVE; // yVel = -$180
            attackCyclesRemaining--;
            state.xVel = 0;
        }
    }

    @Override
    protected void onDefeatStarted() {
        // ROM: Obj54_Defeated (s2.asm:67258-67265)
        bossCountdown = DEFEAT_COUNTDOWN;
        state.routine = 0x10; // → Sub10
    }

    @Override
    protected int getInitialHitCount() {
        return DEFAULT_HIT_COUNT;
    }

    @Override
    protected int getInvulnerabilityDuration() {
        return INVULN_DURATION; // 0x40 = 64 frames
    }

    @Override
    protected int getCollisionSizeIndex() {
        // ROM: move.b #$F,collision_flags(a0)
        return 0x0F;
    }

    @Override
    protected boolean usesDefeatSequencer() {
        return false; // Custom defeat logic in Sub10/Sub12
    }

    // =========================================================================
    // Rendering
    // =========================================================================

    @Override
    public int getPriorityBucket() {
        return 3;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = services().renderManager();
        if (renderManager == null) {
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.MTZ_BOSS);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        boolean flipped = (state.renderFlags & 1) != 0;

        // ROM multi-sprite rendering: body (frame 2), face (frame $C), pod (frame 0)
        // Draw in back-to-front order
        renderer.drawFrameIndex(FRAME_POD, state.x, state.y, flipped, false);
        renderer.drawFrameIndex(FRAME_BODY, state.x, state.y, flipped, false);
        renderer.drawFrameIndex(faceFrame, state.x, state.y, flipped, false);
    }

    @Override
    public int getCollisionFlags() {
        if (state.routine >= 0x10) {
            return 0; // No collision during defeat/flee
        }
        if (state.defeated || state.invulnerable) {
            return 0;
        }
        return 0xC0 | (getCollisionSizeIndex() & 0x3F);
    }

    // Uses inherited isOnScreen() from AbstractObjectInstance

    // =========================================================================
    // Public accessors for child components (orbs, laser shooter)
    // =========================================================================

    public int getOuterOrbRadius() {
        return outerOrbRadius;
    }

    public int getInnerOrbParam() {
        return innerOrbParam;
    }

    public int getOrbBreakState() {
        return orbBreakState;
    }

    public void setOrbBreakState(int value) {
        this.orbBreakState = value;
    }

    public int getOrbBreakCount() {
        return orbBreakCount;
    }

    public void incrementOrbBreakCount() {
        this.orbBreakCount++;
    }

    public int getFlags2B() {
        return flags2B;
    }

    @Override
    protected int getBossHitSfxId() {
        return Sonic2Sfx.BOSS_HIT.id;
    }

    @Override
    protected int getBossExplosionSfxId() {
        return Sonic2Sfx.BOSS_EXPLOSION.id;
    }

    // =========================================================================
    // Inner class: MTZ Boss Orb (Obj53)
    // ROM: s2.asm:67271-67467
    // =========================================================================

    /**
     * Shield orb that orbits the MTZ boss.
     * 7 orbs with different phase offsets create the rotating shield.
     * Each orb can break away when the boss is hit, bouncing off-screen.
     */
    public static class MTZBossOrb extends AbstractBossChild {
        // Orbit state
        private final int orbIndex;
        private int orbitAngle;       // objoff_28: horizontal orbit angle
        private int verticalAngle;    // objoff_3B: vertical orbit angle
        private final int tiltFlag;   // objoff_3A(orb): 0 or 1 for tilt direction
        private int flattenAngle;     // objoff_3C: flatten angle during break state

        // Break-away state
        private boolean breakingAway;
        private int breakTimer;       // objoff_32: frames until burst
        private int xVel;
        private int yVel;

        // Display
        private int mappingFrame = 5; // ROM: move.b #5,mapping_frame
        private int orbPriority = 3;

        /** Collision-property based orb collision size. ROM: move.b #$87,collision_flags(a1) */
        private static final int ORB_COLLISION = 0x87;

        public MTZBossOrb(Sonic2MTZBossInstance parent, int index, int phaseOffset, int tilt) {
            super(parent, "MTZ Orb " + index, 3, Sonic2ObjectIds.MTZ_BOSS_ORB);
            this.orbIndex = index;
            this.orbitAngle = phaseOffset;
            this.verticalAngle = phaseOffset;
            this.tiltFlag = tilt;
            this.flattenAngle = 0;
            this.breakingAway = false;
        }

        @Override
        public void update(int frameCounter, PlayableEntity playerEntity) {
            AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
            if (!shouldUpdate(frameCounter)) {
                return;
            }

            Sonic2MTZBossInstance boss = (Sonic2MTZBossInstance) parent;

            if (!breakingAway) {
                orbitBoss(boss);
                setAnimPriority();
            } else {
                updateBreakAway();
            }

            updateDynamicSpawn();
        }

        /**
         * ROM: Obj53_OrbitBoss (s2.asm:67382-67442)
         * Complex 3D-ish orbital positioning using dual sine lookups.
         */
        private void orbitBoss(Sonic2MTZBossInstance boss) {
            int bossX = boss.getX();
            int bossY = boss.getY() - 4; // ROM: subi_.w #4,objoff_2A

            int outerR = boss.getOuterOrbRadius();
            int innerR = boss.getInnerOrbParam();
            int breakState = boss.getOrbBreakState();

            // Override inner radius during break state
            int effectiveInnerR = innerR;
            if (breakState != 0) {
                effectiveInnerR = 0x10;
            }

            // Calculate horizontal orbit position
            int sinH = TrigLookupTable.sinHex(orbitAngle & 0xFF);
            int product = sinH * outerR;

            // Calculate vertical component
            int vertSin;
            if (breakState != 0) {
                vertSin = TrigLookupTable.sinHex(flattenAngle & 0xFF);
            } else {
                vertSin = TrigLookupTable.sinHex(verticalAngle & 0xFF);
            }

            // X position: bossX + (sinH * outerR * cos(orbitAngle)) >> 16
            int cosH = TrigLookupTable.cosHex(orbitAngle & 0xFF);
            int xOffset = (int) (((long) product * cosH) >> 16);
            currentX = bossX + xOffset;

            // Y position: bossY + (vertSin * effectiveInnerR * sinH) >> 16
            int yProduct = (int) (((long) effectiveInnerR * vertSin) >> 8);
            currentY = bossY + yProduct;

            // Advance angles
            orbitAngle = (orbitAngle + 4) & 0xFF;

            if (breakState == 0) {
                verticalAngle = (verticalAngle + 8) & 0xFF;
            } else if (breakState == -1) {
                // Expanding
                if (flattenAngle < 0x40) {
                    flattenAngle += 2;
                }
            } else if (breakState == (byte) 0x80) {
                // Contracting
                flattenAngle -= 2;
                if (flattenAngle <= 0) {
                    flattenAngle = 0;
                    boss.setOrbBreakState(0);
                }
            }

            // Check if boss signals orb to break away
            // (triggered by boss setting objoff_38 flag)
        }

        /**
         * ROM: Obj53_SetAnimPriority (s2.asm:67445-67467)
         * Set mapping frame and priority based on orbital depth.
         */
        private void setAnimPriority() {
            // Simplified: use orbit angle to determine depth
            int sinVal = TrigLookupTable.sinHex(orbitAngle & 0xFF);
            if (sinVal >= 0) {
                if (sinVal >= 0x0C00) {
                    mappingFrame = 3; // large (front)
                    orbPriority = 1;
                } else {
                    mappingFrame = 4; // medium
                    orbPriority = 2;
                }
            } else {
                if (sinVal <= -0x0C00) {
                    mappingFrame = 5; // small (back)
                    orbPriority = 7;
                } else {
                    mappingFrame = 4; // medium
                    orbPriority = 6;
                }
            }
            priority = orbPriority;
        }

        private void updateBreakAway() {
            // Apply velocity
            currentX += xVel >> 8;
            currentY += yVel >> 8;
            yVel += 0x18; // gravity

            breakTimer--;
            if (breakTimer <= 0) {
                setDestroyed(true);
            }
        }

        /**
         * Called by boss to trigger orb break-away.
         */
        public void triggerBreakAway(int playerX) {
            breakingAway = true;
            breakTimer = 60;
            yVel = -0x400;

            // Move away from player
            xVel = -0x80;
            if (playerX < currentX) {
                xVel = 0x80;
            }

            // Boundary clamping
            if (currentX < DIVE_BOUNDARY_LEFT) {
                xVel = 0x80;
            }
            if (currentX >= DIVE_BOUNDARY_RIGHT) {
                xVel = -0x80;
            }

            Sonic2MTZBossInstance boss = (Sonic2MTZBossInstance) parent;
            boss.incrementOrbBreakCount();
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            ObjectRenderManager renderManager =
                    ((Sonic2MTZBossInstance) parent).services().renderManager();
            if (renderManager == null) {
                return;
            }

            PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.MTZ_BOSS);
            if (renderer == null || !renderer.isReady()) {
                return;
            }

            boolean flipped = (parent.getState().renderFlags & 1) != 0;
            renderer.drawFrameIndex(mappingFrame, currentX, currentY, flipped, false);
        }

        @Override
        public int getPriorityBucket() {
            return orbPriority;
        }
    }

    // =========================================================================
    // Inner class: Laser Shooter (Obj54 subtype 6)
    // ROM: Obj54_LaserShooter (s2.asm:67641-67691)
    // =========================================================================

    /**
     * Laser shooter child that follows boss position.
     * ROM: Positioned at boss x/y, renders frame $13.
     */
    public static class MTZLaserShooter extends AbstractBossChild {

        public MTZLaserShooter(Sonic2MTZBossInstance parent) {
            super(parent, "MTZ Laser Shooter", 6, Sonic2ObjectIds.MTZ_BOSS);
        }

        @Override
        public void update(int frameCounter, PlayableEntity playerEntity) {
            AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
            if (!shouldUpdate(frameCounter)) {
                return;
            }
            // Follow boss position
            syncPositionWithParent();
            updateDynamicSpawn();
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            ObjectRenderManager renderManager =
                    ((Sonic2MTZBossInstance) parent).services().renderManager();
            if (renderManager == null) {
                return;
            }

            PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.MTZ_BOSS);
            if (renderer == null || !renderer.isReady()) {
                return;
            }

            boolean flipped = (parent.getState().renderFlags & 1) != 0;
            renderer.drawFrameIndex(FRAME_LASER_SHOOTER, currentX, currentY, flipped, false);
        }
    }
}
