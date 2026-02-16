package uk.co.jamesj999.sonic.game.sonic3k.events;

import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.game.GameServices;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.ObjectInstance;

import java.util.logging.Logger;

/**
 * Base class for Sonic 3&K per-zone dynamic level events.
 * ROM equivalent: ScreenEvents / BackgroundEvent handler tables.
 *
 * Each zone has its own event routine counter that tracks progress
 * through zone-specific sequences (intro cinematics, boss arenas,
 * dynamic boundary changes, etc.).
 *
 * S3K zones may use dual FG/BG event routines with a stride of 4,
 * and branch on player character (Sonic/Tails vs Knuckles).
 * Subclasses implement per-zone logic in {@link #update(int, int)}.
 */
public abstract class Sonic3kZoneEvents {
    private static final Logger LOGGER = Logger.getLogger(Sonic3kZoneEvents.class.getName());

    /** VDP palette line size: 16 colors x 2 bytes each = 32 bytes */
    private static final int PALETTE_LINE_SIZE = 32;

    protected final Camera camera;
    protected int eventRoutine;
    protected int bossSpawnDelay;

    protected Sonic3kZoneEvents(Camera camera) {
        this.camera = camera;
    }

    /** Reset event state for a new level load. */
    public void init(int act) {
        eventRoutine = 0;
        bossSpawnDelay = 0;
    }

    /** Run per-frame event logic for the given act. */
    public abstract void update(int act, int frameCounter);

    public int getEventRoutine() {
        return eventRoutine;
    }

    public void setEventRoutine(int routine) {
        this.eventRoutine = routine;
    }

    /** Spawn a dynamic object into the level. */
    protected void spawnObject(ObjectInstance object) {
        LevelManager lm = LevelManager.getInstance();
        if (lm.getObjectManager() != null) {
            lm.getObjectManager().addDynamicObject(object);
        }
    }

    /**
     * Loads a palette from ROM and applies it to the specified palette line.
     * ROM equivalent: PalLoad_Now
     */
    protected static void loadPalette(int paletteLine, int romAddr) {
        try {
            byte[] paletteData = GameServices.rom().getRom().readBytes(romAddr, PALETTE_LINE_SIZE);
            LevelManager.getInstance().updatePalette(paletteLine, paletteData);
        } catch (Exception e) {
            LOGGER.warning("Failed to load palette from ROM offset 0x" +
                    Integer.toHexString(romAddr) + ": " + e.getMessage());
        }
    }
}
