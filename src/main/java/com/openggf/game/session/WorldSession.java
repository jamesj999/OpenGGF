package com.openggf.game.session;

import com.openggf.game.GameModule;

public final class WorldSession {
    private final GameModule gameModule;

    public WorldSession(GameModule gameModule) {
        this.gameModule = gameModule;
    }

    public GameModule getGameModule() {
        return gameModule;
    }
}
