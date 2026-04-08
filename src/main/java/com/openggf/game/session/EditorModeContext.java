package com.openggf.game.session;

import com.openggf.game.GameMode;

import java.util.Objects;

public final class EditorModeContext implements ModeContext {
    private final WorldSession worldSession;
    private final EditorCursorState cursor;
    private final EditorPlaytestStash playtestStash;

    public EditorModeContext(WorldSession worldSession, EditorCursorState cursor) {
        this(worldSession, cursor, null);
    }

    public EditorModeContext(WorldSession worldSession, EditorCursorState cursor, EditorPlaytestStash playtestStash) {
        this.worldSession = Objects.requireNonNull(worldSession, "worldSession");
        this.cursor = Objects.requireNonNull(cursor, "cursor");
        this.playtestStash = playtestStash;
    }

    public WorldSession getWorldSession() {
        return worldSession;
    }

    public EditorCursorState getCursor() {
        return cursor;
    }

    public EditorPlaytestStash getPlaytestStash() {
        return playtestStash;
    }

    public boolean hasPlaytestStash() {
        return playtestStash != null;
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
