package uk.co.jamesj999.sonic.game.sonic3k.events;

import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.game.sonic3k.Sonic3kLoadBootstrap;
import uk.co.jamesj999.sonic.game.sonic3k.objects.AizHollowTreeObjectInstance;
import uk.co.jamesj999.sonic.game.sonic3k.objects.AizPlaneIntroInstance;
import uk.co.jamesj999.sonic.level.LevelConstants;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;

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
 * Act 2: Boss arena + fire transition (future work).
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

    /** Camera X threshold for terrain swap (routine 2). Already handled by AizPlaneIntroInstance. */
    private static final int TERRAIN_SWAP_X = 0x1400;
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
    private boolean paletteSwapped;
    private boolean boundariesUnlocked;
    // Tracks one-shot application of AIZ1SE_ChangeChunk4/3/2/1.
    private int appliedTreeRevealChunkCopiesMask;

    public Sonic3kAIZEvents(Camera camera, Sonic3kLoadBootstrap bootstrap) {
        super(camera);
        this.bootstrap = bootstrap;
    }

    @Override
    public void init(int act) {
        super.init(act);
        introSpawned = false;
        paletteSwapped = false;
        boundariesUnlocked = false;
        appliedTreeRevealChunkCopiesMask = 0;
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
        }
    }

    private void updateAct1(int frameCounter) {
        // Spawn intro object (one-shot)
        if (!introSpawned && shouldSpawnIntro(0)) {
            spawnIntroObject();
            introSpawned = true;
        }

        int cameraX = camera.getX();
        applyHollowTreeScreenEvent(cameraX);

        // --- Routine 0→1: MinX tracking during intro panning ---
        // ROM (s3.asm AIZ1_Resize Stage 0): Once camera X >= $1000,
        // Camera_min_X_pos tracks camera X to prevent backtracking.
        // At camera X >= $1308 (Stage 1): lock Camera_min_X_pos = $1308.
        if (shouldSpawnIntro(0)) {
            if (cameraX >= PALETTE_SWAP_X) {
                camera.setMinX((short) PALETTE_SWAP_X);
            } else if (cameraX >= MIN_X_TRACK_START) {
                camera.setMinX((short) cameraX);
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

        // --- Routine 4: Y boundary unlock + dynamic max Y ---
        // Also re-apply main palette here to overwrite cutscene residue:
        // applyEmeraldPalette() (at player X=$13D0 during routine26Explode)
        // writes directly to GPU line 3, bypassing level.setPalette(), which
        // overwrites the Pal_AIZ loaded at $1308. Re-applying at $1400
        // ensures lines 1-3 are correct for the main level.
        if (AizPlaneIntroInstance.isMainLevelPhaseActive() && !boundariesUnlocked) {
            loadPaletteFromPalPointers(PAL_AIZ_INDEX);
            camera.setMinY((short) 0);
            boundariesUnlocked = true;
            // Title card is spawned by CutsceneKnucklesAiz1Instance.routine12Exit()
            // when Knuckles goes offscreen, matching the ROM's Obj_TitleCard allocation.
            LOG.info("AIZ1: unlocked Y boundaries (minY=0), re-applied main palette");
        }
        if (boundariesUnlocked) {
            resizeMaxYFromX(cameraX);
        }
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
                camera.setMaxY((short) maxY);
                return;
            }
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
        int cameraYRounded = camera.getY() & ~0xF;
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
}
