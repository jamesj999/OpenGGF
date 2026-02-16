package uk.co.jamesj999.sonic.game.sonic3k.events;

import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.game.sonic3k.Sonic3kLoadBootstrap;
import uk.co.jamesj999.sonic.game.sonic3k.objects.AizPlaneIntroInstance;
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

    /** Camera X threshold for palette swap (routine 0). */
    private static final int PALETTE_SWAP_X = 0x1308;

    /** PalPointers index for Pal_AIZ (main AIZ palette, 3 lines → palette 1-3). */
    private static final int PAL_AIZ_INDEX = 0x2A;

    /** Camera X threshold for terrain swap (routine 2). Already handled by AizPlaneIntroInstance. */
    private static final int TERRAIN_SWAP_X = 0x1400;

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
        if (act == 0) {
            AizPlaneIntroInstance.resetIntroPhaseState();
        }
        if (shouldSpawnIntro(act)) {
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

        // --- Routine 0: Palette swap at camera X >= $1308 ---
        if (!paletteSwapped && cameraX >= PALETTE_SWAP_X) {
            loadPaletteFromPalPointers(PAL_AIZ_INDEX);
            paletteSwapped = true;
            LOG.info("AIZ1: loaded main palette (PalPointers #0x2A) at cameraX=0x"
                    + Integer.toHexString(cameraX));
        }

        // --- Routine 2: Terrain swap at camera X >= $1400 ---
        // Handled by AizPlaneIntroInstance.updateMainLevelPhaseForCameraX()
        if (shouldSpawnIntro(0)) {
            AizPlaneIntroInstance.updateMainLevelPhaseForCameraX(cameraX);
        }

        // --- Routine 4: Y boundary unlock + dynamic max Y ---
        if (AizPlaneIntroInstance.isMainLevelPhaseActive() && !boundariesUnlocked) {
            camera.setMinY((short) 0);
            boundariesUnlocked = true;
            LOG.info("AIZ1: unlocked Y boundaries (minY=0)");
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
}
