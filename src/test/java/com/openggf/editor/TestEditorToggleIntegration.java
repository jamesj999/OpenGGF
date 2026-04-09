package com.openggf.editor;

import com.openggf.Engine;
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
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.Sonic;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_TAB;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;

class TestEditorToggleIntegration {

    @AfterEach
    void tearDown() {
        RuntimeManager.destroyCurrent();
        SessionManager.clear();
        GameModuleRegistry.reset();
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
    void startGameplayFromBeginning_discardsResumeStashAndReturnsToCanonicalSpawn() throws Exception {
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

    private static void invokeSetCurrent(GameRuntime runtime) throws Exception {
        Method setCurrent = RuntimeManager.class.getDeclaredMethod("setCurrent", GameRuntime.class);
        setCurrent.setAccessible(true);
        setCurrent.invoke(null, runtime);
    }

}
