package com.openggf.game.session;

import com.openggf.game.GameModule;
import com.openggf.game.save.SaveSessionContext;

import java.util.Objects;

public final class WorldSession {
    private final GameModule gameModule;
    private final SaveSessionContext saveSessionContext;

    public WorldSession(GameModule gameModule) {
        this(gameModule, null);
    }

    public WorldSession(GameModule gameModule, SaveSessionContext saveSessionContext) {
        this.gameModule = Objects.requireNonNull(gameModule, "gameModule");
        this.saveSessionContext = saveSessionContext;
    }

    /**
     * Returns the active gameplay module owned by this world session.
     */
    public GameModule getGameModule() {
        return gameModule;
    }

    /**
     * Returns the save session context associated with this world session,
     * or null if no save context was provided (e.g., non-save gameplay).
     */
    public SaveSessionContext getSaveSessionContext() {
        return saveSessionContext;
    }
}
