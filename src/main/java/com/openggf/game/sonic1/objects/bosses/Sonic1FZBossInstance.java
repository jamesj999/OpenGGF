package com.openggf.game.sonic1.objects.bosses;

import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.sonic1.constants.Sonic1AnimationIds;
import com.openggf.game.sonic1.audio.Sonic1Sfx;
import com.openggf.game.sonic1.constants.Sonic1Constants;
import com.openggf.level.objects.boss.BossExplosionObjectInstance;
import com.openggf.game.sonic1.scroll.Sonic1ZoneConstants;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.objects.boss.AbstractBossInstance;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.animation.SpriteAnimationScript;
import com.openggf.sprites.animation.SpriteAnimationSet;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Object 0x85 — Final Zone Boss (Eggman in machine with crushing cylinders and plasma launcher).
 * ROM: _incObj/85 Boss - Final.asm
 *
 * The FZ boss is a multi-component system: Eggman sits inside a control room
 * while 4 crushing cylinders attack Sonic and a plasma launcher fires energy balls.
 * The boss uses custom hit detection — Sonic must be rolling when pushed into
 * the boss body by a cylinder to deal damage (8 hits total, no rings available).
 *
 * State machine (objoff_34 sub-states, within routine 2):
 *   0: WAIT           — Wait for PLC buffer empty + camera at boss_fz_x
 *   2: CYLINDER_ATTACK — Select 2 cylinders, activate, handle solid collision + damage
 *   4: PLASMA_PHASE   — Activate plasma launcher, wait for completion, loop to 2
 *   6: DEFEAT_FALL    — Gravity descent after defeat, transition at Y threshold
 *   8: RUNNING_ESCAPE — Complex X velocity chase of Sonic, Y bounce
 *  10: FINAL_ASCENT   — High gravity arc to landing position
 *  12: SHIP_TRANSFORM — Switch to Map_Eggman, ascend to escape ship
 *  14: FINAL_FLIGHT   — Escape flight, player control lock, ending trigger
 *
 * Sub-objects (routines 4-12) in the ROM are visual overlays that track the parent.
 * In our engine these are handled as rendering overlays within this class.
 */
public class Sonic1FZBossInstance extends AbstractBossInstance
        implements SolidObjectProvider, SolidObjectListener {
    private static final SpriteAnimationSet SEGG_ANIMATIONS = Sonic1BossAnimations.getSEggAnimations();

    // State machine constants (objoff_34 values in the ROM)
    private static final int STATE_WAIT = 0;
    private static final int STATE_CYLINDER_ATTACK = 2;
    private static final int STATE_PLASMA_PHASE = 4;
    private static final int STATE_DEFEAT_FALL = 6;
    private static final int STATE_RUNNING_ESCAPE = 8;
    private static final int STATE_FINAL_ASCENT = 10;
    private static final int STATE_SHIP_TRANSFORM = 12;
    private static final int STATE_FINAL_FLIGHT = 14;

    // Position constants from Constants.asm
    private static final int BOSS_FZ_X = Sonic1Constants.BOSS_FZ_X;     // 0x2450
    private static final int BOSS_FZ_Y = Sonic1Constants.BOSS_FZ_Y;     // 0x510
    private static final int BOSS_FZ_END = Sonic1Constants.BOSS_FZ_END; // 0x2700
    private static final int ENDING_ACT_FLOWERS = 0;
    private static final int ENDING_ACT_NO_EMERALDS = 1;
    private static final int ESCAPE_COLLISION_FLAGS = 0xC0 | 0x0F;

    // Cylinder lookup table: word_19FD6 — maps random index to cylinder pair
    // Random & $C gives 0, 4, 8, or 12 -> lookup gives cylinder pair indices
    private static final int[] CYLINDER_PAIR_TABLE = {0, 2, 2, 4, 4, 6, 6, 0};

    // Solid collision params for combat phase: d1=$2B, d2=$14, d3=$14
    private static final SolidObjectParams COMBAT_SOLID_PARAMS =
            new SolidObjectParams(0x2B, 0x14, 0x14);

    // Solid collision params for escape phases: d1=$1B, d2=$70, d3=$71
    private static final SolidObjectParams ESCAPE_SOLID_PARAMS =
            new SolidObjectParams(0x1B, 0x70, 0x71);
    private static final int COMBAT_TOP_LANDING_HALF_WIDTH = 0x20;

    // Damage cooldown (objoff_35 in ROM)
    private int damageCooldown;

    // Cylinder management
    private FZCylinder[] cylinders;
    private FZPlasmaLauncher plasmaLauncher;
    private boolean childComponentsSpawned;

    // objoff_30: cylinder activation state (-1 = ready for new pair)
    private int cylinderState;

    // objoff_32: active cylinder counter (decremented by each cylinder when done)
    private int activeCylinderCount;

    // Current SEgg animation
    private int seggAnim;
    private int seggAnimPrev;
    private int seggAnimScriptFrame;
    private int seggAnimTimeFrame;
    private int seggFrame;

    // Whether we've transitioned to Map_Eggman (escape ship form)
    private boolean escapingInShip;

    // Eggman face/flame overlays (sub-objects in ROM, rendered inline here)
    // These track state for the 6 sub-objects defined in BossFinal_ObjData
    private int legsFrame;
    private int legsTimer;
    private boolean showDamaged;

    // obColProp flag for escape phase hittability
    private boolean escapeHittable;
    private int escapeHitTimer;
    private int escapeCollisionFlags;
    private boolean endingTransitionRequested;

    public Sonic1FZBossInstance(ObjectSpawn spawn) {
        super(spawn, "FZ Boss");
    }

    @Override
    protected void initializeBossState() {
        // ROM: BossFinal_Main sets objoff_34 = 0 (state WAIT)
        state.routineSecondary = STATE_WAIT;
        state.xVel = 0;
        state.yVel = 0;

        // ROM: Initial position from BossFinal_ObjData — boss_fz_x+$160, boss_fz_y+$80
        state.x = BOSS_FZ_X + 0x160;
        state.y = BOSS_FZ_Y + 0x80;
        state.xFixed = state.x << 16;
        state.yFixed = state.y << 16;

        // ROM: move.w #-1,objoff_30(a0) — ready for first cylinder pair
        cylinderState = -1;
        activeCylinderCount = 0;

        damageCooldown = 0;
        seggAnim = Sonic1BossAnimations.ANIM_SEGG_STAND;
        seggAnimPrev = -1;
        seggAnimScriptFrame = 0;
        seggAnimTimeFrame = 0;
        seggFrame = 0;
        escapingInShip = false;
        legsFrame = 0;
        legsTimer = 0;
        showDamaged = false;
        escapeHittable = false;
        escapeHitTimer = 0;
        escapeCollisionFlags = 0;
        endingTransitionRequested = false;

        // Initialize arrays before spawning (field initializers haven't run yet
        // because AbstractBossInstance constructor calls initializeBossState())
        cylinders = new FZCylinder[4];
        childComponentsSpawned = false;
    }

    private void spawnChildComponents() {
        var objectManager = services().objectManager();
        if (objectManager == null) return;

        // Spawn 4 cylinders with subtypes 0, 2, 4, 6 (ROM: loc_19E3E)
        for (int i = 0; i < 4; i++) {
            int subtype = i * 2;
            FZCylinder cylinder = new FZCylinder(this, GameServices.level(), subtype);
            cylinders[i] = cylinder;
            childComponents.add(cylinder);
            objectManager.addDynamicObject(cylinder);
        }

        // Spawn plasma launcher (ROM: loc_19E20)
        plasmaLauncher = new FZPlasmaLauncher(this, GameServices.level());
        childComponents.add(plasmaLauncher);
        objectManager.addDynamicObject(plasmaLauncher);
    }

    private void ensureChildComponentsSpawned() {
        if (childComponentsSpawned) return;
        if (services().objectManager() == null) return;
        spawnChildComponents();
        childComponentsSpawned = true;
    }

    @Override
    protected int getInitialHitCount() {
        return 8; // ROM: move.b #8,obColProp(a0)
    }

    @Override
    protected int getCollisionSizeIndex() {
        // FZ boss does NOT use standard touch response collision
        return 0;
    }

    @Override
    protected boolean usesDefeatSequencer() {
        return false; // FZ boss has custom defeat logic in states 6-14
    }

    @Override
    public int getCollisionFlags() {
        // ROM: FZ boss never uses standard touch response (obColType is never set
        // during combat). Damage is handled via SolidObject push + roll check.
        // During escape (state 14), obColType=$F is set for hittability.
        if (state.routineSecondary == STATE_FINAL_FLIGHT && escapeHittable) {
            return escapeCollisionFlags;
        }
        return 0;
    }

    @Override
    public void onPlayerAttack(AbstractPlayableSprite player, TouchResponseResult result) {
        // ROM: In state 14, a successful player hit clears obColType, starting objoff_30 timer.
        if (state.routineSecondary != STATE_FINAL_FLIGHT || !escapeHittable || escapeCollisionFlags == 0) {
            return;
        }
        escapeCollisionFlags = 0;
        escapeHitTimer = 0x1E;
        escapeHittable = false;
        showDamaged = true;
        AudioManager.getInstance().playSfx(Sonic1Sfx.HIT_BOSS.id);
    }

    @Override
    protected void onHitTaken(int remainingHits) {
        // Only used during escape phase (state 14) hit
    }

    @Override
    protected void onDefeatStarted() {
        // Not used — FZ boss handles defeat inline
    }

    @Override
    protected void updateBossLogic(int frameCounter, AbstractPlayableSprite player) {
        // Spawn children after the parent is already inserted in ObjectManager.
        // This preserves parent-before-child collision order for FZ boss solids.
        ensureChildComponentsSpawned();

        switch (state.routineSecondary) {
            case STATE_WAIT -> updateWait();
            case STATE_CYLINDER_ATTACK -> updateCylinderAttack(player);
            case STATE_PLASMA_PHASE -> updatePlasmaPhase(frameCounter);
            case STATE_DEFEAT_FALL -> updateDefeatFall();
            case STATE_RUNNING_ESCAPE -> updateRunningEscape(player);
            case STATE_FINAL_ASCENT -> updateFinalAscent();
            case STATE_SHIP_TRANSFORM -> updateShipTransform();
            case STATE_FINAL_FLIGHT -> updateFinalFlight(player, frameCounter);
        }
    }

    // === State 0: WAIT (loc_19E90) ===
    // Wait for camera to reach boss_fz_x
    private void updateWait() {
        Camera camera = Camera.getInstance();
        int camX = camera.getX() & 0xFFFF;

        if (camX >= BOSS_FZ_X) {
            state.routineSecondary = STATE_CYLINDER_ATTACK;
        }
    }

    // === State 2: CYLINDER_ATTACK (loc_19EA8) ===
    // Select and activate cylinder pairs, handle solid collision and damage
    private void updateCylinderAttack(AbstractPlayableSprite player) {
        if (cylinderState < 0) {
            // ROM: clr.w objoff_30 then select new pair
            cylinderState = 0;
            selectCylinderPair();
        }

        // ROM: tst.w objoff_32 — check if cylinders signaled completion
        if (activeCylinderCount < 0) {
            // Cylinders done, check hit count
            if (state.hitCount <= 0) {
                // ROM: loc_19FBC — defeated
                GameServices.gameState().addScore(1000);
                // ROM: v_bossstatus = 0 cleared on defeat (matches other S1 bosses)
                GameServices.gameState().setCurrentBossId(0);
                state.routineSecondary = STATE_DEFEAT_FALL;
                state.x = BOSS_FZ_X + 0x170;
                state.y = BOSS_FZ_Y + 0x2C;
                state.xFixed = state.x << 16;
                state.yFixed = state.y << 16;
                state.defeated = true;
                return;
            }
            // ROM: addq.b #2,objoff_34 — advance to plasma phase
            state.routineSecondary = STATE_PLASMA_PHASE;
            cylinderState = -1;
            activeCylinderCount = 0;
            return;
        }

        // Handle solid collision and damage check (ROM: loc_19F10 onward)
        handleCombatCollision(player);

        // Animate SEgg (ROM: AnimateSprite with Ani_SEgg)
        updateCombatAnimation();
    }

    private void selectCylinderPair() {
        // ROM: loc_19EA8 — RandomNumber, andi #$C
        int random = ThreadLocalRandom.current().nextInt(0x10000) & 0xC;
        int index1 = random >> 1;
        int index2 = (random >> 1) + 1;

        // ROM: word_19FD6 lookup
        int cylIndex1 = CYLINDER_PAIR_TABLE[index1];
        int cylIndex2 = CYLINDER_PAIR_TABLE[index2];

        // Activate the two selected cylinders
        int cyl1 = cylIndex1 >> 1;
        int cyl2 = cylIndex2 >> 1;
        if (cyl1 < 4 && cylinders[cyl1] != null) {
            cylinders[cyl1].activate(-1); // Descending
        }
        if (cyl2 < 4 && cylinders[cyl2] != null) {
            cylinders[cyl2].activate(1);  // Ascending
        }

        // ROM: move.w #1,objoff_32(a0)
        activeCylinderCount = 1;
        damageCooldown = 0;

        // ROM: sfx_Rumbling
        AudioManager.getInstance().playSfx(Sonic1Sfx.RUMBLING.id);
    }

    private void handleCombatCollision(AbstractPlayableSprite player) {
        // The actual SolidObject collision is handled by the engine's SolidContacts
        // system via our SolidObjectProvider implementation. The SolidObjectListener
        // callback (onSolidContact) handles the push/roll damage check.

        // ROM: Face direction toward player (bclr/bset #0,obStatus)
        if (player != null) {
            int playerX = player.getCentreX() & 0xFFFF;
            if (playerX >= state.x) {
                state.renderFlags |= 1; // Face right
            } else {
                state.renderFlags &= ~1; // Face left
            }
        }
    }

    private void updateCombatAnimation() {
        if (damageCooldown > 0) {
            damageCooldown--;
            if (damageCooldown > 0) {
                // ROM: move.b #3,obAnim — inside tube during cooldown
                seggAnim = Sonic1BossAnimations.ANIM_SEGG_INTUBE;
            } else {
                // ROM: move.b #1,obAnim — return to laugh when cooldown ends
                seggAnim = Sonic1BossAnimations.ANIM_SEGG_LAUGH;
            }
        } else {
            // ROM: move.b #1,obAnim — normal laugh (default combat anim)
            seggAnim = Sonic1BossAnimations.ANIM_SEGG_LAUGH;
        }
        updateSeggAnimation();
    }

    // === State 4: PLASMA_PHASE (loc_19FE6) ===
    private void updatePlasmaPhase(int frameCounter) {
        if (cylinderState < 0) {
            // ROM: Activate plasma launcher
            cylinderState = 0;
            if (plasmaLauncher != null) {
                plasmaLauncher.activateForBoss();
            }
        }

        // ROM: play electricity sound every 16 frames
        if ((frameCounter & 0xF) == 0) {
            AudioManager.getInstance().playSfx(Sonic1Sfx.ELECTRIC.id);
        }

        // ROM: tst.w objoff_32 — check if plasma launcher signaled completion
        if (activeCylinderCount < 0) {
            // Plasma done, loop back to cylinder attack
            state.routineSecondary = STATE_CYLINDER_ATTACK;
            cylinderState = -1;
            activeCylinderCount = 0;
        }
    }

    // === State 6: DEFEAT_FALL (loc_1A02A) ===
    // Gravity descent after defeat
    private void updateDefeatFall() {
        // ROM: bset #0,obStatus — face right
        state.renderFlags |= 1;

        // ROM: SpeedToPos
        state.xFixed += (state.xVel << 8);
        state.yFixed += (state.yVel << 8);
        state.x = state.xFixed >> 16;
        state.y = state.yFixed >> 16;

        // ROM: move.b #6,obFrame — starjump frame
        seggAnim = Sonic1BossAnimations.ANIM_SEGG_STARJUMP;
        seggFrame = 6;

        // ROM: addi.w #$10,obVelY — gravity
        state.yVel += 0x10;

        // ROM: cmpi.w #boss_fz_y+$8C,obY — check landing
        if (state.y >= BOSS_FZ_Y + 0x8C) {
            state.y = BOSS_FZ_Y + 0x8C;
            state.yFixed = state.y << 16;
            state.routineSecondary = STATE_RUNNING_ESCAPE;
            // ROM: move.w #$100,obVelX / move.w #-$100,obVelY
            state.xVel = 0x100;
            state.yVel = -0x100;
            // ROM: addq.b #2,(v_dle_routine).w — advance level event routine
            // This is handled by SBZ events checking boss state
        }

        // Expand camera boundary (ROM: loc_1A166)
        expandCameraBoundary();

        // Solid collision during escape (ROM: loc_1A172 — d1=$1B, d2=$70, d3=$71)
        // Only if objoff_34 < $C (states 6-10)
    }

    // === State 8: RUNNING_ESCAPE (loc_1A074) ===
    // Complex X velocity chase of Sonic with Y bounce
    private void updateRunningEscape(AbstractPlayableSprite player) {
        state.renderFlags |= 1; // bset #0,obStatus
        seggAnim = Sonic1BossAnimations.ANIM_SEGG_RUNNING;

        // ROM: SpeedToPos
        state.xFixed += (state.xVel << 8);
        state.yFixed += (state.yVel << 8);
        state.x = state.xFixed >> 16;
        state.y = state.yFixed >> 16;

        // ROM: addi.w #$10,obVelY — gravity
        state.yVel += 0x10;

        // ROM: cmpi.w #boss_fz_y+$93,obY — bounce at floor
        if (state.y >= BOSS_FZ_Y + 0x93) {
            state.yVel = -0x40;
        }

        // ROM: Complex X velocity calculation (loc_1A074 - loc_1A0F2)
        // Base speed: $400, increase to $500 if behind player, reduce based on distance ahead
        int xVelCalc = 0x400;
        if (player != null) {
            int dist = state.x - (player.getCentreX() & 0xFFFF);
            if (dist < 0) {
                // Behind player — speed up
                xVelCalc = 0x500;
            } else {
                // Ahead of player — slow down based on distance
                dist -= 0x70;
                if (dist >= 0) {
                    xVelCalc -= 0x100;
                    dist -= 8;
                    if (dist >= 0) {
                        xVelCalc -= 0x100;
                        dist -= 8;
                        if (dist >= 0) {
                            xVelCalc -= 0x80;
                            dist -= 8;
                            if (dist >= 0) {
                                xVelCalc -= 0x80;
                                dist -= 8;
                                if (dist >= 0) {
                                    xVelCalc -= 0x80;
                                    dist -= 0x38;
                                    if (dist >= 0) {
                                        xVelCalc = 0; // Stop
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        state.xVel = xVelCalc;

        // ROM: cmpi.w #boss_fz_x+$250,obX — transition to final ascent
        if (state.x >= BOSS_FZ_X + 0x250) {
            state.x = BOSS_FZ_X + 0x250;
            state.xFixed = state.x << 16;
            state.xVel = 0x240;
            state.yVel = -0x4C0;
            state.routineSecondary = STATE_FINAL_ASCENT;
        }

        // Expand camera boundary + solid collision (ROM: loc_1A15C -> loc_1A166)
        expandCameraBoundary();
        updateSeggAnimation();
    }

    // === State 10: FINAL_ASCENT (loc_1A112) ===
    // High gravity arc to landing position
    private void updateFinalAscent() {
        // ROM: SpeedToPos
        state.xFixed += (state.xVel << 8);
        state.yFixed += (state.yVel << 8);
        state.x = state.xFixed >> 16;
        state.y = state.yFixed >> 16;

        // ROM: cmpi.w #boss_fz_x+$290,obX — cap X position
        if (state.x >= BOSS_FZ_X + 0x290) {
            state.xVel = 0;
        }

        // ROM: addi.w #$34,obVelY — strong gravity
        state.yVel += 0x34;

        // ROM: cmpi.w #boss_fz_y+$82,obY — landing check (only if yVel >= 0)
        if (state.yVel >= 0 && state.y >= BOSS_FZ_Y + 0x82) {
            state.y = BOSS_FZ_Y + 0x82;
            state.yFixed = state.y << 16;
            state.yVel = 0;
        }

        // Check if both velocities are zero — landed
        if (state.xVel == 0 && state.yVel == 0) {
            state.routineSecondary = STATE_SHIP_TRANSFORM;
            state.yVel = -0x180;
            // ROM: move.b #1,obColProp — mark as hittable (for escape sequence)
            escapeHittable = false; // Not yet — set in ship transform
        }

        // Expand camera boundary + animate (ROM: loc_1A15C -> loc_1A166)
        expandCameraBoundary();
        updateSeggAnimation();
    }

    // === State 12: SHIP_TRANSFORM (loc_1A192) ===
    // Switch to Map_Eggman, ascend to escape ship
    private void updateShipTransform() {
        // ROM: move.l #Map_Eggman,obMap / move.b #0,obAnim
        escapingInShip = true;
        seggAnim = Sonic1BossAnimations.ANIM_SEGG_STAND;
        state.renderFlags |= 1; // bset #0,obStatus

        // ROM: SpeedToPos
        state.xFixed += (state.xVel << 8);
        state.yFixed += (state.yVel << 8);
        state.x = state.xFixed >> 16;
        state.y = state.yFixed >> 16;

        // ROM: cmpi.w #boss_fz_y+$34,obY — ascend until reaching escape height
        if (state.y < BOSS_FZ_Y + 0x34) {
            // ROM: Transition to final flight
            state.xVel = 0x180;
            state.yVel = -0x18;
            state.routineSecondary = STATE_FINAL_FLIGHT;
            escapeHittable = true;
            escapeHitTimer = 0;
            escapeCollisionFlags = ESCAPE_COLLISION_FLAGS;
        }

        updateEscapeLegs();

        // Expand camera boundary + animate
        expandCameraBoundary();
        updateSeggAnimation();
    }

    // === State 14: FINAL_FLIGHT (loc_1A1D4) ===
    // Escape flight with player control lock and ending trigger
    private void updateFinalFlight(AbstractPlayableSprite player, int frameCounter) {
        state.renderFlags |= 1; // bset #0,obStatus

        // ROM: SpeedToPos
        state.xFixed += (state.xVel << 8);
        state.yFixed += (state.yVel << 8);
        state.x = state.xFixed >> 16;
        state.y = state.yFixed >> 16;

        // ROM: Handle hittability during escape (objoff_30 timer)
        if (escapeHitTimer > 0) {
            escapeHitTimer--;
            if (escapeHitTimer == 0) {
                // ROM: Check render bit 7 — if off-screen, set yVel to $60
                if (!isBossOnScreen()) {
                    state.yVel = 0x60;
                } else {
                    escapeHittable = true;
                    escapeCollisionFlags = ESCAPE_COLLISION_FLAGS;
                }
            }
        }

        updateEscapeLegs();

        // ROM: Player control lock when player X >= boss_fz_end + $90
        if (player != null) {
            int playerX = player.getCentreX() & 0xFFFF;

            // ROM: loc_1A216 — lock player controls past threshold
            if (playerX >= BOSS_FZ_END + 0x90) {
                player.setControlLocked(true);
                player.setGSpeed((short) 0);

                // ROM: If boss Y velocity is negative, hold up
                if (state.yVel >= 0) {
                    player.setXSpeed((short) 0);
                }
            }

            // ROM: Cap player X at boss_fz_end + $E0
            if (playerX >= BOSS_FZ_END + 0xE0) {
                player.setCentreX((short) (BOSS_FZ_END + 0xE0));
            }
        }

        // ROM: cmpi.w #boss_fz_end+$200,obX — trigger ending
        if (state.x >= BOSS_FZ_END + 0x200 && !isBossOnScreen()) {
            requestEndingTransition();
            setDestroyed(true);
            return;
        }

        // Expand camera boundary
        expandCameraBoundary();

        // ROM: sub-object routine 6 keeps calling BossDefeated while damaged escape sprite is active.
        if (showDamaged) {
            triggerBossDefeatedExplosion(frameCounter);
        }

        updateSeggAnimation();
    }

    private void updateEscapeLegs() {
        if (!escapingInShip || legsFrame > 2) {
            return;
        }
        if (legsTimer == 0) {
            legsTimer = 0x14;
        }
        legsTimer--;
        if (legsTimer <= 0) {
            legsFrame++;
        }
    }

    private void updateSeggAnimation() {
        SpriteAnimationScript script = SEGG_ANIMATIONS.getScript(seggAnim);
        if (script == null || script.frames().isEmpty()) {
            return;
        }

        if (seggAnim != seggAnimPrev) {
            seggAnimPrev = seggAnim;
            seggAnimScriptFrame = 0;
            seggAnimTimeFrame = 0;
        }

        seggAnimTimeFrame--;
        if (seggAnimTimeFrame >= 0) {
            return;
        }

        seggAnimTimeFrame = script.delay() & 0xFF;

        if (seggAnimScriptFrame < 0 || seggAnimScriptFrame >= script.frames().size()) {
            seggAnimScriptFrame = 0;
        }

        seggFrame = script.frames().get(seggAnimScriptFrame) & 0x1F;
        seggAnimScriptFrame++;

        if (seggAnimScriptFrame < script.frames().size()) {
            return;
        }

        switch (script.endAction()) {
            case HOLD -> seggAnimScriptFrame = script.frames().size() - 1;
            case LOOP_BACK -> {
                int loopBack = script.endParam();
                if (loopBack <= 0) {
                    seggAnimScriptFrame = 0;
                } else {
                    int target = script.frames().size() - loopBack;
                    seggAnimScriptFrame = Math.max(target, 0);
                }
            }
            case SWITCH -> {
                int nextAnim = script.endParam();
                if (nextAnim == seggAnim) {
                    seggAnimScriptFrame = 0;
                } else {
                    seggAnim = nextAnim;
                    seggAnimPrev = -1;
                }
            }
            case LOOP -> seggAnimScriptFrame = 0;
            default -> seggAnimScriptFrame = 0;
        }
    }

    /**
     * Expand camera right boundary by 2 per frame until boss_fz_end.
     * ROM: loc_1A166 — addq.w #2,(v_limitright2).w
     */
    private void expandCameraBoundary() {
        Camera camera = Camera.getInstance();
        int rightBoundary = camera.getMaxX() & 0xFFFF;
        if (rightBoundary < BOSS_FZ_END) {
            camera.setMaxX((short) (rightBoundary + 2));
        }

        // Unfreeze camera during escape so player can follow
        if (state.routineSecondary >= STATE_DEFEAT_FALL && camera.getFrozen()) {
            camera.setFrozen(false);
        }
    }

    /**
     * Called by SolidContacts when player is pushed by this boss.
     * Implements the FZ boss's unique damage mechanic:
     * ROM: loc_19F50 — check d4 > 0 (side contact) AND obAnim == id_Roll
     */
    @Override
    public void onSolidContact(AbstractPlayableSprite player, SolidContact contact, int frameCounter) {
        // Only process during cylinder attack phase
        if (state.routineSecondary != STATE_CYLINDER_ATTACK) return;

        // ROM: tst.w d4 / bgt.s loc_19F50 — side collision path (d4 > 0)
        if (!contact.touchSide()) return;

        // ROM: cmpi.b #id_Roll,(v_player+obAnim).w
        int animId = player.getAnimationId();
        boolean rollAnimating = animId == Sonic1AnimationIds.ROLL.id() || animId == Sonic1AnimationIds.ROLL2.id();
        if (!rollAnimating && !player.getRolling()) return;

        // ROM: Bounce player back — move.w #$300,d0
        int bounceVel = 0x300;
        if ((state.renderFlags & 1) == 0) {
            bounceVel = -bounceVel;
        }
        player.setXSpeed((short) bounceVel);

        // ROM: tst.b objoff_35 — check damage cooldown
        if (damageCooldown > 0) return;

        // Deal damage
        state.hitCount--;
        damageCooldown = 0x64; // ROM: move.b #$64,objoff_35

        // ROM: sfx_HitBoss
        AudioManager.getInstance().playSfx(Sonic1Sfx.HIT_BOSS.id);
    }

    /**
     * Called by cylinders when they finish retracting.
     * ROM: subq.w #1,objoff_32(a1) — decrement parent's active counter
     */
    public void onCylinderDone() {
        activeCylinderCount--;
    }

    /**
     * Called by plasma launcher when all balls are done.
     * ROM: move.w #-1,objoff_32(a1) — signal completion
     */
    public void onPlasmaComplete() {
        activeCylinderCount = -1;
    }

    /**
     * Keep boss position locked to the active host cylinder.
     * ROM: _incObj/84 FZ Eggman's Cylinders.asm loc_1A514.
     */
    void syncPositionFromCylinder(int x, int y) {
        state.x = x;
        state.y = y;
        state.xFixed = x << 16;
        state.yFixed = y << 16;
    }

    /**
     * Check if boss is defeated (for cylinder BossDefeated checks).
     */
    public boolean isBossDefeated() {
        return state.hitCount <= 0;
    }

    /**
     * ROM BossDefeated helper used by FZ child objects during post-defeat sequences.
     * Runs on an 8-frame cadence like v_vbla_byte&7 in the original routine.
     */
    void triggerBossDefeatedExplosion(int frameCounter) {
        if ((frameCounter & 7) == 0) {
            spawnDefeatExplosionAt(state.x, state.y);
        }
    }

    /**
     * ROM BossDefeated helper for FZ child components with independent positions.
     */
    void triggerBossDefeatedExplosion(int frameCounter, int sourceX, int sourceY) {
        if ((frameCounter & 7) == 0) {
            spawnDefeatExplosionAt(sourceX, sourceY);
        }
    }

    private void spawnDefeatExplosionAt(int sourceX, int sourceY) {
        ObjectRenderManager renderManager = services().renderManager();
        if (renderManager == null || services().objectManager() == null) {
            return;
        }

        int random = ThreadLocalRandom.current().nextInt(0x10000);
        int xOffset = ((random & 0xFF) >> 2) - 0x20;
        int yOffset = (((random >> 8) & 0xFF) >> 2) - 0x20;
        BossExplosionObjectInstance explosion = new BossExplosionObjectInstance(
                sourceX + xOffset,
                sourceY + yOffset,
                renderManager,
                Sonic1Sfx.BOSS_EXPLOSION.id);
        services().objectManager().addDynamicObject(explosion);
    }

    private void requestEndingTransition() {
        if (endingTransitionRequested) {
            return;
        }
        endingTransitionRequested = true;

        int endingAct = GameServices.gameState().hasAllEmeralds()
                ? ENDING_ACT_FLOWERS
                : ENDING_ACT_NO_EMERALDS;
        // ROM: move.b #id_Ending,(v_gamemode).w
        // Engine parity path: transition to the dedicated ending zone/act variant.
        GameServices.level().requestZoneAndAct(Sonic1ZoneConstants.ZONE_ENDING, endingAct, true);
    }

    // === SolidObjectProvider interface ===

    @Override
    public SolidObjectParams getSolidParams() {
        // ROM: Phases 6-10 use larger solid box (d1=$1B, d2=$70, d3=$71)
        if (state.routineSecondary >= STATE_DEFEAT_FALL &&
                state.routineSecondary < STATE_SHIP_TRANSFORM) {
            return ESCAPE_SOLID_PARAMS;
        }
        // ROM: Combat phase uses smaller box (d1=$2B, d2=$14, d3=$14)
        if (state.routineSecondary == STATE_CYLINDER_ATTACK) {
            return COMBAT_SOLID_PARAMS;
        }
        // No solidity in other states
        return null;
    }

    @Override
    public boolean isSolidFor(AbstractPlayableSprite player) {
        // ROM: Solid during cylinder attack and escape phases 6-10
        return state.routineSecondary == STATE_CYLINDER_ATTACK ||
                (state.routineSecondary >= STATE_DEFEAT_FALL &&
                        state.routineSecondary < STATE_SHIP_TRANSFORM);
    }

    @Override
    public int getTopLandingHalfWidth(AbstractPlayableSprite player, int collisionHalfWidth) {
        if (state.routineSecondary == STATE_CYLINDER_ATTACK) {
            return COMBAT_TOP_LANDING_HALF_WIDTH;
        }
        return collisionHalfWidth;
    }

    // === Rendering ===

    private boolean isBossOnScreen() {
        Camera camera = Camera.getInstance();
        int screenX = state.x - camera.getX();
        return screenX >= -64 && screenX <= 384;
    }

    @Override
    public int getPriorityBucket() {
        return 4; // ROM: obPriority = 4 (adjusted for escape = 2)
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = services().renderManager();
        if (renderManager == null) return;

        if (!escapingInShip) {
            // Draw using Map_SEgg (FZ Eggman in machine)
            PatternSpriteRenderer seggRenderer = renderManager.getRenderer(ObjectArtKeys.FZ_SEGG);
            if (seggRenderer != null && seggRenderer.isReady()) {
                boolean flipped = (state.renderFlags & 1) != 0;
                seggRenderer.drawFrameIndex(seggFrame, state.x, state.y, flipped, false);
            }
        } else {
            // Draw using Map_Eggman (standard Eggman escape ship)
            PatternSpriteRenderer eggmanRenderer = renderManager.getRenderer(ObjectArtKeys.EGGMAN);
            if (eggmanRenderer != null && eggmanRenderer.isReady()) {
                boolean flipped = (state.renderFlags & 1) != 0;

                // Keep the base escape ship body visible in all escape-hit states.
                eggmanRenderer.drawFrameIndex(0, state.x, state.y, flipped, false);

                if (!showDamaged) {
                    // Panic face (frame 6) before escape hit.
                    eggmanRenderer.drawFrameIndex(6, state.x, state.y, flipped, false);
                }

                // Flame overlay — escape flame
                eggmanRenderer.drawFrameIndex(11, state.x, state.y, flipped, false);
            }

            // Draw FZ legs overlay if legs are visible (sub-object routine 8)
            if (!showDamaged && state.routineSecondary >= STATE_SHIP_TRANSFORM && legsFrame <= 2) {
                PatternSpriteRenderer legsRenderer = renderManager.getRenderer(ObjectArtKeys.FZ_LEGS);
                if (legsRenderer != null && legsRenderer.isReady()) {
                    boolean flipped = (state.renderFlags & 1) != 0;
                    legsRenderer.drawFrameIndex(legsFrame, state.x, state.y, flipped, false);
                }
            }

            // Post-hit escape visuals overlay Map_FZDamaged on top of the base ship.
            if (showDamaged) {
                PatternSpriteRenderer damagedRenderer = renderManager.getRenderer(ObjectArtKeys.FZ_DAMAGED);
                if (damagedRenderer != null && damagedRenderer.isReady()) {
                    boolean flipped = (state.renderFlags & 1) != 0;
                    damagedRenderer.drawFrameIndex((state.lastUpdatedFrame >> 2) & 1, state.x, state.y, flipped, false);
                }
            }
        }
    }

}
