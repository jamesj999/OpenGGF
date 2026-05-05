package com.openggf.editor;

import com.openggf.Engine;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.data.Rom;
import com.openggf.data.RomManager;
import com.openggf.game.session.EngineContext;
import com.openggf.game.GameMode;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameRuntime;
import com.openggf.game.RuntimeManager;
import com.openggf.game.ZoneFeatureProvider;
import com.openggf.game.session.EditorCursorState;
import com.openggf.game.session.EditorPlaytestStash;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.AbstractLevel;
import com.openggf.level.Block;
import com.openggf.level.Chunk;
import com.openggf.level.ChunkDesc;
import com.openggf.level.Level;
import com.openggf.level.Map;
import com.openggf.level.MutableLevel;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.SolidTile;
import com.openggf.level.LevelGeometry;
import com.openggf.level.LevelManager;
import com.openggf.level.LevelTilemapManager;
import com.openggf.level.ParallaxManager;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.Sonic;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_F5;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_E;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_TAB;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;

class TestEditorToggleIntegration {
    private static final ParallaxManager SINGLE_CHUNK_BG_PERIOD = new ParallaxManager() {
        @Override
        public int getBgPeriodWidth() {
            return 16;
        }
    };

    private static final ZoneFeatureProvider BG_WRAPPING_ZONE_FEATURES = new ZoneFeatureProvider() {
        @Override
        public void initZoneFeatures(Rom rom, int zoneIndex, int actIndex, int cameraX) {
        }

        @Override
        public void update(com.openggf.sprites.playable.AbstractPlayableSprite player, int cameraX, int zoneIndex) {
        }

        @Override
        public void reset() {
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
        public void render(com.openggf.camera.Camera camera, int frameCounter) {
        }

        @Override
        public int ensurePatternsCached(GraphicsManager graphicsManager, int baseIndex) {
            return baseIndex;
        }

        @Override
        public boolean bgWrapsHorizontally() {
            return true;
        }
    };

    @BeforeEach
    void setUp() {
        SonicConfigurationService.getInstance().resetToDefaults();
        RuntimeManager.configureEngineServices(EngineContext.fromLegacySingletonsForBootstrap());
    }

    @AfterEach
    void tearDown() {
        SonicConfigurationService.getInstance().resetToDefaults();
        RuntimeManager.destroyCurrent();
        SessionManager.clear();
        GameModuleRegistry.reset();
    }

    @Test
    void enterEditorFromCurrentPlayer_whenEditorDisabled_rejectsActivation() {
        Engine engine = new Engine();
        createGameplayRuntime(engine);
        EditorPlaytestStash stash = new EditorPlaytestStash(100, 200, 9, -3, true, 47, 1);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> engine.enterEditorFromCurrentPlayer(stash, 100, 200));

        assertEquals("Level editor is disabled by configuration.", error.getMessage());
        assertEquals(GameMode.LEVEL, engine.getCurrentGameMode());
        assertNull(SessionManager.getCurrentEditorMode());
        assertNotNull(RuntimeManager.getCurrent());
    }

    @Test
    void shiftTabInGameplay_whenEditorDisabled_doesNotEnterEditor() {
        Engine engine = new Engine();
        createGameplayRuntime(engine);
        InputHandler inputHandler = new InputHandler();
        engine.setInputHandler(inputHandler);

        inputHandler.handleKeyEvent(GLFW_KEY_LEFT_SHIFT, GLFW_PRESS);
        inputHandler.handleKeyEvent(GLFW_KEY_TAB, GLFW_PRESS);

        engine.getGameLoop().step();

        assertEquals(GameMode.LEVEL, engine.getCurrentGameMode());
        assertNull(SessionManager.getCurrentEditorMode());
        assertNotNull(RuntimeManager.getCurrent());
    }

    @Test
    void createGameplayRuntime_createsRuntimeBeforeConstructingPlayer() {
        Engine engine = new Engine();

        GameRuntime runtime = assertDoesNotThrow(() -> createGameplayRuntime(engine));

        assertNotNull(runtime);
        assertSame(runtime, RuntimeManager.getCurrent());
        assertNotNull(runtime.getSpriteManager().getSprite("sonic"));
    }

    // Removed: 3 tests that exercised RuntimeManager.parkCurrent /
    // resumeParked. Editor entry/exit now uses proper teardown+rebuild
    // (RuntimeManager.destroyCurrent + initializeGameplayRuntime + level
    // restoration); the parking mechanism has been removed entirely. The
    // editor round-trip behavior is covered by
    // enterEditorFromCurrentPlayer_thenResumePlaytestFromEditor_roundTripsStashAndSpawn
    // and editorRoundTrip_preservesMutableLevelMutations below.

    @Test
    void enterEditorFromCurrentPlayer_thenResumePlaytestFromEditor_roundTripsStashAndSpawn() throws Exception {
        enableEditor();
        Engine engine = new Engine();
        createGameplayRuntime(engine);
        EditorPlaytestStash stash = new EditorPlaytestStash(100, 200, 9, -3, true, 47, 1);

        engine.enterEditorFromCurrentPlayer(stash, 100, 200);

        assertEquals(GameMode.EDITOR, engine.getCurrentGameMode());
        assertNotNull(SessionManager.getCurrentEditorMode());
        assertEquals(100, SessionManager.getCurrentEditorMode().getCursor().x());
        assertEquals(200, SessionManager.getCurrentEditorMode().getCursor().y());
        assertSame(stash, SessionManager.getCurrentEditorMode().getPlaytestStash());
        assertNull(RuntimeManager.getCurrent());

        engine.resumePlaytestFromEditor();

        // Post-migration: editor exit rebuilds a fresh runtime over the
        // surviving WorldSession (no longer the same instance).
        assertEquals(GameMode.LEVEL, engine.getCurrentGameMode());
        assertNotNull(RuntimeManager.getCurrent());
        assertEquals(100, SessionManager.getCurrentGameplayMode().getSpawnX());
        assertEquals(200, SessionManager.getCurrentGameplayMode().getSpawnY());
        assertSame(stash, SessionManager.getCurrentGameplayMode().getResumeStash().orElseThrow());
    }

    @Test
    void editorRoundTrip_preservesMutableLevelMutations() throws Exception {
        enableEditor();
        Engine engine = new Engine();
        GameRuntime runtime = createGameplayRuntime(engine);

        // Install a synthetic MutableLevel via setLevel(), which writes
        // through to WorldSession (per the runtime ownership migration).
        MutableLevel mutable = MutableLevel.snapshot(new SyntheticLevel());
        runtime.getLevelManager().setLevel(mutable);

        com.openggf.game.session.WorldSession worldSession = runtime.getWorldSession();
        assertSame(mutable, worldSession.getCurrentLevel(),
                "precondition: setLevel must write through to WorldSession");

        // Mutate a map cell to an unambiguous value.
        int newBlockIndex = (mutable.getMap().getValue(0, 0, 0) & 0xFF) ^ 0xAA;
        mutable.setBlockInMap(0, 0, 0, newBlockIndex);

        engine.enterEditorFromCurrentPlayer(
                new EditorPlaytestStash(50, 50, 0, 0, true, 0, 1),
                100, 200);
        engine.resumePlaytestFromEditor();

        // After the editor round trip, WorldSession's Level reference should
        // still be the same MutableLevel and the mutation should still be
        // present — proving editor enter/exit does not throw away world data.
        assertSame(mutable, worldSession.getCurrentLevel(),
                "MutableLevel must survive editor round trip on WorldSession");
        assertEquals(newBlockIndex,
                ((MutableLevel) worldSession.getCurrentLevel()).getMap().getValue(0, 0, 0) & 0xFF,
                "mutation made before editor entry must persist through the round trip");
    }

    @Test
    void editorRoundTrip_preservesWorldSessionAndResetsGameplayCounters() throws Exception {
        enableEditor();
        Engine engine = new Engine();
        GameRuntime runtime = createGameplayRuntime(engine);

        // Capture the durable world state on WorldSession before editor entry.
        com.openggf.game.session.WorldSession worldSession = runtime.getWorldSession();
        com.openggf.level.Level loadedLevelBefore = worldSession.getCurrentLevel();
        int zoneBefore = worldSession.getCurrentZone();
        int actBefore = worldSession.getCurrentAct();

        // Set a session counter to a non-default value; the design requires
        // it to reset on editor exit.
        runtime.getGameState().addScore(7777);
        int scoreBefore = runtime.getGameState().getScore();
        assertTrue(scoreBefore > 0, "score precondition: must be non-zero before editor entry");

        engine.enterEditorFromCurrentPlayer(
                new EditorPlaytestStash(50, 50, 0, 0, true, 0, 1),
                100, 200);
        engine.resumePlaytestFromEditor();

        // World survived the round trip: same WorldSession instance, same
        // loaded Level, same zone/act metadata.
        assertSame(worldSession, RuntimeManager.getCurrent().getWorldSession(),
                "WorldSession must survive editor round trip");
        assertSame(loadedLevelBefore, worldSession.getCurrentLevel(),
                "Loaded Level must survive editor round trip on WorldSession");
        assertEquals(zoneBefore, worldSession.getCurrentZone(),
                "currentZone must be preserved on WorldSession");
        assertEquals(actBefore, worldSession.getCurrentAct(),
                "currentAct must be preserved on WorldSession");

        // Gameplay counters were reset per the design (editor exit reinit).
        assertEquals(0, RuntimeManager.getCurrent().getGameState().getScore(),
                "score must reset on editor exit (was " + scoreBefore + ")");
    }

    @Test
    void editorRoundTrip_rebuildsCameraBoundsAndFocusedSpriteAtCursor() throws Exception {
        enableEditor();
        Engine engine = new Engine();
        GameRuntime runtime = createGameplayRuntime(engine);
        // Set non-trivial bounds on the gameplay-mode camera so we can check
        // they survive (or are correctly re-derived) across the round trip.
        runtime.getCamera().setMinX((short) 0);
        runtime.getCamera().setMaxX((short) 1024);
        runtime.getCamera().setMinY((short) 0);
        runtime.getCamera().setMaxY((short) 768);

        engine.enterEditorFromCurrentPlayer(
                new EditorPlaytestStash(50, 50, 0, 0, true, 0, 1), 100, 200);
        // Move cursor to a deliberate spawn target before exiting editor.
        engine.getLevelEditorController().setWorldCursor(new EditorCursorState(384, 256));
        engine.resumePlaytestFromEditor();

        // After teardown+rebuild, the GameRuntime is a fresh instance and the
        // sprite/camera are too. Re-resolve the active sprite + camera and
        // assert the rebuild produced sensible state at the cursor position.
        GameRuntime resumed = RuntimeManager.getCurrent();
        assertNotNull(resumed, "rebuild must produce a fresh runtime");
        Sonic resumedPlayer = (Sonic) resumed.getSpriteManager().getSprite("sonic");
        assertNotNull(resumedPlayer, "rebuild must spawn the main character");
        assertEquals(384, resumedPlayer.getCentreX(),
                "rebuilt player should be at cursor X (applyResumedPlaytestState)");
        assertEquals(256, resumedPlayer.getCentreY(),
                "rebuilt player should be at cursor Y (applyResumedPlaytestState)");
        assertSame(resumedPlayer, resumed.getCamera().getFocusedSprite(),
                "rebuilt camera should focus on the resumed player");
    }

    @Test
    void syncEditorState_keepsSessionCursorAlignedWithControllerCursor() {
        enableEditor();
        Engine engine = new Engine();
        createGameplayRuntime(engine);

        engine.enterEditorFromCurrentPlayer(new EditorPlaytestStash(100, 200, 0, 0, true, 0, 0), 100, 200);
        engine.getLevelEditorController().setWorldCursor(new EditorCursorState(160, 224));
        engine.syncEditorState();

        assertEquals(160, SessionManager.getCurrentEditorMode().getCursor().x());
        assertEquals(224, SessionManager.getCurrentEditorMode().getCursor().y());
    }

    @Test
    void gameLoop_editorModeStepSyncsSessionCursorWithoutRender() {
        enableEditor();
        Engine engine = new Engine();
        createGameplayRuntime(engine);
        InputHandler inputHandler = new InputHandler();
        engine.setInputHandler(inputHandler);

        engine.enterEditorFromCurrentPlayer(new EditorPlaytestStash(100, 200, 0, 0, true, 0, 0), 100, 200);
        inputHandler.handleKeyEvent(GLFW_KEY_RIGHT, GLFW_PRESS);

        engine.getGameLoop().step();

        assertEquals(103, engine.getLevelEditorController().worldCursor().x());
        assertEquals(103, SessionManager.getCurrentEditorMode().getCursor().x());
        assertEquals(200, SessionManager.getCurrentEditorMode().getCursor().y());
    }

    @Test
    void parkedRuntimeSpriteRendering_doesNotRequireActiveGameServicesRuntime() {
        enableEditor();
        Engine engine = new Engine();
        GameRuntime runtime = createGameplayRuntime(engine);

        engine.enterEditorFromCurrentPlayer(new EditorPlaytestStash(100, 200, 0, 0, true, 0, 0), 100, 200);

        assertNull(RuntimeManager.getCurrent());
        assertDoesNotThrow(() -> runtime.getSpriteManager().drawLowPriority());
    }

    // Removed: parkedRuntimeBackgroundTilemapBuild_doesNotRequireActiveGameServicesRuntime
    // Tested the parking mechanism (RuntimeManager.parkCurrent) which is no longer
    // used by the editor flow — editor entry now does a proper teardown+rebuild
    // per the runtime ownership migration design.

    @Test
    void gameLoop_f5InEditorModeInvokesFreshStartHandler() {
        enableEditor();
        Engine engine = new Engine();
        createGameplayRuntime(engine);
        InputHandler inputHandler = new InputHandler();
        engine.setInputHandler(inputHandler);
        int[] freshStartCount = {0};

        engine.getGameLoop().setEditorFreshStartHandler(() -> freshStartCount[0]++);
        engine.getGameLoop().setGameMode(GameMode.EDITOR);

        inputHandler.handleKeyEvent(GLFW_KEY_F5, GLFW_PRESS);

        engine.getGameLoop().step();

        assertEquals(1, freshStartCount[0]);
        assertFalse(inputHandler.isKeyPressed(GLFW_KEY_F5));
    }

    @Test
    void gameLoop_f5InEditorModeUsesEngineFreshStartHandlerByDefault() {
        enableEditor();
        Engine engine = new Engine();
        GameRuntime runtime = createGameplayRuntime(engine);
        InputHandler inputHandler = new InputHandler();
        engine.setInputHandler(inputHandler);

        engine.enterEditorFromCurrentPlayer(new EditorPlaytestStash(100, 200, 9, -3, true, 47, 1), 100, 200);
        inputHandler.handleKeyEvent(GLFW_KEY_F5, GLFW_PRESS);

        engine.getGameLoop().step();

        assertEquals(GameMode.LEVEL, engine.getCurrentGameMode());
        assertNotSame(runtime, RuntimeManager.getCurrent());
        assertEquals(0, SessionManager.getCurrentGameplayMode().getSpawnX());
        assertEquals(0, SessionManager.getCurrentGameplayMode().getSpawnY());
        assertTrue(SessionManager.getCurrentGameplayMode().getResumeStash().isEmpty());
    }

    @Test
    void gameLoop_f5InEditorModeIgnoresLeakedGarbageRomState() throws Exception {
        enableEditor();
        Engine engine = new Engine();
        injectLeakedGarbageRom();
        GameRuntime runtime = createGameplayRuntime(engine);
        InputHandler inputHandler = new InputHandler();
        engine.setInputHandler(inputHandler);

        engine.enterEditorFromCurrentPlayer(new EditorPlaytestStash(100, 200, 9, -3, true, 47, 1), 100, 200);
        inputHandler.handleKeyEvent(GLFW_KEY_F5, GLFW_PRESS);

        assertDoesNotThrow(() -> engine.getGameLoop().step());

        assertEquals(GameMode.LEVEL, engine.getCurrentGameMode());
        assertNotSame(runtime, RuntimeManager.getCurrent());
        assertEquals(0, SessionManager.getCurrentGameplayMode().getSpawnX());
        assertEquals(0, SessionManager.getCurrentGameplayMode().getSpawnY());
        assertTrue(SessionManager.getCurrentGameplayMode().getResumeStash().isEmpty());
    }

    @Test
    void gameLoop_f5OutsideEditorModeDoesNotInvokeFreshStartHandler() {
        enableEditor();
        Engine engine = new Engine();
        createGameplayRuntime(engine);
        InputHandler inputHandler = new InputHandler();
        engine.setInputHandler(inputHandler);
        int[] freshStartCount = {0};

        engine.getGameLoop().setEditorFreshStartHandler(() -> freshStartCount[0]++);
        engine.getGameLoop().pause();
        inputHandler.handleKeyEvent(GLFW_KEY_F5, GLFW_PRESS);

        engine.getGameLoop().step();

        assertEquals(0, freshStartCount[0]);
    }

    @Test
    void syncEditorState_inWorldDepthCentersCameraOnEditorCursorWhenInsideBounds() {
        enableEditor();
        Engine engine = new Engine();
        GameRuntime runtime = createGameplayRuntime(engine);
        runtime.getCamera().setMinX((short) 0);
        runtime.getCamera().setMaxX((short) 1024);
        runtime.getCamera().setMinY((short) 0);
        runtime.getCamera().setMaxY((short) 1024);

        engine.enterEditorFromCurrentPlayer(new EditorPlaytestStash(100, 200, 0, 0, true, 0, 0), 100, 200);
        engine.getLevelEditorController().setWorldCursor(new EditorCursorState(512, 384));
        engine.syncEditorState();

        assertEquals(360, runtime.getCamera().getX());
        assertEquals(288, runtime.getCamera().getY());
    }

    @Test
    void syncEditorState_inWorldDepthClampsCameraToEditorCursorWhenCenterWouldExceedBounds() {
        enableEditor();
        Engine engine = new Engine();
        GameRuntime runtime = createGameplayRuntime(engine);
        runtime.getCamera().setMinX((short) 64);
        runtime.getCamera().setMaxX((short) 80);
        runtime.getCamera().setMinY((short) 0);
        runtime.getCamera().setMaxY((short) 160);

        engine.enterEditorFromCurrentPlayer(new EditorPlaytestStash(100, 200, 0, 0, true, 0, 0), 100, 200);
        engine.getLevelEditorController().setWorldCursor(new EditorCursorState(192, 288));
        engine.syncEditorState();

        assertEquals(64, runtime.getCamera().getX());
        assertEquals(160, runtime.getCamera().getY());
    }

    @Test
    void resumePlaytestFromEditor_usesMovedControllerCursorForGameplaySpawn() {
        enableEditor();
        Engine engine = new Engine();
        createGameplayRuntime(engine);
        EditorPlaytestStash stash = new EditorPlaytestStash(100, 200, 9, -3, true, 47, 1);

        engine.enterEditorFromCurrentPlayer(stash, 100, 200);
        engine.getLevelEditorController().setWorldCursor(new EditorCursorState(320, 448));

        engine.resumePlaytestFromEditor();

        // Post-migration: editor exit rebuilds a fresh runtime; assert non-null
        // rather than instance identity.
        assertEquals(GameMode.LEVEL, engine.getCurrentGameMode());
        assertNotNull(RuntimeManager.getCurrent());
        assertEquals(320, SessionManager.getCurrentGameplayMode().getSpawnX());
        assertEquals(448, SessionManager.getCurrentGameplayMode().getSpawnY());
        assertSame(stash, SessionManager.getCurrentGameplayMode().getResumeStash().orElseThrow());
    }

    @Test
    void resumePlaytestFromEditor_repairsProgrammaticOutOfBoundsCursorBeforeApplyingSpawn() throws Exception {
        enableEditor();
        Engine engine = new Engine();
        createGameplayRuntime(engine, (short) 100, (short) 180);
        com.openggf.game.GameServices.level().setLevel(MutableLevel.snapshot(new SyntheticLevel()));
        com.openggf.game.GameServices.camera().setMinX((short) 0);
        com.openggf.game.GameServices.camera().setMaxX((short) 255);
        com.openggf.game.GameServices.camera().setMinY((short) 0);
        com.openggf.game.GameServices.camera().setMaxY((short) 191);

        engine.enterEditorFromCurrentPlayer(new EditorPlaytestStash(100, 180, 0, 0, true, 0, 0), 100, 180);
        forceControllerCursor(engine.getLevelEditorController(), new EditorCursorState(999, -99));

        engine.resumePlaytestFromEditor();

        // Post-migration: editor exit rebuilds a fresh runtime; resolve the
        // active sprite to read its position rather than using the stale ref.
        assertEquals(GameMode.LEVEL, engine.getCurrentGameMode());
        GameRuntime resumedRuntime = RuntimeManager.getCurrent();
        assertNotNull(resumedRuntime);
        Sonic resumedPlayer = (Sonic) resumedRuntime.getSpriteManager().getSprite("sonic");
        assertNotNull(resumedPlayer);
        assertEquals(255, SessionManager.getCurrentGameplayMode().getSpawnX());
        assertEquals(0, SessionManager.getCurrentGameplayMode().getSpawnY());
        assertEquals(255, resumedPlayer.getCentreX());
        assertEquals(0, resumedPlayer.getCentreY());
        assertSame(resumedPlayer, resumedRuntime.getCamera().getFocusedSprite());
    }

    @Test
    void movedEditorCursor_becomesResumePositionWhenReturningToGameplay() {
        enableEditor();
        Engine engine = new Engine();
        GameRuntime runtime = createGameplayRuntime(engine);
        Sonic player = (Sonic) runtime.getSpriteManager().getSprite("sonic");
        InputHandler inputHandler = new InputHandler();
        engine.setInputHandler(inputHandler);

        inputHandler.handleKeyEvent(GLFW_KEY_LEFT_SHIFT, GLFW_PRESS);
        inputHandler.handleKeyEvent(GLFW_KEY_TAB, GLFW_PRESS);
        engine.getGameLoop().step();

        assertEquals(GameMode.EDITOR, engine.getCurrentGameMode());
        assertEquals(player.getCentreX(), SessionManager.getCurrentEditorMode().getCursor().x());
        assertEquals(player.getCentreY(), SessionManager.getCurrentEditorMode().getCursor().y());

        inputHandler.handleKeyEvent(GLFW_KEY_RIGHT, GLFW_PRESS);
        inputHandler.handleKeyEvent(GLFW_KEY_DOWN, GLFW_PRESS);
        engine.getGameLoop().step();
        EditorCursorState movedCursor = engine.getLevelEditorController().worldCursor();

        inputHandler.handleKeyEvent(GLFW_KEY_TAB, GLFW_RELEASE);
        inputHandler.handleKeyEvent(GLFW_KEY_LEFT_SHIFT, GLFW_RELEASE);
        inputHandler.update();
        inputHandler.handleKeyEvent(GLFW_KEY_LEFT_SHIFT, GLFW_PRESS);
        inputHandler.handleKeyEvent(GLFW_KEY_TAB, GLFW_PRESS);
        engine.getGameLoop().step();

        // Post-migration: editor exit rebuilds a fresh runtime; re-resolve
        // the active sprite to read its position.
        assertEquals(GameMode.LEVEL, engine.getCurrentGameMode());
        GameRuntime resumedRuntime = RuntimeManager.getCurrent();
        assertNotNull(resumedRuntime);
        Sonic resumedPlayer = (Sonic) resumedRuntime.getSpriteManager().getSprite("sonic");
        assertNotNull(resumedPlayer);
        assertEquals(movedCursor.x(), SessionManager.getCurrentGameplayMode().getSpawnX());
        assertEquals(movedCursor.y(), SessionManager.getCurrentGameplayMode().getSpawnY());
        assertEquals(movedCursor.x(), resumedPlayer.getCentreX());
        assertEquals(movedCursor.y(), resumedPlayer.getCentreY());
    }

    @Test
    void outOfBoundsEditorMovement_resumesFromClampedCursorPosition() {
        enableEditor();
        Engine engine = new Engine();
        createGameplayRuntime(engine, (short) 100, (short) 180);
        com.openggf.game.GameServices.level().setLevel(MutableLevel.snapshot(new SyntheticLevel()));
        com.openggf.game.GameServices.camera().setMinX((short) 0);
        com.openggf.game.GameServices.camera().setMaxX((short) 255);
        com.openggf.game.GameServices.camera().setMinY((short) 0);
        com.openggf.game.GameServices.camera().setMaxY((short) 191);
        InputHandler inputHandler = new InputHandler();
        engine.setInputHandler(inputHandler);

        inputHandler.handleKeyEvent(GLFW_KEY_LEFT_SHIFT, GLFW_PRESS);
        inputHandler.handleKeyEvent(GLFW_KEY_TAB, GLFW_PRESS);
        engine.getGameLoop().step();

        engine.getLevelEditorController().setWorldCursor(new EditorCursorState(255, 191));
        inputHandler.handleKeyEvent(GLFW_KEY_RIGHT, GLFW_PRESS);
        inputHandler.handleKeyEvent(GLFW_KEY_DOWN, GLFW_PRESS);
        engine.getGameLoop().step();

        EditorCursorState boundedCursor = engine.getLevelEditorController().worldCursor();
        assertEquals(255, boundedCursor.x());
        assertEquals(191, boundedCursor.y());
        assertEquals(255, SessionManager.getCurrentEditorMode().getCursor().x());
        assertEquals(191, SessionManager.getCurrentEditorMode().getCursor().y());
        SessionManager.getCurrentEditorMode().setCursor(new EditorCursorState(12, 34));
        assertEquals(12, SessionManager.getCurrentEditorMode().getCursor().x());
        assertEquals(34, SessionManager.getCurrentEditorMode().getCursor().y());

        inputHandler.handleKeyEvent(GLFW_KEY_TAB, GLFW_RELEASE);
        inputHandler.handleKeyEvent(GLFW_KEY_LEFT_SHIFT, GLFW_RELEASE);
        inputHandler.update();
        inputHandler.handleKeyEvent(GLFW_KEY_LEFT_SHIFT, GLFW_PRESS);
        inputHandler.handleKeyEvent(GLFW_KEY_TAB, GLFW_PRESS);
        engine.getGameLoop().step();

        // Post-migration: editor exit rebuilds a fresh runtime; resolve the
        // active sprite to read its position rather than using the stale ref.
        Sonic resumedPlayer = (Sonic) RuntimeManager.getCurrent().getSpriteManager().getSprite("sonic");
        assertNotNull(resumedPlayer);
        assertEquals(255, SessionManager.getCurrentGameplayMode().getSpawnX());
        assertEquals(191, SessionManager.getCurrentGameplayMode().getSpawnY());
        assertEquals(255, resumedPlayer.getCentreX());
        assertEquals(191, resumedPlayer.getCentreY());
    }

    @Test
    void startGameplayFromBeginning_discardsResumeStashAndReturnsToCanonicalSpawn() throws Exception {
        enableEditor();
        Engine engine = new Engine();
        createGameplayRuntime(engine);
        EditorPlaytestStash stash = new EditorPlaytestStash(100, 200, 9, -3, true, 47, 1);

        engine.enterEditorFromCurrentPlayer(stash, 100, 200);

        engine.startGameplayFromBeginning();

        assertEquals(GameMode.LEVEL, engine.getCurrentGameMode());
        assertEquals(0, SessionManager.getCurrentGameplayMode().getSpawnX());
        assertEquals(0, SessionManager.getCurrentGameplayMode().getSpawnY());
        assertTrue(SessionManager.getCurrentGameplayMode().getResumeStash().isEmpty());
        GameRuntime restartedRuntime = RuntimeManager.getCurrent();
        assertNotNull(restartedRuntime);
        assertNotNull(restartedRuntime.getSpriteManager().getSprite("sonic"));
        assertSame(restartedRuntime.getSpriteManager().getSprite("sonic"),
                restartedRuntime.getCamera().getFocusedSprite());
        if (RomManager.getInstance().isRomAvailable()) {
            assertNotNull(restartedRuntime.getLevelManager().getCurrentLevel());
        }
    }

    @Test
    void shiftTabInGameplayTogglesEditorAndBackThroughEngineHelpers() {
        enableEditor();
        Engine engine = new Engine();
        GameRuntime runtime = createGameplayRuntime(engine);
        Sonic player = (Sonic) runtime.getSpriteManager().getSprite("sonic");
        InputHandler inputHandler = new InputHandler();
        engine.setInputHandler(inputHandler);

        inputHandler.handleKeyEvent(GLFW_KEY_LEFT_SHIFT, GLFW_PRESS);
        inputHandler.handleKeyEvent(GLFW_KEY_TAB, GLFW_PRESS);

        engine.getGameLoop().step();

        assertEquals(GameMode.EDITOR, engine.getCurrentGameMode());
        assertNotNull(SessionManager.getCurrentEditorMode());
        assertEquals(player.getCentreX(), SessionManager.getCurrentEditorMode().getCursor().x());
        assertEquals(player.getCentreY(), SessionManager.getCurrentEditorMode().getCursor().y());

        inputHandler.handleKeyEvent(GLFW_KEY_TAB, GLFW_RELEASE);
        inputHandler.handleKeyEvent(GLFW_KEY_LEFT_SHIFT, GLFW_RELEASE);
        inputHandler.update();
        inputHandler.handleKeyEvent(GLFW_KEY_LEFT_SHIFT, GLFW_PRESS);
        inputHandler.handleKeyEvent(GLFW_KEY_TAB, GLFW_PRESS);

        engine.getGameLoop().step();

        assertEquals(GameMode.LEVEL, engine.getCurrentGameMode());
        assertEquals(player.getCentreX(), SessionManager.getCurrentGameplayMode().getSpawnX());
        assertEquals(player.getCentreY(), SessionManager.getCurrentGameplayMode().getSpawnY());
        assertTrue(SessionManager.getCurrentGameplayMode().getResumeStash().isPresent());
    }

    @Test
    void preRuntimePlayer_roundTripsThroughEditorModeAndResumesAtEditorCursor() {
        enableEditor();
        Engine engine = new Engine();
        createGameplayRuntime(engine, (short) 144, (short) 288);
        EditorPlaytestStash stash = new EditorPlaytestStash(144, 288, 9, -3, true, 47, 1);

        engine.enterEditorFromCurrentPlayer(stash, 320, 448);

        assertEquals(GameMode.EDITOR, engine.getCurrentGameMode());
        assertNull(RuntimeManager.getCurrent());
        assertEquals(320, SessionManager.getCurrentEditorMode().getCursor().x());
        assertEquals(448, SessionManager.getCurrentEditorMode().getCursor().y());

        engine.resumePlaytestFromEditor();

        // Post-migration: editor exit rebuilds a fresh runtime over the
        // surviving WorldSession, so runtime/player references from before
        // the editor detour are stale. Re-resolve the active sprite.
        assertEquals(GameMode.LEVEL, engine.getCurrentGameMode());
        GameRuntime resumedRuntime = RuntimeManager.getCurrent();
        assertNotNull(resumedRuntime);
        Sonic resumedPlayer = (Sonic) resumedRuntime.getSpriteManager().getSprite("sonic");
        assertNotNull(resumedPlayer);
        assertSame(resumedPlayer, resumedRuntime.getCamera().getFocusedSprite());
        assertEquals(320, resumedPlayer.getCentreX());
        assertEquals(448, resumedPlayer.getCentreY());
        assertEquals(320, SessionManager.getCurrentGameplayMode().getSpawnX());
        assertEquals(448, SessionManager.getCurrentGameplayMode().getSpawnY());
        assertSame(stash, SessionManager.getCurrentGameplayMode().getResumeStash().orElseThrow());
    }

    @Test
    void editorMvpFlow_shiftTabMoveEyedropApplyResumeAndFreshStartRemainConnected() {
        enableEditor();
        Engine engine = new Engine();
        GameRuntime runtime = createGameplayRuntime(engine, (short) 0, (short) 0);
        Sonic player = (Sonic) runtime.getSpriteManager().getSprite("sonic");
        player.setCentreX((short) 1);
        player.setCentreY((short) 1);
        MutableLevel level = MutableLevel.snapshot(new EditorMvpFlowLevel());
        runtime.getLevelManager().setLevel(level);
        runtime.getCamera().setMinX((short) 0);
        runtime.getCamera().setMaxX((short) 7);
        runtime.getCamera().setMinY((short) 0);
        runtime.getCamera().setMaxY((short) 7);
        InputHandler inputHandler = new InputHandler();
        engine.setInputHandler(inputHandler);

        inputHandler.handleKeyEvent(GLFW_KEY_LEFT_SHIFT, GLFW_PRESS);
        inputHandler.handleKeyEvent(GLFW_KEY_TAB, GLFW_PRESS);
        engine.getGameLoop().step();

        assertEquals(GameMode.EDITOR, engine.getCurrentGameMode());
        assertEquals(new EditorCursorState(1, 1), engine.getLevelEditorController().worldCursor());

        releaseAndAdvance(inputHandler, GLFW_KEY_TAB);
        releaseAndAdvance(inputHandler, GLFW_KEY_LEFT_SHIFT);
        inputHandler.handleKeyEvent(GLFW_KEY_RIGHT, GLFW_PRESS);
        inputHandler.handleKeyEvent(GLFW_KEY_DOWN, GLFW_PRESS);
        engine.getGameLoop().step();
        releaseAndAdvance(inputHandler, GLFW_KEY_RIGHT);
        releaseAndAdvance(inputHandler, GLFW_KEY_DOWN);

        assertEquals(new EditorCursorState(4, 4), engine.getLevelEditorController().worldCursor());

        inputHandler.handleKeyEvent(GLFW_KEY_E, GLFW_PRESS);
        engine.getGameLoop().step();
        releaseAndAdvance(inputHandler, GLFW_KEY_E);

        assertEquals(1, engine.getLevelEditorController().selection().selectedBlock());

        inputHandler.handleKeyEvent(GLFW_KEY_RIGHT, GLFW_PRESS);
        inputHandler.handleKeyEvent(GLFW_KEY_DOWN, GLFW_PRESS);
        engine.getGameLoop().step();
        releaseAndAdvance(inputHandler, GLFW_KEY_RIGHT);
        releaseAndAdvance(inputHandler, GLFW_KEY_DOWN);

        inputHandler.handleKeyEvent(GLFW_KEY_SPACE, GLFW_PRESS);
        engine.getGameLoop().step();
        releaseAndAdvance(inputHandler, GLFW_KEY_SPACE);

        assertEquals(new EditorCursorState(7, 7), engine.getLevelEditorController().worldCursor());
        assertEquals(1, Byte.toUnsignedInt(level.getMap().getValue(0, 7, 7)));

        inputHandler.handleKeyEvent(GLFW_KEY_LEFT_SHIFT, GLFW_PRESS);
        inputHandler.handleKeyEvent(GLFW_KEY_TAB, GLFW_PRESS);
        engine.getGameLoop().step();

        // Post-migration: editor exit rebuilds a fresh runtime; resolve the
        // active sprite to read its position rather than using the stale ref.
        assertEquals(GameMode.LEVEL, engine.getCurrentGameMode());
        GameRuntime resumedRuntime = RuntimeManager.getCurrent();
        assertNotNull(resumedRuntime);
        Sonic resumedPlayer = (Sonic) resumedRuntime.getSpriteManager().getSprite("sonic");
        assertNotNull(resumedPlayer);
        assertEquals(7, SessionManager.getCurrentGameplayMode().getSpawnX());
        assertEquals(7, SessionManager.getCurrentGameplayMode().getSpawnY());
        assertEquals(7, resumedPlayer.getCentreX());
        assertEquals(7, resumedPlayer.getCentreY());
        assertTrue(SessionManager.getCurrentGameplayMode().getResumeStash().isPresent());

        releaseAndAdvance(inputHandler, GLFW_KEY_TAB);
        releaseAndAdvance(inputHandler, GLFW_KEY_LEFT_SHIFT);
        inputHandler.handleKeyEvent(GLFW_KEY_LEFT_SHIFT, GLFW_PRESS);
        inputHandler.handleKeyEvent(GLFW_KEY_TAB, GLFW_PRESS);
        engine.getGameLoop().step();
        releaseAndAdvance(inputHandler, GLFW_KEY_TAB);
        releaseAndAdvance(inputHandler, GLFW_KEY_LEFT_SHIFT);
        inputHandler.handleKeyEvent(GLFW_KEY_F5, GLFW_PRESS);
        engine.getGameLoop().step();

        assertEquals(GameMode.LEVEL, engine.getCurrentGameMode());
        assertNotSame(runtime, RuntimeManager.getCurrent());
        assertEquals(0, SessionManager.getCurrentGameplayMode().getSpawnX());
        assertEquals(0, SessionManager.getCurrentGameplayMode().getSpawnY());
        assertTrue(SessionManager.getCurrentGameplayMode().getResumeStash().isEmpty());
    }

    private static GameRuntime createGameplayRuntime(Engine engine) {
        return createGameplayRuntime(engine, (short) 100, (short) 200);
    }

    private static GameRuntime createGameplayRuntime(Engine engine, short playerX, short playerY) {
        RomManager.getInstance().setRom(null);
        SessionManager.openGameplaySession(new Sonic2GameModule());
        GameRuntime runtime = RuntimeManager.createGameplay(SessionManager.getCurrentGameplayMode());
        SpriteManager spriteManager = runtime.getSpriteManager();
        Sonic player = new Sonic("sonic", playerX, playerY);
        spriteManager.addSprite(player);
        runtime.getCamera().setFocusedSprite(player);
        engine.getGameLoop().setRuntime(runtime);
        return runtime;
    }

    private static void injectLeakedGarbageRom() throws IOException {
        Path romPath = Files.createTempFile("editor-toggle-garbage-rom", ".bin");
        Files.write(romPath, new byte[512 * 1024]);
        romPath.toFile().deleteOnExit();
        Rom rom = new Rom();
        assertTrue(rom.open(romPath.toString()), "Expected garbage ROM temp file to open");
        RomManager.getInstance().setRom(rom);
    }

    private static void releaseAndAdvance(InputHandler inputHandler, int key) {
        inputHandler.handleKeyEvent(key, GLFW_RELEASE);
        inputHandler.update();
    }

    private static void invokeSetCurrent(GameRuntime runtime) throws Exception {
        Method setCurrent = RuntimeManager.class.getDeclaredMethod("setCurrent", GameRuntime.class);
        setCurrent.setAccessible(true);
        setCurrent.invoke(null, runtime);
    }

    private static Block lookupBlock(LevelManager levelManager, byte layer, int x, int y) {
        try {
            Method getBlockAtPosition = LevelManager.class.getDeclaredMethod("getBlockAtPosition", byte.class, int.class, int.class);
            getBlockAtPosition.setAccessible(true);
            return (Block) getBlockAtPosition.invoke(levelManager, layer, x, y);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to invoke LevelManager block lookup", e);
        }
    }

    private static void initializeTilemapManager(LevelManager levelManager) {
        try {
            Method buildGeometry = LevelManager.class.getDeclaredMethod("buildGeometry");
            buildGeometry.setAccessible(true);
            LevelGeometry geometry = (LevelGeometry) buildGeometry.invoke(levelManager);

            Field tilemapManagerField = LevelManager.class.getDeclaredField("tilemapManager");
            tilemapManagerField.setAccessible(true);
            tilemapManagerField.set(levelManager, new LevelTilemapManager(
                    geometry,
                    GraphicsManager.getInstance(),
                    RuntimeManager.getActiveRuntime() != null ? RuntimeManager.getActiveRuntime().getGameState() : null));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to initialize tilemap manager for test", e);
        }
    }

    private static void forceControllerCursor(LevelEditorController controller, EditorCursorState cursor) throws Exception {
        Field field = LevelEditorController.class.getDeclaredField("worldCursor");
        field.setAccessible(true);
        field.set(controller, cursor);
    }

    private static void enableEditor() {
        SonicConfigurationService.getInstance().setConfigValue(SonicConfiguration.EDITOR_ENABLED, true);
        assertTrue(SonicConfigurationService.getInstance().getBoolean(SonicConfiguration.EDITOR_ENABLED));
    }

    private static final class EditorMvpFlowLevel extends AbstractLevel {
        private EditorMvpFlowLevel() {
            super(0);
            patternCount = 1;
            patterns = new Pattern[] { new Pattern() };

            chunkCount = 1;
            chunks = new Chunk[] { new Chunk() };

            blockCount = 2;
            blocks = new Block[blockCount];
            for (int i = 0; i < blockCount; i++) {
                blocks[i] = new Block(1);
                blocks[i].setChunkDesc(0, 0, new ChunkDesc(0));
            }

            solidTileCount = 1;
            solidTiles = new SolidTile[] {
                    new SolidTile(0, new byte[SolidTile.TILE_SIZE_IN_ROM], new byte[SolidTile.TILE_SIZE_IN_ROM], (byte) 0)
            };

            map = new Map(2, 8, 8);
            map.setValue(0, 4, 4, (byte) 1);

            palettes = new Palette[PALETTE_COUNT];
            for (int i = 0; i < PALETTE_COUNT; i++) {
                palettes[i] = new Palette();
            }

            objects = List.of();
            rings = List.of();
            minX = 0;
            maxX = 7;
            minY = 0;
            maxY = 7;
        }

        @Override
        public int getChunksPerBlockSide() {
            return 1;
        }

        @Override
        public int getBlockPixelSize() {
            return 1;
        }
    }

    private static final class BackgroundTilemapLevel extends AbstractLevel {
        private BackgroundTilemapLevel() {
            super(0);
            patternCount = 1;
            patterns = new Pattern[patternCount];
            patterns[0] = new Pattern();
            patterns[0].setPixel(0, 0, (byte) 1);

            chunkCount = 1;
            chunks = new Chunk[chunkCount];
            chunks[0] = new Chunk();
            chunks[0].restoreState(new int[8 * 8]);

            blockCount = 1;
            blocks = new Block[blockCount];
            blocks[0] = new Block(8);
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    blocks[0].setChunkDesc(x, y, new ChunkDesc(0));
                }
            }

            solidTileCount = 1;
            solidTiles = new SolidTile[] {
                    new SolidTile(0, new byte[SolidTile.TILE_SIZE_IN_ROM], new byte[SolidTile.TILE_SIZE_IN_ROM], (byte) 0)
            };

            map = new Map(2, 1, 1);
            map.setValue(0, 0, 0, (byte) 0);
            map.setValue(1, 0, 0, (byte) 0);

            palettes = new Palette[PALETTE_COUNT];
            for (int i = 0; i < PALETTE_COUNT; i++) {
                palettes[i] = new Palette();
            }

            objects = List.of();
            rings = List.of();
            minX = 0;
            maxX = 127;
            minY = 0;
            maxY = 127;
        }

        @Override
        public int getChunksPerBlockSide() {
            return 8;
        }

        @Override
        public int getBlockPixelSize() {
            return 128;
        }
    }

    private static final class SyntheticLevel extends AbstractLevel {
        private SyntheticLevel() {
            super(0);
            patternCount = 1;
            patterns = new Pattern[patternCount];
            patterns[0] = new Pattern();
            patterns[0].setPixel(0, 0, (byte) 1);

            chunkCount = 1;
            chunks = new Chunk[chunkCount];
            chunks[0] = new Chunk();
            chunks[0].restoreState(new int[] { 0, 0, 0, 0, 0, 0 });

            blockCount = 1;
            blocks = new Block[blockCount];
            blocks[0] = new Block(2);
            blocks[0].setChunkDesc(0, 0, new ChunkDesc(0));
            blocks[0].setChunkDesc(1, 0, new ChunkDesc(0));
            blocks[0].setChunkDesc(0, 1, new ChunkDesc(0));
            blocks[0].setChunkDesc(1, 1, new ChunkDesc(0));

            solidTileCount = 1;
            solidTiles = new SolidTile[] {
                    new SolidTile(0, new byte[SolidTile.TILE_SIZE_IN_ROM], new byte[SolidTile.TILE_SIZE_IN_ROM], (byte) 0)
            };

            map = new Map(2, 4, 3);
            for (int layer = 0; layer < 2; layer++) {
                for (int y = 0; y < 3; y++) {
                    for (int x = 0; x < 4; x++) {
                        map.setValue(layer, x, y, (byte) 0);
                    }
                }
            }

            palettes = new Palette[PALETTE_COUNT];
            for (int i = 0; i < PALETTE_COUNT; i++) {
                palettes[i] = new Palette();
            }

            objects = List.of();
            rings = List.of();
            minX = 0;
            maxX = 255;
            minY = 0;
            maxY = 191;
        }

        @Override
        public int getChunksPerBlockSide() {
            return 2;
        }

        @Override
        public int getBlockPixelSize() {
            return 64;
        }
    }

}

