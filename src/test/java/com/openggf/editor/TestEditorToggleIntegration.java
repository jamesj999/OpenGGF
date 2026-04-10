package com.openggf.editor;

import com.openggf.Engine;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.data.RomManager;
import com.openggf.game.GameMode;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameRuntime;
import com.openggf.game.RuntimeManager;
import com.openggf.game.session.EditorCursorState;
import com.openggf.game.session.EditorPlaytestStash;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.level.AbstractLevel;
import com.openggf.level.Block;
import com.openggf.level.Chunk;
import com.openggf.level.ChunkDesc;
import com.openggf.level.Map;
import com.openggf.level.MutableLevel;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.SolidTile;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.Sonic;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @BeforeEach
    void setUp() {
        SonicConfigurationService.getInstance().resetToDefaults();
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
    void runtimeManager_reusesParkedRuntimeOnResume() {
        GameplayModeContext gameplay = SessionManager.openGameplaySession(new Sonic2GameModule());
        GameRuntime runtime = RuntimeManager.createGameplay(gameplay);
        EditorPlaytestStash stash = new EditorPlaytestStash(64, 96, 0, 0, true, 12, 1);

        RuntimeManager.parkCurrent();
        SessionManager.enterEditorMode(new EditorCursorState(320, 640), stash);

        GameplayModeContext resumed = SessionManager.resumeGameplayFromEditor();
        GameRuntime resumedRuntime = RuntimeManager.resumeParked(resumed);

        assertSame(runtime, resumedRuntime);
        assertSame(resumed, resumedRuntime.getGameplayModeContext());
        assertEquals(320, resumedRuntime.getGameplayModeContext().getSpawnX());
        assertEquals(640, resumedRuntime.getGameplayModeContext().getSpawnY());
        assertTrue(resumedRuntime.getGameplayModeContext().hasResumeStash());
        assertSame(stash, resumedRuntime.getGameplayModeContext().getResumeStash().orElseThrow());
        assertEquals(12, resumedRuntime.getGameplayModeContext().getResumeStash().orElseThrow().rings());
    }

    @Test
    void runtimeManager_resumeParkedWithDifferentWorldSession_discardsParkedRuntime() {
        GameplayModeContext firstGameplay = SessionManager.openGameplaySession(new Sonic2GameModule());
        GameRuntime firstRuntime = RuntimeManager.createGameplay(firstGameplay);
        firstRuntime.getGameState().addScore(77);

        RuntimeManager.parkCurrent();
        SessionManager.enterEditorMode(new EditorCursorState(320, 640),
                new EditorPlaytestStash(64, 96, 0, 0, true, 12, 1));

        GameplayModeContext secondGameplay = SessionManager.openGameplaySession(new Sonic2GameModule());
        GameRuntime resumedRuntime = RuntimeManager.resumeParked(secondGameplay);

        assertNotSame(firstRuntime, resumedRuntime);
        assertSame(secondGameplay, resumedRuntime.getGameplayModeContext());
        assertSame(secondGameplay.getWorldSession(), resumedRuntime.getWorldSession());
        assertEquals(0, firstRuntime.getGameState().getScore());
    }

    @Test
    void runtimeManager_setCurrentNullAfterParking_destroysAndClearsParkedRuntime() throws Exception {
        GameplayModeContext gameplay = SessionManager.openGameplaySession(new Sonic2GameModule());
        GameRuntime parkedRuntime = RuntimeManager.createGameplay(gameplay);
        parkedRuntime.getGameState().addScore(55);

        RuntimeManager.parkCurrent();
        invokeSetCurrent(null);

        GameRuntime freshRuntime = RuntimeManager.resumeParked(gameplay);

        assertNotSame(parkedRuntime, freshRuntime);
        assertSame(gameplay, freshRuntime.getGameplayModeContext());
        assertEquals(0, parkedRuntime.getGameState().getScore());
    }

    @Test
    void enterEditorFromCurrentPlayer_thenResumePlaytestFromEditor_roundTripsStashAndSpawn() throws Exception {
        enableEditor();
        Engine engine = new Engine();
        GameRuntime runtime = createGameplayRuntime(engine);
        EditorPlaytestStash stash = new EditorPlaytestStash(100, 200, 9, -3, true, 47, 1);

        engine.enterEditorFromCurrentPlayer(stash, 100, 200);

        assertEquals(GameMode.EDITOR, engine.getCurrentGameMode());
        assertNotNull(SessionManager.getCurrentEditorMode());
        assertEquals(100, SessionManager.getCurrentEditorMode().getCursor().x());
        assertEquals(200, SessionManager.getCurrentEditorMode().getCursor().y());
        assertSame(stash, SessionManager.getCurrentEditorMode().getPlaytestStash());
        assertNull(RuntimeManager.getCurrent());

        engine.resumePlaytestFromEditor();

        assertEquals(GameMode.LEVEL, engine.getCurrentGameMode());
        assertSame(runtime, RuntimeManager.getCurrent());
        assertEquals(100, SessionManager.getCurrentGameplayMode().getSpawnX());
        assertEquals(200, SessionManager.getCurrentGameplayMode().getSpawnY());
        assertSame(stash, SessionManager.getCurrentGameplayMode().getResumeStash().orElseThrow());
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
        GameRuntime runtime = createGameplayRuntime(engine);
        EditorPlaytestStash stash = new EditorPlaytestStash(100, 200, 9, -3, true, 47, 1);

        engine.enterEditorFromCurrentPlayer(stash, 100, 200);
        engine.getLevelEditorController().setWorldCursor(new EditorCursorState(320, 448));

        engine.resumePlaytestFromEditor();

        assertEquals(GameMode.LEVEL, engine.getCurrentGameMode());
        assertSame(runtime, RuntimeManager.getCurrent());
        assertEquals(320, SessionManager.getCurrentGameplayMode().getSpawnX());
        assertEquals(448, SessionManager.getCurrentGameplayMode().getSpawnY());
        assertSame(stash, SessionManager.getCurrentGameplayMode().getResumeStash().orElseThrow());
    }

    @Test
    void resumePlaytestFromEditor_repairsProgrammaticOutOfBoundsCursorBeforeApplyingSpawn() throws Exception {
        enableEditor();
        Engine engine = new Engine();
        GameRuntime runtime = createGameplayRuntime(engine, new Sonic("sonic", (short) 100, (short) 180));
        Sonic player = (Sonic) runtime.getSpriteManager().getSprite("sonic");
        runtime.getLevelManager().setLevel(MutableLevel.snapshot(new SyntheticLevel()));
        runtime.getCamera().setMinX((short) 0);
        runtime.getCamera().setMaxX((short) 255);
        runtime.getCamera().setMinY((short) 0);
        runtime.getCamera().setMaxY((short) 191);

        engine.enterEditorFromCurrentPlayer(new EditorPlaytestStash(100, 180, 0, 0, true, 0, 0), 100, 180);
        forceControllerCursor(engine.getLevelEditorController(), new EditorCursorState(999, -99));

        engine.resumePlaytestFromEditor();

        assertEquals(GameMode.LEVEL, engine.getCurrentGameMode());
        assertEquals(255, SessionManager.getCurrentGameplayMode().getSpawnX());
        assertEquals(0, SessionManager.getCurrentGameplayMode().getSpawnY());
        assertEquals(255, player.getCentreX());
        assertEquals(0, player.getCentreY());
        assertSame(player, runtime.getCamera().getFocusedSprite());
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

        assertEquals(GameMode.LEVEL, engine.getCurrentGameMode());
        assertSame(runtime, RuntimeManager.getCurrent());
        assertEquals(movedCursor.x(), SessionManager.getCurrentGameplayMode().getSpawnX());
        assertEquals(movedCursor.y(), SessionManager.getCurrentGameplayMode().getSpawnY());
        assertEquals(movedCursor.x(), player.getCentreX());
        assertEquals(movedCursor.y(), player.getCentreY());
    }

    @Test
    void outOfBoundsEditorMovement_resumesFromClampedCursorPosition() {
        enableEditor();
        Engine engine = new Engine();
        GameRuntime runtime = createGameplayRuntime(engine, new Sonic("sonic", (short) 100, (short) 180));
        Sonic player = (Sonic) runtime.getSpriteManager().getSprite("sonic");
        runtime.getLevelManager().setLevel(MutableLevel.snapshot(new SyntheticLevel()));
        runtime.getCamera().setMinX((short) 0);
        runtime.getCamera().setMaxX((short) 255);
        runtime.getCamera().setMinY((short) 0);
        runtime.getCamera().setMaxY((short) 191);
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

        assertEquals(255, SessionManager.getCurrentGameplayMode().getSpawnX());
        assertEquals(191, SessionManager.getCurrentGameplayMode().getSpawnY());
        assertEquals(255, player.getCentreX());
        assertEquals(191, player.getCentreY());
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
        Sonic player = new Sonic("sonic", (short) 144, (short) 288);
        GameRuntime runtime = createGameplayRuntime(engine, player);
        EditorPlaytestStash stash = new EditorPlaytestStash(144, 288, 9, -3, true, 47, 1);

        engine.enterEditorFromCurrentPlayer(stash, 320, 448);

        assertEquals(GameMode.EDITOR, engine.getCurrentGameMode());
        assertNull(RuntimeManager.getCurrent());
        assertSame(player, runtime.getSpriteManager().getSprite("sonic"));
        assertEquals(320, SessionManager.getCurrentEditorMode().getCursor().x());
        assertEquals(448, SessionManager.getCurrentEditorMode().getCursor().y());

        engine.resumePlaytestFromEditor();

        assertEquals(GameMode.LEVEL, engine.getCurrentGameMode());
        assertSame(runtime, RuntimeManager.getCurrent());
        assertSame(player, runtime.getSpriteManager().getSprite("sonic"));
        assertSame(player, runtime.getCamera().getFocusedSprite());
        assertEquals(320, player.getCentreX());
        assertEquals(448, player.getCentreY());
        assertEquals(320, SessionManager.getCurrentGameplayMode().getSpawnX());
        assertEquals(448, SessionManager.getCurrentGameplayMode().getSpawnY());
        assertSame(stash, SessionManager.getCurrentGameplayMode().getResumeStash().orElseThrow());
    }

    @Test
    void editorMvpFlow_shiftTabMoveEyedropApplyResumeAndFreshStartRemainConnected() {
        enableEditor();
        Engine engine = new Engine();
        Sonic player = new Sonic("sonic", (short) 0, (short) 0);
        player.setCentreX((short) 1);
        player.setCentreY((short) 1);
        GameRuntime runtime = createGameplayRuntime(engine, player);
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

        assertEquals(GameMode.LEVEL, engine.getCurrentGameMode());
        assertSame(runtime, RuntimeManager.getCurrent());
        assertEquals(7, SessionManager.getCurrentGameplayMode().getSpawnX());
        assertEquals(7, SessionManager.getCurrentGameplayMode().getSpawnY());
        assertEquals(7, player.getCentreX());
        assertEquals(7, player.getCentreY());
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
        Sonic player = new Sonic("sonic", (short) 100, (short) 200);
        return createGameplayRuntime(engine, player);
    }

    private static GameRuntime createGameplayRuntime(Engine engine, Sonic player) {
        SessionManager.openGameplaySession(new Sonic2GameModule());
        GameRuntime runtime = RuntimeManager.createGameplay(SessionManager.getCurrentGameplayMode());
        SpriteManager spriteManager = runtime.getSpriteManager();
        spriteManager.addSprite(player);
        runtime.getCamera().setFocusedSprite(player);
        engine.getGameLoop().setRuntime(runtime);
        return runtime;
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
