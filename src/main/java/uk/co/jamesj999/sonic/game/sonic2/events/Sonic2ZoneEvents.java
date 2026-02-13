package uk.co.jamesj999.sonic.game.sonic2.events;

import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.game.GameServices;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.ObjectInstance;

import java.util.logging.Logger;

/**
 * Base class for Sonic 2 per-zone dynamic level events.
 * Each zone has its own event routine counter (ROM: eventRoutine)
 * that tracks progress through Act 2 boss sequences.
 */
public abstract class Sonic2ZoneEvents {
    private static final Logger LOGGER = Logger.getLogger(Sonic2ZoneEvents.class.getName());

    /** VDP palette line size: 16 colors × 2 bytes each = 32 bytes */
    private static final int PALETTE_LINE_SIZE = 32;

    protected final Camera camera;
    protected int eventRoutine;
    protected int bossSpawnDelay;

    protected Sonic2ZoneEvents(Camera camera) {
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
     * Loads a boss palette from ROM and applies it to the specified palette line.
     * ROM equivalent: PalLoad_Now
     */
    protected static void loadBossPalette(int paletteLine, int romAddr) {
        try {
            byte[] paletteData = GameServices.rom().getRom().readBytes(romAddr, PALETTE_LINE_SIZE);
            LevelManager.getInstance().updatePalette(paletteLine, paletteData);
        } catch (Exception e) {
            LOGGER.warning("Failed to load boss palette from ROM offset 0x" +
                    Integer.toHexString(romAddr) + ": " + e.getMessage());
        }
    }
}
