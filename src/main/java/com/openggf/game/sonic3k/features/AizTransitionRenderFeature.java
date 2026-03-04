package com.openggf.game.sonic3k.features;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.FireWallRenderState;
import com.openggf.game.sonic3k.events.Sonic3kAIZEvents;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.TilemapGpuRenderer;
import com.openggf.level.LevelManager;

import static java.lang.Math.ceil;

/**
 * Encapsulates AIZ transition visual behavior:
 * - post-burn foreground heat haze gating
 * - post-sprite fire-wall overlay draw during AIZ1 miniboss transition
 */
public final class AizTransitionRenderFeature {
    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(AizTransitionRenderFeature.class.getName());
    private static final int AIZ_PRE_FIRE_HAZE_START_X = 0x2E00;

    private final SonicConfigurationService configService = SonicConfigurationService.getInstance();
    private boolean loggedFirstRender;

    // Mutable state consumed by the pre-allocated render command.
    private int pendingScreenWidth;
    private int pendingScreenHeight;
    private int pendingCoverHeight;
    private float pendingWorldOffsetX;
    private float pendingWorldOffsetY;
    private Integer pendingAtlasTextureId;
    private Integer pendingPaletteTextureId;
    private Integer pendingUnderwaterPaletteTextureId;

    private final GLCommand flameOverlayCommand = new GLCommand(GLCommand.CommandType.CUSTOM,
            (cameraX, cameraY, cameraW, cameraH) -> {
                GraphicsManager graphicsManager = GraphicsManager.getInstance();
                TilemapGpuRenderer tilemapRenderer = graphicsManager.getTilemapGpuRenderer();
                if (tilemapRenderer == null
                        || pendingCoverHeight <= 0
                        || pendingAtlasTextureId == null
                        || pendingPaletteTextureId == null
                        || pendingScreenWidth <= 0
                        || pendingScreenHeight <= 0) {
                    return;
                }

                int viewportX = graphicsManager.getViewportX();
                int viewportY = graphicsManager.getViewportY();
                int viewportWidth = graphicsManager.getViewportWidth();
                int viewportHeight = graphicsManager.getViewportHeight();
                if (viewportWidth <= 0 || viewportHeight <= 0) {
                    return;
                }

                float coverRatio = pendingCoverHeight / (float) pendingScreenHeight;
                int scissorHeight = Math.max(1, (int) ceil(coverRatio * viewportHeight));
                if (scissorHeight > viewportHeight) {
                    scissorHeight = viewportHeight;
                }

                graphicsManager.enableScissor(viewportX, viewportY, viewportWidth, scissorHeight);
                try {
                    // ROM: the fire wall is the BG layer scrolled up by
                    // Camera_Y_pos_BG_copy. The BG has solid ground/terrain
                    // tiles that appear as fire with the fire palette.
                    // FG at this position is mostly transparent sky.
                    tilemapRenderer.render(
                            TilemapGpuRenderer.Layer.BACKGROUND,
                            pendingScreenWidth,
                            pendingScreenHeight,
                            viewportX,
                            viewportY,
                            viewportWidth,
                            viewportHeight,
                            pendingWorldOffsetX,
                            pendingWorldOffsetY,
                            graphicsManager.getPatternAtlasWidth(),
                            graphicsManager.getPatternAtlasHeight(),
                            pendingAtlasTextureId,
                            pendingPaletteTextureId,
                            pendingUnderwaterPaletteTextureId != null ? pendingUnderwaterPaletteTextureId : 0,
                            -1,
                            true,
                            false,
                            false,
                            0.0f);
                } finally {
                    graphicsManager.disableScissor();
                }
            });

    public void onZoneInit(int zoneIndex, int actIndex) {
        // No-op; zone/act are read from LevelManager during render.
    }

    public void reset() {
        pendingCoverHeight = 0;
        pendingAtlasTextureId = null;
        pendingPaletteTextureId = null;
        pendingUnderwaterPaletteTextureId = null;
        loggedFirstRender = false;
    }

    public boolean shouldEnableForegroundHeatHaze(int zoneIndex, int actIndex, int cameraX) {
        if (zoneIndex != Sonic3kZoneIds.ZONE_AIZ) {
            return false;
        }
        if (actIndex > 0) {
            return true;
        }
        Sonic3kAIZEvents events = getAizEvents();
        return (events != null && events.isPostFireHazeActive()) || cameraX >= AIZ_PRE_FIRE_HAZE_START_X;
    }

    public void renderFlameOverlay(Camera camera) {
        if (camera == null) {
            return;
        }
        LevelManager levelManager = LevelManager.getInstance();
        if (levelManager.getCurrentZone() != Sonic3kZoneIds.ZONE_AIZ) {
            return;
        }

        Sonic3kAIZEvents events = getAizEvents();
        if (events == null) {
            return;
        }

        int screenWidth = configService.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS);
        int screenHeight = configService.getInt(SonicConfiguration.SCREEN_HEIGHT_PIXELS);
        if (screenWidth <= 0 || screenHeight <= 0) {
            return;
        }

        FireWallRenderState renderState = events.getFireWallRenderState(screenHeight);
        if (renderState == null || renderState.coverHeightPx() <= 0) {
            return;
        }

        GraphicsManager graphicsManager = GraphicsManager.getInstance();
        Integer atlasTextureId = graphicsManager.getPatternAtlasTextureId();
        Integer paletteTextureId = graphicsManager.getCombinedPaletteTextureId();
        if (atlasTextureId == null || paletteTextureId == null) {
            return;
        }

        pendingScreenWidth = screenWidth;
        pendingScreenHeight = screenHeight;
        pendingCoverHeight = renderState.coverHeightPx();
        pendingWorldOffsetX = renderState.sourceWorldX();
        pendingWorldOffsetY = renderState.sourceWorldY();
        pendingAtlasTextureId = atlasTextureId;
        pendingPaletteTextureId = paletteTextureId;
        pendingUnderwaterPaletteTextureId = graphicsManager.getUnderwaterPaletteTextureId();

        if (!loggedFirstRender) {
            loggedFirstRender = true;
            LOG.info("AIZ fire overlay: FIRST RENDER coverH=" + pendingCoverHeight
                    + " worldX=0x" + Integer.toHexString((int) pendingWorldOffsetX)
                    + " worldY=0x" + Integer.toHexString((int) pendingWorldOffsetY)
                    + " screenW=" + screenWidth + " screenH=" + screenHeight);
        }

        graphicsManager.registerCommand(flameOverlayCommand);
    }

    private Sonic3kAIZEvents getAizEvents() {
        Sonic3kLevelEventManager lem = Sonic3kLevelEventManager.getInstance();
        return lem != null ? lem.getAizEvents() : null;
    }
}
