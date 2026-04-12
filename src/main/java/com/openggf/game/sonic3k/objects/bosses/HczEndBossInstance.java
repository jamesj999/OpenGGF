package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.events.Sonic3kHCZEvents;
import com.openggf.game.sonic3k.objects.Aiz2EndEggCapsuleInstance;
import com.openggf.game.sonic3k.objects.S3kBossDefeatSignpostFlow;
import com.openggf.game.sonic3k.objects.S3kBossExplosionChild;
import com.openggf.game.sonic3k.objects.S3kBossExplosionController;
import com.openggf.graphics.GLCommand;
import com.openggf.level.Palette;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.objects.boss.AbstractBossInstance;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.SwingMotion;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.logging.Logger;

/**
 * Hydrocity Zone Act 2 end boss (object 0x9A).
 *
 * <p>ROM: Obj_HCZEndBoss (sonic3k.asm). A propeller-driven machine that
 * descends from above, patrols horizontally with spinning blades, and has
 * a water column suction attack. Defeated after 8 hits.
 *
 * <p>This shell provides the state machine skeleton, camera lock mechanism,
 * collision, rendering, palette flash, and timer/callback utilities. Movement
 * routines and defeat logic are stubbed for later tasks.
 *
 * <p>Child objects (turbine, blades, visual child) are spawned in later tasks.
 */
public class HczEndBossInstance extends AbstractBossInstance {
    private static final Logger LOG = Logger.getLogger(HczEndBossInstance.class.getName());

    // =========================================================================
    // State machine routines (ROM: routine counter values, stride 2)
    // =========================================================================
    private static final int ROUTINE_INIT = 0;
    private static final int ROUTINE_DESCEND = 2;
    private static final int ROUTINE_HOVER_WAIT = 4;
    private static final int ROUTINE_ST_DESCEND = 6;
    private static final int ROUTINE_ST_RISE = 8;
    private static final int ROUTINE_PRE_ATTACK = 10;
    private static final int ROUTINE_ATTACK = 12;
    private static final int ROUTINE_DEFEATED = 14;
    private static final int ROUTINE_FLEE = 16;
    private static final int ROUTINE_CAPSULE_WAIT = 18;
    private static final int ROUTINE_DONE = 20;

    // =========================================================================
    // Boss attributes
    // =========================================================================
    private static final int HIT_COUNT = 8;
    private static final int COLLISION_SIZE = 6;
    private static final int INVULN_TIME = 0x20;
    private static final int BOSS_MUSIC_WAIT = 120; // frames before boss music plays

    // =========================================================================
    // Arena bounds — Sonic/Tails (ROM: word_6AE96)
    // =========================================================================
    private static final int ST_TRIGGER_MIN_X = 0x3F00;
    private static final int ST_TRIGGER_MAX_X = 0x4100;
    private static final int ST_TRIGGER_MIN_Y = 0x0438;
    private static final int ST_TRIGGER_MAX_Y = 0x0838;
    private static final int ST_LOCK_Y_TOP = 0x0738;
    private static final int ST_LOCK_Y_BOTTOM = 0x0738;
    private static final int ST_LOCK_X_LEFT = 0x4000;
    private static final int ST_LOCK_X_RIGHT = 0x4050;

    // =========================================================================
    // Arena bounds — Knuckles (ROM: word_6AEA6)
    // =========================================================================
    private static final int KN_TRIGGER_MIN_X = 0x44E0;
    private static final int KN_TRIGGER_MAX_X = 0x4640;
    private static final int KN_TRIGGER_MIN_Y = 0x0000;
    private static final int KN_TRIGGER_MAX_Y = 0x03B8;
    private static final int KN_LOCK_Y_TOP = 0x02B8;
    private static final int KN_LOCK_Y_BOTTOM = 0x02B8;
    private static final int KN_LOCK_X_LEFT = 0x4540;
    private static final int KN_LOCK_X_RIGHT = 0x4590;

    // =========================================================================
    // Palette flash constants (ROM: word_6BC30 / word_6BC36)
    // =========================================================================
    /** Palette indices in line 1 to flash during invulnerability. */
    private static final int[] FLASH_INDICES = {0x0A, 0x0B, 0x0E};
    /** Normal colors for the flashed indices: 0x006, 0x020, 0x624. */
    private static final int[] FLASH_NORMAL = {0x006, 0x020, 0x624};
    /** Flash color for all indices: 0xEEE (white). */
    private static final int FLASH_WHITE = 0xEEE;

    // =========================================================================
    // Swing/movement constants (ROM: loc_6AF80, loc_6B064)
    // =========================================================================
    /** Per-frame swing acceleration (ROM: $40(a0)). */
    private static final int SWING_ACCEL = 0x10;
    /** Peak swing velocity magnitude (ROM: $3E(a0)). */
    private static final int SWING_AMPLITUDE = 0xC0;

    // =========================================================================
    // Child offsets (ROM: ChildObjDat_6BD8A)
    // =========================================================================
    /** Robotnik ship cockpit offset from boss center. */
    private static final int ROBOTNIK_OFFSET_X = 0;
    private static final int ROBOTNIK_OFFSET_Y = 0x0C;
    /** Visual child (propeller housing) offset from boss center. */
    private static final int VISUAL_OFFSET_X = 0;
    private static final int VISUAL_OFFSET_Y = 0x1C;
    /** Turbine offset from boss center. */
    private static final int TURBINE_OFFSET_X = 0;
    private static final int TURBINE_OFFSET_Y = 0x24;

    // =========================================================================
    // Communication flags (replaces ROM's $38 bit field)
    // =========================================================================
    private boolean propellerActive;
    private boolean bladeFireSignal;
    private boolean defeatSignal;

    // =========================================================================
    // Instance state
    // =========================================================================
    private int waitTimer = -1;
    private Runnable waitCallback;
    private boolean arenaYLocked;
    private boolean arenaXLocked;
    private boolean customFlashDirty;
    private boolean cameraLockComplete;
    private int musicWaitTimer = -1;

    // Movement state (Task 6)
    private int swingVelocity;
    private boolean swingDown;
    private int hoverCentreY;
    private int savedPatrolVel = -0x100;
    private int directionCounter;
    private int attackPassCounter;
    private boolean facingRight;

    /** Stored camera bounds at time of boss trigger, for gradual lock. */
    private int storedMinY;
    private int storedMaxY;
    private int storedMinX;
    private int storedMaxX;

    /** Character-resolved lock targets for the current fight. */
    private int targetLockYTop;
    private int targetLockYBottom;
    private int targetLockXLeft;
    private int targetLockXRight;

    private S3kBossExplosionController defeatExplosionController;

    // =========================================================================
    // Constructor
    // =========================================================================

    public HczEndBossInstance(ObjectSpawn spawn) {
        super(spawn, "HCZEndBoss");
    }

    // =========================================================================
    // AbstractBossInstance contract
    // =========================================================================

    @Override
    protected void initializeBossState() {
        state.routine = ROUTINE_INIT;
        state.hitCount = HIT_COUNT;
        state.renderFlags = 0;
        waitTimer = -1;
        waitCallback = null;
        arenaYLocked = false;
        arenaXLocked = false;
        customFlashDirty = false;
        cameraLockComplete = false;
        musicWaitTimer = -1;
        propellerActive = false;
        bladeFireSignal = false;
        defeatSignal = false;
        defeatExplosionController = null;
        swingVelocity = 0;
        swingDown = false;
        hoverCentreY = 0;
        savedPatrolVel = -0x100;
        directionCounter = 0;
        attackPassCounter = 0;
        facingRight = false;

        // Resolve character-specific lock targets
        boolean isKnuckles = (getPlayerCharacter() == PlayerCharacter.KNUCKLES);
        targetLockYTop = isKnuckles ? KN_LOCK_Y_TOP : ST_LOCK_Y_TOP;
        targetLockYBottom = isKnuckles ? KN_LOCK_Y_BOTTOM : ST_LOCK_Y_BOTTOM;
        targetLockXLeft = isKnuckles ? KN_LOCK_X_LEFT : ST_LOCK_X_LEFT;
        targetLockXRight = isKnuckles ? KN_LOCK_X_RIGHT : ST_LOCK_X_RIGHT;
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
        return false; // Custom defeat logic (Task 7)
    }

    @Override
    protected int getInvulnerabilityDuration() {
        return INVULN_TIME;
    }

    @Override
    protected int getPaletteLineForFlash() {
        return -1; // Custom flash, not the generic base class flash
    }

    @Override
    public int getCollisionFlags() {
        // ROM: collision_flags = $C0 | 6 when active, 0 when invulnerable/defeated
        if (state.routine < ROUTINE_DESCEND || state.invulnerable || state.defeated) {
            return 0;
        }
        return 0xC0 | COLLISION_SIZE;
    }

    @Override
    protected int getBossHitSfxId() {
        return Sonic3kSfx.BOSS_HIT.id;
    }

    @Override
    protected int getBossExplosionSfxId() {
        return Sonic3kSfx.EXPLODE.id;
    }

    // =========================================================================
    // Custom hit processing (overrides base class)
    // =========================================================================

    @Override
    public void onPlayerAttack(PlayableEntity playerEntity, TouchResponseResult result) {
        if (getCollisionFlags() == 0 || state.invulnerable || state.defeated) {
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

    // =========================================================================
    // Main update loop
    // =========================================================================

    @Override
    protected void updateBossLogic(int frameCounter, PlayableEntity playerEntity) {
        switch (state.routine) {
            case ROUTINE_INIT -> updateInit();
            case ROUTINE_DESCEND -> updateMoveAndWait();
            case ROUTINE_HOVER_WAIT -> updateSwingAndWait();
            case ROUTINE_ST_DESCEND -> updateMoveAndWait();
            case ROUTINE_ST_RISE -> updateMoveAndWait();
            case ROUTINE_PRE_ATTACK -> updateSwingAndWait();
            case ROUTINE_ATTACK -> updateAttackPatrol();
            case ROUTINE_DEFEATED -> updateDefeated();
            case ROUTINE_FLEE -> updateFlee();
            case ROUTINE_CAPSULE_WAIT -> updateCapsuleWait();
            case ROUTINE_DONE -> { /* terminal */ }
            default -> { }
        }

        updateCustomFlash();
        updateDynamicSpawn(state.x, state.y);
    }

    // =========================================================================
    // Routine: INIT (0)
    // =========================================================================

    /**
     * ROM: sub_85D6A — Check if camera is in the trigger zone for the current
     * character. If not, delete this object (same pattern as HCZ miniboss).
     * If yes, set Boss_flag=1 on the HCZ events, store current camera bounds,
     * fade music, and begin camera lock sequence.
     */
    private void updateInit() {
        if (!isCameraInRange()) {
            return; // Not yet in range; stay in INIT
        }

        // Set Boss_flag on HCZ events
        setBossFlag(true);

        // Store current camera bounds before locking
        var camera = services().camera();
        storedMinY = camera.getMinY();
        storedMaxY = camera.getMaxY();
        storedMinX = camera.getMinX();
        storedMaxX = camera.getMaxX();

        // Fade out current music
        services().fadeOutMusic();

        // Load boss palette to palette line 1
        loadBossPalette();

        // Wait 120 frames, then play boss music
        musicWaitTimer = BOSS_MUSIC_WAIT;

        // Begin camera lock — Y first, then X
        state.routine = ROUTINE_DESCEND;
        state.yVel = 0;
        state.xVel = 0;

        // Start gradual camera lock (Y boundaries first)
        arenaYLocked = false;
        arenaXLocked = false;
        cameraLockComplete = false;

        LOG.info("HCZ End Boss: triggered, starting camera lock");
    }

    // =========================================================================
    // Camera lock mechanism (ROM: sub_85D6A + loc_85CA4)
    // =========================================================================

    /**
     * Gradually locks camera boundaries toward the target values.
     * ROM: Increments/decrements by 2 per frame until target is reached.
     * Y boundaries are locked first, then X boundaries.
     */
    private void updateCameraLock() {
        if (cameraLockComplete) {
            return;
        }

        // Music wait timer: play boss music after delay
        if (musicWaitTimer >= 0) {
            musicWaitTimer--;
            if (musicWaitTimer < 0) {
                services().playMusic(Sonic3kMusic.BOSS.id);
                services().gameState().setCurrentBossId(Sonic3kObjectIds.HCZ_END_BOSS);
            }
        }

        var camera = services().camera();

        // Phase 1: Lock Y boundaries
        if (!arenaYLocked) {
            boolean yDone = true;

            int currentMinY = camera.getMinY();
            if (currentMinY < targetLockYTop) {
                camera.setMinY((short) Math.min(currentMinY + 2, targetLockYTop));
                yDone = false;
            } else if (currentMinY > targetLockYTop) {
                camera.setMinY((short) Math.max(currentMinY - 2, targetLockYTop));
                yDone = false;
            }

            int currentMaxY = camera.getMaxY();
            if (currentMaxY < targetLockYBottom) {
                camera.setMaxY((short) Math.min(currentMaxY + 2, targetLockYBottom));
                yDone = false;
            } else if (currentMaxY > targetLockYBottom) {
                camera.setMaxY((short) Math.max(currentMaxY - 2, targetLockYBottom));
                yDone = false;
            }

            if (yDone) {
                arenaYLocked = true;
            }
            return; // Don't start X lock until Y is done
        }

        // Phase 2: Lock X boundaries
        if (!arenaXLocked) {
            boolean xDone = true;

            int currentMinX = camera.getMinX();
            if (currentMinX < targetLockXLeft) {
                camera.setMinX((short) Math.min(currentMinX + 2, targetLockXLeft));
                xDone = false;
            } else if (currentMinX > targetLockXLeft) {
                camera.setMinX((short) Math.max(currentMinX - 2, targetLockXLeft));
                xDone = false;
            }

            int currentMaxX = camera.getMaxX();
            if (currentMaxX < targetLockXRight) {
                camera.setMaxX((short) Math.min(currentMaxX + 2, targetLockXRight));
                xDone = false;
            } else if (currentMaxX > targetLockXRight) {
                camera.setMaxX((short) Math.max(currentMaxX - 2, targetLockXRight));
                xDone = false;
            }

            if (xDone) {
                arenaXLocked = true;
                cameraLockComplete = true;
                onCameraLockComplete();
            }
        }
    }

    /**
     * Called when camera lock is fully established.
     * Spawns children and begins descent into the arena.
     * ROM: sets y_vel = 0x100 (descend) with wait timer for descent duration.
     */
    private void onCameraLockComplete() {
        LOG.info("HCZ End Boss: camera lock complete, spawning children");
        spawnChildren();
        // Begin descent into arena
        state.routine = ROUTINE_DESCEND;
        state.yVel = 0x100;
        setWait(0x7F, this::onDescentComplete);
    }

    // =========================================================================
    // Movement routines (ROM: loc_6AF80–loc_6B0AE)
    // =========================================================================

    /**
     * Move + Wait pattern: applies velocity, ticks wait timer.
     * ROM: Used by DESCEND, ST_DESCEND, ST_RISE routines.
     */
    private void updateMoveAndWait() {
        updateCameraLock();
        state.applyVelocity();
        tickWait();
    }

    /**
     * Swing + Move + Wait pattern: hover oscillation with timer.
     * ROM: Used by HOVER_WAIT, PRE_ATTACK routines.
     * Applies Swing_UpAndDown each frame then moves + ticks wait.
     */
    private void updateSwingAndWait() {
        SwingMotion.Result result = SwingMotion.update(
                SWING_ACCEL, swingVelocity, SWING_AMPLITUDE, swingDown);
        swingVelocity = result.velocity();
        swingDown = result.directionDown();
        state.yVel = swingVelocity;

        state.applyVelocity();
        tickWait();
    }

    /**
     * ROM: loc_6AF80 — After initial descent, transition to hover phase.
     * Sets routine to HOVER_WAIT, saves hover center Y, initializes swing
     * parameters, and starts a 0x3F-frame wait before pre-attack.
     */
    private void onDescentComplete() {
        state.routine = ROUTINE_HOVER_WAIT;
        hoverCentreY = state.y;
        swingVelocity = SWING_AMPLITUDE;
        swingDown = false;
        state.yVel = 0;
        setWait(0x9F, () -> {
            setWait(0x3F, this::onPreAttackWaitComplete);
        });
    }

    /**
     * ROM: loc_6AFC8 — Propeller spin-up, then branch by character.
     * Sonic/Tails: slow descent then rise.
     * Knuckles: skip straight to pre-attack wait.
     */
    private void onPreAttackWaitComplete() {
        propellerActive = true;

        if (getPlayerCharacter() != PlayerCharacter.KNUCKLES) {
            // Sonic/Tails path
            state.routine = ROUTINE_ST_DESCEND;
            state.yVel = 0x80;
            setWait(0x9F, this::onStDescentComplete);
        } else {
            // Knuckles path — skip intermediate descent/rise
            state.routine = ROUTINE_PRE_ATTACK;
            setWait(0x1FF, this::beginAttackPhase);
        }
    }

    /**
     * ROM: loc_6B008 — Sonic/Tails: after slow descent, begin rising.
     */
    private void onStDescentComplete() {
        state.yVel = -0x100;
        state.routine = ROUTINE_ST_RISE;
        setWait(0x4F, this::onStRiseComplete);
    }

    /**
     * ROM: loc_6B01E — Sonic/Tails: after rise, snap back to hover center
     * and wait before attacking.
     */
    private void onStRiseComplete() {
        state.routine = ROUTINE_PRE_ATTACK;
        state.y = hoverCentreY;
        state.yFixed = hoverCentreY << 16;
        state.yVel = 0;
        setWait(0xFF, this::beginAttackPhase);
    }

    /**
     * ROM: loc_6B03A — Begin the attack patrol phase.
     * Clears propeller, sets horizontal velocity from savedPatrolVel,
     * initializes direction counter and attack pass counter.
     */
    private void beginAttackPhase() {
        state.routine = ROUTINE_ATTACK;
        propellerActive = false;
        state.xVel = savedPatrolVel;
        facingRight = (state.xVel > 0);
        directionCounter = 0x13F;
        attackPassCounter = 8;
        setWait(0xBF, this::onAttackPassComplete);
        // Re-init swing
        swingVelocity = SWING_AMPLITUDE;
        swingDown = false;
    }

    /**
     * ROM: loc_6B064 — Attack patrol: every frame apply swing oscillation,
     * move, tick wait, and check for direction reversal.
     */
    private void updateAttackPatrol() {
        // Swing oscillation
        SwingMotion.Result result = SwingMotion.update(
                SWING_ACCEL, swingVelocity, SWING_AMPLITUDE, swingDown);
        swingVelocity = result.velocity();
        swingDown = result.directionDown();
        state.yVel = swingVelocity;

        // Apply velocity (both X and Y)
        state.applyVelocity();

        // Tick wait timer
        tickWait();

        // Direction reversal counter
        directionCounter--;
        if (directionCounter < 0) {
            state.xVel = -state.xVel;
            facingRight = !facingRight;
            directionCounter = 0x13F;
        }
    }

    /**
     * ROM: loc_6B08E — One attack pass complete.
     * Decrements pass counter; if passes remain, fires blade and continues.
     * If all passes done, saves patrol velocity and returns to phase decision.
     */
    private void onAttackPassComplete() {
        attackPassCounter--;
        if (attackPassCounter >= 0) {
            // More passes remaining — fire blade and continue
            bladeFireSignal = true;
            setWait(0x5F, this::continueAttackPatrol);
        } else {
            // All passes done — save velocity, return to hover/pre-attack cycle
            savedPatrolVel = state.xVel;
            state.xVel = 0;
            setWait(0x5F, this::onPreAttackWaitComplete);
        }
    }

    /**
     * Continue attack patrol after blade fire pause.
     */
    private void continueAttackPatrol() {
        setWait(0xBF, this::onAttackPassComplete);
    }

    // =========================================================================
    // Defeat & flee constants (ROM: loc_6B0B0–loc_6B154)
    // =========================================================================
    private static final int DEFEAT_WAIT = 0x3F;       // 63 frames of explosions
    private static final int FADE_MUSIC_WAIT = 119;     // 0x77 frames before music resumes
    private static final int FLEE_X_VEL = 0x200;
    private static final int FLEE_Y_VEL = -0x200;
    private static final int FLEE_GRAVITY = 0x18;

    /** Egg capsule spawn positions — Sonic/Tails (ROM: loc_6B0E8). */
    private static final int ST_CAPSULE_X = 0x4250;
    private static final int ST_CAPSULE_Y = 0x7E0;
    /** Egg capsule spawn positions — Knuckles. */
    private static final int K_CAPSULE_X = 0x4760;
    private static final int K_CAPSULE_Y = 0x360;

    /** Camera expansion after flee complete (ROM: loc_6B0E8). */
    private static final int POST_FLEE_CAMERA_EXPAND = 0x180;

    private int fleeTimer;

    // =========================================================================
    // Defeat routines (ROM: BossDefeated → loc_6B154)
    // =========================================================================

    @Override
    protected void onDefeatStarted() {
        state.routine = ROUTINE_DEFEATED;
        state.xVel = 0;
        state.yVel = 0;
        waitTimer = -1;
        waitCallback = null;
        propellerActive = false;
        defeatSignal = true;
        state.invulnerable = false;
        state.invulnerabilityTimer = 0;
        loadBossPalette();
        defeatExplosionController = new S3kBossExplosionController(state.x, state.y, 0);
        services().fadeOutMusic();
        services().gameState().setCurrentBossId(0);
        // Wait DEFEAT_WAIT frames for explosions, then begin flee
        setWait(DEFEAT_WAIT, this::beginFleeSequence);
    }

    /**
     * ROM: post-BossDefeated_StopTimer — tick explosion controller each frame,
     * spawn visual explosion children, and tick the defeat wait timer.
     */
    private void updateDefeated() {
        if (defeatExplosionController != null) {
            defeatExplosionController.tick();
            for (var pending : defeatExplosionController.drainPendingExplosions()) {
                if (pending.playSfx()) {
                    services().playSfx(Sonic3kSfx.EXPLODE.id);
                }
                spawnChild(() -> new S3kBossExplosionChild(pending.x(), pending.y()));
            }
        }
        tickWait();
    }

    /**
     * ROM: loc_6B0B0 — Begin the flee sequence after defeat explosions.
     * Boss flies up and to one side with a fade music wait timer.
     */
    private void beginFleeSequence() {
        state.routine = ROUTINE_FLEE;
        // Flee away from the direction the boss is facing
        state.xVel = facingRight ? -FLEE_X_VEL : FLEE_X_VEL;
        state.yVel = FLEE_Y_VEL;
        fleeTimer = FADE_MUSIC_WAIT;
        LOG.fine("HCZ End Boss: flee sequence started");
    }

    /**
     * ROM: loc_6B0CC — Each frame during flee: apply gravity, move, flicker,
     * decrement timer. When timer expires, call onFleeComplete().
     */
    private void updateFlee() {
        state.yVel += FLEE_GRAVITY;
        state.applyVelocity();
        fleeTimer--;
        if (fleeTimer <= 0) {
            onFleeComplete();
        }
    }

    /**
     * ROM: loc_6B0E8 — Flee complete. Hide boss, clear boss flag, expand camera,
     * resume zone music, and spawn the egg capsule.
     */
    private void onFleeComplete() {
        state.routine = ROUTINE_CAPSULE_WAIT;

        // Clear boss flag
        setBossFlag(false);

        // Clear boss ID
        services().gameState().setCurrentBossId(0);

        // Expand camera rightward to allow player to proceed
        var camera = services().camera();
        camera.setMaxX((short) (targetLockXLeft + POST_FLEE_CAMERA_EXPAND));

        // Resume zone music
        services().playMusic(Sonic3kMusic.HCZ2.id);

        // Spawn Egg Capsule at character-specific position
        boolean isKnuckles = (getPlayerCharacter() == PlayerCharacter.KNUCKLES);
        int capsuleX = isKnuckles ? K_CAPSULE_X : ST_CAPSULE_X;
        int capsuleY = isKnuckles ? K_CAPSULE_Y : ST_CAPSULE_Y;
        spawnChild(() -> new Aiz2EndEggCapsuleInstance(capsuleX, capsuleY));
        LOG.fine("HCZ End Boss: flee complete, capsule spawned at (" + capsuleX + ", " + capsuleY + ")");
    }

    /**
     * ROM: loc_6B154 — Wait for the capsule to be opened and the end-of-level
     * sequence to begin. Lock camera from scrolling left each frame.
     */
    private void updateCapsuleWait() {
        // Lock camera from scrolling left
        var camera = services().camera();
        camera.setMinX((short) camera.getX());

        // Check if end-of-level has been signaled (capsule opened → results screen)
        if (services().gameState().isEndOfLevelActive()) {
            onCapsuleOpened();
        }
    }

    /**
     * ROM: after capsule opened — spawn geyser cutscene and transition to done state.
     *
     * <p>ROM: loc_6B154 → capsule open check → spawn geyser cutscene object.
     * Geyser X = Player_1 X position; geyser spawn Y = Camera_Y + $130.
     */
    private void onCapsuleOpened() {
        state.routine = ROUTINE_DONE;
        // Reset camera Y min to allow full vertical scrolling
        services().camera().setMinY((short) 0);

        // Resolve player X for geyser column (ROM: move.w Player_1+x_pos,d0)
        var camera = services().camera();
        AbstractPlayableSprite player =
                (services().camera().getFocusedSprite() instanceof AbstractPlayableSprite aps) ? aps : null;
        int geyserX   = (player != null) ? player.getCentreX() : state.x;
        int geyserY   = camera.getY() + 0x130;

        // Spawn geyser cutscene as a dynamic object (ROM: loc_6B7BC)
        spawnChild(() -> new HczEndBossGeyserCutscene(geyserX, geyserY));
        LOG.info("HCZ End Boss: capsule opened, geyser cutscene spawned at X="
                + geyserX + " Y=" + geyserY);
        setDestroyed(true);
    }

    // =========================================================================
    // Child spawning (placeholders for Tasks 3-5)
    // =========================================================================

    /**
     * Spawns all child objects for the boss.
     * ROM: ChildObjDat_6BD8A — spawns Robotnik ship, 3 blades, visual child, turbine.
     *
     * <p>Children will be spawned when their classes are created:
     * <ul>
     *   <li>Task 3: Propeller turbine child at (0, 0x24)</li>
     *   <li>Task 4: Blade children at (0x23, 0x12), (0x1B, 0x0A), (0x13, 0x0A)</li>
     *   <li>Task 5: Water column visual child</li>
     * </ul>
     */
    private void spawnChildren() {
        // Task 3: Propeller turbine at offset (0, 0x24)
        spawnChild(() -> new HczEndBossTurbine(this, TURBINE_OFFSET_X, TURBINE_OFFSET_Y));
        // Task 4: Blade children — ROM: ChildObjDat_6BD8A blade entries
        // Index 0 (bottom, fires): xOffset=0x23, yOffset=0x12
        // Index 1 (middle):        xOffset=0x1B, yOffset=0x0A
        // Index 2 (top):           xOffset=0x13, yOffset=0x0A
        spawnChild(() -> new HczEndBossBlade(this, 0, 0x23, 0x12));
        spawnChild(() -> new HczEndBossBlade(this, 1, 0x1B, 0x0A));
        spawnChild(() -> new HczEndBossBlade(this, 2, 0x13, 0x0A));
        // Task 5: Water column is spawned lazily by HczEndBossTurbine when it enters ACTIVE
        LOG.fine("HCZ End Boss: turbine + 3 blades spawned; water column spawned by turbine on activation");
    }

    // =========================================================================
    // Timer/callback utilities (matches HCZ miniboss pattern)
    // =========================================================================

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

    // =========================================================================
    // Custom palette flash (ROM: word_6BC30 / word_6BC36)
    // =========================================================================

    /**
     * Updates boss palette flash during invulnerability.
     * ROM: Alternates palette indices 0x0A, 0x0B, 0x0E in line 1 between
     * normal colors and white (0xEEE) each frame based on invulnerabilityTimer bit 0.
     */
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
        boolean useWhite = (state.invulnerabilityTimer & 1) == 0;

        for (int i = 0; i < FLASH_INDICES.length; i++) {
            int color = useWhite ? FLASH_WHITE : FLASH_NORMAL[i];
            byte[] bytes = {
                    (byte) ((color >> 8) & 0xFF),
                    (byte) (color & 0xFF)
            };
            palette.getColor(FLASH_INDICES[i]).fromSegaFormat(bytes, 0);
        }
        customFlashDirty = true;

        var graphics = services().graphicsManager();
        if (graphics.isGlInitialized()) {
            graphics.cachePaletteTexture(palette, 1);
        }
    }

    /**
     * Loads the boss palette from ROM into palette line 1.
     * ROM: PalLoad Pal_HCZEndBoss -> Normal_palette_line_2
     */
    private void loadBossPalette() {
        try {
            byte[] line = services().rom().readBytes(Sonic3kConstants.PAL_HCZ_END_BOSS_ADDR, 32);
            services().updatePalette(1, line);
        } catch (Exception e) {
            LOG.fine(() -> "HczEndBossInstance.loadBossPalette: " + e.getMessage());
        }
    }

    // =========================================================================
    // Camera range check
    // =========================================================================

    /**
     * ROM: Checks if camera position is within the trigger zone for the
     * current character (Sonic/Tails or Knuckles).
     */
    private boolean isCameraInRange() {
        var camera = services().camera();
        int cameraX = camera.getX();
        int cameraY = camera.getY();

        boolean isKnuckles = (getPlayerCharacter() == PlayerCharacter.KNUCKLES);
        int minX = isKnuckles ? KN_TRIGGER_MIN_X : ST_TRIGGER_MIN_X;
        int maxX = isKnuckles ? KN_TRIGGER_MAX_X : ST_TRIGGER_MAX_X;
        int minY = isKnuckles ? KN_TRIGGER_MIN_Y : ST_TRIGGER_MIN_Y;
        int maxY = isKnuckles ? KN_TRIGGER_MAX_Y : ST_TRIGGER_MAX_Y;

        return cameraX >= minX && cameraX <= maxX
                && cameraY >= minY && cameraY <= maxY;
    }

    // =========================================================================
    // Boss flag integration with HCZ events
    // =========================================================================

    /**
     * Sets the Boss_flag on the HCZ events handler.
     * ROM: Boss_flag gates FG events during boss fights.
     */
    private void setBossFlag(boolean value) {
        try {
            Object provider = services().levelEventProvider();
            if (provider instanceof Sonic3kLevelEventManager lem) {
                Sonic3kHCZEvents hczEvents = lem.getHczEvents();
                if (hczEvents != null) {
                    hczEvents.setBossFlag(value);
                }
            }
        } catch (Exception e) {
            LOG.fine(() -> "HczEndBossInstance.setBossFlag: " + e.getMessage());
        }
    }

    // =========================================================================
    // Player character detection (follows AizEndBossInstance pattern)
    // =========================================================================

    /**
     * Resolves the current player character from the level event manager.
     * Falls back to SONIC_AND_TAILS if unavailable.
     */
    PlayerCharacter getPlayerCharacter() {
        try {
            return ((Sonic3kLevelEventManager) services().levelEventProvider()).getPlayerCharacter();
        } catch (Exception e) {
            return PlayerCharacter.SONIC_AND_TAILS;
        }
    }

    // =========================================================================
    // Communication flag accessors (for child objects)
    // =========================================================================

    /** Whether the propeller turbine should be active (spinning up). */
    public boolean isPropellerActive() {
        return propellerActive;
    }

    public void setPropellerActive(boolean active) {
        this.propellerActive = active;
    }

    /** Whether the bottom blade should fire (detach and attack). */
    public boolean isBladeFireSignal() {
        return bladeFireSignal;
    }

    public void setBladeFireSignal(boolean signal) {
        this.bladeFireSignal = signal;
    }

    /** Called by blade child (index 0) after it has acknowledged the fire signal. */
    public void clearBladeFireSignal() {
        this.bladeFireSignal = false;
    }

    /**
     * Whether the boss is currently facing right.
     * ROM: tracks direction based on patrol velocity sign.
     */
    public boolean isFacingRight() {
        return facingRight;
    }

    /** Whether the boss is defeated (children should clean up). */
    public boolean isDefeatSignal() {
        return defeatSignal;
    }

    /**
     * Public wrapper around the protected {@code spawnChild} for use by child objects
     * that need to spawn their own grandchildren (e.g., the turbine spawning the water column).
     *
     * @param factory supplier that constructs the child object
     * @param <T>     the child type
     * @return the constructed object, already added to the object manager
     */
    public <T extends com.openggf.level.objects.AbstractObjectInstance> T spawnDynamicChild(
            java.util.function.Supplier<T> factory) {
        return spawnChild(factory);
    }

    // =========================================================================
    // Position helpers
    // =========================================================================

    private void setBossPosition(int x, int y) {
        state.x = x;
        state.y = y;
        state.xFixed = x << 16;
        state.yFixed = y << 16;
    }

    // =========================================================================
    // Rendering
    // =========================================================================

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (state.routine < ROUTINE_DESCEND && !state.defeated) {
            return; // Not yet visible
        }

        // During flee: flicker — only render on odd frames
        if (state.routine == ROUTINE_FLEE) {
            if ((fleeTimer & 1) == 0) {
                return;
            }
        }

        // After flee complete: don't render at all (boss has left the arena)
        if (state.routine >= ROUTINE_CAPSULE_WAIT) {
            return;
        }

        // Body frame 0
        PatternSpriteRenderer bossRenderer = getRenderer(Sonic3kObjectArtKeys.HCZ_END_BOSS);
        if (bossRenderer != null) {
            bossRenderer.drawFrameIndex(0, state.x, state.y, false, false);

            // Visual child (propeller housing) at offset (0, 0x1C) — frame 1
            bossRenderer.drawFrameIndex(1,
                    state.x + VISUAL_OFFSET_X, state.y + VISUAL_OFFSET_Y,
                    false, false);
        }

        // Robotnik ship cockpit at offset (0, 0x0C) — uses ROBOTNIK_SHIP art
        PatternSpriteRenderer robotnikRenderer = getRenderer(Sonic3kObjectArtKeys.ROBOTNIK_SHIP);
        if (robotnikRenderer != null) {
            robotnikRenderer.drawFrameIndex(0,
                    state.x + ROBOTNIK_OFFSET_X, state.y + ROBOTNIK_OFFSET_Y,
                    false, false);
        }
    }
}
