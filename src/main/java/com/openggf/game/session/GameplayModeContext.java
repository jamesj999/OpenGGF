package com.openggf.game.session;

import com.openggf.game.GameMode;

import java.util.Optional;
import java.util.Objects;

public final class GameplayModeContext implements ModeContext {
    private final WorldSession worldSession;
    private final int spawnX;
    private final int spawnY;
    private final EditorPlaytestStash resumeStash;

    public GameplayModeContext(WorldSession worldSession) {
        this(worldSession, 0, 0, null);
    }

    public GameplayModeContext(WorldSession worldSession, int spawnX, int spawnY) {
        this(worldSession, spawnX, spawnY, null);
    }

    public GameplayModeContext(WorldSession worldSession,
                               int spawnX,
                               int spawnY,
                               EditorPlaytestStash resumeStash) {
        this.worldSession = Objects.requireNonNull(worldSession, "worldSession");
        this.spawnX = spawnX;
        this.spawnY = spawnY;
        this.resumeStash = resumeStash;
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

    public boolean hasResumeStash() {
        return resumeStash != null;
    }

    public Optional<EditorPlaytestStash> getResumeStash() {
        return Optional.ofNullable(resumeStash);
    }

    @Override
    public GameMode getGameMode() {
        return GameMode.LEVEL;
    }

    @Override
    public void destroy() {
    }
}
