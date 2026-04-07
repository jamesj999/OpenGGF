package com.openggf.game.session;

import com.openggf.game.GameMode;

import java.util.Objects;

public final class EditorModeContext implements ModeContext {
    private final WorldSession worldSession;
    private final EditorCursorState cursor;

    public EditorModeContext(WorldSession worldSession, EditorCursorState cursor) {
        this.worldSession = Objects.requireNonNull(worldSession, "worldSession");
        this.cursor = Objects.requireNonNull(cursor, "cursor");
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
        // Editor lifecycle is a stub for now; destroy only marks the mode as gone.
    }
}
