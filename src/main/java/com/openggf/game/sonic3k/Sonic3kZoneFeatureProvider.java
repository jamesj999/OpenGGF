package com.openggf.game.sonic3k;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.Rom;
import com.openggf.game.ZoneFeatureProvider;
import com.openggf.game.sonic3k.features.AizTransitionRenderFeature;
import com.openggf.game.sonic3k.objects.AizPlaneIntroInstance;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.WaterSystem;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.io.IOException;

/**
 * Zone feature provider for Sonic 3 &amp; Knuckles.
 * Handles AIZ intro ocean phase detection, title card suppression,
 * and other S3K-specific zone features.
 */
public class Sonic3kZoneFeatureProvider implements ZoneFeatureProvider {
    private final AizTransitionRenderFeature aizTransitionRenderFeature = new AizTransitionRenderFeature();

    @Override
    public void initZoneFeatures(Rom rom, int zoneIndex, int actIndex, int cameraX) throws IOException {
        aizTransitionRenderFeature.onZoneInit(zoneIndex, actIndex);
    }

    @Override
    public void update(AbstractPlayableSprite player, int cameraX, int zoneIndex) {
        // No zone features to update yet
    }

    @Override
    public void reset() {
        aizTransitionRenderFeature.reset();
    }

    @Override
    public boolean hasCollisionFeatures(int zoneIndex) {
        return false;
    }

    @Override
    public boolean hasWater(int zoneIndex) {
        // Check if water was loaded for this zone (any act)
        WaterSystem waterSystem = WaterSystem.getInstance();
        return waterSystem.hasWater(zoneIndex, 0) || waterSystem.hasWater(zoneIndex, 1);
    }

    @Override
    public int getWaterLevel(int zoneIndex, int actIndex) {
        WaterSystem waterSystem = WaterSystem.getInstance();
        return waterSystem.getWaterLevelY(zoneIndex, actIndex);
    }

    @Override
    public void render(Camera camera, int frameCounter) {
        aizTransitionRenderFeature.renderFlameOverlay(camera, frameCounter);
    }

    @Override
    public void renderAfterForeground(Camera camera) {
        // No S3K foreground-stage overlays currently. AIZ fire wall is a post-sprite screen pass.
    }

    @Override
    public int ensurePatternsCached(GraphicsManager graphicsManager, int baseIndex) {
        return baseIndex;
    }

    @Override
    public boolean shouldEnableForegroundHeatHaze(int zoneIndex, int actIndex, int cameraX) {
        return aizTransitionRenderFeature.shouldEnableForegroundHeatHaze(zoneIndex, actIndex, cameraX);
    }

    @Override
    public boolean isIntroOceanPhaseActive(int zoneIndex, int actIndex) {
        if (zoneIndex != 0 || actIndex != 0) {
            return false;
        }
        if (AizPlaneIntroInstance.isMainLevelPhaseActive()) {
            return false;
        }
        return !Camera.getInstance().isLevelStarted();
    }

    @Override
    public float getVdpNametableBase(int zoneIndex, int actIndex, int cameraX, int tilemapWidthTiles) {
        if (zoneIndex != 0 || actIndex != 0) {
            return 0.0f;
        }
        int introOffset = AizPlaneIntroInstance.getIntroScrollOffset();
        if (introOffset < 0) {
            return 0.0f;  // Pure ocean phase: no positions overwritten
        }
        // Overflow = total number of nametable column overwrites since scrolling began.
        // BG tile = cameraX / 16 (BG at half speed, 8px/tile).
        // First overwrite when bgTile = VDP_WRAP(64) - SCREEN_TILES(40) + 1 = 25.
        // NOT clamped: the shader decomposes overflow into gen/partial to handle
        // multiple wrap cycles (each position gets overwritten every 64 scroll steps).
        int bgTile = Math.floorDiv(cameraX, 16);
        return Math.max(0.0f, (float) (bgTile - 24));
    }

    @Override
    public boolean shouldSuppressHud(int zoneIndex, int actIndex) {
        if (zoneIndex != 0 || actIndex != 0) {
            return false;
        }
        // Hide HUD during AIZ intro until Camera marks level as started
        return !Camera.getInstance().isLevelStarted();
    }

    @Override
    public boolean shouldSuppressInitialTitleCard(int zoneIndex, int actIndex) {
        if (zoneIndex != 0 || actIndex != 0) {
            return false;
        }
        SonicConfigurationService configService = SonicConfigurationService.getInstance();
        if (configService.getBoolean(SonicConfiguration.S3K_SKIP_INTROS)) {
            return false;
        }
        String mainCharacter = configService.getString(SonicConfiguration.MAIN_CHARACTER_CODE);
        return mainCharacter != null && "sonic".equalsIgnoreCase(mainCharacter.trim());
    }

}
