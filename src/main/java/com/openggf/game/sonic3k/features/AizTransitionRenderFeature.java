package com.openggf.game.sonic3k.features;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.FireCurtainRenderState;
import com.openggf.game.sonic3k.events.Sonic3kAIZEvents;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PatternRenderCommand;
import com.openggf.level.LevelManager;

/**
 * Encapsulates AIZ transition visual behavior:
 * - post-burn foreground heat haze gating
 * - post-sprite fire curtain draw during the AIZ1 miniboss fake-out transition
 */
public final class AizTransitionRenderFeature {
    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(AizTransitionRenderFeature.class.getName());

    private final SonicConfigurationService configService = com.openggf.game.EngineServices.fromLegacySingletonsForBootstrap().configuration();
    private final AizFireCurtainRenderer fireCurtainRenderer = new AizFireCurtainRenderer();
    private final GLCommand disableWaterShaderForCurtain = new GLCommand(
            GLCommand.CommandType.CUSTOM,
            (cx, cy, cw, ch) -> {
                com.openggf.game.EngineServices.fromLegacySingletonsForBootstrap().graphics().setUseWaterShader(false);
                PatternRenderCommand.resetFrameState();
            });
    private boolean loggedFirstRender;

    public void onZoneInit(int zoneIndex, int actIndex) {
        // No-op; zone/act are read from LevelManager during render.
    }

    public void reset() {
        loggedFirstRender = false;
        fireCurtainRenderer.reset();
    }

    public boolean shouldEnableForegroundHeatHaze(int zoneIndex, int actIndex, int cameraX) {
        if (zoneIndex != Sonic3kZoneIds.ZONE_AIZ) {
            return false;
        }
        Sonic3kAIZEvents events = getAizEvents();
        if (events != null) {
            return events.isPostFireHazeActive();
        }
        return actIndex > 0;
    }

    public void renderFlameOverlay(Camera camera, int frameCounter) {
        if (camera == null) {
            return;
        }
        LevelManager levelManager = GameServices.level();
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

        FireCurtainRenderState renderState = events.getFireCurtainRenderState(screenHeight);
        if (!renderState.active() || renderState.coverHeightPx() <= 0) {
            return;
        }

        if (!loggedFirstRender) {
            loggedFirstRender = true;
            LOG.info("AIZ fire curtain: FIRST RENDER coverH=" + renderState.coverHeightPx()
                    + " frame=" + frameCounter
                    + " stage=" + renderState.stage());
        }

        // The curtain is a scene overlay, not a water-surface effect. Render it
        // through the normal pattern path so it does not inherit water shader state.
        com.openggf.game.EngineServices.fromLegacySingletonsForBootstrap().graphics().registerCommand(disableWaterShaderForCurtain);
        fireCurtainRenderer.render(camera, renderState, screenWidth, screenHeight);
    }

    private Sonic3kAIZEvents getAizEvents() {
        Sonic3kLevelEventManager lem = resolveLevelEventManager();
        return lem != null ? lem.getAizEvents() : null;
    }

    private Sonic3kLevelEventManager resolveLevelEventManager() {
        return GameServices.hasRuntime()
                ? (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider()
                : null;
    }
}
