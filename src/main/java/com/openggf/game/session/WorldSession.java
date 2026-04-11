package com.openggf.game.session;

import com.openggf.game.GameModule;

import java.util.Objects;

public final class WorldSession {
    private final GameModule gameModule;

    public WorldSession(GameModule gameModule) {
        this.gameModule = Objects.requireNonNull(gameModule, "gameModule");
    }

    /**
     * Returns the active gameplay module owned by this world session.
     */
    public GameModule getGameModule() {
        return gameModule;
    }
}
