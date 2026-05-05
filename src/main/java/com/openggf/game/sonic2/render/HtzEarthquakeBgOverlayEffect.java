package com.openggf.game.sonic2.render;

import static org.lwjgl.opengl.GL11.GL_VIEWPORT;
import static org.lwjgl.opengl.GL11.glGetIntegerv;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.render.SpecialRenderEffect;
import com.openggf.game.render.SpecialRenderEffectContext;
import com.openggf.game.render.SpecialRenderEffectStage;
import com.openggf.game.sonic2.runtime.HtzRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.TilemapGpuRenderer;
import com.openggf.level.ParallaxManager;

/**
 * Renders the BG high-priority cave-ceiling overlay used by Hill Top Zone's
 * earthquake mode.
 *
 * <p>On real hardware the VDP layer order is BG-low -&gt; FG-low -&gt; BG-high -&gt;
 * FG-high. The engine's main BG pass renders all priorities behind FG, so this
 * effect draws only BG high-priority tiles between FG-low and FG-high to match
 * hardware layering. In earthquake mode the HTZ horizontal scroll is flat, so
 * a single tilemap render call with the BG scroll offset suffices.
 *
 * <p>Activation is driven entirely by the typed
 * {@link HtzRuntimeState#earthquakeActive()} runtime state — there is no
 * HTZ-specific flag on global game state.
 */
public final class HtzEarthquakeBgOverlayEffect implements SpecialRenderEffect {

    @Override
    public SpecialRenderEffectStage stage() {
        return SpecialRenderEffectStage.AFTER_FOREGROUND;
    }

    @Override
    public void render(SpecialRenderEffectContext context) {
        if (!isEarthquakeActive()) {
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

        graphicsManager.registerCommand(new GLCommand(GLCommand.CommandType.CUSTOM, (cx, cy, cw, ch) -> {
            TilemapGpuRenderer tilemapRenderer = graphicsManager.getTilemapGpuRenderer();
            if (tilemapRenderer == null) {
                return;
            }
            int[] viewport = new int[4];
            glGetIntegerv(GL_VIEWPORT, viewport);
            // Disable VDP wrap height for the overlay — earthquake priority tiles are
            // at tilemap rows 48+ (outside the 0-31 range that normal BG wrapping uses).
            float savedWrapHeight = tilemapRenderer.getBgVdpWrapHeight();
            tilemapRenderer.setBgVdpWrapHeight(0.0f);
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
                    0,
                    1,      // priorityPass=1: HIGH-PRIORITY TILES ONLY
                    false,
                    false,
                    false,
                    0.0f);
            tilemapRenderer.setBgVdpWrapHeight(savedWrapHeight);
        }));
    }

    private static boolean isEarthquakeActive() {
        return GameServices.zoneRuntimeRegistry()
                .currentAs(HtzRuntimeState.class)
                .map(HtzRuntimeState::earthquakeActive)
                .orElse(false);
    }
}
