package com.openggf.game;

import com.openggf.camera.Camera;
import com.openggf.data.Rom;
import com.openggf.graphics.GraphicsManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.io.IOException;

/**
 * Interface for zone-specific features that require special initialization.
 *
 * <p>Examples of zone features:
 * <ul>
 *   <li>Casino Night Zone: Bumpers, flippers</li>
 *   <li>Aquatic Ruin Zone: Water level and underwater mechanics</li>
 *   <li>Oil Ocean Zone: Oil mechanics</li>
 *   <li>Labyrinth Zone (Sonic 1): Water level, currents</li>
 * </ul>
 *
 * <p>Zone features are separate from scroll handlers and object registries.
 * They provide collision systems and gameplay mechanics specific to certain zones.
 */
public interface ZoneFeatureProvider {
    /**
     * Called when entering a zone to initialize zone-specific features.
     *
     * @param rom the ROM for loading data
     * @param zoneIndex the zone being entered
     * @param actIndex the act within the zone
     * @param cameraX the camera X position
     * @throws IOException if initialization fails
     */
    void initZoneFeatures(Rom rom, int zoneIndex, int actIndex, int cameraX) throws IOException;

    /**
     * Updates zone features each frame.
     *
     * @param player the player sprite (may be null)
     * @param cameraX the camera X position
     * @param zoneIndex the current zone
     */
    void update(AbstractPlayableSprite player, int cameraX, int zoneIndex);

    /**
     * Resets all zone feature managers.
     * Called when leaving a zone or reloading.
     */
    void reset();

    /**
     * Checks if this zone has special collision features (bumpers, etc.).
     *
     * @param zoneIndex the zone to check
     * @return true if the zone has collision features
     */
    boolean hasCollisionFeatures(int zoneIndex);

    /**
     * Checks if this zone has water mechanics.
     *
     * @param zoneIndex the zone to check
     * @return true if the zone has water
     */
    boolean hasWater(int zoneIndex);

    /**
     * Gets the water level for a zone (if applicable).
     *
     * @param zoneIndex the zone
     * @param actIndex the act
     * @return the water level Y position, or Integer.MAX_VALUE if no water
     */
    int getWaterLevel(int zoneIndex, int actIndex);

    /**
     * Renders zone-specific visual features (e.g., water surface sprites).
     * Called during the draw phase after level rendering and all sprites.
     *
     * @param camera the camera for screen coordinates
     * @param frameCounter current frame number for animation
     */
    void render(Camera camera, int frameCounter);

    /**
     * Updates zone features that must run BEFORE player physics.
     *
     * <p>In the ROM, certain zone features (LZ water slides, wind tunnels) run before
     * Sonic's movement code ({@code ExecuteObjects}). These features set flags like
     * {@code f_slidemode} and overwrite {@code obInertia} so that {@code Sonic_Move}
     * sees the correct state when it runs.
     *
     * <p>Implementations should move any logic that modifies player velocity or
     * movement flags here. The default implementation does nothing, so existing
     * providers (S2, S3K) are unaffected.
     *
     * @param player the player sprite (may be null)
     * @param cameraX the camera X position
     * @param zoneIndex the current zone
     */
    default void updatePrePhysics(AbstractPlayableSprite player, int cameraX, int zoneIndex) {
        // Default implementation does nothing
    }

    /**
     * Queues render commands for zone features that should appear after foreground tiles
     * but before sprites (e.g., slot machine display that covers corrupted tiles).
     * Called after high-priority foreground tilemap pass but before sprite passes.
     *
     * @param camera the camera for screen coordinates
     */
    default void renderAfterForeground(Camera camera) {
        // Default implementation does nothing
    }

    /**
     * Queues render commands for zone features that should appear after the background pass
     * but before any foreground tiles (for example, AIZ2's split-rendered bombership strip).
     *
     * @param camera the camera for screen coordinates
     * @param frameCounter current frame number for animation
     */
    default void renderAfterBackground(Camera camera, int frameCounter) {
        // Default implementation does nothing
    }

    /**
     * Whether the foreground renderer should apply per-line heat haze deformation.
     * This is used for zone-specific post-processing effects such as AIZ fire haze.
     *
     * @param zoneIndex current feature zone id
     * @param actIndex current feature act id
     * @param cameraX current camera X position
     * @return true when per-line foreground haze should be enabled
     */
    default boolean shouldEnableForegroundHeatHaze(int zoneIndex, int actIndex, int cameraX) {
        return false;
    }

    /**
     * Whether the foreground tilemap renderer should sample the per-line foreground
     * h-scroll buffer instead of using a flat camera X origin.
     *
     * <p>This is used by stages whose Plane A positioning is not camera-locked,
     * such as the S3K Slots bonus stage.
     *
     * @param zoneIndex current feature zone id
     * @param actIndex current feature act id
     * @param cameraX current camera X position
     * @return true when per-line foreground scroll should be enabled
     */
    default boolean shouldEnablePerLineForegroundScroll(int zoneIndex, int actIndex, int cameraX) {
        return false;
    }

    /**
     * Ensures zone feature patterns are cached in the graphics manager.
     * Called during level initialization.
     *
     * @param graphicsManager the graphics manager
     * @param baseIndex starting pattern index
     * @return next available pattern index after caching
     */
    int ensurePatternsCached(GraphicsManager graphicsManager, int baseIndex);

    /**
     * Whether the background layer wraps horizontally at VDP plane width (512px).
     * Sonic 2 uses a VDP plane redraw model where BG wraps at 512px.
     * Other games use full-width BG data.
     *
     * @return true if BG should wrap at VDP plane width
     */
    default boolean bgWrapsHorizontally() {
        return false;
    }

    /**
     * Whether the intro ocean phase is currently active (e.g. AIZ intro in S3K).
     * When active, the BG plane wraps at VDP width instead of full layout width.
     *
     * @param zoneIndex the current zone
     * @param actIndex the current act
     * @return true if an intro ocean phase is active
     */
    default boolean isIntroOceanPhaseActive(int zoneIndex, int actIndex) {
        return false;
    }

    /**
     * Returns the VDP nametable base tile for per-line-scroll BG wrapping.
     * During intro sequences with per-line scroll, this controls which 64-tile
     * window of the BG tilemap is visible, matching the VDP ring buffer position.
     *
     * @param zoneIndex the current zone
     * @param actIndex the current act
     * @param cameraX the camera X position
     * @param tilemapWidthTiles the BG tilemap width in tiles
     * @return the nametable base tile (0.0 by default)
     */
    default float getVdpNametableBase(int zoneIndex, int actIndex, int cameraX, int tilemapWidthTiles) {
        return 0.0f;
    }

    /**
     * Whether the initial title card should be suppressed for the given zone/act.
     * Used for intro sequences (e.g. AIZ intro in S3K) that should not show a title card.
     *
     * @param zoneIndex the current zone
     * @param actIndex the current act
     * @return true if the title card should be suppressed
     */
    default boolean shouldSuppressInitialTitleCard(int zoneIndex, int actIndex) {
        return false;
    }

    /**
     * Whether the HUD should be hidden for the given zone/act.
     * Used during intro cinematics (e.g. AIZ intro in S3K) where the HUD
     * should not be visible until gameplay begins.
     *
     * @param zoneIndex the current zone
     * @param actIndex the current act
     * @return true if the HUD should be hidden
     */
    default boolean shouldSuppressHud(int zoneIndex, int actIndex) {
        return false;
    }

    /**
     * Whether the backdrop colour should be forced to black regardless of the
     * level's own backdrop colour.  Used for zones (e.g. MCZ in Sonic 2) whose
     * background is drawn entirely by sprites/tiles with no sky visible.
     *
     * @return true if the backdrop should be forced to black
     */
    default boolean isForceBlackBackdrop() {
        return false;
    }

    /**
     * Returns the current water routine index for checkpoint save/restore.
     * Only meaningful for games with dynamic water routines (e.g., Sonic 1 LZ).
     *
     * @return the water routine index, or 0 if not applicable
     */
    default int getWaterRoutine() {
        return 0;
    }

    /**
     * Sets the water routine index after checkpoint restore.
     * Only meaningful for games with dynamic water routines (e.g., Sonic 1 LZ).
     *
     * @param routine the water routine index to restore
     */
    default void setWaterRoutine(int routine) {
        // No-op by default
    }

    /**
     * Returns a zone-specific renderer for custom visual effects (e.g. CNZ slot machine).
     * Games/zones without custom renderers return {@link ZoneFeatureRenderer#NONE}.
     *
     * @return the zone feature renderer, never null
     */
    default ZoneFeatureRenderer getFeatureRenderer() {
        return ZoneFeatureRenderer.NONE;
    }
}
