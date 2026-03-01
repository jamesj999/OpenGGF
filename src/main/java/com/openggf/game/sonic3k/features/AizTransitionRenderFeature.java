package com.openggf.game.sonic3k.features;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kAIZEvents;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.TilemapGpuRenderer;

import static java.lang.Math.ceil;

/**
 * Encapsulates AIZ transition visual behavior:
 * - post-burn foreground heat haze gating
 * - post-sprite fire-wall overlay draw during AIZ1 miniboss transition
 */
public final class AizTransitionRenderFeature {
    private static final int AIZ_PRE_FIRE_HAZE_START_X = 0x2E00;
    private static final int FLAME_OVERLAY_SOURCE_BASE_X = 0x1000;

    private final SonicConfigurationService configService = SonicConfigurationService.getInstance();

    private int currentZone = -1;
    private int currentAct = -1;

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
                    tilemapRenderer.render(
                            TilemapGpuRenderer.Layer.FOREGROUND,
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
                            false,
                            false,
                            false,
                            0.0f);
                } finally {
                    graphicsManager.disableScissor();
                }
            });

    public void onZoneInit(int zoneIndex, int actIndex) {
        currentZone = zoneIndex;
        currentAct = actIndex;
    }

    public void reset() {
        currentZone = -1;
        currentAct = -1;
        pendingCoverHeight = 0;
        pendingAtlasTextureId = null;
        pendingPaletteTextureId = null;
        pendingUnderwaterPaletteTextureId = null;
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
        if (camera == null || currentZone != Sonic3kZoneIds.ZONE_AIZ || currentAct != 0) {
            return;
        }

        Sonic3kAIZEvents events = getAizEvents();
        if (events == null || (!events.isFireTransitionActive() && !events.isAct2TransitionRequested())) {
            return;
        }

        int screenWidth = configService.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS);
        int screenHeight = configService.getInt(SonicConfiguration.SCREEN_HEIGHT_PIXELS);
        if (screenWidth <= 0 || screenHeight <= 0) {
            return;
        }

        int coverHeight = events.getFireWallCoverHeightPx(screenHeight);
        if (coverHeight <= 0) {
            return;
        }

        GraphicsManager graphicsManager = GraphicsManager.getInstance();
        Integer atlasTextureId = graphicsManager.getPatternAtlasTextureId();
        Integer paletteTextureId = graphicsManager.getCombinedPaletteTextureId();
        if (atlasTextureId == null || paletteTextureId == null) {
            return;
        }

        // Draw only the covered lower section. Keep source anchored to the transition
        // strip's final bottom to avoid sampling unrelated rows (sky/ocean) mid-rise.
        int sourceBottomY = events.getFireTransitionOverlayBottomY();
        int sourceOffsetY = sourceBottomY - screenHeight;

        pendingScreenWidth = screenWidth;
        pendingScreenHeight = screenHeight;
        pendingCoverHeight = coverHeight;
        pendingWorldOffsetX = events.getFireTransitionBgX();
        pendingWorldOffsetY = sourceOffsetY;
        pendingAtlasTextureId = atlasTextureId;
        pendingPaletteTextureId = paletteTextureId;
        pendingUnderwaterPaletteTextureId = graphicsManager.getUnderwaterPaletteTextureId();

        // Keep source X anchored to the transition strip base; wave phase is applied by events.
        pendingWorldOffsetX = Math.max(FLAME_OVERLAY_SOURCE_BASE_X, pendingWorldOffsetX);
        graphicsManager.registerCommand(flameOverlayCommand);
    }

    private Sonic3kAIZEvents getAizEvents() {
        Sonic3kLevelEventManager lem = Sonic3kLevelEventManager.getInstance();
        return lem != null ? lem.getAizEvents() : null;
    }
}
