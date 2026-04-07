package com.openggf.game.session;

import com.openggf.game.GameModule;

public final class SessionManager {
    private static WorldSession currentWorldSession;
    private static GameplayModeContext currentGameplayMode;
    private static EditorModeContext currentEditorMode;

    private SessionManager() {
    }

    public static synchronized GameplayModeContext openGameplaySession(GameModule module) {
        currentWorldSession = new WorldSession(module);
        currentEditorMode = null;
        currentGameplayMode = new GameplayModeContext(currentWorldSession);
        return currentGameplayMode;
    }

    public static synchronized EditorModeContext enterEditorMode(EditorCursorState cursor) {
        if (currentGameplayMode != null) {
            currentGameplayMode.destroy();
            currentGameplayMode = null;
        }
        currentEditorMode = new EditorModeContext(currentWorldSession, cursor);
        return currentEditorMode;
    }

    public static synchronized void clear() {
        if (currentGameplayMode != null) {
            currentGameplayMode.destroy();
        }
        if (currentEditorMode != null) {
            currentEditorMode.destroy();
        }
        currentGameplayMode = null;
        currentEditorMode = null;
        currentWorldSession = null;
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
