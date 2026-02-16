package uk.co.jamesj999.sonic.game.sonic3k;

import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.configuration.SonicConfiguration;
import uk.co.jamesj999.sonic.configuration.SonicConfigurationService;
import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.game.ZoneFeatureProvider;
import uk.co.jamesj999.sonic.game.sonic3k.objects.AizPlaneIntroInstance;
import uk.co.jamesj999.sonic.graphics.GraphicsManager;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.io.IOException;

/**
 * Zone feature provider for Sonic 3 &amp; Knuckles.
 * Handles AIZ intro ocean phase detection, title card suppression,
 * and other S3K-specific zone features.
 */
public class Sonic3kZoneFeatureProvider implements ZoneFeatureProvider {

    @Override
    public void initZoneFeatures(Rom rom, int zoneIndex, int actIndex, int cameraX) throws IOException {
        // No zone features to initialize yet
    }

    @Override
    public void update(AbstractPlayableSprite player, int cameraX, int zoneIndex) {
        // No zone features to update yet
    }

    @Override
    public void reset() {
        // Nothing to reset
    }

    @Override
    public boolean hasCollisionFeatures(int zoneIndex) {
        return false;
    }

    @Override
    public boolean hasWater(int zoneIndex) {
        return false;
    }

    @Override
    public int getWaterLevel(int zoneIndex, int actIndex) {
        return Integer.MAX_VALUE;
    }

    @Override
    public void render(Camera camera, int frameCounter) {
        // Nothing to render yet
    }

    @Override
    public int ensurePatternsCached(GraphicsManager graphicsManager, int baseIndex) {
        return baseIndex;
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
