package com.openggf.game;

import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Level;
import com.openggf.level.Pattern;
import com.openggf.level.objects.HudRenderManager;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.animation.SpriteAnimationSet;

import java.io.IOException;
import java.util.List;

/**
 * Provider interface for game-specific object art.
 * Abstracts the loading and access of object sprites, animations, and related data
 * to support multiple games (Sonic 1, Sonic 2, Sonic 3&K, etc.).
 * <p>
 * Implementations wrap game-specific art loaders and expose renderers/sheets via
 * string keys for flexible lookup.
 */
public interface ObjectArtProvider {

    /**
     * Loads object art for the specified zone.
     *
     * @param zoneIndex the zone index (-1 for default/non-zone-specific)
     * @throws IOException if loading fails
     */
    void loadArtForZone(int zoneIndex) throws IOException;

    /**
     * Gets a renderer by its key.
     *
     * @param key the renderer key (e.g., "monitor", "spring_vertical")
     * @return the renderer, or null if not found
     */
    PatternSpriteRenderer getRenderer(String key);

    /**
     * Gets a sprite sheet by its key.
     *
     * @param key the sheet key (e.g., "monitor", "spring_vertical")
     * @return the sprite sheet, or null if not found
     */
    ObjectSpriteSheet getSheet(String key);

    /**
     * Gets an animation set by its key.
     *
     * @param key the animation key (e.g., "monitor", "spring", "checkpoint")
     * @return the animation set, or null if not found
     */
    SpriteAnimationSet getAnimations(String key);

    /**
     * Gets zone-specific integer data.
     *
     * @param key       the data key (e.g., "animal_type_a", "animal_type_b")
     * @param zoneIndex the zone index
     * @return the data value, or -1 if not found
     */
    int getZoneData(String key, int zoneIndex);

    /**
     * Gets HUD digit patterns for score/time/ring display.
     *
     * @return the digit patterns array
     */
    Pattern[] getHudDigitPatterns();

    /**
     * Gets HUD text patterns for label display.
     *
     * @return the text patterns array
     */
    Pattern[] getHudTextPatterns();

    /**
     * Gets HUD lives icon patterns.
     *
     * @return the lives icon patterns array
     */
    Pattern[] getHudLivesPatterns();

    /**
     * Gets HUD lives number patterns.
     *
     * @return the lives number patterns array
     */
    Pattern[] getHudLivesNumbers();

    /**
     * Gets all available renderer keys.
     *
     * @return list of renderer keys
     */
    List<String> getRendererKeys();

    /**
     * Caches all patterns to GPU memory.
     *
     * @param graphicsManager the graphics manager
     * @param baseIndex       the base pattern index
     * @return the next available pattern index after caching
     */
    int ensurePatternsCached(GraphicsManager graphicsManager, int baseIndex);

    /**
     * Checks if the provider has loaded and is ready to render.
     *
     * @return true if ready
     */
    boolean isReady();

    /**
     * Gets the palette line used for HUD text labels (SCORE/TIME/RINGS).
     * Sonic 2 uses palette line 1 (yellow), Sonic 1 uses palette line 0 (yellow).
     *
     * @return palette line (0-3), default 1
     */
    default int getHudTextPaletteLine() {
        return 1;
    }

    /**
     * Gets the palette line used for HUD icon and flash state.
     * This is the alternate palette shown when rings = 0 flashes.
     *
     * @return palette line (0-3), default 0
     */
    default int getHudFlashPaletteLine() {
        return 0;
    }

    /**
     * Gets the HUD flash mode for warning indicators (rings=0, time>=9:00).
     * S1/S2 use palette swap (red flash), S3K hides the text label entirely.
     *
     * @return the flash mode, default PALETTE_SWAP (S1/S2 behavior)
     */
    default HudRenderManager.HudFlashMode getHudFlashMode() {
        return HudRenderManager.HudFlashMode.PALETTE_SWAP;
    }

    /**
     * Registers object art sheets that depend on level tile data (e.g., smashable
     * ground, collapsing ledges, platforms that reuse level patterns).
     * Called after level load when the level's Pattern[] is available.
     *
     * @param level     the loaded level (provides pattern data)
     * @param zoneIndex the current zone index
     */
    default void registerLevelTileArt(Level level, int zoneIndex) {
        // Default no-op — games without level-tile-based object art need not override
    }
}
