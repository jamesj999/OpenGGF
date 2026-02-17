package uk.co.jamesj999.sonic.game.sonic1;

import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.game.ZoneFeatureProvider;
import uk.co.jamesj999.sonic.game.sonic1.constants.Sonic1Constants;
import uk.co.jamesj999.sonic.game.sonic1.events.Sonic1LZWaterEvents;
import uk.co.jamesj999.sonic.graphics.GraphicsManager;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.WaterSystem;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Zone feature provider for Sonic 1.
 * Handles zone-specific mechanics, primarily water in Labyrinth Zone
 * and Scrap Brain Zone Act 3 (which reuses LZ water mechanics).
 *
 * <p>Water zones:
 * <ul>
 *   <li>Labyrinth Zone (zone ID 0x01, acts 0-2): Full water system with
 *       dynamic water levels driven by {@link Sonic1LZWaterEvents}.</li>
 *   <li>Scrap Brain Zone Act 3 (zone ID 0x05, act 2): Reuses LZ water
 *       mechanics with its own water heights and palette.</li>
 * </ul>
 */
public class Sonic1ZoneFeatureProvider implements ZoneFeatureProvider {
    private static final Logger LOGGER = Logger.getLogger(Sonic1ZoneFeatureProvider.class.getName());

    private Sonic1LZWaterEvents waterEvents;
    private Sonic1WaterSurfaceManager waterSurfaceManager;
    private int currentZone = -1;
    private int currentAct = -1;
    private boolean isSBZ3 = false;

    @Override
    public void initZoneFeatures(Rom rom, int zoneIndex, int actIndex, int cameraX) throws IOException {
        // Only reinitialize if zone/act changed
        if (zoneIndex == currentZone && actIndex == currentAct) {
            return;
        }

        reset();
        currentZone = zoneIndex;
        currentAct = actIndex;
        isSBZ3 = (zoneIndex == Sonic1Constants.ZONE_SBZ && actIndex == 2);

        if (hasWater(zoneIndex)) {
            // Initialize water system from ROM (hardcoded S1 water heights)
            WaterSystem.getInstance().loadForLevelS1(rom, zoneIndex, actIndex);

            // Create the water event state machine for dynamic water levels
            waterEvents = new Sonic1LZWaterEvents(Camera.getInstance());
            if (isSBZ3) {
                // SBZ3 uses its own event handler but shares the LZ water system.
                // Init with SBZ zone/act so WaterSystem lookups use the right key.
                waterEvents.init(zoneIndex, actIndex);
            } else {
                waterEvents.init(zoneIndex, actIndex);
            }

            // ROM only spawns WaterSurface objects in LZ (Level_ChkWater in sonic.asm).
            if (zoneIndex == Sonic1Constants.ZONE_LZ) {
                try {
                    waterSurfaceManager = new Sonic1WaterSurfaceManager(rom, zoneIndex, actIndex);
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Failed to initialize S1 water surface manager", e);
                    waterSurfaceManager = null;
                }
            }

            LOGGER.info(String.format("Initialized S1 water features for zone %d act %d (SBZ3=%s)",
                    zoneIndex, actIndex, isSBZ3));
        }
    }

    @Override
    public void update(AbstractPlayableSprite player, int cameraX, int zoneIndex) {
        if (!hasWater(zoneIndex)) {
            return;
        }

        // ROM call order in LZWaterFeatures:
        //   1. LZWindTunnels (water currents)
        //   2. LZWaterSlides (slide chunks)
        //   3. LZDynamicWater (water level state machine)
        // Only called if Sonic has not just died (obRoutine < 6).
        boolean playerAlive = player != null && !player.getDead();

        if (waterEvents != null && playerAlive) {
            // 1. Wind tunnels (horizontal underwater currents)
            if (isSBZ3) {
                waterEvents.updateWindTunnelsSBZ3();
            } else {
                waterEvents.updateWindTunnels();
            }

            // 2. Water slides (chunk-based slide mechanic)
            // ROM reads from v_lvllayout using obX/obY. In our engine, use the
            // player's centre position to query the equivalent layout block ID.
            int chunkIdAtPlayer = -1;
            int fallbackChunkId = -1;
            LevelManager levelManager = LevelManager.getInstance();
            if (levelManager != null) {
                chunkIdAtPlayer = levelManager.getBlockIdAt(player.getCentreX(), player.getCentreY());
                // ROM uses obX/obY directly; in this engine we also sample sprite-origin
                // to avoid transient misses from coordinate representation differences.
                fallbackChunkId = levelManager.getBlockIdAt(player.getX(), player.getY());
            }
            waterEvents.checkWaterSlide(chunkIdAtPlayer, fallbackChunkId);
        }

        // 3. Dynamic water level state machine + gradual movement
        // Progress dynamic water levels (moves current toward target by 1px/frame)
        WaterSystem.getInstance().update();

        // Run per-act water event state machine
        if (waterEvents != null) {
            if (isSBZ3) {
                waterEvents.updateSBZ3();
            } else {
                waterEvents.update();
            }
        }

        // Note: player.updateWaterState() is called by LevelManager using
        // getVisualWaterLevelY(), matching the ROM's use of v_waterpos1 for
        // underwater detection. We don't call it here to avoid double-application.
    }

    @Override
    public void render(Camera camera, int frameCounter) {
        if (waterSurfaceManager != null && waterSurfaceManager.isInitialized()) {
            waterSurfaceManager.render(camera, frameCounter);
        }
    }

    @Override
    public void renderAfterForeground(Camera camera) {
        // No post-foreground rendering needed for S1
    }

    @Override
    public int ensurePatternsCached(GraphicsManager graphicsManager, int baseIndex) {
        if (waterSurfaceManager != null) {
            return waterSurfaceManager.ensurePatternsCached(graphicsManager, baseIndex);
        }
        return baseIndex;
    }

    @Override
    public void reset() {
        waterEvents = null;
        waterSurfaceManager = null;
        currentZone = -1;
        currentAct = -1;
        isSBZ3 = false;
    }

    @Override
    public boolean hasCollisionFeatures(int zoneIndex) {
        // S1 has no zone-specific collision features (like CNZ bumpers)
        return false;
    }

    @Override
    public boolean hasWater(int zoneIndex) {
        // LZ (zone 0x01) has water in all acts
        if (zoneIndex == Sonic1Constants.ZONE_LZ) {
            return true;
        }
        // SBZ Act 3 (zone 0x05, act 2) reuses LZ water mechanics.
        // We check against currentAct because hasWater() only receives zoneIndex.
        if (zoneIndex == Sonic1Constants.ZONE_SBZ && currentAct == 2) {
            return true;
        }
        return false;
    }

    @Override
    public int getWaterLevel(int zoneIndex, int actIndex) {
        if (hasWater(zoneIndex)) {
            return WaterSystem.getInstance().getWaterLevelY(zoneIndex, actIndex);
        }
        return Integer.MAX_VALUE;
    }

    /**
     * Sets the LZ wind tunnel disable flag (ROM: f_wtunnelallow) when water events
     * are active. No-op outside water-enabled zones.
     */
    public void setWindTunnelDisabled(boolean disabled) {
        if (waterEvents != null) {
            waterEvents.setWindTunnelDisabled(disabled);
        }
    }

    /**
     * Returns the current LZ wind tunnel disable flag (ROM: f_wtunnelallow).
     */
    public boolean isWindTunnelDisabled() {
        return waterEvents != null && waterEvents.isWindTunnelDisabled();
    }
}
