package com.openggf.game.session;

import com.openggf.game.GameMode;

public final class EditorModeContext implements ModeContext {
    private final WorldSession worldSession;
    private final EditorCursorState cursor;

    public EditorModeContext(WorldSession worldSession, EditorCursorState cursor) {
        this.worldSession = worldSession;
        this.cursor = cursor;
    }

    public WorldSession getWorldSession() {
        return worldSession;
    }

    public EditorCursorState getCursor() {
        return cursor;
    }

    @Override
    public GameMode getGameMode() {
        return GameMode.EDITOR;
    }

    @Override
    public void destroy() {
    }
}
