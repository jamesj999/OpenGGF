package com.openggf;

import com.openggf.game.GameId;
import com.openggf.game.GameModule;
import com.openggf.game.EngineServices;
import com.openggf.game.GameMode;
import com.openggf.game.GameRuntime;
import com.openggf.game.GameStateManager;
import com.openggf.game.MasterTitleScreen;
import com.openggf.game.RuntimeManager;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.dataselect.DataSelectAction;
import com.openggf.game.dataselect.DataSelectActionType;
import com.openggf.game.save.SaveManager;
import com.openggf.game.save.SaveReason;
import com.openggf.game.save.SaveSessionContext;
import com.openggf.game.save.SelectedTeam;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.game.sonic1.dataselect.S1DataSelectImageCacheManager;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.session.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mockStatic;

class TestEngine {
    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        RuntimeManager.destroyCurrent();
        SessionManager.clear();
        RuntimeManager.configureEngineServices(EngineServices.fromLegacySingletonsForBootstrap());
    }

    @Test
    void drawMasterTitleScreenDoesNotRequireGameplayCamera() throws Exception {
        RuntimeManager.configureEngineServices(EngineServices.fromLegacySingletonsForBootstrap());
        Engine engine = new Engine();
        MasterTitleScreen masterTitleScreen = mock(MasterTitleScreen.class);

        setPrivateField(engine, "masterTitleScreen", masterTitleScreen);
        engine.getGameLoop().setGameMode(GameMode.MASTER_TITLE_SCREEN);

        assertDoesNotThrow(engine::draw);
        verify(masterTitleScreen).setProjectionMatrix(engine.getProjectionMatrixBuffer());
        verify(masterTitleScreen).draw();
    }

    @Test
    void maybeStartS1DonatedDataSelectImageGeneration_startsWarmupForS3kDonation() throws Exception {
        TrackingS1ImageCacheManager cacheManager = new TrackingS1ImageCacheManager(tempDir);
        Sonic1GameModule module = new Sonic1GameModule() {
            @Override
            public <T> T getGameService(Class<T> type) {
                if (type == S1DataSelectImageCacheManager.class) {
                    return type.cast(cacheManager);
                }
                return super.getGameService(type);
            }
        };
        Engine engine = new Engine();

        try (MockedStatic<CrossGameFeatureProvider> donor = mockStatic(CrossGameFeatureProvider.class)) {
            donor.when(CrossGameFeatureProvider::isS3kDonorActive).thenReturn(true);

            invokeMaybeStartS1DonatedDataSelectImageGeneration(engine, module);
        }

        assertEquals(1, cacheManager.ensureStartedCalls);
    }

    @Test
    void maybeStartS1DonatedDataSelectImageGeneration_doesNothingForPlainS1() throws Exception {
        TrackingS1ImageCacheManager cacheManager = new TrackingS1ImageCacheManager(tempDir);
        Sonic1GameModule module = new Sonic1GameModule() {
            @Override
            public <T> T getGameService(Class<T> type) {
                if (type == S1DataSelectImageCacheManager.class) {
                    return type.cast(cacheManager);
                }
                return super.getGameService(type);
            }
        };
        Engine engine = new Engine();

        try (MockedStatic<CrossGameFeatureProvider> donor = mockStatic(CrossGameFeatureProvider.class)) {
            donor.when(CrossGameFeatureProvider::isS3kDonorActive).thenReturn(false);

            invokeMaybeStartS1DonatedDataSelectImageGeneration(engine, module);
        }

        assertEquals(0, cacheManager.ensureStartedCalls);
    }

    @Test
    void createDataSelectSaveContext_preservesClearSaveStateFromPayload() throws Exception {
        Path saveRoot = Files.createTempDirectory("engine-dataselect-save");
        SaveManager saveManager = new SaveManager(saveRoot);
        saveManager.writeSlot("s3k", 1, Map.of(
                "zone", 6,
                "act", 1,
                "mainCharacter", "sonic",
                "sidekicks", List.of("tails"),
                "lives", 9,
                "continues", 3,
                "chaosEmeralds", List.of(0, 1, 2, 3, 4, 5, 6),
                "superEmeralds", List.of(0, 2),
                "clear", true
        ));

        GameModule module = mock(GameModule.class);
        when(module.getGameId()).thenReturn(GameId.S3K);

        DataSelectAction action = new DataSelectAction(
                DataSelectActionType.CLEAR_RESTART,
                1,
                10,
                0,
                new SelectedTeam("sonic", List.of("tails")));

        SaveSessionContext context = Engine.createDataSelectSaveContext(module, action, saveManager);

        assertEquals(1, context.activeSlot().orElseThrow());
        assertTrue(context.isClear(), "Clear-save launch context should preserve the clear flag");
        assertEquals("sonic", context.selectedTeam().mainCharacter());
        assertEquals(List.of("tails"), context.selectedTeam().sidekicks());
        assertEquals(10, context.startZone());
        assertEquals(0, context.startAct());
    }

    @Test
    void dataSelectLaunchSaveReason_mapsExistingSlotLoad() {
        assertEquals(Optional.of(SaveReason.EXISTING_SLOT_LOAD),
                Engine.dataSelectLaunchSaveReason(DataSelectActionType.LOAD_SLOT));
        assertEquals(Optional.of(SaveReason.NEW_SLOT_START),
                Engine.dataSelectLaunchSaveReason(DataSelectActionType.NEW_SLOT_START));
        assertEquals(Optional.of(SaveReason.CLEAR_RESTART_COMMIT),
                Engine.dataSelectLaunchSaveReason(DataSelectActionType.CLEAR_RESTART));
        assertEquals(Optional.empty(),
                Engine.dataSelectLaunchSaveReason(DataSelectActionType.NO_SAVE_START));
    }

    @Test
    void syncSelectedTeamConfig_appliesSelectedTeamToGameplayConfig() {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        config.resetToDefaults();
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");

        SaveSessionContext save = SaveSessionContext.noSave(
                "s2",
                new SelectedTeam("knuckles", List.of("tails", "sonic")),
                0,
                0);

        Engine.syncSelectedTeamConfig(config, save);

        assertEquals("knuckles", config.getString(SonicConfiguration.MAIN_CHARACTER_CODE));
        assertEquals("tails,sonic", config.getString(SonicConfiguration.SIDEKICK_CHARACTER_CODE));
    }

    @Test
    void syncSelectedTeamConfig_ignoresMissingSelectedTeam() {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        config.resetToDefaults();
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");

        Engine.syncSelectedTeamConfig(config, null);

        assertEquals("sonic", config.getString(SonicConfiguration.MAIN_CHARACTER_CODE));
        assertEquals("tails", config.getString(SonicConfiguration.SIDEKICK_CHARACTER_CODE));
    }

    @Test
    void restoreRuntimeFromDataSelectPayload_restoresS1ChaosEmeraldList() {
        GameStateManager gameState = new GameStateManager();
        gameState.configureSpecialStageProgress(6, 6);
        GameRuntime runtime = mock(GameRuntime.class);
        when(runtime.getGameState()).thenReturn(gameState);

        Engine.restoreRuntimeFromDataSelectPayload(runtime, Map.of(
                "lives", 4,
                "continues", 2,
                "chaosEmeralds", List.of(0, 1, 2, 3, 4, 5)
        ));

        assertEquals(4, gameState.getLives());
        assertEquals(2, gameState.getContinues());
        assertEquals(6, gameState.getEmeraldCount());
        assertEquals(List.of(0, 1, 2, 3, 4, 5), gameState.getCollectedChaosEmeraldIndices());
    }

    @Test
    void restoreRuntimeFromDataSelectPayload_restoresS2ChaosEmeraldList() {
        GameStateManager gameState = new GameStateManager();
        gameState.configureSpecialStageProgress(7, 7);
        GameRuntime runtime = mock(GameRuntime.class);
        when(runtime.getGameState()).thenReturn(gameState);

        Engine.restoreRuntimeFromDataSelectPayload(runtime, Map.of(
                "lives", 8,
                "continues", 3,
                "chaosEmeralds", List.of(0, 1, 2, 3, 4, 5, 6)
        ));

        assertEquals(8, gameState.getLives());
        assertEquals(3, gameState.getContinues());
        assertEquals(7, gameState.getEmeraldCount());
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6), gameState.getCollectedChaosEmeraldIndices());
    }

    private void invokeMaybeStartS1DonatedDataSelectImageGeneration(Engine engine, Sonic1GameModule module)
            throws Exception {
        Method method = Engine.class.getDeclaredMethod(
                "maybeStartS1DonatedDataSelectImageGeneration", com.openggf.game.GameModule.class);
        method.setAccessible(true);
        method.invoke(engine, module);
    }

    private static final class TrackingS1ImageCacheManager extends S1DataSelectImageCacheManager
            implements Sonic1GameModule.S1DataSelectImageWarmup {
        int ensureStartedCalls;

        TrackingS1ImageCacheManager(Path cacheRoot) {
            super(cacheRoot,
                    SonicConfigurationService.getInstance(),
                    () -> "test-rom-sha",
                    new com.fasterxml.jackson.databind.ObjectMapper());
        }

        @Override
        public synchronized void ensureGenerationStarted() {
            ensureStartedCalls++;
        }
    }

    private static void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
