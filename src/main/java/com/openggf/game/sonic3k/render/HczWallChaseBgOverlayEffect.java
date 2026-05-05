package com.openggf.game.sonic3k.render;

import static org.lwjgl.opengl.GL11.GL_VIEWPORT;
import static org.lwjgl.opengl.GL11.glGetIntegerv;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.ZoneFeatureProvider;
import com.openggf.game.render.SpecialRenderEffect;
import com.openggf.game.render.SpecialRenderEffectContext;
import com.openggf.game.render.SpecialRenderEffectStage;
import com.openggf.game.sonic3k.runtime.HczZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.TilemapGpuRenderer;
import com.openggf.level.LevelManager;
import com.openggf.level.ParallaxManager;
import com.openggf.level.WaterSystem;

/**
 * Renders the BG high-priority wall-chase overlay used by Hydrocity Zone Act 2.
 *
 * <p>On real hardware the VDP layer order is BG-low -&gt; FG-low -&gt; BG-high -&gt;
 * FG-high. The engine's main BG pass renders all priorities behind FG, so this
 * effect draws only BG high-priority tiles after the sprite pass to match
 * hardware layering — the approaching water wall must appear in front of FG
 * terrain, sprites (doors, platforms), and other gameplay objects, while still
 * being covered by the HUD.
 *
 * <p>Activation is driven entirely by the typed
 * {@link HczZoneRuntimeState#wallChaseBgOverlayActive()} runtime state — there
 * is no HCZ-specific flag on global game state.
 */
public final class HczWallChaseBgOverlayEffect implements SpecialRenderEffect {

    @Override
    public SpecialRenderEffectStage stage() {
        return SpecialRenderEffectStage.AFTER_SPRITES;
    }

    @Override
    public void render(SpecialRenderEffectContext context) {
        if (!isWallChaseBgOverlayActive()) {
            return;
        }

        GraphicsManager graphicsManager = context.graphicsManager();
        TilemapGpuRenderer renderer = graphicsManager.getTilemapGpuRenderer();
        if (renderer == null) {
            return;
        }

        Integer atlasId = graphicsManager.getPatternAtlasTextureId();
        Integer paletteId = graphicsManager.getCombinedPaletteTextureId();
        if (atlasId == null || paletteId == null) {
            return;
        }

        ParallaxManager parallaxManager = GameServices.parallax();
        int[] hScrollData = parallaxManager.getHScrollForShader();
        if (hScrollData == null || hScrollData.length == 0) {
            return;
        }

        short bgScroll = (short) (hScrollData[hScrollData.length - 1] & 0xFFFF);
        float bgWorldOffsetX = -bgScroll;
        float bgWorldOffsetY = parallaxManager.getVscrollFactorBG();

        SonicConfigurationService configService = GameServices.configuration();
        int screenW = configService.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS);
        int screenH = configService.getInt(SonicConfiguration.SCREEN_HEIGHT_PIXELS);

        // Water palette support — the wall must use the underwater palette below the waterline
        LevelManager levelManager = context.levelManager();
        Camera camera = context.camera();
        WaterSystem waterSystem = GameServices.water();
        int featureZone = levelManager.getFeatureZoneId();
        int featureAct = levelManager.getFeatureActId();
        boolean hasWater = waterSystem.hasWater(featureZone, featureAct);
        ZoneFeatureProvider zoneFeatureProvider = levelManager.getZoneFeatureProvider();
        boolean suppressUnderwaterPalette = zoneFeatureProvider != null
                && zoneFeatureProvider.shouldSuppressUnderwaterPalette(featureZone, featureAct);
        Integer underwaterPaletteId = graphicsManager.getUnderwaterPaletteTextureId();
        boolean useUnderwaterPalette = hasWater && !suppressUnderwaterPalette && underwaterPaletteId != null;
        int waterLevel = hasWater ? waterSystem.getVisualWaterLevelY(featureZone, featureAct) : 0;
        float waterlineScreenY = (float) (waterLevel - camera.getYWithShake());
        int uwPalId = useUnderwaterPalette ? underwaterPaletteId : 0;

        graphicsManager.registerCommand(new GLCommand(GLCommand.CommandType.CUSTOM, (cx, cy, cw, ch) -> {
            TilemapGpuRenderer tilemapRenderer = graphicsManager.getTilemapGpuRenderer();
            if (tilemapRenderer == null) {
                return;
            }
            int[] viewport = new int[4];
            glGetIntegerv(GL_VIEWPORT, viewport);
            tilemapRenderer.render(
                    TilemapGpuRenderer.Layer.BACKGROUND,
                    screenW,
                    screenH,
                    viewport[0],
                    viewport[1],
                    viewport[2],
                    viewport[3],
                    bgWorldOffsetX,
                    bgWorldOffsetY,
                    graphicsManager.getPatternAtlasWidth(),
                    graphicsManager.getPatternAtlasHeight(),
                    atlasId,
                    paletteId,
                    uwPalId,
                    1,      // priorityPass=1: HIGH-PRIORITY TILES ONLY
                    false,
                    false,
                    useUnderwaterPalette,
                    waterlineScreenY);
        }));
    }

    private static boolean isWallChaseBgOverlayActive() {
        return GameServices.zoneRuntimeRegistry()
                .currentAs(HczZoneRuntimeState.class)
                .map(HczZoneRuntimeState::wallChaseBgOverlayActive)
                .orElse(false);
    }
}
