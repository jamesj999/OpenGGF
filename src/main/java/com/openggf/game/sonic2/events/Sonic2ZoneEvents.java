package com.openggf.game.sonic2.events;

import com.openggf.camera.Camera;
import com.openggf.audio.AudioManager;
import com.openggf.data.Rom;
import com.openggf.game.GameServices;
import com.openggf.game.GameStateManager;
import com.openggf.level.LevelManager;
import com.openggf.level.ParallaxManager;
import com.openggf.level.WaterSystem;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.io.IOException;
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

    protected int eventRoutine;
    protected int bossSpawnDelay;

    protected Sonic2ZoneEvents() {
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

    protected AudioManager audio() {
        return GameServices.audio();
    }

    protected GameStateManager gameState() {
        return GameServices.gameState();
    }

    protected WaterSystem waterSystem() {
        return GameServices.water();
    }

    protected ParallaxManager parallax() {
        return GameServices.parallax();
    }

    protected SpriteManager spriteManager() {
        return GameServices.sprites();
    }

    protected Rom rom() throws IOException {
        return GameServices.rom().getRom();
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
     * Loads a boss palette from ROM and applies it to the specified palette line.
     * ROM equivalent: PalLoad_Now
     */
    protected static void loadBossPalette(int paletteLine, int romAddr) {
        try {
            Rom rom = GameServices.rom().getRom();
            LevelManager levelManager = GameServices.level();
            byte[] paletteData = rom.readBytes(romAddr, PALETTE_LINE_SIZE);
            levelManager.updatePalette(paletteLine, paletteData);
        } catch (Exception e) {
            LOGGER.warning("Failed to load boss palette from ROM offset 0x" +
                    Integer.toHexString(romAddr) + ": " + e.getMessage());
        }
    }

    protected void setSidekickBounds(Integer minX, Integer maxX, Integer maxY) {
        for (AbstractPlayableSprite sidekick : spriteManager().getSidekicks()) {
            if (sidekick.getCpuController() != null) {
                sidekick.getCpuController().setLevelBounds(minX, maxX, maxY);
            }
        }
    }

    protected void syncSidekickBoundsToCamera() {
        Camera cam = camera();
        setSidekickBounds((int) cam.getMinX(), (int) cam.getMaxX(),
                (int) Math.max(cam.getMaxY(), cam.getMaxYTarget()));
    }
}
