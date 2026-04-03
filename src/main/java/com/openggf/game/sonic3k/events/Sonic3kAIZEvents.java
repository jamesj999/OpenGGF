package com.openggf.game.sonic3k.events;

import com.openggf.camera.Camera;
import com.openggf.data.Rom;
import com.openggf.game.CheckpointState;
import com.openggf.game.GameServices;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.Sonic3kLoadBootstrap;
import com.openggf.game.sonic3k.Sonic3kLevel;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.graphics.GraphicsManager;
import com.openggf.game.sonic3k.objects.AizBattleshipInstance;
import com.openggf.game.sonic3k.objects.AizBgTreeSpawnerInstance;
import com.openggf.game.sonic3k.objects.AizBombExplosionInstance;
import com.openggf.game.sonic3k.objects.AizShipBombInstance;
import com.openggf.game.sonic3k.objects.AizBossSmallInstance;
import com.openggf.game.sonic3k.objects.AizHollowTreeObjectInstance;
import com.openggf.game.sonic3k.objects.AizMinibossInstance;
import com.openggf.game.sonic3k.objects.AizPlaneIntroInstance;
import com.openggf.level.LevelConstants;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.SeamlessLevelTransitionRequest;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.resources.LoadOp;
import com.openggf.level.resources.ResourceLoader;
import com.openggf.level.WaterSystem;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Angel Island Zone dynamic level events.
 * ROM: AIZ1_Resize / AIZ2_Resize (s3.asm)
 *
 * <p>Act 1 state machine (Dynamic_resize_routine):
 * <ul>
 *   <li>Routine 0: At camera X >= $1308 → load main AIZ palette (PalPointers #$2A)</li>
 *   <li>Routine 2: At camera X >= $1400 → queue terrain swap overlays</li>
 *   <li>Routine 4: Unlock Y boundaries, apply dynamic max Y from table</li>
 * </ul>
 *
 * Act 2: boss arena and scroll/deformation state are handled by the act's own tables.
 */
public class Sonic3kAIZEvents extends Sonic3kZoneEvents {
    private static final Logger LOG = Logger.getLogger(Sonic3kAIZEvents.class.getName());

    // --- ROM constants (s3.asm AIZ1_Resize) ---

    /** Camera X threshold to begin tracking minX (routine 0). */
    private static final int MIN_X_TRACK_START = 0x1000;

    /** Camera X threshold for palette swap and minX lock (routine 0 → 1). */
    private static final int PALETTE_SWAP_X = 0x1308;

    /** PalPointers index for Pal_AIZ (main AIZ palette, 3 lines → palette 1-3). */
    private static final int PAL_AIZ_INDEX = 0x2A;

    /** PalPointers index for Pal_AIZFire (fire palette, 3 lines → palette 1-3). */
    private static final int PAL_POINTER_AIZ_FIRE_INDEX = 0x0B;

    /** PalPointers index for Pal_AIZBoss (boss-area palette, 3 lines → palette 1-3). */
    private static final int PAL_AIZ_BOSS_INDEX = 0x30;

    /** Camera X threshold for terrain swap (routine 2). Already handled by AizPlaneIntroInstance. */
    private static final int TERRAIN_SWAP_X = 0x1400;

    // --- ROM: loc_1A9EC palette[2][15] per-frame mutation (s3.asm:32171-32195) ---
    // Cascading overwrite: $020E → $0004 at $2B00 → $0C02 at $2D80.
    private static final int PALETTE_MUT_THRESHOLD_DARK = 0x2B00;
    private static final int PALETTE_MUT_THRESHOLD_FIRE = 0x2D80;
    private static final int PALETTE_MUT_COLOR_RED = 0x020E;
    private static final int PALETTE_MUT_COLOR_DARK = 0x0004;
    private static final int PALETTE_MUT_COLOR_FIRE = 0x0C02;
    // AIZ1_ScreenEvent hollow-tree reveal thresholds/chunk columns.
    private static final int TREE_REVEAL_CLEAR_CAMERA_X = 0x2D30;
    private static final int TREE_REVEAL_CLEAR_COUNTER = 0x39;
    private static final int TREE_REVEAL_STEP2_COUNTER = 0x34;
    private static final int TREE_REVEAL_STEP3_COUNTER = 0x24;
    private static final int TREE_REVEAL_STEP4_COUNTER = 0x14;
    private static final int TREE_REVEAL_COL_A = 0x59;
    private static final int TREE_REVEAL_COL_B = 0x5A;
    private static final int TREE_REVEAL_SRC_COL_A = 0x00;
    private static final int TREE_REVEAL_SRC_COL_B = 0x01;
    private static final int[] TREE_REVEAL_COLUMNS = {TREE_REVEAL_COL_A, TREE_REVEAL_COL_B};
    private static final int[] TREE_REVEAL_SOURCE_COLUMNS = {TREE_REVEAL_SRC_COL_A, TREE_REVEAL_SRC_COL_B};
    private static final int TREE_REVEAL_SOURCE_X = 0x0000; // ROM call uses d1=0 for source row reads.
    private static final int TREE_REVEAL_DEST_X = 0x2C80;
    private static final int TREE_REVEAL_SOURCE_Y_OFFSET = 0x280;
    private static final int TREE_REVEAL_INITIAL_DEST_Y = 0x470;
    private static final int TREE_REVEAL_MASK_STRIDE = 0x10;
    private static final int TREE_REVEAL_MASK_ROW_ADVANCE = 0x20;
    private static final int TREE_REVEAL_MAX_ROW_WINDOW = 2;
    private static final int TREE_REVEAL_BLOCK_SIZE = 0x80;
    private static final int TILE_SIZE = 8;
    private static final int TREE_REVEAL_ROW_CHUNKS = 16;
    // {sourceRow, targetRow} from AIZ1SE_ChangeChunk4/3/2/1 pointer math.
    private static final int[][] TREE_REVEAL_ROW_COPIES = {
            {3, 8},
            {2, 7},
            {1, 6},
            {0, 5},
    };
    // ROM: AIZ_TreeRevealArray (s3.asm / sonic3k.asm)
    private static final byte[] TREE_REVEAL_MASKS = {
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0,
            0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0,
            0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0,
            0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
    };

    /**
     * Dynamic max Y resize table (word_1AA84 in s3.asm).
     * Format: {maxY, triggerX}. Bit 15 in ROM ($8xxx) = set immediately; all entries have it.
     * Scanned until cameraX <= triggerX. Last entry uses 0xFFFF as catch-all.
     */
    private static final int[][] AIZ1_RESIZE_TABLE = {
            {0x0390, 0x1650},
            {0x03B0, 0x1B00},
            {0x0430, 0x2000},
            {0x04C0, 0x2B00},
            {0x03B0, 0x2D80},
            {0x02E0, 0xFFFF},
    };

    // --- AIZ2 resize state machine (sonic3k.asm AIZ2_Resize) ---
    // Sonic path thresholds
    private static final int AIZ2_SONIC_RESIZE1_TRIGGER_X = 0x02E0;
    private static final int AIZ2_SONIC_RESIZE2_BOSS_MAX_Y = 0x02B8;
    private static final int AIZ2_SONIC_RESIZE2_BOSS_TRIGGER_X = 0x0ED0;
    private static final int AIZ2_SONIC_RESIZE2_LOCK_X = 0x0F50;
    private static final int AIZ2_SONIC_RESIZE3_TRIGGER_X = 0x1500;
    private static final int AIZ2_SONIC_RESIZE3_MAX_Y = 0x0630;
    private static final int AIZ2_SONIC_RESIZE4_TRIGGER_X = 0x3C00;
    private static final int AIZ2_SONIC_RESIZE5_TRIGGER_X = 0x3F00;
    private static final int AIZ2_SONIC_RESIZE5_MIN_Y = 0x015A;
    private static final int AIZ2_SONIC_RESIZE6_TRIGGER_X = 0x4000;
    private static final int AIZ2_SONIC_RESIZE7_TRIGGER_X = 0x4160;
    private static final int AIZ2_SONIC_BOSS_X = 0x11F0;
    private static final int AIZ2_SONIC_BOSS_Y = 0x0289;
    // Knuckles path thresholds
    private static final int AIZ2_KNUX_RESIZE1_TRIGGER_X = 0x02E0;
    private static final int AIZ2_KNUX_RESIZE2_BOSS_MAX_Y = 0x0450;
    private static final int AIZ2_KNUX_RESIZE2_BOSS_TRIGGER_X = 0x0E80;
    private static final int AIZ2_KNUX_RESIZE2_LOCK_X = 0x1040;
    private static final int AIZ2_KNUX_RESIZE3_TRIGGER_X = 0x11A0;
    private static final int AIZ2_KNUX_RESIZE3_TARGET_MAX_Y = 0x0820;
    private static final int AIZ2_KNUX_RESIZE4_TRIGGER_X = 0x3B80;
    private static final int AIZ2_KNUX_RESIZE4_TARGET_MAX_Y = 0x05DA;
    private static final int AIZ2_KNUX_RESIZE5_TRIGGER_X = 0x3F80;
    private static final int AIZ2_KNUX_BOSS_X = 0x11D0;
    private static final int AIZ2_KNUX_BOSS_Y = 0x0420;
    private static final int AIZ2_KNUX_WATER_LEVEL = 0x0F80;
    // Shared
    private static final int AIZ2_DEFAULT_MAX_Y = 0x0590;

    // --- Battleship bombing sequence constants (sonic3k.asm AIZ2_ScreenEvent) ---
    /** Auto-scroll speed during the bombing loop: 4 pixels/frame. */
    private static final int BATTLESHIP_SCROLL_SPEED = 4;
    /** Wrap boundary during bombing: camera X wraps back at $4440. ROM: Events_bg+$02 initial. */
    private static final int BATTLESHIP_WRAP_X_BOMBING = 0x4440;
    /**
     * Wrap boundary after bombing. ROM uses $46C0 with $200 distance, landing at $44C0
     * (before the forest). The ROM hides this seam via HInt screen-split. Without HInt,
     * we use a tight $80 wrap within the uniform forest blocks (cols 140-143 are all
     * E9/E8). Boundary $46C0 keeps the screen right edge at col 144.0 (last forest col
     * is 143); wraps to $4640 (col 140.5, still forest). Small boss trigger $4670 fits.
     */
    private static final int BATTLESHIP_WRAP_X_POST_BOMBING = 0x46C0;
    private static final int BATTLESHIP_WRAP_DIST_POST_BOMBING = 0x80;
    /** Wrap distance during bombing: subtract $200 from all positions on wrap. */
    private static final int BATTLESHIP_WRAP_DIST = 0x200;
    /** Left clamp: player X must be >= camera X + $18 during auto-scroll. */
    private static final int PLAYER_LEFT_MARGIN = 0x18;
    /** Right clamp: player X must be <= camera X + $A0 during auto-scroll. */
    private static final int PLAYER_RIGHT_MARGIN = 0xA0;
    /** Camera max X to set when the small boss exits (end of sequence). */
    private static final int BATTLESHIP_END_CAMERA_MAX_X = 0x6000;

    private final Sonic3kLoadBootstrap bootstrap;
    private boolean introSpawned;
    /** One-shot guard: once AIZ intro minX is locked at $1308, stop rewriting minX each frame. */
    private boolean introMinXLocked;
    private boolean paletteSwapped;
    private boolean boundariesUnlocked;
    // Tracks one-shot application of AIZ1SE_ChangeChunk4/3/2/1.
    private int appliedTreeRevealChunkCopiesMask;

    // --- AIZ2 Dynamic_resize_routine state ---
    /** ROM: Dynamic_resize_routine equivalent for act 2. */
    private int aiz2ResizeRoutine;
    /**
     * ROM equivalent: {@code Apparent_zone_and_act == AIZ2}.
     * True when the player entered AIZ2 directly (level select / death restart),
     * false when arriving through the AIZ1 fire transition.
     * Controls whether the miniboss area is skipped in SonicResize1/KnuxResize1.
     */
    private boolean enteredAsAct2;

    // --- Boss / fire transition state ---
    /** One-shot guard for AIZ2 resize boss spawn. */
    private boolean minibossSpawned;
    /** ROM: (Events_fg_5).w - set by boss exit sequence to trigger fire transition. */
    private boolean eventsFg5;
    /** Boss_flag equivalent - set when boss is present, cleared on cleanup. */
    private boolean bossFlag;
    // --- Battleship bombing sequence state ---
    /** True while the battleship auto-scroll loop is active. */
    private boolean battleshipAutoScrollActive;
    /** True once the battleship object has been spawned (one-shot guard). */
    private boolean battleshipSpawned;
    /** True once the AIZ2 bombership 8x8/16x16 terrain overlays have been applied. */
    private boolean battleshipTerrainLoaded;
    /** Current wrap boundary for auto-scroll (changes after bombing completes). */
    private int battleshipWrapX;
    /**
     * ROM: Screen_shake_flag — timed screen shake countdown. When positive,
     * decrements each frame and applies Y offset from ScreenShakeArray.
     * Set to $10 (16) on bomb impact.
     */
    private int screenShakeTimer;
    /**
     * ROM: Level_repeat_offset — set to $200 during a wrap frame, 0 otherwise.
     * Active objects subtract this from their X each frame to stay in sync.
     */
    private int levelRepeatOffset;
    /**
     * ROM: AIZ2BGE_Normal BG camera Y adjustment applied when eventsFg5 triggers.
     * Value is $A8 if Camera_Y_pos &lt; $400, or -$198 otherwise.
     * Added to the BG vertical scroll factor by the scroll handler.
     */
    private int battleshipBgYOffset;
    /**
     * Cumulative scroll distance during the battleship sequence (never wraps).
     * Used by the parallax scroll handler to compute smooth BG deformation
     * even when the camera X wraps back by $200.
     * ROM equivalent: in the ROM the camera never wraps back for the BG plane;
     * instead, DrawTilesAsYouMove + Level_repeat_offset handle the FG tile columns.
     */
    private int battleshipSmoothScrollX;
    /** Current vertical shake offset produced by {@link #screenShakeTimer}. */
    private int screenShakeOffsetY;
    /** True after the act switch request has been sent to LevelManager. */
    private boolean act2TransitionRequested;
    /** True after in-place mutation stage has been requested. */
    private boolean fireTransitionMutationRequested;
    /** True once the post-burn fine haze phase should be active on FG. */
    private boolean postFireHazeActive;
    /** One-shot guard for AIZ1 fire-overlay 8x8 art staging at x >= $2E00. */
    private boolean fireOverlayTilesLoaded;
    /** Fixed-point Camera_Y_pos_BG_copy used by AIZ1_FireRise (16.16). */
    private int fireBgCopyFixed;
    /** Events_bg+$02 equivalent: rising-fire speed. */
    private int fireRiseSpeed;
    /** _unkEE8E equivalent used by AIZTrans_WavyFlame. */
    private int fireWavePhase;
    /** Total fake-out fire frame counter across act 1 and the resumed act 2 continuation. */
    private int fireTransitionFrames;
    /** Per-phase frame counter used for the redraw phases. */
    private int firePhaseFrames;
    /** True once AIZ2 WaitFire has snapped to the dedicated $200 source strip. */
    private boolean act2WaitFireDrawActive;
    /** Current fake-out fire phase derived from the AIZ1/AIZ2 background event routines. */
    private FireSequencePhase fireSequencePhase = FireSequencePhase.INACTIVE;

    /** BG Y coordinate where fire tiles begin in the AIZ BG layout. */
    private static final int FIRE_TILE_START_Y = 0x0100;
    private static final int FIRE_BG_FIXED_START = 0x0020_0000;
    private static final int FIRE_BG_TARGET = 0x0068_0000;
    private static final int FIRE_BG_LERP_SHIFT = 5;
    private static final int FIRE_BG_LERP_MIN_DELTA = 0x1400;
    private static final int FIRE_RISE_ACCEL = 0x0280;
    private static final int FIRE_RISE_MAX_SPEED = 0xA000;
    private static final int FIRE_BG_FINISH_Y = 0x0310;
    /** Height of the fire tile zone in the BG layout (0x310 - 0x100 = 0x210). */
    private static final int FIRE_TILE_HEIGHT = FIRE_BG_FINISH_Y - FIRE_TILE_START_Y;
    // ROM parity: AIZ1BGE_FireTransition switches to the fire-stage overlays at
    // Camera_Y_pos_BG_copy >= $190 before entering refresh/finish routines.
    private static final int FIRE_BG_MUTATION_Y = 0x0190;
    private static final int FIRE_BG_X_BASE = 0x1000;
    private static final int FIRE_SOURCE_X_AIZ1 = 0x1000;
    private static final int FIRE_SOURCE_X_AIZ2 = 0x0200;
    private static final int FIRE_BG_X_PHASE_MASK = 0x0060;
    private static final int FIRE_WAVE_PHASE_STEP = 6;
    /**
     * Extra frames to hold fire at full screen before transitioning to act 2.
     * The ROM's Kos decompression wait in AIZ1BGE_Finish creates a natural
     * linger where the fire covers the screen while art decompresses.  At 60fps
     * the same game-frame math produces a visually shorter fire because there
     * is no decompression overhead.  This constant approximates the ROM's
     * visual duration by pausing fire advance for the equivalent frames.
     */
    private static final int FIRE_LINGER_FRAMES = 48;
    private static final int FIRE_TRANSITION_FALLBACK_FRAMES = 240;
    private static final int FIRE_REDRAW_FRAMES = 16;
    private static final int FIRE_OVERLAY_STAGE_X = 0x2E00;
    private static final int FIRE_OVERLAY_TILE_DEST = 0x500;
    private static final int FIRE_OVERLAY_PLC = 0x0C;
    public static final int FIRE_WAVE_COLUMN_COUNT = 0x14;
    private static final byte[] FIRE_COLUMN_WAVE = {
            0, -1, -2, -5, -8, -10, -13, -14,
            -15, -14, -13, -10, -7, -5, -2, -1
    };
    // ROM: AIZ1_AIZ2_Transition writes these 6 words to Normal_palette_line_4+$2.
    private static final int[] FIRE_TRANSITION_LINE4_WORDS = {
            0x004E, 0x006E, 0x00AE, 0x00CE, 0x02EE, 0x0AEE
    };
    // ROM: AIZ2BGE_WaitFire rewrites line 4 once the fire finally clears.
    // move.l #$8EE00AA,(a1)+ / move.l #$8E004E,(a1)+ / move.l #$2E000C,(a1)
    private static final int[] POST_FIRE_LINE4_WORDS = {
            0x08EE, 0x00AA, 0x008E, 0x004E, 0x002E, 0x000C
    };
    private static volatile PendingFireSequence pendingFireSequence;

    /**
     * Resets all static/global state held by this class.
     * Called from {@link Sonic3kLevelEventManager#resetState()} to prevent
     * fire wall handoff data from leaking across level loads and test iterations.
     */
    public static void resetGlobalState() {
        pendingFireSequence = null;
    }

    private int fireOverlayTileCount;

    private enum FireSequencePhase {
        INACTIVE,
        AIZ1_FIRE_TRANSITION,
        AIZ1_FIRE_REFRESH,
        AIZ1_FINISH,
        AIZ2_FIRE_REDRAW,
        AIZ2_WAIT_FIRE,
        AIZ2_BG_REDRAW,
        COMPLETE;

        boolean curtainActive() {
            return switch (this) {
                case AIZ1_FIRE_TRANSITION, AIZ1_FIRE_REFRESH, AIZ1_FINISH,
                     AIZ2_FIRE_REDRAW, AIZ2_WAIT_FIRE, AIZ2_BG_REDRAW -> true;
                default -> false;
            };
        }

        /** True for act 1 phases where the fire overlay wraps (loops) tiles. */
        boolean wrapFireTiles() {
            return switch (this) {
                case AIZ1_FIRE_TRANSITION, AIZ1_FIRE_REFRESH, AIZ1_FINISH -> true;
                default -> false;
            };
        }

        boolean usesFireScrollMode() {
            return switch (this) {
                case AIZ1_FIRE_TRANSITION, AIZ1_FIRE_REFRESH, AIZ1_FINISH, AIZ2_FIRE_REDRAW, AIZ2_WAIT_FIRE -> true;
                default -> false;
            };
        }
    }

    private record PendingFireSequence(
            FireSequencePhase phase,
            int fireBgCopyFixed,
            int fireRiseSpeed,
            int fireWavePhase,
            int fireTransitionFrames,
            int firePhaseFrames,
            boolean mutationRequested,
            boolean act2WaitFireDrawActive) {
    }

    public Sonic3kAIZEvents(Sonic3kLoadBootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    @Override
    public void init(int act) {
        super.init(act);
        introSpawned = false;
        introMinXLocked = false;
        paletteSwapped = false;
        boundariesUnlocked = false;
        appliedTreeRevealChunkCopiesMask = 0;
        minibossSpawned = false;
        aiz2ResizeRoutine = 0;
        enteredAsAct2 = false;
        eventsFg5 = false;
        bossFlag = false;
        battleshipAutoScrollActive = false;
        battleshipSpawned = false;
        battleshipTerrainLoaded = false;
        battleshipWrapX = BATTLESHIP_WRAP_X_BOMBING;
        levelRepeatOffset = 0;
        battleshipBgYOffset = 0;
        battleshipSmoothScrollX = 0;
        screenShakeTimer = 0;
        screenShakeOffsetY = 0;
        act2TransitionRequested = false;
        fireTransitionMutationRequested = false;
        postFireHazeActive = false;
        fireOverlayTilesLoaded = false;
        fireBgCopyFixed = FIRE_BG_FIXED_START;
        fireRiseSpeed = 0;
        fireWavePhase = 0;
        fireTransitionFrames = 0;
        firePhaseFrames = 0;
        act2WaitFireDrawActive = false;
        fireSequencePhase = FireSequencePhase.INACTIVE;
        fireOverlayTileCount = 0;
        if (act == 0) {
            pendingFireSequence = null;
        } else {
            setTransitionControlLock(false);
        }
        restorePendingFireSequenceIfPresent(act);
        if (act == 0) {
            AizPlaneIntroInstance.resetIntroPhaseState();
            AizHollowTreeObjectInstance.resetTreeRevealCounter();
        }
        if (shouldSpawnIntro(act)) {
            // Suppress Tails sidekick immediately so he doesn't appear before
            // the intro object's first update(). ROM: Tails_CPU_routine = $20.
            AizPlaneIntroInstance.setSidekickSuppressed(true);
            LOG.info("AIZ1 intro: will spawn intro object");
        }
    }

    @Override
    public void update(int act, int frameCounter) {
        if (act == 0) {
            updateAct1(frameCounter);
        } else {
            updateAct2Continuation();
        }
    }

    private void updateAct1(int frameCounter) {
        // Spawn intro object (one-shot)
        if (!introSpawned && shouldSpawnIntro(0)) {
            spawnIntroObject();
            introSpawned = true;
        }

        int cameraX = camera().getX();
        applyHollowTreeScreenEvent(cameraX);

        // --- Routine 0→1: MinX tracking during intro panning ---
        // ROM (s3.asm AIZ1_Resize Stage 0): Once camera X >= $1000,
        // Camera_min_X_pos tracks camera X to prevent backtracking.
        // At camera X >= $1308 (Stage 1): lock Camera_min_X_pos = $1308.
        if (shouldSpawnIntro(0) && !introMinXLocked) {
            // ROM stage 0->1 lock: track minX until $1308, then freeze at $1308 once.
            if (cameraX >= PALETTE_SWAP_X) {
                camera().setMinX((short) PALETTE_SWAP_X);
                introMinXLocked = true;
            } else if (cameraX >= MIN_X_TRACK_START) {
                camera().setMinX((short) cameraX);
            }
        }

        // --- Routine 0: Palette swap at camera X >= $1308 ---
        if (!paletteSwapped && cameraX >= PALETTE_SWAP_X) {
            loadPaletteFromPalPointers(PAL_AIZ_INDEX);
            paletteSwapped = true;
            LOG.info("AIZ1: loaded main palette (PalPointers #0x2A) at cameraX=0x"
                    + Integer.toHexString(cameraX));
        }

        // --- Routine 2: Terrain swap at camera X >= $1400 ---
        // For skip-intro bootstrap, camera starts past this point and still requires
        // the same main-level overlay activation before tree reveal chunk staging.
        AizPlaneIntroInstance.updateMainLevelPhaseForCameraX(cameraX);
        if (cameraX >= FIRE_OVERLAY_STAGE_X) {
            // Keep the fire overlay staging after the intro/main-level terrain swap.
            // Both paths patch shared level-art VRAM ranges in this engine, and
            // staging flames first lets the terrain swap clobber the curtain bank.
            ensureFireOverlayTilesLoaded();
        }

        // --- Routine 4: Y boundary unlock + dynamic max Y ---
        // Also re-apply main palette here to overwrite cutscene residue:
        // applyEmeraldPalette() (at player X=$13D0 during routine26Explode)
        // writes directly to GPU line 3, bypassing level.setPalette(), which
        // overwrites the Pal_AIZ loaded at $1308. Re-applying at $1400
        // ensures lines 1-3 are correct for the main level.
        if (AizPlaneIntroInstance.isMainLevelPhaseActive() && !boundariesUnlocked) {
            // Skip palette reload if the fire transition is already active — the fire
            // palette takes precedence.  In skip-intro or teleport scenarios, the
            // simulated decompression countdown may expire after the fire has started.
            if (!isFireTransitionActive()) {
                loadPaletteFromPalPointers(PAL_AIZ_INDEX);
            }
            camera().setMinY((short) 0);
            boundariesUnlocked = true;
            LOG.info("AIZ1: unlocked Y boundaries (minY=0)"
                    + (isFireTransitionActive() ? ", skipped palette (fire active)" : ", re-applied main palette"));
        }
        if (boundariesUnlocked) {
            resizeMaxYFromX(cameraX);
            applyResizePaletteMutation(cameraX);
        }

        updateFireTransition();
    }

    /**
     * ROM: Resize_MaxYFromX with word_1AA84 table.
     * Scans table entries until cameraX <= triggerX, then sets max Y.
     * All entries have bit 15 set (immediate, not eased).
     */
    private void resizeMaxYFromX(int cameraX) {
        for (int[] entry : AIZ1_RESIZE_TABLE) {
            int maxY = entry[0];
            int triggerX = entry[1];
            if (triggerX == 0xFFFF || cameraX <= triggerX) {
                camera().setMaxY((short) maxY);
                return;
            }
        }
    }

    /**
     * ROM: loc_1A9EC per-frame palette[2][15] mutation (s3.asm:32171-32195).
     *
     * Every frame while routine 4 is active, the ROM writes a cascading color:
     * <ul>
     *   <li>Unconditional: $020E (bright red)</li>
     *   <li>Camera X >= $2B00: $0004 (nearly black — darkens hollow tree interior)</li>
     *   <li>Camera X >= $2D80: $0C02 (amber — pre-fire zone)</li>
     * </ul>
     * Skipped when the fire transition is active (fire palette takes precedence).
     */
    private void applyResizePaletteMutation(int cameraX) {
        if (isFireTransitionActive()) {
            return;
        }
        LevelManager levelManager = levelManager();
        if (levelManager == null || levelManager.getCurrentLevel() == null) {
            return;
        }
        Level level = levelManager.getCurrentLevel();
        if (level.getPaletteCount() <= 2) {
            return;
        }
        Palette pal2 = level.getPalette(2);

        int segaColor = PALETTE_MUT_COLOR_RED;
        if (cameraX >= PALETTE_MUT_THRESHOLD_FIRE) {
            segaColor = PALETTE_MUT_COLOR_FIRE;
        } else if (cameraX >= PALETTE_MUT_THRESHOLD_DARK) {
            segaColor = PALETTE_MUT_COLOR_DARK;
        }

        byte[] colorBytes = {(byte) ((segaColor >> 8) & 0xFF), (byte) (segaColor & 0xFF)};
        pal2.getColor(15).fromSegaFormat(colorBytes, 0);

        GraphicsManager gm = GraphicsManager.getInstance();
        if (gm.isGlInitialized()) {
            gm.cachePaletteTexture(pal2, 2);
        }
    }

    /**
     * Returns whether the intro cinematic should be spawned for the given act.
     * The intro only runs on Act 1 (act==0) when the bootstrap is not
     * skipping the intro (i.e., a fresh game start, not an intro-skip scenario).
     *
     * Package-private for test access.
     */
    boolean shouldSpawnIntro(int act) {
        return act == 0 && !bootstrap.isSkipIntro();
    }

    private void spawnIntroObject() {
        ObjectSpawn spawn = new ObjectSpawn(0x60, 0x30, 0, 0, 0, false, 0);
        AizPlaneIntroInstance intro = new AizPlaneIntroInstance(spawn);
        spawnObject(intro);
        LOG.info("AIZ1 intro: spawned plane intro object");
    }

    private void applyHollowTreeScreenEvent(int cameraX) {
        int eventsFg4 = AizHollowTreeObjectInstance.getTreeRevealCounter();
        if (eventsFg4 == 0) {
            return;
        }

        LevelManager levelManager = levelManager();
        boolean changed = false;
        if (cameraX >= TREE_REVEAL_CLEAR_CAMERA_X || eventsFg4 >= TREE_REVEAL_CLEAR_COUNTER) {
            changed |= applyChunkCopyAndSync(levelManager, 3);
            AizHollowTreeObjectInstance.setTreeRevealCounter(0);
            if (changed) {
                levelManager.uploadForegroundTilemap();
            }
            return;
        } else if (eventsFg4 >= TREE_REVEAL_STEP2_COUNTER) {
            changed |= applyChunkCopyAndSync(levelManager, 2);
        } else if (eventsFg4 >= TREE_REVEAL_STEP3_COUNTER) {
            changed |= applyChunkCopyAndSync(levelManager, 1);
        } else if (eventsFg4 >= TREE_REVEAL_STEP4_COUNTER) {
            changed |= applyChunkCopyAndSync(levelManager, 0);
        }

        TreeRevealStepResult revealStep = applyTreeRevealMaskedRows(levelManager, eventsFg4);
        changed |= revealStep.changed();
        if (revealStep.reachedEnd()) {
            if (eventsFg4 < TREE_REVEAL_STEP2_COUNTER) {
                // In this renderer, early end-of-window can occur before the upper staged
                // chunk swap has been applied, which leaves the top reveal incomplete.
                if (changed) {
                    levelManager.uploadForegroundTilemap();
                }
                return;
            }
            // ROM flow branches to AIZ1SE_ChangeChunk1 when the reveal window is above
            // camera; keep that clear path here once the upper threshold has occurred.
            changed |= applyChunkCopyAndSync(levelManager, 3);
            AizHollowTreeObjectInstance.setTreeRevealCounter(0);
        }

        if (changed) {
            levelManager.uploadForegroundTilemap();
        }
    }

    private TreeRevealStepResult applyTreeRevealMaskedRows(LevelManager levelManager, int eventsFg4) {
        int maskOffset = ((eventsFg4 & 1) == 0) ? TREE_REVEAL_MASK_STRIDE : 0;
        int d0 = (eventsFg4 - 1) >>> 1;
        int rowsRemaining = Math.min(d0, TREE_REVEAL_MAX_ROW_WINDOW);
        int rowY = TREE_REVEAL_INITIAL_DEST_Y - (d0 << 4);
        int cameraYRounded = camera().getY() & ~0xF;
        boolean changed = false;
        boolean drewAny = false;

        while (rowsRemaining >= 0) {
            if (rowY < cameraYRounded) {
                maskOffset += TREE_REVEAL_MASK_ROW_ADVANCE;
                rowY += 0x10;
                rowsRemaining--;
                continue;
            }

            drewAny = true;
            changed |= applyTreeRevealMaskedRowPair(levelManager, rowY, maskOffset);
            // ROM flow after each row pair:
            // lea $10(a6),a6 / addi #$290,d0 after subi #$280 before call => net +$10.
            maskOffset += TREE_REVEAL_MASK_STRIDE;
            rowY += 0x10;
            rowsRemaining--;
        }

        // ROM parity: if all candidate rows are already above the rounded camera Y,
        // the routine branches to AIZ1SE_ChangeChunk1 and ends the reveal event.
        return new TreeRevealStepResult(changed, !drewAny);
    }

    private boolean applyTreeRevealMaskedRowPair(LevelManager levelManager, int destinationY, int maskOffset) {
        if (maskOffset < 0
                || maskOffset + TREE_REVEAL_MASK_STRIDE + TREE_REVEAL_ROW_CHUNKS > TREE_REVEAL_MASKS.length) {
            return false;
        }
        int sourceY = destinationY - TREE_REVEAL_SOURCE_Y_OFFSET;
        boolean changed = false;
        for (int chunkIndex = 0; chunkIndex < TREE_REVEAL_ROW_CHUNKS; chunkIndex++) {
            if (TREE_REVEAL_MASKS[maskOffset + chunkIndex] != 0) {
                changed |= applyMaskedRevealChunk(levelManager, chunkIndex, sourceY, destinationY);
            }
            if (TREE_REVEAL_MASKS[maskOffset + TREE_REVEAL_MASK_STRIDE + chunkIndex] != 0) {
                changed |= applyMaskedRevealChunk(levelManager, chunkIndex, sourceY + TILE_SIZE, destinationY + TILE_SIZE);
            }
        }
        return changed;
    }

    private boolean applyMaskedRevealChunk(LevelManager levelManager, int chunkIndex, int sourceY, int destinationY) {
        int sourceChunkX = TREE_REVEAL_SOURCE_X + chunkIndex * LevelConstants.CHUNK_WIDTH;
        int destinationChunkX = TREE_REVEAL_DEST_X + chunkIndex * LevelConstants.CHUNK_WIDTH;

        int sourceLeft = levelManager.getForegroundTileDescriptorAtWorld(sourceChunkX, sourceY);
        int sourceRight = levelManager.getForegroundTileDescriptorAtWorld(sourceChunkX + TILE_SIZE, sourceY);
        boolean changed = false;
        changed |= levelManager.setForegroundTileDescriptorAtWorld(destinationChunkX, destinationY, sourceLeft);
        changed |= levelManager.setForegroundTileDescriptorAtWorld(destinationChunkX + TILE_SIZE, destinationY, sourceRight);
        return changed;
    }

    private boolean applyChunkCopyAndSync(LevelManager levelManager, int tableIndex) {
        if (tableIndex < 0 || tableIndex >= TREE_REVEAL_ROW_COPIES.length) {
            return false;
        }

        int bit = 1 << tableIndex;
        if ((appliedTreeRevealChunkCopiesMask & bit) != 0) {
            return false;
        }
        appliedTreeRevealChunkCopiesMask |= bit;

        int sourceRow = TREE_REVEAL_ROW_COPIES[tableIndex][0];
        int targetRow = TREE_REVEAL_ROW_COPIES[tableIndex][1];
        int sourceBaseY = sourceRow * TREE_REVEAL_BLOCK_SIZE;
        int targetBaseY = targetRow * TREE_REVEAL_BLOCK_SIZE;

        boolean changed = false;
        for (int columnIndex = 0; columnIndex < TREE_REVEAL_COLUMNS.length; columnIndex++) {
            int sourceBaseX = TREE_REVEAL_SOURCE_COLUMNS[columnIndex] * TREE_REVEAL_BLOCK_SIZE;
            int targetBaseX = TREE_REVEAL_COLUMNS[columnIndex] * TREE_REVEAL_BLOCK_SIZE;
            for (int y = 0; y < TREE_REVEAL_BLOCK_SIZE; y += TILE_SIZE) {
                int sourceY = sourceBaseY + y;
                int targetY = targetBaseY + y;
                for (int x = 0; x < TREE_REVEAL_BLOCK_SIZE; x += TILE_SIZE) {
                    int sourceX = sourceBaseX + x;
                    int targetX = targetBaseX + x;
                    int descriptor = levelManager.getForegroundTileDescriptorFromTilemapAtWorld(sourceX, sourceY);
                    changed |= levelManager.setForegroundTileDescriptorAtWorld(targetX, targetY, descriptor);
                }
            }
        }
        return changed;
    }

    private record TreeRevealStepResult(boolean changed, boolean reachedEnd) {
    }

    // --- Boss / fire transition accessors ---

    /** Called by the boss object to signal that the fire transition should begin. */
    public void setEventsFg5(boolean flag) {
        this.eventsFg5 = flag;
        if (flag) {
            LOG.info("AIZ1: Events_fg_5 set - fire transition signaled");
        }
    }

    public boolean isEventsFg5() {
        return eventsFg5;
    }

    public void setBossFlag(boolean flag) {
        this.bossFlag = flag;
    }

    public boolean isBossFlag() {
        return bossFlag;
    }

    public boolean isFireTransitionActive() {
        return fireSequencePhase.curtainActive();
    }

    public boolean isAct2TransitionRequested() {
        return act2TransitionRequested;
    }

    public boolean isFireTransitionScrollActive() {
        return fireSequencePhase.usesFireScrollMode();
    }

    public boolean isPostFireHazeActive() {
        return postFireHazeActive;
    }

    /**
     * Equivalent to Camera_Y_pos_BG_copy during AIZ1 fire transition.
     */
    public int getFireTransitionBgY() {
        return fireBgCopyFixed >> 16;
    }

    /**
     * Equivalent to Camera_X_pos_BG_copy updates in AIZTrans_WavyFlame.
     */
    public int getFireTransitionBgX() {
        return FIRE_BG_X_BASE + (fireWavePhase & FIRE_BG_X_PHASE_MASK);
    }

    /**
     * Deterministic, bottom-anchored fire curtain state for the AIZ transition overlay.
     */
    public FireCurtainRenderState getFireCurtainRenderState(int screenHeight) {
        if (screenHeight <= 0) {
            return FireCurtainRenderState.inactive();
        }

        if (!fireSequencePhase.curtainActive()) {
            return FireCurtainRenderState.inactive();
        }

        boolean wrapActive = fireSequencePhase.wrapFireTiles();

        return new FireCurtainRenderState(
                true,
                resolveCoverHeight(screenHeight),
                fireWavePhase,
                fireTransitionFrames,
                resolveFireCurtainSourceX(),
                getFireTransitionBgY(),
                buildFireColumnWaveOffsets(fireTransitionFrames),
                mapCurtainStage(fireSequencePhase),
                FIRE_OVERLAY_TILE_DEST,
                fireOverlayTileCount,
                wrapActive);
    }

    private int resolveCoverHeight(int screenHeight) {
        if (screenHeight <= 0) {
            return 0;
        }
        if (fireSequencePhase == FireSequencePhase.AIZ1_FIRE_TRANSITION) {
            return getFireWallCoverHeightPx(screenHeight);
        }
        // Post-rising phases: full-screen coverage.  The fire EXIT is handled
        // by the renderer: tiles outside the fire zone [0x100..0x310) are not
        // drawn, so the fire naturally scrolls off the top as bgY advances.
        return screenHeight;
    }

    private int resolveFireCurtainSourceX() {
        if (fireSequencePhase == FireSequencePhase.AIZ2_WAIT_FIRE && act2WaitFireDrawActive) {
            return FIRE_SOURCE_X_AIZ2;
        }
        // Use cycling BG X position matching ROM's Camera_X_pos_BG_copy during fire transition
        return getFireTransitionBgX();
    }

    private static FireCurtainStage mapCurtainStage(FireSequencePhase phase) {
        return switch (phase) {
            case AIZ1_FIRE_TRANSITION -> FireCurtainStage.AIZ1_RISING;
            case AIZ1_FIRE_REFRESH -> FireCurtainStage.AIZ1_REFRESH;
            case AIZ1_FINISH -> FireCurtainStage.AIZ1_FINISH;
            case AIZ2_FIRE_REDRAW -> FireCurtainStage.AIZ2_REDRAW;
            case AIZ2_WAIT_FIRE -> FireCurtainStage.AIZ2_WAIT_FIRE;
            case AIZ2_BG_REDRAW -> FireCurtainStage.AIZ2_BG_REDRAW;
            default -> FireCurtainStage.INACTIVE;
        };
    }

    private int[] buildFireColumnWaveOffsets(int animationFrameCounter) {
        int[] waveOffsets = new int[FIRE_WAVE_COLUMN_COUNT];
        int phase = (animationFrameCounter >> 2) & 0xF;
        for (int i = 0; i < FIRE_WAVE_COLUMN_COUNT; i++) {
            phase = (phase + 2) & 0xF;
            waveOffsets[i] = FIRE_COLUMN_WAVE[phase];
        }
        return waveOffsets;
    }

    private void updateAct2Continuation() {
        if (fireSequencePhase.curtainActive() || fireSequencePhase == FireSequencePhase.AIZ2_BG_REDRAW) {
            switch (fireSequencePhase) {
                case AIZ2_FIRE_REDRAW -> {
                    // After transition, fire scrolls off the top to reveal
                    // act 2 terrain.  Wrapping is disabled (wrapFireTiles=false
                    // for act 2 phases), so fire exits naturally.
                    advanceFireRise(false);
                    firePhaseFrames++;
                    if (firePhaseFrames >= FIRE_REDRAW_FRAMES) {
                        fireSequencePhase = FireSequencePhase.AIZ2_WAIT_FIRE;
                        act2WaitFireDrawActive = true;
                        firePhaseFrames = 0;
                    }
                }
                case AIZ2_WAIT_FIRE -> {
                    // Continue scroll-off until fire has exited the screen.
                    advanceFireRise(false);
                    if (getFireTransitionBgY() >= FIRE_BG_FINISH_Y) {
                        applyPostFireContinuationPaletteLine4(levelManager());
                        fireSequencePhase = FireSequencePhase.AIZ2_BG_REDRAW;
                        firePhaseFrames = 0;
                    }
                }
                case AIZ2_BG_REDRAW -> {
                    firePhaseFrames++;
                    if (firePhaseFrames >= FIRE_REDRAW_FRAMES) {
                        fireSequencePhase = FireSequencePhase.COMPLETE;
                        postFireHazeActive = true;
                        pendingFireSequence = null;
                        setTransitionControlLock(false);
                    }
                }
                default -> {
                    // Act 1 phases are handled by updateFireTransition().
                }
            }
        }

        // Battleship auto-scroll loop
        if (battleshipAutoScrollActive) {
            updateBattleshipAutoScroll();
        }

        // Timed screen shake (bomb impacts)
        tickScreenShake();

        // ROM: AIZ2_Resize — dynamic boundary state machine (sonic3k.asm:39012)
        updateAiz2Resize();
    }

    /**
     * AIZ2 dynamic resize state machine.
     * ROM: AIZ2_Resize (sonic3k.asm:39012-39241)
     *
     * <p>Routes to Sonic or Knuckles path based on player character.
     * Adjusts maxY/minY/minX dynamically as camera moves through the zone,
     * spawns the miniboss, and sets up the battleship sequence boundaries.
     */
    private void updateAiz2Resize() {
        PlayerCharacter character = Sonic3kLevelEventManager.getInstance().getPlayerCharacter();
        boolean isKnuckles = (character == PlayerCharacter.KNUCKLES);

        switch (aiz2ResizeRoutine) {
            // --- Routine 0: Route to Sonic or Knuckles path ---
            case 0 -> {
                if (isKnuckles) {
                    aiz2ResizeRoutine = 0x12;
                    updateAiz2KnuxResize1();
                } else {
                    aiz2ResizeRoutine = 2;
                    updateAiz2SonicResize1();
                }
            }
            // --- Sonic path ---
            case 2 -> updateAiz2SonicResize1();
            case 4 -> updateAiz2SonicResize2();
            case 6 -> updateAiz2SonicResize3();
            case 8 -> updateAiz2SonicResize4();
            case 0xA -> updateAiz2SonicResize5();
            case 0xC -> updateAiz2SonicResize6();
            case 0xE -> updateAiz2SonicResize7();
            // case 0x10: SonicResizeEnd — no-op
            // --- Knuckles path ---
            case 0x12 -> updateAiz2KnuxResize1();
            case 0x14 -> updateAiz2KnuxResize2();
            case 0x16 -> updateAiz2KnuxResize3();
            case 0x18 -> updateAiz2KnuxResize4();
            case 0x1A -> updateAiz2KnuxResize5();
            // case 0x1C: KnuxResizeEnd — no-op
            default -> { /* end state */ }
        }
    }

    // --- Sonic resize routines (sonic3k.asm:39046-39153) ---

    /** ROM: AIZ2_SonicResize1 — set maxY=$590 at camera X >= $2E0. */
    private void updateAiz2SonicResize1() {
        if (camera().getX() < AIZ2_SONIC_RESIZE1_TRIGGER_X) {
            return;
        }
        camera().setMaxY((short) AIZ2_DEFAULT_MAX_Y);
        aiz2ResizeRoutine = 4;
        // ROM: if Apparent_zone_and_act == AIZ2, skip the miniboss path.
        // Only skip when the player entered AIZ2 directly (level select / death
        // restart) — the miniboss has already been defeated in that scenario.
        // When arriving through the AIZ1 fire transition, the miniboss hasn't
        // been fought yet, so we must go through SonicResize2.
        if (enteredAsAct2) {
            camera().setMinX((short) AIZ2_SONIC_RESIZE2_LOCK_X);
            aiz2ResizeRoutine = 6; // skip SonicResize2 (miniboss area)
        }
    }

    /** ROM: AIZ2_SonicResize2 — continuous maxY + miniboss spawn. */
    private void updateAiz2SonicResize2() {
        int cameraX = camera().getX();
        int maxY = AIZ2_DEFAULT_MAX_Y;
        if (cameraX >= AIZ2_SONIC_RESIZE2_BOSS_TRIGGER_X) {
            maxY = AIZ2_SONIC_RESIZE2_BOSS_MAX_Y;
        }
        camera().setMaxY((short) maxY);

        if (cameraX >= AIZ2_SONIC_RESIZE2_LOCK_X) {
            camera().setMinX((short) AIZ2_SONIC_RESIZE2_LOCK_X);
            if (!minibossSpawned) {
                spawnAiz2Miniboss(AIZ2_SONIC_BOSS_X, AIZ2_SONIC_BOSS_Y);
            }
            aiz2ResizeRoutine = 6;
        }
    }

    /** ROM: AIZ2_SonicResize3 — maxY=$630 at camera X >= $1500. */
    private void updateAiz2SonicResize3() {
        if (camera().getX() < AIZ2_SONIC_RESIZE3_TRIGGER_X) {
            return;
        }
        camera().setMaxY((short) AIZ2_SONIC_RESIZE3_MAX_Y);
        aiz2ResizeRoutine = 8;
    }

    /** ROM: AIZ2_SonicResize4 — battleship art load at camera X >= $3C00. */
    private void updateAiz2SonicResize4() {
        if (camera().getX() < AIZ2_SONIC_RESIZE4_TRIGGER_X) {
            return;
        }
        ensureBattleshipTerrainLoaded();
        eventsFg5 = true; // Signal to background event
        // ROM: AIZ2BGE_Normal applies a one-time BG camera Y adjustment when eventsFg5 fires.
        // If Camera_Y_pos < $400: add $A8; otherwise: add -$198.
        // This shifts the background to show more sky before the bombing sequence.
        int cameraY = camera().getY() & 0xFFFF;
        battleshipBgYOffset = (cameraY < 0x400) ? 0xA8 : -0x198;
        aiz2ResizeRoutine = 0xA;
        LOG.info("AIZ2 Sonic resize4: battleship art trigger at X=0x"
                + Integer.toHexString(camera().getX())
                + ", bgYOffset=" + battleshipBgYOffset);
    }

    /** ROM: AIZ2_SonicResize5 — minY=$15A at camera X >= $3F00. */
    private void updateAiz2SonicResize5() {
        if (camera().getX() < AIZ2_SONIC_RESIZE5_TRIGGER_X) {
            return;
        }
        camera().setMinY((short) AIZ2_SONIC_RESIZE5_MIN_Y);
        aiz2ResizeRoutine = 0xC;
    }

    /** ROM: AIZ2_SonicResize6 — maxY=$15A at camera X >= $4000. */
    private void updateAiz2SonicResize6() {
        if (camera().getX() < AIZ2_SONIC_RESIZE6_TRIGGER_X) {
            return;
        }
        camera().setMaxY((short) AIZ2_SONIC_RESIZE5_MIN_Y);
        aiz2ResizeRoutine = 0xE;
    }

    /** ROM: AIZ2_SonicResize7 — signal battleship sequence at camera X >= $4160. */
    private void updateAiz2SonicResize7() {
        if (camera().getX() < AIZ2_SONIC_RESIZE7_TRIGGER_X) {
            return;
        }
        // Start the battleship bombing sequence: auto-scroll + spawn battleship
        startBattleshipSequence();
        aiz2ResizeRoutine = 0x10; // SonicResizeEnd
    }

    // --- Knuckles resize routines (sonic3k.asm:39157-39241) ---

    /** ROM: AIZ2_KnuxResize1 — set maxY=$590 at camera X >= $2E0. */
    private void updateAiz2KnuxResize1() {
        if (camera().getX() < AIZ2_KNUX_RESIZE1_TRIGGER_X) {
            return;
        }
        camera().setMaxY((short) AIZ2_DEFAULT_MAX_Y);
        aiz2ResizeRoutine = 0x14;
        // ROM: if Apparent_zone_and_act == AIZ2, skip the miniboss path.
        // Same gate as SonicResize1 — only skip when entered AIZ2 directly.
        if (enteredAsAct2) {
            camera().setMinX((short) AIZ2_KNUX_RESIZE2_LOCK_X);
            aiz2ResizeRoutine = 0x16; // skip KnuxResize2 (miniboss area)
        }
    }

    /** ROM: AIZ2_KnuxResize2 — continuous maxY + miniboss spawn. */
    private void updateAiz2KnuxResize2() {
        int cameraX = camera().getX();
        int maxY = AIZ2_DEFAULT_MAX_Y;
        if (cameraX >= AIZ2_KNUX_RESIZE2_BOSS_TRIGGER_X) {
            maxY = AIZ2_KNUX_RESIZE2_BOSS_MAX_Y;
        }
        camera().setMaxY((short) maxY);

        if (cameraX >= AIZ2_KNUX_RESIZE2_LOCK_X) {
            camera().setMinX((short) AIZ2_KNUX_RESIZE2_LOCK_X);
            if (!minibossSpawned) {
                spawnAiz2Miniboss(AIZ2_KNUX_BOSS_X, AIZ2_KNUX_BOSS_Y);
            }
            // ROM: set Target_water_level = $F80
            WaterSystem waterSystem = waterSystem();
            waterSystem.setWaterLevelTarget(0, 1, AIZ2_KNUX_WATER_LEVEL);
            aiz2ResizeRoutine = 0x16;
        }
    }

    /** ROM: AIZ2_KnuxResize3 — target maxY=$820 at camera X >= $11A0. */
    private void updateAiz2KnuxResize3() {
        if (camera().getX() < AIZ2_KNUX_RESIZE3_TRIGGER_X) {
            return;
        }
        camera().setMaxYTarget((short) AIZ2_KNUX_RESIZE3_TARGET_MAX_Y);
        aiz2ResizeRoutine = 0x18;
    }

    /** ROM: AIZ2_KnuxResize4 — battleship art load at camera X >= $3B80. */
    private void updateAiz2KnuxResize4() {
        if (camera().getX() < AIZ2_KNUX_RESIZE4_TRIGGER_X) {
            return;
        }
        ensureBattleshipTerrainLoaded();
        camera().setMinX((short) AIZ2_KNUX_RESIZE4_TRIGGER_X);
        camera().setMaxYTarget((short) AIZ2_KNUX_RESIZE4_TARGET_MAX_Y);
        eventsFg5 = true;
        // ROM: AIZ2BGE_Normal BG Y adjustment (same logic as Sonic path)
        int cameraY = camera().getY() & 0xFFFF;
        battleshipBgYOffset = (cameraY < 0x400) ? 0xA8 : -0x198;
        aiz2ResizeRoutine = 0x1A;
        LOG.info("AIZ2 Knux resize4: battleship art trigger at X=0x"
                + Integer.toHexString(camera().getX())
                + ", bgYOffset=" + battleshipBgYOffset);
    }

    /** ROM: AIZ2_KnuxResize5 — lock minX=$3F80 at camera X >= $3F80. */
    private void updateAiz2KnuxResize5() {
        if (camera().getX() < AIZ2_KNUX_RESIZE5_TRIGGER_X) {
            return;
        }
        camera().setMinX((short) AIZ2_KNUX_RESIZE5_TRIGGER_X);
        aiz2ResizeRoutine = 0x1C; // KnuxResizeEnd
    }

    /** Spawn the AIZ2 miniboss at the given position. */
    private void spawnAiz2Miniboss(int bossX, int bossY) {
        minibossSpawned = true;
        ObjectSpawn bossSpawn = new ObjectSpawn(bossX, bossY, 0x91, 0, 0, false, bossY);
        AizMinibossInstance boss = new AizMinibossInstance(bossSpawn);
        var objManager = levelManager().getObjectManager();
        if (objManager != null) {
            objManager.addDynamicObject(boss);
        }
        LOG.info("AIZ2 resize: spawned miniboss at (0x" + Integer.toHexString(bossX)
                + ", 0x" + Integer.toHexString(bossY) + ")");
    }

    // ===== Battleship bombing sequence =====

    /**
     * Starts the battleship bombing sequence: locks the camera, begins auto-scroll,
     * and spawns the battleship object.
     */
    private void startBattleshipSequence() {
        battleshipAutoScrollActive = true;
        ensureBattleshipTerrainLoaded();
        // ROM writes Pal_AIZBattleship to Normal_palette_line_2, which maps to
        // engine palette index 1 (palette 0 is the character line).
        loadPalette(1, Sonic3kConstants.PAL_AIZ_BATTLESHIP_ADDR);
        // Lock camera to current X (player can only move within the visible screen)
        int cameraX = camera().getX();
        // Initialize smooth scroll counter at current camera position (never wraps)
        battleshipSmoothScrollX = cameraX;
        camera().setMinX((short) cameraX);
        camera().setMaxX((short) cameraX);
        // Lock player control during the bombing sequence
        setTransitionControlLock(false); // Player can still run left/right

        if (!battleshipSpawned) {
            battleshipSpawned = true;
            int baseSecondaryY = camera().getY() + 0x08F0;
            ObjectSpawn shipSpawn = new ObjectSpawn(cameraX, baseSecondaryY, 0, 0, 0, false, 0);
            AizBattleshipInstance ship = new AizBattleshipInstance(shipSpawn, baseSecondaryY);
            var objManager = levelManager().getObjectManager();
            if (objManager != null) {
                objManager.addDynamicObject(ship);
            }
            LOG.info("AIZ2 battleship: spawned at cameraX=0x" + Integer.toHexString(cameraX));
        }
    }

    private void ensureBattleshipTerrainLoaded() {
        if (battleshipTerrainLoaded) {
            return;
        }
        Level level = levelManager().getCurrentLevel();
        if (!(level instanceof Sonic3kLevel sonic3kLevel)) {
            return;
        }

        try {
            ResourceLoader loader = new ResourceLoader(rom());
            byte[] shipBlocks16x16 = loader.loadSingle(
                    LoadOp.kosinskiBase(Sonic3kConstants.AIZ2_16X16_BOMBERSHIP_ADDR));
            byte[] shipTiles8x8 = loader.loadSingle(
                    LoadOp.kosinskiMBase(Sonic3kConstants.AIZ2_8X8_BOMBERSHIP_ADDR));

            sonic3kLevel.applyChunkOverlay(
                    shipBlocks16x16,
                    Sonic3kConstants.AIZ2_16X16_BOMBERSHIP_DEST_OFFSET,
                    false);
            sonic3kLevel.applyPatternOverlay(
                    shipTiles8x8,
                    Sonic3kConstants.AIZ2_8X8_BOMBERSHIP_DEST_BYTES,
                    false);
            loadPaletteFromPalPointers(PAL_AIZ_BOSS_INDEX);
            levelManager().invalidateAllTilemaps();
            battleshipTerrainLoaded = true;

            LOG.info("AIZ2 battleship: loaded terrain overlays (16x16="
                    + shipBlocks16x16.length + " bytes, 8x8=" + shipTiles8x8.length
                    + " bytes) and boss palette");
        } catch (IOException e) {
            LOG.warning("AIZ2 battleship: failed to load terrain overlays: " + e.getMessage());
        }
    }


    /**
     * Per-frame auto-scroll logic during the battleship bombing loop.
     * ROM: AIZ2_ScreenEvent auto-scroll handler.
     * Scrolls the camera right by {@link #BATTLESHIP_SCROLL_SPEED} pixels per frame
     * and wraps everything back by {@link #BATTLESHIP_WRAP_DIST} when the camera
     * reaches the wrap boundary.
     */
    private void updateBattleshipAutoScroll() {
        Camera cam = camera();
        int cameraX = cam.getX();

        // Clear per-frame wrap offset (ROM: Level_repeat_offset)
        levelRepeatOffset = 0;

        // Smooth scroll counter increments every frame without wrapping.
        // Used by the parallax scroll handler for continuous BG deformation.
        battleshipSmoothScrollX += BATTLESHIP_SCROLL_SPEED;

        // Auto-scroll right
        int newCameraX = cameraX + BATTLESHIP_SCROLL_SPEED;
        cam.setX((short) newCameraX);
        cam.setMinX((short) newCameraX);
        cam.setMaxX((short) newCameraX);

        // Wrap-back: when camera reaches the wrap boundary, subtract $200 from
        // ALL positions (camera, player, active objects) for seamless looping.
        if (newCameraX >= battleshipWrapX) {
            // Use shorter wrap distance in the post-bombing forest phase
            int wrapDelta = (battleshipWrapX == BATTLESHIP_WRAP_X_BOMBING)
                    ? BATTLESHIP_WRAP_DIST : BATTLESHIP_WRAP_DIST_POST_BOMBING;
            levelRepeatOffset = wrapDelta;

            cam.setX((short) (newCameraX - wrapDelta));
            cam.setMinX((short) (newCameraX - wrapDelta));
            cam.setMaxX((short) (newCameraX - wrapDelta));

            // Wrap the player position
            if (cam.getFocusedSprite() instanceof AbstractPlayableSprite player) {
                player.setX((short) (player.getX() - wrapDelta));
            }
            // Wrap sidekick positions
            for (AbstractPlayableSprite sidekick : spriteManager().getSidekicks()) {
                sidekick.setX((short) (sidekick.getX() - wrapDelta));
            }

            // Wrap all active bombing-sequence objects (ROM: Level_repeat_offset)
            var objManager = levelManager().getObjectManager();
            if (objManager != null) {
                for (var obj : objManager.getActiveObjects()) {
                    if (obj instanceof AizShipBombInstance bomb) {
                        bomb.applyWrapOffset(wrapDelta);
                    } else if (obj instanceof AizBombExplosionInstance explosion) {
                        explosion.applyWrapOffset(wrapDelta);
                    }
                }
            }

            LOG.fine("AIZ2 battleship: wrap-back at cameraX=0x"
                    + Integer.toHexString(newCameraX));
        }

        // Clamp player X within camera bounds during auto-scroll
        if (cam.getFocusedSprite() instanceof AbstractPlayableSprite player) {
            int camX = cam.getX();
            int minPlayerX = camX + PLAYER_LEFT_MARGIN;
            int maxPlayerX = camX + PLAYER_RIGHT_MARGIN;
            short playerX = player.getX();
            if (playerX < minPlayerX) {
                player.setX((short) minPlayerX);
            } else if (playerX > maxPlayerX) {
                player.setX((short) maxPlayerX);
            }
        }
    }

    /** ROM: Level_repeat_offset — non-zero on wrap frames, objects subtract this from X. */
    public int getLevelRepeatOffset() {
        return levelRepeatOffset;
    }

    /** True when the battleship auto-scroll loop is active. */
    public boolean isBattleshipAutoScrollActive() {
        return battleshipAutoScrollActive;
    }

    /**
     * ROM: AIZ2BGE_Normal one-time BG Y adjustment ($A8 or -$198).
     * Applied when eventsFg5 fires (resize4). The scroll handler adds this to vscrollFactorBG.
     */
    public int getBattleshipBgYOffset() {
        return battleshipBgYOffset;
    }

    // ROM: ScreenShakeArray — signed byte offsets indexed by countdown value (15→0).
    // Amplitude increases then decreases: ±1, ±1, ±2, ±2, ±3, ±3, ±4, ±4, ±5, ±5
    private static final int[] SCREEN_SHAKE_ARRAY = {
            1, -1, 1, -1, 2, -2, 2, -2, 3, -3, 3, -3, 4, -4, 4, -4, 5, -5, 5, -5
    };

    /**
     * ROM: move.w #$10,(Screen_shake_flag).w — trigger timed screen shake.
     * Called by bomb impact. Countdown applies Y offsets from ScreenShakeArray.
     */
    public void triggerScreenShake(int frames) {
        screenShakeTimer = frames;
    }

    public int getScreenShakeOffsetY() {
        return screenShakeOffsetY;
    }

    /**
     * Ticks the screen shake countdown. Called each frame from {@link #updateAct2Continuation()}.
     * Exposes the current ROM-style Y offset for the AIZ scroll handler.
     */
    private void tickScreenShake() {
        if (screenShakeTimer <= 0) {
            screenShakeOffsetY = 0;
            return;
        }
        screenShakeTimer--;
        screenShakeOffsetY = 0;
        if (screenShakeTimer < SCREEN_SHAKE_ARRAY.length) {
            screenShakeOffsetY = SCREEN_SHAKE_ARRAY[screenShakeTimer];
        }
    }

    /**
     * Returns the smooth (never-wrapping) scroll X for parallax during the bombing sequence.
     * The parallax scroll handler should use this instead of camera.getX() to avoid
     * visible background jumps when the camera wraps back by $200.
     */
    public int getBattleshipSmoothScrollX() {
        return battleshipSmoothScrollX;
    }

    /**
     * Called by {@link AizBattleshipInstance} when the ship has crossed the screen
     * and all bombs have been dropped. Spawns the small Eggman craft.
     */
    public void onBattleshipComplete() {
        // ROM: AIZ2SE_EndRefresh sets Events_bg+$02 = $46C0.
        // This moves the wrap into the forested area right before the boss arena.
        // In the ROM, the HInt screen-split hides the terrain seam at the wrap point;
        // without it there's a slight visual discontinuity, but the loop location is correct.
        battleshipWrapX = BATTLESHIP_WRAP_X_POST_BOMBING;

        var objManager = levelManager().getObjectManager();
        if (objManager != null) {
            AizBossSmallInstance smallBoss = new AizBossSmallInstance();
            objManager.addDynamicObject(smallBoss);

            // ROM: Obj_AIZ2MakeTree - spawner for parallax background trees
            AizBgTreeSpawnerInstance treeSpawner = new AizBgTreeSpawnerInstance();
            objManager.addDynamicObject(treeSpawner);
        }
        LOG.info("AIZ2 battleship: bombing complete, wrap boundary now 0x"
                + Integer.toHexString(battleshipWrapX)
                + ", spawned small boss craft and tree spawner");
    }

    /**
     * Called by {@link AizBossSmallInstance} when the small craft exits the screen.
     * Stops auto-scroll, unlocks camera boundaries, allows the player to proceed
     * to the end boss arena.
     */
    public void onBossSmallComplete() {
        battleshipAutoScrollActive = false;

        // Unlock camera: set maxX to end of level / boss arena
        camera().setMaxX((short) BATTLESHIP_END_CAMERA_MAX_X);
        // Release minX — keep it where it is so player can't go backwards,
        // but don't force it to the current scroll position anymore.

        LOG.info("AIZ2 battleship: small boss exited, camera unlocked to maxX=0x"
                + Integer.toHexString(BATTLESHIP_END_CAMERA_MAX_X));
    }

    private void advanceFireRise(boolean allowInitialLerp) {
        boolean fireRiseEnabled = true;
        if (allowInitialLerp && fireRiseSpeed == 0) {
            int delta = (FIRE_BG_TARGET - fireBgCopyFixed) >> FIRE_BG_LERP_SHIFT;
            fireBgCopyFixed += delta;
            fireRiseEnabled = Integer.compareUnsigned(delta, FIRE_BG_LERP_MIN_DELTA) < 0;
            if (fireRiseEnabled) {
                LOG.info("AIZ1 fire: lerp complete, fire rise starting at frame " + fireTransitionFrames);
            }
        }

        if (fireRiseEnabled) {
            fireRiseSpeed = Math.min(FIRE_RISE_MAX_SPEED, fireRiseSpeed + FIRE_RISE_ACCEL);
            fireBgCopyFixed += (fireRiseSpeed << 4);
        }

        fireWavePhase = (fireWavePhase + FIRE_WAVE_PHASE_STEP) & 0xFFFF;
        fireTransitionFrames++;

        if (fireTransitionFrames % 60 == 0) {
            LOG.info("AIZ fire: phase=" + fireSequencePhase
                    + " frame=" + fireTransitionFrames
                    + " bgY=0x" + Integer.toHexString(getFireTransitionBgY())
                    + " speed=0x" + Integer.toHexString(fireRiseSpeed)
                    + " coverPx=" + getFireWallCoverHeightPx(224));
        }
    }

    private void updateFireTransition() {
        if (fireSequencePhase == FireSequencePhase.INACTIVE) {
            if (eventsFg5) {
                beginFireTransition();
            }
            return;
        }

        switch (fireSequencePhase) {
            case AIZ1_FIRE_TRANSITION -> {
                advanceFireRise(true);
                int fireBgY = getFireTransitionBgY();
                if (!fireTransitionMutationRequested && fireBgY >= FIRE_BG_MUTATION_Y) {
                    applyFireTransitionMutation();
                    fireSequencePhase = FireSequencePhase.AIZ1_FIRE_REFRESH;
                    firePhaseFrames = 0;
                    setTransitionControlLock(true);
                } else if (fireTransitionFrames >= FIRE_TRANSITION_FALLBACK_FRAMES) {
                    applyFireTransitionMutation();
                    fireSequencePhase = FireSequencePhase.AIZ1_FIRE_REFRESH;
                    firePhaseFrames = 0;
                    setTransitionControlLock(true);
                }
            }
            case AIZ1_FIRE_REFRESH -> {
                advanceFireRise(false);
                firePhaseFrames++;
                if (firePhaseFrames >= FIRE_REDRAW_FRAMES) {
                    fireSequencePhase = FireSequencePhase.AIZ1_FINISH;
                    firePhaseFrames = 0;
                }
            }
            case AIZ1_FINISH -> {
                // Fire covers the screen while the level transitions behind it.
                // Linger with looping fire, then request transition mid-fire.
                // The fire persists through the reload (deactivateLevelNow=false)
                // and scrolls off in act 2 to reveal the new terrain.
                advanceFireRise(false);
                firePhaseFrames++;
                if (firePhaseFrames >= FIRE_LINGER_FRAMES && !act2TransitionRequested) {
                    requestAct2Transition();
                }
            }
            default -> {
                // Act 2 continuation is advanced by updateAct2Continuation().
            }
        }
    }

    private void beginFireTransition() {
        eventsFg5 = false;
        fireSequencePhase = FireSequencePhase.AIZ1_FIRE_TRANSITION;
        fireBgCopyFixed = FIRE_BG_FIXED_START;
        fireRiseSpeed = 0;
        fireWavePhase = 0;
        fireTransitionFrames = 0;
        firePhaseFrames = 0;
        act2WaitFireDrawActive = false;

        fireTransitionMutationRequested = false;
        act2TransitionRequested = false;
        postFireHazeActive = false;
        // ROM: AIZ1_AIZ2_Transition does NOT lock controls at fire start.
        // Controls are locked later when the fire covers the full screen (REFRESH phase).
        // ROM: AIZ1_AIZ2_Transition writes 6 fire words to Normal_palette_line_4+$2
        // at the START of the fire transition. The full fire palette (PalPointers #$0B)
        // is loaded later by the mutation executor when bgY >= $190.
        applyFireTransitionPaletteLine4(levelManager());
        ensureFireOverlayTilesLoaded();
        LOG.info("AIZ1: fire transition started");
    }

    /**
     * Apply fire transition art overlays directly (in-place).
     * ROM: AIZ1BGE_FireTransition at Camera_Y_pos_BG_copy >= $190 queues
     * the AIZ2 art overlays via Queue_Kos / Queue_Kos_Module.  The ROM does
     * this purely through the DMA queue without touching camera bounds.
     * We apply the mutation directly to avoid the side-effects of routing
     * through requestSeamlessTransition(MUTATE_ONLY), which calls
     * restoreCameraBoundsForCurrentLevel() and camera.updatePosition(true),
     * undoing the boss arena camera lock.
     */
    private void applyFireTransitionMutation() {
        fireTransitionMutationRequested = true;
        S3kSeamlessMutationExecutor.apply(
                levelManager(),
                S3kSeamlessMutationExecutor.MUTATION_AIZ1_FIRE_TRANSITION_STAGE);
        LOG.info("AIZ1: applied in-place fire mutation stage (direct)");
    }

    private void requestAct2Transition() {
        act2TransitionRequested = true;
        eventsFg5 = false;
        bossFlag = false;
        postFireHazeActive = false;
        gameState().setCurrentBossId(0);
        if (!fireTransitionMutationRequested) {
            applyFireTransitionMutation();
        }
        // Reset BG_Y to within the fire zone so the act 2 scroll-off works.
        // During the linger, BG_Y advanced well past the fire zone (wrapping
        // handled the visuals).  For act 2, the fire needs to start within
        // the zone and scroll off the top naturally (wrapping disabled).
        // 0x1E0 gives full-screen fire that scrolls off over ~19 frames.
        int scrollOffStartY = 0x01E0_0000;
        pendingFireSequence = new PendingFireSequence(
                FireSequencePhase.AIZ2_FIRE_REDRAW,
                scrollOffStartY,
                fireRiseSpeed,
                fireWavePhase,
                fireTransitionFrames,
                0,
                fireTransitionMutationRequested,
                false);
        persistTransitionCheckpoint();
        LevelManager levelManager = levelManager();
        levelManager().requestSeamlessTransition(
                SeamlessLevelTransitionRequest.builder(SeamlessLevelTransitionRequest.TransitionType.RELOAD_TARGET_LEVEL)
                        // ROM loads AIZ act 2 resources here, but this is presented as
                        // a seamless continuation (no title card transition).
                        .targetZoneAct(levelManager.getCurrentZone(), 1)
                        // ROM: level stays active during the Kos decompression
                        // wait in AIZ1BGE_Finish — fire keeps rising and rendering.
                        // deactivateLevelNow(true) freezes the game loop, killing all
                        // fire state machine updates.  Keep the level active so the
                        // fire overlay continues rendering through the transition.
                        // Player controls are already locked (set during REFRESH).
                        .deactivateLevelNow(false)
                        .preserveMusic(false)
                        .showInLevelTitleCard(false)
                        .mutationKey(S3kSeamlessMutationExecutor.MUTATION_AIZ1_POST_RELOAD_ACT2)
                        .musicOverrideId(Sonic3kMusic.AIZ1.id)
                        .playerOffset(-0x2F00, -0x80)
                        .cameraOffset(-0x2F00, -0x80)
                        .build());
        LOG.info("AIZ1: requested seamless in-place post-miniboss reload");
    }

    public int getFireWallCoverHeightPx(int screenHeight) {
        int fireBgY = getFireTransitionBgY();
        // Fire tiles in the BG layout start at Y = FIRE_TILE_START_Y (0x100).
        // The VDP screen shows BG from bgY to bgY + screenHeight.  Fire is visible
        // at the bottom: cover = bgY + screenHeight - FIRE_TILE_START_Y.
        int cover = fireBgY + screenHeight - FIRE_TILE_START_Y;
        if (cover <= 0) {
            return 0;
        }
        return Math.min(cover, screenHeight);
    }

    private void setTransitionControlLock(boolean locked) {
        if (camera().getFocusedSprite() instanceof AbstractPlayableSprite player) {
            player.setControlLocked(locked);
        }
        for (AbstractPlayableSprite sidekick : spriteManager().getSidekicks()) {
            sidekick.setControlLocked(locked);
        }
    }

    private void ensureFireOverlayTilesLoaded() {
        if (fireOverlayTilesLoaded) {
            return;
        }
        LevelManager levelManager = levelManager();
        if (!(levelManager.getCurrentLevel() instanceof Sonic3kLevel sonic3kLevel)) {
            return;
        }
        try {
            Rom rom = rom();
            if (rom == null) {
                return;
            }
            ResourceLoader loader = new ResourceLoader(rom);
            byte[] fireOverlay8x8 = loader.loadSingle(
                    LoadOp.kosinskiMBase(Sonic3kConstants.ART_KOSM_AIZ1_FIRE_OVERLAY_ADDR));
            fireOverlayTileCount = fireOverlay8x8.length / Pattern.PATTERN_SIZE_IN_ROM;
            int destOffset = FIRE_OVERLAY_TILE_DEST * Pattern.PATTERN_SIZE_IN_ROM;
            sonic3kLevel.applyPatternOverlay(fireOverlay8x8, destOffset, false);
            applyPlc(FIRE_OVERLAY_PLC);
            levelManager.invalidateAllTilemaps();
            fireOverlayTilesLoaded = true;
            LOG.info("AIZ1: loaded fire overlay 8x8 tiles at x>=0x2E00");
        } catch (Exception e) {
            LOG.warning("AIZ1: failed to load fire overlay tiles: " + e.getMessage());
        }
    }

    private void restorePendingFireSequenceIfPresent(int act) {
        if (act != 1) {
            return;
        }
        PendingFireSequence pending = pendingFireSequence;
        if (pending == null) {
            // No pending fire sequence → entered AIZ2 directly (level select,
            // death restart).  ROM: Apparent_zone_and_act == AIZ2 here.
            enteredAsAct2 = true;
            postFireHazeActive = true;
            return;
        }

        fireSequencePhase = pending.phase();
        fireBgCopyFixed = pending.fireBgCopyFixed();
        fireRiseSpeed = pending.fireRiseSpeed();
        fireWavePhase = pending.fireWavePhase();
        fireTransitionFrames = pending.fireTransitionFrames();
        firePhaseFrames = pending.firePhaseFrames();
        fireTransitionMutationRequested = pending.mutationRequested();
        act2WaitFireDrawActive = pending.act2WaitFireDrawActive();
        postFireHazeActive = false;
        act2TransitionRequested = false;
        setTransitionControlLock(true);
        // Reload fire overlay tiles from ROM — they were lost during the act 2 level reload.
        fireOverlayTilesLoaded = false;
        ensureFireOverlayTilesLoaded();
        // Re-apply fire palette after act 2 reload so palette line 3 has fire colors.
        // The level reload loads the normal AIZ2 palette which may not match the
        // fire transition state; PalPointers #$0B + fire line 4 words restore it.
        loadPaletteFromPalPointers(PAL_POINTER_AIZ_FIRE_INDEX);
        applyFireTransitionPaletteLine4(levelManager());
        LOG.info("AIZ2 fake-out: resumed fire continuation phase " + fireSequencePhase);
    }

    static void applyFireTransitionPaletteLine4(LevelManager levelManager) {
        applyPaletteLine4Words(levelManager, FIRE_TRANSITION_LINE4_WORDS);
    }

    static void applyPostFireContinuationPaletteLine4(LevelManager levelManager) {
        applyPaletteLine4Words(levelManager, POST_FIRE_LINE4_WORDS);
    }

    private static void applyPaletteLine4Words(LevelManager levelManager, int[] line4Words) {
        if (levelManager == null || line4Words == null) {
            return;
        }
        Level currentLevel = levelManager.getCurrentLevel();
        if (currentLevel == null) {
            return;
        }
        Palette line4 = currentLevel.getPalette(3);
        if (line4 == null) {
            return;
        }

        byte[] lineData = new byte[Palette.PALETTE_SIZE_IN_ROM];
        for (int i = 0; i < Palette.PALETTE_SIZE; i++) {
            int sega = toSegaColorWord(line4.getColor(i));
            int offset = i * 2;
            lineData[offset] = (byte) ((sega >>> 8) & 0xFF);
            lineData[offset + 1] = (byte) (sega & 0xFF);
        }

        for (int i = 0; i < line4Words.length; i++) {
            int offset = (i + 1) * 2;
            int word = line4Words[i];
            lineData[offset] = (byte) ((word >>> 8) & 0xFF);
            lineData[offset + 1] = (byte) (word & 0xFF);
        }
        levelManager.updatePalette(3, lineData);
    }

    private static int toSegaColorWord(Palette.Color color) {
        if (color == null) {
            return 0;
        }
        int r3 = ((color.r & 0xFF) * 7 + 127) / 255;
        int g3 = ((color.g & 0xFF) * 7 + 127) / 255;
        int b3 = ((color.b & 0xFF) * 7 + 127) / 255;
        return ((b3 & 0x7) << 9) | ((g3 & 0x7) << 5) | ((r3 & 0x7) << 1);
    }

    private void persistTransitionCheckpoint() {
        if (!(levelManager().getCheckpointState() instanceof CheckpointState checkpoint)) {
            return;
        }
        Camera cam = camera();
        if (cam.getFocusedSprite() == null) {
            return;
        }
        int x = cam.getFocusedSprite().getCentreX() & 0xFFFF;
        int y = cam.getFocusedSprite().getCentreY() & 0xFFFF;
        checkpoint.saveCheckpoint(0, x, y, false);
    }
}
