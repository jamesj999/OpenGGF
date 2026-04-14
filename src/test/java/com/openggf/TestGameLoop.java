package com.openggf;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.openggf.control.InputHandler;
import com.openggf.game.DataSelectProvider;
import com.openggf.game.dataselect.DataSelectAction;
import com.openggf.game.dataselect.DataSelectActionType;
import com.openggf.game.EngineServices;
import com.openggf.game.BonusStageType;
import com.openggf.game.GameMode;
import com.openggf.game.RuntimeManager;
import com.openggf.game.BonusStageProvider;
import com.openggf.game.GameModule;
import com.openggf.game.MasterTitleScreen;
import com.openggf.game.BonusStageState;
import com.openggf.game.EndingPhase;
import com.openggf.game.EndingProvider;
import com.openggf.game.GameRuntime;
import com.openggf.game.TitleScreenProvider;
import com.openggf.game.TitleScreenProvider.TitleScreenAction;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.save.SaveSessionContext;
import com.openggf.game.save.SelectedTeam;
import com.openggf.game.session.SessionManager;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameRng;
import com.openggf.game.dataselect.DataSelectPresentationProvider;
import com.openggf.game.dataselect.DataSelectSessionController;
import com.openggf.game.sonic3k.dataselect.S3kDataSelectProfile;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.dataselect.S3kDataSelectManager;
import com.openggf.graphics.FadeManager;
import com.openggf.level.SeamlessLevelTransitionRequest;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.level.objects.ObjectSpawn;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.lwjgl.glfw.GLFW.*;

/**
 * Tests for GameLoop class - the core game logic that can run headlessly.
 * These tests verify game state transitions and mode switching
 * without requiring an OpenGL context.
 */
public class TestGameLoop {

    private GameLoop gameLoop;
    private InputHandler mockInputHandler;

    @BeforeEach
    public void setUp() {
        RuntimeManager.configureEngineServices(EngineServices.fromLegacySingletonsForBootstrap());
        GameModuleRegistry.setCurrent(new Sonic2GameModule());
        RuntimeManager.createGameplay();
        mockInputHandler = mock(InputHandler.class);
        gameLoop = new GameLoop(mockInputHandler);
    }

    @AfterEach
    public void tearDown() {
        gameLoop = null;
        RuntimeManager.destroyCurrent();
        SessionManager.clear();
        GameModuleRegistry.setCurrent(new Sonic2GameModule());
    }

    // ==================== Value Object Tests ====================

    @Test
    void selectedTeam_valueObjectPreservesMainAndSidekicks() {
        com.openggf.game.save.SelectedTeam team = new com.openggf.game.save.SelectedTeam("knuckles", java.util.List.of("tails"));
        assertEquals("knuckles", team.mainCharacter());
        assertEquals(java.util.List.of("tails"), team.sidekicks());
    }

    // ==================== Enum Tests ====================

    @Test
    void gameMode_containsDataSelect() {
        assertNotNull(GameMode.valueOf("DATA_SELECT"));
    }

    @Test
    void dataSelectMode_canBeResolvedFromEnum() {
        assertEquals(GameMode.DATA_SELECT, GameMode.valueOf("DATA_SELECT"));
    }

    // ==================== Initialization Tests ====================

    @Test
    public void testGameLoopStartsInLevelMode() {
        assertEquals(GameMode.LEVEL, gameLoop.getCurrentGameMode(), "GameLoop should start in LEVEL mode");
    }

    @Test
    public void testGameLoopConstructorWithInputHandler() {
        GameLoop loop = new GameLoop(mockInputHandler);
        assertEquals(mockInputHandler, loop.getInputHandler(), "Input handler should be set via constructor");
    }

    @Test
    public void testSetInputHandler() {
        GameLoop loop = new GameLoop();
        assertNull(loop.getInputHandler(), "Input handler should be null initially");

        loop.setInputHandler(mockInputHandler);
        assertEquals(mockInputHandler, loop.getInputHandler(), "Input handler should be set");
    }

    @Test
    public void testStepWithoutInputHandlerThrows() {
        GameLoop loop = new GameLoop();
        assertThrows(IllegalStateException.class, loop::step);
    }

    @Test
    public void testMasterTitleScreenStepDoesNotRequireGameplayRuntime() {
        RuntimeManager.destroyCurrent();
        SessionManager.clear();

        InputHandler inputHandler = mock(InputHandler.class);
        MasterTitleScreen masterTitleScreen = mock(MasterTitleScreen.class);
        GameLoop loop = new GameLoop(inputHandler);
        loop.setGameMode(GameMode.MASTER_TITLE_SCREEN);
        loop.setMasterTitleScreenSupplier(() -> masterTitleScreen);

        assertDoesNotThrow(loop::step);
        verify(masterTitleScreen).update(inputHandler);
        verify(inputHandler).update();
    }

    @Test
    public void testMasterTitleScreenSelectionStartsBootstrapFadeWithoutGameplayRuntime() {
        RuntimeManager.destroyCurrent();
        SessionManager.clear();

        InputHandler inputHandler = mock(InputHandler.class);
        MasterTitleScreen masterTitleScreen = mock(MasterTitleScreen.class);
        when(masterTitleScreen.isGameSelected()).thenReturn(true);
        when(masterTitleScreen.getSelectedGameId()).thenReturn("s1");

        FadeManager fadeManager = RuntimeManager.currentEngineServices().graphics().getFadeManager();
        fadeManager.cancel();

        GameLoop loop = new GameLoop(inputHandler);
        loop.setGameMode(GameMode.MASTER_TITLE_SCREEN);
        loop.setMasterTitleScreenSupplier(() -> masterTitleScreen);
        loop.setMasterTitleExitHandler(gameId -> fail("Master title exit should wait for fade completion"));

        assertDoesNotThrow(loop::step);
        assertTrue(fadeManager.isActive(), "Bootstrap fade should start while no gameplay runtime exists");
    }

    // ==================== Game Mode Listener Tests ====================

    @Test
    public void testGameModeChangeListenerCanBeSet() {
        GameLoop.GameModeChangeListener listener = mock(GameLoop.GameModeChangeListener.class);
        gameLoop.setGameModeChangeListener(listener);
        // Verify the listener was accepted (no exception thrown, mode still valid)
        assertNotNull(gameLoop.getCurrentGameMode(), "Game mode should remain valid after setting listener");
    }

    // ==================== Mode Transition Guard Tests ====================

    @Test
    public void testGameModeStartsInLevelMode() {
        // When starting, should be in LEVEL mode
        assertEquals(GameMode.LEVEL, gameLoop.getCurrentGameMode(), "Should be in LEVEL mode");

        // Verify no results screen is active
        assertNull(gameLoop.getResultsScreen(), "Results screen should be null initially");
    }

    // ==================== Game Mode Accessor Tests ====================

    @Test
    public void testGetCurrentGameModeReturnsCorrectMode() {
        // Initially should be in LEVEL mode
        GameMode mode = gameLoop.getCurrentGameMode();
        assertNotNull(mode, "Game mode should not be null");
        assertEquals(GameMode.LEVEL, mode, "Should be in LEVEL mode");
    }

    @Test
    public void testResolveBonusStageDebugShortcutShiftB() {
        InputHandler handler = new InputHandler();
        handler.handleKeyEvent(GLFW_KEY_LEFT_SHIFT, GLFW_PRESS);
        handler.handleKeyEvent(GLFW_KEY_B, GLFW_PRESS);

        assertEquals(BonusStageType.GUMBALL, GameLoop.resolveBonusStageDebugShortcut(handler));
    }

    @Test
    public void testResolveBonusStageDebugShortcutCtrlB() {
        InputHandler handler = new InputHandler();
        handler.handleKeyEvent(GLFW_KEY_LEFT_CONTROL, GLFW_PRESS);
        handler.handleKeyEvent(GLFW_KEY_B, GLFW_PRESS);

        assertEquals(BonusStageType.GLOWING_SPHERE, GameLoop.resolveBonusStageDebugShortcut(handler));
    }

    @Test
    public void testResolveBonusStageDebugShortcutAltB() {
        InputHandler handler = new InputHandler();
        handler.handleKeyEvent(GLFW_KEY_LEFT_ALT, GLFW_PRESS);
        handler.handleKeyEvent(GLFW_KEY_B, GLFW_PRESS);

        assertEquals(BonusStageType.SLOT_MACHINE, GameLoop.resolveBonusStageDebugShortcut(handler));
    }

    @Test
    public void testResolveBonusStageDebugShortcutRequiresExactlyOneModifier() {
        InputHandler handler = new InputHandler();
        handler.handleKeyEvent(GLFW_KEY_LEFT_SHIFT, GLFW_PRESS);
        handler.handleKeyEvent(GLFW_KEY_LEFT_CONTROL, GLFW_PRESS);
        handler.handleKeyEvent(GLFW_KEY_B, GLFW_PRESS);

        assertEquals(BonusStageType.NONE, GameLoop.resolveBonusStageDebugShortcut(handler));
    }

    @Test
    public void testResolveBonusStageDebugShortcutIgnoresPlainB() {
        InputHandler handler = new InputHandler();
        handler.handleKeyEvent(GLFW_KEY_B, GLFW_PRESS);

        assertEquals(BonusStageType.NONE, GameLoop.resolveBonusStageDebugShortcut(handler));
    }

    @Test
    public void testResolveBonusStageBootstrapSpawnForPachinko() {
        ObjectSpawn spawn = GameLoop.resolveBonusStageBootstrapSpawn(BonusStageType.GLOWING_SPHERE);

        assertNotNull(spawn);
        assertEquals(0x78, spawn.x());
        assertEquals(0x0F30, spawn.y());
        assertEquals(Sonic3kObjectIds.PACHINKO_ENERGY_TRAP, spawn.objectId());
    }

    @Test
    public void testResolveBonusStageBootstrapSpawnOnlyForPachinko() {
        assertNull(GameLoop.resolveBonusStageBootstrapSpawn(BonusStageType.GUMBALL));
        assertNull(GameLoop.resolveBonusStageBootstrapSpawn(BonusStageType.SLOT_MACHINE));
        assertNull(GameLoop.resolveBonusStageBootstrapSpawn(BonusStageType.NONE));
    }

    @Test
    public void testExitTitleCardAppliesDeferredBonusStageSetupWithoutSavedState() throws Exception {
        BonusStageProvider provider = mock(BonusStageProvider.class);

        setPrivateField(gameLoop, "currentGameMode", GameMode.TITLE_CARD);
        setPrivateField(gameLoop, "postTitleCardDestination",
                enumConstant(GameLoop.class, "PostTitleCardDestination", "BONUS_STAGE"));
        setPrivateField(gameLoop, "deferredBonusProvider", provider);
        setPrivateField(gameLoop, "deferredBonusType", BonusStageType.SLOT_MACHINE);
        setPrivateField(gameLoop, "deferredBonusState", null);

        invokePrivateMethod(gameLoop, "exitTitleCard");

        verify(provider).onDeferredSetupComplete();
        assertNull(getPrivateField(gameLoop, "deferredBonusProvider"), "Deferred provider should be cleared after setup");
        assertNull(getPrivateField(gameLoop, "deferredBonusState"), "Deferred saved state should stay cleared after setup");
        assertEquals(GameMode.BONUS_STAGE, gameLoop.getCurrentGameMode(), "GameLoop should switch to bonus stage mode");
    }

    @Test
    public void testBonusStageExitFadeIsNotRestartedAfterProviderCompletedFade() {
        BonusStageProvider provider = mock(BonusStageProvider.class);
        when(provider.hasCompletedExitFadeToBlack()).thenReturn(true);

        assertFalse(GameLoop.shouldStartBonusStageExitFade(provider), "provider-owned GOAL fade should be reused instead of starting a second generic fade");
    }

    @Test
    void testExitDataSelectDispatchesPendingAction() throws Exception {
        RuntimeManager.destroyCurrent();
        SessionManager.clear();

        StubDataSelectProvider provider = new StubDataSelectProvider(new DataSelectAction(
                DataSelectActionType.NEW_SLOT_START, 2, 0, 0,
                new SelectedTeam("sonic", List.of("tails"))));
        GameModule module = mock(GameModule.class);
        when(module.getDataSelectProvider()).thenReturn(provider);
        when(module.rngFlavour()).thenReturn(GameRng.Flavour.S1_S2);

        GameModuleRegistry.setCurrent(new Sonic2GameModule());
        GameplayModeContext gameplayMode = SessionManager.openGameplaySession(module);
        RuntimeManager.createGameplay(gameplayMode);

        AtomicReference<DataSelectAction> handled = new AtomicReference<>();
        gameLoop.setDataSelectActionHandler(handled::set);
        FadeManager fadeManager = mock(FadeManager.class);
        setPrivateField(gameLoop, "fadeManager", fadeManager);
        AtomicReference<Runnable> fadeCallback = new AtomicReference<>();
        doAnswer(invocation -> {
            fadeCallback.set(invocation.getArgument(0));
            return null;
        }).when(fadeManager).startFadeToBlack(any());

        invokePrivateMethod(gameLoop, "exitDataSelect");

        assertNotNull(fadeCallback.get());
        fadeCallback.get().run();
        assertNotNull(handled.get());
        assertEquals(DataSelectActionType.NEW_SLOT_START, handled.get().type());
        assertEquals(2, handled.get().slot());
        assertEquals(new SelectedTeam("sonic", List.of("tails")), handled.get().team());
    }

    @Test
    void testExitDataSelectDispatchesPendingActionFromNativeS3kPresentationProvider() throws Exception {
        RuntimeManager.destroyCurrent();
        SessionManager.clear();

        Sonic3kGameModule module = new Sonic3kGameModule();
        DataSelectProvider provider = module.getDataSelectProvider();
        assertInstanceOf(DataSelectPresentationProvider.class, provider);
        assertInstanceOf(S3kDataSelectManager.class, ((DataSelectPresentationProvider) provider).delegate(),
                "S3K data select should resolve to the native S3K manager");
        ((DataSelectPresentationProvider) provider).controller().queuePendingAction(new DataSelectAction(
                DataSelectActionType.NEW_SLOT_START, 3, 0, 0,
                new SelectedTeam("sonic", List.of("tails"))));

        GameModuleRegistry.setCurrent(new Sonic2GameModule());
        GameplayModeContext gameplayMode = SessionManager.openGameplaySession(module);
        gameLoop.setRuntime(RuntimeManager.createGameplay(gameplayMode));

        AtomicReference<DataSelectAction> handled = new AtomicReference<>();
        gameLoop.setDataSelectActionHandler(handled::set);
        FadeManager fadeManager = mock(FadeManager.class);
        setPrivateField(gameLoop, "fadeManager", fadeManager);
        AtomicReference<Runnable> fadeCallback = new AtomicReference<>();
        doAnswer(invocation -> {
            fadeCallback.set(invocation.getArgument(0));
            return null;
        }).when(fadeManager).startFadeToBlack(any());

        invokePrivateMethod(gameLoop, "exitDataSelect");

        assertNotNull(fadeCallback.get());
        fadeCallback.get().run();
        assertNotNull(handled.get(), "Wrapped S3K presentation should still dispatch queued actions");
        assertEquals(DataSelectActionType.NEW_SLOT_START, handled.get().type());
        assertEquals(3, handled.get().slot());
    }

    @Test
    void testExitDataSelectStartsFadeBeforeDispatchingGameplayAction() throws Exception {
        RuntimeManager.destroyCurrent();
        SessionManager.clear();

        StubDataSelectProvider provider = new StubDataSelectProvider(new DataSelectAction(
                DataSelectActionType.LOAD_SLOT, 2, 3, 1,
                new SelectedTeam("tails", List.of())));
        GameModule module = mock(GameModule.class);
        when(module.getDataSelectProvider()).thenReturn(provider);
        when(module.rngFlavour()).thenReturn(GameRng.Flavour.S1_S2);

        GameModuleRegistry.setCurrent(new Sonic2GameModule());
        GameplayModeContext gameplayMode = SessionManager.openGameplaySession(module);
        RuntimeManager.createGameplay(gameplayMode);

        AtomicReference<DataSelectAction> handled = new AtomicReference<>();
        gameLoop.setDataSelectActionHandler(handled::set);

        FadeManager fadeManager = mock(FadeManager.class);
        setPrivateField(gameLoop, "fadeManager", fadeManager);

        AtomicReference<Runnable> fadeCallback = new AtomicReference<>();
        doAnswer(invocation -> {
            fadeCallback.set(invocation.getArgument(0));
            return null;
        }).when(fadeManager).startFadeToBlack(any());

        invokePrivateMethod(gameLoop, "exitDataSelect");

        assertNull(handled.get(), "gameplay action should wait for fade completion");
        verify(fadeManager).startFadeToBlack(any());
        assertNotNull(fadeCallback.get(), "fade callback should capture the deferred Data Select launch");
        assertEquals(com.openggf.game.DataSelectProvider.State.EXITING, provider.getState(),
                "Data Select should stay in its exiting state until fade completion");

        fadeCallback.get().run();

        assertNotNull(handled.get());
        assertEquals(DataSelectActionType.LOAD_SLOT, handled.get().type());
        assertEquals(2, handled.get().slot());
        verify(fadeManager).startFadeFromBlack(isNull());
        assertEquals(com.openggf.game.DataSelectProvider.State.INACTIVE, provider.getState(),
                "Data Select should reset only after the fade callback runs");
    }

    @Test
    void testExitDataSelectDoesNotResetWhileFadeAlreadyActive() throws Exception {
        RuntimeManager.destroyCurrent();
        SessionManager.clear();

        StubDataSelectProvider provider = new StubDataSelectProvider(new DataSelectAction(
                DataSelectActionType.LOAD_SLOT, 2, 3, 1,
                new SelectedTeam("tails", List.of())));
        GameModule module = mock(GameModule.class);
        when(module.getDataSelectProvider()).thenReturn(provider);
        when(module.rngFlavour()).thenReturn(GameRng.Flavour.S1_S2);

        GameplayModeContext gameplayMode = SessionManager.openGameplaySession(module);
        RuntimeManager.createGameplay(gameplayMode);

        gameLoop.setDataSelectActionHandler(action -> fail("No dispatch should occur while fade is already active"));

        FadeManager fadeManager = mock(FadeManager.class);
        when(fadeManager.isActive()).thenReturn(true);
        setPrivateField(gameLoop, "fadeManager", fadeManager);

        invokePrivateMethod(gameLoop, "exitDataSelect");

        assertEquals(com.openggf.game.DataSelectProvider.State.EXITING, provider.getState(),
                "Provider should remain in EXITING while the fade is still active");
        verify(fadeManager, never()).startFadeToBlack(any());
    }

    @Test
    void testDoExitBonusStageDoesNotWriteSaveForActiveSlot() throws Exception {
        RuntimeManager.destroyCurrent();
        SessionManager.clear();

        String gameCode = "test_bonus_stage_return";
        Path saveDir = Path.of("saves").resolve(gameCode);
        deleteRecursively(saveDir);

        GameModule module = mock(GameModule.class);
        when(module.getSaveSnapshotProvider()).thenReturn((reason, ctx) -> Map.of("marker", "bonus"));
        when(module.getTitleCardProvider()).thenReturn(null);
        when(module.rngFlavour()).thenReturn(GameRng.Flavour.S1_S2);

        SaveSessionContext saveContext = SaveSessionContext.forSlot(
                gameCode, 1, new SelectedTeam("sonic", List.of()), 0, 0);
        GameModuleRegistry.setCurrent(new Sonic2GameModule());
        GameplayModeContext gameplayMode = SessionManager.openGameplaySession(module, saveContext);
        gameLoop.setRuntime(RuntimeManager.createGameplay(gameplayMode));

        com.openggf.level.LevelManager levelManager = mock(com.openggf.level.LevelManager.class);
        when(levelManager.getCurrentLevelMusicId()).thenReturn(-1);
        when(levelManager.getCheckpointState()).thenReturn(null);
        when(levelManager.getFeatureZoneId()).thenReturn(0);
        when(levelManager.getFeatureActId()).thenReturn(0);

        com.openggf.sprites.managers.SpriteManager spriteManager = mock(com.openggf.sprites.managers.SpriteManager.class);
        when(spriteManager.getSprite(anyString())).thenReturn(null);
        when(spriteManager.getSidekicks()).thenReturn(List.of());

        setPrivateField(gameLoop, "levelManager", levelManager);
        setPrivateField(gameLoop, "spriteManager", spriteManager);
        setPrivateField(gameLoop, "camera", mock(com.openggf.camera.Camera.class));
        setPrivateField(gameLoop, "fadeManager", mock(FadeManager.class));

        BonusStageProvider provider = mock(BonusStageProvider.class);
        when(provider.getRewards()).thenReturn(BonusStageProvider.BonusStageRewards.none());
        BonusStageState savedState = new BonusStageState(
                0, 0, 0, 0, -1, 0, 0, 0,
                0, 0, 0, 0, (byte) 0, (byte) 0, 0, 0L, 0);

        Method method = GameLoop.class.getDeclaredMethod(
                "doExitBonusStage", BonusStageProvider.class, BonusStageState.class);
        method.setAccessible(true);
        method.invoke(gameLoop, provider, savedState);

        assertTrue(Files.notExists(saveDir.resolve("slot1.json")));
        deleteRecursively(saveDir);
    }

    @Test
    void testDoExitResultsScreenWritesSaveForActiveSlot() throws Exception {
        RuntimeManager.destroyCurrent();
        SessionManager.clear();

        String gameCode = "test_special_stage_return";
        Path saveDir = Path.of("saves").resolve(gameCode);
        deleteRecursively(saveDir);

        GameModule module = mock(GameModule.class);
        when(module.getSaveSnapshotProvider()).thenReturn((reason, ctx) -> Map.of("marker", "special"));
        when(module.rngFlavour()).thenReturn(GameRng.Flavour.S1_S2);

        SaveSessionContext saveContext = SaveSessionContext.forSlot(
                gameCode, 1, new SelectedTeam("sonic", List.of()), 0, 0);
        GameModuleRegistry.setCurrent(new Sonic2GameModule());
        GameplayModeContext gameplayMode = SessionManager.openGameplaySession(module, saveContext);
        gameLoop.setRuntime(RuntimeManager.createGameplay(gameplayMode));

        com.openggf.level.LevelManager levelManager = mock(com.openggf.level.LevelManager.class);
        when(levelManager.getCurrentLevel()).thenReturn(mock(com.openggf.level.AbstractLevel.class));
        when(levelManager.getCurrentZone()).thenReturn(0);
        when(levelManager.getCurrentAct()).thenReturn(0);
        when(levelManager.getCurrentLevelMusicId()).thenReturn(-1);
        when(levelManager.getCheckpointState()).thenReturn(null);
        when(levelManager.hasBigRingReturn()).thenReturn(false);

        com.openggf.sprites.managers.SpriteManager spriteManager = mock(com.openggf.sprites.managers.SpriteManager.class);
        when(spriteManager.getSprite(anyString())).thenReturn(null);
        when(spriteManager.getSidekicks()).thenReturn(List.of());

        setPrivateField(gameLoop, "levelManager", levelManager);
        setPrivateField(gameLoop, "spriteManager", spriteManager);
        setPrivateField(gameLoop, "camera", mock(com.openggf.camera.Camera.class));
        setPrivateField(gameLoop, "fadeManager", mock(FadeManager.class));
        setPrivateField(gameLoop, "resultsScreen", mock(com.openggf.game.ResultsScreen.class));
        setPrivateField(gameLoop, "currentGameMode", GameMode.SPECIAL_STAGE_RESULTS);

        invokePrivateMethod(gameLoop, "doExitResultsScreen");

        assertTrue(Files.exists(saveDir.resolve("slot1.json")));
        deleteRecursively(saveDir);
    }

    @Test
    void testStepDoesNotWriteSaveForActiveSlotOnSeamlessTransition() throws Exception {
        RuntimeManager.destroyCurrent();
        SessionManager.clear();
        gameLoop.setRuntime(null);

        String gameCode = "test_seamless_transition";
        Path saveDir = Path.of("saves").resolve(gameCode);
        deleteRecursively(saveDir);

        GameModule module = mock(GameModule.class);
        when(module.getSaveSnapshotProvider()).thenReturn((reason, ctx) -> Map.of("marker", "seamless"));
        when(module.getTitleCardProvider()).thenReturn(null);
        when(module.rngFlavour()).thenReturn(GameRng.Flavour.S1_S2);

        SaveSessionContext saveContext = SaveSessionContext.forSlot(
                gameCode, 1, new SelectedTeam("sonic", List.of()), 0, 0);
        SessionManager.openGameplaySession(module, saveContext);

        com.openggf.level.LevelManager levelManager = mock(com.openggf.level.LevelManager.class);
        com.openggf.sprites.managers.SpriteManager spriteManager = mock(com.openggf.sprites.managers.SpriteManager.class);
        SeamlessLevelTransitionRequest request = SeamlessLevelTransitionRequest
                .builder(SeamlessLevelTransitionRequest.TransitionType.RELOAD_TARGET_LEVEL)
                .targetZoneAct(1, 0)
                .build();
        when(levelManager.consumeSeamlessTransitionRequest()).thenReturn(request);
        when(levelManager.consumeInLevelTitleCardRequest()).thenReturn(false);

        GameRuntime runtime = mock(GameRuntime.class);
        when(runtime.getLevelManager()).thenReturn(levelManager);
        when(runtime.getSpriteManager()).thenReturn(spriteManager);
        when(runtime.getCamera()).thenReturn(mock(com.openggf.camera.Camera.class));
        when(runtime.getTimers()).thenReturn(mock(com.openggf.timer.TimerManager.class));
        when(runtime.getGameState()).thenReturn(mock(com.openggf.game.GameStateManager.class));
        when(runtime.getFadeManager()).thenReturn(mock(FadeManager.class));
        when(runtime.getWaterSystem()).thenReturn(mock(com.openggf.level.WaterSystem.class));
        gameLoop.setRuntime(runtime);

        setPrivateField(gameLoop, "currentGameMode", GameMode.LEVEL);

        gameLoop.step();

        verify(levelManager).applySeamlessTransition(request);
        assertTrue(Files.notExists(saveDir.resolve("slot1.json")));
        deleteRecursively(saveDir);
    }

    @Test
    void testDoEnterEndingDoesNotWriteSaveForActiveSlot() throws Exception {
        RuntimeManager.destroyCurrent();
        SessionManager.clear();

        String gameCode = "test_ending_clear";
        Path saveDir = Path.of("saves").resolve(gameCode);
        deleteRecursively(saveDir);

        EndingProvider endingProvider = mock(EndingProvider.class);
        when(endingProvider.getCurrentPhase()).thenReturn(EndingPhase.CUTSCENE);

        GameModule module = mock(GameModule.class);
        when(module.getEndingProvider()).thenReturn(endingProvider);
        when(module.getSaveSnapshotProvider()).thenReturn(
                (reason, ctx) -> Map.of("clear", ctx.saveSessionContext().isClear(), "marker", "ending"));
        when(module.rngFlavour()).thenReturn(GameRng.Flavour.S1_S2);

        SaveSessionContext saveContext = SaveSessionContext.forSlot(
                gameCode, 1, new SelectedTeam("sonic", List.of()), 0, 0);
        GameplayModeContext gameplayMode = SessionManager.openGameplaySession(module, saveContext);
        gameLoop.setRuntime(RuntimeManager.createGameplay(gameplayMode));

        invokePrivateMethod(gameLoop, "doEnterEnding");

        Path slotFile = saveDir.resolve("slot1.json");
        assertTrue(Files.notExists(slotFile));
        assertFalse(saveContext.isClear());
        verify(endingProvider).initialize();
        deleteRecursively(saveDir);
    }

    @Test
    void testDoExitTitleScreenRoutesOnePlayerToNativeDataSelect() throws Exception {
        RuntimeManager.destroyCurrent();
        SessionManager.clear();
        com.openggf.configuration.SonicConfigurationService.getInstance()
                .setConfigValue(com.openggf.configuration.SonicConfiguration.LEVEL_SELECT_ON_STARTUP, false);

        StubTitleScreenProvider titleScreen = new StubTitleScreenProvider(TitleScreenAction.ONE_PLAYER);
        TrackingNativeDataSelectProvider nativeDelegate = new TrackingNativeDataSelectProvider();
        DataSelectPresentationProvider dataSelect = new DataSelectPresentationProvider(
                nativeDelegate,
                new DataSelectSessionController(new S3kDataSelectProfile()));
        GameModule module = mock(GameModule.class);
        when(module.getTitleScreenProvider()).thenReturn(titleScreen);
        when(module.getDataSelectProvider()).thenReturn(dataSelect);
        when(module.getGameId()).thenReturn(com.openggf.game.GameId.S3K);
        when(module.rngFlavour()).thenReturn(GameRng.Flavour.S3K);
        GameplayModeContext gameplayMode = SessionManager.openGameplaySession(module);
        gameLoop.setRuntime(RuntimeManager.createGameplay(gameplayMode));

        com.openggf.level.LevelManager levelManager = mock(com.openggf.level.LevelManager.class);
        setPrivateField(gameLoop, "levelManager", levelManager);
        setPrivateField(gameLoop, "currentGameMode", GameMode.TITLE_SCREEN);

        invokePrivateMethod(gameLoop, "doExitTitleScreen");

        assertEquals(GameMode.DATA_SELECT, gameLoop.getCurrentGameMode());
        assertEquals(1, nativeDelegate.initializeCalls);
        verify(levelManager, never()).loadZoneAndAct(anyInt(), anyInt());
    }

    @Test
    void testDoExitTitleScreenRoutesS1OnePlayerToDonatedDataSelectWhenPresentationResolvesToS3k() throws Exception {
        RuntimeManager.destroyCurrent();
        SessionManager.clear();
        com.openggf.configuration.SonicConfigurationService.getInstance()
                .setConfigValue(com.openggf.configuration.SonicConfiguration.LEVEL_SELECT_ON_STARTUP, false);

        StubTitleScreenProvider titleScreen = new StubTitleScreenProvider(TitleScreenAction.ONE_PLAYER);
        DataSelectPresentationProvider dataSelect = new DataSelectPresentationProvider(
                S3kDataSelectManager::new,
                new DataSelectSessionController(new S3kDataSelectProfile()));
        GameModule module = mock(GameModule.class);
        when(module.getTitleScreenProvider()).thenReturn(titleScreen);
        when(module.getDataSelectProvider()).thenReturn(dataSelect);
        when(module.getGameId()).thenReturn(com.openggf.game.GameId.S1);
        when(module.rngFlavour()).thenReturn(GameRng.Flavour.S1_S2);
        GameplayModeContext gameplayMode = SessionManager.openGameplaySession(module);
        gameLoop.setRuntime(RuntimeManager.createGameplay(gameplayMode));

        assertInstanceOf(S3kDataSelectManager.class, dataSelect.delegate(),
                "Donated S1 data select should resolve to the native S3K manager");

        com.openggf.level.LevelManager levelManager = mock(com.openggf.level.LevelManager.class);
        setPrivateField(gameLoop, "levelManager", levelManager);
        setPrivateField(gameLoop, "currentGameMode", GameMode.TITLE_SCREEN);

        invokePrivateMethod(gameLoop, "doExitTitleScreen");

        assertEquals(GameMode.DATA_SELECT, gameLoop.getCurrentGameMode());
        verify(levelManager, never()).loadZoneAndAct(anyInt(), anyInt());
    }

    @Test
    void testDoExitTitleScreenRoutesS2OnePlayerToDonatedDataSelectWhenPresentationResolvesToS3k() throws Exception {
        RuntimeManager.destroyCurrent();
        SessionManager.clear();
        com.openggf.configuration.SonicConfigurationService.getInstance()
                .setConfigValue(com.openggf.configuration.SonicConfiguration.LEVEL_SELECT_ON_STARTUP, false);

        StubTitleScreenProvider titleScreen = new StubTitleScreenProvider(TitleScreenAction.ONE_PLAYER);
        DataSelectPresentationProvider dataSelect = new DataSelectPresentationProvider(
                S3kDataSelectManager::new,
                new DataSelectSessionController(new S3kDataSelectProfile()));
        GameModule module = mock(GameModule.class);
        when(module.getTitleScreenProvider()).thenReturn(titleScreen);
        when(module.getDataSelectProvider()).thenReturn(dataSelect);
        when(module.getGameId()).thenReturn(com.openggf.game.GameId.S2);
        when(module.rngFlavour()).thenReturn(GameRng.Flavour.S1_S2);
        GameplayModeContext gameplayMode = SessionManager.openGameplaySession(module);
        gameLoop.setRuntime(RuntimeManager.createGameplay(gameplayMode));

        assertInstanceOf(S3kDataSelectManager.class, dataSelect.delegate(),
                "Donated S2 data select should resolve to the native S3K manager");

        com.openggf.level.LevelManager levelManager = mock(com.openggf.level.LevelManager.class);
        setPrivateField(gameLoop, "levelManager", levelManager);
        setPrivateField(gameLoop, "currentGameMode", GameMode.TITLE_SCREEN);

        invokePrivateMethod(gameLoop, "doExitTitleScreen");

        assertEquals(GameMode.DATA_SELECT, gameLoop.getCurrentGameMode());
        verify(levelManager, never()).loadZoneAndAct(anyInt(), anyInt());
    }

    @Test
    void testDoExitTitleScreenRoutesTwoPlayerAwayFromDataSelect() throws Exception {
        RuntimeManager.destroyCurrent();
        SessionManager.clear();
        com.openggf.configuration.SonicConfigurationService.getInstance()
                .setConfigValue(com.openggf.configuration.SonicConfiguration.LEVEL_SELECT_ON_STARTUP, false);

        StubTitleScreenProvider titleScreen = new StubTitleScreenProvider(TitleScreenAction.TWO_PLAYER);
        TrackingNativeDataSelectProvider nativeDelegate = new TrackingNativeDataSelectProvider();
        DataSelectPresentationProvider dataSelect = new DataSelectPresentationProvider(
                nativeDelegate,
                new DataSelectSessionController(new S3kDataSelectProfile()));
        GameModule module = mock(GameModule.class);
        when(module.getTitleScreenProvider()).thenReturn(titleScreen);
        when(module.getDataSelectProvider()).thenReturn(dataSelect);
        when(module.getGameId()).thenReturn(com.openggf.game.GameId.S3K);
        when(module.rngFlavour()).thenReturn(GameRng.Flavour.S3K);
        GameplayModeContext gameplayMode = SessionManager.openGameplaySession(module);
        gameLoop.setRuntime(RuntimeManager.createGameplay(gameplayMode));

        com.openggf.level.LevelManager levelManager = mock(com.openggf.level.LevelManager.class);
        setPrivateField(gameLoop, "levelManager", levelManager);
        setPrivateField(gameLoop, "currentGameMode", GameMode.TITLE_SCREEN);

        invokePrivateMethod(gameLoop, "doExitTitleScreen");

        assertEquals(GameMode.LEVEL, gameLoop.getCurrentGameMode());
        assertEquals(0, nativeDelegate.initializeCalls);
        verify(levelManager).loadZoneAndAct(0, 0);
    }

    @Test
    void testDoExitTitleScreenRoutesToLevelWhenPresentationIsNotS3k() throws Exception {
        RuntimeManager.destroyCurrent();
        SessionManager.clear();
        com.openggf.configuration.SonicConfigurationService.getInstance()
                .setConfigValue(com.openggf.configuration.SonicConfiguration.LEVEL_SELECT_ON_STARTUP, false);

        com.openggf.game.TitleScreenProvider titleScreen = mock(com.openggf.game.TitleScreenProvider.class);
        StubDataSelectProvider dataSelect = new StubDataSelectProvider(DataSelectAction.none());
        GameModule module = mock(GameModule.class);
        when(module.getTitleScreenProvider()).thenReturn(titleScreen);
        when(module.getDataSelectProvider()).thenReturn(dataSelect);
        when(module.getGameId()).thenReturn(com.openggf.game.GameId.S2);
        when(module.rngFlavour()).thenReturn(GameRng.Flavour.S1_S2);
        GameplayModeContext gameplayMode = SessionManager.openGameplaySession(module);
        gameLoop.setRuntime(RuntimeManager.createGameplay(gameplayMode));

        com.openggf.level.LevelManager levelManager = mock(com.openggf.level.LevelManager.class);
        setPrivateField(gameLoop, "levelManager", levelManager);
        setPrivateField(gameLoop, "currentGameMode", GameMode.TITLE_SCREEN);

        invokePrivateMethod(gameLoop, "doExitTitleScreen");

        assertEquals(GameMode.LEVEL, gameLoop.getCurrentGameMode());
        verify(titleScreen).reset();
        verify(levelManager).loadZoneAndAct(0, 0);
        assertTrue(dataSelect.isActive(), "Provider presence alone should not trigger Data Select");
    }

    @Test
    void testTitleScreenExitHandlerUsesExplicitRouteResolutionWithoutStartingSecondFade() throws Exception {
        RuntimeManager.destroyCurrent();
        SessionManager.clear();
        com.openggf.configuration.SonicConfigurationService.getInstance()
                .setConfigValue(com.openggf.configuration.SonicConfiguration.LEVEL_SELECT_ON_STARTUP, false);

        StubTitleScreenProvider titleScreen = new StubTitleScreenProvider(TitleScreenProvider.TitleScreenAction.OPTIONS);
        StubDataSelectProvider dataSelect = new StubDataSelectProvider(DataSelectAction.none());
        GameModule module = mock(GameModule.class);
        when(module.getTitleScreenProvider()).thenReturn(titleScreen);
        when(module.getDataSelectProvider()).thenReturn(dataSelect);
        when(module.getGameId()).thenReturn(com.openggf.game.GameId.S3K);
        when(module.rngFlavour()).thenReturn(GameRng.Flavour.S3K);
        GameplayModeContext gameplayMode = SessionManager.openGameplaySession(module);
        gameLoop.setRuntime(RuntimeManager.createGameplay(gameplayMode));

        com.openggf.level.LevelManager levelManager = mock(com.openggf.level.LevelManager.class);
        FadeManager fadeManager = mock(FadeManager.class);
        when(fadeManager.isActive()).thenReturn(false);
        setPrivateField(gameLoop, "levelManager", levelManager);
        setPrivateField(gameLoop, "currentGameMode", GameMode.TITLE_SCREEN);
        setPrivateField(gameLoop, "fadeManager", fadeManager);

        gameLoop.getTitleScreenProvider();
        titleScreen.triggerExitHandler();

        assertEquals(GameMode.LEVEL, gameLoop.getCurrentGameMode());
        verify(levelManager).loadZoneAndAct(0, 0);
        verify(fadeManager, never()).startFadeToBlack(any());
        assertTrue(dataSelect.isActive(), "Explicit route resolution should prevent OPTIONS from entering Data Select");
    }

    @Test
    void testTitleScreenExitHandlerStartsFadeBeforeEnteringDataSelect() throws Exception {
        RuntimeManager.destroyCurrent();
        SessionManager.clear();
        com.openggf.configuration.SonicConfigurationService.getInstance()
                .setConfigValue(com.openggf.configuration.SonicConfiguration.LEVEL_SELECT_ON_STARTUP, false);

        StubTitleScreenProvider titleScreen = new StubTitleScreenProvider(TitleScreenAction.ONE_PLAYER);
        TrackingNativeDataSelectProvider nativeDelegate = new TrackingNativeDataSelectProvider();
        DataSelectPresentationProvider dataSelect = new DataSelectPresentationProvider(
                nativeDelegate,
                new DataSelectSessionController(new S3kDataSelectProfile()));
        GameModule module = mock(GameModule.class);
        when(module.getTitleScreenProvider()).thenReturn(titleScreen);
        when(module.getDataSelectProvider()).thenReturn(dataSelect);
        when(module.getGameId()).thenReturn(com.openggf.game.GameId.S3K);
        when(module.rngFlavour()).thenReturn(GameRng.Flavour.S3K);
        GameplayModeContext gameplayMode = SessionManager.openGameplaySession(module);
        gameLoop.setRuntime(RuntimeManager.createGameplay(gameplayMode));

        com.openggf.level.LevelManager levelManager = mock(com.openggf.level.LevelManager.class);
        FadeManager fadeManager = mock(FadeManager.class);
        when(fadeManager.isActive()).thenReturn(false);
        AtomicReference<Runnable> fadeCallback = new AtomicReference<>();
        doAnswer(invocation -> {
            fadeCallback.set(invocation.getArgument(0));
            return null;
        }).when(fadeManager).startFadeToBlack(any());
        setPrivateField(gameLoop, "levelManager", levelManager);
        setPrivateField(gameLoop, "currentGameMode", GameMode.TITLE_SCREEN);
        setPrivateField(gameLoop, "fadeManager", fadeManager);

        gameLoop.getTitleScreenProvider();
        titleScreen.triggerExitHandler();

        verify(fadeManager).startFadeToBlack(any());
        assertEquals(GameMode.TITLE_SCREEN, gameLoop.getCurrentGameMode(),
                "Data Select should wait for fade completion before entering");
        assertEquals(0, nativeDelegate.initializeCalls,
                "Data Select must not initialize until the title fade completes");

        assertNotNull(fadeCallback.get(), "Title -> Data Select should register a fade completion callback");
        fadeCallback.get().run();

        assertEquals(GameMode.DATA_SELECT, gameLoop.getCurrentGameMode());
        assertEquals(1, nativeDelegate.initializeCalls);
        verify(levelManager, never()).loadZoneAndAct(anyInt(), anyInt());
    }

    @Test
    void testDoExitTitleScreenDefaultsUnknownActionToOtherInsteadOfDataSelect() throws Exception {
        RuntimeManager.destroyCurrent();
        SessionManager.clear();
        com.openggf.configuration.SonicConfigurationService.getInstance()
                .setConfigValue(com.openggf.configuration.SonicConfiguration.LEVEL_SELECT_ON_STARTUP, false);

        com.openggf.game.TitleScreenProvider titleScreen = mock(com.openggf.game.TitleScreenProvider.class);
        StubDataSelectProvider dataSelect = new StubDataSelectProvider(DataSelectAction.none());
        GameModule module = mock(GameModule.class);
        when(module.getTitleScreenProvider()).thenReturn(titleScreen);
        when(module.getDataSelectProvider()).thenReturn(dataSelect);
        when(module.getGameId()).thenReturn(com.openggf.game.GameId.S3K);
        when(module.rngFlavour()).thenReturn(GameRng.Flavour.S3K);
        GameplayModeContext gameplayMode = SessionManager.openGameplaySession(module);
        gameLoop.setRuntime(RuntimeManager.createGameplay(gameplayMode));

        com.openggf.level.LevelManager levelManager = mock(com.openggf.level.LevelManager.class);
        setPrivateField(gameLoop, "levelManager", levelManager);
        setPrivateField(gameLoop, "currentGameMode", GameMode.TITLE_SCREEN);

        invokePrivateMethod(gameLoop, "doExitTitleScreen");

        assertEquals(GameMode.LEVEL, gameLoop.getCurrentGameMode());
        verify(levelManager).loadZoneAndAct(0, 0);
        assertTrue(dataSelect.isActive(), "Unknown title actions must fail closed instead of entering Data Select");
    }

    @Test
    void testTitleScreenExitHandlerUsesOverlayPathWhenLevelSelectOverlayApplies() throws Exception {
        RuntimeManager.destroyCurrent();
        SessionManager.clear();
        com.openggf.configuration.SonicConfigurationService.getInstance()
                .setConfigValue(com.openggf.configuration.SonicConfiguration.LEVEL_SELECT_ON_STARTUP, true);

        StubTitleScreenProvider titleScreen = new StubTitleScreenProvider(TitleScreenAction.ONE_PLAYER);
        titleScreen.supportsLevelSelectOverlay = true;
        GameModule module = mock(GameModule.class);
        com.openggf.game.LevelSelectProvider levelSelect = mock(com.openggf.game.LevelSelectProvider.class);
        when(module.getTitleScreenProvider()).thenReturn(titleScreen);
        when(module.getLevelSelectProvider()).thenReturn(levelSelect);
        when(module.getDataSelectProvider()).thenReturn(new StubDataSelectProvider(DataSelectAction.none()));
        when(module.getGameId()).thenReturn(com.openggf.game.GameId.S1);
        when(module.rngFlavour()).thenReturn(GameRng.Flavour.S1_S2);
        GameplayModeContext gameplayMode = SessionManager.openGameplaySession(module);
        gameLoop.setRuntime(RuntimeManager.createGameplay(gameplayMode));

        com.openggf.level.LevelManager levelManager = mock(com.openggf.level.LevelManager.class);
        setPrivateField(gameLoop, "levelManager", levelManager);
        setPrivateField(gameLoop, "currentGameMode", GameMode.TITLE_SCREEN);
        setPrivateField(gameLoop, "fadeManager", mock(FadeManager.class));

        GameModuleRegistry.setCurrent(module);
        gameLoop.getTitleScreenProvider();
        titleScreen.triggerExitHandler();

        assertEquals(GameMode.LEVEL_SELECT, gameLoop.getCurrentGameMode());
        verify(levelManager, never()).loadZoneAndAct(anyInt(), anyInt());
        verify(levelSelect).initializeFromTitleScreen();
        verify(levelSelect, never()).initialize();
        verify((FadeManager) getPrivateField(gameLoop, "fadeManager"), never()).startFadeToBlack(any());
    }

    private static void invokePrivateMethod(Object target, String methodName) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        method.invoke(target);
    }

    private static void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getPrivateField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object enumConstant(Class<?> ownerClass, String nestedTypeName, String constantName)
            throws Exception {
        Class<?> nestedType = Class.forName(ownerClass.getName() + "$" + nestedTypeName);
        return Enum.valueOf((Class<? extends Enum>) nestedType.asSubclass(Enum.class), constantName);
    }

    private static void deleteRecursively(Path path) throws Exception {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var walk = Files.walk(path)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    private static final class StubDataSelectProvider extends com.openggf.game.dataselect.AbstractDataSelectProvider {
        private StubDataSelectProvider(DataSelectAction action) {
            this.pendingAction = action;
            this.state = State.EXITING;
        }

        @Override
        public void initialize() {
        }

        @Override
        public void update(InputHandler input) {
        }

        @Override
        public void draw() {
        }

        @Override
        public void setClearColor() {
        }

        @Override
        public void reset() {
            state = State.INACTIVE;
            pendingAction = DataSelectAction.none();
        }

        @Override
        public State getState() {
            return state;
        }

        @Override
        public boolean isExiting() {
            return state == State.EXITING;
        }

        @Override
        public boolean isActive() {
            return state != State.INACTIVE;
        }
    }

    private static final class StubTitleScreenProvider implements TitleScreenProvider {
        private final TitleScreenAction exitAction;
        private boolean supportsLevelSelectOverlay;
        private Runnable exitHandler = () -> {};

        private StubTitleScreenProvider(TitleScreenAction exitAction) {
            this.exitAction = exitAction;
        }

        private void triggerExitHandler() {
            exitHandler.run();
        }

        @Override
        public TitleScreenAction consumeExitAction() {
            return exitAction;
        }

        @Override
        public boolean supportsLevelSelectOverlay() {
            return supportsLevelSelectOverlay;
        }

        @Override
        public void initialize() {
        }

        @Override
        public void update(InputHandler input) {
        }

        @Override
        public void draw() {
        }

        @Override
        public void setClearColor() {
        }

        @Override
        public void reset() {
        }

        @Override
        public State getState() {
            return State.ACTIVE;
        }

        @Override
        public boolean isExiting() {
            return false;
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public void setExitToLevelHandler(Runnable handler) {
            exitHandler = handler != null ? handler : () -> {};
        }
    }

    private static final class TrackingNativeDataSelectProvider
            extends com.openggf.game.dataselect.AbstractDataSelectProvider {
        private int initializeCalls;

        @Override
        public void initialize() {
            initializeCalls++;
            state = State.FADE_IN;
        }

        @Override
        public void update(InputHandler input) {
        }

        @Override
        public void draw() {
        }

        @Override
        public void setClearColor() {
        }

        @Override
        public void reset() {
            state = State.INACTIVE;
        }

        @Override
        public State getState() {
            return state;
        }

        @Override
        public boolean isExiting() {
            return state == State.EXITING;
        }

        @Override
        public boolean isActive() {
            return state != State.INACTIVE;
        }
    }
}


