package com.openggf.game.sonic3k.events;

import com.openggf.camera.Camera;
import com.openggf.audio.AudioManager;
import com.openggf.data.Rom;
import com.openggf.game.GameServices;
import com.openggf.game.GameStateManager;
import com.openggf.game.sonic3k.Sonic3kLevel;
import com.openggf.game.sonic3k.Sonic3kPlcLoader;
import com.openggf.level.resources.PlcParser.PlcDefinition;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import com.openggf.level.WaterSystem;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.sprites.managers.SpriteManager;

import java.io.IOException;
import java.util.List;
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

    protected int eventRoutine;
    protected int bossSpawnDelay;

    protected Sonic3kZoneEvents() {
    }

    /**
     * Returns the current Camera singleton. Always call this accessor rather
     * than caching the reference, so it survives singleton replacement.
     */
    protected Camera camera() {
        return GameServices.camera();
    }

    protected LevelManager levelManager() {
        return GameServices.level();
    }

    protected Rom rom() throws IOException {
        return GameServices.rom().getRom();
    }

    protected AudioManager audio() {
        return GameServices.audio();
    }

    protected GameStateManager gameState() {
        return GameServices.gameState();
    }

    protected WaterSystem waterSystem() {
        return GameServices.water();
    }

    protected SpriteManager spriteManager() {
        return GameServices.sprites();
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
        LevelManager lm = levelManager();
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
            Rom rom = GameServices.rom().getRom();
            LevelManager levelManager = GameServices.level();
            byte[] paletteData = rom.readBytes(romAddr, PALETTE_LINE_SIZE);
            levelManager.updatePalette(paletteLine, paletteData);
        } catch (Exception e) {
            LOGGER.warning("Failed to load palette from ROM offset 0x" +
                    Integer.toHexString(romAddr) + ": " + e.getMessage());
        }
    }

    /**
     * Parses and applies a Pattern Load Cue to the current level, then
     * refreshes any affected object renderers' GPU textures.
     * ROM equivalent: Load_PLC (append) or Load_PLC_2 (clear+load).
     *
     * @param plcId the PLC ID (0x00–0x7B)
     */
    protected static void applyPlc(int plcId) {
        try {
            LevelManager levelManager = GameServices.level();
            Level level = levelManager.getCurrentLevel();
            if (!(level instanceof Sonic3kLevel sonic3kLevel)) {
                return;
            }
            Rom rom = GameServices.rom().getRom();
            PlcDefinition plc = Sonic3kPlcLoader.parsePlc(rom, plcId);
            List<Sonic3kPlcLoader.TileRange> modified = Sonic3kPlcLoader.applyToLevel(plc, sonic3kLevel);
            Sonic3kPlcLoader.refreshAffectedRenderers(modified, levelManager);
        } catch (Exception e) {
            LOGGER.warning(String.format("Failed to apply PLC 0x%02X: %s", plcId, e.getMessage()));
        }
    }

    protected static void loadPaletteFromPalPointers(int palPointersIndex) {
        try {
            Rom rom = GameServices.rom().getRom();
            int entryAddr = Sonic3kConstants.PAL_POINTERS_ADDR
                    + palPointersIndex * Sonic3kConstants.PAL_POINTER_ENTRY_SIZE;
            int sourceAddr = rom.read32BitAddr(entryAddr) & 0x00FFFFFF;
            int ramDest = rom.read16BitAddr(entryAddr + 4) & 0xFFFF;
            int countMinusOne = rom.read16BitAddr(entryAddr + 6) & 0xFFFF;
            int byteCount = (countMinusOne + 1) * 4; // dbf count → longword count → bytes

            // Convert RAM destination to palette line index.
            // Normal_palette = $FC00; each line = $20 (32) bytes.
            // Line index = low byte of ramDest / 32.
            int startLine = (ramDest & 0xFF) / PALETTE_LINE_SIZE;
            int lineCount = byteCount / PALETTE_LINE_SIZE;

            byte[] data = rom.readBytes(sourceAddr, byteCount);
            LevelManager levelManager = GameServices.level();
            for (int i = 0; i < lineCount; i++) {
                byte[] lineData = new byte[PALETTE_LINE_SIZE];
                System.arraycopy(data, i * PALETTE_LINE_SIZE, lineData, 0, PALETTE_LINE_SIZE);
                levelManager.updatePalette(startLine + i, lineData);
            }
            LOGGER.info("Loaded palette #" + palPointersIndex + ": " + lineCount
                    + " lines from 0x" + Integer.toHexString(sourceAddr)
                    + " to line " + startLine);
        } catch (Exception e) {
            LOGGER.warning("Failed to load palette from PalPointers index "
                    + palPointersIndex + ": " + e.getMessage());
        }
    }
}
