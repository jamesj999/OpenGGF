package com.openggf.game;

import com.openggf.data.Rom;
import com.openggf.level.Level;
import com.openggf.level.LevelData;

/**
 * Mutable context accumulated during level load step execution.
 * <p>
 * Each {@link InitStep} in a {@link LevelInitProfile#levelLoadSteps(LevelLoadContext)} list
 * captures a reference to this context. Steps populate fields as they execute;
 * later steps read values set by earlier ones. This replaces the local variables
 * that were previously threaded through {@code LevelManager.loadLevel()}.
 * <p>
 * The context is created fresh for each level load and discarded afterward.
 */
public class LevelLoadContext {
    private Rom rom;
    private int levelIndex = -1;
    private int zone = -1;
    private int act = -1;
    private LevelLoadMode loadMode = LevelLoadMode.FULL;
    private Level level;
    private GameModule gameModule;

    // Checkpoint snapshot fields (saved before loadLevel clears checkpoint state)
    private boolean hasCheckpoint;
    private int checkpointX;
    private int checkpointY;
    private int checkpointCameraX;
    private int checkpointCameraY;
    private int checkpointIndex = -1;
    private boolean hasWaterState;
    private int checkpointWaterLevel;
    private int checkpointWaterRoutine;

    // Post-load assembly fields
    private boolean includePostLoadAssembly;
    private boolean showTitleCard = true;
    private LevelData levelData;
    private int spawnY = -1;

    public Rom getRom() { return rom; }
    public void setRom(Rom rom) { this.rom = rom; }

    public int getLevelIndex() { return levelIndex; }
    public void setLevelIndex(int levelIndex) { this.levelIndex = levelIndex; }

    public int getZone() { return zone; }
    public void setZone(int zone) { this.zone = zone; }

    public int getAct() { return act; }
    public void setAct(int act) { this.act = act; }

    public LevelLoadMode getLoadMode() { return loadMode; }
    public void setLoadMode(LevelLoadMode loadMode) {
        this.loadMode = loadMode == null ? LevelLoadMode.FULL : loadMode;
    }

    public Level getLevel() { return level; }
    public void setLevel(Level level) { this.level = level; }

    public GameModule getGameModule() { return gameModule; }
    public void setGameModule(GameModule gameModule) { this.gameModule = gameModule; }

    // Checkpoint snapshot accessors

    public boolean hasCheckpoint() { return hasCheckpoint; }
    public int getCheckpointX() { return checkpointX; }
    public int getCheckpointY() { return checkpointY; }
    public int getCheckpointCameraX() { return checkpointCameraX; }
    public int getCheckpointCameraY() { return checkpointCameraY; }
    public int getCheckpointIndex() { return checkpointIndex; }
    public boolean hasWaterState() { return hasWaterState; }
    public int getCheckpointWaterLevel() { return checkpointWaterLevel; }
    public int getCheckpointWaterRoutine() { return checkpointWaterRoutine; }

    // Post-load assembly accessors

    public boolean isIncludePostLoadAssembly() { return includePostLoadAssembly; }
    public void setIncludePostLoadAssembly(boolean include) { this.includePostLoadAssembly = include; }

    public boolean isShowTitleCard() { return showTitleCard; }
    public void setShowTitleCard(boolean showTitleCard) { this.showTitleCard = showTitleCard; }

    public LevelData getLevelData() { return levelData; }
    public void setLevelData(LevelData levelData) { this.levelData = levelData; }

    public int getSpawnY() { return spawnY; }
    public void setSpawnY(int spawnY) { this.spawnY = spawnY; }

    /**
     * Snapshot checkpoint state from a {@link RespawnState} before level reload.
     * <p>
     * {@code loadLevel()} clears checkpoint state, so callers must snapshot
     * before the reload and restore afterward. Returns early if the state
     * is null or inactive.
     *
     * @param state the respawn state to snapshot (may be null)
     */
    public void snapshotCheckpoint(RespawnState state) {
        if (state == null || !state.isActive()) {
            hasCheckpoint = false;
            checkpointX = 0;
            checkpointY = 0;
            checkpointCameraX = 0;
            checkpointCameraY = 0;
            checkpointIndex = -1;
            hasWaterState = false;
            checkpointWaterLevel = 0;
            checkpointWaterRoutine = 0;
            return;
        }
        hasCheckpoint = true;
        checkpointX = state.getSavedX();
        checkpointY = state.getSavedY();
        checkpointCameraX = state.getSavedCameraX();
        checkpointCameraY = state.getSavedCameraY();
        checkpointIndex = state.getLastCheckpointIndex();

        if (state instanceof CheckpointState cs && cs.hasWaterState()) {
            hasWaterState = true;
            checkpointWaterLevel = cs.getSavedWaterLevel();
            checkpointWaterRoutine = cs.getSavedWaterRoutine();
        } else {
            hasWaterState = false;
            checkpointWaterLevel = 0;
            checkpointWaterRoutine = 0;
        }
    }
}
