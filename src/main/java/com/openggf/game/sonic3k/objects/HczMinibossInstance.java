package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.graphics.GLCommand;
import com.openggf.level.Palette;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SplashObjectInstance;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.objects.boss.AbstractBossInstance;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Hydrocity Act 1 miniboss (object 0x99).
 *
 * <p>The implementation keeps the encounter self-contained: ROM trigger bounds,
 * camera lock, PLC-loaded art, multi-region hurt boxes, a drop/rise/dive attack
 * loop with orbiting rockets, the suction phase, custom palette flashing, and
 * a signpost-style defeat handoff.
 */
public class HczMinibossInstance extends AbstractBossInstance {
    private static final Logger LOG = Logger.getLogger(HczMinibossInstance.class.getName());

    private static final int ROUTINE_INIT = 0;
    private static final int ROUTINE_WAIT_TRIGGER = 2;
    private static final int ROUTINE_WAIT_FADE = 4;
    private static final int ROUTINE_DESCEND = 6;
    private static final int ROUTINE_WAIT = 8;
    private static final int ROUTINE_RISE = 10;
    private static final int ROUTINE_DIVE = 12;
    private static final int ROUTINE_STRAFE = 14;
    private static final int ROUTINE_ASCEND = 16;
    private static final int ROUTINE_PRE_VORTEX_DRIFT = 18;
    private static final int ROUTINE_VORTEX = 20;
    private static final int ROUTINE_COOLDOWN = 22;
    private static final int ROUTINE_SLOW_RISE = 24;
    private static final int ROUTINE_DEFEATED = 26;

    private static final int HIT_COUNT = 6;
    private static final int COLLISION_SIZE = 0x0F;
    private static final int CORE_COLLISION_FLAGS = COLLISION_SIZE;
    private static final int ENGINE_COLLISION_FLAGS = 0x92;
    private static final int ROCKET_COLLISION_FLAGS = 0x8B;
    private static final int INVULN_TIME = 0x20;

    private static final int TRIGGER_MIN_Y = 0x300;
    private static final int TRIGGER_MAX_Y = 0x400;
    private static final int TRIGGER_MIN_X = 0x3500;
    private static final int TRIGGER_MAX_X = 0x3700;
    private static final int ARENA_LOCK_X = 0x3680;
    private static final int ARENA_LOCK_Y = 0x638;
    private static final int FADE_OUT_TIME = 2 * 60;

    private static final int DESCEND_VEL = 0x100;
    private static final int DESCEND_TIME = 0xDF;
    private static final int WAIT_TIME = 60 - 1;
    private static final int RISE_VEL = -0x400;
    private static final int RISE_TIME = 0x37;
    private static final int DIVE_VEL = 0x400;
    private static final int DIVE_TIME = 0x47;
    private static final int STRAFE_TIME = 0x2F;
    private static final int VORTEX_TIME = 0x17F;
    private static final int COOLDOWN_TIME = 0x7F;
    private static final int REOPEN_TIME = 0x3F;

    private static final int ENGINE_OFFSET_Y = 0x24;
    private static final int ENGINE_FRAME = 0x15;
    private static final int WATER_EFFECT_OFFSET_Y = 0x148;
    private static final int WATER_EFFECT_BASE_FRAME = 0x16;
    private static final int FLOOR_CHECK_RADIUS = 0x28;
    private static final int ROCKET_PHASE_STEP = 4;
    private static final int VORTEX_PHASE_STEP = 2;
    private static final int VORTEX_PULL_X = 2;
    private static final int VORTEX_PULL_Y = 1;
    private static final int PRE_VORTEX_DRIFT_VEL = 0x180;
    private static final int PRE_VORTEX_DRIFT_GRAVITY = 0x20;
    private static final int SLOW_RISE_VEL = -0x20;
    private static final int VORTEX_APPROACH_Y = 0x108;


    private static final int[][] ATTACK_PATTERNS = {
            {0x40, 1},
            {0x100, 1},
            {0x40, 0},
            {0x40, 1},
            {0x100, 0},
            {0x100, 1},
            {0x40, 0},
            {0x100, 0}
    };

    private static final int[] ROCKET_FRAMES = {
            0x01, 0x02, 0x03, 0x04,
            0x05, 0x06, 0x07, 0x08,
            0x09, 0x0A, 0x0B, 0x1A,
            0x1A, 0x1A, 0x0C, 0x0D
    };

    private static final int[] ENGINE_FRAMES = {
            0x0E, 0x0F, 0x10, 0x11,
            0x12, 0x11, 0x10, 0x0F,
            0x0E, 0x1A, 0x1A, 0x1A,
            0x0E, 0x0E, 0x1A, 0x1A
    };

    private static final int[] ENGINE_CHILD_OFFSETS = {
            3, 3,
            0, 0,
            6, 6,
            0x0C, 0x0C,
            0x12, 0x12,
            0x0C, 0x0C,
            8, 8,
            0, 0,
            3, 3,
            0, 0,
            0, 0,
            0, 0,
            -6, -6,
            -0x0A, -0x0A,
            0, 0,
            0, 0
    };

    private static final int[] ENGINE_CHILD_PRIORITIES = {
            0x280, 0x200, 0x200, 0x200,
            0x200, 0x180, 0x180, 0x180,
            0x180, 0x180, 0x180, 0x280,
            0x280, 0x280, 0x280, 0x280
    };

    /**
     * HCZMiniboss_RocketTwistLookup: 128-entry signed byte table used by sub_489BA.
     * For phase 0x00-0x7F, index directly. For 0x80-0xFF, mirror: index = 0xFF - phase.
     * The same table is used for BOTH X and Y offsets (not sin/cos).
     */
    private static final byte[] ROCKET_TWIST_LOOKUP = {
            0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x17, 0x17, 0x17, 0x17, 0x17, 0x17, 0x16,
            0x16, 0x16, 0x16, 0x15, 0x15, 0x15, 0x15, 0x14, 0x14, 0x14, 0x13, 0x13, 0x13, 0x12, 0x12, 0x11,
            0x11, 0x11, 0x10, 0x10, 0x0F, 0x0F, 0x0E, 0x0E, 0x0D, 0x0D, 0x0C, 0x0C, 0x0B, 0x0B, 0x0A, 0x0A,
            0x09, 0x09, 0x08, 0x08, 0x07, 0x06, 0x06, 0x05, 0x05, 0x04, 0x04, 0x03, 0x02, 0x02, 0x01, 0x01,
            0x00, -0x01, -0x01, -0x02, -0x02, -0x03, -0x04, -0x04, -0x05, -0x05, -0x06, -0x06, -0x07, -0x08, -0x08, -0x09,
            -0x09, -0x0A, -0x0A, -0x0B, -0x0B, -0x0C, -0x0C, -0x0D, -0x0D, -0x0E, -0x0E, -0x0F, -0x0F, -0x10, -0x10, -0x11,
            -0x11, -0x11, -0x12, -0x12, -0x13, -0x13, -0x13, -0x14, -0x14, -0x14, -0x15, -0x15, -0x15, -0x15, -0x16, -0x16,
            -0x16, -0x16, -0x17, -0x17, -0x17, -0x17, -0x17, -0x17, -0x18, -0x18, -0x18, -0x18, -0x18, -0x18, -0x18, -0x18
    };

    private static final int[] FLASH_INDICES = {4, 7, 9, 10, 11, 13, 14};
    private static final int[] FLASH_DARK = {0x0004, 0x0000, 0x000C, 0x0008, 0x0020, 0x0826, 0x0624};
    private static final int[] FLASH_BRIGHT = {0x0AAA, 0x0AAA, 0x0888, 0x0AAA, 0x0EEE, 0x0888, 0x0AAA};

    private RocketState[] rockets;

    private int anchorY;
    private int waterLevelY;
    private int waitTimer = -1;
    private Runnable waitCallback;
    private int ascendTargetY;
    private Runnable ascendCallback;
    private int attackPatternIndex;
    private int storedHorizontalVelocity;
    private int passCounter;
    private boolean closedBody;
    private boolean rocketsArmed;
    private int rocketOrbitSpeed;
    private int rocketSpeedTimer;
    private Runnable rocketSpeedCallback;
    private boolean arenaYLocked;
    private boolean arenaXLocked;
    private boolean customFlashDirty;
    private boolean vortexActive;
    private boolean defeatRenderComplete;
    private boolean crossedWaterThisPass;
    private boolean waterPaletteLoaded;
    private int waterEffectFrame;
    private int lastFrameCounter;
    private List<VortexBubbleChild> vortexBubbles;
    private S3kBossExplosionController defeatExplosionController;

    private static final class RocketState {
        private int phaseX;
        private int phaseY;
        private int x;
        private int y;
        private int frame;
        private boolean front;
        private boolean hFlip;

        private RocketState(int phaseX, int phaseY, boolean hFlip) {
            this.phaseX = phaseX;
            this.phaseY = phaseY;
            this.hFlip = hFlip;
            this.frame = 1;
        }
    }

    public HczMinibossInstance(ObjectSpawn spawn) {
        super(spawn, "HCZMiniboss");
    }

    @Override
    protected void initializeBossState() {
        ensureRocketState();
        anchorY = spawn.y();
        waterLevelY = anchorY + 0x100;
        state.routine = ROUTINE_INIT;
        state.hitCount = HIT_COUNT;
        state.renderFlags = 0;
        attackPatternIndex = 0;
        storedHorizontalVelocity = 0;
        passCounter = 0;
        closedBody = false;
        rocketsArmed = false;
        rocketOrbitSpeed = ROCKET_PHASE_STEP;
        rocketSpeedTimer = -1;
        rocketSpeedCallback = null;
        waitTimer = -1;
        waitCallback = null;
        ascendTargetY = anchorY;
        ascendCallback = null;
        arenaYLocked = false;
        arenaXLocked = false;
        customFlashDirty = false;
        vortexActive = false;
        defeatRenderComplete = false;
        crossedWaterThisPass = false;
        waterPaletteLoaded = false;
        waterEffectFrame = WATER_EFFECT_BASE_FRAME;
        vortexBubbles = new ArrayList<>();
        defeatExplosionController = null;
        resetRocketPhases();
    }

    @Override
    protected int getInitialHitCount() {
        return HIT_COUNT;
    }

    @Override
    protected void onHitTaken(int remainingHits) {
        customFlashDirty = true;
    }

    @Override
    protected int getCollisionSizeIndex() {
        return COLLISION_SIZE;
    }

    @Override
    protected boolean usesDefeatSequencer() {
        return false;
    }

    @Override
    protected int getInvulnerabilityDuration() {
        return INVULN_TIME;
    }

    @Override
    protected int getPaletteLineForFlash() {
        return -1;
    }

    @Override
    public int getCollisionFlags() {
        return getCoreCollisionFlags();
    }

    @Override
    public TouchResponseProvider.TouchRegion[] getMultiTouchRegions() {
        if (!isFightVisible() || state.defeated) {
            return null;
        }

        List<TouchResponseProvider.TouchRegion> regions = new ArrayList<>(6);
        int coreFlags = getCoreCollisionFlags();
        if (coreFlags != 0) {
            regions.add(new TouchResponseProvider.TouchRegion(state.x, state.y, coreFlags));
        }

        if (!closedBody) {
            regions.add(new TouchResponseProvider.TouchRegion(
                    state.x, state.y + ENGINE_OFFSET_Y, ENGINE_COLLISION_FLAGS));
        }
        for (RocketState rocket : rockets()) {
            regions.add(new TouchResponseProvider.TouchRegion(rocket.x, rocket.y, getRocketCollisionFlags()));
        }
        return regions.toArray(new TouchResponseProvider.TouchRegion[0]);
    }

    @Override
    public void onPlayerAttack(PlayableEntity playerEntity, TouchResponseResult result) {
        if (getCoreCollisionFlags() == 0 || state.invulnerable || state.defeated) {
            return;
        }

        state.hitCount--;
        state.invulnerabilityTimer = INVULN_TIME;
        state.invulnerable = true;
        paletteFlasher.startFlash();
        services().playSfx(Sonic3kSfx.BOSS_HIT.id);
        customFlashDirty = true;
        onHitTaken(state.hitCount);

        if (state.hitCount <= 0) {
            state.hitCount = 0;
            state.defeated = true;
            services().gameState().addScore(1000);
            onDefeatStarted();
        }
    }

    @Override
    protected void onDefeatStarted() {
        state.routine = ROUTINE_DEFEATED;
        state.xVel = 0;
        state.yVel = 0;
        waitTimer = -1;
        waitCallback = null;
        vortexActive = false;
        state.invulnerable = false;
        state.invulnerabilityTimer = 0;
        loadBossPalette();
        defeatExplosionController = new S3kBossExplosionController(state.x, state.y, 0);
        services().fadeOutMusic();
        services().gameState().setCurrentBossId(0);
    }

    @Override
    protected void updateBossLogic(int frameCounter, PlayableEntity playerEntity) {
        AbstractPlayableSprite player = playerEntity instanceof AbstractPlayableSprite aps ? aps : null;
        lastFrameCounter = frameCounter;
        updateWaterLevel();
        ensureWaterEffectPalette();

        switch (state.routine) {
            case ROUTINE_INIT -> updateInit();
            case ROUTINE_WAIT_TRIGGER -> updateWaitTrigger();
            case ROUTINE_WAIT_FADE, ROUTINE_WAIT, ROUTINE_COOLDOWN -> updateWaitOnly();
            case ROUTINE_DESCEND, ROUTINE_RISE -> updateMoveAndWait();
            case ROUTINE_DIVE -> updateDive();
            case ROUTINE_STRAFE -> updateStrafe();
            case ROUTINE_ASCEND -> updateAscend();
            case ROUTINE_PRE_VORTEX_DRIFT -> updatePreVortexDrift();
            case ROUTINE_VORTEX -> updateVortex(player);
            case ROUTINE_SLOW_RISE -> updateSlowRise();
            case ROUTINE_DEFEATED -> updateDefeated();
            default -> {
            }
        }

        updateRocketOrbit();
        updateWaterEffect(frameCounter);
        updateCustomFlash();
        updateDynamicSpawn(state.x, state.y);
    }

    private void updateInit() {
        if (!isCameraInTriggerWindow()) {
            return;
        }
        state.routine = ROUTINE_WAIT_TRIGGER;
        services().camera().setMinY((short) TRIGGER_MIN_Y);
    }

    private void updateWaitTrigger() {
        var camera = services().camera();
        int cameraX = camera.getX();
        int cameraY = camera.getY();
        camera.setMinY((short) TRIGGER_MIN_Y);
        if (!arenaYLocked && cameraY >= ARENA_LOCK_Y) {
            camera.setMinY((short) ARENA_LOCK_Y);
            camera.setMaxY((short) ARENA_LOCK_Y);
            arenaYLocked = true;
        }

        if (!arenaXLocked) {
            camera.setMinX((short) cameraX);
            if (cameraX >= ARENA_LOCK_X) {
                camera.setMinX((short) ARENA_LOCK_X);
                camera.setMaxX((short) ARENA_LOCK_X);
                arenaXLocked = true;
            }
        }

        if (!arenaXLocked || !arenaYLocked) {
            return;
        }

        state.routine = ROUTINE_WAIT_FADE;
        services().fadeOutMusic();
        loadBossPalette();
        setWait(FADE_OUT_TIME, this::startFight);
    }

    private void startFight() {
        services().gameState().setCurrentBossId(0x99);
        services().playMusic(Sonic3kMusic.MINIBOSS.id);
        beginRocketWindUp();
        state.routine = ROUTINE_DESCEND;
        state.yVel = DESCEND_VEL;
        crossedWaterThisPass = false;
        resetRocketPhases();
        setWait(DESCEND_TIME, this::finishDescent);
    }

    private void finishDescent() {
        state.routine = ROUTINE_WAIT;
        state.yVel = 0;
        setWait(WAIT_TIME, this::startRise);
    }

    private void startRise() {
        state.routine = ROUTINE_RISE;
        state.yVel = RISE_VEL;
        setWait(RISE_TIME, this::finishRise);
    }

    private void finishRise() {
        state.routine = ROUTINE_WAIT;
        state.yVel = 0;
        setWait(WAIT_TIME, this::selectAttackPattern);
    }

    /**
     * sub_48916: Reads next attack pattern entry, positions boss, stores direction and pass count.
     * Called once per attack cycle (before the first dive of the cycle).
     */
    private void selectAttackPattern() {
        int[] pattern = ATTACK_PATTERNS[attackPatternIndex];
        attackPatternIndex = (attackPatternIndex + 1) % ATTACK_PATTERNS.length;

        int xOffset = pattern[0];
        passCounter = pattern[1];
        storedHorizontalVelocity = (xOffset < 0xA0) ? 0x400 : -0x400;
        int targetX = services().camera().getX() + xOffset;
        setBossPosition(targetX, state.y);
        setFacingLeft(storedHorizontalVelocity < 0);

        startDive();
    }

    /**
     * loc_47E96: Start a dive. The timer/callback set here ($47 / loc_47EE8) are only
     * used as a dead fallback — ObjHitFloor_DoRoutine fires the callback directly
     * when the floor is hit, bypassing Obj_Wait entirely.
     */
    private void startDive() {
        state.routine = ROUTINE_DIVE;
        state.xVel = 0;
        state.yVel = DIVE_VEL;
        closedBody = true;
        crossedWaterThisPass = false;
        services().playSfx(Sonic3kSfx.ROLL.id);
    }

    /**
     * loc_47EE8: Called directly by ObjHitFloor_DoRoutine when the floor is hit during
     * the dive — NOT via timer. Starts horizontal strafe movement immediately.
     * Uses stored velocity then negates it for the next pass.
     */
    private void startStrafe() {
        state.routine = ROUTINE_STRAFE;
        state.xVel = storedHorizontalVelocity;
        storedHorizontalVelocity = -storedHorizontalVelocity;
        state.yVel = 0;
        crossedWaterThisPass = false;
        setWait(STRAFE_TIME, this::finishStrafe);
    }

    /**
     * loc_47F28: After strafe completes, decrement pass counter.
     * If passes remain (>=0), ascend to anchor and dive again (routine $E).
     * If exhausted (<0), ascend to vortex position (routine $10).
     */
    private void finishStrafe() {
        state.xVel = 0;
        state.yVel = RISE_VEL;
        passCounter--;
        if (passCounter < 0) {
            startAscend(anchorY + VORTEX_APPROACH_Y, this::startPreVortexDrift);
        } else {
            startAscend(anchorY, this::startDive);
        }
    }

    /**
     * loc_47F7A: Pre-vortex drift. Snaps Y to target (anchor + $108),
     * drifts horizontally based on $40(a0) sign. Does NOT set y_vel —
     * the ascend velocity (-$400) carries over, and gravity in
     * updatePreVortexDrift decelerates it into a parabolic arc.
     */
    private void startPreVortexDrift() {
        state.routine = ROUTINE_PRE_VORTEX_DRIFT;
        setBossPosition(state.x, anchorY + VORTEX_APPROACH_Y);
        crossedWaterThisPass = false;
        int driftVel = PRE_VORTEX_DRIFT_VEL;
        if (storedHorizontalVelocity < 0) {
            driftVel = -driftVel;
        }
        state.xVel = driftVel;
        setWait(REOPEN_TIME, this::startVortexWindup);
    }

    /**
     * loc_47FBC: Vortex windup. bclr #3,$38(a0) closes the boss —
     * rockets detect this and return to home position, losing collision.
     */
    private void startVortexWindup() {
        state.routine = ROUTINE_WAIT;
        state.xVel = 0;
        state.yVel = 0;
        rocketsArmed = false;
        beginRocketWindDown();
        services().playSfx(Sonic3kSfx.DOOR_CLOSE.id);
        setWait(0x9F, this::beginVortexSequence);
    }

    private void beginVortexSequence() {
        state.routine = ROUTINE_VORTEX;
        state.xVel = 0;
        state.yVel = 0;
        vortexActive = true;
        crossedWaterThisPass = true;
        services().playSfx(Sonic3kSfx.FAN_BIG.id);
        spawnVortexBubbleBatch();
        setWait(VORTEX_TIME, this::endVortex);
    }

    private void endVortex() {
        vortexActive = false;
        releaseVortexPlayers();
        for (VortexBubbleChild bubble : vortexBubbles) {
            bubble.signalVortexEnd();
        }
        vortexBubbles.clear();
        state.routine = ROUTINE_COOLDOWN;
        setWait(COOLDOWN_TIME, this::startPostVortexPause);
    }

    private void releaseVortexPlayers() {
        PlayableEntity focused = services().camera().getFocusedSprite();
        if (focused instanceof AbstractPlayableSprite player && player.isObjectControlled()) {
            player.setObjectControlled(false);
            player.setForcedAnimationId(-1);
        }
        for (PlayableEntity entity : services().sidekicks()) {
            if (entity instanceof AbstractPlayableSprite sidekick && sidekick.isObjectControlled()) {
                sidekick.setObjectControlled(false);
                sidekick.setForcedAnimationId(-1);
            }
        }
    }

    /**
     * loc_48010: Post-vortex reopen. bset #3,$38(a0) reopens the boss —
     * rockets detect this and begin spin-up sequence, regaining collision.
     */
    private void startPostVortexPause() {
        state.routine = ROUTINE_WAIT;
        state.xVel = 0;
        state.yVel = 0;
        beginRocketWindUp();
        setWait(REOPEN_TIME, this::startSlowRiseFromVortex);
    }

    private void startSlowRiseFromVortex() {
        state.routine = ROUTINE_SLOW_RISE;
        state.xVel = 0;
        state.yVel = SLOW_RISE_VEL;
        setWait(0x7F, this::startAscendToAnchor);
    }

    /**
     * loc_4804A: Final ascend to anchor after vortex. Clears closed body, plays sfx.
     */
    private void startAscendToAnchor() {
        closedBody = false;
        services().playSfx(Sonic3kSfx.LAVA_BALL.id);
        startAscend(anchorY, this::selectAttackPattern);
    }

    private void startAscend(int targetY, Runnable callback) {
        state.routine = ROUTINE_ASCEND;
        state.xVel = 0;
        state.yVel = RISE_VEL;
        ascendTargetY = targetY;
        ascendCallback = callback;
    }

    private void updateWaitOnly() {
        tickWait();
    }

    private void updateMoveAndWait() {
        int previousY = state.y;
        state.applyVelocity();
        updateWaterCrossing(previousY, state.y);
        tickWait();
    }

    /**
     * Routine $A (loc_47EC6): Dive — water check, MoveSprite2, ObjHitFloor_DoRoutine.
     * ObjHitFloor_DoRoutine fires the callback ($34 = loc_47EE8 = startStrafe) directly
     * when floor is hit. No Obj_Wait, no timer ticking during the dive.
     */
    private void updateDive() {
        if (!crossedWaterThisPass) {
            if (state.y >= waterLevelY) {
                triggerWaterSplash();
            }
        }
        state.applyVelocity();
        TerrainCheckResult floor = ObjectTerrainUtils.checkFloorDist(state.x, state.y, FLOOR_CHECK_RADIUS);
        if (floor.hasCollision()) {
            setBossPosition(state.x, state.y + floor.distance());
            startStrafe();
        }
    }

    private void updateStrafe() {
        state.applyVelocity();
        followFloorCurve();
        tickWait();
    }

    /**
     * Shared ascend handler for routines $E and $10.
     * Does NOT snap Y or clear velocity — matches the disasm where loc_47F48
     * jumps straight to loc_47E96 with current y_pos when y <= target.
     * Callbacks that need snapping (e.g. pre-vortex drift) do it themselves.
     */
    private void updateAscend() {
        checkWaterSplashDuringAscent();
        int previousY = state.y;
        state.applyVelocity();
        updateWaterCrossing(previousY, state.y);
        if (state.y > ascendTargetY) {
            return;
        }
        Runnable callback = ascendCallback;
        ascendCallback = null;
        if (callback != null) {
            callback.run();
        }
    }

    private void updateVortex(AbstractPlayableSprite player) {
        applyVortexPull(player);
        tickWait();
    }

    private void updatePreVortexDrift() {
        state.yVel += PRE_VORTEX_DRIFT_GRAVITY;
        state.applyVelocity();
        tickWait();
    }

    private void updateSlowRise() {
        int previousY = state.y;
        state.applyVelocity();
        updateWaterCrossing(previousY, state.y);
        tickWait();
    }

    private void updateDefeated() {
        if (defeatExplosionController == null) {
            return;
        }
        defeatExplosionController.tick();
        for (var pending : defeatExplosionController.drainPendingExplosions()) {
            if (pending.playSfx()) {
                services().playSfx(Sonic3kSfx.EXPLODE.id);
            }
            spawnChild(() -> new S3kBossExplosionChild(pending.x(), pending.y()));
        }
        if (!defeatExplosionController.isFinished() || defeatRenderComplete) {
            return;
        }
        defeatRenderComplete = true;
        spawnChild(() -> new S3kBossDefeatSignpostFlow(state.x, 0, null));
        setDestroyed(true);
    }

    private void tickWait() {
        if (waitTimer < 0) {
            return;
        }
        waitTimer--;
        if (waitTimer >= 0) {
            return;
        }
        Runnable callback = waitCallback;
        waitCallback = null;
        waitTimer = -1;
        if (callback != null) {
            callback.run();
        }
    }

    private void setWait(int frames, Runnable callback) {
        waitTimer = frames;
        waitCallback = callback;
    }

    private void updateWaterLevel() {
        try {
            waterLevelY = services().waterSystem().getWaterLevelY(
                    services().featureZoneId(), services().featureActId());
        } catch (Exception e) {
            waterLevelY = anchorY + 0x100;
        }
    }

    private void updateWaterCrossing(int previousY, int currentY) {
        if (crossedWaterThisPass) {
            return;
        }
        if (previousY < waterLevelY && currentY >= waterLevelY) {
            triggerWaterSplash();
        }
    }

    /**
     * sub_488E4: During ascent, check if boss is at/below water level and trigger splash once.
     * In the disasm this fires while still underwater; bit 7 prevents repeat.
     */
    private void checkWaterSplashDuringAscent() {
        if (crossedWaterThisPass) {
            return;
        }
        if (state.y >= waterLevelY) {
            triggerWaterSplash();
        }
    }

    /**
     * sub_488FA: Creates splash child at the BOSS X position, not the player's.
     * Uses the player sprite only to obtain the shared dust/splash renderer.
     */
    private void triggerWaterSplash() {
        crossedWaterThisPass = true;
        services().playSfx(Sonic3kSfx.SPLASH.id);
        PlayableEntity focused = services().camera().getFocusedSprite();
        if (focused instanceof AbstractPlayableSprite aps) {
            var dustController = aps.getSpindashDustController();
            if (dustController != null && dustController.getRenderer() != null) {
                spawnDynamicObject(new SplashObjectInstance(
                        state.x, waterLevelY, dustController.getRenderer(), false));
            }
        }
    }

    private void applyVortexPull(AbstractPlayableSprite player) {
        if (player != null) {
            applyVortexPullTo(player);
        }
        for (PlayableEntity entity : services().sidekicks()) {
            if (entity instanceof AbstractPlayableSprite sidekick) {
                applyVortexPullTo(sidekick);
            }
        }
    }

    /**
     * sub_487FC + sub_48844 + sub_48874: Full vortex interaction per player.
     * First contact: sets player airborne, under object control, tumble animation.
     * Each frame: accelerates player toward vortex X center, nudges Y toward center.
     */
    private void applyVortexPullTo(AbstractPlayableSprite sprite) {
        if (sprite.getDead()) {
            return;
        }
        int vortexY = getWaterEffectY();
        int checkY = vortexY - 0x20;
        if (sprite.getY() < checkY) {
            return;
        }

        if (!sprite.isObjectControlled()) {
            sprite.setObjectControlled(true);
            sprite.setForcedAnimationId(Sonic3kAnimationIds.FLOAT2.id());
            sprite.setXSpeed((short) 0);
            sprite.setYSpeed((short) 0);
        }

        int vortexX = getWaterEffectX();
        int playerX = sprite.getCentreX();
        int xDist = playerX - vortexX;
        boolean playerLeft = xDist < 0;
        if (playerLeft) xDist = -xDist;

        int xAccel = 0x40;
        short xVel = sprite.getXSpeed();

        if (xDist <= 3) {
            if (xVel < 0) {
                xAccel = -xAccel;
            }
        } else {
            if (xDist > 0x70) {
                xVel = 0;
            }
            if (!playerLeft) {
                xAccel = -xAccel;
            }
        }

        xVel = (short) (xVel + xAccel);
        sprite.setXSpeed(xVel);
        sprite.shiftX(xVel >> 8);

        int yDist = sprite.getY() - vortexY;
        if (yDist < -0x10) {
            sprite.setY((short) (sprite.getY() + 1));
        } else if (yDist > 0x10) {
            sprite.setY((short) (sprite.getY() - 1));
        }
    }

    /**
     * sub_489BA: Fold phase into 0-$7F range and look up signed offset from
     * HCZMiniboss_RocketTwistLookup. Used for BOTH X and Y orbit offsets.
     */
    private static int rocketTwistOffset(int phase) {
        phase &= 0xFF;
        if (phase >= 0x80) {
            phase = 0xFF - phase;
        }
        return ROCKET_TWIST_LOOKUP[phase];
    }

    private void followFloorCurve() {
        TerrainCheckResult floor = ObjectTerrainUtils.checkFloorDist(state.x, state.y, FLOOR_CHECK_RADIUS);
        if (!floor.foundSurface()) {
            return;
        }
        setBossPosition(state.x, state.y + floor.distance());
    }

    /**
     * Rocket wind-down sequence: matches the disasm's rocket routine $C → 8 chain.
     * Rockets orbit to home at full speed, then decelerate through speed 2 → 1 → 0.
     * Called when the boss closes for the vortex (bit 3 cleared / startVortexWindup).
     */
    private void beginRocketWindDown() {
        rocketOrbitSpeed = ROCKET_PHASE_STEP;
        setRocketSpeedTimer(0x1F, () -> {
            rocketOrbitSpeed = 2;
            setRocketSpeedTimer(0x1F, () -> {
                rocketOrbitSpeed = 1;
                setRocketSpeedTimer(0x1F, () -> {
                    rocketOrbitSpeed = 0;
                });
            });
        });
    }

    /**
     * Rocket wind-up sequence: matches the disasm's rocket routine 2 → 4 → 8 → $A chain.
     * Rockets start slow (speed 1), pause, then ramp to full speed (4).
     * Called when the boss reopens after the vortex (bit 3 set / startPostVortexPause).
     */
    private void beginRocketWindUp() {
        rocketOrbitSpeed = 1;
        setRocketSpeedTimer(0x3F, () -> {
            rocketOrbitSpeed = 2;
            setRocketSpeedTimer(0x3F, () -> {
                rocketOrbitSpeed = ROCKET_PHASE_STEP;
                rocketsArmed = true;
            });
        });
    }

    private void setRocketSpeedTimer(int frames, Runnable callback) {
        rocketSpeedTimer = frames;
        rocketSpeedCallback = callback;
    }

    private void tickRocketSpeedTimer() {
        if (rocketSpeedTimer < 0) {
            return;
        }
        rocketSpeedTimer--;
        if (rocketSpeedTimer >= 0) {
            return;
        }
        Runnable callback = rocketSpeedCallback;
        rocketSpeedCallback = null;
        rocketSpeedTimer = -1;
        if (callback != null) {
            callback.run();
        }
    }

    /**
     * sub_4895E: Update rocket orbit using HCZMiniboss_RocketTwistLookup.
     * Both X and Y offsets use the SAME fold+lookup function (sub_489BA),
     * applied to phaseX and phaseY respectively. This creates the distinctive
     * "figure-8 twist" orbit where rockets cross in front of the boss.
     */
    private void updateRocketOrbit() {
        tickRocketSpeedTimer();
        int phaseStep = rocketOrbitSpeed;
        int engineIndex = 0;
        RocketState[] rocketStates = rockets();
        for (int i = 0; i < rocketStates.length; i++) {
            RocketState rocket = rocketStates[i];
            rocket.phaseX = (rocket.phaseX + phaseStep) & 0xFF;
            rocket.phaseY = (rocket.phaseY + phaseStep) & 0xFF;
            int offsetX = rocketTwistOffset(rocket.phaseX);
            int offsetY = rocketTwistOffset(rocket.phaseY);
            rocket.x = state.x + offsetX;
            rocket.y = state.y + offsetY;
            int frameIndex = (rocket.phaseY >> 4) & 0x0F;
            rocket.frame = ROCKET_FRAMES[frameIndex];
            rocket.front = frameIndex < 8;
            if (i == 0) {
                engineIndex = (rocket.phaseY >> 3) & 0x0F;
            }
        }
        state.routineSecondary = engineIndex;
    }

    private void updateWaterEffect(int frameCounter) {
        if (!isWaterEffectVisible()) {
            waterEffectFrame = WATER_EFFECT_BASE_FRAME;
            return;
        }
        if (!vortexActive) {
            waterEffectFrame = WATER_EFFECT_BASE_FRAME;
            return;
        }
        waterEffectFrame = WATER_EFFECT_BASE_FRAME + ((frameCounter >> 1) % 3);
    }

    /**
     * ChildObjDat_48BD6: Spawns $1E (30) vortex bubble particles at once.
     * Each gets ROM-accurate random spread and vortex pull physics.
     */
    private void spawnVortexBubbleBatch() {
        int vortexCentreX = getWaterEffectX();
        int vortexCentreY = getWaterEffectY();
        vortexBubbles.clear();
        for (int i = 0; i < 0x1E; i++) {
            var rng = ThreadLocalRandom.current();
            int bubbleX = vortexCentreX + (byte) rng.nextInt(256);
            int bubbleY = vortexCentreY + (rng.nextInt(64) - 8);
            int bubbleFrame = rng.nextInt(4);
            VortexBubbleChild bubble = spawnChild(() -> new VortexBubbleChild(
                    bubbleX, bubbleY, bubbleFrame, vortexCentreX, vortexCentreY));
            if (bubble != null) {
                vortexBubbles.add(bubble);
            }
        }
    }

    /**
     * Vortex bubble particle — swirls toward the vortex centre using the same
     * pull physics as the player (sub_48874). Lives for the entire vortex duration:
     * Phase 1 ($1F frames): animated pull. Phase 2: static pull until vortex ends.
     * Phase 3 ($1F frames): dying pull, then delete.
     */
    private static final class VortexBubbleChild extends com.openggf.level.objects.AbstractObjectInstance {
        private static final int PHASE_TIMER = 0x1F;
        private static final int PHASE_PULL = 0;
        private static final int PHASE_HOLD = 1;
        private static final int PHASE_DYING = 2;
        private final int vortexX;
        private final int vortexY;
        private final int frame;
        private int phase;
        private int timer;
        private short xVel;
        private boolean vortexEnded;

        VortexBubbleChild(int x, int y, int frame, int vortexX, int vortexY) {
            super(new ObjectSpawn(x, y, 0, 0, 0, false, 0), "VortexBubble");
            this.vortexX = vortexX;
            this.vortexY = vortexY;
            this.frame = frame;
            this.phase = PHASE_PULL;
            this.timer = PHASE_TIMER;
            this.xVel = 0;
        }

        void signalVortexEnd() {
            vortexEnded = true;
        }

        @Override
        public void update(int frameCounter, com.openggf.game.PlayableEntity player) {
            applyVortexPull();
            switch (phase) {
                case PHASE_PULL -> {
                    timer--;
                    if (timer < 0) {
                        phase = PHASE_HOLD;
                    }
                }
                case PHASE_HOLD -> {
                    if (vortexEnded) {
                        phase = PHASE_DYING;
                        timer = PHASE_TIMER;
                    }
                }
                case PHASE_DYING -> {
                    timer--;
                    if (timer < 0) {
                        setDestroyed(true);
                    }
                }
            }
        }

        /**
         * sub_487EE → sub_48874: Same pull as player — accelerate toward vortex X,
         * nudge toward vortex Y.
         */
        private void applyVortexPull() {
            int curX = getSpawn().x();
            int curY = getSpawn().y();
            int xDist = curX - vortexX;
            boolean left = xDist < 0;
            if (left) xDist = -xDist;

            int xAccel = 0x40;
            if (xDist <= 3) {
                if (xVel < 0) xAccel = -xAccel;
            } else {
                if (xDist > 0x70) xVel = 0;
                if (!left) xAccel = -xAccel;
            }
            xVel = (short) (xVel + xAccel);
            curX += xVel >> 8;

            int yDist = curY - vortexY;
            if (yDist < -0x10) {
                curY++;
            } else if (yDist > 0x10) {
                curY--;
            }

            updateDynamicSpawn(curX, curY);
        }

        @Override
        public void appendRenderCommands(java.util.List<GLCommand> commands) {
            PatternSpriteRenderer renderer = getRenderer(
                    Sonic3kObjectArtKeys.BUBBLER);
            if (renderer != null) {
                renderer.drawFrameIndex(frame, getSpawn().x(), getSpawn().y(), false, false);
            }
        }

        @Override
        public boolean isPersistent() {
            return false;
        }
    }

    private void resetRocketPhases() {
        RocketState[] rocketStates = rockets();
        rocketStates[0].phaseX = 0x00;
        rocketStates[0].phaseY = 0x00;
        rocketStates[1].phaseX = 0x80;
        rocketStates[1].phaseY = 0x80;
        rocketStates[2].phaseX = 0x80;
        rocketStates[2].phaseY = 0x00;
        rocketStates[3].phaseX = 0x00;
        rocketStates[3].phaseY = 0x80;
        updateRocketOrbit();
    }

    private RocketState[] rockets() {
        ensureRocketState();
        return rockets;
    }

    private void ensureRocketState() {
        if (rockets != null) {
            return;
        }
        rockets = new RocketState[] {
                new RocketState(0x00, 0x00, false),
                new RocketState(0x80, 0x80, false),
                new RocketState(0x80, 0x00, true),
                new RocketState(0x00, 0x80, true)
        };
    }

    private int getCoreCollisionFlags() {
        if (!isFightVisible() || state.invulnerable || state.defeated) {
            return 0;
        }
        return CORE_COLLISION_FLAGS;
    }

    private int getRocketCollisionFlags() {
        if (!isFightVisible() || state.defeated || !rocketsArmed) {
            return 0;
        }
        return ROCKET_COLLISION_FLAGS;
    }

    private boolean isFightVisible() {
        return state.routine >= ROUTINE_DESCEND && !defeatRenderComplete;
    }

    private boolean isWaterEffectVisible() {
        if (state.routine == ROUTINE_DEFEATED || defeatRenderComplete) {
            return false;
        }
        return state.routine >= ROUTINE_WAIT_TRIGGER || isCameraInTriggerWindow();
    }

    private boolean isCameraInTriggerWindow() {
        var camera = services().camera();
        int cameraX = camera.getX();
        int cameraY = camera.getY();
        return cameraX >= TRIGGER_MIN_X && cameraX <= TRIGGER_MAX_X
                && cameraY >= TRIGGER_MIN_Y && cameraY <= TRIGGER_MAX_Y;
    }

    private void setBossPosition(int x, int y) {
        state.x = x;
        state.y = y;
        state.xFixed = x << 16;
        state.yFixed = y << 16;
    }

    private void setFacingLeft(boolean facingLeft) {
        if (facingLeft) {
            state.renderFlags |= 1;
        } else {
            state.renderFlags &= ~1;
        }
    }

    private void updateCustomFlash() {
        if (state.defeated) {
            return;
        }
        if (!state.invulnerable) {
            if (customFlashDirty) {
                loadBossPalette();
                customFlashDirty = false;
            }
            return;
        }

        var level = services().currentLevel();
        if (level == null || level.getPaletteCount() <= 1) {
            return;
        }

        Palette palette = level.getPalette(1);
        int[] colors = ((state.invulnerabilityTimer & 1) != 0) ? FLASH_DARK : FLASH_BRIGHT;
        for (int i = 0; i < FLASH_INDICES.length; i++) {
            byte[] bytes = {
                    (byte) ((colors[i] >> 8) & 0xFF),
                    (byte) (colors[i] & 0xFF)
            };
            palette.getColor(FLASH_INDICES[i]).fromSegaFormat(bytes, 0);
        }
        customFlashDirty = true;
        var graphics = services().graphicsManager();
        if (graphics.isGlInitialized()) {
            graphics.cachePaletteTexture(palette, 1);
        }
    }

    private void loadBossPalette() {
        try {
            byte[] line = services().rom().readBytes(Sonic3kConstants.PAL_HCZ_MINIBOSS_ADDR, 32);
            services().updatePalette(1, line);
        } catch (Exception e) {
            LOG.fine(() -> "HczMinibossInstance.loadBossPalette: " + e.getMessage());
        }
    }

    /**
     * loc_483A0: Loads Pal_HCZMinibossWater directly into Water_palette_line_2
     * (underwater palette line 1). Done once when the water column becomes visible.
     */
    private void ensureWaterEffectPalette() {
        if (waterPaletteLoaded || !isWaterEffectVisible()) {
            return;
        }
        try {
            byte[] line = services().rom().readBytes(Sonic3kConstants.PAL_HCZ_MINIBOSS_WATER_ADDR, 32);
            var waterSystem = services().waterSystem();
            var graphics = services().graphicsManager();
            var level = services().currentLevel();
            Palette[] uwPalettes = waterSystem.getUnderwaterPalette(
                    services().featureZoneId(), services().featureActId());
            if (uwPalettes != null && uwPalettes.length > 1 && graphics != null && level != null) {
                Palette bossPal = new Palette();
                bossPal.fromSegaFormat(line);
                uwPalettes[1] = bossPal;
                graphics.cacheUnderwaterPaletteTexture(uwPalettes, level.getPalette(0));
            }
            waterPaletteLoaded = true;
        } catch (Exception e) {
            LOG.fine(() -> "HczMinibossInstance.ensureWaterEffectPalette: " + e.getMessage());
        }
    }

    private Palette[] clonePalettes(Palette[] source) {
        Palette[] copy = new Palette[source.length];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i] != null ? source[i].deepCopy() : null;
        }
        return copy;
    }

    private int getEngineChildFrame(RocketState rocket) {
        return ENGINE_FRAMES[(rocket.phaseY >> 4) & 0x0F];
    }

    private int getEngineChildOffsetX(RocketState rocket) {
        int index = (rocket.phaseY >> 3) & 0x1E;
        int offset = ENGINE_CHILD_OFFSETS[index];
        return rocket.hFlip ? -offset : offset;
    }

    private int getEngineChildOffsetY(RocketState rocket) {
        int index = (rocket.phaseY >> 3) & 0x1E;
        return ENGINE_CHILD_OFFSETS[index + 1];
    }

    private boolean isEngineChildFront(RocketState rocket) {
        int index = (rocket.phaseY >> 4) & 0x0F;
        return ENGINE_CHILD_PRIORITIES[index] >= 0x280;
    }

    private int getWaterEffectX() {
        return spawn.x();
    }

    private int getWaterEffectY() {
        return spawn.y() + WATER_EFFECT_OFFSET_Y;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.HCZ_MINIBOSS);
        if (renderer == null) {
            return;
        }

        if (isWaterEffectVisible()) {
            renderer.drawFrameIndex(waterEffectFrame, getWaterEffectX(), getWaterEffectY(), false, false);
        }
        if (!isFightVisible()) {
            return;
        }

        // Rockets orbit the boss with a front/back split matching VDP priority:
        // Back rockets (priority $200, phaseY index < 8) drawn BEHIND boss body.
        // Front rockets (priority $280, phaseY index >= 8) drawn IN FRONT of boss body.
        boolean showRocketExhaust = rocketsArmed && (lastFrameCounter & 1) == 0;
        for (RocketState rocket : rockets()) {
            if (!rocket.front) {
                if (showRocketExhaust) {
                    renderer.drawFrameIndex(
                            getEngineChildFrame(rocket),
                            rocket.x + getEngineChildOffsetX(rocket),
                            rocket.y + getEngineChildOffsetY(rocket),
                            rocket.hFlip, false, 0);
                }
                renderer.drawFrameIndex(rocket.frame, rocket.x, rocket.y, rocket.hFlip, false);
            }
        }
        renderer.drawFrameIndex(0, state.x, state.y, false, false);
        if (!closedBody) {
            renderer.drawFrameIndex(ENGINE_FRAME, state.x, state.y + ENGINE_OFFSET_Y, false, false, 0);
        }
        for (RocketState rocket : rockets()) {
            if (rocket.front) {
                if (showRocketExhaust) {
                    renderer.drawFrameIndex(
                            getEngineChildFrame(rocket),
                            rocket.x + getEngineChildOffsetX(rocket),
                            rocket.y + getEngineChildOffsetY(rocket),
                            rocket.hFlip, false, 0);
                }
                renderer.drawFrameIndex(rocket.frame, rocket.x, rocket.y, rocket.hFlip, false);
            }
        }
    }

    @Override
    public boolean isPersistent() {
        return !isDestroyed();
    }

    @Override
    public boolean isHighPriority() {
        return true;
    }

    @Override
    public int getPriorityBucket() {
        return 5;
    }

    @Override
    protected int getBossHitSfxId() {
        return Sonic3kSfx.BOSS_HIT.id;
    }

    @Override
    protected int getBossExplosionSfxId() {
        return Sonic3kSfx.EXPLODE.id;
    }
}
