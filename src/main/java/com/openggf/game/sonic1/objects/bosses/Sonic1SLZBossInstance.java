package com.openggf.game.sonic1.objects.bosses;

import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.sonic1.audio.Sonic1Music;
import com.openggf.game.sonic1.objects.Sonic1SeesawObjectInstance;
import com.openggf.graphics.GLCommand;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.boss.AbstractBossInstance;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.ArrayList;
import java.util.List;

/**
 * Object 0x7A — Star Light Zone Boss (Eggman with seesaw bomb launcher).
 * ROM: _incObj/7A Boss - Star Light.asm
 *
 * Eggman oscillates horizontally over 3 seesaws, periodically dropping
 * spikeballs onto them. The player must jump on the other end of a seesaw
 * to launch the spikeball into Eggman.
 *
 * State machine (BossStarLight_ShipIndex, routineSecondary):
 *   0: ENTRANCE     — Approach from right at -0x100, sine Y oscillation, stop at X = 0x2120
 *   2: SCANNING     — Patrol at ±0x200, scan for seesaw alignment
 *   4: BALL_SPAWN   — Timer 0x28, drop spikeball on matched seesaw, then back to 2
 *   6: DEFEAT_WAIT  — Timer 0x78 countdown with explosions
 *   8: EXIT_JUMP    — Multi-stage upward bounce, play zone music
 *  10: ESCAPE       — X vel 0x400, Y vel -0x40, extend camera right, delete when off-screen
 *
 * Face, flame, and tube are rendered as overlays on the ship (not separate object instances).
 */
public class Sonic1SLZBossInstance extends AbstractBossInstance {

    // State machine constants (routineSecondary values, matching ROM's even-numbered index)
    private static final int STATE_ENTRANCE = 0;
    private static final int STATE_SCANNING = 2;
    private static final int STATE_BALL_SPAWN = 4;
    private static final int STATE_DEFEAT_WAIT = 6;
    private static final int STATE_EXIT_JUMP = 8;
    private static final int STATE_ESCAPE = 10;

    // Position constants from DynamicLevelEvents.asm / Constants.asm
    private static final int BOSS_SLZ_X = 0x2000;
    private static final int BOSS_SLZ_Y = 0x210;
    private static final int BOSS_SLZ_END = BOSS_SLZ_X + 0x160; // 0x2160

    // Entrance stop: boss_slz_x + $120
    private static final int ENTRANCE_STOP_X = BOSS_SLZ_X + 0x120;

    // Scanning boundaries (left/right patrol limits)
    private static final int SCAN_LEFT = BOSS_SLZ_X + 0x8;
    private static final int SCAN_RIGHT = BOSS_SLZ_X + 0x138;

    // Seesaw alignment offset: $28 pixels
    private static final int SEESAW_ALIGN_OFFSET = 0x28;

    // Timers
    private static final int BALL_SPAWN_DELAY = 0x28; // 40 frames
    private static final int DEFEAT_TIMER = 0x78;     // 120 frames

    // Velocities (8.8 fixed-point)
    private static final int ENTRANCE_X_VEL = -0x100;
    private static final int SCANNING_X_VEL = 0x200;
    private static final int ESCAPE_X_VEL = 0x400;
    private static final int ESCAPE_Y_VEL = -0x40;

    // Sine oscillation counter — objoff_3F
    private int sineAngle;

    // General-purpose timer — objoff_3C
    private int timer;

    // Face animation state
    private int faceAnim;

    // Flame animation state
    private int flameAnim;

    // Seesaw references (objoff_2A array, up to 3 seesaws)
    private final List<Sonic1SeesawObjectInstance> seesaws = new ArrayList<>();
    private boolean seesawsScanned;

    // Target seesaw index for ball spawn (obSubtype stores seesaw index 0-2)
    private int targetSeesawIndex;

    public Sonic1SLZBossInstance(ObjectSpawn spawn, LevelManager levelManager) {
        super(spawn, levelManager, "SLZ Boss");
    }

    @Override
    protected void initializeBossState() {
        state.routineSecondary = STATE_ENTRANCE;
        state.xVel = ENTRANCE_X_VEL; // -$100 — move left
        state.yVel = 0;

        // Store initial position in fixed-point (objoff_30/objoff_38)
        state.xFixed = state.x << 16;
        state.yFixed = state.y << 16;

        sineAngle = 0;
        timer = 0;
        faceAnim = Sonic1BossAnimations.ANIM_FACE_NORMAL_1;
        flameAnim = Sonic1BossAnimations.ANIM_FLAME_1;
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
        return false; // SLZ boss has custom defeat logic in states 6-10
    }

    @Override
    protected void onHitTaken(int remainingHits) {
        // ROM: sfx_HitBoss played by BossHitHandler
        faceAnim = Sonic1BossAnimations.ANIM_FACE_HIT;
    }

    @Override
    protected void onDefeatStarted() {
        // ROM: AddPoints 100, then transition to post-defeat pause
        GameServices.gameState().addScore(100);
        state.routineSecondary = STATE_DEFEAT_WAIT;
        state.xVel = 0;
        timer = DEFEAT_TIMER; // $78 = 120 frames
    }

    @Override
    protected void updateBossLogic(int frameCounter, AbstractPlayableSprite player) {
        // Lazy seesaw scanning — ensures seesaws are loaded before scanning
        if (!seesawsScanned) {
            scanForSeesaws();
        }

        // Track whether BossMove and sine should run after the state handler.
        // ROM: BossMove + sine (loc_189CA) only for specific states.
        boolean runBossMove = true;
        boolean runSine = true;

        switch (state.routineSecondary) {
            case STATE_ENTRANCE -> updateEntrance();
            case STATE_SCANNING -> updateScanning();
            case STATE_BALL_SPAWN -> {
                boolean timerExpired = updateBallSpawn();
                // ROM: only runs BossMove+sine on final frame (loc_18B40 -> loc_189CA)
                // Countdown frames branch to loc_189FE (no BossMove, no sine)
                runBossMove = timerExpired;
                runSine = timerExpired;
            }
            case STATE_DEFEAT_WAIT -> {
                updateDefeatWait(frameCounter);
                runBossMove = false;
                runSine = false;
            }
            case STATE_EXIT_JUMP -> {
                updateExitJump();
                // ROM: loc_189EE — BossMove but NO sine (Y set directly from yFixed)
                runSine = false;
            }
            case STATE_ESCAPE -> updateEscape();
        }

        if (runBossMove) {
            bossMove();
        }

        if (runSine) {
            updateSineOscillation();
        } else if (runBossMove) {
            // EXIT_JUMP/ESCAPE without sine: set display position directly
            updatePositionNoSine();
        }

        // Update face animation based on state
        updateFaceAnimation(player);

        // Update flame animation based on state
        updateFlameAnimation();
    }

    // === State 0: ENTRANCE ===
    // ROM: loc_189B8 — approach from right with sine Y oscillation
    private void updateEntrance() {
        state.xVel = ENTRANCE_X_VEL; // -$100

        // ROM: cmpi.w #boss_slz_x+$120,objoff_30(a0) / bhs.s loc_189CA
        // bhs = unsigned higher or same; stays while >= stop, advances when < stop
        if ((state.xFixed >> 16) < ENTRANCE_STOP_X) {
            state.routineSecondary = STATE_SCANNING;
        }
        // BossMove + sine called centrally after state handler
    }

    // === State 2: SCANNING ===
    // ROM: loc_18A5E — patrol left/right, scan for seesaw alignment
    private void updateScanning() {
        // ROM: move.w #$200,obVelX(a0) then negate if moving left
        state.xVel = SCANNING_X_VEL;

        // ROM: btst #0,obStatus(a0) / bne.s loc_18A7C
        if ((state.renderFlags & 1) == 0) {
            // Moving left
            state.xVel = -SCANNING_X_VEL;
            // ROM: cmpi.w #boss_slz_x+8,d0 / bgt.s loc_18A88
            if ((state.xFixed >> 16) <= SCAN_LEFT) {
                state.renderFlags ^= 1;
            }
        } else {
            // Moving right
            // ROM: cmpi.w #boss_slz_x+$138,d0 / blt.s loc_18A88
            if ((state.xFixed >> 16) >= SCAN_RIGHT) {
                state.renderFlags ^= 1;
            }
        }

        // Check seesaw alignment BEFORE BossMove (ROM order: loc_18A88 uses obX from previous frame)
        // ROM: d4 = $28, negated if obVelX < 0 (moving left)
        // Then checks: seesawX + d4 - bossX == 0
        int d4 = SEESAW_ALIGN_OFFSET;
        if (state.xVel < 0) {
            d4 = -d4;
        }
        int bossX = state.x; // display X from previous frame (matches ROM's obX)
        for (int i = 0; i < seesaws.size(); i++) {
            Sonic1SeesawObjectInstance seesaw = seesaws.get(i);
            if (seesaw.isDestroyed()) {
                continue;
            }
            // ROM: btst #3,obStatus(a3) — skip if player standing on seesaw
            if (seesaw.isPlayerStanding()) {
                continue;
            }
            int seesawX = seesaw.getSpawn().x();
            // ROM: exact match (beq.s loc_18AC0)
            if (seesawX + d4 == bossX) {
                // Matched seesaw — advance to ball spawn
                targetSeesawIndex = i;
                state.routineSecondary = STATE_BALL_SPAWN;
                timer = BALL_SPAWN_DELAY; // $28 = 40 frames
                spawnBossSpikeball();
                return;
            }
        }
        // BossMove + sine called centrally after state handler
    }

    // === State 4: BALL_SPAWN ===
    // ROM: BossStarLight_MakeBall — timer countdown, then back to scanning
    // Returns true on timer expiry (BossMove + sine should run via loc_189CA)
    private boolean updateBallSpawn() {
        timer--;
        if (timer <= 0) {
            // ROM: subq.b #2,ob2ndRout(a0) — back to scanning (loc_18B40 -> loc_189CA)
            state.routineSecondary = STATE_SCANNING;
            return true;
        }
        // ROM: countdown frames branch to loc_189FE (no BossMove, no sine)
        return false;
    }

    // === State 6: DEFEAT_WAIT ===
    // ROM: loc_18B48
    private void updateDefeatWait(int frameCounter) {
        timer--;
        if (timer < 0) {
            // Timer expired — start exit jump (loc_18B52)
            state.routineSecondary = STATE_EXIT_JUMP;
            state.renderFlags |= 1;     // bset #0,obStatus — face right
            state.renderFlags &= ~0x80; // bclr #7,obStatus
            state.xVel = 0;
            state.yVel = 0;
            timer = -0x18; // Start exit jump counter at -$18

            // ROM: v_bossstatus = 1 (boss defeated flag)
            GameServices.gameState().setCurrentBossId(0);
        } else {
            // Spawn explosions every 8 frames (BossDefeated)
            if ((frameCounter & 7) == 0) {
                spawnDefeatExplosion();
            }
        }
    }

    // === State 8: EXIT_JUMP ===
    // ROM: loc_18B80 — multi-stage upward movement
    // Uses loc_189EE (BossMove WITHOUT sine)
    private void updateExitJump() {
        timer++;

        if (timer == 0) {
            // loc_18B90: clear Y velocity
            state.yVel = 0;
        } else if (timer < 0) {
            // ROM: addi.w #$18,obVelY(a0) — pull toward zero
            state.yVel += 0x18;
        } else if (timer < 0x20) {
            // loc_18BAE: decelerate upward
            state.yVel -= 8;
        } else if (timer == 0x20) {
            // loc_18BB4: stop and play zone music
            state.yVel = 0;
            AudioManager.getInstance().playMusic(Sonic1Music.SLZ.id);
        } else if (timer >= 0x2A) {
            // Advance to escape
            state.routineSecondary = STATE_ESCAPE;
        }
        // BossMove (no sine) called centrally after state handler
    }

    // === State 10: ESCAPE ===
    // ROM: loc_18BC6
    private void updateEscape() {
        state.xVel = ESCAPE_X_VEL;  // $400
        state.yVel = ESCAPE_Y_VEL;  // -$40

        Camera camera = Camera.getInstance();
        int rightBoundary = camera.getMaxX() & 0xFFFF;

        if (rightBoundary >= BOSS_SLZ_END) {
            // ROM: tst.b obRender(a0) / bpl.w BossStarLight_Delete
            if (!isBossOnScreen()) {
                setDestroyed(true);
                return;
            }
        } else {
            // ROM: addq.w #2,(v_limitright2).w
            camera.setMaxX((short) (rightBoundary + 2));
        }
        // BossMove + sine called centrally after state handler
    }

    /**
     * BossMove subroutine — applies velocity to fixed-point position.
     * ROM: sonic.asm (BossMove) — applies xVel/yVel shifted left 8 to fixed-point X/Y.
     */
    private void bossMove() {
        state.xFixed += (state.xVel << 8);
        state.yFixed += (state.yVel << 8);
    }

    /**
     * Apply sine oscillation to Y position and update display coordinates.
     * ROM: loc_189CA — CalcSine(objoff_3F) >> 6 + objoff_38 -> obY
     */
    private void updateSineOscillation() {
        int sinVal = TrigLookupTable.sinHex(sineAngle & 0xFF);
        int yOffset = sinVal >> 6;

        state.y = (state.yFixed >> 16) + yOffset;
        state.x = state.xFixed >> 16;

        sineAngle = (sineAngle + 2) & 0xFF;
    }

    /**
     * Update display position from fixed-point without sine oscillation.
     * ROM: loc_189EE — sets obY directly from objoff_38, obX from objoff_30.
     */
    private void updatePositionNoSine() {
        state.y = state.yFixed >> 16;
        state.x = state.xFixed >> 16;
    }

    /**
     * Update face animation based on boss state.
     * ROM: BossStarLight_FaceMain (routine 4)
     */
    private void updateFaceAnimation(AbstractPlayableSprite player) {
        if (state.routineSecondary >= STATE_DEFEAT_WAIT) {
            // ROM: ob2ndRout >= 6: anim = $A (facedefeat)
            // Then: if ob2ndRout == $A (ESCAPE): anim = 6 (facepanic)
            if (state.routineSecondary == STATE_ESCAPE) {
                faceAnim = Sonic1BossAnimations.ANIM_FACE_PANIC;
            } else {
                faceAnim = Sonic1BossAnimations.ANIM_FACE_DEFEAT;
            }
            return;
        }

        // During combat: check if being hit (invulnerable = flash)
        // ROM: tst.b obColType(a1) / bne.s — if collision disabled, show hit face
        if (state.invulnerable) {
            faceAnim = Sonic1BossAnimations.ANIM_FACE_HIT;
            return;
        }

        // ROM: check if player is hurt (routine >= 4) — laugh
        if (player != null && player.isHurt()) {
            faceAnim = Sonic1BossAnimations.ANIM_FACE_LAUGH;
            return;
        }

        faceAnim = Sonic1BossAnimations.ANIM_FACE_NORMAL_1;
    }

    /**
     * Update flame animation based on boss state.
     * ROM: BossStarLight_FlameMain (routine 6)
     *
     * Default = anim 8 (flame1).
     * If ob2ndRout == $A (ESCAPE): anim = $B (escape flame).
     * If ob2ndRout >= 4 AND ob2ndRout <= 8: anim = 7 (blank) — flame OFF.
     */
    private void updateFlameAnimation() {
        if (state.routineSecondary == STATE_ESCAPE) {
            flameAnim = Sonic1BossAnimations.ANIM_ESCAPE_FLAME;
        } else if (state.routineSecondary >= STATE_BALL_SPAWN
                && state.routineSecondary <= STATE_EXIT_JUMP) {
            // ROM: ob2ndRout between 4 and 8 inclusive — flame off
            flameAnim = Sonic1BossAnimations.ANIM_BLANK;
        } else {
            flameAnim = Sonic1BossAnimations.ANIM_FLAME_1;
        }
    }

    /**
     * Scan active objects for seesaws with subtype != 0 (no pre-spawned ball).
     * ROM: loc_18968 — scans all object RAM for id_Seesaw with obSubtype != 0.
     * Stores up to 3 seesaw references in objoff_2A array.
     */
    private void scanForSeesaws() {
        seesawsScanned = true;
        seesaws.clear();

        if (levelManager.getObjectManager() == null) {
            return;
        }

        for (ObjectInstance obj : levelManager.getObjectManager().getActiveObjects()) {
            if (obj instanceof Sonic1SeesawObjectInstance seesaw) {
                // ROM: tst.b obSubtype(a1) / beq.s .next — only seesaws with subtype != 0
                if (seesaw.getSpawn().subtype() != 0) {
                    seesaws.add(seesaw);
                    if (seesaws.size() >= 3) {
                        break; // Max 3 seesaws
                    }
                }
            }
        }
    }

    /**
     * Spawn a boss spikeball aimed at the target seesaw.
     * ROM: creates BossSpikeball (id_BossSpikeball) with seesaw reference.
     */
    private void spawnBossSpikeball() {
        if (targetSeesawIndex < 0 || targetSeesawIndex >= seesaws.size()) {
            return;
        }

        Sonic1SeesawObjectInstance targetSeesaw = seesaws.get(targetSeesawIndex);
        if (targetSeesaw.isDestroyed()) {
            return;
        }

        // ROM: move.w obY(a0),obY(a1) / addi.w #$20,obY(a1) — spawn +$20 below boss
        Sonic1SLZBossSpikeball spikeball = new Sonic1SLZBossSpikeball(
                this,
                targetSeesaw,
                state.x,
                state.y + 0x20);

        if (levelManager.getObjectManager() != null) {
            levelManager.getObjectManager().addDynamicObject(spikeball);
        }
    }

    /**
     * Called by BossSpikeball when it collides with the boss.
     * Triggers a hit via the standard hit handler.
     */
    public void onSpikeballHit() {
        hitHandler.processHit(null);
    }

    private boolean isBossOnScreen() {
        Camera camera = Camera.getInstance();
        int screenX = state.x - camera.getX();
        return screenX >= -64 && screenX <= 384;
    }

    @Override
    public int getPriorityBucket() {
        return 3; // ROM: obPriority = 3
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = levelManager.getObjectRenderManager();
        if (renderManager == null) {
            return;
        }

        PatternSpriteRenderer eggmanRenderer = renderManager.getRenderer(ObjectArtKeys.EGGMAN);
        if (eggmanRenderer == null || !eggmanRenderer.isReady()) {
            return;
        }

        boolean flipped = (state.renderFlags & 1) != 0;

        // Draw ship body (frame 0)
        eggmanRenderer.drawFrameIndex(0, state.x, state.y, flipped, false);

        // Draw face overlay (frames 1-7)
        int faceFrame = getFaceFrame();
        if (faceFrame >= 0) {
            eggmanRenderer.drawFrameIndex(faceFrame, state.x, state.y, flipped, false);
        }

        // Draw flame overlay (frames 8-12)
        int flameFrame = getFlameFrame();
        if (flameFrame >= 0) {
            eggmanRenderer.drawFrameIndex(flameFrame, state.x, state.y, flipped, false);
        }

        // Draw tube/jet pipe (Map_BossItems frame 3 = .widepipe)
        // ROM: BossStarLight_TubeMain — uses Map_BossItems with ArtTile_Eggman_Weapons
        if (state.routineSecondary != STATE_ESCAPE || isBossOnScreen()) {
            PatternSpriteRenderer weaponsRenderer = renderManager.getRenderer(ObjectArtKeys.BOSS_WEAPONS);
            if (weaponsRenderer != null && weaponsRenderer.isReady()) {
                weaponsRenderer.drawFrameIndex(3, state.x, state.y, flipped, false);
            }
        }
    }

    /**
     * Map face animation ID to current mapping frame index.
     */
    private int getFaceFrame() {
        return switch (faceAnim) {
            case Sonic1BossAnimations.ANIM_FACE_NORMAL_1,
                 Sonic1BossAnimations.ANIM_FACE_NORMAL_2,
                 Sonic1BossAnimations.ANIM_FACE_NORMAL_3 -> 1;
            case Sonic1BossAnimations.ANIM_FACE_LAUGH -> 3;
            case Sonic1BossAnimations.ANIM_FACE_HIT -> 5;
            case Sonic1BossAnimations.ANIM_FACE_PANIC -> 6;
            case Sonic1BossAnimations.ANIM_FACE_DEFEAT -> 7;
            default -> -1;
        };
    }

    /**
     * Map flame animation ID to current mapping frame index.
     */
    private int getFlameFrame() {
        return switch (flameAnim) {
            case Sonic1BossAnimations.ANIM_FLAME_1,
                 Sonic1BossAnimations.ANIM_FLAME_2 -> 8;
            case Sonic1BossAnimations.ANIM_ESCAPE_FLAME -> 11;
            case Sonic1BossAnimations.ANIM_BLANK -> -1;
            default -> -1;
        };
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
