package com.openggf.game;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.Rom;
import com.openggf.data.RomManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.graphics.GraphicsManager;
import com.openggf.sprites.playable.Sonic;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class TestPowerUpGraphicsRegression {

    @AfterEach
    void tearDown() {
        CrossGameFeatureProvider.getInstance().resetState();
        GraphicsManager.getInstance().resetState();
        RomManager.getInstance().close();
        RuntimeManager.destroyCurrent();
        GameModuleRegistry.reset();
        SonicConfigurationService.getInstance().resetToDefaults();
    }

    @Test
    void invincibilityStarsKeepRendererWhenSpawnedFromPowerUpSpawner() throws Exception {
        Sonic player = loadSonic2Player(false);

        player.giveInvincibility();

        Object stars = player.getInvincibilityObject();
        assertNotNull(stars, "Invincibility should create a star object");
        assertNotNull(readField(stars, "renderer"),
                "Invincibility stars should resolve their renderer during power-up spawn");
    }

    @Test
    void crossGameS2LoadCreatesPersistentInstaShieldObject() throws Exception {
        Sonic player = loadSonic2Player(true);

        Object instaShield = player.getInstaShieldObject();
        assertNotNull(instaShield,
                "Sonic should have a persistent insta-shield object after cross-game S2 level load");
        assertNotNull(readField(instaShield, "dplcRenderer"),
                "Persistent insta-shield object should have donor art renderer ready");
    }

    private Sonic loadSonic2Player(boolean crossGame) throws Exception {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        config.resetToDefaults();
        config.setConfigValue(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, crossGame);
        config.setConfigValue(SonicConfiguration.CROSS_GAME_SOURCE, "s3k");

        RuntimeManager.configureEngineServices(EngineServices.fromLegacySingletonsForBootstrap());
        RuntimeManager.destroyCurrent();
        RuntimeManager.createGameplay();
        GameModuleRegistry.setCurrent(new Sonic2GameModule());

        Rom primaryRom = openRom("Sonic The Hedgehog 2 (W) (REV01) [!].gen",
                "Sonic The Hedgehog 2 (W) (REV00) [!].gen");
        RomManager.getInstance().setRom(primaryRom);

        if (crossGame) {
            assumeTrue(Files.exists(Path.of("Sonic and Knuckles & Sonic 3 (W) [!].gen")),
                    "S3K donor ROM is required for cross-game insta-shield regression coverage");
            CrossGameFeatureProvider.getInstance().resetState();
            CrossGameFeatureProvider.getInstance().initialize("s3k");
        }

        GraphicsManager.getInstance().resetState();
        GraphicsManager.getInstance().initHeadless();

        Sonic player = new Sonic("sonic", (short) 100, (short) 624);
        GameServices.sprites().addSprite(player);
        GameServices.level().loadZoneAndAct(0, 0);
        return player;
    }

    private static Rom openRom(String... candidates) throws IOException {
        for (String candidate : candidates) {
            if (!Files.exists(Path.of(candidate))) {
                continue;
            }
            Rom rom = new Rom();
            if (rom.open(candidate)) {
                return rom;
            }
        }
        assumeTrue(false, "Required ROM not available: " + String.join(", ", candidates));
        throw new IOException("ROM unavailable");
    }

    private static Object readField(Object target, String fieldName) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }
}
