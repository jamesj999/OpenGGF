package com.openggf.game.session;

import com.openggf.game.GameModule;
import com.openggf.game.save.SaveSessionContext;

import java.util.Objects;

public final class SessionManager {
    private static WorldSession currentWorldSession;
    private static GameplayModeContext currentGameplayMode;
    private static EditorModeContext currentEditorMode;

    private SessionManager() {
    }

    public static synchronized GameplayModeContext openGameplaySession(GameModule module) {
        return openGameplaySession(module, null);
    }

    public static synchronized GameplayModeContext openGameplaySession(GameModule module,
                                                                       SaveSessionContext saveSessionContext) {
        Objects.requireNonNull(module, "module");
        destroyCurrentMode();
        currentWorldSession = new WorldSession(module, saveSessionContext);
        currentGameplayMode = new GameplayModeContext(currentWorldSession);
        return currentGameplayMode;
    }

    public static synchronized EditorModeContext enterEditorMode(EditorCursorState cursor) {
        return enterEditorMode(cursor, null);
    }

    public static synchronized EditorModeContext enterEditorMode(EditorCursorState cursor,
                                                                 EditorPlaytestStash playtestStash) {
        Objects.requireNonNull(cursor, "cursor");
        if (currentWorldSession == null) {
            throw new IllegalStateException("Cannot enter editor mode without an active world session.");
        }
        destroyCurrentMode();
        currentEditorMode = new EditorModeContext(currentWorldSession, cursor, playtestStash);
        return currentEditorMode;
    }

    public static synchronized GameplayModeContext exitEditorMode() {
        return resumeGameplayFromEditor();
    }

    public static synchronized GameplayModeContext resumeGameplayFromEditor() {
        if (currentEditorMode == null) {
            throw new IllegalStateException("Cannot exit editor mode without an active editor mode.");
        }
        EditorCursorState cursor = currentEditorMode.getCursor();
        EditorPlaytestStash playtestStash = currentEditorMode.getPlaytestStash();
        WorldSession worldSession = currentEditorMode.getWorldSession();
        destroyCurrentMode();
        currentGameplayMode = new GameplayModeContext(worldSession, cursor.x(), cursor.y(), playtestStash);
        return currentGameplayMode;
    }

    public static synchronized GameplayModeContext restartGameplayFromBeginning() {
        if (currentEditorMode == null) {
            throw new IllegalStateException("Cannot restart gameplay without an active editor mode.");
        }
        WorldSession worldSession = currentEditorMode.getWorldSession();
        destroyCurrentMode();
        currentGameplayMode = new GameplayModeContext(worldSession);
        return currentGameplayMode;
    }

    public static synchronized void clear() {
        destroyCurrentMode();
        currentWorldSession = null;
    }

    public static synchronized GameModule requireCurrentGameModule() {
        if (currentWorldSession == null) {
            throw new IllegalStateException("No active WorldSession");
        }
        return currentWorldSession.getGameModule();
    }

    private static void destroyCurrentMode() {
        if (currentGameplayMode != null) {
            currentGameplayMode.destroy();
            currentGameplayMode = null;
        }
        if (currentEditorMode != null) {
            currentEditorMode.destroy();
            currentEditorMode = null;
        }
    }

    public static synchronized WorldSession getCurrentWorldSession() {
        return currentWorldSession;
    }

    public static synchronized GameplayModeContext getCurrentGameplayMode() {
        return currentGameplayMode;
    }

    public static synchronized EditorModeContext getCurrentEditorMode() {
        return currentEditorMode;
    }

    /**
     * Runs the supplied teardown action while preserving the world-scoped
     * fields on the active {@link WorldSession} (loaded {@code Level}, zone
     * index, act index, apparent act). The teardown is expected to destroy
     * the gameplay runtime — which, today, transitively calls
     * {@code LevelManager.resetState()} and write-throughs {@code null} to
     * those same WorldSession fields. Capturing them here and republishing
     * after the teardown isolates that implementation detail from callers
     * (e.g. {@link com.openggf.Engine#enterEditorFromCurrentPlayer}). When
     * {@code LevelManager.resetState()} is split into a gameplay-only reset
     * and a full session reset, this helper can collapse to invoking the
     * teardown directly.
     *
     * @param teardown an action that destroys the gameplay runtime; must not
     *                 itself clear the session — the WorldSession is meant
     *                 to survive the teardown
     */
    public static synchronized void runRuntimeTeardownPreservingWorld(Runnable teardown) {
        WorldSession ws = currentWorldSession;
        com.openggf.level.Level savedLevel = ws != null ? ws.getCurrentLevel() : null;
        int savedZone = ws != null ? ws.getCurrentZone() : 0;
        int savedAct = ws != null ? ws.getCurrentAct() : 0;
        int savedApparentAct = ws != null ? ws.getApparentAct() : 0;

        teardown.run();

        if (ws != null) {
            ws.setCurrentLevel(savedLevel);
            ws.setCurrentZone(savedZone);
            ws.setCurrentAct(savedAct);
            ws.setApparentAct(savedApparentAct);
        }
    }
}
