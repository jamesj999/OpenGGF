package com.openggf.game.session;

import com.openggf.game.GameMode;

public final class GameplayModeContext implements ModeContext {
    private final WorldSession worldSession;

    public GameplayModeContext(WorldSession worldSession) {
        this.worldSession = worldSession;
    }

    public WorldSession getWorldSession() {
        return worldSession;
    }

    @Override
    public GameMode getGameMode() {
        return GameMode.LEVEL;
    }

    @Override
    public void destroy() {
    }
}
