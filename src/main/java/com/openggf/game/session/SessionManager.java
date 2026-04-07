package com.openggf.game.session;

import com.openggf.game.GameModule;

import java.util.Objects;

public final class SessionManager {
    private static WorldSession currentWorldSession;
    private static GameplayModeContext currentGameplayMode;
    private static EditorModeContext currentEditorMode;

    private SessionManager() {
    }

    public static synchronized GameplayModeContext openGameplaySession(GameModule module) {
        Objects.requireNonNull(module, "module");
        destroyCurrentMode();
        currentWorldSession = new WorldSession(module);
        currentGameplayMode = new GameplayModeContext(currentWorldSession);
        return currentGameplayMode;
    }

    public static synchronized EditorModeContext enterEditorMode(EditorCursorState cursor) {
        Objects.requireNonNull(cursor, "cursor");
        if (currentWorldSession == null) {
            throw new IllegalStateException("Cannot enter editor mode without an active world session.");
        }
        destroyCurrentMode();
        currentEditorMode = new EditorModeContext(currentWorldSession, cursor);
        return currentEditorMode;
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
}
