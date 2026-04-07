package com.openggf.game.session;

import com.openggf.game.GameMode;

import java.util.Objects;

public final class GameplayModeContext implements ModeContext {
    private final WorldSession worldSession;
    private final int spawnX;
    private final int spawnY;

    public GameplayModeContext(WorldSession worldSession) {
        this(worldSession, 0, 0);
    }

    public GameplayModeContext(WorldSession worldSession, int spawnX, int spawnY) {
        this.worldSession = Objects.requireNonNull(worldSession, "worldSession");
        this.spawnX = spawnX;
        this.spawnY = spawnY;
    }

    public WorldSession getWorldSession() {
        return worldSession;
    }

    public int getSpawnX() {
        return spawnX;
    }

    public int getSpawnY() {
        return spawnY;
    }

    @Override
    public GameMode getGameMode() {
        return GameMode.LEVEL;
    }

    @Override
    public void destroy() {
    }
}
