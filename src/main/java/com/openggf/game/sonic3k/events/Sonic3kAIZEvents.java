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
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;

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

    private final Sonic3kLoadBootstrap bootstrap;
    private boolean introSpawned;
    /** One-shot guard: once AIZ intro minX is locked at $1308, stop rewriting minX each frame. */
    private boolean introMinXLocked;
    private boolean paletteSwapped;
    private boolean boundariesUnlocked;
    // Tracks one-shot application of AIZ1SE_ChangeChunk4/3/2/1.
    private int appliedTreeRevealChunkCopiesMask;

    // --- Boss / fire transition state ---
    /** One-shot guard for AIZ2 resize boss spawn after fire transition completes. */
    private boolean minibossSpawned;
    /** ROM: (Events_fg_5).w - set by boss exit sequence to trigger fire transition. */
    private boolean eventsFg5;
    /** Boss_flag equivalent - set when boss is present, cleared on cleanup. */
    private boolean bossFlag;
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
        eventsFg5 = false;
        bossFlag = false;
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
        LevelManager levelManager = LevelManager.getInstance();
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

        LevelManager levelManager = LevelManager.getInstance();
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
                fireOverlayTileCount);
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
                    advanceFireRise(false);
                    firePhaseFrames++;
                    if (firePhaseFrames >= FIRE_REDRAW_FRAMES) {
                        fireSequencePhase = FireSequencePhase.AIZ2_WAIT_FIRE;
                        firePhaseFrames = 0;
                        act2WaitFireDrawActive = true;
                    }
                }
                case AIZ2_WAIT_FIRE -> {
                    advanceFireRise(false);
                    int fireBgY = getFireTransitionBgY();
                    int mod = fireBgY & 0x7F;
                    if (mod >= 0x20 && mod < 0x30) {
                        int snappedY = mod + 0x180;
                        fireBgCopyFixed = (snappedY << 16) | (fireBgCopyFixed & 0xFFFF);
                    }
                    if (getFireTransitionBgY() >= FIRE_BG_FINISH_Y) {
                        applyPostFireContinuationPaletteLine4(LevelManager.getInstance());
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

        // AIZ2 resize: spawn real miniboss when camera reaches threshold.
        // ROM: AIZ2_SonicResize2 (line 39076) / AIZ2_KnuxResize2 (line 39187)
        if (!minibossSpawned && fireSequencePhase == FireSequencePhase.COMPLETE) {
            PlayerCharacter character = Sonic3kLevelEventManager.getInstance().getPlayerCharacter();
            int cameraTrigger = (character == PlayerCharacter.KNUCKLES) ? 0x1040 : 0x0F50;

            if (Camera.getInstance().getX() >= cameraTrigger) {
                minibossSpawned = true;
                int bossX = (character == PlayerCharacter.KNUCKLES) ? 0x11D0 : 0x11F0;
                int bossY = (character == PlayerCharacter.KNUCKLES) ? 0x0420 : 0x0289;

                ObjectSpawn bossSpawn = new ObjectSpawn(bossX, bossY, 0x91, 0, 0, false, bossY);
                AizMinibossInstance boss = new AizMinibossInstance(bossSpawn, LevelManager.getInstance());
                LevelManager.getInstance().getObjectManager().addDynamicObject(boss);
                LOG.info("AIZ2 resize: spawned miniboss at (0x" + Integer.toHexString(bossX)
                        + ", 0x" + Integer.toHexString(bossY) + ")");
            }
        }
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
                advanceFireRise(false);
                if (!act2TransitionRequested) {
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
        applyFireTransitionPaletteLine4(LevelManager.getInstance());
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
                LevelManager.getInstance(),
                S3kSeamlessMutationExecutor.MUTATION_AIZ1_FIRE_TRANSITION_STAGE);
        LOG.info("AIZ1: applied in-place fire mutation stage (direct)");
    }

    private void requestAct2Transition() {
        act2TransitionRequested = true;
        eventsFg5 = false;
        bossFlag = false;
        postFireHazeActive = false;
        GameServices.gameState().setCurrentBossId(0);
        if (!fireTransitionMutationRequested) {
            applyFireTransitionMutation();
        }
        pendingFireSequence = new PendingFireSequence(
                FireSequencePhase.AIZ2_FIRE_REDRAW,
                fireBgCopyFixed,
                fireRiseSpeed,
                fireWavePhase,
                fireTransitionFrames,
                0,
                fireTransitionMutationRequested,
                false);
        persistTransitionCheckpoint();
        LevelManager levelManager = LevelManager.getInstance();
        LevelManager.getInstance().requestSeamlessTransition(
                SeamlessLevelTransitionRequest.builder(SeamlessLevelTransitionRequest.TransitionType.RELOAD_TARGET_LEVEL)
                        // ROM loads AIZ act 2 resources here, but this is presented as
                        // a seamless continuation (no title card transition).
                        .targetZoneAct(levelManager.getCurrentZone(), 1)
                        .deactivateLevelNow(true)
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
        AbstractPlayableSprite sidekick = SpriteManager.getInstance().getSidekick();
        if (sidekick != null) {
            sidekick.setControlLocked(locked);
        }
    }

    private void ensureFireOverlayTilesLoaded() {
        if (fireOverlayTilesLoaded) {
            return;
        }
        LevelManager levelManager = LevelManager.getInstance();
        if (!(levelManager.getCurrentLevel() instanceof Sonic3kLevel sonic3kLevel)) {
            return;
        }
        try {
            Rom rom = GameServices.rom().getRom();
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
        applyFireTransitionPaletteLine4(LevelManager.getInstance());
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
        if (!(LevelManager.getInstance().getCheckpointState() instanceof CheckpointState checkpoint)) {
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
