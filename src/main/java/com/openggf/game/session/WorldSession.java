package com.openggf.game.session;

import com.openggf.game.GameModule;

import java.util.Objects;

public final class WorldSession {
    private final GameModule gameModule;

    public WorldSession(GameModule gameModule) {
        this.gameModule = Objects.requireNonNull(gameModule, "gameModule");
    }

    public GameModule getGameModule() {
        return gameModule;
    }
}
